package mx.budget.ui.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import mx.budget.data.local.entity.CategoryEntity
import mx.budget.data.local.entity.PaymentMethodEntity
import mx.budget.data.local.entity.QuincenaEntity
import mx.budget.data.local.result.ExpenseWithDetails
import mx.budget.data.repository.CategoryRepository
import mx.budget.data.repository.ExpenseRepository
import mx.budget.data.repository.QuincenaRepository
import mx.budget.data.repository.WalletRepository

/**
 * ViewModel del Libro Mayor (MVP Fase 3): historial completo paginado POR
 * QUINCENA (nunca `SELECT *` del ledger entero) con filtros client-side de
 * categoría y wallet sobre la quincena seleccionada.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LedgerViewModel(
    private val expenseRepository: ExpenseRepository,
    quincenaRepository: QuincenaRepository,
    categoryRepository: CategoryRepository,
    walletRepository: WalletRepository,
    private val householdId: String,
) : ViewModel() {

    /** Todas las quincenas (selector de página). */
    val quincenas: StateFlow<List<QuincenaEntity>> =
        quincenaRepository.observeAll(householdId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> =
        categoryRepository.observeAll(householdId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val wallets: StateFlow<List<PaymentMethodEntity>> =
        walletRepository.observeActive(householdId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedQuincenaId = MutableStateFlow<String?>(null)
    val selectedQuincenaId: StateFlow<String?> = _selectedQuincenaId.asStateFlow()

    private val _categoryFilter = MutableStateFlow<String?>(null)
    val categoryFilter: StateFlow<String?> = _categoryFilter.asStateFlow()

    /** Filtro de wallet por nombre visible (ExpenseWithDetails no trae el id). */
    private val _walletFilter = MutableStateFlow<String?>(null)
    val walletFilter: StateFlow<String?> = _walletFilter.asStateFlow()

    /** Quincena efectiva: la seleccionada, o la activa (fallback a la más reciente). */
    val effectiveQuincena: StateFlow<QuincenaEntity?> =
        combine(_selectedQuincenaId, quincenas) { sel, all ->
            all.firstOrNull { it.id == sel }
                ?: all.firstOrNull { it.status == "ACTIVE" }
                ?: all.maxByOrNull { it.startDate }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Movimientos de la quincena efectiva con filtros aplicados. */
    val rows: StateFlow<List<ExpenseWithDetails>> =
        combine(
            effectiveQuincena.flatMapLatest { q ->
                if (q == null) flowOf(emptyList())
                else expenseRepository.observeWithDetails(q.id)
            },
            _categoryFilter,
            _walletFilter,
        ) { list, cat, walletName ->
            list.filter { row ->
                (cat == null || row.categoryId == cat) &&
                    (walletName == null || row.paymentMethodName == walletName)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectQuincena(id: String?) { _selectedQuincenaId.value = id }

    fun setCategoryFilter(id: String?) { _categoryFilter.value = id }

    fun setWalletFilter(displayName: String?) { _walletFilter.value = displayName }
}
