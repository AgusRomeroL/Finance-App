package mx.budget.wear.data

import android.content.Context
import org.json.JSONArray
import java.util.Locale

/**
 * Cache local del reloj (SharedPreferences), poblado por [MobileSyncListenerService]
 * desde el push del teléfono. **Única fuente de datos del reloj**: ni Room, ni red,
 * ni LLM: todo llega ya cocinado del teléfono y aquí solo se lee y se parsea.
 *
 * Los payloads de lista viajan como JSON string (los serializa `WearSnapshotBuilder`
 * en el teléfono) y se parsean con `org.json` (incluido en Android, sin deps). Lo
 * consumen los Tiles ProtoLayout y las pantallas del hub.
 */
object WearCache {

    const val PREFS = "wear_budget_prefs"

    const val K_BALANCE = "latest_balance"        // Float
    const val K_BUDGET_TOTAL = "budget_total"     // Float (denominador del arco)
    const val K_LABEL = "latest_label"            // String
    const val K_SUGGESTIONS = "suggestions_json"  // JSON array
    const val K_MOVEMENTS = "movements_json"      // JSON array
    const val K_PENDING = "pending_json"          // JSON array
    const val K_MEMBER_SPEND = "member_spend_json" // JSON array
    const val K_UPCOMING = "upcoming_json"        // JSON array
    const val K_CACHE_VERSION = "cache_version"   // Long

    /**
     * Instante de LLEGADA del ultimo snapshot, medido con el reloj DEL RELOJ.
     *
     * A proposito no se reutiliza [K_CACHE_VERSION] para esto, aunque su valor
     * sea un `currentTimeMillis`: esa marca la pone el TELEFONO, y restarla del
     * `currentTimeMillis` del reloj mezcla dos relojes distintos. Un reloj
     * adelantado marcaria todo como viejo y uno atrasado no marcaria nada nunca,
     * y las dos formas de fallar son silenciosas. Anotando la llegada aqui, la
     * edad es una resta dentro de una sola base de tiempo.
     *
     * (Tampoco vale `elapsedRealtime`, que seria inmune incluso a que el usuario
     * cambie la hora: se reinicia al reiniciar el reloj, asi que tras un reboot
     * todo snapshot pareceria recien llegado.)
     */
    const val K_RECEIVED_AT = "received_at"       // Long

    /**
     * A partir de aqui el dato se considera viejo y las superficies lo dicen.
     *
     * La cadencia nominal es alta (el telefono empuja en cada cambio del
     * dashboard y el worker periodico cada ~15 min), asi que media hora parece
     * defendible. No lo es: con el telefono en doze nocturno WorkManager difiere
     * el trabajo periodico, y un hueco de cuatro a ocho horas de madrugada es
     * normal, no un fallo. Un umbral corto gritaria "ANTIGUO" cada manana sobre
     * un numero correcto, y una alarma que suena todos los dias deja de leerse.
     *
     * El caso urgente (telefono ausente) ya lo cubre [PhoneLink], que es
     * inmediato. Esto es el respaldo para el caso raro: conectado pero mudo.
     * Seis horas son veinticuatro ciclos nominales perdidos: sin ambiguedad.
     */
    const val STALE_AFTER_MS = 6L * 60 * 60 * 1000

    // ── Modelos planos que consume la UI del reloj ──────────────────────────────

    data class Suggestion(
        val concept: String,
        val amount: Double,
        val reason: String,
        val canonicalKey: String,
    )

    data class Movement(val concept: String, val amount: Double, val occurredAt: Long)

    data class Pending(
        val id: String,
        val concept: String,
        val amount: Double,
        val occurredAt: Long,
        val source: String,
    )

    data class MemberSpend(val name: String, val total: Double)

    data class Upcoming(val concept: String, val amount: Double, val dueDate: Long)

    // ── Lectura ─────────────────────────────────────────────────────────────────

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun balance(context: Context): Double = prefs(context).getFloat(K_BALANCE, 0f).toDouble()

    fun budgetTotal(context: Context): Double = prefs(context).getFloat(K_BUDGET_TOTAL, 0f).toDouble()

