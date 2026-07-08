package mx.budget.data.statements

/**
 * Modelos del paso "Reescribir movimientos" del flujo de estado de cuenta
 * (extensión del paquete C1).
 *
 * Al importar el estado de cuenta de una tarjeta, además de reconciliar
 * wallet + MSI (C1), la app puede reescribir el ledger con confirmación
 * explícita del usuario:
 *
 *  1. [AggregateCandidate] — gastos agregados de "pago de tarjeta" (en la
 *     semilla, categoría `LOANS.<TARJETA>` pagada desde un wallet bancario)
 *     detectados dentro del periodo del estado. Convertirlos = borrar el gasto
 *     (revirtiendo su saldo) y registrar una transferencia banco→tarjeta
 *     (RF-41), para que el pago deje de contar como gasto y quede como abono
 *     a la deuda.
 *  2. [PlannedPurchase] — compras itemizadas del estado que se insertan como
 *     gastos reales cargados al wallet de la tarjeta, con categoría sugerida
 *     por historial, beneficiarios en partes iguales y pagador 100% el adulto
 *     pagador de la casa (regla del hogar desde oct-2025), marcadas para
 *     revisión de atribución (`attribution_review`).
 */

/**
 * Gasto agregado de pago de tarjeta detectado como convertible a transferencia.
 * Snapshot de solo lectura para la UI de confirmación; la conversión relee el
 * gasto por id dentro de la transacción.
 */
data class AggregateCandidate(
    val expenseId: String,
    val concept: String,
    val amountMxn: Double,
    val occurredAt: Long,
    /** Wallet bancario desde el que se pagó (origen de la transferencia). */
    val fromWalletId: String,
    val fromWalletName: String,
    val categoryName: String,
)

/**
 * Compra itemizada del estado de cuenta lista para insertarse como gasto.
 * Derivada de un [StatementMovement] (posiblemente editado en el preview) con
 * la categoría ya sugerida por la heurística de historial.
 */
data class PlannedPurchase(
    /** Fecha ISO `YYYY-MM-DD` del cargo; null = se usa el corte/hoy. */
    val fecha: String?,
    val concepto: String,
    val montoMxn: Double,
    /** `true` si el movimiento es MSI (por default NO se inserta como gasto: ya se registra como plan a meses). */
    val esMsi: Boolean,
    val suggestedCategoryId: String?,
    val suggestedCategoryName: String?,
    /**
     * Miembros que se benefician de esta compra (memberIds). Precargados desde
     * `beneficiariosSugeridos` del LLM; el usuario los ajusta con chips en el paso
     * de reescritura. Vacío = reparto equitativo entre todos los miembros.
     */
    val suggestedBeneficiaryIds: List<String> = emptyList(),
)

/** Miembro del hogar para los chips de beneficiario del paso de reescritura. */
data class RewriteMember(
    val id: String,
    val name: String,
)

/**
 * Plan de reescritura propuesto: qué se insertaría y qué se convertiría.
 * La UI lo presenta con checkboxes; nada se aplica sin confirmación.
 */
data class RewritePlan(
    val purchases: List<PlannedPurchase>,
    val aggregates: List<AggregateCandidate>,
    /** Nombre del miembro que quedará como PAYER 100% de las compras. */
    val payerName: String?,
    /** Cuántos miembros reciben el reparto equitativo como BENEFICIARY. */
    val beneficiaryCount: Int,
    /** Miembros del hogar disponibles para los chips de beneficiario. */
    val members: List<RewriteMember> = emptyList(),
    /** Categorías hoja (id → nombre) para el dropdown de categoría por compra. */
    val categories: List<RewriteMember> = emptyList(),
)

/** Resultado de aplicar el estado de cuenta con reescritura. */
data class RewriteResult(
    /** Planes MSI creados/actualizados (reconciliación C1). */
    val msiTouched: Int,
    /** Compras insertadas como gastos. */
    val insertedExpenses: Int,
    /** Suma MXN de las compras insertadas. */
    val insertedTotalMxn: Double,
    /** Agregados convertidos en transferencia banco→tarjeta. */
    val convertedTransfers: Int,
)
