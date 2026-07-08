package mx.budget.ui.wallets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mx.budget.data.local.dao.ExpenseDao
import mx.budget.data.local.entity.IncomeSourceEntity
import mx.budget.data.local.entity.MemberEntity
import mx.budget.data.local.entity.PaymentMethodEntity
import mx.budget.data.local.entity.WalletTransferEntity
import mx.budget.data.local.result.ExpenseWithDetails
import mx.budget.data.local.result.TransferWithNames
import mx.budget.data.local.result.WalletBalanceInfo
import mx.budget.data.local.entity.InstallmentPlanEntity
import mx.budget.data.local.entity.LoanEntity
import mx.budget.data.local.entity.SavingsGoalEntity
import mx.budget.data.repository.IncomeRepository
import mx.budget.data.repository.InstallmentRepository
import mx.budget.data.repository.LoanRepository
import mx.budget.data.repository.MemberRepository
import mx.budget.data.repository.QuincenaRepository
import mx.budget.data.repository.SavingsRepository
import mx.budget.data.repository.TransferRepository
import mx.budget.data.repository.WalletRepository
import java.util.UUID

/**
 * ViewModel de la pantalla Wallets ("Cuentas"): expone los saldos por wallet,
 * los KPIs (deuda revolvente total + líquido disponible) y, bajo demanda, los
 * movimientos (gastos) cargados al wallet seleccionado.
 *
 * Solo lectura. Toda la lógica de saldos vive ya en la capa de datos
 * ([WalletRepository.observeBalances]); aquí solo se compone y se expone.
 */
/** Fila del panel de deuda por tarjeta (estados v2 Fase 5). */
data class CardDebt(
    val walletId: String,
    val name: String,
    val last4: String?,
    val saldo: Double,
    val limite: Double?,
    val utilizationPct: Double?,
    val apr: Double?,
    val pagoMinimo: Double?,
    val pagoNoIntereses: Double?,
    val fechaLimite: String?,
)

