package mx.budget.wear.data

import android.content.Context
import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import mx.budget.core.wear.WearPaths
import mx.budget.wear.presentation.tile.DisponibleTileService
import mx.budget.wear.presentation.tile.MemberSpendTileService
import mx.budget.wear.presentation.tile.PendingConfirmTileService
import mx.budget.wear.presentation.tile.QuickEntryTileService
import mx.budget.wear.presentation.tile.SuggestionsTileService
import mx.budget.wear.presentation.tile.UpcomingPaymentsTileService

/**
 * Estado del enlace con el telefono, cacheado como una preferencia mas.
 *
 * El problema que resuelve: los tiles y las complications son servicios
 * efimeros, no pueden suscribirse a nada, y consultar el Data Layer dentro de su
 * render metería latencia de red en el camino de dibujo. El hub, en cambio,
 * necesita reaccionar. La salida que encaja con la arquitectura que ya tiene el
 * reloj (todo son `SharedPreferences` mas un listener) es cachear la
 * alcanzabilidad: la escribe quien tiene el evento y la lee todo el mundo de
 * forma sincrona y barata.
 *
 * Se usa [CapabilityClient] y no `NodeClient` porque responde la pregunta
 * correcta. "Hay un nodo conectado" es cierto aunque la app del telefono este
 * desinstalada o parada; "hay un nodo que declara [WearPaths.CAPABILITY_PHONE_APP]"
 * es exactamente la condicion bajo la que un envio puede llegar. Ademas el
 * sistema despierta a `MobileSyncListenerService.onCapabilityChanged` cuando eso
 * cambia, asi que no hace falta sondear.
 */
object PhoneLink {

    /**
     * Boolean en las mismas prefs del cache. Default optimista a proposito: en
     * una instalacion fresca, antes de que llegue ningun evento, decir "sin
     * telefono cerca" seria una falsa alarma. Cualquiera de los tres escritores
     * corrige el valor en menos de un segundo.
     */
    const val K_PHONE_REACHABLE = "phone_reachable"

    private fun prefs(context: Context) =
        context.getSharedPreferences(WearCache.PREFS, Context.MODE_PRIVATE)

    fun isReachable(context: Context): Boolean =
        prefs(context).getBoolean(K_PHONE_REACHABLE, true)

    /**
     * Escribe SOLO si el valor cambia y, en ese flanco, pide refresco de los
     * tiles. Concentrar aqui el fan-out evita que cada llamador lo repita y que
     * un evento de conexion repetido dispare seis renders para nada.
     */
    fun setReachable(context: Context, reachable: Boolean) {
        val p = prefs(context)
        if (p.getBoolean(K_PHONE_REACHABLE, true) == reachable) return
        p.edit().putBoolean(K_PHONE_REACHABLE, reachable).apply()
        requestTileUpdates(context)
    }

    /**
     * Sonda activa con tope de tiempo, para el primer arranque: antes de que
     * llegue ningun evento de capability no hay nada cacheado que consultar.
     */
    suspend fun probe(context: Context, timeoutMs: Long = 1_500L): Boolean =
        withTimeoutOrNull(timeoutMs) {
            runCatching {
                Wearable.getCapabilityClient(context)
                    .getCapability(WearPaths.CAPABILITY_PHONE_APP, CapabilityClient.FILTER_REACHABLE)
                    .await()
                    .nodes
                    .isNotEmpty()
            }.getOrNull()
        } ?: false

    /** Repinta los seis tiles. Cada uno por separado: si uno falla, siguen los otros. */
    fun requestTileUpdates(context: Context) {
        val updater = TileService.getUpdater(context)
        listOf(
            SuggestionsTileService::class.java,
            QuickEntryTileService::class.java,
            DisponibleTileService::class.java,
            UpcomingPaymentsTileService::class.java,
            MemberSpendTileService::class.java,
            PendingConfirmTileService::class.java,
        ).forEach { runCatching { updater.requestUpdate(it) } }
    }
}
