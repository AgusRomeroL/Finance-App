package mx.budget.wear.data

import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import mx.budget.core.wear.WearPaths

/**
 * Servicio residente en el Reloj (Wear OS).
 * Despierta cuando se detecta un push del móvil con el balance actual,
 * guardándolo internamente mediante SharedPreferences para el renderizado inmediato 
 * del Tile y App de captura sin necesitar un call network directo.
 */
class MobileSyncListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val prefs = getSharedPreferences("wear_budget_prefs", Context.MODE_PRIVATE)
        
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val path = event.dataItem.uri.path
                if (path == WearPaths.PATH_BUDGET_SYNC) {
                    val dataMapItem = DataMapItem.fromDataItem(event.dataItem)
                    val map = dataMapItem.dataMap

                    val balance = map.getDouble(WearPaths.KEY_BALANCE_DISPONIBLE, 0.0)
                    val label = map.getString(WearPaths.KEY_QUINCENA_LABEL, "")
                    
                    // Almacenamiento perenne nativo
                    prefs.edit()
                        .putFloat("latest_balance", balance.toFloat())
                        .putString("latest_label", label)
                        .apply()
                }
            }
        }
    }
}
