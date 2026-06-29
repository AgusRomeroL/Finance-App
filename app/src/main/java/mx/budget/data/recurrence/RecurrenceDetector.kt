package mx.budget.data.recurrence

import mx.budget.data.local.entity.ExpenseEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Plantilla recurrente **sugerida** (no creada) a partir del historial — la
 * propuesta de la inferencia bajo autorización (Apéndice G.2, Fase 5).
 *
 * Lleva todo lo necesario para construir el `recurrence_template` SI el usuario
 * acepta (propose-then-confirm); el [RecurrenceViewModel] completa los splits con
 * el [mx.budget.ai.proactive.RetroAttributionEngine] al aceptar.
 */
data class RecurrenceSuggestion(
    val canonicalKey: String,
    val concept: String,
    val categoryId: String,
    val paymentMethodId: String?,
    val amountMxn: Double,
    val cadence: String,
    val dayOfMonth: Int,
    val occurrences: Int,
    val confidence: Double,
    val learnedFromExpenseIds: List<String>,
    val reason: String,
)

/**
 * Detector determinista de recurrencia (Apéndice G.2, Fase 5). Agrupa el historial
 * POSTED por clave canónica (la que dejó Feature B) y, para cada grupo, mide la
 * regularidad temporal y de monto. Devuelve las plantillas **candidatas** que
 * superan el umbral de confianza, ordenadas por confianza.
 *
 * Confianza (doc de `RecurrenceTemplateEntity`):
 *   `0.4·sigmoid(n-3) + 0.3·(1-interval_cv) + 0.3·(1-amount_cv)`
 * donde `cv` = desviación estándar / media (coeficiente de variación, acotado a [0,1]).
 *
 * No crea nada: solo propone. El consumidor filtra lo ya plantillado y lo descartado.
 */
class RecurrenceDetector(
    private val zone: ZoneId = ZoneId.of("America/Mexico_City"),
) {

    fun detect(history: List<ExpenseEntity>): List<RecurrenceSuggestion> {
        val posted = history.filter { it.status == "POSTED" && !it.conceptCanonical.isNullOrBlank() }
        return posted.groupBy { it.conceptCanonical!! }
            .mapNotNull { (key, rows) -> analyze(key, rows) }
            .filter { it.confidence >= CONFIDENCE_THRESHOLD }
            .sortedByDescending { it.confidence }
    }

    private fun analyze(key: String, rows: List<ExpenseEntity>): RecurrenceSuggestion? {
        if (rows.size < MIN_OCCURRENCES) return null

        // Fechas distintas, ordenadas (un mismo día con dos cargos no infla la cadencia).
        val dates = rows.map { dayOf(it) }.distinct().sorted()
        if (dates.size < MIN_OCCURRENCES) return null

        val gaps = dates.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b).toDouble() }
        val intervalMean = gaps.average()
        if (intervalMean <= 0) return null
        val intervalCv = (std(gaps) / intervalMean).coerceIn(0.0, 1.0)

        val amounts = rows.map { it.amountMxn }
        val amountMean = amounts.average()
        val amountCv = if (amountMean > 0) (std(amounts) / amountMean).coerceIn(0.0, 1.0) else 1.0

        val n = dates.size
        val confidence = 0.4 * sigmoid(n - 3.0) + 0.3 * (1 - intervalCv) + 0.3 * (1 - amountCv)

        val (cadence, dayOfMonth) = inferCadence(intervalMean, dates) ?: return null

        val concept = rows.groupingBy { it.concept }.eachCount().maxByOrNull { it.value }!!.key
        val categoryId = rows.groupingBy { it.categoryId }.eachCount().maxByOrNull { it.value }!!.key
        val paymentMethodId = rows.groupingBy { it.paymentMethodId }.eachCount().maxByOrNull { it.value }?.key
        val median = amounts.sorted().let { it[it.size / 2] }

        return RecurrenceSuggestion(
            canonicalKey = key,
            concept = concept,
            categoryId = categoryId,
            paymentMethodId = paymentMethodId,
            amountMxn = median,
            cadence = cadence,
            dayOfMonth = dayOfMonth,
            occurrences = n,
            confidence = confidence,
            learnedFromExpenseIds = rows.map { it.id }.take(MAX_LEARNED),
            reason = reasonText(median, cadence, n),
        )
    }

    /** Cadencia + día del mes representativo, o null si el intervalo no encaja. */
    private fun inferCadence(intervalMean: Double, dates: List<LocalDate>): Pair<String, Int>? {
        val day = dates.groupingBy { it.dayOfMonth }.eachCount().maxByOrNull { it.value }!!.key
        return when {
            intervalMean in 11.0..18.0 -> {
                val first = dates.count { it.dayOfMonth <= 15 }
                val second = dates.size - first
                val cadence = when {
                    first > 0 && second > 0 && minOf(first, second).toDouble() / dates.size >= 0.3 -> "QUINCENAL_EVERY"
                    first >= second -> "QUINCENAL_FIRST"
                    else -> "QUINCENAL_SECOND"
                }
                cadence to day
            }
            intervalMean in 25.0..38.0 -> "MONTHLY_SPECIFIC_HALF" to day
            intervalMean in 50.0..70.0 -> "BIMONTHLY" to day
            else -> null
        }
    }

    private fun reasonText(amount: Double, cadence: String, n: Int): String {
        val money = "$" + java.text.NumberFormat.getIntegerInstance(java.util.Locale("es", "MX")).format(amount.toLong())
        val cad = when (cadence) {
            "QUINCENAL_FIRST", "QUINCENAL_SECOND" -> "cada quincena"
            "QUINCENAL_EVERY" -> "dos veces al mes"
            "MONTHLY_SPECIFIC_HALF" -> "cada mes"
            "BIMONTHLY" -> "cada dos meses"
            else -> "con regularidad"
        }
        return "≈$money · $cad · $n registros"
    }

    private fun dayOf(e: ExpenseEntity): LocalDate =
        Instant.ofEpochMilli(e.occurredAt).atZone(zone).toLocalDate()

    private fun std(xs: List<Double>): Double {
        if (xs.size < 2) return 0.0
        val mean = xs.average()
        return sqrt(xs.sumOf { (it - mean) * (it - mean) } / xs.size)
    }

    private fun sigmoid(x: Double): Double = 1.0 / (1.0 + exp(-x))

    companion object {
        const val MIN_OCCURRENCES = 3
        const val CONFIDENCE_THRESHOLD = 0.55
        const val MAX_LEARNED = 20
    }
}
