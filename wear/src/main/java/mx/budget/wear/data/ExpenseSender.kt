package mx.budget.wear.data

import android.content.Context
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import mx.budget.core.wear.WearPaths

/**
 * Cliente de emisión en Wear OS.
 * Envía un raw 'Message' directo al teléfono principal acoplado para ejecutar la 
 * persistencia en el backend Room en caso de click a un botón de "Registrar $500 Gasolina".
 */
class ExpenseSender(private val context: Context) {

    private val messageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)

    suspend fun sendQuickExpense(amount: Double, concept: String): Result<Unit> =
        sendQueued(WearPaths.PATH_NEW_EXPENSE, "$amount|$concept")

    /**
     * Confirma un cargo recomendado (Tile A) reusando el camino de gasto rápido:
     * el teléfono lo deja en la bandeja `pending_capture` (propose-then-confirm).
     */
    suspend fun acceptSuggestion(amount: Double, concept: String): Result<Unit> =
        sendQuickExpense(amount, concept)

    /** Ingreso manual (monto|etiqueta). El teléfono lo inserta PLANNED. */
    suspend fun sendIncome(amount: Double, label: String): Result<Unit> =
        sendQueued(WearPaths.PATH_NEW_INCOME, "$amount|$label")

    /** Confirma una captura de la bandeja desde el reloj (payload = id). */
    suspend fun confirmPending(id: String): Result<Unit> =
        send(WearPaths.PATH_CONFIRM_PENDING, id)

    /** Descarta una captura de la bandeja desde el reloj (payload = id). */
    suspend fun discardPending(id: String): Result<Unit> =
        send(WearPaths.PATH_DISCARD_PENDING, id)

    /**
     * Envía una frase en lenguaje natural dictada en el reloj (§G.3). El reloj NO
     * corre el LLM: el teléfono recibe el texto crudo, lo parsea y lo enriquece, dejando
     * la propuesta en la bandeja (propose-then-confirm).
     */
    suspend fun sendNaturalLanguage(text: String): Result<Unit> =
        sendQueued(WearPaths.PATH_NEW_NL, text)

    /**
     * Pide al teléfono un snapshot fresco (pull-on-open del espejo en vivo, §G.3.3).
     * El teléfono responde re-empujando el estado por el Data Layer → el cache del
     * reloj se repuebla y "Disponible" refleja la cifra real aunque el dashboard del
     * teléfono no esté abierto. Best-effort: falla en silencio si no hay teléfono.
     */
    suspend fun requestSync(): Result<Unit> =
        send(WearPaths.PATH_REQUEST_SYNC, "")

    /**
     * Envio de captura: si no sale, se guarda en [Outbox] para reintentarlo al
     * volver el telefono. Sigue devolviendo el fallo, porque la pantalla tiene
     * que decir la verdad ("se enviara al reconectar" y no un exito falso).
     */
    private suspend fun sendQueued(path: String, payload: String): Result<Unit> =
        send(path, payload).onFailure { Outbox.enqueue(context, path, payload) }

    /** Reintento desde la cola. NO reencola: de eso se ocupa [Outbox.drain]. */
    internal suspend fun sendRaw(path: String, payload: String): Result<Unit> =
        send(path, payload)

    private suspend fun send(path: String, payload: String): Result<Unit> {
        return try {
            // Localiza el nodo conectado primario (El Teléfono)
            val nodes = nodeClient.connectedNodes.await()
            val targetNode = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
            if (targetNode == null) {
                PhoneLink.setReachable(context, false)
                return Result.failure(Exception("Teléfono no conectado"))
            }

            messageClient.sendMessage(targetNode.id, path, payload.toByteArray()).await()
            // Un envio que llega es la mejor prueba de que hay telefono, mejor
            // que cualquier sonda: se aprovecha para refrescar el estado.
            PhoneLink.setReachable(context, true)
            Result.success(Unit)
        } catch (e: Exception) {
            PhoneLink.setReachable(context, false)
            Result.failure(e)
        }
    }
}
