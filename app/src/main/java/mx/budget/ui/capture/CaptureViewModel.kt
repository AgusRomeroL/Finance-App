package mx.budget.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.budget.ai.proactive.AttributionSuggestion
import mx.budget.ai.proactive.RetroAttributionEngine
import mx.budget.data.local.entity.CategoryEntity
import mx.budget.data.local.entity.ExpenseAttributionEntity
import mx.budget.data.local.entity.ExpenseEntity
import mx.budget.data.local.entity.MemberEntity
import mx.budget.data.local.entity.PaymentMethodEntity
import mx.budget.data.location.LocationProvider
import mx.budget.data.location.LocationSource
import mx.budget.data.repository.CategoryRepository
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

/**
 * Sugerencia de atribución para la captura en vivo (Feature A, §F.4).
 *
 * Combina las dos dimensiones en una sola tarjeta, pero cada lado es
 * **independiente**: solo se incluye el que superó el umbral de confianza
 * ([CaptureViewModel.SUGGEST_THRESHOLD]). Así el caso mixto (un rol predecible,
 * el otro ruidoso) muestra solo el lado fiable.
 */
data class CaptureSuggestion(
    val beneficiary: AttributionSuggestion?,
    val payer: AttributionSuggestion?,
) {
    /** `true` si al menos una dimensión superó el umbral (si no, no se muestra chip). */
    val hasAny: Boolean get() = beneficiary != null || payer != null
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
 * @param categoryRepository Repositorio para listar categorías.
 * @param retroAttributionEngine Motor de inferencia para la sugerencia en vivo (Feature A).
 * @param locationProvider   Proveedor de ubicación on-device (§G.4); fix al registrar.
 * @param householdId        ID del hogar activo.
 */
class CaptureViewModel(
    private val expenseRepository: ExpenseRepository,
    private val quincenaRepository: QuincenaRepository,
    private val walletRepository: WalletRepository,
    private val memberRepository: MemberRepository,
    private val categoryRepository: CategoryRepository,
    private val retroAttributionEngine: RetroAttributionEngine,
    private val locationProvider: LocationProvider,
    private val householdId: String
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

    // ── Sugerencia de atribución en vivo (Feature A, §F.4) ──────────────────
    //
    // Mientras el usuario teclea el concepto, el motor de B infiere la atribución
    // aprendida del historial. Es propose-then-confirm: NUNCA auto-aplica; el chip
    // es visible pero ignorable (no tocarlo = señal negativa implícita). Solo se
    // muestra un lado (BENEFICIARY/PAYER) si supera el umbral de confianza.

    /**
     * Sugerencia combinada (BENEFICIARY/PAYER) para el chip de captura, o `null`
     * si el concepto está en blanco o ninguna dimensión supera el umbral.
     *
     * `debounce(250)` evita una query por pulsación; `mapLatest` cancela la
     * inferencia anterior al seguir tecleando; `catch` garantiza que un fallo del
     * motor jamás tumbe la captura (el flujo manual queda intacto).
     */
    @OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val attributionSuggestion: StateFlow<CaptureSuggestion?> = _concept
        .debounce(250)
        .map { it.trim() }
        .distinctUntilChanged()
        .mapLatest { concept ->
            if (concept.isBlank()) return@mapLatest null
            val benef = retroAttributionEngine.suggest(concept, "BENEFICIARY")
                ?.takeIf { it.confidence >= SUGGEST_THRESHOLD }
            val payer = retroAttributionEngine.suggest(concept, "PAYER")
                ?.takeIf { it.confidence >= SUGGEST_THRESHOLD }
            CaptureSuggestion(benef, payer).takeIf { it.hasAny }
        }
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ── Categorías ─────────────────────────────────────────────────────────

    val categories: StateFlow<List<CategoryEntity>> = categoryRepository
        .observeAll(householdId)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedCategoryId = MutableStateFlow<String?>(null)
    val selectedCategoryId: StateFlow<String?> = _selectedCategoryId.asStateFlow()

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
        .map { list -> list.filter { !it.role.startsWith("EXTERNAL_") } }
        .catch { emit(emptyList()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    // Atribución en DOS dimensiones, como mapas memberId → porcentaje (0-100).
    // Cada dimensión debe sumar 100 para registrar. Convertimos a basis points
    // (×100) al guardar, con ajuste de resto para sumar exactamente 10,000.
    private val _beneficiaryShares = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val _payerShares = MutableStateFlow<Map<String, Int>>(emptyMap())

    /** BENEFICIARY: quién consume → porcentaje por miembro. */
    val beneficiaryShares: StateFlow<Map<String, Int>> = _beneficiaryShares.asStateFlow()

    /** PAYER: quién paga → porcentaje por miembro (default = dueño del wallet). */
    val payerShares: StateFlow<Map<String, Int>> = _payerShares.asStateFlow()

    /**
     * Aplica la sugerencia de atribución vigente (Feature A): rellena SOLO las
     * dimensiones presentes en el chip (el caso mixto no pisa la dimensión que el
     * usuario tendría que elegir a mano). Convierte bps→% con el mismo invariante
     * que el commit (el último miembro absorbe el resto para sumar 100 exacto).
     * No cierra el sheet ni registra nada: el usuario sigue editando/confirmando.
     */
    fun applySuggestion() {
        val suggestion = attributionSuggestion.value ?: return
        suggestion.beneficiary?.let { _beneficiaryShares.value = bpsToPercent(it.distribution) }
        suggestion.payer?.let { _payerShares.value = bpsToPercent(it.distribution) }
    }

    /** Convierte un mapa memberId→bps (suma 10,000) a memberId→% (suma 100). */
    private fun bpsToPercent(distribution: Map<String, Int>): Map<String, Int> {
        val entries = distribution.entries.toList()
        if (entries.isEmpty()) return emptyMap()
        var assigned = 0
        return entries.mapIndexed { i, (memberId, bps) ->
            val pct = if (i == entries.lastIndex) 100 - assigned
            else (bps / 100).also { assigned += it }
            memberId to pct
        }.toMap()
    }

    /** Reparte 100% equitativamente entre [ids], con el resto en el último. */
    private fun equalSplit(ids: Collection<String>): Map<String, Int> {
        val list = ids.toList()
        if (list.isEmpty()) return emptyMap()
        val base = 100 / list.size
        val remainder = 100 - base * list.size
        return list.mapIndexed { i, id ->
            id to if (i == list.lastIndex) base + remainder else base
        }.toMap()
    }

    // ── Estado de la operación de registro ────────────────────────────────

    private val _operationState = MutableStateFlow<CaptureOperationState>(CaptureOperationState.Idle)

    /**
     * Estado del ciclo de vida de la inserción atómica.
     * La UI observa este flujo para manejar Loading, Success y Error.
     */
    val operationState: StateFlow<CaptureOperationState> = _operationState.asStateFlow()

    // ── Validación ─────────────────────────────────────────────────────────

    /**
     * `true` si el formulario es válido para registrar (brief C11 — campos mínimos):
     * - Importe > 0, wallet y categoría seleccionados.
     * - Beneficiarios y pagadores cada uno **sumando exactamente 100%**.
     *
     * El concepto es OPCIONAL (vive bajo "Más"); si se omite, se usa el nombre
     * de la categoría como default en [onRegisterExpense].
     */
    val canRegister: StateFlow<Boolean> = combine(
        _rawAmount, _selectedWalletId, _selectedCategoryId, _beneficiaryShares, _payerShares
    ) { amount, walletId, categoryId, benef, payer ->
        val amountNum = amount.replace(",", "").toDoubleOrNull() ?: 0.0
        amountNum > 0 && walletId != null && categoryId != null &&
            benef.isNotEmpty() && benef.values.sum() == 100 &&
            payer.isNotEmpty() && payer.values.sum() == 100
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

    /**
     * Selecciona un wallet como fuente de pago. Si aún no hay pagadores
     * elegidos, siembra el pagador con el dueño de la cuenta al 100% (default
     * conveniente, sobreescribible). Si el wallet no tiene dueño, deja al primer
     * PAYER_ADULT del hogar.
     */
    fun onWalletSelected(walletId: String) {
        _selectedWalletId.value = walletId
        if (_payerShares.value.isEmpty()) {
            val owner = wallets.value.firstOrNull { it.id == walletId }?.ownerMemberId
                ?: members.value.firstOrNull { it.role == "PAYER_ADULT" }?.id
            if (owner != null) _payerShares.value = mapOf(owner to 100)
        }
    }

    /** Selecciona una categoría. */
    fun onCategorySelected(categoryId: String) {
        _selectedCategoryId.value = categoryId
    }

    // ── Beneficiarios (quién consume) ──────────────────────────────────────────

    /** Alterna un beneficiario; al cambiar el conjunto se reparte equitativo. */
    fun onBeneficiaryToggled(memberId: String) {
        val ids = _beneficiaryShares.value.keys.let {
            if (memberId in it) it - memberId else it + memberId
        }
        _beneficiaryShares.value = equalSplit(ids)
    }

    /** Ajusta el % de un beneficiario en pasos (clamp 0-100). */
    fun onBeneficiaryShareDelta(memberId: String, delta: Int) {
        val cur = _beneficiaryShares.value
        if (memberId !in cur) return
        _beneficiaryShares.value = cur + (memberId to (cur.getValue(memberId) + delta).coerceIn(0, 100))
    }

    /** Selecciona a todo el hogar como beneficiario (reparto equitativo). */
    fun onSelectAllMembers() {
        _beneficiaryShares.value = equalSplit(members.value.map { it.id })
    }

    /** Limpia los beneficiarios (para alternar el chip "Todos"). */
    fun onClearMembers() {
        _beneficiaryShares.value = emptyMap()
    }

    // ── Pagadores (quién paga) ─────────────────────────────────────────────────

    /** Alterna un pagador; al cambiar el conjunto se reparte equitativo. */
    fun onPayerToggled(memberId: String) {
        val ids = _payerShares.value.keys.let {
            if (memberId in it) it - memberId else it + memberId
        }
        _payerShares.value = equalSplit(ids)
    }

    /** Ajusta el % de un pagador en pasos (clamp 0-100). */
    fun onPayerShareDelta(memberId: String, delta: Int) {
        val cur = _payerShares.value
        if (memberId !in cur) return
        _payerShares.value = cur + (memberId to (cur.getValue(memberId) + delta).coerceIn(0, 100))
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

                // 3. Categoría seleccionada
                val categoryId = _selectedCategoryId.value
                    ?: throw IllegalStateException("Selecciona una categoría.")

                // 4. Monto
                val amount = parsedAmount
                if (amount <= 0) throw IllegalArgumentException("El importe debe ser mayor a $0.")

                // 5. Construir ExpenseEntity (concepto opcional → default = nombre de categoría)
                val expenseId = UUID.randomUUID().toString()
                val nowEpoch = System.currentTimeMillis()
                val categoryName = categories.value.firstOrNull { it.id == categoryId }?.displayName
                val conceptValue = _concept.value.trim().ifBlank { categoryName ?: "Gasto" }.take(64)

                // Ubicación (§G.4): el sheet está en foreground → fix fresco si el
                // nivel/permiso lo permiten. Best-effort: si no, queda NONE y la
                // captura procede igual.
                val fix = locationProvider.currentFix(requireForeground = true)

                val expense = ExpenseEntity(
                    id = expenseId,
                    householdId = householdId,
                    occurredAt = nowEpoch,
                    quincenaId = quincena.id,
                    categoryId = categoryId,
                    concept = conceptValue,
                    amountMxn = amount,
                    paymentMethodId = walletId,
                    status = "POSTED",
                    createdAt = nowEpoch,
                    latitude = fix?.latitude,
                    longitude = fix?.longitude,
                    placeLabel = fix?.placeLabel,
                    locationSource = if (fix != null) LocationSource.CAPTURE else LocationSource.NONE,
                )

                // 6. Construir atribuciones de AMBAS dimensiones desde los shares (%).
                val beneficiaryShares = _beneficiaryShares.value
                val payerShares = _payerShares.value
                if (beneficiaryShares.isEmpty() || beneficiaryShares.values.sum() != 100) {
                    throw IllegalArgumentException("La atribución de beneficiarios debe sumar 100%.")
                }
                if (payerShares.isEmpty() || payerShares.values.sum() != 100) {
                    throw IllegalArgumentException("La atribución de pagadores debe sumar 100%.")
                }

                // % → basis points; el último absorbe el resto para sumar 10,000 exacto.
                fun buildRows(shares: Map<String, Int>, role: String): List<ExpenseAttributionEntity> {
                    val entries = shares.entries.toList()
                    var assigned = 0
                    return entries.mapIndexed { i, (memberId, pct) ->
                        val bps = if (i == entries.lastIndex) 10_000 - assigned
                        else (pct * 100).also { assigned += it }
                        ExpenseAttributionEntity(
                            id = UUID.randomUUID().toString(),
                            expenseId = expenseId,
                            memberId = memberId,
                            role = role,
                            shareBps = bps,
                            shareAmountMxn = amount * bps / 10_000.0
                        )
                    }
                }

                val attributions = buildRows(beneficiaryShares, "BENEFICIARY") +
                    buildRows(payerShares, "PAYER")

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
        _selectedCategoryId.value = null
        _beneficiaryShares.value = emptyMap()
        _payerShares.value = emptyMap()
        _operationState.value = CaptureOperationState.Idle
    }

    companion object {
        /**
         * Umbral de confianza para MOSTRAR un lado de la sugerencia en el chip.
         * Distinto del [RetroAttributionEngine.AUTO_APPLY_THRESHOLD] (0.7): aquí
         * solo sugerimos, nunca aplicamos solos, así que el listón es más bajo.
         */
        const val SUGGEST_THRESHOLD = 0.5
    }

    // ── Extensión privada para combine con 5 flows ────────────────────────────
    private fun <T1, T2, T3, T4, T5, R> combine(
        flow1: StateFlow<T1>,
        flow2: StateFlow<T2>,
        flow3: StateFlow<T3>,
        flow4: StateFlow<T4>,
        flow5: StateFlow<T5>,
        transform: suspend (T1, T2, T3, T4, T5) -> R
    ) = kotlinx.coroutines.flow.combine(flow1, flow2, flow3, flow4, flow5, transform)
}
