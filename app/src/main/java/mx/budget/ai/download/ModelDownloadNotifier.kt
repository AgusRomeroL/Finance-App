package mx.budget.ai.download

import android.app.Notification
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
 * Notificación persistente de la descarga del modelo de IA.
 *
 * Canal aparte y en `IMPORTANCE_LOW`: es un progreso que dura minutos, no un aviso
 * que deba sonar. Es la notificación que sostiene el servicio en primer plano del
 * [ModelDownloadWorker], así que sin ella la descarga la mataría el sistema al salir
 * de la app.
 */
object ModelDownloadNotifier {

    const val CHANNEL_ID = "model_download"
    const val NOTIF_ID = 0x6D0D

    fun ensureChannel(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Descarga del asistente",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Progreso de la descarga del modelo de IA." }
        mgr.createNotificationChannel(channel)
    }

    /** Notificación de progreso. [total] en cero pinta la barra indeterminada. */
    fun progress(context: Context, title: String, written: Long, total: Long): Notification {
        ensureChannel(context)
        val indeterminate = total <= 0L
        val percent = if (indeterminate) 0 else ((written * 100) / total).toInt().coerceIn(0, 100)
        val detail = if (indeterminate) {
            "Preparando la descarga"
        } else {
            "${megabytes(written)} de ${megabytes(total)} MB"
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(detail)
            .setProgress(100, percent, indeterminate)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(context))
            .build()
    }

    /** Aviso final, ya descartable. */
    fun finished(context: Context, title: String, detail: String) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannel(context)
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIF_ID + 1, notif) }
    }

    private fun megabytes(bytes: Long): String = "%.0f".format(bytes / 1_048_576.0)

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, "model_download_open".hashCode(), intent, flags)
    }
}
