package mx.budget.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import mx.budget.data.local.dao.AttributionReviewDao
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
        /** Gasto por miembro BENEFICIARY (quién consume) — toggle "Beneficiario". */
        val beneficiaryDistribution: List<SpendByMember>,
        /** Gasto por miembro PAYER (quién paga) — toggle "Pagador". */
        val payerDistribution: List<SpendByMember>
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
 * @param householdId         ID del hogar activo, resuelto dinámicamente e inyectado.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val quincenaRepository: QuincenaRepository,
    private val expenseRepository: ExpenseRepository,
    private val memberRepository: MemberRepository,
    private val householdId: String,
    private val attributionReviewDao: AttributionReviewDao
) : ViewModel() {

    /**
     * Conteo de atribuciones pendientes de revisar (Feature B). Alimenta el badge
     * "N por revisar" del dashboard, que entra a la pantalla de revisión.
     */
    val pendingReviewCount: StateFlow<Int> = attributionReviewDao
        .observePendingCount()
        .catch { emit(0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // ── Estado unificado del dashboard ─────────────────────────────────────────

    /**
     * Estado de UI del dashboard, derivado de la quincena activa.
     *
     * **Por qué un solo `flatMapLatest` + `combine` sobre flujos crudos:** todo lo
     * dependiente de la quincena (transacciones, totales, distribución por miembro)
     * se calcula DENTRO de un único `flatMapLatest`. Así el `combine` espera a que
     * TODAS sus fuentes tengan su primer valor antes de emitir el primer `Success`
     * de una quincena, y la UI nunca ve un frame "roto" (quincena cargada pero
     * gastos en $0). La versión anterior combinaba 5 `StateFlow` independientes, cada
     * uno con valor inicial vacío/0, lo que producía ese flash en el arranque en frío.
     *
     * Balance disponible = projectedIncomeMxn − actualExpensesMxn (snapshot de la quincena).
     */
    val uiState: StateFlow<DashboardUiState> = quincenaRepository
        .observeActive(householdId)
        .distinctUntilChanged()
        .flatMapLatest { quincena ->
            if (quincena == null) {
                flowOf<DashboardUiState>(
                    DashboardUiState.Success(
                        quincena = null,
                        transactions = emptyList(),
                        postedTotal = 0.0,
                        plannedTotal = 0.0,
                        balance = 0.0,
                        beneficiaryDistribution = emptyList(),
                        payerDistribution = emptyList()
                    )
                )
            } else {
                combine(
                    expenseRepository.observeWithDetails(quincena.id),
                    expenseRepository.observePostedTotal(quincena.id),
                    expenseRepository.observePlannedTotal(quincena.id),
                    expenseRepository.observeSpendByMember(quincena.id),
                    expenseRepository.observePaidByMember(quincena.id)
                ) { tx, posted, planned, beneficiary, payer ->
                    DashboardUiState.Success(
                        quincena = quincena,
                        transactions = tx,
                        postedTotal = posted,
                        plannedTotal = planned,
                        balance = quincena.projectedIncomeMxn - quincena.actualExpensesMxn,
                        beneficiaryDistribution = beneficiary,
                        payerDistribution = payer
                    ) as DashboardUiState
                }
            }
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
