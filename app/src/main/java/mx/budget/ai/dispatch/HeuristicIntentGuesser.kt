package mx.budget.ai.dispatch

import mx.budget.ai.domain.AssistantResponse
import mx.budget.core.unaccent

/**
 * Red de seguridad determinista del asistente: cuando la salida del LLM no se
 * pudo parsear como intent, adivina el intent directamente de la pregunta con
 * reglas de palabras clave + resolución de entidades por nombre (vía
 * [AliasResolver.findCategoryIn] y compañía). Así el chat SIEMPRE intenta
 * responder algo útil con datos reales en vez de rendirse con "reformula".
 *
 * Devuelve null solo si ninguna regla aplica (→ la UI muestra el error).
 */
object HeuristicIntentGuesser {

    fun guess(question: String, resolver: AliasResolver): AssistantResponse? {
        val q = question.lowercase().unaccent()

        val category = resolver.findCategoryIn(question)
        val member = resolver.findMemberIn(question)
        val wallet = resolver.findWalletIn(question)
        val plan = resolver.findInstallmentPlanIn(question)

        fun has(vararg keys: String) = keys.any { q.contains(it) }

        val intent: AssistantResponse.Intent
        var args = AssistantResponse.Args()

        when {
            // "¿Quién gasta más?" / "¿quién ha gastado más?"
            has("quien") && has("gast") -> intent = AssistantResponse.Intent.GET_TOP_SPENDER

            // "¿En qué gasto más?" / "¿en qué se va el dinero?" / "¿dónde gastamos más?"
            has("en que gast", "en que se va", "donde gast", "que gasto mas", "gasto mas", "gastamos mas") ->
                intent = AssistantResponse.Intent.GET_TOP_CATEGORY

            // Cuotas / MSI / planes a meses.
            has("cuota", "msi", "meses sin intereses", "mensualidad") -> {
                if (plan != null) {
                    intent = AssistantResponse.Intent.GET_INSTALLMENT_STATUS
                    args = args.copy(plan_name = plan.displayName)
                } else {
                    intent = AssistantResponse.Intent.LIST_UPCOMING_INSTALLMENTS
                }
            }

            // Comparaciones con el pasado.
            has("compara", "promedio", "anterior", "pasad", "historic", "tendenc") ->
                intent = AssistantResponse.Intent.COMPARE_QUINCENAS

            // Resumen general.
            has("resumen", "como vamos", "como voy", "panorama") ->
                intent = AssistantResponse.Intent.SUMMARIZE_QUINCENA

            // Gasto de un miembro concreto.
            member != null && has("gast", "lleva", "cuanto") -> {
                intent = AssistantResponse.Intent.GET_SPEND_BY_MEMBER
                args = args.copy(member_alias = member.displayName)
            }

            // "¿Cuánto queda en X?" / presupuesto de una categoría.
            category != null && has("queda", "quedan", "resta", "disponible", "presupuesto", "cuanto") -> {
                intent = AssistantResponse.Intent.GET_CATEGORY_REMAINING
                args = args.copy(category_code = category.code)
            }

            // Saldo de una cuenta/tarjeta.
            wallet != null && has("saldo", "cuanto hay", "cuanto tengo", "debo") -> {
                intent = AssistantResponse.Intent.GET_WALLET_BALANCE
                args = args.copy(wallet_name = wallet.displayName)
            }

            // Menciones sueltas: una categoría o una cuenta a secas.
            category != null -> {
                intent = AssistantResponse.Intent.GET_CATEGORY_REMAINING
                args = args.copy(category_code = category.code)
            }
            wallet != null -> {
                intent = AssistantResponse.Intent.GET_WALLET_BALANCE
                args = args.copy(wallet_name = wallet.displayName)
            }

            else -> return null
        }

        return AssistantResponse(intent = intent, args = args, reason = "heurística local")
    }
}
