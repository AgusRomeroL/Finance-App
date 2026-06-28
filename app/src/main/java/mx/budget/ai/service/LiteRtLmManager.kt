package mx.budget.ai.service

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Motor LLM on-device vía **LiteRT-LM**: corre un modelo Gemma `.litertlm` local
 * en GPU/CPU, independiente de AICore. Es el camino que SÍ funciona en Tensor G4
 * (Pixel 9 / Fold de Norma), donde AICore aún no provisiona el Prompt API.
 *
 * El modelo (Gemma-4-E4B-it, ~3.7 GB) NO cabe en el APK: se espera en
 * `getExternalFilesDir()/gemma-4-e4b-it.litertlm`. La descarga in-app (desde
 * HuggingFace litert-community, modelo *gated*) es trabajo aparte; mientras tanto
 * el archivo se empuja por adb para pruebas. Sin modelo → [LlmReadiness.Unavailable].
 *
 * Carga del engine (pesada, ~GB) en background: [ensureReady] no bloquea — devuelve
 * [LlmReadiness.Pending] mientras carga y [LlmReadiness.Available] cuando termina.
 */
class LiteRtLmManager(
    private val context: Context,
    private val modelFileName: String = MODEL_FILE,
) : OnDeviceLlm {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var engine: Engine? = null
    @Volatile private var initFailed = false
    private var initJob: Job? = null

    private fun modelFile(): File = File(context.getExternalFilesDir(null), modelFileName)

    /** ¿Está el archivo del modelo presente? (lo consulta la UI de descarga futura). */
    fun isModelPresent(): Boolean = modelFile().exists()

    override suspend fun ensureReady(): LlmReadiness {
        if (engine != null) return LlmReadiness.Available
        if (initFailed) return LlmReadiness.Unavailable
        if (!modelFile().exists()) return LlmReadiness.Unavailable

        if (initJob?.isActive != true) {
            initJob = scope.launch {
                runCatching {
                    val e = Engine(
                        EngineConfig(
                            modelPath = modelFile().absolutePath,
                            // GPU para que un modelo de ~GB sea usable; si falla en
                            // este dispositivo, el runCatching marca initFailed → SQL.
                            backend = Backend.GPU(),
                            cacheDir = context.cacheDir.path,
                        )
                    )
                    e.initialize()
                    engine = e
                }.onFailure { initFailed = true }
            }
        }
        return if (engine != null) LlmReadiness.Available else LlmReadiness.Pending("Cargando modelo Gemma")
    }

    override suspend fun generate(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        val e = engine ?: return@withContext Result.failure(IllegalStateException("LiteRT-LM no listo"))
        runCatching {
            e.createConversation(
                ConversationConfig(
                    samplerConfig = SamplerConfig(topK = 16, topP = 0.95, temperature = 0.05, seed = 0)
                )
            ).use { conversation ->
                val response = conversation.sendMessage(prompt)
                response.contents.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString("") { it.text }
            }
        }
    }

    override fun close() {
        runCatching { engine?.close() }
        engine = null
    }

    companion object {
        const val MODEL_FILE = "gemma-4-e4b-it.litertlm"
    }
}
