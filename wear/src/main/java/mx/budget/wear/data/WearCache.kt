package mx.budget.wear.data

import android.content.Context
import org.json.JSONArray
import java.util.Locale

/**
 * Cache local del reloj (SharedPreferences), poblado por [MobileSyncListenerService]
 * desde el push del teléfono. **Única fuente de datos del reloj**: ni Room, ni red,
 * ni LLM — todo llega ya cocinado del teléfono y aquí solo se lee/parsea.
 *
 * Los payloads de lista viajan como JSON string (los serializa `WearSnapshotBuilder`
 * en el teléfono) y se parsean con `org.json` (incluido en Android, sin deps). Lo
 * consumen los Tiles ProtoLayout y las pantallas del hub.
 */
object WearCache {

    const val PREFS = "wear_budget_prefs"

    const val K_BALANCE = "latest_balance"        // Float
    const val K_LABEL = "latest_label"            // String
    const val K_SUGGESTIONS = "suggestions_json"  // JSON array
    const val K_MOVEMENTS = "movements_json"      // JSON array
    const val K_PENDING = "pending_json"          // JSON array
    const val K_MEMBER_SPEND = "member_spend_json" // JSON array

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

    // ── Lectura ─────────────────────────────────────────────────────────────────

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun balance(context: Context): Double = prefs(context).getFloat(K_BALANCE, 0f).toDouble()

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

    private fun <T> parseArray(json: String?, map: (org.json.JSONObject) -> T): List<T> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { map(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    // ── Formato ─────────────────────────────────────────────────────────────────

    /** "$1,234" — pesos sin decimales, con separador de miles. */
    fun money(amount: Double): String = "$" + String.format(Locale.US, "%,.0f", amount)
}
