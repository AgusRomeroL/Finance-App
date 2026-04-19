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

    suspend fun sendQuickExpense(amount: Double, concept: String): Result<Unit> {
        return try {
            // Localiza el nodo conectado primario (El Teléfono)
            val nodes = nodeClient.connectedNodes.await()
            val targetNode = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
            
            if (targetNode == null) {
                return Result.failure(Exception("Teléfono no conectado"))
            }

            val payload = "$amount|$concept".toByteArray()
            
            messageClient.sendMessage(
                targetNode.id,
                WearPaths.PATH_NEW_EXPENSE,
                payload
            ).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
