package mx.budget.ui.capture

// ─────────────────────────────────────────────────────────────────────────────
// CaptureSheetMode — contrato público del modo de apertura del CaptureBottomSheet
// (SP-A1, paquete A3). Otros paquetes (voz, banco, reloj, bandeja) abren la hoja
// con un modo Review pre-llenado y campos faltantes marcados "Por decidir".
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Modo con el que se abre el [CaptureBottomSheet].
 *
 * - [New]: captura manual de gasto desde cero (default — todos los call sites
 *   existentes siguen compilando sin cambios).
 * - [Income]: la hoja abre directamente en modo ingreso (toggle en "Ingreso").
 * - [Review]: la hoja abre pre-llenada desde una captura externa (NL/voz/banco);
 *   los campos en [Review.missingFields] se marcan "Por decidir" y el botón
 *   Registrar queda deshabilitado hasta resolverlos.
 */
sealed interface CaptureSheetMode {
    /** Captura manual de gasto, formulario vacío. */
    data object New : CaptureSheetMode

    /** Captura manual de ingreso (mismo sheet, contenido conmutado). */
    data object Income : CaptureSheetMode

    /**
     * Revisión de una captura pre-llenada (p. ej. desde `pending_capture`).
     *
     * @param prefill       Valores conocidos; los `null` se dejan como estén.
     * @param missingFields Campos que el origen NO pudo determinar: se marcan
     *                      visualmente y bloquean el registro hasta resolverse.
     */
    data class Review(
        val prefill: CapturePrefill,
        val missingFields: Set<CaptureField> = emptySet(),
    ) : CaptureSheetMode
}

/**
 * Valores pre-llenados para el modo [CaptureSheetMode.Review].
 *
 * Los mapas de atribución vienen en **basis points** (memberId → bps, suma
 * 10 000 por rol), igual que `expense_attribution`; el ViewModel los convierte
 * a % para la edición.
 *
 * @param pendingCaptureId Si la revisión nació de una fila de `pending_capture`,
 *        su id: al registrar, esa captura se marca CONFIRMED y sale de la bandeja.
 */
data class CapturePrefill(
    val amountMxn: Double? = null,
    val concept: String? = null,
    val categoryId: String? = null,
    val walletId: String? = null,
    val occurredAt: Long? = null,
    val beneficiaryBps: Map<String, Int>? = null,
    val payerBps: Map<String, Int>? = null,
    val notes: String? = null,
    val pendingCaptureId: String? = null,
)

/** Campos del formulario de captura que un origen externo puede dejar sin decidir. */
enum class CaptureField { AMOUNT, CONCEPT, CATEGORY, WALLET, DATE, BENEFICIARY, PAYER }

/** Tipo de movimiento activo en la hoja (toggle Gasto/Ingreso). */
enum class CaptureKind { EXPENSE, INCOME }
