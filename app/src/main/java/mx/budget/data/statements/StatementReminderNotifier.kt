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
 * Notificación mensual "hay estados de cuenta por importar" (Tarea 4).
 *
 * Canal propio (separado de `planned_reminders`): la cadencia es mensual, no por
 * gasto. Una sola notificación resumen que lista las tarjetas pendientes y abre la
 * app (el usuario llega al checklist "Estados del mes" desde Perfil).
 */
object StatementReminderNotifier {

    const val CHANNEL_ID = "statement_reminders"
    private const val NOTIF_ID = 0x57A7 // "STAT"

    fun ensureChannel(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Estados de cuenta",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Aviso mensual para importar los estados de cuenta." }
        mgr.createNotificationChannel(channel)
    }

    /** Postea el resumen de tarjetas pendientes. [walletNames] no vacío. */
    fun notifyPending(context: Context, walletNames: List<String>) {
        if (walletNames.isEmpty()) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val lista = walletNames.joinToString(", ")
        val titulo = if (walletNames.size == 1) "1 estado de cuenta por importar"
        else "${walletNames.size} estados de cuenta por importar"

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(titulo)
            .setContentText(lista)
            .setStyle(NotificationCompat.BigTextStyle().bigText("Sube: $lista"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent(context))
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIF_ID, notif)
        }
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, "statement_open".hashCode(), intent, flags)
    }
}
