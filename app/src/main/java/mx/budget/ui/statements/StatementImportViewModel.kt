package mx.budget.ui.statements

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mx.budget.data.local.entity.PaymentMethodEntity
import mx.budget.data.repository.WalletRepository
import mx.budget.data.settings.SettingsRepository
import mx.budget.data.statements.AggregateCandidate
import mx.budget.data.statements.ParsedStatement
import mx.budget.data.statements.PlannedPurchase
import mx.budget.data.statements.RewriteMember
import mx.budget.data.statements.StatementImportManager
import mx.budget.data.statements.StatementMovement
import mx.budget.data.statements.StatementPeriod

/**
 * Fase del flujo de importación de estado de cuenta (paquete C1 + reescritura).
 */
sealed interface ImportPhase {
    /** Aún no se eligió archivo (o sin API key configurada). */
    data object Idle : ImportPhase
    /** Extrayendo texto local del PDF/imagen. */
    data object Extracting : ImportPhase
    /** Texto extraído; consultando al LLM cloud. */
    data object Analyzing : ImportPhase
    /** Preview editable listo. */
    data object Preview : ImportPhase
    /** Construyendo el plan de reescritura (detección de agregados + categorías). */
    data object BuildingRewrite : ImportPhase
    /** Paso "Reescribir movimientos": confirmación item por item. */
    data object RewriteReview : ImportPhase
    /** Reconciliación (y reescritura, si la hubo) aplicada. */
    data class Applied(
        val msiCount: Int,
        val insertedCount: Int = 0,
        val insertedTotalMxn: Double = 0.0,
        val convertedCount: Int = 0,
    ) : ImportPhase
    /** Error legible en cualquier paso. */
    data class Error(val message: String) : ImportPhase
}

/** Item seleccionable del paso de reescritura (checkbox + payload). */
data class RewriteItem<T>(val item: T, val selected: Boolean)

/**
 * ViewModel de la pantalla "Importar estado de cuenta" (Fase C, paquete C1 +
 * extensión "Reescribir movimientos").
 *
 * Orquesta el flujo elegir-archivo → extraer (local) → analizar (cloud) → preview
 * editable → (si hay wallet elegido) paso de reescritura con confirmación item
 * por item → aplicar. Mantiene el estado editable del [ParsedStatement] en un
 * [StateFlow] para que la UI lo edite campo a campo antes de aplicar.
 */
