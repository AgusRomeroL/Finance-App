package mx.budget.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import mx.budget.data.local.result.ExpenseWithDetails
import mx.budget.data.repository.ExpenseRepository

/**
 * ViewModel de la búsqueda de movimientos (barra inferior). Mantiene el texto y
 * deriva los resultados con `debounce` sobre [ExpenseRepository.searchWithDetails].
 * El filtro por grupo lo aplica la pantalla usando el `DashboardViewModel` (estado
 * compartido), así un mismo set de pills filtra dashboard y búsqueda.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(
    private val expenseRepository: ExpenseRepository,
    private val householdId: String
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    fun onQueryChange(q: String) { _query.value = q }

    val results: StateFlow<List<ExpenseWithDetails>> = _query
        .debounce(250)
        .flatMapLatest { q ->
            if (q.isBlank()) flowOf(emptyList())
            else expenseRepository.searchWithDetails(householdId, q.trim())
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
