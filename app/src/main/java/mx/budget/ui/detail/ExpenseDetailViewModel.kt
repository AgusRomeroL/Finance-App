package mx.budget.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.budget.data.local.entity.CategoryEntity
import mx.budget.data.local.entity.ExpenseAttributionEntity
import mx.budget.data.local.entity.ExpenseEntity
import mx.budget.data.local.entity.MemberEntity
import mx.budget.data.local.entity.PaymentMethodEntity
import mx.budget.data.local.result.ExpenseWithDetails
import mx.budget.data.location.LocationProvider
import mx.budget.data.location.LocationSource
import mx.budget.data.repository.CategoryRepository
import mx.budget.data.repository.ExpenseRepository
import mx.budget.data.repository.MemberRepository
import mx.budget.data.repository.WalletRepository

/**
 * Estado del detalle de un gasto (Apéndice G.4.1 / G.4.3 + edición completa, MVP Fase 1).
 *
 * Combina los campos de display del [row] (concepto, monto, categoría, wallet) con
 * los campos de ubicación/hora editables, las atribuciones actuales y — en modo
 * edición — los borradores de concepto/monto/categoría/wallet/splits.
 */
data class ExpenseDetailState(
    val row: ExpenseWithDetails,
    val occurredAt: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val placeLabel: String? = null,
    val locationSource: String? = null,
    /** `true` mientras se obtiene un fix de GPS (spinner en el botón). */
    val locating: Boolean = false,

    // ── Edición completa (MVP Fase 1) ────────────────────────────────────────
    /** Entidad completa cargada por id; base de la edición. */
    val entity: ExpenseEntity? = null,
    /** Atribuciones actuales (ambas dimensiones), para mostrar y precargar drafts. */
    val attributions: List<ExpenseAttributionEntity> = emptyList(),
    val editing: Boolean = false,
    val draftConcept: String = "",
    /** Monto como texto del campo (validado en [canSave]). */
    val draftAmount: String = "",
    val draftCategoryId: String? = null,
    val draftWalletId: String? = null,
    /** memberId → % (suma 100), mismo modelo que CaptureViewModel. */
    val beneficiaryShares: Map<String, Int> = emptyMap(),
    val payerShares: Map<String, Int> = emptyMap(),
    val saving: Boolean = false,
    val editError: String? = null,
) {
    /** `true` si el gasto ya trae una ubicación real (no NONE/null). */
    val hasLocation: Boolean
        get() = latitude != null && longitude != null &&
            locationSource != null && locationSource != LocationSource.NONE

    /** Guardar habilitado solo con datos válidos y ambos roles sumando 100 %. */
    val canSave: Boolean
        get() = draftConcept.isNotBlank() &&
            (draftAmount.toDoubleOrNull() ?: 0.0) > 0.0 &&
            draftCategoryId != null && draftWalletId != null &&
            beneficiaryShares.isNotEmpty() && beneficiaryShares.values.sum() == 100 &&
            payerShares.isNotEmpty() && payerShares.values.sum() == 100
}

/**
 * ViewModel de la hoja de detalle del gasto (§G.4 + edición/borrado, MVP Fase 1).
 *
 * Además de ver/editar ubicación (G.4.3) y hora (G.4.1), permite **editar el gasto
 * completo** (concepto, monto, categoría, wallet y atribuciones por rol) vía
 * [ExpenseRepository.updateWithAttributions] — que revierte/aplica el efecto en el
 * saldo del wallet en la misma transacción — y **borrarlo** vía
 * [ExpenseRepository.deleteAndRevertBalance].
 *
 * Nota MVP: editar la fecha NO recalcula la quincena; el gasto conserva su
 * `quincena_id` original (documentado como limitación consciente).
 */
