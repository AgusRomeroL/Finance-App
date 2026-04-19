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
    fun classify(q: String): Set<ContextDimension> {
        val n = q.lowercase().unaccent()
        return buildSet {
            if (n.matches(Regex(".*(categor|gast.+en|cuanto.+en).*"))) add(ContextDimension.SpendByCategory)
            if (n.matches(Regex(".*(ultimo|recient|top|mayor).*"))) add(ContextDimension.TopExpenses)
            if (n.matches(Regex(".*(tarjeta|banamex|bbva|saldo|cuenta).*"))) add(ContextDimension.Wallets)
            if (n.matches(Regex(".*(quien|david|pau|santi|agustin|miembro).*"))) add(ContextDimension.ByMember)
            if (n.matches(Regex(".*(cuota|prestam|omar|mercado libre|plan).*"))) add(ContextDimension.Installments)
            if (n.matches(Regex(".*(compara|antes|pasad|anterior|tendenc).*"))) add(ContextDimension.HistoricalCompare)
            
            if (isEmpty()) {
                add(ContextDimension.SpendByCategory)
                add(ContextDimension.TopExpenses)
                add(ContextDimension.Wallets)
            }
        }
    }
}
