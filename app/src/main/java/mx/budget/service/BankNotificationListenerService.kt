package mx.budget.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mx.budget.BudgetApplication

/**
 * Escucha las notificaciones del sistema y, sólo para los **bancos de la
 * allowlist** (`bank_templates.json`), extrae el cargo on-device y lo deja como
 * propuesta pendiente (Feature D, §F.6).
 *
 * Privacidad: cualquier notificación de un package fuera de la allowlist se
 * ignora de inmediato y **nunca** se lee ni persiste. La feature es opt-in: si
 * el usuario no la activó en Perfil, el servicio no hace nada aunque el sistema
 * le esté entregando notificaciones (el permiso del SO y el toggle son distintos).
 *
 * Requiere `BIND_NOTIFICATION_LISTENER_SERVICE` + que el usuario conceda "Acceso
 * a notificaciones" en Ajustes (no es un runtime permission normal).
 */
class BankNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val app = applicationContext as? BudgetApplication ?: return
        val parser = app.bankNotificationParser ?: return

        // Filtro barato ANTES de tocar el contenido: package en la allowlist.
        if (!parser.isAllowed(sbn.packageName)) return

        // Gate opt-in: si el usuario no activó la feature, no procesar.
        val enabled = runCatching { runBlocking { app.settingsRepository.bankCaptureEnabled.first() } }
            .getOrDefault(false)
        if (!enabled) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()

        val parsed = parser.parse(sbn.packageName, title, text, sbn.postTime) ?: return

        scope.launch {
            runCatching { app.bankCaptureManager.ingest(parsed) }
        }
    }
}
