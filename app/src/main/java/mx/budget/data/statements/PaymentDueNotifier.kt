package mx.budget.data.statements

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import mx.budget.MainActivity
import mx.budget.R

/**
 * Notificación "se acerca la fecha límite de pago de tu tarjeta" (estados v2 Fase 5).
 * Canal propio, separado del recordatorio de importación.
 */
object PaymentDueNotifier {

    const val CHANNEL_ID = "payment_due_reminders"

    fun ensureChannel(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Pagos de tarjeta",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Avisos antes de la fecha límite de pago de tus tarjetas." }
        mgr.createNotificationChannel(channel)
    }

    /** @param cardName tarjeta, @param dueIso fecha límite, @param detail "mínimo $X · sin intereses $Y". */
    fun notifyDue(context: Context, walletId: String, cardName: String, dueIso: String, detail: String) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("$cardName: pago antes del $dueIso")
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(context))
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(("due:$walletId").hashCode(), notif)
        }
    }

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, "payment_due_open".hashCode(), intent, flags)
    }
}
