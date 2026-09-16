package mx.budget.ui.quicktap

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import mx.budget.ui.capture.QuickCaptureActivity

/**
 * Mosaico de Ajustes rápidos "Capturar gasto" (especificación §3.3, componente D).
 *
 * Quick Tap solo existe en los Pixel recientes; el mosaico existe desde Android 7
 * en cualquier teléfono. Es el mismo punto de entrada por otro camino, no una
 * función distinta.
 */
class QuickCaptureTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "Capturar gasto"
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, QuickCaptureActivity::class.java).apply {
            action = QuickCaptureActivity.ACCION_CAPTURA_RAPIDA
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
