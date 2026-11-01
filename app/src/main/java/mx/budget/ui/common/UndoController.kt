package mx.budget.ui.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Deshacer a nivel de app (Fase 7b, alcance 3).
 *
 * La hoja de captura se cierra al guardar y la hoja de detalle al borrar, y con
 * ellas se iría cualquier snackbar propio. Por eso los ViewModels publican aquí
 * lo que se puede deshacer y el grafo de navegación muestra un único
 * `SnackbarHost` sobre todas las pantallas. Un evento nuevo reemplaza al anterior.
 *
 * Vive en `BudgetApplication`, no en la actividad (Fase 8). Los ViewModels que
 * publican aquí sobreviven a que la actividad se recree, y eso pasa en el primer
 * arranque (fijar el idioma de la app la recrea), al cambiar de hogar y al
 * cambiar el tema del sistema. Con un controlador por actividad, el ViewModel
 * seguía publicando en el de la actividad muerta y el snackbar de la nueva nunca
 * aparecía, hasta reiniciar el proceso. Por lo mismo el deshacer corre en
 * [scope], el de la aplicación, y termina aunque la pantalla cambie.
 */
class UndoController(private val scope: CoroutineScope) {

    /** Algo que se puede deshacer: el mensaje del snackbar y la acción que lo revierte. */
    class Event(val message: String, val undo: suspend () -> Unit)

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 4)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    fun offer(message: String, undo: suspend () -> Unit) {
        _events.tryEmit(Event(message, undo))
    }

    /** Ejecuta el deshacer de [event]; [onError] recibe el motivo si falla. */
    fun runUndo(event: Event, onError: (String) -> Unit) {
        scope.launch {
            runCatching { event.undo() }
                .onFailure { onError(it.message ?: "No se pudo deshacer.") }
        }
    }
}
