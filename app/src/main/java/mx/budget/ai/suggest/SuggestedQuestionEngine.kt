package mx.budget.ai.suggest

import mx.budget.ai.domain.AssistantResponse
import mx.budget.ai.service.LlmReadiness
import mx.budget.ai.service.OnDeviceLlm
import mx.budget.data.repository.AnalyticsRepository
import mx.budget.data.repository.QuincenaRepository
import mx.budget.data.settings.SettingsRepository

/**
 * Pregunta sugerida para el chat de Analíticas (pill de atajo).
 *
 * Dos formas de despacho, ambas SIN parseo LLM:
 * - [response] != null → bypass `sendPredefined` (intent ya estructurado).
 * - [openQuestion] != null → ruta OPEN_ANALYSIS (`sendOpenAnalysis`).
 */
data class SuggestedQuestion(
    /** Clave estable para la rotación (p. ej. "overspend:FOOD"). */
    val id: String,
    /** Texto visible del pill. */
    val label: String,
    val response: AssistantResponse? = null,
    val openQuestion: String? = null,
)

/**
 * Genera los pills de pregunta del chat de Analíticas a partir del ESTADO REAL
 * del presupuesto — espejo estructural de `ProactiveReasoner`: candidatos
 * deterministas (SQL/Kotlin) primero, re-ranking LLM opcional después, y el
 * fallback determinista siempre intacto.
 *
 * Reglas de candidatos (en orden de prioridad):
 * 1. Categoría con MAYOR sobregasto (actual > projected) → EXPLAIN_VARIANCE.
 * 2. Categoría cerca del límite (80-100 % ejecutado) → GET_CATEGORY_REMAINING.
 * 3. Wallet de crédito con mayor deuda → GET_WALLET_BALANCE.
 * 4. Pool estable (resumen, top categoría, top spender, comparación, MSI,
 *    patrón inusual) para completar el cupo.
 *
 * Rotación/frescura: los ids mostrados se registran en DataStore con timestamp;
 * un candidato del pool visto hace <24 h se penaliza al final para que los
 * pills no sean siempre los mismos. Los candidatos DINÁMICOS (1-3) no se
 * penalizan: mientras la señal exista (sobregasto vivo), son lo más relevante.
 *
 * Re-ranking LLM (opcional, "sugeridos por IA"): si el LLM on-device ya está
 * `Available` (no se espera su carga), se le pide elegir el orden de los ids;
 * la respuesta se valida contra el conjunto de candidatos — ids inventados se
 * descartan y ante cualquier fallo queda el orden determinista.
 */
