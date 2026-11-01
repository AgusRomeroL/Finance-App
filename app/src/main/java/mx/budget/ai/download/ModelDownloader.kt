package mx.budget.ai.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import mx.budget.ai.service.LlmModelVariant
import mx.budget.ai.service.ModelStorage
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Descarga el modelo `.litertlm` desde Hugging Face (decisión 4 del plan, Fase 8).
 *
 * **Por qué de ahí:** el proyecto Firebase no tiene bucket de Storage y crearlo
 * exige el plan Blaze, mientras que los repos `litert-community` son públicos y no
 * piden token. La URL de cada variante apunta a un commit, así que el contenido no
 * puede cambiar por debajo; aun así, nada llega al engine sin que el SHA-256
 * completo cuadre con el fijado en [mx.budget.ai.service.ModelCatalog]. Hugging
 * Face responde con un redirect a su CDN y OkHttp lo sigue conservando `Range`, así
 * que la descarga reanuda desde donde se quedó.
 *
 * El archivo crece en `<nombre>.part` y solo se renombra al destino final cuando el
 * SHA-256 completo cuadra: un `.part` truncado nunca llega a manos del engine.
 */
class ModelDownloader(
    private val client: OkHttpClient = defaultClient(),
) {

    /** Resultado de una descarga, ya clasificado para que el worker sepa si reintentar. */
    sealed interface Outcome {
        object Success : Outcome

        /** Fallo de red o del servidor: tiene sentido reintentar más tarde. */
        data class Retryable(val message: String) : Outcome

        /** Fallo que no se arregla solo (archivo retirado, hash que no cuadra). */
        data class Fatal(val message: String) : Outcome
    }

    /**
     * Descarga (o reanuda) la variante y deja el archivo verificado en [target].
     * [onProgress] recibe bytes escritos y total esperado.
     */
    suspend fun download(
        variant: LlmModelVariant,
        target: File,
        onProgress: suspend (Long, Long) -> Unit,
    ): Outcome = withContext(Dispatchers.IO) {
        val part = File(target.parentFile, target.name + ModelStorage.PART_SUFFIX)
        part.parentFile?.mkdirs()

        if (target.exists() && target.length() == variant.sizeBytes) {
            return@withContext Outcome.Success
        }
        if (part.exists() && part.length() > variant.sizeBytes) part.delete()

        var written = if (part.exists()) part.length() else 0L
        if (written < variant.sizeBytes) {
            // Sin Authorization: el repo es público, y mandar una credencial a un
            // tercero que no la necesita solo la expondría.
            val builder = Request.Builder().url(variant.downloadUrl)
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
                        401, 403, 404, 410 -> return@withContext Outcome.Fatal(
                            "Hugging Face ya no ofrece ${variant.displayName} (${response.code}). " +
                                "Hace falta una versión nueva de la app."
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
                                onProgress(written, variant.sizeBytes)
                            }
                        }
                        sink.flush()
                    }
                }
            } catch (e: IOException) {
                return@withContext Outcome.Retryable(e.message ?: "Se interrumpió la descarga.")
            }
        }

        onProgress(written, variant.sizeBytes)

        if (part.length() != variant.sizeBytes) {
            return@withContext Outcome.Retryable("La descarga quedó incompleta.")
        }
        if (!sha256(part).equals(variant.sha256, ignoreCase = true)) {
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

    companion object {
        private const val BUFFER_BYTES = 256 * 1024
        private const val REPORT_EVERY_BYTES = 2L * 1024 * 1024

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
