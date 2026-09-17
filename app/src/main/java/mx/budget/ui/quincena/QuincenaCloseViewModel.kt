package mx.budget.ui.quincena

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.budget.data.local.entity.QuincenaEntity
import mx.budget.data.local.result.ExpenseWithDetails
import mx.budget.data.local.result.SpendByCategory
import mx.budget.data.local.result.SpendByMember
import mx.budget.data.local.result.WalletBalanceInfo
import mx.budget.data.quincena.QuincenaFigures
import mx.budget.data.quincena.QuincenaLifecycle
import mx.budget.data.quincena.quincenaFigures
import mx.budget.data.repository.AnalyticsRepository
import mx.budget.data.repository.ExpenseRepository
import mx.budget.data.repository.IncomeRepository
import mx.budget.data.repository.QuincenaRepository
import mx.budget.data.repository.WalletRepository
import java.time.LocalDate
import java.time.ZoneId

/**
 * Que hacer con un gasto PLANNED que la quincena nunca ejecuto.
 *
 * No hay opcion "dejarlo": una quincena cerrada con planeados pendientes
 * arrastraria para siempre un compromiso que ya no va a ocurrir en ese periodo,
 * y el "Disponible" de la quincena en curso seguiria reservandolo.
 */
enum class PlannedDecision {
    /** Se pasa a la quincena en curso con su fecha ajustada. */
    MOVE,

    /** Si ocurrio: se marca POSTED y mueve el saldo de su cuenta. */
    POST,

    /** No ocurrio ni va a ocurrir: se borra con lapida. */
    DISCARD,
}

/** Todo lo que la pantalla de cierre muestra antes de congelar el periodo. */
data class QuincenaCloseState(
    val quincena: QuincenaEntity? = null,
    val figures: QuincenaFigures? = null,
    val planned: List<ExpenseWithDetails> = emptyList(),
    val decisions: Map<String, PlannedDecision> = emptyMap(),
    val byCategory: List<SpendByCategory> = emptyList(),
    val byBeneficiary: List<SpendByMember> = emptyList(),
    val byPayer: List<SpendByMember> = emptyList(),
    val balances: List<WalletBalanceInfo> = emptyList(),
    val loading: Boolean = true,
    val working: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
    /** El rol de la sesion permite cerrar y reabrir (Dueño o Administrador). */
    val canManage: Boolean = true,
    /** Hoy, para decidir si el periodo ya vencio. */
    val today: LocalDate = LocalDate.now(ZoneId.of("America/Mexico_City")),
) {
    val isClosed: Boolean get() = quincena?.status == QuincenaLifecycle.CLOSED

    /** Falta decidir que hacer con algun planeado. */
    val pendingDecisions: Int get() = planned.count { it.expenseId !in decisions }

    /**
     * Cuantos planeados y cuanto dinero se lleva cada decision.
     *
     * Cerrar es dificil de deshacer y con decenas de filas nadie recuerda que
     * eligio arriba: la barra inferior y el dialogo de confirmacion dicen esto
     * para que confirmar no sea un acto de fe. Reabrir devuelve la quincena a
     * revision, pero no resucita lo descartado ni des-ejecuta lo que se dio por
     * pagado, asi que el numero tiene que verse ANTES.
     */
    fun totalPor(decision: PlannedDecision): Pair<Int, Double> {
        val filas = planned.filter { decisions[it.expenseId] == decision }
        return filas.size to filas.sumOf { it.amountMxn }
    }

    val canClose: Boolean
        get() = quincena != null && canManage && !working && !isClosed &&
            QuincenaLifecycle.canClose(quincena, today) && pendingDecisions == 0

    /** Motivo por el que el boton de cerrar esta apagado, o null si se puede. */
    val blockedReason: String?
        get() = when {
            quincena == null -> null
            isClosed -> null
            !canManage -> "Solo el Dueño o el Administrador pueden cerrar una quincena."
            !QuincenaLifecycle.canClose(quincena, today) ->
                "Esta quincena se podrá cerrar cuando termine, a partir del día siguiente a su último día."
            pendingDecisions > 0 ->
                if (pendingDecisions == 1) "Decide qué hacer con el pago planeado que falta antes de cerrar."
                else "Decide qué hacer con los $pendingDecisions pagos planeados que faltan antes de cerrar."
            else -> null
        }
}

/**
 * Cierre manual de quincena (RF-32).
 *
 * Reune lo que se congela (cifras, gasto por categoria y por miembro, saldos) y
 * resuelve los planeados pendientes dentro de la MISMA transaccion del cierre,
 * asi que o se cierra todo o no se cierra nada.
 */
