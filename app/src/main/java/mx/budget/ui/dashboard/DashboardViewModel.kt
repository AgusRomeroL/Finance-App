package mx.budget.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mx.budget.ai.proactive.ProactiveSuggestion
import mx.budget.ai.proactive.ProactiveSuggestionEngine
import mx.budget.data.capture.BankCaptureManager
import mx.budget.data.local.dao.AttributionReviewDao
import mx.budget.data.local.dao.CategoryDao
import mx.budget.data.local.dao.ExpenseDao
import mx.budget.data.local.dao.PendingBankCaptureDao
import mx.budget.data.local.entity.CategoryEntity
import mx.budget.data.local.entity.PendingBankCaptureEntity
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
        val payerDistribution: List<SpendByMember>,
        /** Hay una quincena más antigua a la que navegar (chevron ‹). */
        val canViewOlder: Boolean = false,
        /** Hay una quincena más reciente a la que navegar (chevron ›). */
        val canViewNewer: Boolean = false,
        /** La quincena mostrada es la ACTIVA (no se está viendo una histórica). */
        val viewingActive: Boolean = true
    ) : DashboardUiState()

    /** Estado de error con mensaje recuperable. */
    data class Error(val message: String) : DashboardUiState()
}

/** Tope de sugerencias proactivas calculadas (la sección y "Ver más" toman de aquí). */
private const val MAX_PROACTIVE = 8

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
    private val attributionReviewDao: AttributionReviewDao,
    private val expenseDao: ExpenseDao,
    private val pendingBankCaptureDao: PendingBankCaptureDao,
    private val bankCaptureManager: BankCaptureManager,
    private val categoryDao: CategoryDao
) : ViewModel() {

    // ── Capturas bancarias pendientes (Feature D, §F.6) ─────────────────────────

    /** Propuestas de gasto detectadas en notificaciones bancarias, por confirmar. */
    val pendingBankCaptures: StateFlow<List<PendingBankCaptureEntity>> = pendingBankCaptureDao
        .observePending()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Confirma la captura → inserta el gasto POSTED con atribución inferida. */
    fun confirmBankCapture(id: String) {
        viewModelScope.launch { bankCaptureManager.confirm(id) }
    }

    /** Descarta la captura (señal negativa implícita). */
    fun dismissBankCapture(id: String) {
        viewModelScope.launch { bankCaptureManager.dismiss(id) }
    }

    /**
     * Conteo de atribuciones pendientes de revisar (Feature B). Alimenta el badge
     * "N por revisar" del dashboard, que entra a la pantalla de revisión.
     */
    val pendingReviewCount: StateFlow<Int> = attributionReviewDao
        .observePendingCount()
        .catch { emit(0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // ── Navegación entre quincenas ──────────────────────────────────────────────

    /** Todas las quincenas del hogar, ordenadas de más reciente a más antigua. */
    private val allQuincenas: StateFlow<List<QuincenaEntity>> = quincenaRepository
        .observeAll(householdId)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Quincena activa (la que el ciclo marca como ACTIVE). */
    private val activeQuincena: StateFlow<QuincenaEntity?> = quincenaRepository
        .observeActive(householdId)
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Quincena que el usuario eligió ver con el chip ‹ › (null = seguir la activa). */
    private val selectedQuincenaId = MutableStateFlow<String?>(null)

    // ── Sugerencia proactiva al abrir la app (Feature C, §F.5) ──────────────────

    private val proactiveEngine = ProactiveSuggestionEngine()

    /** Claves canónicas descartadas con "Ahora no" en esta sesión (no persistente). */
    private val dismissedSuggestions = MutableStateFlow<Set<String>>(emptySet())

    /**
     * Sugerencia proactiva calculada on-demand al abrir/cambiar de quincena: el
     * gasto que el hogar suele registrar ahora (hora + día de semana + día de
     * quincena). Determinista sobre el historial canonicalizado de B; sin tabla
     * persistente ni worker (pre-cómputo opcional en la spec). `null` si no hay
     * patrón fiable o si el usuario ya la descartó esta sesión.
     */
    val proactiveSuggestions: StateFlow<List<ProactiveSuggestion>> = combine(
        activeQuincena, dismissedSuggestions
    ) { quincena, dismissed -> quincena to dismissed }
        .flatMapLatest { (quincena, dismissed) ->
            flow {
                val history = runCatching { expenseDao.getAll(householdId) }.getOrDefault(emptyList())
                val list = proactiveEngine
                    .suggestMany(history, quincena, System.currentTimeMillis(), limit = MAX_PROACTIVE)
                    .filter { it.canonicalKey !in dismissed }
                emit(list)
            }
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** "Ahora no" → señal negativa implícita: oculta esa sugerencia esta sesión. */
    fun dismissProactiveSuggestion(canonicalKey: String) {
        dismissedSuggestions.value = dismissedSuggestions.value + canonicalKey
    }

    /** Contexto de la quincena mostrada + flags de navegación + filtro (valor inmutable). */
    private data class ViewContext(
        val quincena: QuincenaEntity?,
        val canViewOlder: Boolean,
        val canViewNewer: Boolean,
        val viewingActive: Boolean,
        val selectedGroupIds: Set<String>,
        val categoryToGroup: Map<String, String>
    )

    private fun currentViewedId(): String? =
        selectedQuincenaId.value ?: activeQuincena.value?.id

    // ── Filtros por grupo de categoría (pills) ──────────────────────────────────

    /** Grupos top-level (parentId==null) — alimentan los pills de filtro. */
    val groups: StateFlow<List<CategoryEntity>> = categoryDao
        .observeRootCategories(householdId)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Mapa categoría(hoja)→grupo raíz, para filtrar movimientos por su grupo. */
    private val categoryToGroup: StateFlow<Map<String, String>> = categoryDao
        .observeAll(householdId)
        .map { buildLeafToRoot(it) }
        .catch { emit(emptyMap()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _selectedGroupIds = MutableStateFlow<Set<String>>(emptySet())
    /** Grupos seleccionados como filtro (vacío = sin filtro). Compartido con la búsqueda. */
    val selectedGroupIds: StateFlow<Set<String>> = _selectedGroupIds

    fun setSelectedGroups(ids: Set<String>) { _selectedGroupIds.value = ids }

    /** Resuelve, para cada categoría, su ancestro raíz (grupo). Las raíces se mapean a sí mismas. */
    private fun buildLeafToRoot(all: List<CategoryEntity>): Map<String, String> {
        val byId = all.associateBy { it.id }
        fun root(start: CategoryEntity): String {
            var cur = start
            var guard = 0
            while (cur.parentId != null && guard < 16) {
                cur = byId[cur.parentId] ?: break
                guard++
            }
            return cur.id
        }
        return all.associate { it.id to root(it) }
    }

    /**
     * Aplica el filtro de grupos a una lista de movimientos. Reutilizable por la
     * pantalla de búsqueda (que comparte `selectedGroupIds`/`categoryToGroup`).
     */
    fun applyGroupFilter(list: List<ExpenseWithDetails>): List<ExpenseWithDetails> {
        val selected = _selectedGroupIds.value
        if (selected.isEmpty()) return list
        val map = categoryToGroup.value
        return list.filter { map[it.categoryId] in selected }
    }

    // ── Estado unificado del dashboard ─────────────────────────────────────────

    /**
     * Estado de UI del dashboard, derivado de la quincena MOSTRADA (la activa por
     * defecto, o la que el usuario eligió con el navegador ‹ ›).
     *
     * **Por qué un solo `flatMapLatest` + `combine` sobre flujos crudos:** todo lo
     * dependiente de la quincena (transacciones, totales, distribución por miembro)
     * se calcula DENTRO de un único `flatMapLatest`. Así el `combine` espera a que
     * TODAS sus fuentes tengan su primer valor antes de emitir el primer `Success`,
     * y la UI nunca ve un frame "roto" (quincena cargada pero gastos en $0).
     *
     * Balance disponible = projectedIncomeMxn − actualExpensesMxn (snapshot de la quincena).
     */
    val uiState: StateFlow<DashboardUiState> = combine(
        allQuincenas, activeQuincena, selectedQuincenaId, _selectedGroupIds, categoryToGroup
    ) { all, active, selectedId, selectedGroups, catToGroup ->
        val viewed = selectedId?.let { id -> all.firstOrNull { it.id == id } } ?: active
        val idx = all.indexOfFirst { it.id == viewed?.id }
        ViewContext(
            quincena = viewed,
            // Orden DESC (recientes primero): "más antigua" = índice mayor.
            canViewOlder = idx in 0 until (all.size - 1),
            canViewNewer = idx > 0,
            viewingActive = viewed?.id != null && viewed.id == active?.id,
            selectedGroupIds = selectedGroups,
            categoryToGroup = catToGroup
        )
    }
        .distinctUntilChanged()
        .flatMapLatest { ctx ->
            val quincena = ctx.quincena
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
                    val filteredTx = if (ctx.selectedGroupIds.isEmpty()) tx
                    else tx.filter { ctx.categoryToGroup[it.categoryId] in ctx.selectedGroupIds }
                    DashboardUiState.Success(
                        quincena = quincena,
                        transactions = filteredTx,
                        postedTotal = posted,
                        plannedTotal = planned,
                        balance = quincena.projectedIncomeMxn - quincena.actualExpensesMxn,
                        beneficiaryDistribution = beneficiary,
                        payerDistribution = payer,
                        canViewOlder = ctx.canViewOlder,
                        canViewNewer = ctx.canViewNewer,
                        viewingActive = ctx.viewingActive
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

    // ── Acciones del navegador de quincenas ─────────────────────────────────────

    /** Ver la quincena inmediatamente más antigua (chevron ‹). */
    fun viewOlderQuincena() {
        val all = allQuincenas.value
        val idx = all.indexOfFirst { it.id == currentViewedId() }
        if (idx in 0 until all.lastIndex) selectedQuincenaId.value = all[idx + 1].id
    }

    /** Ver la quincena inmediatamente más reciente (chevron ›). */
    fun viewNewerQuincena() {
        val all = allQuincenas.value
        val idx = all.indexOfFirst { it.id == currentViewedId() }
        if (idx > 0) selectedQuincenaId.value = all[idx - 1].id
    }

    /** Volver a la quincena activa (tocar la etiqueta del navegador). */
    fun resetToActiveQuincena() {
        selectedQuincenaId.value = null
    }
}
