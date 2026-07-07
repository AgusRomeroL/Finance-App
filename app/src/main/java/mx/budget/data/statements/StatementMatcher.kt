package mx.budget.data.statements

import mx.budget.core.jaroWinkler
import mx.budget.core.unaccent
import mx.budget.data.local.entity.ExpenseEntity
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * Matching determinista de movimientos del estado de cuenta contra gastos
 * existentes (Fase 5). **El LLM propone, esta clase dispone**: cualquier vínculo
 * — local o sugerido por NIM — pasa por [validate] antes de aceptarse.
 *
 * Score por par (movimiento, gasto), máximo 1.0:
 * - **Monto** (criterio bloqueante): exacto ±$0.005 → 0.55; ±1 % → 0.45;
 *   ±10 % → 0.15; más lejos → el par se descarta (score 0).
 * - **Fecha**: mismo día → 0.25; ±1 día → 0.20; ±3 → 0.12; ±7 → 0.05;
 *   sin fecha en el movimiento → 0.15 neutral; >14 días → par descartado.
 * - **Concepto**: Jaro-Winkler entre descripciones canonicalizadas × 0.20.
 *
 * Umbrales: ≥ [AUTO_THRESHOLD] = vínculo automático; ≥ [SUGGEST_THRESHOLD] =
 * sugerido (el usuario confirma); debajo = movimiento NUEVO.
 *
 * La asignación es **greedy por score descendente con unicidad**: un gasto no
 * puede quedar vinculado a dos movimientos del mismo import.
 */
class StatementMatcher(
    private val zone: ZoneId = ZoneId.of("America/Mexico_City"),
) {

    /** Resultado por movimiento (mismo índice que `statement.movimientos`). */
    data class MovementMatch(
        val movementIndex: Int,
        val expenseId: String?,
        val confidence: Double,
        /** true si superó [AUTO_THRESHOLD] (vínculo directo, no solo sugerencia). */
        val auto: Boolean,
        val source: String?,
    )

    /**
     * Huella determinista de la línea: fecha|monto(2dec)|descripción canónica.
     * Con el índice UNIQUE (wallet, fingerprint), re-importar el mismo PDF es
     * un no-op fila a fila.
     */
    fun fingerprint(mov: StatementMovement): String {
        val base = listOf(
            mov.fecha.orEmpty(),
            String.format(java.util.Locale.US, "%.2f", mov.monto ?: 0.0),
            canonicalize(mov.concepto.orEmpty()),
        ).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(base.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }

    /** Canonicalización ligera (lower + unaccent + tokens ordenados), suficiente
     *  para descripciones bancarias — sin expansión de miembros (no aparecen). */
    fun canonicalize(text: String): String =
        text.lowercase()
            .unaccent()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .joinToString("|")

    /**
     * Matching greedy de todos los movimientos contra los candidatos.
     * @param excludeExpenseIds gastos ya vinculados en imports previos del wallet.
     */
    fun match(
        movements: List<StatementMovement>,
        candidates: List<ExpenseEntity>,
        excludeExpenseIds: Set<String> = emptySet(),
    ): List<MovementMatch> {
        val available = candidates.filter { it.id !in excludeExpenseIds }
        data class Pair_(val movIdx: Int, val expense: ExpenseEntity, val score: Double)

        val pairs = buildList {
            movements.forEachIndexed { i, mov ->
                available.forEach { exp ->
                    val s = score(mov, exp)
                    if (s > 0.0) add(Pair_(i, exp, s))
                }
            }
        }.sortedByDescending { it.score }

        val takenMovs = HashSet<Int>()
        val takenExps = HashSet<String>()
        val byMov = HashMap<Int, Pair_>()
        for (p in pairs) {
            if (p.movIdx in takenMovs || p.expense.id in takenExps) continue
            if (p.score < SUGGEST_THRESHOLD) break
            takenMovs += p.movIdx
            takenExps += p.expense.id
            byMov[p.movIdx] = p
        }

        return movements.indices.map { i ->
            val hit = byMov[i]
            if (hit == null) MovementMatch(i, null, 0.0, auto = false, source = null)
            else MovementMatch(i, hit.expense.id, hit.score, auto = hit.score >= AUTO_THRESHOLD, source = "LOCAL")
        }
    }

    /**
     * Regla dura para aceptar un vínculo propuesto (por NIM o por el usuario
     * asistido): monto dentro de ±1 % Y fecha dentro de ±3 días (o sin fecha).
     */
    fun validate(mov: StatementMovement, expense: ExpenseEntity): Boolean {
        val monto = mov.monto ?: return false
        val rel = abs(monto - expense.amountMxn) / maxOf(expense.amountMxn, 0.01)
        if (rel > 0.01 && abs(monto - expense.amountMxn) > 0.005) return false
        val days = daysBetween(mov.fecha, expense.occurredAt) ?: return true
        return days <= 3
    }

    /** Score de un par; 0.0 = descartado. */
    fun score(mov: StatementMovement, expense: ExpenseEntity): Double {
        val monto = mov.monto ?: return 0.0
        if (monto <= 0.0) return 0.0

        val absDiff = abs(monto - expense.amountMxn)
        val rel = absDiff / maxOf(expense.amountMxn, 0.01)
        val amountScore = when {
            absDiff <= 0.005 -> 0.55
            rel <= 0.01 -> 0.45
            rel <= 0.10 -> 0.15
            else -> return 0.0
        }

        val days = daysBetween(mov.fecha, expense.occurredAt)
        val dateScore = when {
            days == null -> 0.15
            days == 0L -> 0.25
            days == 1L -> 0.20
            days <= 3L -> 0.12
            days <= 7L -> 0.05
            days <= 14L -> 0.0
            else -> return 0.0
        }

        val canonMov = canonicalize(mov.concepto.orEmpty())
        val canonExp = expense.conceptCanonical ?: canonicalize(expense.concept)
        val conceptScore = if (canonMov.isBlank() || canonExp.isBlank()) 0.0
        else jaroWinkler(canonMov, canonExp) * 0.20

        return amountScore + dateScore + conceptScore
    }

    /** Días absolutos entre la fecha ISO del movimiento y el epoch del gasto. */
    private fun daysBetween(iso: String?, epochMillis: Long): Long? {
        if (iso.isNullOrBlank()) return null
        val movDate = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return null
        val expDate = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
        return abs(java.time.temporal.ChronoUnit.DAYS.between(movDate, expDate))
    }

    companion object {
        const val AUTO_THRESHOLD = 0.90
        const val SUGGEST_THRESHOLD = 0.60
    }
}