class SuggestedQuestionEngine(
    private val analyticsRepository: AnalyticsRepository,
    private val quincenaRepository: QuincenaRepository,
    private val settingsRepository: SettingsRepository,
    private val llm: OnDeviceLlm? = null,
) {

    suspend fun suggest(householdId: String, limit: Int = 6): List<SuggestedQuestion> {
        val candidates = buildCandidates(householdId)
        val fresh = applyRotation(candidates)
        val ranked = rerankWithLlmIfReady(fresh)
        val shown = ranked.take(limit)
        markShown(shown.map { it.id })
        return shown
    }

    // ── 1. Candidatos deterministas ─────────────────────────────────────────

    private suspend fun buildCandidates(householdId: String): List<SuggestedQuestion> {
        val dynamic = mutableListOf<SuggestedQuestion>()
        val quincena = runCatching { quincenaRepository.getActive(householdId) }.getOrNull()

        if (quincena != null) {
            val byCategory = runCatching {
                analyticsRepository.getSpendByCategory(householdId, quincena.id)
            }.getOrDefault(emptyList())

            // 1. Mayor sobregasto (solo con presupuesto real; projected=0 no es señal).
            byCategory
                .filter { it.projected > 0 && it.actual > it.projected }
                .maxByOrNull { it.actual - it.projected }
                ?.let {
                    dynamic += SuggestedQuestion(
                        id = "overspend:${it.categoryCode}",
                        label = "¿Por qué me pasé en ${it.categoryName}?",
                        response = AssistantResponse(
                            intent = AssistantResponse.Intent.EXPLAIN_VARIANCE,
                            args = AssistantResponse.Args(category_code = it.categoryCode),
                            reason = "",
                        ),
                    )
                }

            // 2. Cerca del límite (80-100 %), la más avanzada.
            byCategory
                .filter { it.projected > 0 && it.pctExec in 80..100 }
                .maxByOrNull { it.pctExec }
                ?.let {
                    dynamic += SuggestedQuestion(
                        id = "nearlimit:${it.categoryCode}",
                        label = "¿Cuánto queda en ${it.categoryName}?",
                        response = AssistantResponse(
                            intent = AssistantResponse.Intent.GET_CATEGORY_REMAINING,
                            args = AssistantResponse.Args(category_code = it.categoryCode),
                            reason = "",
                        ),
                    )
                }
        }

        // 3. Wallet de crédito con mayor deuda (balance más negativo o mayor utilización).
        runCatching { analyticsRepository.getDebtConcentration(householdId) }
            .getOrDefault(emptyList())
            .filter { it.kind == "CREDIT" && it.balance < 0 }
            .minByOrNull { it.balance }
            ?.let {
                dynamic += SuggestedQuestion(
                    id = "debt:${it.paymentMethodId}",
                    label = "¿Cuánto debo en ${it.displayName}?",
                    response = AssistantResponse(
                        intent = AssistantResponse.Intent.GET_WALLET_BALANCE,
                        args = AssistantResponse.Args(wallet_name = it.displayName),
                        reason = "",
                    ),
                )
            }

        return dynamic + STABLE_POOL
    }

    // ── 2. Rotación por frescura (DataStore) ────────────────────────────────

    private suspend fun applyRotation(candidates: List<SuggestedQuestion>): List<SuggestedQuestion> {
        val recent = runCatching { settingsRepository.getSuggestedChipHistory() }
            .getOrDefault(emptyMap())
        val cutoff = System.currentTimeMillis() - FRESHNESS_WINDOW_MS
        val (dynamic, stable) = candidates.partition { !it.isStable() }
        val (stale, freshStable) = stable.partition { (recent[it.id] ?: 0L) > cutoff }
        // Dinámicos siempre primero; del pool, primero los no vistos en 24 h.
        return dynamic + freshStable + stale
    }

    private suspend fun markShown(ids: List<String>) {
        runCatching {
            val now = System.currentTimeMillis()
            val cutoff = now - FRESHNESS_WINDOW_MS
            val history = settingsRepository.getSuggestedChipHistory()
                .filterValues { it > cutoff }
                .toMutableMap()
            // Solo el pool estable rota; los dinámicos viven mientras viva su señal.
            ids.filter { id -> STABLE_POOL.any { it.id == id } }.forEach { history[it] = now }
            settingsRepository.setSuggestedChipHistory(history)
        }
    }

    private fun SuggestedQuestion.isStable() = STABLE_POOL.any { it.id == id }

    // ── 3. Re-ranking LLM opcional (patrón ProactiveReasoner) ───────────────

    private suspend fun rerankWithLlmIfReady(candidates: List<SuggestedQuestion>): List<SuggestedQuestion> {
        val engine = llm ?: return candidates
        if (candidates.size < 3) return candidates
        // NO se espera la carga del LLM: los pills deben aparecer al instante.
        val ready = runCatching { engine.ensureReady() }.getOrNull()
        if (ready != LlmReadiness.Available) return candidates

        val prompt = buildString {
            appendLine("Ordena estos atajos de preguntas de finanzas del más al menos útil para el usuario hoy.")
            appendLine("Responde SOLO con los ids separados por coma, sin texto extra.")
            candidates.forEach { appendLine("${it.id} = ${it.label}") }
        }
        val raw = runCatching { engine.generate(prompt) }.getOrNull()?.getOrNull() ?: return candidates
        val byId = candidates.associateBy { it.id }
        // Validación estricta: solo ids existentes, sin duplicados; lo no
        // mencionado conserva su orden determinista al final.
        val picked = raw.split(',', '\n')
            .map { it.trim() }
            .mapNotNull { byId[it] }
            .distinctBy { it.id }
        if (picked.size < 2) return candidates
        return picked + candidates.filter { c -> picked.none { it.id == c.id } }
    }

    private companion object {
        const val FRESHNESS_WINDOW_MS = 24L * 60 * 60 * 1000

        /** Pool estable — los 5 chips históricos + el patrón inusual (OPEN_ANALYSIS). */
        val STABLE_POOL = listOf(
            SuggestedQuestion(
                id = "pool:unusual",
                label = "¿Algún patrón inusual?",
                openQuestion = "¿Qué patrón no sobresale a simple vista en mis gastos?",
            ),
            SuggestedQuestion(
                id = "pool:summary",
                label = "Resumen de la quincena",
                response = AssistantResponse(AssistantResponse.Intent.SUMMARIZE_QUINCENA, AssistantResponse.Args(), ""),
            ),
            SuggestedQuestion(
                id = "pool:topcat",
                label = "¿En qué gasto más?",
                response = AssistantResponse(AssistantResponse.Intent.GET_TOP_CATEGORY, AssistantResponse.Args(), ""),
            ),
            SuggestedQuestion(
                id = "pool:topspender",
                label = "¿Quién gasta más?",
                response = AssistantResponse(AssistantResponse.Intent.GET_TOP_SPENDER, AssistantResponse.Args(), ""),
            ),
            SuggestedQuestion(
                id = "pool:compare",
                label = "Comparar quincenas",
                response = AssistantResponse(AssistantResponse.Intent.COMPARE_QUINCENAS, AssistantResponse.Args(), ""),
            ),
            SuggestedQuestion(
                id = "pool:msi",
                label = "Cuotas pendientes",
                response = AssistantResponse(AssistantResponse.Intent.LIST_UPCOMING_INSTALLMENTS, AssistantResponse.Args(), ""),
            ),
        )
    }
}
