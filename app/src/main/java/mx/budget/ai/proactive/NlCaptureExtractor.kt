package mx.budget.ai.proactive

import android.util.Log
import mx.budget.ai.dispatch.JsonRepairer
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
 * Extractor de captura en lenguaje natural (Apéndice G.3). Orquesta dos motores:
 *
 *  1. **LLM opcional** (Gemini Nano vía AICore o Gemma vía LiteRT-LM, el híbrido
 *     ya existente): traduce la frase a un intent `ADD_EXPENSE` estructurado y
 *     **rico** (beneficiarios, pagadores, notas, categoría, wallet). Solo se
 *     intenta si el motor reporta [LlmReadiness.Available]. En Tensor G4 corre la
 *     ruta LiteRT-LM (Gemma local) cuando el modelo está descargado.
 *  2. **Parser determinista** ([NaturalLanguageCaptureParser]): el contrato
 *     **garantizado** (solo monto/concepto/fecha). Si el LLM no está listo o
 *     devuelve algo inválido, este produce el resultado básico.
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
        context: CaptureContext = CaptureContext(),
        nowEpochMs: Long = System.currentTimeMillis(),
    ): ParsedNlCapture? {
        // 1) LLM opcional (mejora la extracción cuando el modelo está disponible).
        runCatching { extractViaLlm(rawText, context, nowEpochMs) }
            .onFailure { Log.w(TAG, "Ruta LLM falló → fallback determinista", it) }
            .getOrNull()?.let { return it }
        // 2) Determinista — contrato garantizado (solo monto/concepto/fecha).
        return parser.parse(rawText, nowEpochMs)
    }

    private suspend fun extractViaLlm(
        rawText: String,
        context: CaptureContext,
        nowEpochMs: Long,
    ): ParsedNlCapture? {
        val engine = llm ?: return null
        if (systemPrompt.isBlank()) return null
        val readiness = engine.ensureReady()
        if (readiness !is LlmReadiness.Available) {
            Log.d(TAG, "LLM no disponible ($readiness) → fallback determinista")
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
