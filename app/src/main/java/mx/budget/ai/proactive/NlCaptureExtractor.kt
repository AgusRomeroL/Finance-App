package mx.budget.ai.proactive

import mx.budget.ai.dispatch.JsonRepairer
import mx.budget.ai.service.LlmReadiness
import mx.budget.ai.service.OnDeviceLlm
import org.json.JSONObject

/**
 * Extractor de captura en lenguaje natural (Apéndice G.3). Orquesta dos motores:
 *
 *  1. **LLM opcional** (Gemini Nano vía AICore o Gemma vía LiteRT-LM, el híbrido
 *     ya existente): traduce la frase a un intent `ADD_EXPENSE` estructurado. Solo
 *     se intenta si el motor reporta [LlmReadiness.Available]. Hoy NO está
 *     aprovisionado en Tensor G4, así que en la práctica casi siempre se omite.
 *  2. **Parser determinista** ([NaturalLanguageCaptureParser]): el contrato
 *     **garantizado**. Si el LLM no está listo o devuelve algo inválido, este
 *     produce el resultado.
 *
 * El LLM **nunca** muta el ledger: solo enriquece la extracción. Ambos caminos
 * desembocan en el mismo `pending_capture` (propose-then-confirm).
 */
class NlCaptureExtractor(
    private val parser: NaturalLanguageCaptureParser,
    private val llm: OnDeviceLlm?,
    private val systemPrompt: String,
) {

    suspend fun extract(
        rawText: String,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): ParsedNlCapture? {
        // 1) LLM opcional (mejora la extracción cuando el modelo está disponible).
        runCatching { extractViaLlm(rawText, nowEpochMs) }.getOrNull()?.let { return it }
        // 2) Determinista — contrato garantizado.
        return parser.parse(rawText, nowEpochMs)
    }

    private suspend fun extractViaLlm(rawText: String, nowEpochMs: Long): ParsedNlCapture? {
        val engine = llm ?: return null
        if (systemPrompt.isBlank()) return null
        if (engine.ensureReady() !is LlmReadiness.Available) return null

        val prompt = "$systemPrompt\n\nTEXTO: \"${rawText.trim()}\"\nJSON:"
        val raw = engine.generate(prompt).getOrNull() ?: return null

        val json = runCatching { JSONObject(JsonRepairer.repair(raw)) }.getOrNull() ?: return null
        if (json.optString("action") != "ADD_EXPENSE") return null

        val amount = json.optDouble("amount", 0.0)
        if (amount <= 0.0) return null
        val concept = json.optString("concept").trim().take(40).ifBlank { return null }

        // La fecha la resolvemos siempre determinista (hoy/ayer/antier) para no
        // depender de que el modelo no alucine un día arbitrario.
        return ParsedNlCapture(
            amountMxn = amount,
            concept = concept,
            occurredAt = parser.resolveDate(rawText, nowEpochMs),
        )
    }
}
