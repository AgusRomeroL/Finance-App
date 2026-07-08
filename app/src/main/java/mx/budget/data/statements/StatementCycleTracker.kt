package mx.budget.data.statements

import mx.budget.data.local.entity.PaymentMethodEntity
import java.time.LocalDate

/** Estado de importación del estado de cuenta de un wallet en el ciclo vigente. */
enum class StatementCycleStatus {
    /** Ya hay un estado importado que cubre el último corte vencido. */
    IMPORTED,

    /** El corte ya venció (con gracia) y no hay estado importado que lo cubra. */
    PENDING,

    /** Wallet de tarjeta sin día de corte conocido (hasta el primer import). */
    NO_CUTOFF,
}

/** Estado por wallet, para el checklist mensual y el recordatorio. */
data class WalletStatementStatus(
    val walletId: String,
    val walletName: String,
    val cutoffDay: Int?,
    val status: StatementCycleStatus,
    /** Fecha del corte vencido que se espera tener importado (null si NO_CUTOFF). */
    val expectedCutoff: LocalDate?,
    /** Fin de periodo del último import aplicado a este wallet (null si nunca). */
    val lastImportPeriodEnd: LocalDate?,
)

/**
 * Lógica **pura** (sin Android/Room) que decide, por wallet de tarjeta, si su
 * estado de cuenta del ciclo vigente ya se importó o está pendiente. La comparten
 * el checklist "Estados del mes", la tarjeta del Dashboard y el
 * [StatementReminderWorker]. Es unit-testeable sin instrumentación.
 *
 * Modelo: para cada tarjeta con `cutoffDay`, el **corte esperado** `D` es el corte
 * más reciente que ya lleva al menos [GRACE_DAYS] días vencido (el PDF suele tardar
 * en estar disponible). Si el último import aplicado cubre `D` (su `periodo_fin`
 * llega a `D − TOLERANCE_DAYS`), está IMPORTED; si no, PENDING.
 */
object StatementCycleTracker {

    /** Días de gracia tras el corte antes de considerar pendiente (PDF tardío). */
    const val GRACE_DAYS = 2L

    /** Holgura por desfases entre el fin de periodo y el día de corte. */
    const val TOLERANCE_DAYS = 5L

    private val STATEMENT_KINDS = setOf(
        "CREDIT_CARD", "DEPARTMENT_STORE_CARD", "BNPL_INSTALLMENT", "DIGITAL_WALLET",
    )

    /** ¿Este wallet es una tarjeta que recibe estados de cuenta mensuales? */
    fun isStatementCard(kind: String): Boolean = kind in STATEMENT_KINDS

    fun compute(
        wallets: List<PaymentMethodEntity>,
        lastImportEndByWallet: Map<String, LocalDate?>,
        today: LocalDate,
    ): List<WalletStatementStatus> = wallets
        .filter { it.isActive && isStatementCard(it.kind) }
        .map { w ->
            val cutoff = w.cutoffDay
            val lastEnd = lastImportEndByWallet[w.id]
            if (cutoff == null || cutoff !in 1..31) {
                WalletStatementStatus(
                    walletId = w.id,
                    walletName = w.displayName,
                    cutoffDay = cutoff,
                    status = StatementCycleStatus.NO_CUTOFF,
                    expectedCutoff = null,
                    lastImportPeriodEnd = lastEnd,
                )
            } else {
                val expected = lastCutoffOnOrBefore(cutoff, today.minusDays(GRACE_DAYS))
                val covered = lastEnd != null &&
                    !lastEnd.isBefore(expected.minusDays(TOLERANCE_DAYS))
                WalletStatementStatus(
                    walletId = w.id,
                    walletName = w.displayName,
                    cutoffDay = cutoff,
                    status = if (covered) StatementCycleStatus.IMPORTED
                    else StatementCycleStatus.PENDING,
                    expectedCutoff = expected,
                    lastImportPeriodEnd = lastEnd,
                )
            }
        }

    /** Solo los wallets con estado pendiente de importar este ciclo. */
    fun pending(statuses: List<WalletStatementStatus>): List<WalletStatementStatus> =
        statuses.filter { it.status == StatementCycleStatus.PENDING }

    /**
     * Fecha del corte más reciente con día [cutoffDay] que sea ≤ [ref], con clamp al
     * último día del mes (para cortes 29-31 en meses cortos como febrero).
     */
    fun lastCutoffOnOrBefore(cutoffDay: Int, ref: LocalDate): LocalDate {
        val thisMonth = clampToMonth(ref.year, ref.monthValue, cutoffDay)
        if (!thisMonth.isAfter(ref)) return thisMonth
        val prev = ref.minusMonths(1)
        return clampToMonth(prev.year, prev.monthValue, cutoffDay)
    }

    private fun clampToMonth(year: Int, month: Int, day: Int): LocalDate {
        val last = LocalDate.of(year, month, 1).lengthOfMonth()
        return LocalDate.of(year, month, day.coerceIn(1, last))
    }
}
