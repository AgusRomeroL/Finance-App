package mx.budget.ai.service

/**
 * Coordinador LLM on-device (Apéndice F.8, decisión 2026-06-28): intenta los
 * motores en orden de preferencia y enruta la generación al que esté listo.
 *
 *  1. **AICore / Gemini Nano** ([AiCoreManager]) — TPU, gratis, eficiente; pero
 *     solo donde Google provisionó el feature (Pixel 10 hoy; Tensor G4 en el futuro).
 *  2. **LiteRT-LM** ([LiteRtLmManager]) — modelo Gemma `.litertlm` local en GPU/CPU;
 *     funciona en Tensor G4 (Pixel 9 / Fold de Norma) si el modelo está descargado.
 *  3. Si ninguno → [LlmReadiness.Unavailable]/[LlmReadiness.Pending] y el
 *     [mx.budget.ai.proactive.ProactiveReasoner] usa el ranking SQL determinista.
 *
 * Adapta el `AiCoreManager.Readiness` propio del manager AICore a [LlmReadiness]
 * para no acoplar su API pública (lo usa también el asistente reactivo).
 */
class HybridLlm(
    private val aiCore: AiCoreManager,
    private val liteRtLm: LiteRtLmManager,
) : OnDeviceLlm {

    private enum class Active { NONE, AICORE, LITERTLM }

    @Volatile private var active = Active.NONE

    override suspend fun ensureReady(): LlmReadiness {
        // 1) AICore primero (TPU/gratis).
        if (runCatching { aiCore.ensureReady() }.getOrNull() == AiCoreManager.Readiness.Available) {
            active = Active.AICORE
            return LlmReadiness.Available
        }
        // 2) LiteRT-LM (modelo Gemma local). Available/Pending/Unavailable se propaga.
        val lite = runCatching { liteRtLm.ensureReady() }.getOrElse { LlmReadiness.Unavailable }
        active = if (lite == LlmReadiness.Available) Active.LITERTLM else Active.NONE
        return lite
    }

    /**
     * Comprueba SOLO el motor AICore (Gemini Nano), sin tocar LiteRT-LM.
     * Para caminos sensibles a memoria (captura por voz, paquete A2): cargar
     * Gemma (3.7 GB, CPU) desde el pipeline de captura provocaba OOM-kill de la
     * app. Si AICore está listo lo deja como motor activo para [generate];
     * si no, devuelve `false` SIN intentar (ni disparar) la carga de Gemma.
     */
    suspend fun ensureAiCoreOnly(): Boolean {
        val ok = runCatching { aiCore.ensureReady() }
            .getOrNull() == AiCoreManager.Readiness.Available
        if (ok) active = Active.AICORE
        return ok
    }

    override suspend fun generate(prompt: String): Result<String> = when (active) {
        Active.AICORE -> aiCore.generate(prompt)
        Active.LITERTLM -> liteRtLm.generate(prompt)
        Active.NONE -> Result.failure(IllegalStateException("Ningún LLM on-device listo"))
    }

    override fun close() {
        runCatching { aiCore.close() }
        runCatching { liteRtLm.close() }
    }
}
