package mx.budget.data.quincena

import android.util.Log
import mx.budget.data.local.dao.QuincenaDao
import mx.budget.data.local.entity.QuincenaEntity
import java.time.LocalDate
import java.time.ZoneId

/**
 * Rollover automatico de quincena (MVP). La semilla del Excel termina en
 * jun-2026: a partir de ahi NADIE creaba la siguiente quincena y la app
 * quedaba "SIN QUINCENA ACTIVA" (captura y dashboard inservibles). Este
 * componente garantiza al arrancar que exista una quincena ACTIVE que cubra
 * HOY, respetando el DFA (una sola ACTIVE por household):
 *
 * 1. Si la ACTIVE actual cubre hoy, no hace nada.
 * 2. Si la ACTIVE vencio (end_date anterior a hoy) la cierra con `closed_at`
 *    (los totales actual_* ya viven en sus columnas, snapshot implicito).
 * 3. **Rellena las quincenas intermedias ausentes.** Antes solo se creaba la de
 *    hoy, asi que tras semanas sin abrir la app el historial saltaba de julio a
 *    septiembre: los periodos de en medio no existian, nada podia colgar de
 *    ellos y el libro mayor no podia navegarlos. Se crean CLOSED y sin
 *    materializar recurrencias, para no fabricar cargos planeados retroactivos
 *    que nadie va a pagar.
 * 4. Busca la quincena que cubre hoy (puede existir PROVISIONED del seed); si no
 *    existe la crea con **id determinista** `q-YYYY-MM-HALF`: ambos telefonos
 *    generan LA MISMA quincena y el pull multi-dispositivo nunca rompe la FK
 *    `expense.quincena_id`.
 * 5. La activa, sellando `updated_at` para que el LWW del sync vea el cambio.
 *    Antes la activacion usaba `updateStatus`, que no toca esa marca, asi que el
 *    cambio de estado viajaba invisible.
 *
 * Toda quincena creada aqui **arrastra el presupuesto** de la ultima que lo
 * tenia declarado. Sin eso nacia con proyectado en cero y el disponible del
 * dashboard salia en negativo desde el dia 1, aunque el hogar no hubiera
 * gastado nada.
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
        val now = System.currentTimeMillis()

        val active = dao.getActive(householdId)
        if (active != null && active.startDate <= iso && active.endDate >= iso) return active

        if (active != null) {
            Log.i(TAG, "Cerrando quincena vencida ${active.label} (fin ${active.endDate})")
            dao.update(active.copy(status = "CLOSED", closedAt = now, updatedAt = now))
            backfillBetween(active.endDate, today, now)
        }

        val existing = dao.getForDate(householdId, iso)
        val target = existing ?: withCarriedBudget(buildQuincena(today)).also { dao.insert(it) }

        dao.update(target.copy(status = "ACTIVE", updatedAt = now))
        Log.i(TAG, "Quincena activa: ${target.label} (${target.id})")
        return dao.getById(target.id)
    }

    /**
     * Crea, como CLOSED, las quincenas que faltan entre la que acaba de cerrarse
     * y la de hoy.
     *
     * El tope de [MAX_BACKFILL] periodos evita que una fecha corrupta en
     * `end_date` dispare un bucle largo: son dos anios de quincenas.
     */
    private suspend fun backfillBetween(closedEndDate: String, today: LocalDate, now: Long) {
        val from = runCatching { LocalDate.parse(closedEndDate) }.getOrNull() ?: return
        val todayHalfStart = halfStart(today)
        var cursor = nextHalfStart(halfStart(from))
        var created = 0
        var guard = 0
        while (cursor.isBefore(todayHalfStart) && guard < MAX_BACKFILL) {
            guard++
            if (dao.getForDate(householdId, cursor.toString()) == null) {
                val hueco = withCarriedBudget(buildQuincena(cursor))
                    .copy(status = "CLOSED", closedAt = now, updatedAt = now)
                dao.insert(hueco)
                created++
            }
            cursor = nextHalfStart(cursor)
        }
        if (created > 0) {
            Log.i(TAG, "Quincenas intermedias creadas: $created (desde $closedEndDate)")
        }
    }

    /**
     * Garantiza que exista una quincena (cualquier status) que cubra [date];
     * si falta la crea PROVISIONED con id determinista. NO toca la ACTIVE.
     * La usa el pago manual one-off: un PLANNED con fecha fuera de las
     * quincenas existentes se asignaba a la ACTIVE y contaminaba sus
     * agregados (P1 de auditoria runtime).
     */
    suspend fun ensureForDate(date: LocalDate): QuincenaEntity {
        val existing = dao.getForDate(householdId, date.toString())
        if (existing != null) return existing
        val q = withCarriedBudget(buildQuincena(date))
        dao.insert(q)
        Log.i(TAG, "Quincena aprovisionada para $date: ${q.label} (${q.id})")
        return q
    }

    /** Copia el presupuesto de la ultima quincena que lo tenia declarado. */
    private suspend fun withCarriedBudget(q: QuincenaEntity): QuincenaEntity {
        val previa = dao.getLatestWithBudget(householdId, q.startDate) ?: return q
        return q.copy(
            projectedIncomeMxn = previa.projectedIncomeMxn,
            projectedExpensesMxn = previa.projectedExpensesMxn,
        )
    }

    /** Primer dia de la mitad de mes a la que pertenece [date]. */
    private fun halfStart(date: LocalDate): LocalDate =
        if (date.dayOfMonth <= 15) date.withDayOfMonth(1) else date.withDayOfMonth(16)

    /** Primer dia de la mitad siguiente a la que empieza en [start]. */
    private fun nextHalfStart(start: LocalDate): LocalDate =
        if (start.dayOfMonth == 1) start.withDayOfMonth(16)
        else start.plusMonths(1).withDayOfMonth(1)

    /**
     * Builder determinista de quincena: id `q-YYYY-MM-HALF`, status PROVISIONED.
     * Es `internal` para que la captura lo reuse en vez de replicarlo.
     */
    internal fun buildQuincena(date: LocalDate): QuincenaEntity {
        val year = date.year
        val month = date.monthValue
        val first = date.dayOfMonth <= 15
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

        /** Tope de quincenas a rellenar de una vez: dos anios. */
        const val MAX_BACKFILL = 48
    }
}