class ExpenseDetailViewModel(
    private val expenseRepository: ExpenseRepository,
    private val locationProvider: LocationProvider,
    private val categoryRepository: CategoryRepository,
    private val walletRepository: WalletRepository,
    private val memberRepository: MemberRepository,
    private val householdId: String,
    /** Fase 6: refleja el reembolso en la propuesta remota del colaborador. */
    private val membershipRepository: mx.budget.data.remote.MembershipRepository? = null,
) : ViewModel() {

    private val _state = MutableStateFlow<ExpenseDetailState?>(null)

    /** Detalle abierto, o `null` si la hoja está cerrada. */
    val state: StateFlow<ExpenseDetailState?> = _state.asStateFlow()

    /** Catálogos para los pickers del modo edición (solo se materializan con la hoja abierta). */
    val categories: StateFlow<List<CategoryEntity>> = categoryRepository.observeAll(householdId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val wallets: StateFlow<List<PaymentMethodEntity>> = walletRepository.observeActive(householdId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val members: StateFlow<List<MemberEntity>> = memberRepository.observeActiveMembers(householdId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Abre la hoja para [row], cargando entidad, ubicación/hora y atribuciones. */
    fun open(row: ExpenseWithDetails) {
        _state.value = ExpenseDetailState(row = row, occurredAt = row.occurredAt)
        viewModelScope.launch {
            val entity = expenseRepository.getById(row.expenseId) ?: return@launch
            val attributions = expenseRepository.getAttributions(row.expenseId)
            _state.update {
                it?.copy(
                    occurredAt = entity.occurredAt,
                    latitude = entity.latitude,
                    longitude = entity.longitude,
                    placeLabel = entity.placeLabel,
                    locationSource = entity.locationSource,
                    entity = entity,
                    attributions = attributions,
                )
            }
        }
    }

    /** Cierra la hoja y limpia el estado. */
    fun dismiss() {
        _state.value = null
    }

    // ── Edición completa (MVP Fase 1) ────────────────────────────────────────

    /** Entra a modo edición precargando drafts desde la entidad y sus atribuciones. */
    fun startEdit() {
        val current = _state.value ?: return
        val entity = current.entity ?: return
        _state.update {
            it?.copy(
                editing = true,
                editError = null,
                draftConcept = entity.concept,
                draftAmount = formatAmount(entity.amountMxn),
                draftCategoryId = entity.categoryId,
                draftWalletId = entity.paymentMethodId,
                beneficiaryShares = bpsToPercent(
                    current.attributions.filter { a -> a.role == "BENEFICIARY" }
                ),
                payerShares = bpsToPercent(
                    current.attributions.filter { a -> a.role == "PAYER" }
                ),
            )
        }
    }

    /** Sale de modo edición descartando los drafts. */
    fun cancelEdit() {
        _state.update { it?.copy(editing = false, editError = null) }
    }

    fun onDraftConcept(value: String) {
        _state.update { it?.copy(draftConcept = value.take(64)) }
    }

    fun onDraftAmount(value: String) {
        // Solo dígitos y un punto decimal (mismo criterio que el keypad de captura).
        val sanitized = value.filter { c -> c.isDigit() || c == '.' }
        if (sanitized.count { c -> c == '.' } <= 1) {
            _state.update { it?.copy(draftAmount = sanitized) }
        }
    }

    fun onDraftCategory(categoryId: String) {
        _state.update { it?.copy(draftCategoryId = categoryId) }
    }

    fun onDraftWallet(walletId: String) {
        _state.update { it?.copy(draftWalletId = walletId) }
    }

    // Selección de miembros por rol — mismo comportamiento que CaptureViewModel:
    // toggle re-reparte equitativo; el stepper ajusta ±5 acotado a [0, 100].

    fun onBeneficiaryToggled(memberId: String) {
        _state.update { s ->
            s?.copy(beneficiaryShares = toggleEqual(s.beneficiaryShares, memberId))
        }
    }

    fun onBeneficiaryDelta(memberId: String, delta: Int) {
        _state.update { s ->
            s?.copy(beneficiaryShares = applyDelta(s.beneficiaryShares, memberId, delta))
        }
    }

    fun onBeneficiaryAll() {
        _state.update { s -> s?.copy(beneficiaryShares = equalSplit(members.value.map { it.id })) }
    }

    fun onBeneficiaryClear() {
        _state.update { it?.copy(beneficiaryShares = emptyMap()) }
    }

    fun onPayerToggled(memberId: String) {
        _state.update { s -> s?.copy(payerShares = toggleEqual(s.payerShares, memberId)) }
    }

    fun onPayerDelta(memberId: String, delta: Int) {
        _state.update { s -> s?.copy(payerShares = applyDelta(s.payerShares, memberId, delta)) }
    }

    /**
     * Persiste la edición: entidad actualizada + atribuciones reconstruidas de ambos
     * roles (%→bps, el último miembro absorbe el resto para sumar 10,000 exacto).
     * [ExpenseRepository.updateWithAttributions] revierte el saldo del wallet viejo y
     * aplica el nuevo en la misma transacción, y encola el push de sync.
     */
    fun saveEdit() {
        val current = _state.value ?: return
        val entity = current.entity ?: return
        if (!current.canSave || current.saving) return
        val amount = current.draftAmount.toDoubleOrNull() ?: return
        val concept = current.draftConcept.trim()
        _state.update { it?.copy(saving = true, editError = null) }
        viewModelScope.launch {
            try {
                val updated = entity.copy(
                    concept = concept,
                    amountMxn = amount,
                    categoryId = current.draftCategoryId!!,
                    paymentMethodId = current.draftWalletId!!,
                    // Si el concepto cambió, invalida la clave canónica para que el
                    // pipeline de normalización retroactiva (Feature B) lo re-procese.
                    conceptCanonical = if (concept == entity.concept) entity.conceptCanonical else null,
                )
                val attributions =
                    buildRows(entity.id, amount, current.beneficiaryShares, "BENEFICIARY") +
                        buildRows(entity.id, amount, current.payerShares, "PAYER")
                expenseRepository.updateWithAttributions(updated, attributions)

                // Refresca el estado local para que la hoja no muestre datos viejos
                // mientras los Flows del dashboard emiten.
                val fresh = expenseRepository.getById(entity.id) ?: updated
                val freshAttributions = expenseRepository.getAttributions(entity.id)
                val categoryName = categories.value
                    .firstOrNull { it.id == fresh.categoryId }?.displayName
                    ?: current.row.categoryName
                val walletName = wallets.value
                    .firstOrNull { it.id == fresh.paymentMethodId }?.displayName
                    ?: current.row.paymentMethodName
                _state.update {
                    it?.copy(
                        editing = false,
                        saving = false,
                        entity = fresh,
                        attributions = freshAttributions,
                        row = it.row.copy(
                            concept = fresh.concept,
                            amountMxn = fresh.amountMxn,
                            categoryId = fresh.categoryId,
                            categoryName = categoryName,
                            paymentMethodName = walletName,
                        ),
                    )
                }
            } catch (e: Exception) {
                _state.update { it?.copy(saving = false, editError = e.message ?: "No se pudo guardar") }
            }
        }
    }

    // ── Reembolso / liquidación de gastos pagados por un tercero (Fase B, B3) ────

    /**
     * Reembolsa/liquida un gasto `PENDING_REIMBURSEMENT` desde un wallet real
     * [walletId]: lo re-asigna a esa cuenta y marca `REIMBURSED`; el cargo pasa del
     * wallet virtual EXTERNAL (sin efecto) al real, moviendo su saldo. Cierra la hoja
     * al terminar (el saldo/movimiento ya vive en el wallet real).
     */
    fun reimburseFrom(walletId: String) {
        val current = _state.value ?: return
        if (current.saving) return
        _state.update { it?.copy(saving = true, editError = null) }
        viewModelScope.launch {
            try {
                expenseRepository.reimburseFrom(current.row.expenseId, walletId)
                // Fase 6: si el gasto nació de una propuesta de colaborador,
                // estampa reimbursedAt en Firestore (la web lo saca de "me deben").
                runCatching {
                    membershipRepository?.markProposalReimbursedByExpense(
                        householdId, current.row.expenseId
                    )
                }
                _state.value = null
            } catch (e: Exception) {
                _state.update { it?.copy(saving = false, editError = e.message ?: "No se pudo reembolsar") }
            }
        }
    }

    /** Marca el gasto como absorbido por el tercero (no se le repone). No toca saldos. */
    fun markAbsorbed() {
        val current = _state.value ?: return
        if (current.saving) return
        _state.update { it?.copy(saving = true, editError = null) }
        viewModelScope.launch {
            try {
                expenseRepository.markAbsorbed(current.row.expenseId)
                val fresh = expenseRepository.getById(current.row.expenseId)
                _state.update { it?.copy(saving = false, entity = fresh ?: it.entity) }
            } catch (e: Exception) {
                _state.update { it?.copy(saving = false, editError = e.message ?: "No se pudo marcar absorbido") }
            }
        }
    }

    /**
     * Elimina el gasto revirtiendo su efecto en el saldo del wallet (si POSTED) y
     * encolando el DELETE de sync. Cierra la hoja al terminar.
     */
    fun delete() {
        val current = _state.value ?: return
        if (current.saving) return
        _state.update { it?.copy(saving = true) }
        viewModelScope.launch {
            try {
                expenseRepository.deleteAndRevertBalance(current.row.expenseId)
                _state.value = null
            } catch (e: Exception) {
                _state.update { it?.copy(saving = false, editError = e.message ?: "No se pudo eliminar") }
            }
        }
    }

    // ── Ubicación / hora (G.4) ───────────────────────────────────────────────

    /**
     * Añade ubicación a mano (G.4.3): toma un fix fresco (la hoja está en foreground)
     * y lo persiste como `MANUAL`. Best-effort: si no hay permiso/nivel o el fix falla,
     * solo deja de mostrar el spinner sin cambiar nada.
     */
    fun addLocation() {
        val current = _state.value ?: return
        if (current.locating) return
        _state.update { it?.copy(locating = true) }
        viewModelScope.launch {
            val fix = locationProvider.currentFix(requireForeground = true)
            if (fix != null) {
                expenseRepository.setLocation(
                    expenseId = current.row.expenseId,
                    latitude = fix.latitude,
                    longitude = fix.longitude,
                    placeLabel = fix.placeLabel,
                    source = LocationSource.MANUAL,
                )
            }
            _state.update {
                it?.copy(
                    locating = false,
                    latitude = fix?.latitude ?: it.latitude,
                    longitude = fix?.longitude ?: it.longitude,
                    placeLabel = fix?.placeLabel ?: it.placeLabel,
                    locationSource = if (fix != null) LocationSource.MANUAL else it.locationSource,
                )
            }
        }
    }

    /** Quita la ubicación del gasto (vuelve a `NONE`). */
    fun removeLocation() {
        val current = _state.value ?: return
        viewModelScope.launch {
            expenseRepository.setLocation(
                expenseId = current.row.expenseId,
                latitude = null,
                longitude = null,
                placeLabel = null,
                source = LocationSource.NONE,
            )
            _state.update {
                it?.copy(latitude = null, longitude = null, placeLabel = null, locationSource = LocationSource.NONE)
            }
        }
    }

    /**
     * Cambia el color de identidad de la categoría del gasto (paquete A4). Persiste
     * `category.color_hex` vía [CategoryRepository.update] (estampa `updated_at` y
     * encola CATEGORY al sync). Se refleja en TODAS las filas de esa categoría porque
     * [TransactionRow] lee `categoryColorHex`. `hex` = "#RRGGBB" o null (quitar color).
     */
    fun setCategoryColor(hex: String?) {
        val current = _state.value ?: return
        val categoryId = current.entity?.categoryId ?: current.row.categoryId
        viewModelScope.launch {
            val category = categories.value.firstOrNull { it.id == categoryId }
                ?: categoryRepository.getById(categoryId) ?: return@launch
            categoryRepository.update(category.copy(colorHex = hex))
        }
    }

    /** Actualiza la fecha/hora del gasto (G.4.1, epoch millis). */
    fun setOccurredAt(occurredAt: Long) {
        val current = _state.value ?: return
        viewModelScope.launch {
            expenseRepository.setOccurredAt(current.row.expenseId, occurredAt)
            _state.update { it?.copy(occurredAt = occurredAt) }
        }
    }

    // ── Helpers de reparto (mismos invariantes que CaptureViewModel) ─────────

    /** Reparte 100 % equitativo entre [ids]; el último absorbe el residuo. */
    private fun equalSplit(ids: Collection<String>): Map<String, Int> {
        val list = ids.toList()
        if (list.isEmpty()) return emptyMap()
        val base = 100 / list.size
        val remainder = 100 - base * list.size
        return list.mapIndexed { i, id ->
            id to if (i == list.lastIndex) base + remainder else base
        }.toMap()
    }

    /** Alta/baja de un miembro re-repartiendo equitativo (patrón de Captura). */
    private fun toggleEqual(shares: Map<String, Int>, memberId: String): Map<String, Int> {
        val ids = shares.keys.let { if (memberId in it) it - memberId else it + memberId }
        return equalSplit(ids)
    }

    /** Ajuste ±[delta] acotado a [0, 100] sin tocar a los demás. */
    private fun applyDelta(shares: Map<String, Int>, memberId: String, delta: Int): Map<String, Int> {
        if (memberId !in shares) return shares
        return shares + (memberId to (shares.getValue(memberId) + delta).coerceIn(0, 100))
    }

    /** Convierte atribuciones (bps, suman 10,000) a memberId→% (suma 100). */
    private fun bpsToPercent(rows: List<ExpenseAttributionEntity>): Map<String, Int> {
        if (rows.isEmpty()) return emptyMap()
        var assigned = 0
        return rows.mapIndexed { i, row ->
            row.memberId to if (i == rows.lastIndex) (100 - assigned)
            else (row.shareBps / 100).also { assigned += it }
        }.toMap()
    }

    /**
     * Construye las filas de atribución de un rol desde los shares en % —
     * el último miembro absorbe el resto para garantizar 10,000 bps exactos.
     */
    private fun buildRows(
        expenseId: String,
        amount: Double,
        shares: Map<String, Int>,
        role: String,
    ): List<ExpenseAttributionEntity> {
        val entries = shares.entries.toList()
        var assigned = 0
        return entries.mapIndexed { i, (memberId, pct) ->
            val bps = if (i == entries.lastIndex) 10_000 - assigned
            else (pct * 100).also { assigned += it }
            ExpenseAttributionEntity(
                id = java.util.UUID.randomUUID().toString(),
                expenseId = expenseId,
                memberId = memberId,
                role = role,
                shareBps = bps,
                shareAmountMxn = amount * bps / 10_000.0,
            )
        }
    }

    /** Formatea el monto para el campo de texto ("250" en vez de "250.0"). */
    private fun formatAmount(amount: Double): String =
        if (amount % 1.0 == 0.0) amount.toLong().toString() else amount.toString()
}
