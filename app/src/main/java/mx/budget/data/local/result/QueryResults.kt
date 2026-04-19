package mx.budget.data.local.result

/**
 * POJOs para resultados de queries complejas con JOINs.
 *
 * Room no puede mapear directamente JOINs a entidades con FK,
 * así que usamos data classes planas como contenedores de resultado.
 * Estos NO son entidades — son proyecciones de lectura.
 */

/**
 * Gasto con datos resolucionales (categoría, wallet, quincena).
 * Resultado de JOIN expense + category + payment_method.
 */
data class ExpenseWithDetails(
    val expenseId: String,
    val concept: String,
    val amountMxn: Double,
    val occurredAt: Long,
    val status: String,
    val categoryName: String,
    val categoryCode: String,
    val categoryColorHex: String?,
    val paymentMethodName: String,
    val paymentMethodKind: String,
    val quincenaLabel: String,
    val installmentNumber: Int?,
    val installmentTotal: Int?,
    val notes: String?
)

/**
 * Gasto por categoría: proyectado vs ejecutado con restante.
 * Alimenta el dashboard quincenal y el contexto RAG del asistente IA.
 */
data class SpendByCategory(
    val categoryId: String,
    val categoryName: String,
    val categoryCode: String,
    val colorHex: String?,
    val projected: Double,
    val actual: Double,
    val remaining: Double,
    val pctExec: Int
)

/**
 * Gasto acumulado por miembro con rol BENEFICIARY.
 * Alimenta el módulo analítico B (gasto proporcional por miembro)
 * y el intent GET_SPEND_BY_MEMBER del asistente IA.
 */
data class SpendByMember(
    val memberId: String,
    val memberName: String,
    val totalMxn: Double,
    val expenseCount: Int
)

/**
 * Desglose de gasto de un miembro por categoría.
 */
data class MemberSpendByCategory(
    val memberId: String,
    val memberName: String,
    val categoryId: String,
    val categoryName: String,
    val totalMxn: Double,
    val expenseCount: Int
)

/**
 * Snapshot de una quincena para analíticas históricas.
 * Alimenta el módulo D (varianza) y el pronóstico de liquidez.
 */
data class QuincenaSnapshot(
    val quincenaId: String,
    val label: String,
    val startDate: String,
    val endDate: String,
    val projectedIncomeMxn: Double,
    val projectedExpensesMxn: Double,
    val actualIncomeMxn: Double,
    val actualExpensesMxn: Double,
    val savingsMxn: Double
)

/**
 * Saldo y utilización de un método de pago.
 * Alimenta la pantalla WalletsScreen y el tile WalletBalanceTile.
 */
data class WalletBalanceInfo(
    val paymentMethodId: String,
    val displayName: String,
    val kind: String,
    val balance: Double,
    val creditLimit: Double?,
    val utilizationPct: Double?
)

/**
 * Resumen de un plan de cuotas activo.
 */
data class InstallmentSummary(
    val planId: String,
    val displayName: String,
    val currentInstallment: Int,
    val totalInstallments: Int,
    val installmentAmountMxn: Double,
    val nextDate: String?,
    val remainingMxn: Double
)

/**
 * Top gastos de una quincena, ordenados por monto descendente.
 */
data class TopExpense(
    val expenseId: String,
    val date: String,
    val concept: String,
    val amountMxn: Double,
    val categoryName: String,
    val walletName: String
)

/**
 * Intereses pagados agrupados por método de pago.
 * Alimenta el módulo analítico C (detección de intereses).
 */
data class InterestByWallet(
    val paymentMethodId: String,
    val paymentMethodName: String,
    val kind: String,
    val interestPaidMxn: Double,
    val totalPaidMxn: Double,
    val interestPct: Double
)

/**
 * Ingreso por miembro para una quincena específica.
 */
data class IncomeByMember(
    val memberId: String,
    val memberName: String,
    val totalMxn: Double,
    val status: String
)
