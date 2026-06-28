package mx.budget.ai.proactive

import mx.budget.data.local.dao.ExpenseAttributionDao

/**
 * Sugerencia de atribución inferida del patrón histórico (Apéndice F.3.5).
 *
 * @param role         "BENEFICIARY" o "PAYER".
 * @param distribution memberId → shareBps; suma exactamente 10,000.
 * @param confidence   [0.0, 1.0]: acuerdo histórico penalizado por muestra pequeña.
 * @param sampleSize   nº de gastos históricos que respaldan la sugerencia.
 * @param basis        texto explicable para la UI.
 */
data class AttributionSuggestion(
    val role: String,
    val distribution: Map<String, Int>,
    val confidence: Double,
    val sampleSize: Int,
    val basis: String
)

/**
 * Motor reutilizable de inferencia de atribución. Lo consume el pipeline
 * retroactivo (Feature B) y, más adelante, la captura en vivo (Feature A) y la
 * captura desde notificaciones (Feature D).
 *
 * Dada una clave canónica y un rol, reconstruye la distribución de cada gasto
 * histórico de esa clave, identifica la **composición modal** (qué miembros
 * participan con más frecuencia) y devuelve su distribución promedio con un
 * score de confianza.
 */
class RetroAttributionEngine(
    private val attributionDao: ExpenseAttributionDao,
    private val canonicalizer: ConceptCanonicalizer
) {

    /**
     * Sugerencia a partir de una clave canónica ya almacenada (`expense.concept_canonical`).
     * Es la vía correcta para el pipeline retroactivo: usa la clave del cluster,
     * no una re-derivada del texto crudo.
     */
    suspend fun suggestForKey(canonicalKey: String, role: String): AttributionSuggestion? {
        val rows = attributionDao.findHistoricalByCanonical(canonicalKey, role)
        if (rows.isEmpty()) return null

        // Reconstruir la distribución por gasto.
        val byExpense = rows.groupBy { it.expenseId }
        val n = byExpense.size

        // Agrupar por composición (conjunto ordenado de miembros que participan).
        val byComposition = byExpense.values.groupBy { dist ->
            dist.map { it.memberId }.sorted().joinToString("|")
        }
        val modal = byComposition.maxByOrNull { it.value.size } ?: return null
        val agreement = modal.value.size.toDouble() / n
        val confidence = agreement * minOf(1.0, n / SAMPLE_FLOOR)

        // Promediar los bps de cada miembro dentro de la composición modal.
        val sums = HashMap<String, Long>()
        modal.value.forEach { dist ->
            dist.forEach { row -> sums[row.memberId] = (sums[row.memberId] ?: 0L) + row.shareBps }
        }
        val count = modal.value.size
        val avg = sums.mapValues { (it.value / count).toInt() }
        val distribution = normalizeTo10000(avg)

        return AttributionSuggestion(
            role = role,
            distribution = distribution,
            confidence = confidence,
            sampleSize = n,
            basis = "Basado en $n gastos similares"
        )
    }

    /**
     * Sugerencia a partir de texto libre (canoniza primero). Pensada para captura
     * en vivo (Feature A). Aproximación: la clave derivada puede no coincidir con
     * el representante de cluster almacenado; aceptable para sugerencia en vivo.
     */
    suspend fun suggest(concept: String, role: String): AttributionSuggestion? {
        val key = canonicalizer.canonicalize(concept) ?: return null
        return suggestForKey(key, role)
    }

    /** Reescala una distribución para que sume exactamente 10,000 bps (el último absorbe el resto). */
    private fun normalizeTo10000(raw: Map<String, Int>): Map<String, Int> {
        if (raw.isEmpty()) return emptyMap()
        val total = raw.values.sum().takeIf { it > 0 } ?: return equalSplit(raw.keys)
        val entries = raw.entries.toList()
        var assigned = 0
        return entries.mapIndexed { i, (memberId, bps) ->
            val scaled = if (i == entries.lastIndex) 10_000 - assigned
            else ((bps.toLong() * 10_000) / total).toInt().also { assigned += it }
            memberId to scaled
        }.toMap()
    }

    private fun equalSplit(ids: Set<String>): Map<String, Int> {
        val list = ids.toList()
        if (list.isEmpty()) return emptyMap()
        val base = 10_000 / list.size
        val remainder = 10_000 - base * list.size
        return list.mapIndexed { i, id ->
            id to if (i == list.lastIndex) base + remainder else base
        }.toMap()
    }

    companion object {
        /** Piso de muestra: por debajo de esto la confianza se penaliza linealmente. */
        const val SAMPLE_FLOOR = 5.0

        /** Umbral de auto-aplicación: ≥ τ se aplica solo; < τ va a revisión. */
        const val AUTO_APPLY_THRESHOLD = 0.7
    }
}
