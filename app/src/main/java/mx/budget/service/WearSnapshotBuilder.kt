package mx.budget.service

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.first
import mx.budget.BudgetApplication
import mx.budget.ai.proactive.ProactiveSuggestionEngine
import mx.budget.core.wear.WearPaths
import mx.budget.data.local.entity.ExpenseEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * Arma y empuja el snapshot COMPLETO del presupuesto al reloj (Data Layer,
 * path [WearPaths.PATH_BUDGET_SYNC]). Sustituye al push mínimo de solo-balance:
 * además del saldo/quincena serializa a JSON (parseable con `org.json` en el
 * reloj, sin dependencias nuevas) cuatro colecciones que alimentan los Tiles y el
 * hub del reloj:
 *  - **recomendados** ([ProactiveSuggestionEngine]) con un monto típico histórico,
 *  - **últimos movimientos** POSTED,
 *  - **bandeja pendiente** (`pending_capture`),
 *  - **gasto por miembro** (rol BENEFICIARY) para las barras del Estado.
 *
 * Regla de oro: el reloj NO corre Room/LLM/red; todo se calcula aquí y se cachea
 * allá en `SharedPreferences` vía `MobileSyncListenerService`. Best-effort: nunca
 * lanza (si algo falla, se omite esa sección). Lo invocan [WearSyncManager]
 * (foreground, en cada cambio del dashboard) y el `ReminderWorker` (~15 min).
 */
object WearSnapshotBuilder {

    /** Cuántos items se empujan por colección (acota el tamaño del DataItem). */
    private const val MAX_SUGGESTIONS = 5
    private const val MAX_MOVEMENTS = 8
    private const val MAX_PENDING = 8

    /**
     * Reúne el estado y lo empuja al reloj. Suspende: consulta DAOs de Room.
     * Cada sección va protegida: un fallo aislado no tumba el resto del snapshot.
     */
    suspend fun push(context: Context) {
        val app = context.applicationContext as? BudgetApplication ?: return
        val householdId = app.householdId
        val quincena = runCatching {
            app.database.quincenaDao().getActive(householdId)
        }.getOrNull()

        val balance = quincena?.let { it.projectedIncomeMxn - it.actualExpensesMxn } ?: 0.0
        val label = quincena?.label ?: "Sin Quincena"

        val history = runCatching { app.database.expenseDao().getAll(householdId) }
            .getOrDefault(emptyList())
        val posted = history.filter { it.status == "POSTED" }

        val suggestionsJson = runCatching {
            val engine = ProactiveSuggestionEngine()
            val suggestions = engine.suggestMany(
                history = history,
                activeQuincena = quincena,
                nowEpochMs = System.currentTimeMillis(),
                limit = MAX_SUGGESTIONS,
            )
            JSONArray().apply {
                suggestions.forEach { s ->
                    put(
                        JSONObject()
                            .put("concept", s.concept)
                            .put("amount", typicalAmount(posted, s.canonicalKey))
                            .put("reason", s.reason)
                            .put("canonicalKey", s.canonicalKey)
                    )
                }
            }.toString()
        }.getOrDefault("[]")

        val movementsJson = runCatching {
            JSONArray().apply {
                posted.sortedByDescending { it.occurredAt }
                    .take(MAX_MOVEMENTS)
                    .forEach { e ->
                        put(
                            JSONObject()
                                .put("concept", e.concept)
                                .put("amount", e.amountMxn)
                                .put("occurredAt", e.occurredAt)
                        )
                    }
            }.toString()
        }.getOrDefault("[]")

        val pendingJson = runCatching {
            val pending = app.database.pendingCaptureDao().observePending().first()
            JSONArray().apply {
                pending.take(MAX_PENDING).forEach { p ->
                    put(
                        JSONObject()
                            .put("id", p.id)
                            .put("concept", p.concept)
                            .put("amount", p.amountMxn)
                            .put("occurredAt", p.occurredAt)
                            .put("source", p.source)
                    )
                }
            }.toString()
        }.getOrDefault("[]")

        val memberSpendJson = runCatching {
            if (quincena == null) "[]" else {
                val rows = app.database.expenseAttributionDao()
                    .observeSpendByMember(quincena.id, "BENEFICIARY").first()
                JSONArray().apply {
                    rows.forEach { r ->
                        put(
                            JSONObject()
                                .put("name", r.memberName)
                                .put("total", r.totalMxn)
                        )
                    }
                }.toString()
            }
        }.getOrDefault("[]")

        runCatching {
            val req = PutDataMapRequest.create(WearPaths.PATH_BUDGET_SYNC)
            req.dataMap.apply {
                putDouble(WearPaths.KEY_BALANCE_DISPONIBLE, balance)
                putString(WearPaths.KEY_QUINCENA_LABEL, label)
                putLong(WearPaths.KEY_TIMESTAMP, System.currentTimeMillis())
                putString(WearPaths.KEY_SUGGESTIONS_JSON, suggestionsJson)
                putString(WearPaths.KEY_MOVEMENTS_JSON, movementsJson)
                putString(WearPaths.KEY_PENDING_JSON, pendingJson)
                putString(WearPaths.KEY_MEMBER_SPEND_JSON, memberSpendJson)
            }
            Wearable.getDataClient(context).putDataItem(req.asPutDataRequest().setUrgent())
        }
    }

    /**
     * Monto "típico" de un cluster canónico: la mediana de los montos POSTED que
     * comparten esa clave. Mediana (no media) para no dejar que un outlier
     * arrastre la sugerencia. 0.0 si no hay historia (el reloj ocultará el monto).
     */
    private fun typicalAmount(posted: List<ExpenseEntity>, canonicalKey: String): Double {
        val amounts = posted
            .filter { it.conceptCanonical == canonicalKey }
            .map { it.amountMxn }
            .sorted()
        if (amounts.isEmpty()) return 0.0
        val mid = amounts.size / 2
        return if (amounts.size % 2 == 1) amounts[mid]
        else (amounts[mid - 1] + amounts[mid]) / 2.0
    }
}
