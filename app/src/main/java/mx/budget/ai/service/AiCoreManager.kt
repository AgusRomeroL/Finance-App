package mx.budget.ai.service

import android.content.Context
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Gestiona el modelo Gemini Nano on-device vía **ML Kit GenAI Prompt API**
 * (`com.google.mlkit.genai`), la vía GA. Reemplaza al SDK experimental
 * `com.google.ai.edge.aicore`, que en el canal de terceros del Pixel 9 daba
 * `PERMISSION_DENIED` (no allowlist por-app) y además exigía pasarle un `Context`
 * al `GenerativeModel` (bug histórico). ML Kit no necesita Context en `getClient()`
 * y maneja la **descarga del modelo** con `checkStatus()`/`download()`.
 *
 * El contrato público ([Readiness], [ensureReady], [generate], [generateStream],
 * [close]) se conserva: el [mx.budget.ai.proactive.ProactiveReasoner] no cambia.
 */
class AiCoreManager(@Suppress("unused") private val context: Context) {

    sealed class Readiness {
        object Available : Readiness()
        object UnsupportedDevice : Readiness()
        /** Modelo no listo aún (descargable/descargando); el caller cae al fallback. */
        data class TemporaryError(val reason: Int) : Readiness()
    }

    private val _readiness = MutableStateFlow<Readiness>(Readiness.UnsupportedDevice)
    val readiness: StateFlow<Readiness> = _readiness.asStateFlow()

    private var model: GenerativeModel? = null

    // Scope propio para la descarga en background (no bloquea ensureReady).
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null

    private fun client(): GenerativeModel = model ?: Generation.getClient().also { model = it }

    /**
     * Resuelve el estado del modelo. AVAILABLE → listo; DOWNLOADABLE/DOWNLOADING →
     * dispara la descarga en background (idempotente) y devuelve TemporaryError
     * (el reasoner usa SQL mientras baja); UNAVAILABLE/excepción → UnsupportedDevice.
     * No bloquea: la descarga corre en [scope], no aquí.
     */
    suspend fun ensureReady(): Readiness = withContext(Dispatchers.IO) {
        val m = runCatching { client() }.getOrNull()
            ?: return@withContext setReadiness(Readiness.UnsupportedDevice)

        val status = runCatching { m.checkStatus() }.getOrElse { FeatureStatus.UNAVAILABLE }
        val readiness = when (status) {
            FeatureStatus.AVAILABLE -> Readiness.Available
            FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> {
                ensureDownloadRunning(m)
                Readiness.TemporaryError(status)
            }
            else -> Readiness.UnsupportedDevice
        }
        setReadiness(readiness)
    }

    /** Lanza la descarga del modelo una sola vez (colecta el Flow en background). */
    private fun ensureDownloadRunning(m: GenerativeModel) {
        if (downloadJob?.isActive == true) return
        downloadJob = scope.launch {
            runCatching { m.download().collect { /* progreso disponible si la UI lo quiere */ } }
        }
    }

    suspend fun generate(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        val m = model ?: return@withContext Result.failure(IllegalStateException("Model not ready"))
        runCatching {
            val response = m.generateContent(
                generateContentRequest(TextPart(prompt)) {
                    temperature = 0.05f
                    topK = 16
                    maxOutputTokens = 240
                    candidateCount = 1
                }
            )
            response.candidates.firstOrNull()?.text.orEmpty()
        }
    }

    suspend fun generateStream(prompt: String): Flow<String> {
        val m = model ?: return flowOf()
        return m.generateContentStream(
            generateContentRequest(TextPart(prompt)) {
                temperature = 0.05f
                topK = 16
                maxOutputTokens = 240
                candidateCount = 1
            }
        ).map { it.candidates.firstOrNull()?.text.orEmpty() }.flowOn(Dispatchers.IO)
    }

    fun close() {
        runCatching { model?.close() }
        model = null
        _readiness.value = Readiness.UnsupportedDevice
    }

    private fun setReadiness(r: Readiness): Readiness {
        _readiness.value = r
        return r
    }
}
