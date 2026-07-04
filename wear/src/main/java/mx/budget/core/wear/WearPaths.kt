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

    /** El reloj envía un ingreso manual: payload "amount|label". Se inserta PLANNED. */
    const val PATH_NEW_INCOME = "/budget/income/new"

    /** El reloj confirma una captura de la bandeja (payload = pending_capture.id). */
    const val PATH_CONFIRM_PENDING = "/budget/pending/confirm"

    /** El reloj descarta una captura de la bandeja (payload = pending_capture.id). */
    const val PATH_DISCARD_PENDING = "/budget/pending/discard"

    // ---- DataMap / Message Keys ----
    const val KEY_BALANCE_DISPONIBLE = "key_balance_disponible"
    const val KEY_QUINCENA_LABEL = "key_quincena_label"
    const val KEY_TIMESTAMP = "key_timestamp"

    const val KEY_EXPENSE_CONCEPT = "key_expense_concept"
    const val KEY_EXPENSE_AMOUNT = "key_expense_amount"
    const val KEY_NL_TEXT = "key_nl_text"

    // ---- Payloads JSON empujados teléfono → reloj (parseados con org.json) ----
    /** Recomendados: JSON array de `{concept, amount, reason, canonicalKey}`. */
    const val KEY_SUGGESTIONS_JSON = "key_suggestions_json"

    /** Últimos movimientos POSTED: JSON array de `{concept, amount, occurredAt}`. */
    const val KEY_MOVEMENTS_JSON = "key_movements_json"

    /** Bandeja pendiente: JSON array de `{id, concept, amount, occurredAt, source}`. */
    const val KEY_PENDING_JSON = "key_pending_json"

    /** Gasto por miembro (rol BENEFICIARY): JSON array de `{name, total}`. */
    const val KEY_MEMBER_SPEND_JSON = "key_member_spend_json"
}