class StatementImportViewModel(
    private val manager: StatementImportManager,
    private val walletRepository: WalletRepository,
    private val settings: SettingsRepository,
    private val householdId: String,
) : ViewModel() {

    private val _phase = MutableStateFlow<ImportPhase>(ImportPhase.Idle)
    val phase: StateFlow<ImportPhase> = _phase.asStateFlow()

    /** `true` si hay una API key de NVIDIA configurada (gate del flujo). */
    val hasApiKey: StateFlow<Boolean> = settings.nvidiaApiKey
        .map { it.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val wallets: StateFlow<List<PaymentMethodEntity>> =
        walletRepository.observeActive(householdId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Datos editables del preview (mutados por la UI). */
    private val _draft = MutableStateFlow(ParsedStatement())
    val draft: StateFlow<ParsedStatement> = _draft.asStateFlow()

    /** Wallet elegido para la reconciliación (null = solo auditar). */
    private val _selectedWalletId = MutableStateFlow<String?>(null)
    val selectedWalletId: StateFlow<String?> = _selectedWalletId.asStateFlow()

    /**
     * Wallet preseleccionado al entrar desde el checklist "Estados del mes": si el
     * `last4` del PDF no resuelve un wallet, se usa este como fallback. El VM es
     * activity-scoped, así que quien navega aquí debe llamar `reset()` + `presetWallet`.
     */
    private var presetWalletId: String? = null

    fun presetWallet(id: String?) {
        presetWalletId = id
        _selectedWalletId.value = id
    }

    // ── Estado del paso "Reescribir movimientos" ────────────────────────────────

    /** Compras del estado, con checkbox (MSI default deseleccionado). */
    private val _rewritePurchases = MutableStateFlow<List<RewriteItem<PlannedPurchase>>>(emptyList())
    val rewritePurchases: StateFlow<List<RewriteItem<PlannedPurchase>>> = _rewritePurchases.asStateFlow()

    /** Pagos agregados detectados, con checkbox (default seleccionados). */
    private val _rewriteAggregates = MutableStateFlow<List<RewriteItem<AggregateCandidate>>>(emptyList())
    val rewriteAggregates: StateFlow<List<RewriteItem<AggregateCandidate>>> = _rewriteAggregates.asStateFlow()

    /** Nombre del pagador default de las compras (Norma) para el copy del resumen. */
    private val _rewritePayerName = MutableStateFlow<String?>(null)
    val rewritePayerName: StateFlow<String?> = _rewritePayerName.asStateFlow()

    /** Cuántos miembros reciben el reparto equitativo (copy del resumen). */
    private val _rewriteBeneficiaryCount = MutableStateFlow(0)
    val rewriteBeneficiaryCount: StateFlow<Int> = _rewriteBeneficiaryCount.asStateFlow()

    /** Miembros del hogar para los chips de beneficiario por compra. */
    private val _rewriteMembers = MutableStateFlow<List<RewriteMember>>(emptyList())
    val rewriteMembers: StateFlow<List<RewriteMember>> = _rewriteMembers.asStateFlow()

    /** Categorías hoja (id → nombre) para el dropdown de categoría por compra. */
    private val _rewriteCategories = MutableStateFlow<List<RewriteMember>>(emptyList())
    val rewriteCategories: StateFlow<List<RewriteMember>> = _rewriteCategories.asStateFlow()

    // JSON crudo original del LLM, para auditoría al aplicar.
    private var rawJson: String = "{}"

    /**
     * Arranca el pipeline con el [uri] elegido. Si no hay API key, corta con un
     * error que la UI traduce en "configúrala en Perfil".
     */
    fun onFileChosen(uri: Uri) {
        viewModelScope.launch {
            if (settings.getNvidiaApiKey().isBlank()) {
                _phase.value = ImportPhase.Error(
                    "Configura tu API key de NVIDIA en Perfil antes de importar."
                )
                return@launch
            }
            _phase.value = ImportPhase.Extracting
            when (val ext = manager.extractText(uri)) {
                is StatementImportManager.ExtractResult.Failure -> {
                    _phase.value = ImportPhase.Error(ext.message)
                }
                is StatementImportManager.ExtractResult.Success -> {
                    _phase.value = ImportPhase.Analyzing
                    when (val res = manager.analyze(ext.text)) {
                        is StatementImportManager.AnalyzeResult.Failure -> {
                            _phase.value = ImportPhase.Error(res.message)
                        }
                        is StatementImportManager.AnalyzeResult.Success -> {
                            rawJson = res.rawJson
                            _draft.value = res.statement
                            // Prefill del wallet por last4; si no resuelve, cae al
                            // preseleccionado desde el checklist (si lo hubo).
                            _selectedWalletId.value =
                                matchWalletByLast4(res.statement.last4) ?: presetWalletId
                            _phase.value = ImportPhase.Preview
                        }
                    }
                }
            }
        }
    }

    private fun matchWalletByLast4(last4: String?): String? {
        if (last4.isNullOrBlank()) return null
        return wallets.value.firstOrNull { it.last4 == last4 }?.id
    }

    fun selectWallet(id: String?) {
        _selectedWalletId.value = id
    }

    // ── Ediciones del preview (campos de cabecera) ──────────────────────────────

    fun updateEmisor(v: String) = update { it.copy(emisor = v.ifBlank { null }) }
    fun updateLast4(v: String) = update { it.copy(last4 = v.ifBlank { null }) }
    fun updatePeriodoInicio(v: String) = update {
        it.copy(periodo = (it.periodo ?: StatementPeriod()).copy(inicio = v.ifBlank { null }))
    }
    fun updatePeriodoFin(v: String) = update {
        it.copy(periodo = (it.periodo ?: StatementPeriod()).copy(fin = v.ifBlank { null }))
    }
    fun updateFechaCorte(v: String) = update { it.copy(fechaCorte = v.ifBlank { null }) }
    fun updateFechaLimite(v: String) = update { it.copy(fechaLimitePago = v.ifBlank { null }) }
    fun updateSaldoTotal(v: String) = update { it.copy(saldoTotal = v.toDoubleOrNull()) }
    fun updatePagoMinimo(v: String) = update { it.copy(pagoMinimo = v.toDoubleOrNull()) }
    fun updatePagoNoIntereses(v: String) = update { it.copy(pagoNoIntereses = v.toDoubleOrNull()) }
    fun updateTasaAnual(v: String) = update { it.copy(tasaAnual = v.toDoubleOrNull()) }

    // ── Ediciones de movimientos ────────────────────────────────────────────────

    fun updateMovement(index: Int, transform: (StatementMovement) -> StatementMovement) = update {
        val list = it.movimientos.toMutableList()
        if (index in list.indices) list[index] = transform(list[index])
        it.copy(movimientos = list)
    }

    fun removeMovement(index: Int) = update {
        val list = it.movimientos.toMutableList()
        if (index in list.indices) list.removeAt(index)
        it.copy(movimientos = list)
    }

    private inline fun update(transform: (ParsedStatement) -> ParsedStatement) {
        _draft.value = transform(_draft.value)
    }

    // ── Continuación desde el preview ───────────────────────────────────────────

    /**
     * CTA del preview. Sin wallet elegido → aplica solo la reconciliación (ruta
     * C1 clásica). Con wallet → construye el plan de reescritura y pasa al paso
     * "Reescribir movimientos" para confirmar item por item.
     */
    fun continueFromPreview() {
        val walletId = _selectedWalletId.value
        if (walletId == null) {
            applyReconcileOnly()
            return
        }
        viewModelScope.launch {
            _phase.value = ImportPhase.BuildingRewrite
            runCatching {
                manager.buildRewritePlan(_draft.value, walletId)
            }.onSuccess { plan ->
                _rewritePurchases.value = plan.purchases.map {
                    // MSI default deseleccionado: ya se registra como plan a meses.
                    RewriteItem(it, selected = !it.esMsi)
                }
                _rewriteAggregates.value = plan.aggregates.map { RewriteItem(it, selected = true) }
                _rewritePayerName.value = plan.payerName
                _rewriteBeneficiaryCount.value = plan.beneficiaryCount
                _rewriteMembers.value = plan.members
                _rewriteCategories.value = plan.categories
                _phase.value = ImportPhase.RewriteReview
            }.onFailure { e ->
                _phase.value = ImportPhase.Error(
                    "No se pudo preparar la reescritura: ${e.message ?: "error"}"
                )
            }
        }
    }

    /** Aplica SOLO la reconciliación C1 (sin tocar gastos). */
    private fun applyReconcileOnly() {
        viewModelScope.launch {
            runCatching {
                manager.apply(_draft.value, rawJson, _selectedWalletId.value)
            }.onSuccess { count ->
                _phase.value = ImportPhase.Applied(msiCount = count)
            }.onFailure { e ->
                _phase.value = ImportPhase.Error("No se pudo aplicar: ${e.message ?: "error"}")
            }
        }
    }

    // ── Paso "Reescribir movimientos" ───────────────────────────────────────────

    fun togglePurchase(index: Int) {
        _rewritePurchases.value = _rewritePurchases.value.mapIndexed { i, item ->
            if (i == index) item.copy(selected = !item.selected) else item
        }
    }

    /** Alterna a un miembro como beneficiario de la compra [index] (reparto equitativo). */
    fun togglePurchaseBeneficiary(index: Int, memberId: String) {
        val allIds = _rewriteMembers.value.map { it.id }
        _rewritePurchases.value = _rewritePurchases.value.mapIndexed { i, item ->
            if (i != index) return@mapIndexed item
            // Vacío = "todos por igual": al primer toque se materializa a todos y se
            // quita el tocado, para que el chip se comporte como esperaría el usuario.
            val current = item.item.suggestedBeneficiaryIds.ifEmpty { allIds }
            val next = if (memberId in current) current - memberId else current + memberId
            item.copy(item = item.item.copy(suggestedBeneficiaryIds = next))
        }
    }

    /** Fija la categoría de la compra [index] (dropdown del paso de reescritura). */
    fun updatePurchaseCategory(index: Int, categoryId: String, categoryName: String) {
        _rewritePurchases.value = _rewritePurchases.value.mapIndexed { i, item ->
            if (i != index) return@mapIndexed item
            item.copy(item = item.item.copy(
                suggestedCategoryId = categoryId,
                suggestedCategoryName = categoryName,
            ))
        }
    }

    fun toggleAggregate(index: Int) {
        _rewriteAggregates.value = _rewriteAggregates.value.mapIndexed { i, item ->
            if (i == index) item.copy(selected = !item.selected) else item
        }
    }

    /** Regresa del paso de reescritura al preview (conserva las ediciones). */
    fun backToPreview() {
        _phase.value = ImportPhase.Preview
    }

    /**
     * Aplica reconciliación + reescritura con lo confirmado. Si el usuario
     * deseleccionó todo, equivale a la reconciliación C1 clásica.
     */
    fun applyRewrite() {
        val walletId = _selectedWalletId.value ?: return
        viewModelScope.launch {
            runCatching {
                manager.applyWithRewrite(
                    statement = _draft.value,
                    rawJson = rawJson,
                    walletId = walletId,
                    purchases = _rewritePurchases.value.filter { it.selected }.map { it.item },
                    aggregateExpenseIds = _rewriteAggregates.value
                        .filter { it.selected }
                        .map { it.item.expenseId },
                )
            }.onSuccess { result ->
                _phase.value = ImportPhase.Applied(
                    msiCount = result.msiTouched,
                    insertedCount = result.insertedExpenses,
                    insertedTotalMxn = result.insertedTotalMxn,
                    convertedCount = result.convertedTransfers,
                )
            }.onFailure { e ->
                _phase.value = ImportPhase.Error("No se pudo aplicar: ${e.message ?: "error"}")
            }
        }
    }

    /** Vuelve al inicio (para reintentar / importar otro). */
    fun reset() {
        _phase.value = ImportPhase.Idle
        _draft.value = ParsedStatement()
        _selectedWalletId.value = null
        presetWalletId = null
        _rewritePurchases.value = emptyList()
        _rewriteAggregates.value = emptyList()
        _rewritePayerName.value = null
        _rewriteBeneficiaryCount.value = 0
        _rewriteMembers.value = emptyList()
        _rewriteCategories.value = emptyList()
        rawJson = "{}"
    }
}
