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

    /** "¿En qué gasto más?" — categorías con más gasto real en la quincena activa. */
    data class TopCategories(
        val categories: List<SpendByCategory>,
        val totalMxn: Double
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

    /**
     * Respuesta redactada por la ruta OPEN_ANALYSIS (segunda pasada sobre un
     * digest determinista del estado financiero). [deterministic] = true si la
     * generó el motor de plantillas local (sin LLM); false si la redactó el
     * LLM on-device. La UI la marca con un badge "Análisis IA"/"Análisis local".
     */
    data class OpenAnalysis(
        val text: String,
        val deterministic: Boolean
    ) : DispatchResult()

    data class Unknown(
        val reason: String
    ) : DispatchResult()

    /**
     * La pregunta cae fuera del dominio financiero (saludo, charla, etc.). El
     * modelo la clasificó bien como UNKNOWN; la UI responde con una guía
     * proactiva de capacidades en vez de un error.
     */
    data object OutOfScope : DispatchResult()

    data class ParseError(
        val rawJson: String
    ) : DispatchResult()

    data class MissingArg(
        val argumentName: String
    ) : DispatchResult()
}
