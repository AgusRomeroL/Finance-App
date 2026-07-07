package mx.budget.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.budget.ai.proactive.AttributionSuggestion
import mx.budget.ai.proactive.RetroAttributionEngine
import mx.budget.data.local.dao.CategoryDao
import mx.budget.data.local.dao.ExpenseDao
import mx.budget.data.local.dao.PendingCaptureDao
import mx.budget.data.local.dao.QuincenaDao
import mx.budget.data.local.entity.CategoryEntity
import mx.budget.data.local.entity.ExpenseAttributionEntity
import mx.budget.data.local.entity.ExpenseEntity
import mx.budget.data.local.entity.IncomeSourceEntity
import mx.budget.data.local.entity.MemberEntity
import mx.budget.data.local.entity.PaymentMethodEntity
import mx.budget.data.local.entity.PendingCaptureEntity
import mx.budget.data.local.entity.QuincenaEntity
import org.json.JSONObject
import mx.budget.data.location.LocationProvider
import mx.budget.data.location.LocationSource
import mx.budget.data.repository.CategoryRepository
import mx.budget.data.repository.ExpenseRepository
import mx.budget.data.repository.IncomeRepository
import mx.budget.data.repository.MemberRepository
import mx.budget.data.repository.QuincenaRepository
import mx.budget.data.repository.WalletRepository
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
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
    private val householdId: String,
    /**
     * Callback opcional para cerrar el ciclo "revisar al confirmar" (§G.3): tras
     * registrar un gasto que vino de una captura pendiente, marca esa captura como
     * CONFIRMED (y cancela su notificación). null = la hoja no nació de una captura.
     */
    private val onPendingConfirmed: (suspend (String) -> Unit)? = null,
    /** Repo de ingresos (paquete A3): camino idéntico a WalletsViewModel.recordIncome. */
    private val incomeRepository: IncomeRepository? = null,
    /** DAO para las categorías recientes reales (v13, `observeRecentCategoryIds`). */
    private val expenseDao: ExpenseDao? = null,
    /** DAO para el autocompletado anti-duplicados (v13, `searchLeavesByName`). */
    private val categoryDao: CategoryDao? = null,
    /** DAO para resolver la quincena de una fecha arbitraria (DatePicker, A3). */
    private val quincenaDao: QuincenaDao? = null,
    /** Fallback para marcar CONFIRMED una pending_capture cuando no hay callback. */
    private val pendingCaptureDao: PendingCaptureDao? = null,
    /**
     * Fase 6 (colaboradores): al aceptar una pending `proposal:{docId}` se
     * escribe status=ACCEPTED + expenseId en la propuesta remota, para que la
     * web del colaborador refleje el resultado. Opcional (emulador sin red OK).
     */
    private val membershipRepository: mx.budget.data.remote.MembershipRepository? = null,
) : ViewModel() {

    /** Zona canónica del hogar (spec: America/Mexico_City). */
    private val zone: ZoneId = ZoneId.of("America/Mexico_City")

    // ── Tipo de movimiento (toggle Gasto/Ingreso) ──────────────────────────

    private val _captureKind = MutableStateFlow(CaptureKind.EXPENSE)

    /** Gasto o ingreso: conmuta el contenido del sheet y la ruta de registro. */
    val captureKind: StateFlow<CaptureKind> = _captureKind.asStateFlow()

    /** Cambia el tipo de movimiento (toggle del header). */
    fun onKindChange(kind: CaptureKind) {
        _captureKind.value = kind
    }

    // ── Miembro que genera el ingreso (solo modo INCOME) ───────────────────

    private val _incomeMemberId = MutableStateFlow<String?>(null)

    /** Miembro que genera el ingreso (Benjamín, Norma…); requerido en INCOME. */
    val incomeMemberId: StateFlow<String?> = _incomeMemberId.asStateFlow()

    /** Selecciona quién genera el ingreso. */
    fun onIncomeMemberSelected(memberId: String) {
        _incomeMemberId.value = memberId
    }

    // ── Fecha del movimiento (DatePicker M3) ───────────────────────────────

    private val _selectedDate = MutableStateFlow<LocalDate?>(null)

    /** Fecha elegida en el DatePicker; null = hoy (default). */
    val selectedDate: StateFlow<LocalDate?> = _selectedDate.asStateFlow()

    /** Fija la fecha del movimiento; null restablece "hoy". */
    fun onDateSelected(date: LocalDate?) {
        _selectedDate.value = date
    }

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

    // ── Notas libres (viven bajo "Más") ────────────────────────────────────────

    private val _notes = MutableStateFlow("")

    /** Nota libre opcional del gasto (detalle que no es el concepto). */
    val notes: StateFlow<String> = _notes.asStateFlow()

    /** Actualiza la nota libre. */
    fun onNotesChange(value: String) {
        _notes.value = value.take(280)
    }

    // ── Captura pendiente en revisión (§G.3, "revisar al confirmar") ────────────
    // Si la hoja se abrió prellenada desde una propuesta de la bandeja, guardamos su
    // id para marcarla CONFIRMED al registrar (no se queda colgada en la bandeja).
    private var pendingCaptureId: String? = null

    /**
     * Prellena la hoja desde una captura pendiente rica (§G.3): monto, concepto,
     * categoría, wallet, beneficiarios/pagadores (bps→%) y notas. El usuario revisa
     * y edita antes de registrar; al registrar se marca la captura CONFIRMED.
     */
    fun prefillFromPending(capture: PendingCaptureEntity) {
        pendingCaptureId = capture.id
        val a = capture.amountMxn
        _rawAmount.value = if (a % 1.0 == 0.0) a.toLong().toString() else a.toString()
        _concept.value = capture.concept.take(64)
        _notes.value = capture.notes.orEmpty().take(280)
        capture.suggestedCategoryId?.let { _selectedCategoryId.value = it }

        val benef = jsonToPercent(capture.suggestedBeneficiaryJson)
        if (benef.isNotEmpty()) _beneficiaryShares.value = benef
        val payer = jsonToPercent(capture.suggestedPayerJson)
        if (payer.isNotEmpty()) _payerShares.value = payer

        // Wallet: si no vino pagador en la frase, sembramos al dueño de la cuenta
        // (mismo default que [onWalletSelected]).
        capture.suggestedWalletId?.let { walletId ->
            _selectedWalletId.value = walletId
            if (_payerShares.value.isEmpty()) {
                val owner = wallets.value.firstOrNull { it.id == walletId }?.ownerMemberId
                    ?: members.value.firstOrNull { it.role == "PAYER_ADULT" }?.id
                if (owner != null) _payerShares.value = mapOf(owner to 100)
            }
        }
    }

    /** Parsea un JSON `{memberId: bps}` a `memberId → %` (suma 100), o vacío. */
    private fun jsonToPercent(json: String?): Map<String, Int> {
        if (json.isNullOrBlank()) return emptyMap()
        val obj = runCatching { JSONObject(json) }.getOrNull() ?: return emptyMap()
        val bps = LinkedHashMap<String, Int>()
        obj.keys().forEach { k -> bps[k] = obj.optInt(k) }
        return bpsToPercent(bps)
    }

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

    // ── Recientes reales (A3): últimas categorías usadas en gastos POSTED ───

    /**
     * Categorías "recientes" reales: ids por último uso ([ExpenseDao.observeRecentCategoryIds],
     * orden de recencia preservado) mapeados a entidades. Si hay menos de 5,
     * completa con las hojas de menor `sortOrder` (fallback estable para el
     * primer arranque).
     */
    val recentCategories: StateFlow<List<CategoryEntity>> = combine(
        expenseDao?.observeRecentCategoryIds(householdId, RECENT_LIMIT)
            ?.catch { emit(emptyList()) } ?: flowOf(emptyList()),
        categories,
    ) { recentIds, cats ->
        val leaves = cats.filter { it.parentId != null }
        val byId = leaves.associateBy { it.id }
        val recents = recentIds.mapNotNull { byId[it] }
        val fill = leaves
            .filter { leaf -> recents.none { it.id == leaf.id } }
            .sortedBy { it.sortOrder }
        (recents + fill).take(RECENT_LIMIT)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Autocompletado de categoría anti-duplicados (A3) ────────────────────

    private val _categoryQuery = MutableStateFlow("")

    /** Actualiza la búsqueda de categoría (alimenta [categorySearchResults]). */
    fun onCategoryQueryChange(query: String) {
        _categoryQuery.value = query
    }

    /**
     * Sugerencias "¿Quisiste decir…?" para la búsqueda de categoría, vía
     * [CategoryDao.searchLeavesByName] (LIKE sobre displayName entre hojas).
     * Con debounce para no lanzar una query por pulsación.
     */
    @OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val categorySearchResults: StateFlow<List<CategoryEntity>> = _categoryQuery
        .debounce(150)
        .map { it.trim() }
        .distinctUntilChanged()
        .mapLatest { query ->
            if (query.isBlank()) emptyList()
            else categoryDao?.searchLeavesByName(householdId, query, 8)
                ?: categories.value
                    .filter { it.parentId != null && it.displayName.contains(query, ignoreCase = true) }
                    .take(8)
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Crea una categoría hoja inline y la deja seleccionada (A3 §4).
     *
     * - id UUID; code = `PADRE.NOMBRE_NORMALIZADO` (sin acentos, espacios→_,
     *   uppercase) con sufijo numérico si colisiona (índice único household+code);
     * - kind heredado del padre (o EXPENSE_VARIABLE);
     * - sortOrder = max+1 del grupo.
     *
     * `categoryRepository.insert` ya estampa `updated_at` y encola CATEGORY al
     * sync (infra A0), así que la sincronización ocurre sola.
     */
    fun onCreateCategory(displayName: String, parentId: String) {
        val name = displayName.trim().take(48)
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                val all = categories.value
                val parent = all.firstOrNull { it.id == parentId }
                val siblings = all.filter { it.parentId == parentId }
                val baseCode = "${parent?.code ?: "OTROS"}.${normalizeCodeSegment(name)}"
                val existingCodes = all.map { it.code }.toSet()
                var code = baseCode
                var suffix = 2
                while (code in existingCodes) code = "${baseCode}_${suffix++}"

                val entity = CategoryEntity(
                    id = UUID.randomUUID().toString(),
                    householdId = householdId,
                    parentId = parentId,
                    code = code,
                    displayName = name,
                    kind = parent?.kind ?: "EXPENSE_VARIABLE",
                    sortOrder = (siblings.maxOfOrNull { it.sortOrder } ?: 0) + 1,
                )
                categoryRepository.insert(entity)
                _selectedCategoryId.value = entity.id
            } catch (e: Exception) {
                _operationState.value = CaptureOperationState.Error(
                    e.message ?: "No se pudo crear la categoría."
                )
            }
        }
    }

    /** "Niños & Juguetes" → "NINOS_JUGUETES" (sin acentos ni símbolos). */
    private fun normalizeCodeSegment(name: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^A-Za-z0-9]+"), "_")
            .trim('_')
            .uppercase(Locale.ROOT)
            .ifBlank { "CATEGORIA" }

    // ── Wallets (fuentes de pago) ──────────────────────────────────────────

    /**
     * Lista reactiva de métodos de pago activos del hogar.
     * Alimenta el SourceCarousel del modal.
     */
    val wallets: StateFlow<List<PaymentMethodEntity>> = walletRepository
        .observeActive(householdId)
        // El wallet virtual "Pagado por terceros" (kind=EXTERNAL, Fase B/B3) NO es una
        // fuente de pago normal: se selecciona vía el toggle "pagó un tercero", no en
        // el carrusel de cuentas.
        .map { list -> list.filter { it.kind != "EXTERNAL" } }
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

    /**
     * TODOS los miembros activos (incluidos dependientes y EXTERNAL_*), para el
     * selector de "¿Quién pagó?" con tercero (Fase B, B3). El caso normal usa
     * [members] (solo adultos/dependientes del hogar para atribución de beneficio).
     */
    val allMembers: StateFlow<List<MemberEntity>> = memberRepository
        .observeActiveMembers(householdId)
        .catch { emit(emptyList()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    // ── "¿Quién pagó?" con tercero (Fase B, B3) ─────────────────────────────────
    //
    // Cuando el gasto lo adelanta alguien que NO es una cuenta del hogar (un hijo,
    // un familiar, un tercero), se registra contra el wallet virtual EXTERNAL sin
    // mover saldos reales. El usuario elige si se le reembolsará o si lo absorbe.

    /** Cómo se salda un gasto pagado por un tercero. */
    enum class ThirdPartyMode { REIMBURSE, ABSORB }

    private val _thirdPartyPayerId = MutableStateFlow<String?>(null)

    /** Miembro/tercero que adelantó el gasto (null = pago normal con wallet real). */
    val thirdPartyPayerId: StateFlow<String?> = _thirdPartyPayerId.asStateFlow()

    private val _thirdPartyMode = MutableStateFlow(ThirdPartyMode.REIMBURSE)

    /** Se le reembolsará (default) o lo absorbe; solo aplica si hay tercero. */
    val thirdPartyMode: StateFlow<ThirdPartyMode> = _thirdPartyMode.asStateFlow()

    /** Selecciona/deselecciona el tercero que pagó (null limpia y vuelve a pago normal). */
    fun onThirdPartyPayerSelected(memberId: String?) {
        _thirdPartyPayerId.value = memberId
    }

    /** Cambia el modo de liquidación del tercero (reembolsar / absorber). */
    fun onThirdPartyModeChange(mode: ThirdPartyMode) {
        _thirdPartyMode.value = mode
    }

    /**
     * Crea un tercero nuevo (rol EXTERNAL_DEBTOR) desde el texto que tecleó el usuario
     * y lo deja seleccionado como pagador tercero. Idempotente por nombre: si ya existe
     * un miembro con ese displayName, lo reutiliza (el índice único household+display_name
     * lo impediría de todas formas).
     */
    fun onCreateExternalPayer(name: String) {
        val trimmed = name.trim().take(48)
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            try {
                val existing = allMembers.value.firstOrNull { it.displayName.equals(trimmed, ignoreCase = true) }
                if (existing != null) {
                    _thirdPartyPayerId.value = existing.id
                    return@launch
                }
                val member = MemberEntity(
                    id = UUID.randomUUID().toString(),
                    householdId = householdId,
                    displayName = trimmed,
                    role = "EXTERNAL_DEBTOR",
                    isActive = true,
                    updatedAt = System.currentTimeMillis(),
                )
                memberRepository.insert(member)
                _thirdPartyPayerId.value = member.id
            } catch (e: Exception) {
                _operationState.value = CaptureOperationState.Error(
                    e.message ?: "No se pudo crear el tercero."
                )
            }
        }
    }

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

    // ── Modo Review (SP-A1): prellenado + campos "Por decidir" ───────────────

    private val _reviewMissing = MutableStateFlow<Set<CaptureField>>(emptySet())

    /**
     * Campos declarados faltantes por el origen ([CaptureSheetMode.Review]) que
     * el usuario AÚN no resuelve. Vacío fuera de Review o cuando todo quedó
     * decidido. La UI los marca "Por decidir" y [canRegister] los bloquea.
     */
    val unresolvedFields: StateFlow<Set<CaptureField>> = combine(
        _reviewMissing, _rawAmount, _concept, _selectedCategoryId,
        _selectedWalletId, _beneficiaryShares, _payerShares, _selectedDate,
    ) { values: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val missing = values[0] as Set<CaptureField>
        if (missing.isEmpty()) emptySet()
        else buildSet {
            val amount = (values[1] as String).replace(",", "").toDoubleOrNull() ?: 0.0
            if (CaptureField.AMOUNT in missing && amount <= 0) add(CaptureField.AMOUNT)
            if (CaptureField.CONCEPT in missing && (values[2] as String).isBlank()) add(CaptureField.CONCEPT)
            if (CaptureField.CATEGORY in missing && values[3] == null) add(CaptureField.CATEGORY)
            if (CaptureField.WALLET in missing && values[4] == null) add(CaptureField.WALLET)
            @Suppress("UNCHECKED_CAST")
            val benef = values[5] as Map<String, Int>
            if (CaptureField.BENEFICIARY in missing && benef.values.sum() != 100) add(CaptureField.BENEFICIARY)
            @Suppress("UNCHECKED_CAST")
            val payer = values[6] as Map<String, Int>
            if (CaptureField.PAYER in missing && payer.values.sum() != 100) add(CaptureField.PAYER)
            if (CaptureField.DATE in missing && values[7] == null) add(CaptureField.DATE)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /**
     * Aplica el modo con el que se abrió la hoja (SP-A1). Idempotente por modo:
     * la UI lo invoca en un `LaunchedEffect(mode)`. [CaptureSheetMode.New] e
     * [CaptureSheetMode.Income] solo fijan el tipo (no pisan un prellenado hecho
     * vía [prefillFromPending]); [CaptureSheetMode.Review] prellena y marca los
     * campos por decidir.
     */
    fun applyMode(mode: CaptureSheetMode) {
        when (mode) {
            CaptureSheetMode.New -> _captureKind.value = CaptureKind.EXPENSE
            CaptureSheetMode.Income -> _captureKind.value = CaptureKind.INCOME
            is CaptureSheetMode.Review -> {
                _captureKind.value = CaptureKind.EXPENSE
                _reviewMissing.value = mode.missingFields
                val p = mode.prefill
                p.amountMxn?.takeIf { it > 0 }?.let { _rawAmount.value = formatRawAmount(it) }
                p.concept?.let { _concept.value = it.take(64) }
                p.categoryId?.let { _selectedCategoryId.value = it }
                p.walletId?.let { _selectedWalletId.value = it }
                p.occurredAt?.let {
                    _selectedDate.value = Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
                }
                p.beneficiaryBps?.takeIf { it.isNotEmpty() }
                    ?.let { _beneficiaryShares.value = bpsToPercent(it) }
                p.payerBps?.takeIf { it.isNotEmpty() }
                    ?.let { bps ->
                        _payerShares.value = bpsToPercent(bps)
                        // Fase 6 (colaboradores): si el pagador sugerido es UN solo
                        // miembro que NO es adulto pagador (un hijo colaborador, un
                        // tercero), se preactiva el camino "alguien más pagó" +
                        // reembolsable — el gasto saldrá con external_payer y
                        // settlement PENDING_REIMBURSEMENT y caerá en "Por
                        // reembolsar". El usuario puede cambiarlo antes de registrar.
                        val soloPayer = bps.keys.singleOrNull()
                        if (soloPayer != null) {
                            viewModelScope.launch {
                                val m = runCatching { memberRepository.getById(soloPayer) }.getOrNull()
                                if (m != null && m.role != "PAYER_ADULT") {
                                    _thirdPartyPayerId.value = m.id
                                    _thirdPartyMode.value = ThirdPartyMode.REIMBURSE
                                }
                            }
                        }
                    }
                p.notes?.let { _notes.value = it.take(280) }
                pendingCaptureId = p.pendingCaptureId
            }
        }
    }

    /** 45.0 → "45"; 45.5 → "45.5" (formato del numpad). */
    private fun formatRawAmount(amount: Double): String =
        if (amount % 1.0 == 0.0) amount.toLong().toString() else amount.toString()

    // ── Validación ─────────────────────────────────────────────────────────

    /**
     * `true` si el formulario es válido para registrar (brief C11 — campos mínimos):
     * - EXPENSE: importe > 0, wallet y categoría seleccionados; beneficiarios y
     *   pagadores cada uno **sumando exactamente 100%**; y en modo Review, sin
     *   campos "Por decidir" pendientes ([unresolvedFields] vacío).
     * - INCOME: importe > 0, cuenta destino y miembro que genera el ingreso.
     *
     * El concepto es OPCIONAL; si se omite, se usa el nombre de la categoría
     * (gasto) o "Ingreso" como default.
     */
    val canRegister: StateFlow<Boolean> = combine(
        combine(
            _captureKind, _rawAmount, _selectedWalletId, _selectedCategoryId,
        ) { k, a, w, c -> arrayOf(k, a, w, c) },
        combine(
            _beneficiaryShares, _payerShares, _incomeMemberId, unresolvedFields,
        ) { b, p, m, u -> arrayOf(b, p, m, u) },
        _thirdPartyPayerId,
    ) { g1, g2, thirdPartyId ->
        val kind = g1[0] as CaptureKind
        val amountNum = (g1[1] as String).replace(",", "").toDoubleOrNull() ?: 0.0
        val walletId = g1[2] as String?
        when (kind) {
            CaptureKind.INCOME -> amountNum > 0 && walletId != null && g2[2] != null
            CaptureKind.EXPENSE -> {
                @Suppress("UNCHECKED_CAST")
                val benef = g2[0] as Map<String, Int>
                @Suppress("UNCHECKED_CAST")
                val payer = g2[1] as Map<String, Int>
                @Suppress("UNCHECKED_CAST")
                val unresolved = g2[3] as Set<CaptureField>
                // Con tercero (Fase B/B3): el wallet lo pone el sistema (EXTERNAL) y el
                // PAYER es el tercero, así que no se exigen ni wallet ni payer del form.
                val walletOk = thirdPartyId != null || walletId != null
                val payerOk = thirdPartyId != null || (payer.isNotEmpty() && payer.values.sum() == 100)
                amountNum > 0 && walletOk && g1[3] != null &&
                    benef.isNotEmpty() && benef.values.sum() == 100 &&
                    payerOk && unresolved.isEmpty()
            }
        }
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
                // 1. Quincena de la fecha elegida (default hoy → la activa).
                val chosenDate = _selectedDate.value
                val quincena = resolveQuincena(chosenDate)

                // 2. Wallet seleccionado. Si pagó un tercero (Fase B/B3), el gasto se
                // carga al wallet virtual EXTERNAL (no mueve saldos reales) en vez de
                // la cuenta del carrusel.
                val thirdPartyId = _thirdPartyPayerId.value
                val walletId = if (thirdPartyId != null) {
                    expenseRepository.ensureExternalWallet(householdId).id
                } else {
                    _selectedWalletId.value
                        ?: throw IllegalStateException("Selecciona un método de pago.")
                }

                // 3. Categoría seleccionada
                val categoryId = _selectedCategoryId.value
                    ?: throw IllegalStateException("Selecciona una categoría.")

                // 4. Monto
                val amount = parsedAmount
                if (amount <= 0) throw IllegalArgumentException("El importe debe ser mayor a $0.")

                // 5. Construir ExpenseEntity (concepto opcional → default = nombre de categoría)
                val expenseId = UUID.randomUUID().toString()
                val nowEpoch = System.currentTimeMillis()
                // Fecha elegida → mediodía local (evita brincos de quincena por TZ);
                // sin fecha explícita → instante actual (comportamiento original).
                val occurredEpoch = chosenDate
                    ?.atTime(12, 0)?.atZone(zone)?.toInstant()?.toEpochMilli()
                    ?: nowEpoch
                val categoryName = categories.value.firstOrNull { it.id == categoryId }?.displayName
                val conceptValue = _concept.value.trim().ifBlank { categoryName ?: "Gasto" }.take(64)

                // Ubicación (§G.4): el sheet está en foreground → fix fresco si el
                // nivel/permiso lo permiten. Best-effort: si no, queda NONE y la
                // captura procede igual.
                val fix = locationProvider.currentFix(requireForeground = true)

                // Estado de liquidación (Fase B/B3): NONE en el caso normal; si pagó un
                // tercero, PENDING_REIMBURSEMENT (se le repondrá) o ABSORBED (no).
                val settlementStatus = when {
                    thirdPartyId == null -> "NONE"
                    _thirdPartyMode.value == ThirdPartyMode.ABSORB -> "ABSORBED"
                    else -> "PENDING_REIMBURSEMENT"
                }

                val expense = ExpenseEntity(
                    id = expenseId,
                    householdId = householdId,
                    occurredAt = occurredEpoch,
                    quincenaId = quincena.id,
                    categoryId = categoryId,
                    concept = conceptValue,
                    amountMxn = amount,
                    paymentMethodId = walletId,
                    status = "POSTED",
                    createdAt = nowEpoch,
                    notes = _notes.value.trim().ifBlank { null },
                    latitude = fix?.latitude,
                    longitude = fix?.longitude,
                    placeLabel = fix?.placeLabel,
                    locationSource = if (fix != null) LocationSource.CAPTURE else LocationSource.NONE,
                    settlementStatus = settlementStatus,
                    externalPayerMemberId = thirdPartyId,
                )

                // 6. Construir atribuciones de AMBAS dimensiones desde los shares (%).
                val beneficiaryShares = _beneficiaryShares.value
                // El PAYER: si pagó un tercero, la dimensión PAYER es ese tercero al 100%
                // (quién desembolsó); si no, los shares del formulario.
                val payerShares = if (thirdPartyId != null) mapOf(thirdPartyId to 100)
                else _payerShares.value
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

                // 8. Si la hoja nació de una captura pendiente (§G.3 / Review SP-A1),
                // márcala CONFIRMED para sacarla de la bandeja. Con callback
                // (BankCaptureManager cancela también la notificación) o, si no
                // hay, directo vía PendingCaptureDao.
                pendingCaptureId?.let { id ->
                    runCatching {
                        if (onPendingConfirmed != null) onPendingConfirmed.invoke(id)
                        else pendingCaptureDao?.updateStatus(id, "CONFIRMED")
                    }
                    // Fase 6: si la captura era una propuesta de colaborador, avisa
                    // a Firestore que fue ACEPTADA y con qué gasto (best-effort).
                    if (id.startsWith("proposal:")) {
                        runCatching {
                            membershipRepository?.updateProposalStatus(
                                householdId = householdId,
                                proposalDocId = id.removePrefix("proposal:"),
                                status = "ACCEPTED",
                                expenseId = expenseId,
                            )
                        }
                    }
                    pendingCaptureId = null
                }

                _operationState.value = CaptureOperationState.Success(expenseId)

            } catch (e: Exception) {
                _operationState.value = CaptureOperationState.Error(
                    e.message ?: "Error al registrar el gasto. Intenta de nuevo."
                )
            }
        }
    }

    /**
     * Registra un ingreso POSTED (modo INCOME del sheet). MISMO camino que
     * `WalletsViewModel.recordIncome`: [IncomeRepository.insert] crea el
     * `income_source` POSTED, acredita el saldo de la cuenta destino y encola
     * INCOME+WALLET en `sync_queue` (todo en una transacción del repo impl).
     * La quincena se resuelve por la fecha elegida (default hoy → activa).
     */
    fun onRegisterIncome() {
        if (_operationState.value is CaptureOperationState.Loading) return

        viewModelScope.launch {
            _operationState.value = CaptureOperationState.Loading
            try {
                val repo = incomeRepository
                    ?: throw IllegalStateException("El registro de ingresos no está disponible aquí.")
                val walletId = _selectedWalletId.value
                    ?: throw IllegalStateException("Selecciona la cuenta destino.")
                val memberId = _incomeMemberId.value
                    ?: throw IllegalStateException("Selecciona quién genera el ingreso.")
                val amount = parsedAmount
                if (amount <= 0) throw IllegalArgumentException("El importe debe ser mayor a $0.")

                val chosenDate = _selectedDate.value
                val quincena = resolveQuincena(chosenDate)
                val date = chosenDate ?: LocalDate.now(zone)

                val income = IncomeSourceEntity(
                    id = UUID.randomUUID().toString(),
                    householdId = householdId,
                    quincenaId = quincena.id,
                    memberId = memberId,
                    label = _concept.value.trim().ifBlank { "Ingreso" }.take(64),
                    amountMxn = amount,
                    cadence = "IRREGULAR",
                    expectedDate = date.toString(),
                    paymentMethodId = walletId,
                    status = "POSTED",
                    createdAt = System.currentTimeMillis(),
                )
                repo.insert(income)

                _operationState.value = CaptureOperationState.Success(income.id)
            } catch (e: Exception) {
                _operationState.value = CaptureOperationState.Error(
                    e.message ?: "Error al registrar el ingreso. Intenta de nuevo."
                )
            }
        }
    }

    /** Punto de entrada único del CTA: despacha por [captureKind]. */
    fun onRegister() = when (_captureKind.value) {
        CaptureKind.INCOME -> onRegisterIncome()
        CaptureKind.EXPENSE -> onRegisterExpense()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Resolución de quincena por fecha (DatePicker, A3 §5)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Quincena que corresponde a [date] (null = hoy → la ACTIVE).
     *
     * Mismo contrato que `QuincenaRollover`: si la fecha cae en la activa se usa
     * esa; si existe otra en Room (histórica/PROVISIONED) se usa; si no, se crea
     * con **id determinista** `q-YYYY-MM-FIRST|SECOND` (día 1-15 = FIRST, 16+ =
     * SECOND) y status PROVISIONED — convergente entre dispositivos, sin romper
     * la FK `expense.quincena_id`. Sin [quincenaDao] cae a la activa.
     */
    private suspend fun resolveQuincena(date: LocalDate?): QuincenaEntity {
        val active = quincenaRepository.getActive(householdId)
        if (date == null) {
            return active ?: throw IllegalStateException("No hay quincena activa para el hogar.")
        }
        val iso = date.toString()
        if (active != null && active.startDate <= iso && active.endDate >= iso) return active

        val dao = quincenaDao
            ?: return active ?: throw IllegalStateException("No hay quincena activa para el hogar.")
        dao.getForDate(householdId, iso)?.let { return it }

        val built = buildQuincena(date)
        dao.insert(built)
        return built
    }

    /**
     * Builder determinista de quincena — réplica 1:1 de
     * `QuincenaRollover.buildQuincena` (privado allá): id `q-YYYY-MM-HALF`,
     * status PROVISIONED (aquí NO se activa: la activa de hoy no cambia).
     */
    private fun buildQuincena(date: LocalDate): QuincenaEntity {
        val year = date.year
        val month = date.monthValue
        val first = date.dayOfMonth <= 15
        val half = if (first) "FIRST" else "SECOND"
        val start = LocalDate.of(year, month, if (first) 1 else 16)
        val end = if (first) LocalDate.of(year, month, 15) else start.withDayOfMonth(start.lengthOfMonth())
        return QuincenaEntity(
            id = "q-%04d-%02d-%s".format(year, month, half),
            householdId = householdId,
            year = year,
            month = month,
            half = half,
            startDate = start.toString(),
            endDate = end.toString(),
            label = "${if (first) "Q1" else "Q2"} ${MONTH_NAMES[month - 1]} $year",
            status = "PROVISIONED",
        )
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
        _notes.value = ""
        _selectedWalletId.value = null
        _selectedCategoryId.value = null
        _beneficiaryShares.value = emptyMap()
        _payerShares.value = emptyMap()
        _captureKind.value = CaptureKind.EXPENSE
        _incomeMemberId.value = null
        _selectedDate.value = null
        _reviewMissing.value = emptySet()
        _categoryQuery.value = ""
        _thirdPartyPayerId.value = null
        _thirdPartyMode.value = ThirdPartyMode.REIMBURSE
        pendingCaptureId = null
        _operationState.value = CaptureOperationState.Idle
    }

    companion object {
        /**
         * Umbral de confianza para MOSTRAR un lado de la sugerencia en el chip.
         * Distinto del [RetroAttributionEngine.AUTO_APPLY_THRESHOLD] (0.7): aquí
         * solo sugerimos, nunca aplicamos solos, así que el listón es más bajo.
         */
        const val SUGGEST_THRESHOLD = 0.5

        /** Cuántas categorías "recientes" mostrar en el sheet. */
        const val RECENT_LIMIT = 5

        /** Nombres de mes para el label de quincena (mismo formato que QuincenaRollover). */
        private val MONTH_NAMES = listOf(
            "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
            "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre",
        )
    }
}
