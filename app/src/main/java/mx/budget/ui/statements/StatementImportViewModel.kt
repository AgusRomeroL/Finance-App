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
import mx.budget.data.statements.ParsedStatement
import mx.budget.data.statements.StatementImportManager
import mx.budget.data.statements.StatementMovement
import mx.budget.data.statements.StatementPeriod

/**
 * Fase del flujo de importación de estado de cuenta (paquete C1).
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
    /** Reconciliación aplicada. */
    data class Applied(val msiCount: Int) : ImportPhase
    /** Error legible en cualquier paso. */
    data class Error(val message: String) : ImportPhase
}

/**
 * ViewModel de la pantalla "Importar estado de cuenta" (Fase C, paquete C1).
 *
 * Orquesta el flujo elegir-archivo → extraer (local) → analizar (cloud) → preview
 * editable → aplicar. Mantiene el estado editable del [ParsedStatement] en un
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
                            // Prefill del wallet por last4 si coincide con alguno.
                            _selectedWalletId.value = matchWalletByLast4(res.statement.last4)
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

    /** Aplica la reconciliación con el draft editado. */
    fun apply() {
        viewModelScope.launch {
            runCatching {
                manager.apply(_draft.value, rawJson, _selectedWalletId.value)
            }.onSuccess { count ->
                _phase.value = ImportPhase.Applied(count)
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
        rawJson = "{}"
    }
}
