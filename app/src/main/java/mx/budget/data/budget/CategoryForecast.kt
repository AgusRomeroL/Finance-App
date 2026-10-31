package mx.budget.data.budget

/**
 * Nivel de un aviso de presupuesto por categoria (Fase 7b, alcance 2). El orden
 * importa: un nivel mayor reemplaza a los menores en la misma quincena.
 */
enum class BudgetAlertLevel {
    /** Todavia por debajo del 80 %, pero al ritmo actual se pasaria del tope. */
    ANTICIPADO,

    /** Lo gastado llego al 80 % del tope. */
    OCHENTA,

    /** Lo gastado llego al tope o lo paso. */
    CIEN,
}

/**
 * Pronostico de gasto por categoria y nivel de aviso. Puro, sin Android.
 *
 * El pronostico es lineal: lo gastado dividido entre la fraccion de quincena
 * transcurrida, la misma que usa la tarjeta Ritmo ([mx.budget.data.quincena.QuincenaProgress]).
 * Con uno o dos dias de datos una sola compra proyectaria una cifra absurda, asi
 * que no hay pronostico antes del dia [MIN_DAY_FOR_FORECAST].
 */
object CategoryForecast {

    const val MIN_DAY_FOR_FORECAST = 3

    /** Gasto proyectado al cierre de la quincena, o null si todavia no hay base. */
    fun projected(spent: Double, fraction: Float, dayIndex: Int): Double? {
        if (dayIndex < MIN_DAY_FOR_FORECAST || fraction <= 0f || spent <= 0.0) return null
        return spent / fraction
    }

    /** Porcentaje entero de [amount] sobre [cap], o 0 sin tope. */
    fun pct(amount: Double, cap: Double): Int =
        if (cap > 0.0) Math.round(amount / cap * 100).toInt() else 0

    /** Nivel de aviso que corresponde, o null si no hay nada que avisar. */
    fun level(spent: Double, cap: Double, projected: Double?): BudgetAlertLevel? = when {
        cap <= 0.0 -> null
        spent >= cap -> BudgetAlertLevel.CIEN
        spent >= cap * 0.8 -> BudgetAlertLevel.OCHENTA
        projected != null && projected > cap -> BudgetAlertLevel.ANTICIPADO
        else -> null
    }
}
