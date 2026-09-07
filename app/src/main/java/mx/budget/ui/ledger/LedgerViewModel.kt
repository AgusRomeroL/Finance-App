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
import mx.budget.data.local.result.TransferWithNames
import mx.budget.data.repository.CategoryRepository
import mx.budget.data.repository.ExpenseRepository
import mx.budget.data.repository.QuincenaRepository
import mx.budget.data.repository.TransferRepository
import mx.budget.data.repository.WalletRepository
import java.time.LocalDate
import java.time.ZoneId

/**
 * Un renglon del Libro Mayor. El historial no son solo gastos: mover dinero
 * entre cuentas tambien es un movimiento del hogar, y antes era invisible aqui
 * pese a cambiar los saldos.
 */
sealed interface LedgerItem {
    /** Fecha del movimiento en epoch millis, para ordenar la lista mezclada. */
    val occurredAt: Long

    /** Clave estable para la lista perezosa. */
    val key: String

    data class Expense(val row: ExpenseWithDetails) : LedgerItem {
        override val occurredAt: Long get() = row.occurredAt
        override val key: String get() = "ex_" + row.expenseId
    }

    data class Transfer(val row: TransferWithNames) : LedgerItem {
        override val occurredAt: Long get() = row.occurredAt
        override val key: String get() = "tr_" + row.id
    }
}

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
    private val transferRepository: TransferRepository,
    private val householdId: String,
    private val zone: ZoneId = ZoneId.of("America/Mexico_City"),
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

    /**
     * Movimientos de la quincena efectiva con filtros aplicados: gastos y
     * transferencias mezclados y ordenados por fecha descendente.
     *
     * Las transferencias no tienen categoria, asi que un filtro de categoria
     * activo las deja fuera. El filtro de cuenta si les aplica, por cualquiera
     * de sus dos extremos.
     */
    val rows: StateFlow<List<LedgerItem>> =
        combine(
            effectiveQuincena.flatMapLatest { q ->
                if (q == null) flowOf(emptyList())
                else expenseRepository.observeWithDetails(q.id)
            },
            effectiveQuincena.flatMapLatest { q ->
                if (q == null) flowOf(emptyList()) else {
                    val (start, end) = q.rangeMillis()
                    transferRepository.observeTransfersInRange(householdId, start, end)
                }
            },
            _categoryFilter,
            _walletFilter,
        ) { expenses, transfers, cat, walletName ->
            val visibleExpenses = expenses.filter { row ->
                (cat == null || row.categoryId == cat) &&
                    (walletName == null || row.paymentMethodName == walletName)
            }.map(LedgerItem::Expense)
            val visibleTransfers = if (cat != null) emptyList() else transfers.filter { t ->
                walletName == null || t.fromName == walletName || t.toName == walletName
            }.map(LedgerItem::Transfer)
            (visibleExpenses + visibleTransfers).sortedByDescending { it.occurredAt }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Rango de la quincena en epoch millis. Las fechas de la quincena son ISO y
     * `occurred_at` es epoch, asi que hay que convertir en la zona del hogar; el
     * final es el ultimo milisegundo del dia de cierre.
     */
    private fun QuincenaEntity.rangeMillis(): Pair<Long, Long> {
        val start = LocalDate.parse(startDate).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.parse(endDate).plusDays(1)
            .atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return start to end
    }

    fun selectQuincena(id: String?) { _selectedQuincenaId.value = id }

    fun setCategoryFilter(id: String?) { _categoryFilter.value = id }

    fun setWalletFilter(displayName: String?) { _walletFilter.value = displayName }
}
