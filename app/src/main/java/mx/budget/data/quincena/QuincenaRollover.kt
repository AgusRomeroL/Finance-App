package mx.budget.data.quincena

import android.util.Log
import mx.budget.data.local.dao.QuincenaDao
import mx.budget.data.local.entity.QuincenaEntity
import java.time.LocalDate
import java.time.ZoneId

/**
 * Rollover automático de quincena (MVP). La semilla del Excel termina en
 * jun-2026: a partir de ahí NADIE creaba la siguiente quincena y la app
 * quedaba "SIN QUINCENA ACTIVA" (captura y dashboard inservibles). Este
 * componente garantiza al arrancar que exista una quincena ACTIVE que cubra
 * HOY, respetando el DFA (una sola ACTIVE por household):
 *
 * 1. Si la ACTIVE actual cubre hoy → no-op.
 * 2. Si la ACTIVE venció (end_date < hoy) → CLOSED con `closed_at` (los
 *    totales actual_* ya viven en sus columnas — snapshot implícito).
 * 3. Busca la quincena que cubre hoy (puede existir PROVISIONED del seed);
 *    si no existe la crea con **id determinista** `q-YYYY-MM-HALF`: ambos
 *    teléfonos generan LA MISMA quincena y el pull multi-dispositivo nunca
 *    rompe la FK `expense.quincena_id`.
 * 4. La activa.
 */
class QuincenaRollover(
    private val dao: QuincenaDao,
    private val householdId: String,
    private val zone: ZoneId = ZoneId.of("America/Mexico_City"),
) {

    private val monthNames = listOf(
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre",
    )

    /** Garantiza la quincena ACTIVE de hoy; devuelve la activa resultante. */
    suspend fun ensureActiveForToday(): QuincenaEntity? {
        val today = LocalDate.now(zone)
        val iso = today.toString()

        val active = dao.getActive(householdId)
        if (active != null && active.startDate <= iso && active.endDate >= iso) return active

        if (active != null) {
            Log.i(TAG, "Cerrando quincena vencida ${active.label} (fin ${active.endDate})")
            dao.update(active.copy(status = "CLOSED", closedAt = System.currentTimeMillis()))
        }

        val existing = dao.getForDate(householdId, iso)
        val target = existing ?: buildQuincena(today).also { dao.insert(it) }

        dao.updateStatus(target.id, "ACTIVE")
        Log.i(TAG, "Quincena activa: ${target.label} (${target.id})")
        return dao.getById(target.id)
    }

    /**
     * Garantiza que exista una quincena (cualquier status) que cubra [date];
     * si falta la crea PROVISIONED con id determinista. NO toca la ACTIVE.
     * La usa el pago manual one-off: un PLANNED con fecha fuera de las
     * quincenas existentes se asignaba a la ACTIVE y contaminaba sus
     * agregados (P1 de auditoría runtime).
     */
    suspend fun ensureForDate(date: LocalDate): QuincenaEntity {
        val existing = dao.getForDate(householdId, date.toString())
        if (existing != null) return existing
        val q = buildQuincena(date)
        dao.insert(q)
        Log.i(TAG, "Quincena aprovisionada para $date: ${q.label} (${q.id})")
        return q
    }

    private fun buildQuincena(today: LocalDate): QuincenaEntity {
        val year = today.year
        val month = today.monthValue
        val first = today.dayOfMonth <= 15
        val half = if (first) "FIRST" else "SECOND"
        val start = LocalDate.of(year, month, if (first) 1 else 16)
        val end = if (first) LocalDate.of(year, month, 15) else start.withDayOfMonth(start.lengthOfMonth())
        return QuincenaEntity(
            // Id DETERMINISTA (no UUID): convergente entre dispositivos.
            id = "q-%04d-%02d-%s".format(year, month, half),
            householdId = householdId,
            year = year,
            month = month,
            half = half,
            startDate = start.toString(),
            endDate = end.toString(),
            label = "${if (first) "Q1" else "Q2"} ${monthNames[month - 1]} $year",
            status = "PROVISIONED",
        )
    }

    private companion object {
        const val TAG = "QuincenaRollover"
    }
}
