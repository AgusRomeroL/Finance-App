package mx.budget.core.shortcuts

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import mx.budget.ui.capture.QuickCaptureActivity

object DynamicShortcutManager {

    /**
     * Instala un App Shortcut programáticamente en el Launcher del usuario.
     * Diseñado para facilitar la integración con gestos de Hardware (ej: Quick Tap / Moto Actions)
     * hacia un intent explícito sin arrancar MainActivity.
     */
    fun pushQuickCaptureShortcut(context: Context) {
        if (ShortcutManagerCompat.getDynamicShortcuts(context).any { it.id == "quick_capture" }) return

        val intent = Intent(context, QuickCaptureActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val shortcut = ShortcutInfoCompat.Builder(context, "quick_capture")
            .setShortLabel("Captura Rápida")
            .setLongLabel("Registrar Gasto Rápidamente")
            // IconCompat.createWithResource(context, R.drawable.ic_shortcut_capture) <- Replace in prod
            .setIcon(IconCompat.createWithResource(context, android.R.drawable.ic_input_add))
            .setIntent(intent)
            .build()

        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
    }
}
