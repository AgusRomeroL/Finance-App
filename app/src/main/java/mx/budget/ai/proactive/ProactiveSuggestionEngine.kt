package mx.budget.ai.proactive

import mx.budget.data.local.entity.ExpenseEntity
import mx.budget.data.local.entity.QuincenaEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Sugerencia proactiva mostrada al abrir la app (Apéndice F.5, Feature C).
 *
 * @param canonicalKey clave canónica del cluster sugerido (para descartar/deduplicar).
 * @param concept      representante legible del cluster (prefill del concepto en captura).
 * @param categoryId   categoría más frecuente del cluster (prefill de categoría).
 * @param reason       texto explicable y no técnico ("Sueles registrarlo los sábados…").
 * @param basis        nº de gastos históricos que respaldan la sugerencia.
 */
data class ProactiveSuggestion(
    val canonicalKey: String,
    val concept: String,
    val categoryId: String,
    val reason: String,
    val basis: Int,
)

/**
 * Motor determinista (100% SQL/Kotlin, sin ML) que predice **qué gasto se va a
 * querer registrar ahora** a partir de tres señales públicas (§F.5.1): hora del
 * día (franja), día de la semana y **día de quincena** (la señal diferenciadora
 * de esta app). Opera sobre el historial ya canonicalizado por Feature B.
 *
 * Con ~800 registros, recorrer la lista en memoria al abrir el dashboard es
 * trivial; por eso NO hay tabla `proactive_suggestion` ni `PeriodicWorker` (el
 * pre-cómputo es opcional en la spec). El cálculo es puro y testeable: recibe el
 * historial, la quincena activa y `nowEpochMs`, y devuelve a lo más una sugerencia.
 *
 * Dos caminos, en orden de prioridad:
 *  1. **Inicio de quincena** (días ~1-2 / 16-17): prioriza los gastos grandes
 *     recurrentes del periodo (renta, servicios, colegiaturas) aún no registrados
 *     en la quincena activa → "¿ya registraste este gasto del periodo?".
 *  2. **Patrón hora+día**: el gasto que el hogar suele registrar este mismo día de
 *     la semana y franja horaria, excluyendo lo ya registrado hoy.
 */
