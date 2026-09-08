// `getBenchmarkInfo()` sigue marcada como experimental en litertlm 0.13.1. Se acepta
// a propósito: es la única forma de medir carga, primer token y tokens por segundo,
// y si un día desaparece solo se pierde la telemetría, no la inferencia.
@file:OptIn(com.google.ai.edge.litertlm.ExperimentalApi::class)

package mx.budget.ai.service

import android.content.Context
import android.os.SystemClock
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.BenchmarkInfo
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Motor LLM on-device vía **LiteRT-LM**: corre un modelo Gemma `.litertlm` local en
 * CPU, independiente de AICore. Es el camino que SÍ funciona en Tensor G4 (el Fold
 * de Norma), donde AICore aún no provisiona el Prompt API.
 *
 * El modelo no cabe en el APK: vive en `getExternalFilesDir()` y lo pone ahí la
 * descarga in-app ([mx.budget.ai.download.ModelDownloadWorker], Fase 4). El archivo
 * solo llega a esa carpeta con su SHA-256 verificado, así que aquí basta con
 * comprobar que existe y que no es un resto ridículamente pequeño. Sin modelo,
 * [LlmReadiness.Unavailable].
 *
 * Carga del engine (pesada, gigabytes) en background: [ensureReady] no bloquea,
 * devuelve [LlmReadiness.Pending] mientras carga y [LlmReadiness.Available] cuando
 * termina. Si la carga se pasa de [INIT_TIMEOUT_MS] se da por fallida y la app cae al
 * camino determinista, en vez de dejar al usuario esperando para siempre.
 */
class LiteRtLmManager(
    private val context: Context,
) : OnDeviceLlm {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var engine: Engine? = null
    @Volatile private var initFailed = false
    @Volatile private var initStartedAt = 0L
    @Volatile private var loadedVariant: LlmModelVariant? = null
    @Volatile private var currentConversation: Conversation? = null
    private var initJob: Job? = null

    /** Últimas cifras del motor (carga, primer token, tokens por segundo). */
    @Volatile var lastBenchmark: BenchmarkInfo? = null
        private set

    /**
     * Variante elegida en Perfil. La fija [AiModelManager]; sirve para desempatar
     * cuando hay dos archivos en disco y para recargar el engine si el usuario
     * cambia de variante sin reiniciar la app.
     */
    @Volatile var preferredVariant: LlmModelVariant? = null

    /** Variante cuyo archivo está en disco, si hay alguno. */
    fun installedVariant(): LlmModelVariant? {
        val preferred = preferredVariant
        if (preferred != null && ModelStorage.file(context, preferred).exists()) return preferred
        return ModelStorage.installed(context)
    }

    /** ¿Está el archivo del modelo presente? (lo consulta la UI de descarga). */
    fun isModelPresent(): Boolean = installedVariant() != null

    override suspend fun ensureReady(): LlmReadiness {
        if (engine != null) {
            if (loadedVariant == installedVariant()) return LlmReadiness.Available
            // Cambió la variante bajo los pies: se suelta el engine y se recarga.
            releaseAndForget()
        }
        if (initFailed) return LlmReadiness.Unavailable

        val variant = installedVariant() ?: return LlmReadiness.Unavailable
        val file = ModelStorage.file(context, variant)
        if (file.length() < MIN_MODEL_BYTES) {
            android.util.Log.w("LiteRtLmManager", "el archivo del modelo es demasiado chico, se ignora")
            return LlmReadiness.Unavailable
        }

        if (initJob?.isActive != true) {
            initStartedAt = SystemClock.elapsedRealtime()
            initJob = scope.launch {
                runCatching {
                    val e = Engine(
                        EngineConfig(
                            modelPath = file.absolutePath,
                            // CPU: el más compatible (GPU daba INTERNAL al compilar el
                            // modelo). Más lento pero la inferencia corre en background.
                            backend = Backend.CPU(),
                            cacheDir = context.cacheDir.path,
                        )
                    )
                    e.initialize()
                    engine = e
                    loadedVariant = variant
                }.onFailure {
                    initFailed = true
                    android.util.Log.w("LiteRtLmManager", "init del engine Gemma falló, fallback SQL", it)
                }
            }
        }
        if (engine != null) return LlmReadiness.Available

        val elapsed = SystemClock.elapsedRealtime() - initStartedAt
        if (elapsed > INIT_TIMEOUT_MS) {
            // El `initialize()` nativo no es cancelable, pero la app deja de esperarlo.
            initFailed = true
            android.util.Log.w("LiteRtLmManager", "la carga del modelo excedió $INIT_TIMEOUT_MS ms")
            return LlmReadiness.Unavailable
        }
        return LlmReadiness.Pending("Cargando ${variant.displayName}")
    }

    override suspend fun generate(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        val e = engine ?: return@withContext Result.failure(IllegalStateException("LiteRT-LM no listo"))
        runCatching {
            e.createConversation(conversationConfig()).use { conversation ->
                currentConversation = conversation
                try {
                    val text = conversation.sendMessage(prompt).text()
                    lastBenchmark = runCatching { conversation.getBenchmarkInfo() }.getOrNull()
                    text
                } finally {
                    currentConversation = null
                }
            }
        }
    }

    /**
     * Generación token a token. Es lo que permite que el chat muestre algo antes de
     * que termine: en CPU una respuesta larga tarda decenas de segundos, y un spinner
     * mudo todo ese rato no le dice nada a nadie.
     */
    override fun generateStream(prompt: String): Flow<String> = flow {
        val e = engine ?: throw IllegalStateException("LiteRT-LM no listo")
        val conversation = e.createConversation(conversationConfig())
        currentConversation = conversation
        try {
            conversation.sendMessageAsync(prompt).collect { message -> emit(message.text()) }
            lastBenchmark = runCatching { conversation.getBenchmarkInfo() }.getOrNull()
        } finally {
            currentConversation = null
            runCatching { conversation.close() }
        }
    }.flowOn(Dispatchers.IO)

    /** Corta la generación en curso. La corrutina que la espera también termina. */
    override fun cancelGeneration() {
        runCatching { currentConversation?.cancelProcess() }
    }

    /**
     * Llega un modelo nuevo: se olvida el fallo anterior para poder intentarlo sin
     * reiniciar el proceso. Antes, un `initFailed` se quedaba pegado hasta que el
     * sistema mataba la app.
     */
    fun onModelChanged() {
        releaseAndForget()
    }

    /** Cierra el engine y limpia el estado, dejando el manager listo para reintentar. */
    fun releaseAndForget() {
        initJob?.cancel()
        initJob = null
        runCatching { engine?.close() }
        engine = null
        loadedVariant = null
        initFailed = false
        initStartedAt = 0L
        lastBenchmark = null
    }

    override fun close() {
        runCatching { engine?.close() }
        engine = null
        loadedVariant = null
    }

    private fun conversationConfig() = ConversationConfig(
        samplerConfig = SamplerConfig(topK = 16, topP = 0.95, temperature = 0.05, seed = 0)
    )

    private fun Message.text(): String = contents.contents
        .filterIsInstance<Content.Text>()
        .joinToString("") { it.text }

    companion object {
        /** Menos que esto no es un modelo, es un archivo roto. */
        private const val MIN_MODEL_BYTES = 100L * 1024 * 1024

        /** Tope de paciencia para la carga del engine. */
        const val INIT_TIMEOUT_MS = 240_000L
    }
}
