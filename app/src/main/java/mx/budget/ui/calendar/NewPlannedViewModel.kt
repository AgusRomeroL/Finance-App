package mx.budget.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mx.budget.data.local.dao.QuincenaDao
import mx.budget.data.local.entity.CategoryEntity
import mx.budget.data.local.entity.ExpenseAttributionEntity
import mx.budget.data.local.entity.ExpenseEntity
import mx.budget.data.local.entity.MemberEntity
import mx.budget.data.local.entity.PaymentMethodEntity
import mx.budget.data.repository.CategoryRepository
import mx.budget.data.repository.ExpenseRepository
import mx.budget.data.repository.MemberRepository
import mx.budget.data.repository.QuincenaRepository
import mx.budget.data.repository.WalletRepository
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Estado del guardado de un pago planeado manual (Fase 4 inc. 2b). */
sealed interface NewPlannedState {
    data object Idle : NewPlannedState
    data object Saving : NewPlannedState
    data object Saved : NewPlannedState
    data class Error(val message: String) : NewPlannedState
}

/**
 * ViewModel del formulario "Nuevo pago planeado" (Apéndice G.2, Fase 4 inc. 2b):
 * crea un `ExpenseEntity` con `status=PLANNED` en una fecha arbitraria (default =
 * el día seleccionado en el calendario), con atribución beneficiario/pagador.
 *
 * Mismo invariante que la captura normal: ambas particiones (% por miembro) deben
 * sumar 100; se convierten a basis points (el último absorbe el resto = 10,000).
 * El gasto cae en la quincena que contiene la fecha (o la activa como fallback).
 */
