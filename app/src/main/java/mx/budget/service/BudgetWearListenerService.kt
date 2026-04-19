package mx.budget.service

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import mx.budget.BudgetApplication
import mx.budget.core.wear.WearPaths
import mx.budget.data.local.entity.ExpenseEntity
import java.util.UUID

/**
 * Servicio residente en el Móvil que escucha transacciones entrantes desde el Reloj.
 * Recibe intents predefinidos de Quick Capture y los persiste atómicamente en Room.
 */
class BudgetWearListenerService : WearableListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == WearPaths.PATH_NEW_EXPENSE) {
            val payload = String(messageEvent.data)
            // Payload esperado (simplificado): "amount|concept"
            val parts = payload.split("|")
            if (parts.size == 2) {
                val amount = parts[0].toDoubleOrNull() ?: return
                val concept = parts[1]
                
                serviceScope.launch {
                    persistQuickExpense(amount, concept)
                }
            }
        } else {
            super.onMessageReceived(messageEvent)
        }
    }

    private suspend fun persistQuickExpense(amount: Double, concept: String) {
        // Accedemos a la base de recursos globales atómicamente (Cero mocking)
        val app = applicationContext as BudgetApplication
        val database = app.database

        // Obtiene la quincena activa vigente
        val activeQuincena = database.quincenaDao().currentActive("default_household") ?: return
        
        // Creación del registro (Utilizando defaults preestablecidos para el flujo express de reloj)
        val expense = ExpenseEntity(
            id = UUID.randomUUID().toString(),
            householdId = activeQuincena.householdId,
            occurredAt = System.currentTimeMillis(),
            quincenaId = activeQuincena.id,
            categoryId = "UNASSIGNED", // Categoría temporal hasta reconciliación
            concept = concept,
            amountMxn = amount,
            paymentMethodId = "DEFAULT_WALLET", // Wallet por defecto
            status = "POSTED",
            createdAt = System.currentTimeMillis()
        )

        // Se inserta atómicamente la entidad en el ledger
        database.expenseDao().insert(expense)
        
        // Actualiza heurísticamente la quincena actual (Actualización de proyecciones)
        val updatedActualExpenses = activeQuincena.actualExpensesMxn + amount
        database.quincenaDao().update(
            activeQuincena.copy(actualExpensesMxn = updatedActualExpenses)
        )
    }
}