class ProactiveSuggestionEngine(
    private val zone: ZoneId = ZoneId.of("America/Mexico_City")
) {

    /** Compatibilidad: la mejor sugerencia (la primera de [suggestMany]). */
    fun suggest(
        history: List<ExpenseEntity>,
        activeQuincena: QuincenaEntity?,
        nowEpochMs: Long,
    ): ProactiveSuggestion? = suggestMany(history, activeQuincena, nowEpochMs, limit = 1).firstOrNull()

    /**
     * Hasta [limit] sugerencias, en orden de prioridad: primero los gastos de
     * arranque de quincena (por monto desc), luego los patrones hora+día (por
     * frecuencia y recencia). Dedupe por clave canónica (el camino 1 gana).
     */
    fun suggestMany(
        history: List<ExpenseEntity>,
        activeQuincena: QuincenaEntity?,
        nowEpochMs: Long,
        limit: Int,
    ): List<ProactiveSuggestion> {
        val posted = history.filter { it.status == "POSTED" && !it.conceptCanonical.isNullOrBlank() }
        if (posted.isEmpty() || limit <= 0) return emptyList()

        val nowZdt = Instant.ofEpochMilli(nowEpochMs).atZone(zone)
        val today: LocalDate = nowZdt.toLocalDate()
        val nowDow: DayOfWeek = nowZdt.dayOfWeek
        val nowHour: Int = nowZdt.hour

        fun dayOf(e: ExpenseEntity): LocalDate =
            Instant.ofEpochMilli(e.occurredAt).atZone(zone).toLocalDate()

        // Dedupe por clave canónica preservando la prioridad (camino 1 antes que 2).
        val out = LinkedHashMap<String, ProactiveSuggestion>()

        // ── Camino 1: inicio de quincena (señal diferenciadora) ────────────────
        if (activeQuincena != null && isNearQuincenaStart(activeQuincena, today)) {
            val alreadyThisQuincena = posted
                .filter { it.quincenaId == activeQuincena.id }
                .mapNotNull { it.conceptCanonical }
                .toSet()

            posted
                .filter { isQuincenaStartDay(dayOf(it).dayOfMonth) }
                .groupBy { it.conceptCanonical!! }
                .filter { (key, rows) -> rows.size >= MIN_RECURRENCE && key !in alreadyThisQuincena }
                .entries
                .sortedByDescending { (_, rows) -> rows.sumOf { it.amountMxn } }
                .forEach { (key, rows) ->
                    out.getOrPut(key) { build(key, rows, "Suele tocar al inicio de la quincena") }
                }
        }

        // ── Camino 2: patrón por día de la semana + franja horaria ─────────────
        val registeredToday = posted
            .filter { dayOf(it) == today }
            .mapNotNull { it.conceptCanonical }
            .toSet()

        posted
            .filter {
                val z = Instant.ofEpochMilli(it.occurredAt).atZone(zone)
                z.dayOfWeek == nowDow && abs(z.hour - nowHour) <= HOUR_BAND &&
                    it.conceptCanonical !in registeredToday
            }
            .groupBy { it.conceptCanonical!! }
            .filter { it.value.size >= MIN_OCCURRENCES }
            .entries
            .sortedByDescending { (_, rows) -> rows.size * RECENCY_SCALE + rows.maxOf { it.occurredAt } }
            .forEach { (key, rows) ->
                out.getOrPut(key) {
                    build(key, rows, "Sueles registrarlo ${dayPhrase(nowDow)} ${bandPhrase(nowHour)}")
                }
            }

        return out.values.take(limit)
    }

    /** Representante legible + categoría modal del cluster. */
    private fun build(key: String, rows: List<ExpenseEntity>, reason: String): ProactiveSuggestion {
        val concept = rows.groupingBy { it.concept }.eachCount().maxByOrNull { it.value }!!.key
        val categoryId = rows.groupingBy { it.categoryId }.eachCount().maxByOrNull { it.value }!!.key
        return ProactiveSuggestion(
            canonicalKey = key,
            concept = concept,
            categoryId = categoryId,
            reason = reason,
            basis = rows.size,
        )
    }

    /** Los primeros ~2 días del periodo (la quincena acaba de empezar). */
    private fun isNearQuincenaStart(quincena: QuincenaEntity, today: LocalDate): Boolean {
        val start = runCatching { LocalDate.parse(quincena.startDate) }.getOrNull() ?: return false
        val elapsed = ChronoUnit.DAYS.between(start, today)
        return elapsed in 0..1
    }

    /** Día del mes típico de un gasto "de arranque de quincena" (1ª o 2ª mitad). */
    private fun isQuincenaStartDay(dayOfMonth: Int): Boolean =
        dayOfMonth in 1..3 || dayOfMonth in 16..18

    private fun dayPhrase(dow: DayOfWeek): String = when (dow) {
        DayOfWeek.MONDAY -> "los lunes"
        DayOfWeek.TUESDAY -> "los martes"
        DayOfWeek.WEDNESDAY -> "los miércoles"
        DayOfWeek.THURSDAY -> "los jueves"
        DayOfWeek.FRIDAY -> "los viernes"
        DayOfWeek.SATURDAY -> "los sábados"
        DayOfWeek.SUNDAY -> "los domingos"
    }

    private fun bandPhrase(hour: Int): String = when (hour) {
        in 5..11 -> "por la mañana"
        in 12..18 -> "por la tarde"
        else -> "por la noche"
    }

    companion object {
        /** Mínimo de ocurrencias hora+día para sugerir (evita ruido de un solo gasto). */
        const val MIN_OCCURRENCES = 2

        /** Mínimo de apariciones para considerar un gasto de arranque "recurrente". */
        const val MIN_RECURRENCE = 2

        /** Ventana horaria ±N horas alrededor de la hora actual. */
        const val HOUR_BAND = 2

        /** Escala para que la frecuencia domine sobre la recencia (epoch millis) en el ranking. */
        const val RECENCY_SCALE = 100_000_000_000_000L
    }
}
