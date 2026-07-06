package mx.budget.ai.proactive

import android.util.Log
import mx.budget.ai.dispatch.JsonRepairer
import mx.budget.ai.service.HybridLlm
import mx.budget.ai.service.LlmReadiness
import mx.budget.ai.service.OnDeviceLlm
import org.json.JSONArray
import org.json.JSONObject

/**
 * Contexto del hogar que se inyecta al prompt del LLM para que pueda mapear la
 * frase a entidades reales (§G.3, captura rica). Sin esto el modelo no sabría a
 * qué miembro/categoría/wallet se refiere "para los niños", "de despensa" o "con
 * la BBVA". Los nombres se pasan tal cual (display names); la resolución a IDs es
 * posterior y determinista ([mx.budget.ai.dispatch.AliasResolver]).
 */
data class CaptureContext(
    val memberNames: List<String> = emptyList(),
    val categoryNames: List<String> = emptyList(),
    val walletNames: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = memberNames.isEmpty() && categoryNames.isEmpty() && walletNames.isEmpty()
}

/**
 * Extractor de captura en lenguaje natural (Apéndice G.3, rediseño A2). Expone
 * dos fases SEPARADAS que antes vivían en un solo `extract()` bloqueante:
 *
 *  1. **[extractFast]** — parser determinista ([NaturalLanguageCaptureParser]),
 *     el contrato **garantizado** (solo monto/concepto/fecha). Síncrono, <1 ms,
 *     sin LLM: es lo único que corre en el camino de creación inmediata de la
 *     captura (D1). Devuelve `null` si no hay monto.
 *  2. **[enrich]** — pasada LLM opcional y ASÍNCRONA (la llama
 *     [mx.budget.data.capture.EnrichCaptureWorker]): traduce la frase a un
 *     intent `ADD_EXPENSE` **rico** (beneficiarios, pagadores, notas, categoría,
 *     wallet). SOLO usa AICore/Gemini Nano ([HybridLlm.ensureAiCoreOnly]);
 *     **NUNCA** carga LiteRT-LM/Gemma aquí — cargar 3.7 GB en el camino de
 *     captura era la causa del OOM-kill reportado (crash tras dictar).
 *
 * El LLM **nunca** muta el ledger: solo enriquece la extracción. Ambos caminos
 * desembocan en el mismo `pending_capture` (propose-then-confirm).
 */
class NlCaptureExtractor(
    private val parser: NaturalLanguageCaptureParser,
    private val llm: OnDeviceLlm?,
    private val systemPrompt: String,
) {

    /**
     * Fase 1 (síncrona, garantizada): solo el parser determinista. `null` si no
     * se extrae un monto > 0. [nowEpochMs] debe ser el **momento del dictado**
     * (para que "ayer" se resuelva relativo a cuándo se dijo, no a cuándo se
     * terminó de procesar).
     */
    fun extractFast(
        rawText: String,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): ParsedNlCapture? = parser.parse(rawText, nowEpochMs)

    /**
     * Fase 2 (asíncrona, opcional): pasada LLM rica, SOLO si AICore está
     * disponible. Devuelve `null` si no hay motor, el prompt está vacío, el
     * modelo no está listo o la salida es inválida — el caller conserva
     * entonces lo que ya extrajo [extractFast]. Nunca lanza.
     */
    suspend fun enrich(
        rawText: String,
        context: CaptureContext = CaptureContext(),
        nowEpochMs: Long = System.currentTimeMillis(),
    ): ParsedNlCapture? =
        runCatching { extractViaLlm(rawText, context, nowEpochMs) }
            .onFailure { Log.w(TAG, "Ruta LLM de enriquecimiento falló", it) }
            .getOrNull()

    private suspend fun extractViaLlm(
        rawText: String,
        context: CaptureContext,
        nowEpochMs: Long,
    ): ParsedNlCapture? {
        val engine = llm ?: return null
        if (systemPrompt.isBlank()) return null
        // Gate de memoria (A2): en el camino de captura solo se permite AICore.
        // HybridLlm.ensureReady() intentaría cargar Gemma (LiteRT-LM, 3.7 GB) y
        // eso mata la app por OOM; ensureAiCoreOnly() pregunta sin cargar nada.
        val ready = when (engine) {
            is HybridLlm -> engine.ensureAiCoreOnly()
            else -> engine.ensureReady() is LlmReadiness.Available
        }
        if (!ready) {
            Log.d(TAG, "AICore no disponible → sin enriquecimiento LLM")
            return null
        }

        val prompt = buildString {
            append(systemPrompt)
            if (!context.isEmpty) {
                append("\n\n")
                append(renderContext(context))
            }
            append("\n\nTEXTO: \"")
            append(rawText.trim())
            append("\"\nJSON:")
        }
        val raw = engine.generate(prompt).getOrNull() ?: run {
            Log.w(TAG, "LLM sin salida → fallback determinista")
            return null
        }
        Log.d(TAG, "LLM raw: ${raw.take(400)}")

        val json = runCatching { JSONObject(JsonRepairer.repair(raw)) }.getOrNull() ?: run {
            Log.w(TAG, "JSON no parseable → fallback. raw=${raw.take(160)}")
            return null
        }
        if (json.optString("action") != "ADD_EXPENSE") return null

        val amount = json.optDouble("amount", 0.0)
        if (amount <= 0.0) return null
        val concept = json.optString("concept").trim().take(40).ifBlank { return null }

        val parsed = ParsedNlCapture(
            amountMxn = amount,
            concept = concept,
            // La fecha la resolvemos siempre determinista (hoy/ayer/antier) para no
            // depender de que el modelo no alucine un día arbitrario.
            occurredAt = parser.resolveDate(rawText, nowEpochMs),
            notes = json.optString("notes").trim().ifBlank { null },
            categoryHint = json.optString("category").trim().ifBlank { null },
            walletHint = json.optString("wallet").trim().ifBlank { null },
            beneficiaries = parseShares(json.optJSONArray("beneficiaries")),
            payers = parseShares(json.optJSONArray("payers")),
        )
        Log.d(
            TAG,
            "parsed: monto=${parsed.amountMxn} concepto='${parsed.concept}' cat='${parsed.categoryHint}' " +
                "wallet='${parsed.walletHint}' notas='${parsed.notes}' " +
                "benef=${parsed.beneficiaries} pagadores=${parsed.payers}",
        )
        return parsed
    }

    /** Serializa el contexto del hogar en líneas compactas para el prompt. */
    private fun renderContext(context: CaptureContext): String = buildString {
        append("CONTEXTO:")
        if (context.memberNames.isNotEmpty()) {
            append("\nMIEMBROS: ").append(context.memberNames.joinToString(", "))
        }
        if (context.categoryNames.isNotEmpty()) {
            append("\nCATEGORÍAS: ").append(context.categoryNames.joinToString(", "))
        }
        if (context.walletNames.isNotEmpty()) {
            append("\nWALLETS: ").append(context.walletNames.joinToString(", "))
        }
    }

    /** Parsea un arreglo `[{"name":..,"share":..}]`; tolera ausencia de `share`. */
    private fun parseShares(arr: JSONArray?): List<NameShare> {
        if (arr == null) return emptyList()
        val out = ArrayList<NameShare>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val name = obj.optString("name").trim()
            if (name.isBlank()) continue
            val share = if (obj.has("share") && !obj.isNull("share")) {
                obj.optInt("share").takeIf { it > 0 }
            } else null
            out.add(NameShare(name, share))
        }
        return out
    }

    private companion object {
        const val TAG = "NlCapture"
    }
}
