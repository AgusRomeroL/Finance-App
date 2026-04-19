package mx.budget.core.wear

/**
 * Paths y Keys compartidos entre los módulos App y Wear para la API Data Layer.
 */
object WearPaths {
    // ---- Rutas ----
    /** Path para sincronizar el estado del presupuesto desde el móvil al reloj. */
    const val PATH_BUDGET_SYNC = "/budget/sync"
    
    /** Path usado por el reloj para enviar eventos de gasto rápido al móvil. */
    const val PATH_NEW_EXPENSE = "/expense/new"

    // ---- DataMap Keys ----
    const val KEY_BALANCE_DISPONIBLE = "key_balance_disponible"
    const val KEY_QUINCENA_LABEL = "key_quincena_label"
    const val KEY_TIMESTAMP = "key_timestamp"

    const val KEY_EXPENSE_CONCEPT = "key_expense_concept"
    const val KEY_EXPENSE_AMOUNT = "key_expense_amount"
}
