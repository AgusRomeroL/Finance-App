package mx.budget.ai.service

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/** Qué motor está respondiendo ahora mismo. Lo pinta Perfil y lo usa el chat. */
enum class LlmEngine {
    /** Ninguno: las respuestas salen del camino determinista. */
    NONE,

    /** Gemini Nano por AICore (hoy solo Pixel 10). */
    AICORE,

    /** Modelo Gemma local por LiteRT-LM. */
    LITERTLM,
}

/**
 * Coordinador LLM on-device (Apéndice F.8, decisión 2026-06-28): intenta los
 * motores en orden de preferencia y enruta la generación al que esté listo.
 *
 *  1. **AICore / Gemini Nano** ([AiCoreManager]): TPU, gratis, eficiente; pero solo
 *     donde Google provisionó el feature (Pixel 10 hoy; Tensor G4 en el futuro).
 *  2. **LiteRT-LM** ([LiteRtLmManager]): modelo Gemma `.litertlm` local en CPU;
 *     funciona en Tensor G4 (el Fold de Norma) si el modelo ya se descargó.
 *  3. Si ninguno, [LlmReadiness.Unavailable] o [LlmReadiness.Pending] y el
 *     [mx.budget.ai.proactive.ProactiveReasoner] usa el ranking SQL determinista.
 *
 * Adapta el `AiCoreManager.Readiness` propio del manager AICore a [LlmReadiness]
 * para no acoplar su API pública (lo usa también el asistente reactivo).
 */
class HybridLlm(
    private val aiCore: AiCoreManager,
    val liteRtLm: LiteRtLmManager,
) : OnDeviceLlm {

    private val _engine = MutableStateFlow(LlmEngine.NONE)

    /** Motor activo, para que la interfaz pueda decir la verdad sobre quién responde. */
    val engine: StateFlow<LlmEngine> = _engine.asStateFlow()

    override suspend fun ensureReady(): LlmReadiness {
        // 1) AICore primero (TPU/gratis).
        if (runCatching { aiCore.ensureReady() }.getOrNull() == AiCoreManager.Readiness.Available) {
            _engine.value = LlmEngine.AICORE
            return LlmReadiness.Available
        }
        // 2) LiteRT-LM (modelo Gemma local). Available/Pending/Unavailable se propaga.
        val lite = runCatching { liteRtLm.ensureReady() }.getOrElse { LlmReadiness.Unavailable }
        _engine.value = if (lite == LlmReadiness.Available) LlmEngine.LITERTLM else LlmEngine.NONE
        return lite
    }

    /**
     * Comprueba SOLO el motor AICore (Gemini Nano), sin tocar LiteRT-LM.
     * Para caminos sensibles a memoria (captura por voz, paquete A2): cargar
     * Gemma desde el pipeline de captura provocaba OOM-kill de la app. Si AICore
     * está listo lo deja como motor activo para [generate]; si no, devuelve `false`
     * SIN intentar (ni disparar) la carga de Gemma.
     */
    suspend fun ensureAiCoreOnly(): Boolean {
        val ok = runCatching { aiCore.ensureReady() }
            .getOrNull() == AiCoreManager.Readiness.Available
        if (ok) _engine.value = LlmEngine.AICORE
        return ok
    }

    override suspend fun generate(prompt: String): Result<String> = when (_engine.value) {
        LlmEngine.AICORE -> aiCore.generate(prompt)
        LlmEngine.LITERTLM -> liteRtLm.generate(prompt)
        LlmEngine.NONE -> Result.failure(IllegalStateException("Ningún LLM on-device listo"))
    }

    override fun generateStream(prompt: String): Flow<String> = when (_engine.value) {
        LlmEngine.AICORE -> flow { emitAll(aiCore.generateStream(prompt)) }
        LlmEngine.LITERTLM -> liteRtLm.generateStream(prompt)
        LlmEngine.NONE -> flow { throw IllegalStateException("Ningún LLM on-device listo") }
    }

    override fun cancelGeneration() {
        liteRtLm.cancelGeneration()
    }

    override fun close() {
        runCatching { aiCore.close() }
        runCatching { liteRtLm.close() }
        _engine.value = LlmEngine.NONE
    }
}
