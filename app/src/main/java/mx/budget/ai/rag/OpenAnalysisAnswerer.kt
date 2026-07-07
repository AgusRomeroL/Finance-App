package mx.budget.ai.rag

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import mx.budget.ai.service.OnDeviceLlm
import mx.budget.data.local.entity.QuincenaEntity
import mx.budget.data.local.result.SpendByCategory
import mx.budget.data.repository.AnalyticsRepository
import mx.budget.data.repository.ExpenseRepository
import mx.budget.data.repository.InstallmentRepository
import mx.budget.data.repository.QuincenaRepository
import java.io.InputStreamReader
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

/**
 * Ruta OPEN_ANALYSIS del asistente (segunda pasada). Se activa cuando la
 * pregunta es abierta/analítica ("¿qué patrón no sobresale a simple vista?") o
 * cuando el pipeline de intents se rindió (OutOfScope/ParseError/Unknown sin
 * razón): en vez del texto enlatado de capacidades, responde con un análisis
 * real de los datos.
 *
 * Dos modos:
 *  - **Con LLM**: arma un digest determinista compacto del estado financiero
 *    (mismas queries selectivas que [LedgerRagUseCase], serializadas denso con
 *    [ContextSerializer]) y pide al LLM redactar 3-6 frases usando SOLO cifras
 *    del contexto (prompt en `assets/ai/open_analysis_prompt.es.txt`). Si la
 *    salida viene vacía o rota, cae al modo determinista.
 *  - **Sin LLM (emulador)**: genera un insight determinista con plantillas en
 *    español — top 3 desviaciones presupuesto-vs-real de la quincena activa +
 *    el dato más inusual disponible (categoría con mayor crecimiento vs su
 *    promedio histórico).
 *
 * Nunca escribe nada: es 100% lector, como el resto del pipeline RAG.
 */
