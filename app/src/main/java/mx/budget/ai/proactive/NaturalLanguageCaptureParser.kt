package mx.budget.ai.proactive

import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Movimiento propuesto extraído de una frase en lenguaje natural (Apéndice G.3).
 *
 * @param amountMxn   monto positivo en MXN.
 * @param concept     comercio/descripción breve ("gasolina", "café").
 * @param occurredAt  epoch millis (hoy por default; "ayer"/"antier" lo retroceden).
 */
data class ParsedNlCapture(
    val amountMxn: Double,
    val concept: String,
    val occurredAt: Long,
)

/**
 * Parser determinista (100% on-device, sin red ni ML) de captura en lenguaje
 * natural (Apéndice G.3). Es el espejo de [BankNotificationParser] pero para
 * texto dictado/escrito por el usuario: "gasté 200 en gasolina", "café 60",
 * "ayer 500 de despensa".
 *
 * Es el camino **garantizado** del pipeline NL: [NlCaptureExtractor] intenta
 * primero el LLM opcional y cae aquí siempre que no esté disponible o falle.
 * Devuelve `null` si no logra extraer un monto > 0 (sin monto no hay gasto que
 * proponer). Como rige propose-then-confirm, el concepto no necesita ser
 * perfecto: el usuario lo ajusta al confirmar.
 */
class NaturalLanguageCaptureParser {

    fun parse(rawText: String, nowEpochMs: Long): ParsedNlCapture? {
        val text = rawText.trim()
        if (text.isBlank()) return null

        val match = AMOUNT.find(text) ?: return null
        val amount = normalizeAmount(match.groupValues[1]) ?: return null
        if (amount <= 0.0) return null

        return ParsedNlCapture(
            amountMxn = amount,
            concept = extractConcept(text, match.range),
            occurredAt = resolveDate(text, nowEpochMs),
        )
    }

    /**
     * Resuelve la fecha relativa de la frase ("hoy"=default, "ayer"=-1d,
     * "antier"/"anteayer"=-2d). Público para que [NlCaptureExtractor] lo reuse en
     * el camino LLM (la fecha la resolvemos siempre determinista para no depender
     * de que el modelo no alucine).
     */
    fun resolveDate(rawText: String, nowEpochMs: Long): Long {
        val lower = rawText.lowercase()
        val daysAgo = when {
            "antier" in lower || "anteayer" in lower -> 2L
            "ayer" in lower -> 1L
            else -> 0L
        }
        return if (daysAgo == 0L) nowEpochMs
        else Instant.ofEpochMilli(nowEpochMs).minus(daysAgo, ChronoUnit.DAYS).toEpochMilli()
    }

    /** Quita el monto y las palabras de ruido; deja la descripción. */
    private fun extractConcept(text: String, amountRange: IntRange): String {
        val withoutAmount =
            text.substring(0, amountRange.first) + " " + text.substring(amountRange.last + 1)
        val tokens = withoutAmount.split(WHITESPACE)
            .map { it.trim('.', ',', ';', ':', '$', '"', '(', ')') }
            .filter { it.isNotBlank() }

        // 1) Quita ruido de cabecera (verbos, preposiciones, artículos, moneda, fecha).
        var start = 0
        while (start < tokens.size && tokens[start].lowercase() in LEADING_NOISE) start++
        // 2) Quita moneda/fecha en cualquier posición (preserva "de"/"con" internos).
        var body = tokens.subList(start, tokens.size)
            .filterNot { it.lowercase() in STRONG_NOISE }
        // 3) Recorta preposiciones/artículos colgando al final.
        while (body.isNotEmpty() && body.last().lowercase() in TRAILING_NOISE) {
            body = body.dropLast(1)
        }

        val concept = body.joinToString(" ").trim()
        return concept.take(40).ifBlank { "Gasto" }
    }

    /** Primer número MXN plausible. Tolerante a `$`, comas y puntos. */
    private fun normalizeAmount(raw: String): Double? {
        var s = raw.trim().trimEnd('.', ',')
        if (s.isEmpty()) return null
        val lastDot = s.lastIndexOf('.')
        val lastComma = s.lastIndexOf(',')
        // El separador decimal es el que aparece más a la derecha.
        s = when {
            lastDot >= 0 && lastComma >= 0 ->
                if (lastDot > lastComma) s.replace(",", "")
                else s.replace(".", "").replace(',', '.')
            lastComma >= 0 -> // sólo comas: decimal si hay exactamente 2 dígitos tras la última
                if (s.length - lastComma - 1 == 2) s.replace(',', '.') else s.replace(",", "")
            else -> s // sólo puntos (o ninguno): el punto ya es decimal
        }
        return s.toDoubleOrNull()
    }

    companion object {
        /** `$1,234.56`, `200 pesos`, `café 60`: captura el primer número. */
        private val AMOUNT = Regex("""\$?\s*(\d[\d.,]*\d|\d)""")
        private val WHITESPACE = Regex("\\s+")

        private val VERBS = setOf(
            "gasté", "gaste", "gasto", "pagué", "pague", "pago", "compré", "compre",
            "costó", "costo", "fue", "fueron", "me", "gastamos", "pagamos", "cuesta",
        )
        private val PREPS = setOf("en", "de", "del", "para", "por", "con", "a", "al")
        private val ARTICLES = setOf("el", "la", "los", "las", "un", "una", "unos", "unas", "mi", "mis")
        private val CURRENCY = setOf("pesos", "peso", "mxn", "varos", "varas", "lana", "$")
        private val DATE = setOf("hoy", "ayer", "antier", "anteayer")

        private val LEADING_NOISE = VERBS + PREPS + ARTICLES + CURRENCY + DATE
        private val STRONG_NOISE = CURRENCY + DATE
        private val TRAILING_NOISE = PREPS + ARTICLES
    }
}
