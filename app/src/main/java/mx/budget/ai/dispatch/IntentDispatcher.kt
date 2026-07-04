package mx.budget.ai.dispatch

import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import mx.budget.ai.domain.AssistantResponse
import mx.budget.ai.domain.DispatchResult
import mx.budget.data.local.result.QuincenaSnapshot
import mx.budget.data.repository.AnalyticsRepository
import mx.budget.data.repository.ExpenseRepository
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

    suspend fun dispatch(rawResponse: String): DispatchResult {
        val repairedJson = JsonRepairer.repair(rawResponse)

        val response = try {
            jsonConfig.decodeFromString<AssistantResponse>(repairedJson)
        } catch (e: Exception) {
            return DispatchResult.ParseError(rawResponse)
        }

        return dispatch(response)
    }

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
                    val avg = history.map { it.actualExpensesMxn }.average()
                    val delta = quincena.actualExpensesMxn - avg
                    val dir = if (delta >= 0) "por ENCIMA" else "por DEBAJO"
                    DispatchResult.QuincenaComparison(
                        "Gasto actual %.2f vs promedio %.2f de las últimas ${history.size} cerradas: %.2f $dir del baseline."
                            .format(quincena.actualExpensesMxn, avg, kotlin.math.abs(delta)),
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
                DispatchResult.QuincenaSummary(
                    QuincenaSnapshot(
                        quincenaId = quincena.id,
                        label = quincena.label,
                        startDate = quincena.startDate,
                        endDate = quincena.endDate,
                        projectedIncomeMxn = quincena.projectedIncomeMxn,
                        projectedExpensesMxn = quincena.projectedExpensesMxn,
                        actualIncomeMxn = quincena.actualIncomeMxn,
                        actualExpensesMxn = quincena.actualExpensesMxn,
                        savingsMxn = quincena.actualIncomeMxn - quincena.actualExpensesMxn,
                    )
                )
            }

            AssistantResponse.Intent.UNKNOWN -> DispatchResult.Unknown(response.reason)
        }
    }

    /** Rango [inicio, fin] de la quincena como epoch millis (fechas ISO, TZ del hogar). */
    private fun quincenaEpochRange(startIso: String, endIso: String): Pair<Long, Long> {
        val from = LocalDate.parse(startIso).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = LocalDate.parse(endIso).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return from to to
    }
}
