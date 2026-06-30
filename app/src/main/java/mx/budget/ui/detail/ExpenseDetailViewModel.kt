package mx.budget.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.budget.data.local.result.ExpenseWithDetails
import mx.budget.data.location.LocationProvider
import mx.budget.data.location.LocationSource
import mx.budget.data.repository.ExpenseRepository

/**
 * Estado del detalle de un gasto (Apéndice G.4.1 / G.4.3).
 *
 * Combina los campos de display del [row] (concepto, monto, categoría, wallet) con
 * los campos de ubicación/hora editables, leídos del `ExpenseEntity` por id. La hoja
 * observa esto para mostrar lugar+hora y habilitar "añadir ubicación".
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
) {
    /** `true` si el gasto ya trae una ubicación real (no NONE/null). */
    val hasLocation: Boolean
        get() = latitude != null && longitude != null &&
            locationSource != null && locationSource != LocationSource.NONE
}

/**
 * ViewModel de la hoja de detalle del gasto (§G.4).
 *
 * Permite ver la ubicación (lugar reverse-geocodeado) y la hora, **editar la hora**
 * (G.4.1) y **añadir ubicación a mano** (G.4.3, `location_source=MANUAL`) cuando el
 * gasto no la tiene — típico de los 793 sembrados o de capturas sin permiso.
 *
 * Self-contained: solo depende del [ExpenseRepository] (escritura transaccional +
 * sync) y el [LocationProvider]. La hoja se abre con [open] y se cierra con [dismiss].
 */
class ExpenseDetailViewModel(
    private val expenseRepository: ExpenseRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    private val _state = MutableStateFlow<ExpenseDetailState?>(null)

    /** Detalle abierto, o `null` si la hoja está cerrada. */
    val state: StateFlow<ExpenseDetailState?> = _state.asStateFlow()

    /** Abre la hoja para [row], cargando los campos de ubicación/hora del gasto. */
    fun open(row: ExpenseWithDetails) {
        _state.value = ExpenseDetailState(row = row, occurredAt = row.occurredAt)
        viewModelScope.launch {
            val entity = expenseRepository.getById(row.expenseId) ?: return@launch
            _state.update {
                it?.copy(
                    occurredAt = entity.occurredAt,
                    latitude = entity.latitude,
                    longitude = entity.longitude,
                    placeLabel = entity.placeLabel,
                    locationSource = entity.locationSource,
                )
            }
        }
    }

    /** Cierra la hoja y limpia el estado. */
    fun dismiss() {
        _state.value = null
    }

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

    /** Actualiza la fecha/hora del gasto (G.4.1, epoch millis). */
    fun setOccurredAt(occurredAt: Long) {
        val current = _state.value ?: return
        viewModelScope.launch {
            expenseRepository.setOccurredAt(current.row.expenseId, occurredAt)
            _state.update { it?.copy(occurredAt = occurredAt) }
        }
    }
}
