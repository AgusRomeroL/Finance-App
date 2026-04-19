package mx.budget.ai.service

import android.content.Context
import com.google.ai.edge.aicore.DownloadCallback
import com.google.ai.edge.aicore.GenerativeAIException
import com.google.ai.edge.aicore.GenerativeModel
import com.google.ai.edge.aicore.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Singleton que gestiona el ciclo de vida del modelo Gemini Nano embebido
 * vía el servicio AICore del dispositivo (Pixel 9+).
 */
class AiCoreManager(private val context: Context) {

    sealed class Readiness {
        object Available : Readiness()
        object Downloading : Readiness()
        object UnsupportedDevice : Readiness()
        data class TemporaryError(val reason: Int) : Readiness() // errorCode from GenerativeAIException
    }

    private val _readiness = MutableStateFlow<Readiness>(Readiness.UnsupportedDevice)
    val readiness: StateFlow<Readiness> = _readiness.asStateFlow()

    private var model: GenerativeModel? = null

    /**
     * Prepara el motor de inferencia. Es idempotente.
     */
    suspend fun ensureReady(): Readiness = withContext(Dispatchers.IO) {
        if (model != null) return@withContext Readiness.Available

        try {
            val generationConfig = generationConfig {
                temperature = 0.05f 
                topK = 16 
                topP = 0.90f
                maxOutputTokens = 240 
                candidateCount = 1
                stopSequences = listOf("\n\n## END")
            }

            val newModel = GenerativeModel(
                generationConfig = generationConfig,
                downloadCallback = object : DownloadCallback {
                    override fun onDownloadStarted(bytesToDownload: Long) {
                        _readiness.value = Readiness.Downloading
                    }
                    override fun onDownloadProgress(totalBytesDownloaded: Long) {}
                    override fun onDownloadCompleted() {
                        _readiness.value = Readiness.Available
                    }
                    override fun onDownloadFailed(failureStatus: DownloadCallback.DownloadFailureStatus) {
                        _readiness.value = Readiness.TemporaryError(-1) // Custom indicator for download fail
                    }
                }
            )

            newModel.prepareInferenceEngine()
            model = newModel
            _readiness.value = Readiness.Available
            Readiness.Available

        } catch (e: UnsupportedOperationException) {
            _readiness.value = Readiness.UnsupportedDevice
            Readiness.UnsupportedDevice
        } catch (e: GenerativeAIException) {
            val err = e.errorCode
            _readiness.value = Readiness.TemporaryError(err)
            Readiness.TemporaryError(err)
        }
    }

    suspend fun generate(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        val m = model ?: return@withContext Result.failure(IllegalStateException("Model not ready"))
        
        runCatching {
            m.generateContent(prompt).text.orEmpty()
        }.recoverCatching { throwable ->
            if (throwable is GenerativeAIException && throwable.errorCode == 4) { // Assuming 4 is BUSY
                delay(250)
                m.generateContent(prompt).text.orEmpty()
            } else throw throwable
        }
    }

    suspend fun generateStream(prompt: String): Flow<String> {
        return model?.generateContentStream(prompt)
            ?.map { it.text.orEmpty() }
            ?.flowOn(Dispatchers.IO)
            ?: flowOf()
    }

    fun close() {
        model?.close()
        model = null
        _readiness.value = Readiness.UnsupportedDevice
    }
}
