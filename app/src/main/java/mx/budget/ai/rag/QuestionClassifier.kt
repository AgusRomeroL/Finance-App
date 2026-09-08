package mx.budget.ai.rag

import mx.budget.core.unaccent

enum class ContextDimension {
    SpendByCategory, TopExpenses, Wallets, ByMember, Installments, HistoricalCompare
}

/**
 * Clasificador heurístico barato para decidir qué queries SQL ejecutar.
 * Sobrecarga dimensiones en caso de duda para garantizar que el LLM
 * tenga los datos requeridos.
 */
object QuestionClassifier {

    /**
     * Pregunta abierta/analítica ("¿qué patrón no sobresale?", "analiza mis
     * gastos", "¿algo inusual?"): no mapea a un intent del schema, así que se
     * enruta DIRECTO a la ruta OPEN_ANALYSIS ([OpenAnalysisAnswerer]) sin
     * gastar una pasada del LLM intentando el intent JSON primero.
     */
    private val OPEN_ANALYSIS = Regex(
        ".*(patron|patrones|insight|analiza|analisis|analitic|inusual|raro|rara|extran|curioso|" +
            "sorprend|sobresale|oculto|escondid|llama la atencion|destaca|hallazgo|" +
            "tendencia general|vista general|panorama general|a simple vista|no veo|que observas|que notas).*"
    )

    fun isOpenAnalysis(q: String): Boolean = q.lowercase().unaccent().matches(OPEN_ANALYSIS)

    /**
     * Clasifica la pregunta a dimensiones de contexto. Los nombres propios del
     * hogar (miembros, cuentas, planes) llegan como PARÁMETROS; antes vivían
     * hardcodeados en los regex ("david|pau|santi", "banamex|bbva", "omar"),
     * lo que no generalizaba a otros hogares ni a datos nuevos.
     */
    fun classify(
        q: String,
        memberAliases: Collection<String> = emptyList(),
        walletNames: Collection<String> = emptyList(),
        planNames: Collection<String> = emptyList(),
    ): Set<ContextDimension> =
        detect(q, memberAliases, walletNames, planNames).ifEmpty {
            // Sin senal clara se sobrecarga el contexto para que el LLM tenga con
            // que trabajar. Quien necesite saber si de verdad se reconocio algo
            // (por ejemplo, para decir "esto no lo se") usa [detect].
            setOf(
                ContextDimension.SpendByCategory,
                ContextDimension.TopExpenses,
                ContextDimension.Wallets,
            )
        }

    /**
     * Dimensiones REALMENTE reconocidas, sin relleno. Conjunto vacio significa que
     * la pregunta no menciona nada que el ledger sepa contestar.
     */
    fun detect(
        q: String,
        memberAliases: Collection<String> = emptyList(),
        walletNames: Collection<String> = emptyList(),
        planNames: Collection<String> = emptyList(),
    ): Set<ContextDimension> {
        val n = q.lowercase().unaccent()
        fun mentionsAny(names: Collection<String>): Boolean = names.any { name ->
            val f = name.lowercase().unaccent().trim()
            f.length >= 3 && n.contains(f)
        }
        return buildSet {
            if (n.matches(Regex(".*(categor|gast.+en|cuanto.+en).*"))) add(ContextDimension.SpendByCategory)
            if (n.matches(Regex(".*(ultimo|recient|top|mayor).*"))) add(ContextDimension.TopExpenses)
            if (n.matches(Regex(".*(tarjeta|saldo|cuenta|debo|deuda).*")) || mentionsAny(walletNames)) add(ContextDimension.Wallets)
            if (n.matches(Regex(".*(quien|miembro).*")) || mentionsAny(memberAliases)) add(ContextDimension.ByMember)
            if (n.matches(Regex(".*(cuota|prestam|meses sin intereses|msi|plan).*")) || mentionsAny(planNames)) add(ContextDimension.Installments)
            if (n.matches(Regex(".*(compara|antes|pasad|anterior|tendenc).*"))) add(ContextDimension.HistoricalCompare)
        }
    }

    /**
     * ¿La pregunta usa vocabulario de dinero del hogar? Sirve para separar "no supe
     * resolverlo" de "esto no es del presupuesto": lo primero merece una segunda
     * pasada, lo segundo merece que la app lo diga en vez de improvisar un analisis.
     *
     * Se compara palabra por palabra, no por substring: con `contains` bruto,
     * "cuentame un chiste" caia dentro del presupuesto por la palabra "cuenta".
     */
    fun hasFinancialVocabulary(q: String): Boolean {
        val words = q.lowercase().unaccent()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotBlank() }
        return words.any { w -> w in MONEY_WORDS || MONEY_PREFIXES.any { w.startsWith(it) } }
    }

    private val MONEY_WORDS = setOf(
        "dinero", "peso", "pesos", "lana", "saldo", "saldos", "deuda", "deudas",
        "presupuesto", "quincena", "quincenas", "nomina", "sueldo", "ingreso", "ingresos",
        "tarjeta", "tarjetas", "cuenta", "cuentas", "efectivo", "banco", "bancos",
        "categoria", "categorias", "cuota", "cuotas", "msi", "mensualidad", "mensualidades",
        "factura", "facturas", "recibo", "recibos", "colegiatura", "colegiaturas",
        "renta", "hipoteca", "despensa", "super", "comida", "transporte", "gasolina",
        "servicio", "servicios", "mesada", "mesadas", "abono", "abonos", "intereses",
        "meta", "metas", "disponible", "reservado", "sobregiro",
    )

    /** Familias completas: gasto/gastar/gastamos, pago/pagar/pague, y demas. */
    private val MONEY_PREFIXES = listOf(
        "gast", "pag", "cobr", "ahorr", "deb", "prestam", "invers", "presupuest",
    )
}