class WalletsViewModel(
    private val walletRepository: WalletRepository,
    private val transferRepository: TransferRepository,
    private val incomeRepository: IncomeRepository,
    private val memberRepository: MemberRepository,
    private val quincenaRepository: QuincenaRepository,
    private val expenseDao: ExpenseDao,
    private val householdId: String,
    // MVP Fase 3 — hoja de balance (opcionales para no romper llamadas previas).
    private val savingsRepository: SavingsRepository? = null,
    private val loanRepository: LoanRepository? = null,
    private val installmentRepository: InstallmentRepository? = null,
    private val statementImportDao: mx.budget.data.local.dao.StatementImportDao? = null,
) : ViewModel() {

    /** Kinds líquidos (saldo disponible, no deuda). */
    private val liquidKinds = setOf(
        "DEBIT_ACCOUNT", "CASH", "DIGITAL_WALLET", "EMPLOYER_SAVINGS_FUND",
    )

    /**
     * El wallet virtual "Pagado por terceros" (`kind="EXTERNAL"`, Fase B/B3) NO es
     * una cuenta real: se excluye de la lista de saldos, del selector de transferencias
     * e ingresos y de los KPIs. Existe solo para "colgar" gastos que adelantó un tercero
     * sin mover ningún saldo real.
     */
    private val EXTERNAL_KIND = "EXTERNAL"

    val balances: StateFlow<List<WalletBalanceInfo>> =
        walletRepository.observeBalances(householdId)
            .map { list -> list.filter { it.kind != EXTERNAL_KIND } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Entidades completas (para precargar el formulario de edición). */
    val entities: StateFlow<List<PaymentMethodEntity>> =
        walletRepository.observeActive(householdId)
            .map { list -> list.filter { it.kind != EXTERNAL_KIND } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** El householdId del hogar, para construir wallets nuevos desde el form. */
    val household: String get() = householdId

    /** Deuda revolvente total (tarjetas de crédito + departamentales). */
    val revolvingDebt: StateFlow<Double> =
        walletRepository.observeTotalRevolvingDebt(householdId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    /** Panel de deuda por tarjeta: saldo/límite/tasa del wallet + pago mínimo y fecha
     *  límite del último estado importado (estados v2 Fase 5). */
    val cardDebts: StateFlow<List<CardDebt>> =
        kotlinx.coroutines.flow.combine(
            walletRepository.observeActive(householdId),
            statementImportDao?.observeLatestFullByWallet(householdId)
                ?: kotlinx.coroutines.flow.flowOf(emptyList()),
        ) { wallets, imports ->
            val byWallet = imports.associateBy { it.walletId }
            wallets.filter {
                mx.budget.data.statements.StatementCycleTracker.isStatementCard(it.kind)
            }.map { w ->
                val imp = byWallet[w.id]
                CardDebt(
                    walletId = w.id,
                    name = w.displayName,
                    last4 = w.last4,
                    saldo = w.currentBalanceMxn,
                    limite = w.creditLimitMxn,
                    utilizationPct = w.creditLimitMxn?.takeIf { it > 0 }
                        ?.let { (w.currentBalanceMxn / it * 100).coerceIn(0.0, 999.0) },
                    apr = w.interestApr,
                    pagoMinimo = imp?.pagoMinimo,
                    pagoNoIntereses = imp?.pagoNoIntereses,
                    fechaLimite = imp?.fechaLimitePago,
                )
            }.sortedByDescending { it.saldo }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Líquido disponible: suma de saldos de wallets de tipo líquido. */
    val liquidTotal: StateFlow<Double> =
        balances
            .map { list -> list.filter { it.kind in liquidKinds }.sumOf { it.balance } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    private val selectedWalletId = MutableStateFlow<String?>(null)

    /** Wallet seleccionado para ver su detalle/movimientos (null = ninguno). */
    val selected: StateFlow<WalletBalanceInfo?> =
        combine(selectedWalletId, balances) { id, list ->
            id?.let { sel -> list.firstOrNull { it.paymentMethodId == sel } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Movimientos del wallet seleccionado (vacío si no hay selección). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val movements: StateFlow<List<ExpenseWithDetails>> =
        selectedWalletId
            .flatMapLatest { id: String? ->
                if (id == null) flowOf(emptyList()) else expenseDao.observeByWallet(id)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectWallet(paymentMethodId: String) { selectedWalletId.value = paymentMethodId }

    fun clearSelection() { selectedWalletId.value = null }

    /**
     * Alta o edición de un wallet. `dao.insert` es REPLACE, así que sirve para
     * ambos casos (id nuevo = alta; id existente = edición); el repo encola sync.
     */
    fun saveWallet(wallet: PaymentMethodEntity) {
        viewModelScope.launch { walletRepository.insert(wallet) }
    }

    /**
     * Baja lógica (`isActive = false`): NO se borra la fila por las FKs de
     * `expense`/`income_source` que la referencian. Desaparece de la lista activa.
     */
    fun deactivateWallet(wallet: PaymentMethodEntity) {
        viewModelScope.launch { walletRepository.update(wallet.copy(isActive = false)) }
    }

    /**
     * Conciliación manual (RF-42): re-ancla el saldo al valor real del estado de
     * cuenta. Es la válvula de corrección del modelo guardado+mantenido (Fase 2),
     * que puede derivar al editar/borrar gastos. Fija `current_balance_mxn` absoluto
     * (en crédito = deuda real) y encola sync.
     */
    fun reconcileWallet(paymentMethodId: String, newBalance: Double) {
        viewModelScope.launch { walletRepository.reconcileBalance(paymentMethodId, newBalance) }
    }

    /** Historial de transferencias entre cuentas (RF-41). */
    val transfers: StateFlow<List<TransferWithNames>> =
        transferRepository.observeTransfers(householdId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Miembros activos del hogar (para elegir quién recibe el ingreso). */
    val members: StateFlow<List<MemberEntity>> =
        memberRepository.observeActiveMembers(householdId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Registra un ingreso POSTED en la quincena activa y acredita la cuenta de
     * depósito (RF: ingresos acreditan el wallet). No-op si no hay quincena activa.
     */
    fun recordIncome(
        walletId: String,
        memberId: String,
        amountMxn: Double,
        label: String,
        expectedDateIso: String,
        colorHex: String? = null,
    ) {
        viewModelScope.launch {
            val quincena = quincenaRepository.getActive(householdId) ?: return@launch
            incomeRepository.insert(
                IncomeSourceEntity(
                    id = UUID.randomUUID().toString(),
                    householdId = householdId,
                    quincenaId = quincena.id,
                    memberId = memberId,
                    label = label.ifBlank { "Ingreso" },
                    amountMxn = amountMxn,
                    cadence = "IRREGULAR",
                    expectedDate = expectedDateIso,
                    paymentMethodId = walletId,
                    status = "POSTED",
                    colorHex = colorHex,
                    createdAt = System.currentTimeMillis(),
                )
            )
        }
    }

    /**
     * Registra una transferencia (o pago de tarjeta): mueve el saldo de ambas
     * cuentas. [occurredAt] por defecto = ahora.
     */
    fun recordTransfer(
        fromId: String,
        toId: String,
        amountMxn: Double,
        note: String?,
        occurredAt: Long,
    ) {
        viewModelScope.launch {
            transferRepository.recordTransfer(
                WalletTransferEntity(
                    id = UUID.randomUUID().toString(),
                    householdId = householdId,
                    fromPaymentMethodId = fromId,
                    toPaymentMethodId = toId,
                    amountMxn = amountMxn,
                    occurredAt = occurredAt,
                    note = note?.ifBlank { null },
                    createdAt = occurredAt,
                )
            )
        }
    }

    /**
     * Borra una transferencia y revierte el ajuste de saldo de ambas cuentas
     * (RF-41). El repo encola el push del borrado y de los dos wallets.
     */
    fun deleteTransfer(id: String) {
        viewModelScope.launch { transferRepository.deleteTransfer(id) }
    }

    // ── MVP Fase 3: hoja de balance (ahorro / préstamos / MSI) ───────────────

    val savingsGoals: StateFlow<List<SavingsGoalEntity>> =
        (savingsRepository?.observeAll(householdId) ?: flowOf(emptyList()))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val loans: StateFlow<List<LoanEntity>> =
        (loanRepository?.observeAll(householdId) ?: flowOf(emptyList()))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val installments: StateFlow<List<InstallmentPlanEntity>> =
        (installmentRepository?.observeActive(householdId) ?: flowOf(emptyList()))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Alta/edición de meta de ahorro (insert es REPLACE). */
    fun saveSavingsGoal(goal: SavingsGoalEntity) {
        viewModelScope.launch { savingsRepository?.insert(goal) }
    }

    /**
     * Crea un deudor nuevo (rol EXTERNAL_DEBTOR) desde el editor de préstamo y lo
     * deja seleccionado vía [onCreated]. Reutiliza el mismo camino que la captura
     * ([CaptureViewModel.onCreateExternalPayer]): inserta por [memberRepository]
     * (encola sync + LWW). Idempotente por nombre: si ya existe un miembro con ese
     * displayName lo reutiliza (el índice único household+display_name lo exige).
     */
    fun createDebtor(name: String, onCreated: (String) -> Unit) {
        val trimmed = name.trim().take(48)
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val existing = members.value.firstOrNull { it.displayName.equals(trimmed, ignoreCase = true) }
            if (existing != null) {
                onCreated(existing.id)
                return@launch
            }
            val aliasJson = "[\"" + trimmed.replace("\\", "\\\\").replace("\"", "\\\"") + "\"]"
            val member = MemberEntity(
                id = UUID.randomUUID().toString(),
                householdId = householdId,
                displayName = trimmed,
                shortAliases = aliasJson,
                role = "EXTERNAL_DEBTOR",
                isActive = true,
                updatedAt = System.currentTimeMillis(),
            )
            memberRepository.insert(member)
            onCreated(member.id)
        }
    }

    /** Alta/edición de préstamo (insert es REPLACE). */
    fun saveLoan(loan: LoanEntity) {
        viewModelScope.launch { loanRepository?.insert(loan) }
    }

    /** Registra un pago recibido del deudor (reduce el saldo pendiente). */
    fun applyLoanPayment(loanId: String, paymentMxn: Double) {
        viewModelScope.launch { loanRepository?.applyPayment(loanId, paymentMxn) }
    }

    fun deleteLoan(loan: LoanEntity) {
        viewModelScope.launch { loanRepository?.delete(loan) }
    }

    /** Alta/edición de plan de cuotas (insert es REPLACE). */
    fun saveInstallment(plan: InstallmentPlanEntity) {
        viewModelScope.launch { installmentRepository?.insert(plan) }
    }

    /** Avanza la cuota; al llegar al total el repo lo marca PAID_OFF. */
    fun advanceInstallment(planId: String) {
        viewModelScope.launch { installmentRepository?.advanceInstallment(planId) }
    }
}
