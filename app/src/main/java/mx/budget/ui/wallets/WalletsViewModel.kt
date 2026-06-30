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
import mx.budget.data.local.entity.PaymentMethodEntity
import mx.budget.data.local.result.ExpenseWithDetails
import mx.budget.data.local.result.WalletBalanceInfo
import mx.budget.data.repository.WalletRepository

/**
 * ViewModel de la pantalla Wallets ("Cuentas"): expone los saldos por wallet,
 * los KPIs (deuda revolvente total + líquido disponible) y, bajo demanda, los
 * movimientos (gastos) cargados al wallet seleccionado.
 *
 * Solo lectura. Toda la lógica de saldos vive ya en la capa de datos
 * ([WalletRepository.observeBalances]); aquí solo se compone y se expone.
 */
class WalletsViewModel(
    private val walletRepository: WalletRepository,
    private val expenseDao: ExpenseDao,
    private val householdId: String,
) : ViewModel() {

    /** Kinds líquidos (saldo disponible, no deuda). */
    private val liquidKinds = setOf(
        "DEBIT_ACCOUNT", "CASH", "DIGITAL_WALLET", "EMPLOYER_SAVINGS_FUND",
    )

    val balances: StateFlow<List<WalletBalanceInfo>> =
        walletRepository.observeBalances(householdId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Entidades completas (para precargar el formulario de edición). */
    val entities: StateFlow<List<PaymentMethodEntity>> =
        walletRepository.observeActive(householdId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** El householdId del hogar, para construir wallets nuevos desde el form. */
    val household: String get() = householdId

    /** Deuda revolvente total (tarjetas de crédito + departamentales). */
    val revolvingDebt: StateFlow<Double> =
        walletRepository.observeTotalRevolvingDebt(householdId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

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
}
