package mx.budget.ui.quincena

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mx.budget.data.quincena.QuincenaLifecycle

/** Estado de la hoja que edita el presupuesto de una quincena. */
data class QuincenaBudgetState(
    val saving: Boolean = false,
    val error: String? = null,
    /** Id de la ultima quincena guardada con exito; la hoja se cierra al verlo. */
    val savedId: String? = null,
)

/**
 * Presupuesto declarado de la quincena (Fase 7b, alcance 1). Las columnas de
 * ingreso y gasto proyectados ya existian y el rollover las arrastra, pero
 * ninguna pantalla las editaba: solo llegaban por la semilla o por Firestore.
 * La escritura pasa por [QuincenaLifecycle.saveBudget], que sella `updated_at`
 * y encola la fila `QUINCENA`.
 */
class QuincenaBudgetViewModel(
    private val lifecycle: QuincenaLifecycle,
) : ViewModel() {

    private val _state = MutableStateFlow(QuincenaBudgetState())
    val state: StateFlow<QuincenaBudgetState> = _state.asStateFlow()

    fun save(quincenaId: String, projectedIncome: Double, projectedExpenses: Double) {
        if (_state.value.saving) return
        if (projectedIncome < 0.0 || projectedExpenses < 0.0) {
            _state.value = QuincenaBudgetState(error = "Las cifras no pueden ser negativas.")
            return
        }
        _state.value = QuincenaBudgetState(saving = true)
        viewModelScope.launch {
            val ok = runCatching { lifecycle.saveBudget(quincenaId, projectedIncome, projectedExpenses) }
                .getOrDefault(false)
            _state.value = if (ok) QuincenaBudgetState(savedId = quincenaId)
            else QuincenaBudgetState(error = "No se pudo guardar: la quincena ya está cerrada o no existe.")
        }
    }

    /** La hoja ya reacciono al guardado o al error. */
    fun consume() {
        _state.value = QuincenaBudgetState()
    }
}
