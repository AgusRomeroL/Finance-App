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
     * hogar (miembros, cuentas, planes) llegan como PARÁMETROS — antes vivían
     * hardcodeados en los regex ("david|pau|santi", "banamex|bbva", "omar"),
     * lo que no generalizaba a otros hogares ni a datos nuevos.
     */
    fun classify(
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

            if (isEmpty()) {
                add(ContextDimension.SpendByCategory)
                add(ContextDimension.TopExpenses)
                add(ContextDimension.Wallets)
            }
        }
    }
}