class NewPlannedViewModel(
    private val householdId: String,
    private val expenseRepository: ExpenseRepository,
    private val quincenaRepository: QuincenaRepository,
    private val quincenaDao: QuincenaDao,
    private val categoryRepository: CategoryRepository,
    private val walletRepository: WalletRepository,
    private val memberRepository: MemberRepository,
) : ViewModel() {

    val members: StateFlow<List<MemberEntity>> =
        memberRepository.observeActiveMembers(householdId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> =
        categoryRepository.observeAll(householdId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val wallets: StateFlow<List<PaymentMethodEntity>> =
        walletRepository.observeActive(householdId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _concept = MutableStateFlow("")
    val concept: StateFlow<String> = _concept.asStateFlow()

    private val _amountText = MutableStateFlow("")
    val amountText: StateFlow<String> = _amountText.asStateFlow()

    private val _categoryId = MutableStateFlow<String?>(null)
    val categoryId: StateFlow<String?> = _categoryId.asStateFlow()

    private val _walletId = MutableStateFlow<String?>(null)
    val walletId: StateFlow<String?> = _walletId.asStateFlow()

    private val _date = MutableStateFlow(LocalDate.now(ZONE))
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    private val _beneficiaryShares = MutableStateFlow<Map<String, Int>>(emptyMap())
    val beneficiaryShares: StateFlow<Map<String, Int>> = _beneficiaryShares.asStateFlow()

    private val _payerShares = MutableStateFlow<Map<String, Int>>(emptyMap())
    val payerShares: StateFlow<Map<String, Int>> = _payerShares.asStateFlow()

    private val _state = MutableStateFlow<NewPlannedState>(NewPlannedState.Idle)
    val state: StateFlow<NewPlannedState> = _state.asStateFlow()

    init { viewModelScope.launch { applyDefaults() } }

    /**
     * Prepara el formulario para una nueva captura en [date]: limpia concepto/monto,
     * resetea el estado y reaplica los defaults de atribución. Lo llama el FAB del
     * calendario con el día seleccionado.
     */
    fun start(date: LocalDate) {
        _concept.value = ""
        _amountText.value = ""
        _date.value = date
        _state.value = NewPlannedState.Idle
        _beneficiaryShares.value = emptyMap()
        _payerShares.value = emptyMap()
        viewModelScope.launch { applyDefaults() }
    }

    /** Defaults: beneficiarios = todos por igual; pagador = dueño del primer wallet. */
    private suspend fun applyDefaults() {
        val activeMembers = memberRepository.observeActiveMembers(householdId).first()
            .filter { !it.role.startsWith("EXTERNAL_") }
        if (activeMembers.isNotEmpty() && _beneficiaryShares.value.isEmpty()) {
            _beneficiaryShares.value = equalSplit(activeMembers.map { it.id })
        }
        val activeWallets = walletRepository.observeActive(householdId).first()
        if (_walletId.value == null) _walletId.value = activeWallets.firstOrNull()?.id
        if (_payerShares.value.isEmpty()) {
            val owner = activeWallets.firstOrNull()?.ownerMemberId
                ?: activeMembers.firstOrNull { it.role == "PAYER_ADULT" }?.id
                ?: activeMembers.firstOrNull()?.id
            if (owner != null) _payerShares.value = mapOf(owner to 100)
        }
    }

    fun onConcept(v: String) { _concept.value = v }
    fun onAmount(v: String) { _amountText.value = v.filter { it.isDigit() || it == '.' } }
    fun onCategory(id: String) { _categoryId.value = id }
    fun onWallet(id: String) { _walletId.value = id }
    fun onDate(d: LocalDate) { _date.value = d }

    fun onBeneficiaryToggle(memberId: String) {
        val ids = _beneficiaryShares.value.keys.toMutableSet().apply { if (!add(memberId)) remove(memberId) }
        _beneficiaryShares.value = equalSplit(ids)
    }

    fun onBeneficiaryDelta(memberId: String, delta: Int) {
        val cur = _beneficiaryShares.value
        if (memberId !in cur) return
        _beneficiaryShares.value = cur + (memberId to (cur.getValue(memberId) + delta).coerceIn(0, 100))
    }

    fun onBeneficiaryAll() { _beneficiaryShares.value = equalSplit(activeMemberIds()) }
    fun onBeneficiaryClear() { _beneficiaryShares.value = emptyMap() }

    fun onPayerToggle(memberId: String) {
        val ids = _payerShares.value.keys.toMutableSet().apply { if (!add(memberId)) remove(memberId) }
        _payerShares.value = equalSplit(ids)
    }

    fun onPayerDelta(memberId: String, delta: Int) {
        val cur = _payerShares.value
        if (memberId !in cur) return
        _payerShares.value = cur + (memberId to (cur.getValue(memberId) + delta).coerceIn(0, 100))
    }

    fun onPayerAll() { _payerShares.value = equalSplit(activeMemberIds()) }
    fun onPayerClear() { _payerShares.value = emptyMap() }

    private fun activeMemberIds() =
        members.value.filter { !it.role.startsWith("EXTERNAL_") }.map { it.id }

    fun save() {
        if (_state.value is NewPlannedState.Saving) return
        viewModelScope.launch {
            _state.value = NewPlannedState.Saving
            try {
                val amount = _amountText.value.toDoubleOrNull()
                    ?: throw IllegalArgumentException("Ingresa un monto válido.")
                if (amount <= 0) throw IllegalArgumentException("El monto debe ser mayor a 0.")
                val categoryId = _categoryId.value ?: throw IllegalArgumentException("Selecciona una categoría.")
                val walletId = _walletId.value ?: throw IllegalArgumentException("Selecciona un método de pago.")
                val benef = _beneficiaryShares.value
                val payer = _payerShares.value
                if (benef.isEmpty() || benef.values.sum() != 100) {
                    throw IllegalArgumentException("Los beneficiarios deben sumar 100%.")
                }
                if (payer.isEmpty() || payer.values.sum() != 100) {
                    throw IllegalArgumentException("Los pagadores deben sumar 100%.")
                }

                val date = _date.value
                // La quincena REAL de la fecha; si cae en un hueco se aprovisiona la
                // determinista (nunca la ACTIVE: asignar un pago del 20-jul a la
                // quincena 1-15 jul contaminaba sus agregados).
                val quincena = mx.budget.data.quincena.QuincenaRollover(quincenaDao, householdId)
                    .ensureForDate(date)

                val occurredAt = date.atTime(9, 0).atZone(ZONE).toInstant().toEpochMilli()
                val now = System.currentTimeMillis()
                val categoryName = categories.value.firstOrNull { it.id == categoryId }?.displayName
                val conceptValue = _concept.value.trim().ifBlank { categoryName ?: "Pago" }.take(64)
                val expenseId = UUID.randomUUID().toString()

                val expense = ExpenseEntity(
                    id = expenseId,
                    householdId = householdId,
                    occurredAt = occurredAt,
                    quincenaId = quincena.id,
                    categoryId = categoryId,
                    concept = conceptValue,
                    amountMxn = amount,
                    paymentMethodId = walletId,
                    status = "PLANNED",
                    createdAt = now,
                )
                val attributions =
                    buildRows(expenseId, benef, "BENEFICIARY", amount) +
                        buildRows(expenseId, payer, "PAYER", amount)

                expenseRepository.insertWithAttributions(expense, attributions)
                _state.value = NewPlannedState.Saved
            } catch (e: Exception) {
                _state.value = NewPlannedState.Error(e.message ?: "No se pudo guardar.")
            }
        }
    }

    /** Reinicia el estado tras consumir un Saved/Error (para reusar el VM). */
    fun consumeState() { _state.value = NewPlannedState.Idle }

    private fun equalSplit(ids: Collection<String>): Map<String, Int> {
        if (ids.isEmpty()) return emptyMap()
        val list = ids.toList()
        val base = 100 / list.size
        val remainder = 100 - base * list.size
        return list.mapIndexed { i, id -> id to if (i == list.lastIndex) base + remainder else base }.toMap()
    }

    /** % → basis points; el último absorbe el resto para sumar 10,000 exacto. */
    private fun buildRows(
        expenseId: String,
        shares: Map<String, Int>,
        role: String,
        amount: Double,
    ): List<ExpenseAttributionEntity> {
        val entries = shares.entries.toList()
        var assigned = 0
        return entries.mapIndexed { i, (memberId, pct) ->
            val bps = if (i == entries.lastIndex) 10_000 - assigned else (pct * 100).also { assigned += it }
            ExpenseAttributionEntity(
                id = UUID.randomUUID().toString(),
                expenseId = expenseId,
                memberId = memberId,
                role = role,
                shareBps = bps,
                shareAmountMxn = amount * bps / 10_000.0,
            )
        }
    }

    companion object {
        private val ZONE: ZoneId = ZoneId.of("America/Mexico_City")
    }
}
