package mx.budget.data.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import mx.budget.MainActivity
import mx.budget.R
import mx.budget.data.local.result.PlannedReminder
import java.text.NumberFormat
import java.util.Locale

/**
 * Notificación de recordatorio de un gasto `PLANNED` (Apéndice G.2, Fase 3).
 *
 * Espejo de la notificación de captura bancaria ([mx.budget.data.capture.BankCaptureManager]),
 * pero sobre el ledger (no `pending_capture`): el gasto ya existe como PLANNED y la
 * acción [Confirmar] lo pasa a POSTED vía `confirmPlanned`. Tres acciones:
 *  - **Confirmar** → broadcast a [ReminderActionReceiver] (POSTED con el monto previsto).
 *  - **Posponer**  → broadcast a [ReminderActionReceiver] (re-agenda, sigue PLANNED).
 *  - **Editar**    → abre la app para ajustar monto/atribución (la edición fina vive
 *    en el dashboard/calendario; aquí solo se navega).
 */
object ReminderNotifier {

    const val CHANNEL_ID = "planned_reminders"

    /** Crea el canal de recordatorios (idempotente). Llamar al arrancar. */
    fun ensureChannel(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Recordatorios de gastos",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Avisos de gastos planeados próximos a vencer." }
        mgr.createNotificationChannel(channel)
    }

    /** Postea (o actualiza) la notificación de un PLANNED próximo. */
    fun notify(context: Context, reminder: PlannedReminder) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val money = NumberFormat.getCurrencyInstance(Locale("es", "MX")).format(reminder.amountMxn)

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Próximo: $money en ${reminder.concept}")
            .setContentText("¿Ya se realizó este gasto?")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent(context, reminder.expenseId))
            .addAction(0, "Confirmar", broadcast(context, reminder.expenseId, ReminderActions.CONFIRM))
            .addAction(0, "Editar", openAppIntent(context, reminder.expenseId))
            .addAction(0, "Posponer", broadcast(context, reminder.expenseId, ReminderActions.POSTPONE))
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId(reminder.expenseId), notif)
        }
    }

    fun cancel(context: Context, expenseId: String) {
        NotificationManagerCompat.from(context).cancel(notificationId(expenseId))
    }

    private fun notificationId(expenseId: String): Int = expenseId.hashCode()

    private fun broadcast(context: Context, expenseId: String, action: String): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java).apply {
            this.action = action
            putExtra(ReminderActions.EXTRA_EXPENSE_ID, expenseId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, (expenseId + action).hashCode(), intent, flags)
    }

    private fun openAppIntent(context: Context, expenseId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, (expenseId + "open").hashCode(), intent, flags)
    }
}
