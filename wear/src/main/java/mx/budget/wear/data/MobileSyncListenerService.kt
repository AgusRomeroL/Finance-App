package mx.budget.wear.data

import android.content.Context
import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import mx.budget.core.wear.WearPaths
import mx.budget.wear.presentation.tile.DisponibleTileService
import mx.budget.wear.presentation.tile.MemberSpendTileService
import mx.budget.wear.presentation.tile.PendingConfirmTileService
import mx.budget.wear.presentation.tile.QuickEntryTileService
import mx.budget.wear.presentation.tile.SuggestionsTileService
import mx.budget.wear.presentation.tile.UpcomingPaymentsTileService

/**
 * Servicio residente en el Reloj (Wear OS). Despierta con el push del móvil
 * ([WearPaths.PATH_BUDGET_SYNC]) y vuelca el snapshot completo (saldo, quincena y
 * los cuatro JSON: recomendados, movimientos, pendientes, gasto por miembro) en
 * [WearCache] (SharedPreferences) para el renderizado inmediato de los Tiles y el
 * hub sin llamadas a red ni a Room.
 *
 * Tras cachear, pide a los Tiles que se re-rendericen ([TileService.getUpdater])
 * para que reflejen los datos nuevos sin esperar al siguiente ciclo de refresco.
 */
class MobileSyncListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val prefs = getSharedPreferences(WearCache.PREFS, Context.MODE_PRIVATE)
        var changed = false

        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == WearPaths.PATH_BUDGET_SYNC
            ) {
                val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                prefs.edit()
                    .putFloat(
                        WearCache.K_BALANCE,
                        map.getDouble(WearPaths.KEY_BALANCE_DISPONIBLE, 0.0).toFloat(),
                    )
                    .putFloat(
                        WearCache.K_BUDGET_TOTAL,
                        map.getDouble(WearPaths.KEY_BUDGET_TOTAL, 0.0).toFloat(),
                    )
                    .putString(WearCache.K_LABEL, map.getString(WearPaths.KEY_QUINCENA_LABEL, ""))
                    .putString(WearCache.K_SUGGESTIONS, map.getString(WearPaths.KEY_SUGGESTIONS_JSON, "[]"))
                    .putString(WearCache.K_MOVEMENTS, map.getString(WearPaths.KEY_MOVEMENTS_JSON, "[]"))
                    .putString(WearCache.K_PENDING, map.getString(WearPaths.KEY_PENDING_JSON, "[]"))
                    .putString(WearCache.K_MEMBER_SPEND, map.getString(WearPaths.KEY_MEMBER_SPEND_JSON, "[]"))
                    .putString(WearCache.K_UPCOMING, map.getString(WearPaths.KEY_UPCOMING_JSON, "[]"))
                    // Llegada medida con el reloj DEL RELOJ, para que la edad del
                    // snapshot sea una resta dentro de una sola base de tiempo.
                    // Va antes de la clave de version, que tiene que quedar la
                    // ultima: cuando el hub reaccione, esta ya estara escrita.
                    .putLong(WearCache.K_RECEIVED_AT, System.currentTimeMillis())
                    // LAST: la versión monotónica que dispara UNA sola recomposición.
                    .putLong(WearCache.K_CACHE_VERSION, map.getLong(WearPaths.KEY_CACHE_VERSION, 0L))
                    .apply()
                changed = true
            }
        }

        if (changed) {
            // Si acaba de llegar un snapshot, por definicion hay telefono.
            PhoneLink.setReachable(this, true)
            runCatching {
                val updater = TileService.getUpdater(this)
                updater.requestUpdate(SuggestionsTileService::class.java)
                updater.requestUpdate(QuickEntryTileService::class.java)
                updater.requestUpdate(DisponibleTileService::class.java)
                updater.requestUpdate(UpcomingPaymentsTileService::class.java)
                updater.requestUpdate(MemberSpendTileService::class.java)
                updater.requestUpdate(PendingConfirmTileService::class.java)
            }
        }
    }

    /**
     * El sistema despierta este servicio cuando el telefono con la app entra o
     * sale del alcance. Es la via barata de mantener el estado del enlace: sin
     * sondeos, sin wakelocks y sin que el reloj tenga que descubrir la
     * desconexion solo cuando la persona ya intento enviar algo.
     */
    override fun onCapabilityChanged(info: com.google.android.gms.wearable.CapabilityInfo) {
        if (info.name != WearPaths.CAPABILITY_PHONE_APP) return
        PhoneLink.setReachable(this, info.nodes.isNotEmpty())
    }
}
