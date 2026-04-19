package mx.budget.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import mx.budget.data.local.entity.QuincenaEntity
import mx.budget.data.local.result.ExpenseWithDetails
import mx.budget.data.local.result.SpendByMember
import mx.budget.data.repository.ExpenseRepository
import mx.budget.data.repository.MemberRepository
import mx.budget.data.repository.QuincenaRepository

// ─────────────────────────────────────────────────────────────────────────────
// Estado de UI del Dashboard
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Representa el estado completo de la pantalla principal.
 *
 * Se emite como un objeto sellado para facilitar el manejo de estados
 * de carga y error sin múltiples StateFlows desacoplados.
 */
sealed class DashboardUiState {
    /** Pantalla cargando — primer frame después de crear el ViewModel. */
    object Loading : DashboardUiState()

    /**
     * Estado estable con todos los datos listos para renderizar.
     *
     * @param quincena    Quincena activa actual (null solo al inicio del hogar).
     * @param transactions Lista de gastos con detalles de JOIN para el LedgerPane.
     * @param postedTotal  Total ejecutado (POSTED) en la quincena — KPI principal.
     * @param plannedTotal Total presupuestado (PLANNED) pendiente.
     * @param balance      Balance disponible = projectedIncome - postedTotal.
     * @param memberDistribution Gasto por miembro BENEFICIARY para el gráfico de barras.
     */
    data class Success(
        val quincena: QuincenaEntity?,
        val transactions: List<ExpenseWithDetails>,
        val postedTotal: Double,
        val plannedTotal: Double,
        val balance: Double,
        val memberDistribution: List<SpendByMember>
    ) : DashboardUiState()

    /** Estado de error con mensaje recuperable. */
    data class Error(val message: String) : DashboardUiState()
}

// ─────────────────────────────────────────────────────────────────────────────
// DashboardViewModel
// ─────────────────────────────────────────────────────────────────────────────

/**
 * ViewModel de la pantalla principal del presupuesto quincenal.
 *
 * Consume los flujos reactivos de los repositorios de la Fase 1
 * y los combina en un único [StateFlow] de [DashboardUiState] para
 * que la UI solo tenga un punto de verdad al que suscribirse.
 *
 * **Inyección de dependencias**: los repositorios se inyectan manualmente
 * para mantener la compatibilidad con el patrón empleado en la Fase 1
 * (sin Hilt). Migrar a `@HiltViewModel` cuando se integre DI.
 *
 * **ID de hogar**: constante provisional hasta que se implemente
 * el flujo de autenticación/selección de hogar.
 *
 * @param quincenaRepository  Repositorio principal para el ciclo quincenal.
 * @param expenseRepository   Repositorio de gastos y atribuciones.
 * @param memberRepository    Repositorio de miembros (no usado en Dashboard,
 *                            pero disponible para extensión futura).
 * @param householdId         ID del hogar activo. Provisional = "default_household".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val quincenaRepository: QuincenaRepository,
    private val expenseRepository: ExpenseRepository,
    private val memberRepository: MemberRepository,
    private val householdId: String = "default_household"
) : ViewModel() {

    // ── Quincena activa ───────────────────────────────────────────────────────

    /**
     * Flujo de la quincena activa. Emite `null` si no hay ninguna.
     * Compartido con [WhileSubscribed] para evitar queries innecesarias
     * cuando la UI está en background.
     */
    val activeQuincena: StateFlow<QuincenaEntity?> = quincenaRepository
        .observeActive(householdId)
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    // ── Flujos derivados de la quincena activa ────────────────────────────────

    /**
     * Lista de transacciones con detalles (JOIN expense + category + wallet).
     * Se actualiza automáticamente al cambiar la quincena activa.
     */
    val transactions: StateFlow<List<ExpenseWithDetails>> = activeQuincena
        .flatMapLatest { quincena ->
            quincena?.let { expenseRepository.observeWithDetails(it.id) } ?: flowOf(emptyList())
        }
        .catch { emit(emptyList()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /**
     * Total ejecutado (POSTED) en MXN para la quincena activa.
     * KPI principal — alimenta la SummaryCard "Total Spent".
     */
    val postedTotal: StateFlow<Double> = activeQuincena
        .flatMapLatest { quincena ->
            quincena?.let { expenseRepository.observePostedTotal(it.id) } ?: flowOf(0.0)
        }
        .catch { emit(0.0) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0.0
        )

    /**
     * Total presupuestado pendiente (PLANNED) en MXN.
     * Indicador "falta gastar" — alimenta la SummaryCard "Total Budget".
     */
    val plannedTotal: StateFlow<Double> = activeQuincena
        .flatMapLatest { quincena ->
            quincena?.let { expenseRepository.observePlannedTotal(it.id) } ?: flowOf(0.0)
        }
        .catch { emit(0.0) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0.0
        )

    /**
     * Distribución de gasto por beneficiario en la quincena activa.
     * Alimenta el gráfico de barras verticales "Member Distribution".
     */
    val memberDistribution: StateFlow<List<SpendByMember>> = activeQuincena
        .flatMapLatest { quincena ->
            quincena?.let { expenseRepository.observeSpendByMember(it.id) } ?: flowOf(emptyList())
        }
        .catch { emit(emptyList()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    // ── Estado compuesto ──────────────────────────────────────────────────────

    /**
     * Estado unificado de UI que combina todos los flujos anteriores.
     *
     * La UI solo necesita observar este StateFlow y hacer un `when` sobre
     * el tipo para renderizar Loading / Success / Error.
     *
     * El balance disponible se calcula como:
     *   projectedIncomeMxn - actualExpensesMxn (del snapshot de QuincenaEntity)
     */
    val uiState: StateFlow<DashboardUiState> = combine(
        activeQuincena,
        transactions,
        postedTotal,
        plannedTotal,
        memberDistribution
    ) { quincena, txList, posted, planned, members ->
        val balance = if (quincena != null) {
            quincena.projectedIncomeMxn - quincena.actualExpensesMxn
        } else {
            0.0
        }
        DashboardUiState.Success(
            quincena = quincena,
            transactions = txList,
            postedTotal = posted,
            plannedTotal = planned,
            balance = balance,
            memberDistribution = members
        )
    }
        .catch { e ->
            emit(DashboardUiState.Error(e.message ?: "Error desconocido al cargar el dashboard"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DashboardUiState.Loading
        )
}
