package mx.budget.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mx.budget.BudgetApplication
import java.util.concurrent.TimeUnit

/** Acciones de la notificación de recordatorio de un gasto PLANNED (Fase 3). */
object ReminderActions {
    const val CONFIRM = "mx.budget.action.REMINDER_CONFIRM"
    const val POSTPONE = "mx.budget.action.REMINDER_POSTPONE"
    const val EXTRA_EXPENSE_ID = "expenseId"

    /** Cuánto se pospone un recordatorio al tocar [Posponer]: 1 día. */
    val SNOOZE_MILLIS: Long = TimeUnit.DAYS.toMillis(1)
}

/**
 * Recibe los taps de [Confirmar]/[Posponer] de la notificación de recordatorio y
 * delega en el ledger. Usa `goAsync()` para sobrevivir al trabajo de DB tras
 * devolver el control de `onReceive`.
 *
 * - **Confirmar:** `confirmPlanned` pasa el gasto a POSTED con el monto previsto
 *   (re-escala atribuciones si cambiara; aquí no cambia). El gasto deja de ser
 *   PLANNED, así que el worker lo poda del estado en su próxima corrida.
 * - **Posponer:** mantiene el gasto PLANNED y re-agenda el recordatorio para
 *   dentro de [ReminderActions.SNOOZE_MILLIS].
 */
class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val expenseId = intent.getStringExtra(ReminderActions.EXTRA_EXPENSE_ID) ?: return
        val app = context.applicationContext as? BudgetApplication ?: return
        val action = intent.action ?: return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ReminderActions.CONFIRM -> app.expenseRepository.confirmPlanned(expenseId)
                    ReminderActions.POSTPONE -> app.settingsRepository.snoozeReminder(
                        expenseId,
                        System.currentTimeMillis() + ReminderActions.SNOOZE_MILLIS,
                    )
                }
                ReminderNotifier.cancel(context, expenseId)
            } finally {
                pending.finish()
            }
        }
    }
}
