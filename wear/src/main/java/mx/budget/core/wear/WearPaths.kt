package mx.budget.core.wear

/**
 * Paths y Keys compartidos entre los módulos App y Wear para la API Data Layer.
 *
 * **Copia espejo** del `WearPaths` del módulo `:app` (`app/src/main/java/mx/budget/
 * core/wear/WearPaths.kt`). Los dos son APKs separados sin un módulo común, así que
 * estas constantes se duplican a propósito; mantener AMBAS en sync al cambiarlas.
 */
object WearPaths {
    // ---- Rutas (todas bajo /budget para casar el intent-filter del listener) ----
    /** Sincroniza el estado del presupuesto desde el móvil al reloj. */
    const val PATH_BUDGET_SYNC = "/budget/sync"

    /** El reloj envía un gasto rápido (preset) al móvil: payload "amount|concept". */
    const val PATH_NEW_EXPENSE = "/budget/expense/new"

    /** El reloj envía texto en lenguaje natural; el móvil lo parsea (§G.3). */
    const val PATH_NEW_NL = "/budget/nl"

    // ---- DataMap / Message Keys ----
    const val KEY_BALANCE_DISPONIBLE = "key_balance_disponible"
    const val KEY_QUINCENA_LABEL = "key_quincena_label"
    const val KEY_TIMESTAMP = "key_timestamp"

    const val KEY_EXPENSE_CONCEPT = "key_expense_concept"
    const val KEY_EXPENSE_AMOUNT = "key_expense_amount"
    const val KEY_NL_TEXT = "key_nl_text"
}