class OpenAnalysisAnswerer(
    private val context: Context,
    private val llm: OnDeviceLlm,
    private val quincenaRepository: QuincenaRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val expenseRepository: ExpenseRepository,
    private val installmentRepository: InstallmentRepository,
) {

    /** Resultado de la segunda pasada. */
    sealed class Answer {
        /** Redactado por el LLM on-device a partir del digest. */
        data class Llm(val text: String) : Answer()

        /** Insight determinista por plantillas (sin LLM, o LLM falló). */
        data class Deterministic(val text: String) : Answer()

        /** No hay datos mínimos para analizar (p. ej. sin quincena activa). */
        data class Failed(val reason: String) : Answer()
    }

    private val promptTemplate: String by lazy { loadAsset("ai/open_analysis_prompt.es.txt") }

    private val money: NumberFormat =
        NumberFormat.getCurrencyInstance(Locale("es", "MX")).apply { maximumFractionDigits = 0 }

    suspend fun answer(question: String, householdId: String, useLlm: Boolean): Answer =
        withContext(Dispatchers.IO) {
            val quincena = quincenaRepository.getActive(householdId)
                ?: return@withContext Answer.Failed("No hay quincena activa para analizar.")

            val spendByCategory = runCatching {
                analyticsRepository.getSpendByCategory(householdId, quincena.id)
            }.getOrDefault(emptyList())

            val historicalAvg = runCatching {
                analyticsRepository.getAvgSpendByCategoryHistorical(
                    householdId, sinceDate = "2000-01-01", nQuincenas = 6,
                )
            }.getOrDefault(emptyList())

            if (useLlm && promptTemplate.isNotBlank()) {
                val digest = buildDigest(quincena, spendByCategory, historicalAvg, householdId)
                val prompt = assemble(digest, PromptSanitizer.sanitize(question))
                val generated = llm.generate(prompt).getOrNull()?.let(::cleanLlmText)
                if (!generated.isNullOrBlank()) return@withContext Answer.Llm(generated)
                // LLM roto/vacío → nunca dejar al usuario sin respuesta.
            }

            Answer.Deterministic(deterministicInsight(quincena, spendByCategory, historicalAvg))
        }

    // ── Digest para el LLM ───────────────────────────────────────────────

    /**
     * Snapshot financiero completo pero compacto, serializado denso (mismo
     * formato tabular que el pipeline de intents) + promedios históricos por
     * categoría para que el LLM pueda hablar de tendencias sin inventar.
     */
    private suspend fun buildDigest(
        quincena: QuincenaEntity,
        spendByCategory: List<SpendByCategory>,
        historicalAvg: List<SpendByCategory>,
        householdId: String,
    ): String {
        val ctx = RagContext(
            currentQuincena = quincena,
            spendByCategory = spendByCategory,
            topExpenses = runCatching {
                expenseRepository.getTopExpenses(quincena.id, 12)
            }.getOrDefault(emptyList()),
            walletsSnapshot = runCatching {
                analyticsRepository.getDebtConcentration(householdId)
            }.getOrDefault(emptyList()),
            memberSpend = runCatching {
                expenseRepository.observeSpendByMember(quincena.id).first()
            }.getOrDefault(emptyList()),
            activeInstallments = runCatching {
                installmentRepository.observeActiveSummaries(householdId).first()
            }.getOrDefault(emptyList()),
            history6q = runCatching {
                quincenaRepository.getClosedSnapshots(householdId, 6)
            }.getOrDefault(emptyList()),
        )
        return buildString {
            append(ContextSerializer.serialize(ctx))
            if (historicalAvg.isNotEmpty()) {
                appendLine()
                appendLine("## PROMEDIO_HISTORICO_POR_CATEGORIA (ultimas 6 quincenas cerradas)")
                appendLine("categoria|promedio_por_quincena")
                historicalAvg.forEach {
                    appendLine("${it.categoryCode}|${"%.0f".format(it.actual)}")
                }
            }
        }
    }

    private fun assemble(digest: String, question: String): String = buildString {
        appendLine(promptTemplate.trim())
        appendLine()
        appendLine("## CONTEXTO_LOCAL")
        appendLine(digest.trim())
        appendLine()
        appendLine("## PREGUNTA_DEL_USUARIO")
        appendLine(question)
        appendLine()
        append("## RESPUESTA")
    }

    /**
     * Post-proceso defensivo de la salida del LLM: quita fences/etiquetas y
     * descarta salidas que parezcan JSON (el modelo confundió el contrato) —
     * en ese caso se cae al insight determinista.
     */
    private fun cleanLlmText(raw: String): String {
        var t = raw.trim()
            .removePrefix("```json").removePrefix("```JSON").removePrefix("```")
            .removeSuffix("```")
            .trim()
        t = t.removePrefix("## RESPUESTA").trim()
        if (t.startsWith("{") || t.startsWith("[")) return ""
        return t
    }

    // ── Insight determinista (sin LLM) ───────────────────────────────────

    /**
     * Plantillas en español: encabezado de la quincena + top 3 desviaciones
     * presupuesto-vs-real + el dato más inusual (mayor crecimiento vs promedio
     * histórico). Solo cifras reales de Room; cero invención.
     */
    private fun deterministicInsight(
        quincena: QuincenaEntity,
        byCategory: List<SpendByCategory>,
        historicalAvg: List<SpendByCategory>,
    ): String = buildString {
        // Los agregados de la quincena pueden venir en 0 (p. ej. quincena
        // creada por el rollover): en ese caso se totaliza desde las categorías.
        val actualTotal = quincena.actualExpensesMxn
            .takeIf { it > 0 } ?: byCategory.sumOf { it.actual }
        val projectedTotal = quincena.projectedExpensesMxn
            .takeIf { it > 0 } ?: byCategory.sumOf { it.projected }
        val pct = if (projectedTotal > 0) (actualTotal / projectedTotal * 100).toInt() else 0
        // Etiquetado explícito POSTED vs proyectado: al inicio de una quincena el
        // gasto ejecutado es ~$0 y sin la aclaración la cifra parece contradecir
        // el "Reservado" del dashboard (que descuenta lo PLANNED).
        append(
            "Esto es lo que veo en ${quincena.label}: llevan " +
                "${money.format(actualTotal)} ya pagados (no incluye lo planeado " +
                "por pagar) de ${money.format(projectedTotal)} proyectados ($pct %)."
        )

        val deviations = byCategory
            .filter { it.projected > 0 || it.actual > 0 }
            .sortedByDescending { abs(it.actual - it.projected) }
            .take(3)
        if (deviations.isNotEmpty()) {
            append("\n\nMayores desviaciones presupuesto vs real:")
            deviations.forEach { c ->
                val diff = c.actual - c.projected
                append(
                    if (diff >= 0) {
                        "\n· ${c.categoryName}: ${money.format(c.actual)} de " +
                            "${money.format(c.projected)} — excedida por ${money.format(diff)}"
                    } else {
                        "\n· ${c.categoryName}: ${money.format(c.actual)} de " +
                            "${money.format(c.projected)} — quedan ${money.format(-diff)}"
                    }
                )
            }
        } else {
            append("\n\nAún no hay gasto registrado por categoría en esta quincena.")
        }

        // Dato más inusual: la categoría que más creció vs su promedio histórico.
        val histById = historicalAvg.associateBy { it.categoryId }
        val unusual = byCategory
            .mapNotNull { c ->
                val avg = histById[c.categoryId]?.actual ?: return@mapNotNull null
                if (avg < MIN_HISTORICAL_BASE_MXN || c.actual <= avg) null
                else Triple(c, avg, c.actual / avg)
            }
            .maxByOrNull { it.third }
        if (unusual != null) {
            val (c, avg, ratio) = unusual
            append(
                "\n\nLo más inusual: ${c.categoryName} lleva ${money.format(c.actual)}, " +
                    "${"%.1f".format(ratio)}× su promedio histórico (${money.format(avg)} por quincena)."
            )
        }
    }

    private fun loadAsset(path: String): String = try {
        InputStreamReader(context.assets.open(path)).readText()
    } catch (e: Exception) {
        ""
    }

    private companion object {
        /** Base histórica mínima para que un ratio de crecimiento sea significativo. */
        const val MIN_HISTORICAL_BASE_MXN = 50.0
    }
}
