package mx.budget.ai.service

/**
 * Contrato de un motor LLM on-device. Tiene dos implementaciones (Apéndice F.8):
 *  - [AiCoreManager]: Gemini Nano vía AICore/ML Kit (TPU, gratis, requiere que
 *    Google provisione el feature — disponible en Pixel 10, aún no en Tensor G4).
 *  - [LiteRtLmManager]: modelo Gemma `.litertlm` local vía LiteRT-LM (CPU/GPU,
 *    independiente de AICore — SÍ corre en Tensor G4 / Pixel 9 / Fold de Norma).
 *
 * [HybridLlm] los compone: AICore primero, luego LiteRT-LM, y si ninguno está,
 * el [mx.budget.ai.proactive.ProactiveReasoner] cae al ranking SQL determinista.
 */
interface OnDeviceLlm {
    suspend fun ensureReady(): LlmReadiness
    suspend fun generate(prompt: String): Result<String>
    fun close()
}

/** Estado de disponibilidad de un [OnDeviceLlm]. */
sealed interface LlmReadiness {
    /** Listo para inferir. */
    object Available : LlmReadiness
    /** No disponible en este dispositivo (sin AICore, sin modelo, init fallido). */
    object Unavailable : LlmReadiness
    /** Preparándose (descargando el modelo de AICore o cargando el engine LiteRT-LM). */
    data class Pending(val detail: String) : LlmReadiness
}