class QuincenaCloseViewModel(
    private val quincenaRepository: QuincenaRepository,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val walletRepository: WalletRepository,
    private val householdId: String,
    private val zone: ZoneId = ZoneId.of("America/Mexico_City"),
) : ViewModel() {

    private val _state = MutableStateFlow(QuincenaCloseState())
    val state: StateFlow<QuincenaCloseState> = _state.asStateFlow()

    private var quincenaId: String? = null

    /** Carga (o recarga) la quincena [id]. [canManage] viene del rol de la sesion. */
    fun load(id: String, canManage: Boolean = true) {
        quincenaId = id
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, canManage = canManage) }
            val quincena = quincenaRepository.getById(id)
            if (quincena == null) {
                _state.update { it.copy(loading = false, error = "La quincena ya no existe.") }
                return@launch
            }
            val movimientos = runCatching { expenseRepository.observeWithDetails(id).first() }
                .getOrDefault(emptyList())
            val planeados = movimientos.filter { it.status == "PLANNED" }
            val ejecutado = movimientos.filter { it.status == "POSTED" }.sumOf { it.amountMxn }
            val reservado = planeados.sumOf { it.amountMxn }
            val ingresoRecibido = runCatching { incomeRepository.observePostedTotal(id).first() }
                .getOrDefault(0.0)
            val figuras = quincenaFigures(
                quincena = quincena,
                receivedIncome = ingresoRecibido,
                spent = ejecutado,
                reserved = reservado,
                projectedExpensesFallback = ejecutado + reservado,
            )
            _state.update {
                it.copy(
                    quincena = quincena,
                    figures = figuras,
                    planned = planeados,
                    // Las decisiones tomadas se conservan al recargar.
                    decisions = it.decisions.filterKeys { key -> planeados.any { p -> p.expenseId == key } },
                    byCategory = runCatching { analyticsRepository.getSpendByCategory(householdId, id) }
                        .getOrDefault(emptyList()),
                    byBeneficiary = runCatching { expenseRepository.observeSpendByMember(id).first() }
                        .getOrDefault(emptyList()),
                    byPayer = runCatching { expenseRepository.observePaidByMember(id).first() }
                        .getOrDefault(emptyList()),
                    balances = runCatching { walletRepository.observeBalances(householdId).first() }
                        .getOrDefault(emptyList()),
                    loading = false,
                    today = LocalDate.now(zone),
                )
            }
        }
    }

    fun decide(expenseId: String, decision: PlannedDecision) {
        _state.update { it.copy(decisions = it.decisions + (expenseId to decision)) }
    }

    /** Aplica la misma decisión a todos los planeados pendientes. */
    fun decideAll(decision: PlannedDecision) {
        _state.update { current ->
            current.copy(decisions = current.planned.associate { it.expenseId to decision })
        }
    }

    fun close() {
        val id = quincenaId ?: return
        val current = _state.value
        if (!current.canClose) return
        viewModelScope.launch {
            _state.update { it.copy(working = true, error = null) }
            try {
                val destino = quincenaRepository.getActive(householdId)
                quincenaRepository.close(id) {
                    current.planned.forEach { fila ->
                        when (current.decisions[fila.expenseId]) {
                            PlannedDecision.POST ->
                                expenseRepository.confirmPlanned(fila.expenseId)
                            PlannedDecision.DISCARD ->
                                expenseRepository.deleteAndRevertBalance(fila.expenseId)
                            PlannedDecision.MOVE -> {
                                if (destino != null && destino.id != id) {
                                    // La fecha sube al inicio del periodo destino: un
                                    // planeado con fecha vieja nace vencido y el
                                    // recordatorio suena de inmediato.
                                    val inicio = LocalDate.parse(destino.startDate)
                                        .atStartOfDay(zone).toInstant().toEpochMilli()
                                    expenseRepository.moveToQuincena(
                                        expenseId = fila.expenseId,
                                        quincenaId = destino.id,
                                        occurredAt = maxOf(fila.occurredAt, inicio),
                                    )
                                }
                            }
                            null -> Unit
                        }
                    }
                }
                _state.update { it.copy(working = false, done = true) }
                load(id, current.canManage)
            } catch (e: Exception) {
                _state.update {
                    it.copy(working = false, error = e.message ?: "No se pudo cerrar la quincena.")
                }
            }
        }
    }

    fun reopen() {
        val id = quincenaId ?: return
        if (!_state.value.canManage) return
        viewModelScope.launch {
            _state.update { it.copy(working = true, error = null) }
            try {
                quincenaRepository.reopen(id)
                _state.update { it.copy(working = false) }
                load(id, _state.value.canManage)
            } catch (e: Exception) {
                _state.update {
                    it.copy(working = false, error = e.message ?: "No se pudo reabrir la quincena.")
                }
            }
        }
    }

    fun consumeDone() {
        _state.update { it.copy(done = false) }
    }
}
