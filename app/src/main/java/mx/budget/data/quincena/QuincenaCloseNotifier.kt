package mx.budget.data.quincena

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import mx.budget.MainActivity
import mx.budget.R
import mx.budget.data.local.entity.QuincenaEntity
import mx.budget.data.reminder.ReminderNotifier

/**
 * Aviso de quincena pendiente de cierre (spec UX 2.3: al terminar el periodo, la
 * app pregunta si ya se puede cerrar).
 *
 * Reutiliza el canal de recordatorios en vez de abrir uno nuevo: para quien lo
 * recibe es el mismo tipo de aviso, algo del presupuesto que espera una decision,
 * y un canal mas solo ensucia los ajustes de notificaciones.
 *
 * Avisa una sola vez por quincena; el aviso persistente del dashboard es el que
 * insiste mientras el periodo siga abierto.
 */
object QuincenaCloseNotifier {

    fun notify(context: Context, quincena: QuincenaEntity) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val abrir = PendingIntent.getActivity(
            context,
            (quincena.id + "close").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(context, ReminderNotifier.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("¿Listo para cerrar ${quincena.label}?")
            .setContentText("Revisa lo que quedó planeado y congela el periodo.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(abrir)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(quincena.id.hashCode(), notif)
        }
    }
}
