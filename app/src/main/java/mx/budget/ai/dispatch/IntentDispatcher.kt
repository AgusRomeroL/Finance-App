package mx.budget.ai.dispatch

import kotlinx.serialization.json.Json
import mx.budget.ai.domain.AssistantResponse
import mx.budget.ai.domain.DispatchResult

/**
 * Recibe el JSON validado de la IA e invoca la parte de la app que hace el trabajo
 * real, construyendo el DispatchResult a retornar en la vista.
 */
class IntentDispatcher(
    private val resolver: AliasResolver
) {

    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun dispatch(rawResponse: String): DispatchResult {
        val repairedJson = JsonRepairer.repair(rawResponse)
        
        val response = try {
            jsonConfig.decodeFromString<AssistantResponse>(repairedJson)
        } catch (e: Exception) {
            return DispatchResult.ParseError(rawResponse)
        }

        return when (response.intent) {
            AssistantResponse.Intent.GET_CATEGORY_REMAINING -> {
                val cat = resolver.resolveCategory(response.args.category_code)
                    ?: return DispatchResult.MissingArg("category_code")
                // Simplified result for demonstration setup. Normally hooks to AnalyticsRepo.
                DispatchResult.CategoryRemaining(cat, projected = 0.0, actual = 0.0, remaining = 0.0)
            }
            AssistantResponse.Intent.GET_TOP_SPENDER -> {
                // Normally fetched from repository
                DispatchResult.Unknown("Falta implementación repositorio")
            }
            AssistantResponse.Intent.GET_SPEND_BY_MEMBER -> {
                val mem = resolver.resolveMember(response.args.member_alias)
                    ?: return DispatchResult.MissingArg("member_alias")
                DispatchResult.SpendByMemberResult(mem, totalMxn = 0.0, byCategory = emptyList())
            }
            AssistantResponse.Intent.GET_WALLET_BALANCE -> {
                val wal = resolver.resolveWallet(response.args.wallet_name)
                    ?: return DispatchResult.MissingArg("wallet_name")
                DispatchResult.WalletBalance(wal, balance = 0.0)
            }
            AssistantResponse.Intent.PROJECT_SAVINGS_IF -> {
                val cat = resolver.resolveCategory(response.args.hypothetical_cut_category)
                    ?: return DispatchResult.MissingArg("hypothetical_cut_category")
                DispatchResult.SavingsProjection(cat, deltaMxn = 0.0, assumptions = emptyList())
            }
            AssistantResponse.Intent.COMPARE_QUINCENAS -> {
                DispatchResult.QuincenaComparison("Comparativa no disponible", baselineQuincenas = response.args.baseline_quincenas ?: 6)
            }
            AssistantResponse.Intent.GET_INSTALLMENT_STATUS -> {
                val plan = resolver.resolveInstallmentPlan(response.args.plan_name)
                    ?: return DispatchResult.MissingArg("plan_name")
                DispatchResult.InstallmentStatus(plan, summary = null)
            }
            AssistantResponse.Intent.LIST_UPCOMING_INSTALLMENTS -> {
                DispatchResult.UpcomingInstallments(emptyList())
            }
            AssistantResponse.Intent.EXPLAIN_VARIANCE -> {
                val cat = resolver.resolveCategory(response.args.category_code)
                    ?: return DispatchResult.MissingArg("category_code")
                DispatchResult.VarianceExplanation(cat, "No hay varianza") 
            }
            AssistantResponse.Intent.SUMMARIZE_QUINCENA -> {
                DispatchResult.Unknown("Requires QuincenaSnapshot")
            }
            AssistantResponse.Intent.UNKNOWN -> {
                DispatchResult.Unknown(response.reason)
            }
        }
    }
}
