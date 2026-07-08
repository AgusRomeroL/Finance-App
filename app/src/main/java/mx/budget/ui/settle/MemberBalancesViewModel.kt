package mx.budget.ui.settle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mx.budget.data.local.entity.LoanEntity
import mx.budget.data.local.result.PendingReimbursementExpense
import mx.budget.data.repository.ExpenseRepository
import mx.budget.data.repository.LoanRepository
import mx.budget.data.repository.MemberRepository

/**
 * ViewModel de "Cuentas entre miembros" — **deudas EXPLÍCITAS y opt-in** en dos
 * sentidos, por miembro (reemplaza el netting automático que sumaba TODAS las
 * atribuciones y producía montos gigantes).
 *
 * ## Dos sentidos, ambos visibles (sin cancelación automática)
 * - **Por pagar (el hogar debe):** gastos que un tercero adelantó y el hogar aún
 *   le repondrá — `expense.settlement_status = 'PENDING_REIMBURSEMENT'`, agrupados
 *   por `external_payer_member_id`. Ej.: David paga el cine del hogar → el hogar le
 *   debe. Acción **"Marcar como pagado"** → `settlement_status = 'REIMBURSED'`
 *   ([ExpenseRepository.markReimbursed]); NO mueve saldos (la reposición es en
 *   efectivo, fuera del ledger).
 * - **Por cobrar (le deben al hogar):** préstamos ([LoanEntity]) con saldo
 *   pendiente, agrupados por deudor. Ej.: Agustín usó la tarjeta de Norma a meses.
 *   Acción **"Abonar"** → [LoanRepository.applyPayment] (reutiliza el flujo de loans).
 *
 * Un mismo miembro puede aparecer con deuda en **ambos** sentidos: se muestran las
 * dos cifras lado a lado, **sin** netearlas (decisión explícita del producto).
 *
 * No hay cálculo automático de "quién debe a quién" a partir de atribuciones.
 */
class MemberBalancesViewModel(
    private val expenseRepository: ExpenseRepository,
    private val loanRepository: LoanRepository,
    memberRepository: MemberRepository,
    private val householdId: String,
) : ViewModel() {

    val uiState: StateFlow<MemberBalancesUiState> =
        combine(
            expenseRepository.observePendingReimbursementExpenses(householdId),
            loanRepository.observeAll(householdId),
            memberRepository.observeAllMembers(householdId),
        ) { reimbursements, loans, members ->
            build(reimbursements, loans, members.associate { it.id to it.displayName })
        }
            .catch { emit(MemberBalancesUiState(loading = false)) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                MemberBalancesUiState(loading = true),
            )

    /** Marca un gasto adelantado por un tercero como reembolsado (el hogar ya pagó). */
    fun markReimbursed(expenseId: String) {
        viewModelScope.launch { expenseRepository.markReimbursed(expenseId) }
    }

    /** Registra un abono a un préstamo por cobrar (reutiliza el flujo de loans). */
    fun applyLoanPayment(loanId: String, amount: Double) {
        if (amount <= 0.0) return
        viewModelScope.launch { loanRepository.applyPayment(loanId, amount) }
    }

    // ── Construcción de la vista por miembro ─────────────────────────────────────

    private fun build(
        reimbursements: List<PendingReimbursementExpense>,
        loans: List<LoanEntity>,
        names: Map<String, String>,
    ): MemberBalancesUiState {
        // Por pagar: agrupa los gastos adelantados por el tercero que los puso.
        // TODO(multi-family): `external_payer_member_id` apunta a un miembro que en
        // el futuro podría vivir en otra familia; agrupar por ese id ya deja la
        // puerta abierta a sincronizar estos movimientos entre hogares/PWA.
        val payableByMember: Map<String, List<PendingReimbursementExpense>> =
            reimbursements
                .filter { it.externalPayerMemberId != null }
                .groupBy { it.externalPayerMemberId!! }

        // Por cobrar: solo préstamos con saldo vivo, agrupados por deudor.
        val receivableByMember: Map<String, List<LoanEntity>> =
            loans
                .filter { it.remainingBalanceMxn > 0.0 }
                .groupBy { it.debtorMemberId }

        val memberIds = (payableByMember.keys + receivableByMember.keys)

        val rows = memberIds.map { id ->
            val payables = payableByMember[id].orEmpty()
            val receivables = receivableByMember[id].orEmpty()
            MemberDebtRow(
                memberId = id,
                name = names[id] ?: "Miembro",
                // El hogar le debe a este miembro (adelantó gastos del hogar).
                payableTotal = payables.sumOf { it.amountMxn },
                payables = payables.map {
                    PayableExpense(
                        expenseId = it.expenseId,
                        concept = it.concept,
                        occurredAt = it.occurredAt,
                        amount = it.amountMxn,
                    )
                }.sortedByDescending { it.occurredAt },
                // Este miembro le debe al hogar (préstamos pendientes).
                receivableTotal = receivables.sumOf { it.remainingBalanceMxn },
                receivables = receivables.sortedByDescending { it.remainingBalanceMxn },
            )
        }.sortedByDescending { maxOf(it.payableTotal, it.receivableTotal) }

        return MemberBalancesUiState(rows = rows, loading = false)
    }
}

/**
 * Un gasto que un tercero adelantó y el hogar aún le debe (deuda *por pagar*).
 * `occurredAt` en epoch millis.
 */
data class PayableExpense(
    val expenseId: String,
    val concept: String,
    val occurredAt: Long,
    val amount: Double,
)

/**
 * Deudas explícitas de un miembro en ambos sentidos. Un miembro puede tener las
 * dos: se muestran sin netear.
 *
 * @property payableTotal    Cuánto le debe el hogar (suma de gastos adelantados).
 * @property payables        Desglose de esos gastos.
 * @property receivableTotal Cuánto le debe él al hogar (saldo vivo de préstamos).
 * @property receivables     Préstamos que lo componen.
 */
data class MemberDebtRow(
    val memberId: String,
    val name: String,
    val payableTotal: Double,
    val payables: List<PayableExpense>,
    val receivableTotal: Double,
    val receivables: List<LoanEntity>,
) {
    val hasPayable: Boolean get() = payableTotal > 0.0
    val hasReceivable: Boolean get() = receivableTotal > 0.0
}

data class MemberBalancesUiState(
    val rows: List<MemberDebtRow> = emptyList(),
    val loading: Boolean = true,
) {
    val isEmpty: Boolean get() = !loading && rows.isEmpty()
}
