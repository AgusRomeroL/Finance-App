package mx.budget.data.repository

import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.entity.ExpenseAttributionEntity
import mx.budget.data.local.entity.ExpenseEntity
import mx.budget.data.local.result.ExpenseWithDetails
import mx.budget.data.local.result.SpendByMember
import mx.budget.data.local.result.MemberSpendByCategory
import mx.budget.data.local.result.TopExpense

/**
 * Contrato del repositorio de gastos y atribuciones.
 *
 * Encapsula la lógica transaccional de registrar un gasto con
 * sus atribuciones y el ajuste de saldo del wallet, garantizando
 * atomicidad mediante transacciones Room.
 */
interface ExpenseRepository {

    // ── Observación ─────────────────────────────────────────────

    /** Gastos de una quincena con detalles de categoría y wallet. */
    fun observeWithDetails(quincenaId: String): Flow<List<ExpenseWithDetails>>

    /**
     * Búsqueda de movimientos por texto (concepto o categoría), scope household.
     * Alimenta la barra de búsqueda inferior del dashboard.
     */
    fun searchWithDetails(householdId: String, query: String): Flow<List<ExpenseWithDetails>>

    /** Total ejecutado (POSTED) en una quincena — KPI principal. */
    fun observePostedTotal(quincenaId: String): Flow<Double>

    /** Total planeado (PLANNED) pendiente — indicador "falta gastar". */
    fun observePlannedTotal(quincenaId: String): Flow<Double>

    /** Gasto por miembro (BENEFICIARY = quién consume) en la quincena activa. */
    fun observeSpendByMember(quincenaId: String): Flow<List<SpendByMember>>

    /** Gasto por miembro (PAYER = quién paga) en la quincena activa. */
    fun observePaidByMember(quincenaId: String): Flow<List<SpendByMember>>

    // ── Lectura ─────────────────────────────────────────────────

    suspend fun getById(id: String): ExpenseEntity?

    /**
     * Atribuciones actuales de un gasto (ambas dimensiones BENEFICIARY + PAYER).
     * Alimenta el modo edición del detalle del gasto.
     */
    suspend fun getAttributions(expenseId: String): List<ExpenseAttributionEntity>

    /** Top N gastos de una quincena (para RAG). */
    suspend fun getTopExpenses(quincenaId: String, limit: Int = 15): List<TopExpense>

    /** Gasto de un miembro específico en una quincena. */
    suspend fun getSpendForMember(quincenaId: String, memberId: String): SpendByMember?

    /** Desglose por miembro y categoría en un rango de fechas. */
    suspend fun getSpendByMemberAndCategory(
        fromEpoch: Long,
        toEpoch: Long
    ): List<MemberSpendByCategory>

    /** Gastos por rango de fechas. */
    suspend fun getByDateRange(
        householdId: String,
        fromEpoch: Long,
        toEpoch: Long
    ): List<ExpenseEntity>

    // ── Escritura transaccional ──────────────────────────────────

    /**
     * Registra un gasto nuevo con sus atribuciones en una transacción atómica.
     *
     * 1. Inserta el ExpenseEntity.
     * 2. Inserta las ExpenseAttributionEntity (beneficiarios + pagadores).
     * 3. Ajusta el saldo del PaymentMethod (solo si status=POSTED).
     * 4. Recalcula los totales de la Quincena.
     *
     * Valida que ambas particiones (BENEFICIARY, PAYER) sumen 10,000 bps.
     *
     * @throws IllegalArgumentException si las basis points no suman 10,000.
     */
    suspend fun insertWithAttributions(
        expense: ExpenseEntity,
        attributions: List<ExpenseAttributionEntity>
    )

    /**
     * Actualiza un gasto existente y recalcula atribuciones.
     * Solo permitido si la quincena NO está CLOSED.
     */
    suspend fun updateWithAttributions(
        expense: ExpenseEntity,
        attributions: List<ExpenseAttributionEntity>
    )

    /**
     * Reemplaza la atribución de UNA dimensión ([role] = "BENEFICIARY" | "PAYER")
     * de un gasto, preservando la dimensión opuesta. Recalcula `share_amount_mxn`
     * desde el monto del gasto y encola un push de sync. Pasar [sharesBps] vacío
     * **borra** esa dimensión (revertir una auto-aplicación).
     *
     * Lo usa la pantalla "Revisión de atribuciones" al confirmar/editar/revertir
     * una sugerencia (Apéndice F.3.7). [sharesBps] debe sumar 10,000 si no es vacío.
     */
    suspend fun applyAttributionForRole(
        expenseId: String,
        role: String,
        sharesBps: Map<String, Int>
    )

    /**
     * Elimina un gasto y revierte el ajuste de saldo del wallet.
     * Solo permitido si la quincena NO está CLOSED.
     */
    suspend fun deleteAndRevertBalance(expenseId: String)

    /**
     * Fija/actualiza la ubicación de un gasto (Apéndice G.4). Lo usa el detalle del
     * gasto al "añadir ubicación" (source=MANUAL) o para borrarla (pasar todo null
     * con [source]="NONE"). Encola push de sync. No-op si el gasto no existe.
     */
    suspend fun setLocation(
        expenseId: String,
        latitude: Double?,
        longitude: Double?,
        placeLabel: String?,
        source: String
    )

    /**
     * Actualiza la fecha/hora (`occurred_at`, epoch millis) de un gasto (§G.4.1).
     * Encola push de sync. No-op si el gasto no existe.
     */
    suspend fun setOccurredAt(expenseId: String, occurredAt: Long)

    /**
     * Transiciona un gasto de PLANNED a POSTED.
     * Ajusta saldo del wallet y recalcula totales de quincena.
     */
    suspend fun postPlannedExpense(expenseId: String)

    /**
     * Confirma un gasto PLANNED (materializado desde una plantilla recurrente,
     * Apéndice G.2 Fase 2): lo pasa a POSTED y, si [actualAmountMxn] difiere del
     * monto previsto, lo actualiza y **re-escala** los `share_amount_mxn` de sus
     * atribuciones al monto real (los `share_bps` NO cambian). Encola push de sync.
     * No-op si el gasto no existe o ya no está PLANNED. [actualAmountMxn] = null
     * confirma con el monto previsto tal cual.
     */
    suspend fun confirmPlanned(expenseId: String, actualAmountMxn: Double? = null)
}
