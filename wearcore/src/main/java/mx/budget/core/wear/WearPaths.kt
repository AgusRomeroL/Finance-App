package mx.budget.core.wear

/**
 * Paths y Keys compartidos entre los módulos App y Wear para la API Data Layer.
 */
object WearPaths {
    // ---- Rutas (todas bajo /budget para casar el intent-filter del listener) ----
    /** Path para sincronizar el estado del presupuesto desde el móvil al reloj. */
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

    /**
     * El reloj pide al móvil un snapshot fresco (pull-on-open del espejo en vivo,
     * §G.3.3). Payload vacío. El móvil responde re-empujando [PATH_BUDGET_SYNC] —
     * así el reloj no depende de que el dashboard del teléfono esté abierto para
     * ver la cifra "Disponible" real en su primer arranque.
     */
    const val PATH_REQUEST_SYNC = "/budget/sync/request"

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
