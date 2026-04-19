package mx.budget.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.budget.data.local.entity.ExpenseAttributionEntity
import mx.budget.data.local.entity.ExpenseEntity
import mx.budget.data.local.entity.MemberEntity
import mx.budget.data.local.entity.PaymentMethodEntity
import mx.budget.data.repository.ExpenseRepository
import mx.budget.data.repository.MemberRepository
import mx.budget.data.repository.QuincenaRepository
import mx.budget.data.repository.WalletRepository
import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
// Estados auxiliares del modal de captura
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Estado del ciclo de vida de la operación de registro de gasto.
 *
 * La UI observa este estado para mostrar indicadores de carga, cerrar
 * el sheet tras éxito, o mostrar un Snackbar en caso de error.
 */
sealed class CaptureOperationState {
    /** El modal está en reposo, listo para recibir input. */
    object Idle : CaptureOperationState()

    /** Inserción atómica en curso — mostrar CircularProgressIndicator. */
    object Loading : CaptureOperationState()

    /**
     * Gasto registrado exitosamente.
     * @param expenseId ID asignado al nuevo gasto para posible navegación.
     */
    data class Success(val expenseId: String) : CaptureOperationState()

    /**
     * Error durante el registro — mostrar Snackbar recuperable.
     * @param message Descripción del error para mostrar al usuario.
     */
    data class Error(val message: String) : CaptureOperationState()
}

// ─────────────────────────────────────────────────────────────────────────────
// CaptureViewModel
// ─────────────────────────────────────────────────────────────────────────────

/**
 * ViewModel del modal de captura de gasto rápido.
 *
 * Gestiona el estado del teclado numérico, el concepto, la selección
 * de wallet (fuente) y la atribución fraccionada a beneficiarios.
 * Al confirmar, construye la transacción atómica y la delega al
 * [ExpenseRepository.insertWithAttributions].
 *
 * **Numpad**: el importe se construye dígito a dígito (String) para
 * evitar aritmética de punto flotante hasta el commit final.
 *
 * **Atribución**: si el usuario selecciona múltiples beneficiarios,
 * los basis points se distribuyen equitativamente (con ajuste de resto
 * en el último miembro para garantizar la suma exacta de 10,000 bps).
 *
 * @param expenseRepository  Repositorio para inserción atómica.
 * @param quincenaRepository Repositorio para obtener la quincena activa.
 * @param walletRepository   Repositorio para listar fuentes de pago.
 * @param memberRepository   Repositorio para listar miembros del hogar.
 * @param householdId        ID del hogar activo.
 */
