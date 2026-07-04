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
        send(WearPaths.PATH_NEW_EXPENSE, "$amount|$concept")

    /**
     * Confirma un cargo recomendado (Tile A) reusando el camino de gasto rápido:
     * el teléfono lo deja en la bandeja `pending_capture` (propose-then-confirm).
     */
    suspend fun acceptSuggestion(amount: Double, concept: String): Result<Unit> =
        sendQuickExpense(amount, concept)

    /** Ingreso manual (monto|etiqueta). El teléfono lo inserta PLANNED. */
    suspend fun sendIncome(amount: Double, label: String): Result<Unit> =
        send(WearPaths.PATH_NEW_INCOME, "$amount|$label")

    /** Confirma una captura de la bandeja desde el reloj (payload = id). */
    suspend fun confirmPending(id: String): Result<Unit> =
        send(WearPaths.PATH_CONFIRM_PENDING, id)

    /** Descarta una captura de la bandeja desde el reloj (payload = id). */
    suspend fun discardPending(id: String): Result<Unit> =
        send(WearPaths.PATH_DISCARD_PENDING, id)

    /**
     * Envía una frase en lenguaje natural dictada en el reloj (§G.3). El reloj NO
     * corre el LLM: el teléfono recibe el texto crudo y lo parsea/enriquece, dejando
     * la propuesta en la bandeja (propose-then-confirm).
     */
    suspend fun sendNaturalLanguage(text: String): Result<Unit> =
        send(WearPaths.PATH_NEW_NL, text)

    private suspend fun send(path: String, payload: String): Result<Unit> {
        return try {
            // Localiza el nodo conectado primario (El Teléfono)
            val nodes = nodeClient.connectedNodes.await()
            val targetNode = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
                ?: return Result.failure(Exception("Teléfono no conectado"))

            messageClient.sendMessage(targetNode.id, path, payload.toByteArray()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
