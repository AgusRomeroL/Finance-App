package mx.budget.ai.domain

import mx.budget.data.local.entity.CategoryEntity
import mx.budget.data.local.entity.InstallmentPlanEntity
import mx.budget.data.local.entity.MemberEntity
import mx.budget.data.local.entity.PaymentMethodEntity
import mx.budget.data.local.result.ExpenseWithDetails
import mx.budget.data.local.result.InstallmentSummary
import mx.budget.data.local.result.MemberSpendByCategory
import mx.budget.data.local.result.QuincenaSnapshot
import mx.budget.data.local.result.SpendByCategory
import mx.budget.data.local.result.SpendByMember

/**
 * Jerarquía de resultados producidos por el dispatcher.
 * Estos objetos encapsulan respuestas deterministas extraídas de la base de datos
 * para ser renderizadas por la UI.
 */
sealed class DispatchResult {
    data class CategoryRemaining(
        val category: CategoryEntity,
        val projected: Double,
        val actual: Double,
        val remaining: Double
    ) : DispatchResult()

    data class TopSpender(
        val member: MemberEntity,
        val totalMxn: Double,
        val share: Double
    ) : DispatchResult()

    data class SpendByMemberResult(
        val member: MemberEntity,
        val totalMxn: Double,
        val byCategory: List<MemberSpendByCategory>
    ) : DispatchResult()

    data class WalletBalance(
        val wallet: PaymentMethodEntity,
        val balance: Double
    ) : DispatchResult()

    data class InstallmentStatus(
        val plan: InstallmentPlanEntity,
        val summary: InstallmentSummary?
    ) : DispatchResult()

    data class SavingsProjection(
        val hypotheticalCutCategory: CategoryEntity,
        val deltaMxn: Double,
        val assumptions: List<String>
    ) : DispatchResult()

    // Para VarianceComparePanel
    data class QuincenaComparison(
        val comparisonText: String, // Simplified for this phase
        val baselineQuincenas: Int
    ) : DispatchResult()

    // Para VarianceExplainPanel
    data class VarianceExplanation(
        val category: CategoryEntity,
        val explanation: String // Simplified
    ) : DispatchResult()

    data class UpcomingInstallments(
        val installments: List<InstallmentSummary>
    ) : DispatchResult()

    data class QuincenaSummary(
        val summary: QuincenaSnapshot
    ) : DispatchResult()

    data class Unknown(
        val reason: String
    ) : DispatchResult()

    data class ParseError(
        val rawJson: String
    ) : DispatchResult()

    data class MissingArg(
        val argumentName: String
    ) : DispatchResult()
}
