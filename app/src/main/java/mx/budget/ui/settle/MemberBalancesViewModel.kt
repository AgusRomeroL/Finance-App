package mx.budget.ui.settle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mx.budget.data.local.result.NettingAttributionRow
import mx.budget.data.repository.ExpenseRepository
import mx.budget.data.repository.MemberRepository
import kotlin.math.abs

/**
 * ViewModel de "Cuentas entre miembros" (netting automático).
 *
 * Computa de forma **determinista** cuánto se deben entre sí los miembros del
 * hogar a partir de las atribuciones (PAYER / BENEFICIARY) de los gastos POSTED
 * no liquidados. No mueve dinero: solo cancela deudas persona-a-persona.
 *
 * ## Fórmula
 * Para cada gasto de monto `A` con beneficiario `B` (consumió `ben`) y pagador
 * `P` (puso `pay`), `B` le debe a `P`:  `ben * pay / A`  (para todo `B != P`).
 * Se acumula en `debt[deudor][acreedor]`; el neto del par (X,Y) =
 * `debt[X][Y] - debt[Y][X]`.
 *
 * ## Liquidación
 * - **Por par**: marca `NETTED` los gastos cuyos participantes son EXACTAMENTE
 *   ese par (2-party puro), de modo que nunca borra la deuda de un tercero que
 *   comparta el mismo gasto (un `settlement_status` es por gasto, indivisible).
 * - **Global**: marca `NETTED` todos los gastos vigentes → deja a todos a mano.
 */
class MemberBalancesViewModel(
    private val expenseRepository: ExpenseRepository,
    memberRepository: MemberRepository,
    private val householdId: String,
) : ViewModel() {

    val uiState: StateFlow<MemberBalancesUiState> =
        combine(
            expenseRepository.observeNettingRows(householdId),
            memberRepository.observeAllMembers(householdId),
        ) { rows, members ->
            compute(rows, members.associate { it.id to it.displayName })
        }
            .catch { emit(MemberBalancesUiState(loading = false)) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                MemberBalancesUiState(loading = true),
            )

    /** Liquida (marca NETTED) los gastos 2-party puros del par indicado. */
    fun settlePair(pair: PairDebt) {
        if (pair.settleableExpenseIds.isEmpty()) return
        viewModelScope.launch { expenseRepository.markNetted(pair.settleableExpenseIds) }
    }

    /** Liquida absolutamente todos los gastos vigentes: deja a todos a mano. */
    fun settleAll(allExpenseIds: List<String>) {
        if (allExpenseIds.isEmpty()) return
        viewModelScope.launch { expenseRepository.markNetted(allExpenseIds) }
    }

    // ── Cómputo determinista ─────────────────────────────────────────────────

    private fun compute(
        rows: List<NettingAttributionRow>,
        names: Map<String, String>,
    ): MemberBalancesUiState {
        // Agrupa filas por gasto: monto, pagadores, beneficiarios, participantes.
        data class Group(
            val amount: Double,
            val payers: MutableMap<String, Double> = mutableMapOf(),
            val beneficiaries: MutableMap<String, Double> = mutableMapOf(),
            val participants: MutableSet<String> = mutableSetOf(),
        )

        val groups = mutableMapOf<String, Group>()
        for (r in rows) {
            val g = groups.getOrPut(r.expenseId) { Group(amount = r.amountMxn) }
            when (r.role) {
                "PAYER" -> g.payers.merge(r.memberId, r.shareAmountMxn, Double::plus)
                "BENEFICIARY" -> g.beneficiaries.merge(r.memberId, r.shareAmountMxn, Double::plus)
            }
            g.participants.add(r.memberId)
        }

        // Matriz de deuda dirigida: debt[(deudor, acreedor)] = MXN.
        val debt = mutableMapOf<Pair<String, String>, Double>()
        // Gastos 2-party puros por par (para liquidación por par sin dañar a terceros).
        val pureByPair = mutableMapOf<Set<String>, MutableList<String>>()
        val allExpenseIds = mutableSetOf<String>()

        for ((expenseId, g) in groups) {
            allExpenseIds.add(expenseId)
            val a = g.amount
            if (a <= 0.0) continue
            for ((b, ben) in g.beneficiaries) {
                for ((p, pay) in g.payers) {
                    if (b == p) continue
                    debt.merge(b to p, ben * pay / a, Double::plus)
                }
            }
            // Gasto puro entre 2 personas → se puede liquidar sin tocar a nadie más.
            if (g.participants.size == 2) {
                pureByPair.getOrPut(g.participants.toSet()) { mutableListOf() }.add(expenseId)
            }
        }

        // Netos por par (no dirigido) y por miembro.
        val seen = mutableSetOf<Set<String>>()
        val pairs = mutableListOf<PairDebt>()
        val memberNet = mutableMapOf<String, Double>()

        for ((key, _) in debt) {
            val (x, y) = key
            val unordered = setOf(x, y)
            if (unordered.size < 2 || !seen.add(unordered)) continue
            val xy = debt[x to y] ?: 0.0
            val yx = debt[y to x] ?: 0.0
            val net = xy - yx // >0 => x le debe a y
            if (abs(net) < 0.01) continue
            val (debtor, creditor, amount) =
                if (net > 0) Triple(x, y, net) else Triple(y, x, -net)
            memberNet.merge(debtor, -amount, Double::plus)
            memberNet.merge(creditor, amount, Double::plus)
            pairs += PairDebt(
                debtorId = debtor,
                debtorName = names[debtor] ?: "Miembro",
                creditorId = creditor,
                creditorName = names[creditor] ?: "Miembro",
                amount = amount,
                settleableExpenseIds = pureByPair[unordered].orEmpty(),
            )
        }

        val memberNets = memberNet
            .filter { abs(it.value) >= 0.01 }
            .map { (id, v) -> MemberNet(id, names[id] ?: "Miembro", v) }
            .sortedByDescending { it.net }

        return MemberBalancesUiState(
            pairs = pairs.sortedByDescending { it.amount },
            memberNets = memberNets,
            allExpenseIds = allExpenseIds.toList(),
            loading = false,
        )
    }
}

/** Deuda neta dirigida entre dos miembros. `amount` siempre > 0. */
data class PairDebt(
    val debtorId: String,
    val debtorName: String,
    val creditorId: String,
    val creditorName: String,
    val amount: Double,
    /**
     * Gastos 2-party puros entre este par: los únicos que se pueden marcar
     * `NETTED` desde el botón "Saldar" sin borrar la deuda de un tercero que
     * comparta el gasto. Vacío => solo liquidable vía "Saldar todo".
     */
    val settleableExpenseIds: List<String>,
) {
    /** true si el botón por par puede liquidar este neto por completo. */
    val fullySettleable: Boolean get() = settleableExpenseIds.isNotEmpty()
}

/** Saldo neto de un miembro. `net` > 0 = le deben; `net` < 0 = debe. */
data class MemberNet(
    val memberId: String,
    val name: String,
    val net: Double,
)

data class MemberBalancesUiState(
    val pairs: List<PairDebt> = emptyList(),
    val memberNets: List<MemberNet> = emptyList(),
    val allExpenseIds: List<String> = emptyList(),
    val loading: Boolean = true,
) {
    val isSettled: Boolean get() = !loading && pairs.isEmpty()
}