    fun cacheVersion(context: Context): Long = prefs(context).getLong(K_CACHE_VERSION, 0L)

    /** Cuando llego el ultimo snapshot, en el reloj del reloj. Cero si ninguno. */
    fun receivedAt(context: Context): Long = prefs(context).getLong(K_RECEIVED_AT, 0L)

    /**
     * Si alguna vez llego un snapshot. Sin esto, un cache vacio y un presupuesto
     * realmente agotado se pintaban los dos como "$0", que es la peor confusion
     * posible en una app de dinero.
     */
    fun hasData(context: Context): Boolean = receivedAt(context) > 0L

    /** Edad del snapshot. [Long.MAX_VALUE] si nunca llego ninguno. */
    fun snapshotAgeMs(context: Context): Long {
        val at = receivedAt(context)
        if (at <= 0L) return Long.MAX_VALUE
        // coerceAtLeast(0): si el usuario atrasa la hora del reloj, la resta sale
        // negativa. Cero (recien llegado) es mejor mentira que una edad absurda.
        return (System.currentTimeMillis() - at).coerceAtLeast(0L)
    }

    fun isStale(context: Context): Boolean = snapshotAgeMs(context) >= STALE_AFTER_MS

    fun label(context: Context): String =
        prefs(context).getString(K_LABEL, "Sin sincronizar") ?: "Sin sincronizar"

    fun suggestions(context: Context): List<Suggestion> =
        parseArray(prefs(context).getString(K_SUGGESTIONS, null)) { o ->
            Suggestion(
                concept = o.optString("concept"),
                amount = o.optDouble("amount", 0.0),
                reason = o.optString("reason"),
                canonicalKey = o.optString("canonicalKey"),
            )
        }

    fun movements(context: Context): List<Movement> =
        parseArray(prefs(context).getString(K_MOVEMENTS, null)) { o ->
            Movement(
                concept = o.optString("concept"),
                amount = o.optDouble("amount", 0.0),
                occurredAt = o.optLong("occurredAt", 0L),
            )
        }

    fun pending(context: Context): List<Pending> =
        parseArray(prefs(context).getString(K_PENDING, null)) { o ->
            Pending(
                id = o.optString("id"),
                concept = o.optString("concept"),
                amount = o.optDouble("amount", 0.0),
                occurredAt = o.optLong("occurredAt", 0L),
                source = o.optString("source"),
            )
        }

    fun memberSpend(context: Context): List<MemberSpend> =
        parseArray(prefs(context).getString(K_MEMBER_SPEND, null)) { o ->
            MemberSpend(name = o.optString("name"), total = o.optDouble("total", 0.0))
        }

    fun upcoming(context: Context): List<Upcoming> =
        parseArray(prefs(context).getString(K_UPCOMING, null)) { o ->
            Upcoming(
                concept = o.optString("concept"),
                amount = o.optDouble("amount", 0.0),
                dueDate = o.optLong("dueDate", 0L),
            )
        }

    private fun <T> parseArray(json: String?, map: (org.json.JSONObject) -> T): List<T> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { map(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    // ── Formato ─────────────────────────────────────────────────────────────────

    /** "$1,234": pesos sin decimales, con separador de miles. */
    fun money(amount: Double): String = "$" + String.format(Locale.US, "%,.0f", amount)

    /**
     * "$8.1k", "$26k", "-$1.2k". El SHORT_TEXT de una complication da unos siete
     * caracteres, y [money] produce "$123,456", que son ocho y se recorta.
     */
    fun moneyCompact(amount: Double): String {
        val abs = kotlin.math.abs(amount)
        val sign = if (amount < 0) "-" else ""
        val body = when {
            abs < 1_000 -> String.format(Locale.US, "%.0f", abs)
            abs < 10_000 ->
                String.format(Locale.US, "%.1f", abs / 1_000).removeSuffix(".0") + "k"
            abs < 1_000_000 -> String.format(Locale.US, "%.0f", abs / 1_000) + "k"
            else -> String.format(Locale.US, "%.1f", abs / 1_000_000).removeSuffix(".0") + "M"
        }
        return "$sign$" + body
    }
}
