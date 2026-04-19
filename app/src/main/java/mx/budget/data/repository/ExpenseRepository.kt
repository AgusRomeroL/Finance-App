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

    /** Total ejecutado (POSTED) en una quincena — KPI principal. */
    fun observePostedTotal(quincenaId: String): Flow<Double>

    /** Total planeado (PLANNED) pendiente — indicador "falta gastar". */
    fun observePlannedTotal(quincenaId: String): Flow<Double>

    /** Gasto por miembro (BENEFICIARY) en la quincena activa. */
    fun observeSpendByMember(quincenaId: String): Flow<List<SpendByMember>>

    // ── Lectura ─────────────────────────────────────────────────

    suspend fun getById(id: String): ExpenseEntity?

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
     * Elimina un gasto y revierte el ajuste de saldo del wallet.
     * Solo permitido si la quincena NO está CLOSED.
     */
    suspend fun deleteAndRevertBalance(expenseId: String)

    /**
     * Transiciona un gasto de PLANNED a POSTED.
     * Ajusta saldo del wallet y recalcula totales de quincena.
     */
    suspend fun postPlannedExpense(expenseId: String)
}
