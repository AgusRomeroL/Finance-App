package mx.budget.ai.domain

import kotlinx.serialization.Serializable

/**
 * Esquema de respuesta JSON esperado de Gemini Nano.
 */
@Serializable
data class AssistantResponse(
    val intent: Intent,
    val args: Args,
    val reason: String
) {
    @Serializable
    enum class Intent {
        GET_CATEGORY_REMAINING,
        GET_TOP_SPENDER,
        GET_SPEND_BY_MEMBER,
        GET_WALLET_BALANCE,
        GET_INSTALLMENT_STATUS,
        PROJECT_SAVINGS_IF,
        COMPARE_QUINCENAS,
        EXPLAIN_VARIANCE,
        LIST_UPCOMING_INSTALLMENTS,
        SUMMARIZE_QUINCENA,
        UNKNOWN
    }

    @Serializable
    data class Args(
        val category_code: String? = null,
        val member_alias: String? = null,
        val wallet_name: String? = null,
        val plan_name: String? = null,
        val hypothetical_cut_category: String? = null,
        val baseline_quincenas: Int? = null,
        val from_date: String? = null,
        val to_date: String? = null
    )
}
