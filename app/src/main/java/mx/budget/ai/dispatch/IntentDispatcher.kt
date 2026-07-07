package mx.budget.ai.dispatch

import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import mx.budget.ai.domain.AssistantResponse
import mx.budget.ai.domain.DispatchResult
import mx.budget.data.local.result.QuincenaSnapshot
import mx.budget.data.repository.AnalyticsRepository
import mx.budget.data.repository.ExpenseRepository
import mx.budget.data.repository.IncomeRepository
import mx.budget.data.repository.InstallmentRepository
import mx.budget.data.repository.MemberRepository
import mx.budget.data.repository.QuincenaRepository
import mx.budget.data.repository.WalletRepository
import java.time.LocalDate
import java.time.ZoneId

/**
 * Recibe el JSON validado de la IA e invoca la parte de la app que hace el
 * trabajo real, construyendo el DispatchResult a retornar en la vista.
 *
 * MVP Fase 4: los 10 handlers consultan los repositorios Room reales
 * (determinista, sin LLM en esta capa). [resolverProvider] entrega un
 * [AliasResolver] fresco (miembros/categorías/wallets/planes actuales) en cada
 * dispatch — así los alias siempre reflejan el estado vivo de la DB.
 */
class IntentDispatcher(
    private val resolverProvider: suspend () -> AliasResolver,
    private val analyticsRepository: AnalyticsRepository,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val walletRepository: WalletRepository,
    private val installmentRepository: InstallmentRepository,
    private val quincenaRepository: QuincenaRepository,
    private val memberRepository: MemberRepository,
    private val householdId: String,
) {

    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** Zona horaria canónica del hogar (spec). */
    private val zone = ZoneId.of("America/Mexico_City")

    /**
     * Despacha la salida CRUDA del LLM. Tres niveles de tolerancia:
     *  1. Decode estricto del JSON reparado.
     *  2. Parseo manual laxo (intent en minúsculas/con espacios, args sueltos).
     *  3. Fallback heurístico sobre [originalQuestion] (sin LLM) — el chat
     *     siempre intenta responder con datos reales antes de rendirse.
     */
    suspend fun dispatch(rawResponse: String, originalQuestion: String? = null): DispatchResult {
        val repairedJson = JsonRepairer.repair(rawResponse)

        val response = runCatching {
            jsonConfig.decodeFromString<AssistantResponse>(repairedJson)
        }.getOrNull()
            ?: parseTolerant(repairedJson)
            ?: originalQuestion?.let { q ->
                android.util.Log.w(TAG, "Salida LLM no parseable, fallback heurístico. Raw: $rawResponse")
                HeuristicIntentGuesser.guess(q, resolverProvider())
            }
            ?: return DispatchResult.ParseError(rawResponse)

        return dispatch(response)
    }

    /**
     * Parseo laxo para salidas "casi válidas" de modelos chicos: intent con
     * mayúsculas/espacios/guiones inconsistentes, args con tipos primitivos
     * variados, campos faltantes. Devuelve null si ni siquiera es un objeto
     * JSON con un intent reconocible.
     */
    private fun parseTolerant(json: String): AssistantResponse? = runCatching {
        val obj = jsonConfig.parseToJsonElement(json).jsonObject
        val intentRaw = (obj["intent"] as? JsonPrimitive)?.contentOrNull ?: return null
        val normalized = intentRaw.trim().uppercase().replace(Regex("[\\s-]+"), "_")
        val intent = AssistantResponse.Intent.entries.firstOrNull { it.name == normalized }
            ?: return null

        val argsObj = obj["args"] as? JsonObject
        fun str(key: String): String? = (argsObj?.get(key) as? JsonPrimitive)?.contentOrNull

        AssistantResponse(
            intent = intent,
            args = AssistantResponse.Args(
                category_code = str("category_code"),
                member_alias = str("member_alias"),
                wallet_name = str("wallet_name"),
                plan_name = str("plan_name"),
                hypothetical_cut_category = str("hypothetical_cut_category"),
                baseline_quincenas = (argsObj?.get("baseline_quincenas") as? JsonPrimitive)?.intOrNull,
                from_date = str("from_date"),
                to_date = str("to_date"),
            ),
            reason = (obj["reason"] as? JsonPrimitive)?.contentOrNull ?: "",
        )
    }.getOrNull()

    /**
     * Despacha una respuesta YA estructurada (la usan los chips deterministas
     * de la UI cuando no hay LLM disponible — bypass del parseo).
     */
    suspend fun dispatch(response: AssistantResponse): DispatchResult {
        val resolver = resolverProvider()
        val quincena = quincenaRepository.getActive(householdId)
            ?: return DispatchResult.Unknown("No hay quincena activa")

        return when (response.intent) {
            AssistantResponse.Intent.GET_CATEGORY_REMAINING -> {
                val cat = resolver.resolveCategory(response.args.category_code)
                    ?: return DispatchResult.MissingArg("category_code")
                val row = analyticsRepository.getSpendByCategory(householdId, quincena.id)
                    .firstOrNull { it.categoryId == cat.id }
                DispatchResult.CategoryRemaining(
                    category = cat,
                    projected = row?.projected ?: (cat.budgetDefaultMxn ?: 0.0),
                    actual = row?.actual ?: 0.0,
                    remaining = row?.remaining ?: (cat.budgetDefaultMxn ?: 0.0),
                )
            }

            AssistantResponse.Intent.GET_TOP_CATEGORY -> {
                val rows = analyticsRepository.getSpendByCategory(householdId, quincena.id)
                    .filter { it.actual > 0 }
                    .sortedByDescending { it.actual }
                if (rows.isEmpty()) DispatchResult.Unknown("Sin gastos en la quincena activa")
                else DispatchResult.TopCategories(
                    categories = rows.take(3),
                    totalMxn = rows.sumOf { it.actual },
                )
            }

            AssistantResponse.Intent.GET_TOP_SPENDER -> {
                val spend = expenseRepository.observeSpendByMember(quincena.id).first()
                val top = spend.maxByOrNull { it.totalMxn }
                    ?: return DispatchResult.Unknown("Sin gastos atribuidos en la quincena")
                val member = memberRepository.getById(top.memberId)
                    ?: return DispatchResult.Unknown("Miembro no encontrado")
                val total = spend.sumOf { it.totalMxn }
                DispatchResult.TopSpender(
                    member = member,
                    totalMxn = top.totalMxn,
                    share = if (total > 0) top.totalMxn / total else 0.0,
                )
            }

            AssistantResponse.Intent.GET_SPEND_BY_MEMBER -> {
                val mem = resolver.resolveMember(response.args.member_alias)
                    ?: return DispatchResult.MissingArg("member_alias")
                val spend = expenseRepository.getSpendForMember(quincena.id, mem.id)
                val (from, to) = quincenaEpochRange(quincena.startDate, quincena.endDate)
                val byCategory = expenseRepository.getSpendByMemberAndCategory(from, to)
                    .filter { it.memberId == mem.id }
                DispatchResult.SpendByMemberResult(
                    member = mem,
                    totalMxn = spend?.totalMxn ?: 0.0,
                    byCategory = byCategory,
                )
            }

            AssistantResponse.Intent.GET_WALLET_BALANCE -> {
                val wal = resolver.resolveWallet(response.args.wallet_name)
                    ?: return DispatchResult.MissingArg("wallet_name")
                val fresh = walletRepository.getById(wal.id) ?: wal
                DispatchResult.WalletBalance(wallet = fresh, balance = fresh.currentBalanceMxn)
            }

            AssistantResponse.Intent.PROJECT_SAVINGS_IF -> {
                val cat = resolver.resolveCategory(response.args.hypothetical_cut_category)
                    ?: return DispatchResult.MissingArg("hypothetical_cut_category")
                val row = analyticsRepository.getSpendByCategory(householdId, quincena.id)
                    .firstOrNull { it.categoryId == cat.id }
                DispatchResult.SavingsProjection(
                    hypotheticalCutCategory = cat,
                    deltaMxn = row?.actual ?: 0.0,
                    assumptions = listOf(
                        "Corte del 100 % del gasto de la quincena activa",
                        "No considera gastos PLANNED pendientes",
                    ),
                )
            }

            AssistantResponse.Intent.COMPARE_QUINCENAS -> {
                val n = response.args.baseline_quincenas ?: 6
                val history = quincenaRepository.getClosedSnapshots(householdId, n)
                if (history.isEmpty()) {
                    DispatchResult.QuincenaComparison("Aún no hay quincenas cerradas para comparar.", n)
                } else {
                    // Gasto de la quincena ACTIVA en vivo desde el ledger (las columnas
                    // agregadas de quincena no se mantienen; llegarían en 0). El baseline
                    // usa los snapshots cerrados, que sí traen su total consolidado.
                    val currentExpenses = expenseRepository.observePostedTotal(quincena.id).first()
                    val avg = history.map { it.actualExpensesMxn }.average()
                    val delta = currentExpenses - avg
                    val dir = if (delta >= 0) "por ENCIMA" else "por DEBAJO"
                    DispatchResult.QuincenaComparison(
                        "Gasto actual %.2f vs promedio %.2f de las últimas ${history.size} cerradas: %.2f $dir del baseline."
                            .format(currentExpenses, avg, kotlin.math.abs(delta)),
                        n,
                    )
                }
            }

            AssistantResponse.Intent.GET_INSTALLMENT_STATUS -> {
                val plan = resolver.resolveInstallmentPlan(response.args.plan_name)
                    ?: return DispatchResult.MissingArg("plan_name")
                val summary = installmentRepository.observeActiveSummaries(householdId).first()
                    .firstOrNull { it.planId == plan.id }
                DispatchResult.InstallmentStatus(plan = plan, summary = summary)
            }

            AssistantResponse.Intent.LIST_UPCOMING_INSTALLMENTS -> {
                DispatchResult.UpcomingInstallments(
                    installmentRepository.observeActiveSummaries(householdId).first()
                )
            }

            AssistantResponse.Intent.EXPLAIN_VARIANCE -> {
                val cat = resolver.resolveCategory(response.args.category_code)
                    ?: return DispatchResult.MissingArg("category_code")
                val current = analyticsRepository.getSpendByCategory(householdId, quincena.id)
                    .firstOrNull { it.categoryId == cat.id }?.actual ?: 0.0
                val baseline = analyticsRepository.getAvgSpendByCategoryHistorical(
                    householdId, sinceDate = "2000-01-01", nQuincenas = 6,
                ).firstOrNull { it.categoryId == cat.id }?.actual ?: 0.0
                val explanation = when {
                    baseline <= 0.0 -> "Sin histórico suficiente para ${cat.displayName}; gasto actual %.2f.".format(current)
                    current > baseline -> "${cat.displayName} va %.2f, un %.0f %% ARRIBA de su promedio histórico (%.2f)."
                        .format(current, 100 * (current - baseline) / baseline, baseline)
                    else -> "${cat.displayName} va %.2f, un %.0f %% ABAJO de su promedio histórico (%.2f)."
                        .format(current, 100 * (baseline - current) / baseline, baseline)
                }
                DispatchResult.VarianceExplanation(cat, explanation)
            }

            AssistantResponse.Intent.SUMMARIZE_QUINCENA -> {
                // Los "ejecutados" se leen EN VIVO del ledger (igual que el dashboard),
                // no de las columnas agregadas de la quincena — que no se mantienen y
                // llegaban en 0, haciendo que el resumen reportara "$0 gastado" con el
                // KPI mostrando el gasto real.
                val actualExpenses = expenseRepository.observePostedTotal(quincena.id).first()
                val actualIncome = incomeRepository.observePostedTotal(quincena.id).first()
                DispatchResult.QuincenaSummary(
                    QuincenaSnapshot(
                        quincenaId = quincena.id,
                        label = quincena.label,
                        startDate = quincena.startDate,
                        endDate = quincena.endDate,
                        projectedIncomeMxn = quincena.projectedIncomeMxn,
                        projectedExpensesMxn = quincena.projectedExpensesMxn,
                        actualIncomeMxn = actualIncome,
                        actualExpensesMxn = actualExpenses,
                        savingsMxn = actualIncome - actualExpenses,
                    )
                )
            }

            AssistantResponse.Intent.UNKNOWN -> DispatchResult.OutOfScope
        }
    }

    /** Rango [inicio, fin] de la quincena como epoch millis (fechas ISO, TZ del hogar). */
    private fun quincenaEpochRange(startIso: String, endIso: String): Pair<Long, Long> {
        val from = LocalDate.parse(startIso).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = LocalDate.parse(endIso).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return from to to
    }

    private companion object {
        const val TAG = "IntentDispatcher"
    }
}
