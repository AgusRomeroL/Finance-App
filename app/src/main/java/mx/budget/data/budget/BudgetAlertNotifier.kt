package mx.budget.data.budget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import mx.budget.MainActivity
import mx.budget.R
import mx.budget.data.local.entity.QuincenaEntity
import mx.budget.data.local.result.CategoryCapRow
import mx.budget.ui.common.AppLocale
import java.text.NumberFormat

/**
 * Avisos de presupuesto por categoria (Fase 7b, alcance 2): al 80 %, al 100 % y
 * el anticipado, cuando al ritmo actual la categoria se pasaria del tope.
 *
 * Canal propio, porque es un aviso distinto a los recordatorios de pago y quien
 * lo reciba puede querer silenciar uno sin el otro. Una notificacion por
 * categoria y quincena: el nivel mayor reemplaza al menor con el mismo id. Tocarla
 * abre Analiticas con la categoria al frente ([EXTRA_NAV_TARGET]).
 */
object BudgetAlertNotifier {

    const val CHANNEL_ID = "budget_alerts"

    /** Extras del enlace a Analiticas que lee [MainActivity]. */
    const val EXTRA_NAV_TARGET = "mx.budget.nav_target"
    const val EXTRA_CATEGORY_ID = "mx.budget.category_id"
    const val NAV_TARGET_ANALYTICS = "analytics"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.budget_alert_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.budget_alert_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Publica el aviso de [row] en [level]. Devuelve true solo si de verdad se
     * publico: el worker no marca como avisado lo que el permiso o el canal
     * apagado callaron (la trampa que dejo el recordatorio de pagos).
     */
    fun notify(
        context: Context,
        quincena: QuincenaEntity,
        row: CategoryCapRow,
        level: BudgetAlertLevel,
        projected: Double?,
    ): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        val channel = context.getSystemService(NotificationManager::class.java)?.getNotificationChannel(CHANNEL_ID)
        if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) return false

        val money = NumberFormat.getCurrencyInstance(AppLocale).apply { maximumFractionDigits = 0 }
        val gastado = money.format(row.spent)
        val tope = money.format(row.cap)
        val (title, text) = when (level) {
            BudgetAlertLevel.CIEN -> context.getString(
                // Justo en el tope no es "te pasaste": medio peso de margen por el redondeo.
                if (row.spent > row.cap + 0.5) R.string.budget_alert_over_title else R.string.budget_alert_reached_title,
                row.categoryName,
            ) to context.getString(R.string.budget_alert_over_text, gastado, tope)
            BudgetAlertLevel.OCHENTA -> context.getString(
                R.string.budget_alert_80_title, row.categoryName, CategoryForecast.pct(row.spent, row.cap),
            ) to context.getString(R.string.budget_alert_80_text, gastado, tope, money.format(row.cap - row.spent))
            BudgetAlertLevel.ANTICIPADO -> context.getString(R.string.budget_alert_forecast_title, row.categoryName) to
                context.getString(R.string.budget_alert_forecast_text, gastado, tope, money.format(projected ?: row.spent))
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_NAV_TARGET, NAV_TARGET_ANALYTICS)
            putExtra(EXTRA_CATEGORY_ID, row.categoryId)
        }
        val id = "budget:${quincena.id}|${row.categoryId}".hashCode()
        val abrir = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setContentIntent(abrir)
            .build()
        return runCatching { manager.notify(id, notif); true }.getOrDefault(false)
    }
}
