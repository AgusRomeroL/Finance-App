package mx.budget.ai.download

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mx.budget.ai.service.LlmModelVariant
import mx.budget.ai.service.ModelStorage
import mx.budget.ai.service.RemoteModelInfo
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Descarga el modelo `.litertlm` desde el Firebase Storage del proyecto.
 *
 * **Por qué a mano y no con el SDK de Storage:** el `getDownloadUrl()` del SDK
 * devuelve una URL con token que se salta las reglas, y su `getFile()` no expone
 * `Range`. Aquí se pega al endpoint REST con el `Authorization: Firebase <idToken>`
 * de la sesión, así que la regla de "solo autenticado" de `storage.rules` se aplica
 * de verdad y la descarga reanuda desde donde se quedó.
 *
 * El archivo crece en `<nombre>.part` y solo se renombra al destino final cuando el
 * SHA-256 completo cuadra con el del manifiesto: un `.part` truncado nunca llega a
 * manos del engine.
 */
class ModelDownloader(
    private val client: OkHttpClient = defaultClient(),
) {

    /** Resultado de una descarga, ya clasificado para que el worker sepa si reintentar. */
    sealed interface Outcome {
        object Success : Outcome

        /** Fallo de red o del servidor: tiene sentido reintentar más tarde. */
        data class Retryable(val message: String) : Outcome

        /** Fallo que no se arregla solo (sin sesión, sin publicar, hash que no cuadra). */
        data class Fatal(val message: String) : Outcome
    }

    @Serializable
    private data class ManifestEntry(
        val id: String,
        @SerialName("file_name") val fileName: String = "",
        @SerialName("size_bytes") val sizeBytes: Long = 0L,
        val sha256: String = "",
    )

    @Serializable
    private data class Manifest(val models: List<ManifestEntry> = emptyList())

    /**
     * Lee `models/manifest.json`, que dice cuánto pesa cada variante y qué huella
     * debe tener. Es lo que permite cambiar el archivo del modelo sin publicar una
     * versión nueva de la app.
     */
    suspend fun fetchManifest(): Result<Map<String, RemoteModelInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val token = idToken() ?: error("La sesión no está iniciada.")
            val request = Request.Builder()
                .url(objectUrl(LlmModelVariant.MODELS_PREFIX + MANIFEST_FILE))
                .header("Authorization", "Firebase $token")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("El manifiesto respondió ${response.code}.")
                val body = response.body?.string().orEmpty()
                json.decodeFromString<Manifest>(body).models
                    .filter { it.sha256.isNotBlank() && it.sizeBytes > 0 }
                    .associate { it.id to RemoteModelInfo(it.id, it.sizeBytes, it.sha256.lowercase()) }
            }
        }
    }

    /**
     * Descarga (o reanuda) la variante y deja el archivo verificado en [target].
     * [onProgress] recibe bytes escritos y total esperado.
     */
    suspend fun download(
        variant: LlmModelVariant,
        expected: RemoteModelInfo,
        target: File,
        onProgress: suspend (Long, Long) -> Unit,
    ): Outcome = withContext(Dispatchers.IO) {
        val part = File(target.parentFile, target.name + ModelStorage.PART_SUFFIX)
        part.parentFile?.mkdirs()

        if (target.exists() && target.length() == expected.sizeBytes) {
            return@withContext Outcome.Success
        }
        if (part.exists() && part.length() > expected.sizeBytes) part.delete()

        val token = idToken()
            ?: return@withContext Outcome.Fatal("Inicia sesión en Perfil antes de descargar el modelo.")

        var written = if (part.exists()) part.length() else 0L
        if (written < expected.sizeBytes) {
            val builder = Request.Builder()
                .url(objectUrl(variant.storagePath))
                .header("Authorization", "Firebase $token")
            if (written > 0) builder.header("Range", "bytes=$written-")

            try {
                client.newCall(builder.build()).execute().use { response ->
                    when (response.code) {
                        200 -> {
                            // El servidor ignoró el Range: se empieza de cero.
                            part.delete()
                            written = 0L
                        }
                        206 -> Unit
                        401, 403 -> return@withContext Outcome.Fatal(
                            "El servidor rechazó la descarga (${response.code}). Vuelve a iniciar sesión."
                        )
                        404 -> return@withContext Outcome.Fatal(
                            "El modelo ${variant.displayName} todavía no está publicado."
                        )
                        416 -> {
                            part.delete()
                            return@withContext Outcome.Retryable("La descarga previa quedó inconsistente.")
                        }
                        else -> return@withContext Outcome.Retryable("El servidor respondió ${response.code}.")
                    }

                    val source = response.body?.byteStream()
                        ?: return@withContext Outcome.Retryable("Respuesta sin contenido.")
                    FileOutputStream(part, written > 0).use { sink ->
                        val buffer = ByteArray(BUFFER_BYTES)
                        var lastReport = written
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = source.read(buffer)
                            if (read <= 0) break
                            sink.write(buffer, 0, read)
                            written += read
                            if (written - lastReport >= REPORT_EVERY_BYTES) {
                                lastReport = written
                                onProgress(written, expected.sizeBytes)
                            }
                        }
                        sink.flush()
                    }
                }
            } catch (e: IOException) {
                return@withContext Outcome.Retryable(e.message ?: "Se interrumpió la descarga.")
            }
        }

        onProgress(written, expected.sizeBytes)

        if (part.length() != expected.sizeBytes) {
            return@withContext Outcome.Retryable("La descarga quedó incompleta.")
        }
        if (!sha256(part).equals(expected.sha256, ignoreCase = true)) {
            part.delete()
            return@withContext Outcome.Fatal(
                "El archivo descargado no coincide con el original. Se borró; vuelve a intentar."
            )
        }
        if (target.exists()) target.delete()
        if (!part.renameTo(target)) {
            return@withContext Outcome.Fatal("No se pudo mover el modelo a su carpeta final.")
        }
        Outcome.Success
    }

    /** Huella del archivo completo. Se calcula al final, para poder reanudar. */
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun idToken(): String? = runCatching {
        FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
    }.getOrNull()

    private fun objectUrl(path: String): String {
        val bucket = FirebaseApp.getInstance().options.storageBucket
            ?: error("El proyecto no declara bucket de Storage.")
        return "https://firebasestorage.googleapis.com/v0/b/$bucket/o/${URLEncoder.encode(path, "UTF-8")}?alt=media"
    }

    companion object {
        const val MANIFEST_FILE = "manifest.json"

        private const val BUFFER_BYTES = 256 * 1024
        private const val REPORT_EVERY_BYTES = 2L * 1024 * 1024

        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Cliente propio: el de [mx.budget.data.statements.NvidiaNimClient] lee el
         * cuerpo entero en memoria y corta a los 90 segundos, que para gigabytes es
         * justo lo contrario de lo que hace falta.
         */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
