package mx.budget.wear.data

import android.content.Context
import mx.budget.core.wear.WearPaths
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cola minima de envios que no salieron, como un JSON array en las mismas prefs
 * del cache. Se drena al abrir el hub y cuando vuelve el telefono.
 *
 * **Que entra: solo capturas** (gasto, ingreso y lenguaje natural dictado). Son
 * las unicas donde perder el envio cuesta trabajo de la persona: tecleo un monto
 * en una pantalla de reloj, dicto un concepto. Que eso se pierda porque el
 * telefono estaba en otra habitacion es exactamente lo que no debe pasar.
 *
 * **Que NO entra: confirmar y descartar pendientes.** Referencian un
 * `pending_capture.id` que pudo resolverse en el telefono mientras tanto, y
 * reproducir esa orden horas despues es peor que no hacer nada. Ademas la
 * pantalla de pendientes ya revierte el borrado optimista y el item reaparece
 * con el siguiente snapshot, asi que el reintento natural ya existe.
 *
 * **Por que prefs y no `DataClient`.** Un `DataItem` es un almacen de estado, no
 * una cola: dos gastos identicos ("cafe 50") se deduplicarian y uno se perderia
 * en silencio, que es el peor fallo posible aqui. Hacerlo bien exigiria un path
 * por envio, un `onDataChanged` nuevo en el telefono, borrado del item tras
 * consumirlo e idempotencia contra la re-entrega. Eso es rediseñar el camino de
 * escritura, y estrena un modo de fallo (gastos duplicados en la bandeja) peor
 * que el que arregla.
 *
 * El riesgo de duplicado al reintentar es bajo: `sendMessage().await()` solo
 * completa cuando el mensaje llego al nodo, asi que un fallo significa que no se
 * entrego. Y si aun asi se colara uno, cae en la bandeja bajo el patron de
 * proponer y confirmar, donde se ve y se descarta.
 */
object Outbox {

    /** JSON array de `{path, payload, at}`. */
    const val K_OUTBOX = "outbox_json"

    /** Tope: acota lo que ocupan las prefs y evita arrastrar una cola eterna. */
    private const val MAX = 20

    private fun prefs(context: Context) =
        context.getSharedPreferences(WearCache.PREFS, Context.MODE_PRIVATE)

    private fun read(context: Context): MutableList<Entry> {
        val raw = prefs(context).getString(K_OUTBOX, null) ?: return mutableListOf()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Entry(o.optString("path"), o.optString("payload"), o.optLong("at"))
            }.toMutableList()
        }.getOrDefault(mutableListOf())
    }

    private fun write(context: Context, entries: List<Entry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject().put("path", e.path).put("payload", e.payload).put("at", e.at))
        }
        prefs(context).edit().putString(K_OUTBOX, arr.toString()).apply()
    }

    fun size(context: Context): Int = read(context).size

    fun enqueue(context: Context, path: String, payload: String) {
        val entries = read(context)
        entries.add(Entry(path, payload, System.currentTimeMillis()))
        // Si se desborda se tira lo MAS VIEJO: lo que la persona acaba de teclear
        // importa mas que un envio de anteayer que quiza ya ni recuerde.
        write(context, entries.takeLast(MAX))
    }

    /**
     * Reintenta en orden y se detiene al primer fallo, para no gastar la bateria
     * repitiendo veinte veces el mismo error cuando el telefono sigue sin estar.
     * Devuelve cuantos quedaron sin enviar.
     */
    suspend fun drain(context: Context, sender: ExpenseSender): Int {
        val entries = read(context)
        if (entries.isEmpty()) return 0
        var sent = 0
        for (e in entries) {
            if (sender.sendRaw(e.path, e.payload).isFailure) break
            sent++
        }
        val left = entries.drop(sent)
        write(context, left)
        if (sent > 0) PhoneLink.requestTileUpdates(context)
        return left.size
    }

    /** Si el path es de los que se encolan (ver la nota de arriba). */
    fun isQueueable(path: String): Boolean = path == WearPaths.PATH_NEW_EXPENSE ||
        path == WearPaths.PATH_NEW_INCOME ||
        path == WearPaths.PATH_NEW_NL

    data class Entry(val path: String, val payload: String, val at: Long)
}
