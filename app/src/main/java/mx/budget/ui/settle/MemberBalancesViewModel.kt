package mx.budget.ui.settle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mx.budget.data.local.entity.LoanEntity
import mx.budget.data.local.entity.MemberEntity
import mx.budget.data.local.entity.QuincenaEntity
import mx.budget.data.local.result.SpendByMember
import mx.budget.data.local.result.PendingReimbursementExpense
import mx.budget.data.repository.ExpenseRepository
import mx.budget.data.repository.LoanRepository
import mx.budget.data.repository.MemberRepository
import mx.budget.data.repository.QuincenaRepository
import mx.budget.ui.common.MemberPeriod
import mx.budget.ui.common.memberPeriodRangeMs

/**
 * ViewModel de "Cuentas entre miembros": **deudas EXPLÍCITAS y opt-in** en dos
 * sentidos, por miembro (reemplaza el netting automático que sumaba TODAS las
 * atribuciones y producía montos gigantes).
 *
 * ## Dos sentidos, ambos visibles (sin cancelación automática)
 * - **Por pagar (el hogar debe):** gastos que un tercero adelantó y el hogar aún
 *   le repondrá, con `expense.settlement_status = 'PENDING_REIMBURSEMENT'`, agrupados
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
 * ## Tercera seccion: balance entre adultos (orientativo)
 *
 * Se reintroduce el calculo automatico, pero con un diseno que evita las tres
 * causas por las que se retiro:
 *
 * 1. **Ventana obligatoria.** Se acota siempre a un periodo, con la quincena
 *    activa por defecto. La consulta anterior no tenia filtro de fecha y barria
 *    dieciocho meses de historia.
 * 2. **Solo entre adultos pagadores.** Los dependientes consumen sin deber nada:
 *    es el proposito del hogar, no una deuda. El grafo anterior hacia que un hijo
 *    "debiera" medio millon de pesos por vivir en casa.
 * 3. **Cuota justa por ingreso, no consumo bruto.** Comparar lo que cada adulto
 *    pago contra lo que cada adulto consumio convertia en deuda el simple hecho
 *    de que uno pasara la tarjeta. Aqui la referencia es la parte del gasto del
 *    hogar que le toca a cada uno segun su ingreso declarado.
 *
 * Es **informativo y de solo lectura**: sugiere el ajuste, no mueve nada. El
 * diseno anterior tenia un "liquidar todo" que marcaba cientos de gastos de una
 * vez, y ese andamiaje se retiro junto con el valor `NETTED`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemberBalancesViewModel(
    private val expenseRepository: ExpenseRepository,
    private val loanRepository: LoanRepository,
    private val memberRepository: MemberRepository,
    quincenaRepository: QuincenaRepository,
    private val householdId: String,
) : ViewModel() {

    private val _period = MutableStateFlow(MemberPeriod.QUINCENAL)

    /** Periodo del balance entre adultos. Arranca en la quincena activa. */
    val period: StateFlow<MemberPeriod> = _period.asStateFlow()

    private val activeQuincena: StateFlow<QuincenaEntity?> =
        quincenaRepository.observeActive(householdId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Balance entre adultos del periodo elegido. Null mientras no hay quincena
     * activa y el periodo es quincenal.
     */
    val adultBalance: StateFlow<AdultBalance?> =
        combine(_period, activeQuincena) { p, q -> p to q }
            .flatMapLatest { (p, q) ->
                val range = memberPeriodRangeMs(p, q)
                if (range == null) flowOf(null) else combine(
                    expenseRepository.observePaidByAdultInRange(householdId, range.first, range.second),
                    memberRepository.observeAllMembers(householdId),
                ) { paid, members -> buildAdultBalance(paid, members, p) }
            }
            .catch { emit(null) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setPeriod(p: MemberPeriod) { _period.value = p }

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

    // ── Balance entre adultos ────────────────────────────────────────────────────

    /**
     * Reparte el gasto corriente del periodo entre los adultos pagadores segun su
     * ingreso declarado y compara esa cuota con lo que cada uno puso.
     *
     * Con Norma en 60,000 y Benjamin en 45,000 la cuota es 57 y 43 por ciento. Si
     * algun adulto no declara ingreso, la ponderacion cae a partes iguales y la
     * pantalla lo avisa: repartir por un ingreso que no esta capturado seria
     * inventarse la referencia.
     *
     * Los netos suman cero por construccion, porque el total repartido es
     * exactamente lo que los adultos pusieron.
     */
    private fun buildAdultBalance(
        paid: List<SpendByMember>,
        members: List<MemberEntity>,
        period: MemberPeriod,
    ): AdultBalance {
        val adults = members.filter { it.role == "PAYER_ADULT" }
        val paidById = paid.associateBy { it.memberId }
        val total = paid.sumOf { it.totalMxn }

        val incomes = adults.associate { it.id to (it.defaultIncomeMxn ?: 0.0) }
        val incomeSum = incomes.values.sum()
        val byIncome = incomeSum > 0.0 && incomes.values.none { it <= 0.0 }

        val rows = adults.map { m ->
            val puso = paidById[m.id]?.totalMxn ?: 0.0
            val peso = if (byIncome) (incomes[m.id] ?: 0.0) / incomeSum
            else if (adults.isEmpty()) 0.0 else 1.0 / adults.size
            val tocaba = total * peso
            AdultShare(
                memberId = m.id,
                name = m.displayName,
                paid = puso,
                fairShare = tocaba,
                net = puso - tocaba,
                sharePct = kotlin.math.round(peso * 100).toInt(),
            )
        }.sortedByDescending { it.net }

        // Tope de cordura: un neto no puede superar el gasto repartido. Si pasa,
        // los datos estan corruptos y es mejor decirlo que mostrar una cifra falsa.
        val inconsistent = total > 0.0 && rows.any { kotlin.math.abs(it.net) > total + 0.01 }

        return AdultBalance(
            period = period,
            rows = rows,
            totalShared = total,
            weightedByIncome = byIncome,
            inconsistent = inconsistent,
        )
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
 * Lo que un adulto puso frente a lo que le tocaba en el periodo.
 *
 * @property paid      lo que efectivamente desembolso.
 * @property fairShare su parte del gasto del hogar segun su ingreso.
 * @property net       positivo = puso de mas y el resto le debe; negativo al reves.
 * @property sharePct  porcentaje del gasto que le corresponde.
 */
data class AdultShare(
    val memberId: String,
    val name: String,
    val paid: Double,
    val fairShare: Double,
    val net: Double,
    val sharePct: Int,
)

/**
 * Balance entre adultos de un periodo.
 *
 * @property totalShared      gasto corriente repartido (lo que pusieron los adultos).
 * @property weightedByIncome false cuando algun adulto no declara ingreso y el
 *                            reparto cae a partes iguales; la pantalla lo advierte.
 * @property inconsistent     un neto mayor que el total repartido: dato corrupto.
 */
data class AdultBalance(
    val period: MemberPeriod,
    val rows: List<AdultShare>,
    val totalShared: Double,
    val weightedByIncome: Boolean,
    val inconsistent: Boolean,
) {
    /** Netos por debajo de este monto son redondeo de basis points, no deuda. */
    val significant: List<AdultShare>
        get() = rows.filter { kotlin.math.abs(it.net) >= NOISE_FLOOR_MXN }

    private companion object {
        const val NOISE_FLOOR_MXN = 50.0
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
