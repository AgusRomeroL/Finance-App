package mx.budget.ai.proactive

import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Una persona (o "TODOS") con un % opcional, tal como lo nombra la frase. El
 * nombre es CRUDO (sin resolver a memberId); [mx.budget.data.capture.BankCaptureManager]
 * lo resuelve contra el roster con [mx.budget.ai.dispatch.AliasResolver].
 *
 * @param name  nombre como aparece en la frase, o el literal "TODOS" (toda la familia).
 * @param share porcentaje 0-100 si la frase lo especifica; null = reparto equitativo.
 */
data class NameShare(
    val name: String,
    val share: Int? = null,
)

/**
 * Movimiento propuesto extraído de una frase en lenguaje natural (Apéndice G.3).
 *
 * Los campos básicos (monto, concepto, fecha) los garantiza el parser determinista;
 * los **enriquecidos** (notas, hints de categoría/wallet, beneficiarios, pagadores)
 * solo los llena el camino LLM con contexto del hogar (§G.3, captura rica). El
 * parser determinista los deja en sus defaults vacíos.
 *
 * @param amountMxn      monto positivo en MXN.
 * @param concept        comercio/descripción breve ("gasolina", "café").
 * @param occurredAt     epoch millis (hoy por default; "ayer"/"antier" lo retroceden).
 * @param notes          detalle extra que no es el concepto, o null.
 * @param categoryHint   nombre de categoría sugerido por el LLM (sin resolver), o null.
 * @param walletHint     nombre de método de pago sugerido por el LLM (sin resolver), o null.
 * @param beneficiaries  quién consume, con nombres crudos + % opcional (vacío si la frase no lo dice).
 * @param payers         quién paga, con nombres crudos + % opcional (vacío si no se menciona).
 */
data class ParsedNlCapture(
    val amountMxn: Double,
    val concept: String,
    val occurredAt: Long,
    val notes: String? = null,
    val categoryHint: String? = null,
    val walletHint: String? = null,
    val beneficiaries: List<NameShare> = emptyList(),
    val payers: List<NameShare> = emptyList(),
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

        // 1) Quita ruido de cabecera (verbos, preposiciones, artículos, moneda,
        //    fecha). Los nexos "de"/"que" tras un verbo también son cabecera:
        //    cubren perífrasis como "acabo de gastar" o "terminé de pagar".
        var start = 0
        while (start < tokens.size) {
            val t = tokens[start].lowercase()
            val esNexoDePerifrasis = (t == "de" || t == "que") &&
                start > 0 && tokens[start - 1].lowercase() in VERBS
            if (t in LEADING_NOISE || esNexoDePerifrasis) start++ else break
        }

        // 2) Heurística prioritaria: si queda una preposición de objeto (en|de|por)
        //    FUERA de la cabecera, el concepto es lo que sigue a la ÚLTIMA de
        //    ellas ("gasté 10 en comprando papas fritas" → "papas fritas").
        val lastPrep = (start until tokens.size - 1)
            .lastOrNull { tokens[it].lowercase() in OBJECT_PREPS }
        if (lastPrep != null) {
            val fragment = cleanFragment(tokens.subList(lastPrep + 1, tokens.size))
            if (fragment.isNotBlank()) return truncateAtWord(fragment)
        }

        // 3) Heurística de cabecera (camino histórico): quita moneda/fecha en
        //    cualquier posición (preserva "de"/"con" internos) y recorta
        //    preposiciones/artículos colgando al final.
        val concept = cleanFragment(tokens.subList(start, tokens.size))
        return truncateAtWord(concept).ifBlank { "Gasto" }
    }

    /**
     * Limpia un fragmento candidato a concepto: quita verbos/gerundios líderes
     * ("comprando papas fritas" → "papas fritas"), moneda/fecha en cualquier
     * posición y preposiciones/artículos colgando al final.
     */
    private fun cleanFragment(fragment: List<String>): String {
        var lead = 0
        while (lead < fragment.size) {
            val t = fragment[lead].lowercase()
            val esNexoDePerifrasis = (t == "de" || t == "que") &&
                lead > 0 && fragment[lead - 1].lowercase() in VERBS
            if (t in VERBS || esNexoDePerifrasis) lead++ else break
        }
        var body = fragment.subList(lead, fragment.size)
            .filterNot { it.lowercase() in STRONG_NOISE }
        while (body.isNotEmpty() && body.last().lowercase() in TRAILING_NOISE) {
            body = body.dropLast(1)
        }
        return body.joinToString(" ").trim()
    }

    /**
     * Trunca a [MAX_CONCEPT_LEN] chars SIN partir palabras: si el corte cae a
     * media palabra retrocede al último espacio ("papas frit" → "papas"), y
     * quita preposiciones/artículos que queden colgando tras el corte.
     */
    private fun truncateAtWord(s: String): String {
        if (s.length <= MAX_CONCEPT_LEN) return s
        val cut = s.take(MAX_CONCEPT_LEN)
        val lastSpace = cut.lastIndexOf(' ')
        val whole = (if (lastSpace > 0) cut.substring(0, lastSpace) else cut).trimEnd()
        var words = whole.split(' ')
        while (words.size > 1 && words.last().lowercase() in TRAILING_NOISE) {
            words = words.dropLast(1)
        }
        return words.joinToString(" ")
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

        /** Máximo de caracteres del concepto propuesto (se trunca por palabra). */
        private const val MAX_CONCEPT_LEN = 40

        private val VERBS = setOf(
            // Pretéritos/presentes (con y sin acento).
            "gasté", "gaste", "gasto", "pagué", "pague", "pago", "compré", "compre",
            "costó", "costo", "fue", "fueron", "me", "gastamos", "pagamos", "compramos",
            "cuesta",
            // Perífrasis "acabo de…"/"terminé de…" (con y sin acento).
            "acabo", "acabé", "acabe", "acabamos", "terminé", "termine", "terminamos",
            // Infinitivos ("acabo de gastar").
            "gastar", "pagar", "comprar",
            // Gerundios ("en comprando papas fritas").
            "gastando", "pagando", "comprando",
        )
        private val PREPS = setOf("en", "de", "del", "para", "por", "con", "a", "al")

        /** Preposiciones que introducen el objeto del gasto ("en gasolina", "por la luz"). */
        private val OBJECT_PREPS = setOf("en", "de", "por")
        private val ARTICLES = setOf("el", "la", "los", "las", "un", "una", "unos", "unas", "mi", "mis")
        private val CURRENCY = setOf("pesos", "peso", "mxn", "varos", "varas", "lana", "$")
        private val DATE = setOf("hoy", "ayer", "antier", "anteayer")

        private val LEADING_NOISE = VERBS + PREPS + ARTICLES + CURRENCY + DATE
        private val STRONG_NOISE = CURRENCY + DATE
        private val TRAILING_NOISE = PREPS + ARTICLES
    }
}