class CaptureViewModel(
    private val expenseRepository: ExpenseRepository,
    private val quincenaRepository: QuincenaRepository,
    private val walletRepository: WalletRepository,
    private val memberRepository: MemberRepository,
    private val householdId: String = "default_household"
) : ViewModel() {

    // ── Importe ingresado desde el numpad ──────────────────────────────────
    // Se mantiene como String para controlar exactamente la representación.
    // Máximo 9 dígitos + 2 decimales (MXN práctico: hasta $9,999,999.99).

    private val _rawAmount = MutableStateFlow("0")

    /**
     * Representación visual del importe para renderizar en el display.
     * Formato: "0.00", "1,234.56", etc.
     */
    val displayAmount: StateFlow<String> = _rawAmount
        .stateIn(viewModelScope, SharingStarted.Eagerly, "0.00")

    /** Importe numérico para validación interna. */
    private val parsedAmount: Double
        get() = _rawAmount.value.replace(",", "").toDoubleOrNull() ?: 0.0

    // ── Concepto libre ─────────────────────────────────────────────────────

    private val _concept = MutableStateFlow("")

    /**
     * Descripción del gasto. Ej: "Netflix", "Gasolina camioneta".
     * Truncada a 64 chars en el commit (contrato de ExpenseEntity).
     */
    val concept: StateFlow<String> = _concept.asStateFlow()

    // ── Wallets (fuentes de pago) ──────────────────────────────────────────

    /**
     * Lista reactiva de métodos de pago activos del hogar.
     * Alimenta el SourceCarousel del modal.
     */
    val wallets: StateFlow<List<PaymentMethodEntity>> = walletRepository
        .observeActive(householdId)
        .catch { emit(emptyList()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _selectedWalletId = MutableStateFlow<String?>(null)

    /**
     * ID del wallet seleccionado en el SourceCarousel.
     * Null = ninguno seleccionado (el botón Registrar estará deshabilitado).
     */
    val selectedWalletId: StateFlow<String?> = _selectedWalletId.asStateFlow()

    // ── Miembros para atribución de beneficiarios ──────────────────────────

    /**
     * Lista reactiva de miembros activos del hogar.
     * Alimenta los FilterChips de atribución.
     */
    val members: StateFlow<List<MemberEntity>> = memberRepository
        .observeActiveMembers(householdId)
        .catch { emit(emptyList()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _selectedMemberIds = MutableStateFlow<Set<String>>(emptySet())

    /**
     * IDs de los miembros marcados como BENEFICIARY del gasto.
     * La atribución de PAYER se infiere del wallet seleccionado (ownerMemberId).
     * Si el wallet no tiene owner, el payer es el primer PAYER_ADULT del hogar.
     */
    val selectedMemberIds: StateFlow<Set<String>> = _selectedMemberIds.asStateFlow()

    // ── Estado de la operación de registro ────────────────────────────────

    private val _operationState = MutableStateFlow<CaptureOperationState>(CaptureOperationState.Idle)

    /**
     * Estado del ciclo de vida de la inserción atómica.
     * La UI observa este flujo para manejar Loading, Success y Error.
     */
    val operationState: StateFlow<CaptureOperationState> = _operationState.asStateFlow()

    // ── Validación ─────────────────────────────────────────────────────────

    /**
     * `true` si el formulario es válido para registrar:
     * - Importe > 0
     * - Concepto no vacío
     * - Wallet seleccionado
     * - Al menos un beneficiario seleccionado
     */
    val canRegister: StateFlow<Boolean> = combine(
        _rawAmount, _concept, _selectedWalletId, _selectedMemberIds
    ) { amount, concept, walletId, memberIds ->
        val amountNum = amount.replace(",", "").toDoubleOrNull() ?: 0.0
        amountNum > 0 && concept.isNotBlank() && walletId != null && memberIds.isNotEmpty()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // ─────────────────────────────────────────────────────────────────────────
    // Eventos del numpad
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Procesa la pulsación de una tecla numérica del teclado integrado.
     *
     * @param key Carácter pulsado: '0'-'9', '.', o 'DEL' para borrar.
     */
    fun onNumpadKey(key: String) {
        val current = _rawAmount.value

        when (key) {
            "DEL" -> {
                // Borrar el último carácter; si queda vacío, mostrar "0"
                val next = if (current.length <= 1) "0" else current.dropLast(1)
                _rawAmount.value = next
            }
            "." -> {
                // Solo un punto decimal permitido; ignorar si ya existe
                if (!current.contains(".")) {
                    _rawAmount.value = "$current."
                }
            }
            else -> {
                // Dígito 0-9
                // Límite: máximo 2 decimales
                val decimalIndex = current.indexOf(".")
                if (decimalIndex != -1 && current.length - decimalIndex > 2) return

                // Límite: máximo 9 dígitos enteros
                val integerPart = if (decimalIndex != -1) current.substring(0, decimalIndex)
                else current
                if (integerPart.length >= 9 && decimalIndex == -1) return

                _rawAmount.value = if (current == "0") key else "$current$key"
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Eventos de formulario
    // ─────────────────────────────────────────────────────────────────────────

    /** Actualiza el concepto libre del gasto. */
    fun onConceptChange(value: String) {
        _concept.value = value.take(64) // contrato de ExpenseEntity.concept
    }

    /** Selecciona o deselecciona un wallet como fuente de pago. */
    fun onWalletSelected(walletId: String) {
        _selectedWalletId.value = walletId
    }

    /**
     * Alterna la selección de un miembro como beneficiario.
     * Multi-select: al pulsar sobre un chip ya seleccionado, se deselecciona.
     */
    fun onMemberToggled(memberId: String) {
        _selectedMemberIds.update { current ->
            if (memberId in current) current - memberId else current + memberId
        }
    }

    /** Selecciona todos los miembros activos a la vez (botón "All / Family"). */
    fun onSelectAllMembers() {
        _selectedMemberIds.value = members.value.map { it.id }.toSet()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Acoplamiento de evento principal: Registrar
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Registra el gasto en la base de datos Room mediante una transacción atómica.
     *
     * Flujo:
     * 1. Obtiene la quincena activa del hogar.
     * 2. Construye [ExpenseEntity] con los valores del estado actual.
     * 3. Calcula las atribuciones BENEFICIARY distribuyendo los basis points
     *    equitativamente entre los miembros seleccionados.
     * 4. Construye la atribución PAYER desde el wallet → ownerMemberId.
     * 5. Delega a [ExpenseRepository.insertWithAttributions] → atómico.
     * 6. Emite [CaptureOperationState.Success] para cerrar el modal.
     *
     * **Invariante de basis points**: BENEFICIARY sum = 10,000 bps,
     * PAYER sum = 10,000 bps. El resto se asigna al último beneficiario.
     */
    fun onRegisterExpense() {
        if (_operationState.value is CaptureOperationState.Loading) return

        viewModelScope.launch {
            _operationState.value = CaptureOperationState.Loading

            try {
                // 1. Quincena activa
                val quincena = quincenaRepository.getActive(householdId)
                    ?: throw IllegalStateException("No hay quincena activa para el hogar.")

                // 2. Wallet seleccionado
                val walletId = _selectedWalletId.value
                    ?: throw IllegalStateException("Selecciona un método de pago.")

                // 3. Monto
                val amount = parsedAmount
                if (amount <= 0) throw IllegalArgumentException("El importe debe ser mayor a $0.")

                // 4. Construir ExpenseEntity
                val expenseId = UUID.randomUUID().toString()
                val nowEpoch = System.currentTimeMillis()

                val expense = ExpenseEntity(
                    id = expenseId,
                    householdId = householdId,
                    occurredAt = nowEpoch,
                    quincenaId = quincena.id,
                    // Se usa la primera categoría disponible como placeholder.
                    // TODO: Agregar sección de selección de categoría al modal en Sprint 3.
                    categoryId = "default_category",
                    concept = _concept.value.trim().take(64),
                    amountMxn = amount,
                    paymentMethodId = walletId,
                    status = "POSTED",
                    createdAt = nowEpoch
                )

                // 5. Construir atribuciones BENEFICIARY
                val beneficiaryIds = _selectedMemberIds.value.toList()
                if (beneficiaryIds.isEmpty()) {
                    throw IllegalArgumentException("Selecciona al menos un beneficiario.")
                }

                val baseBps = 10_000 / beneficiaryIds.size
                val remainder = 10_000 - (baseBps * beneficiaryIds.size)

                val attributions = mutableListOf<ExpenseAttributionEntity>()

                beneficiaryIds.forEachIndexed { index, memberId ->
                    val bps = if (index == beneficiaryIds.lastIndex) baseBps + remainder else baseBps
                    attributions.add(
                        ExpenseAttributionEntity(
                            id = UUID.randomUUID().toString(),
                            expenseId = expenseId,
                            memberId = memberId,
                            role = "BENEFICIARY",
                            shareBps = bps,
                            shareAmountMxn = amount * bps / 10_000.0
                        )
                    )
                }

                // 6. Construir atribución PAYER desde el ownerMemberId del wallet
                val wallet = walletRepository.getById(walletId)
                val payerMemberId = wallet?.ownerMemberId
                    ?: memberRepository
                        .getByRole(householdId, "PAYER_ADULT")
                        .firstOrNull()?.id
                    ?: throw IllegalStateException(
                        "No se encontró un pagador para el wallet seleccionado."
                    )

                attributions.add(
                    ExpenseAttributionEntity(
                        id = UUID.randomUUID().toString(),
                        expenseId = expenseId,
                        memberId = payerMemberId,
                        role = "PAYER",
                        shareBps = 10_000,
                        shareAmountMxn = amount
                    )
                )

                // 7. Inserción atómica (valida bps internamente)
                expenseRepository.insertWithAttributions(expense, attributions)

                _operationState.value = CaptureOperationState.Success(expenseId)

            } catch (e: Exception) {
                _operationState.value = CaptureOperationState.Error(
                    e.message ?: "Error al registrar el gasto. Intenta de nuevo."
                )
            }
        }
    }

    /**
     * Resetea el estado de la operación a [CaptureOperationState.Idle].
     * Debe llamarse desde la UI después de manejar Success o Error.
     */
    fun onOperationStateConsumed() {
        _operationState.value = CaptureOperationState.Idle
    }

    /**
     * Limpia todos los campos del formulario al cerrar el modal.
     */
    fun onDismiss() {
        _rawAmount.value = "0"
        _concept.value = ""
        _selectedWalletId.value = null
        _selectedMemberIds.value = emptySet()
        _operationState.value = CaptureOperationState.Idle
    }

    // ── Extensión privada para combine con 4 flows ────────────────────────────
    private fun <T1, T2, T3, T4, R> combine(
        flow1: StateFlow<T1>,
        flow2: StateFlow<T2>,
        flow3: StateFlow<T3>,
        flow4: StateFlow<T4>,
        transform: suspend (T1, T2, T3, T4) -> R
    ) = kotlinx.coroutines.flow.combine(flow1, flow2, flow3, flow4, transform)
}
