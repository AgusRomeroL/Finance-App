package mx.budget.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.entity.ExpenseEntity
import mx.budget.data.local.result.ExpenseWithDetails
import mx.budget.data.local.result.TopExpense

@Dao
interface ExpenseDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(expense: ExpenseEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(expenses: List<ExpenseEntity>)

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Delete
    suspend fun delete(expense: ExpenseEntity)

    @Query("SELECT * FROM expense WHERE id = :id")
    suspend fun getById(id: String): ExpenseEntity?

    @Query("SELECT * FROM expense WHERE id = :id")
    fun observeById(id: String): Flow<ExpenseEntity?>

    // ── Queries por quincena ────────────────────────────────────

    /**
     * Gastos de una quincena, todos los estados, orden cronológico descendente.
     */
    @Query("""
        SELECT * FROM expense 
        WHERE quincena_id = :quincenaId 
        ORDER BY occurred_at DESC
    """)
    fun observeByQuincena(quincenaId: String): Flow<List<ExpenseEntity>>

    /**
     * Gastos de una quincena filtrados por estado.
     */
    @Query("""
        SELECT * FROM expense 
        WHERE quincena_id = :quincenaId AND status = :status 
        ORDER BY occurred_at DESC
    """)
    fun observeByQuincenaAndStatus(quincenaId: String, status: String): Flow<List<ExpenseEntity>>

    /**
     * Gastos con detalles (JOIN con category, payment_method, quincena).
     * Alimenta las listas de gastos en QuincenaScreen.
     */
    @Query("""
        SELECT 
            e.id AS expenseId,
            e.concept,
            e.amount_mxn AS amountMxn,
            e.occurred_at AS occurredAt,
            e.status,
            c.display_name AS categoryName,
            c.code AS categoryCode,
            c.color_hex AS categoryColorHex,
            pm.display_name AS paymentMethodName,
            pm.kind AS paymentMethodKind,
            q.label AS quincenaLabel,
            e.installment_number AS installmentNumber,
            ip.total_installments AS installmentTotal,
            e.notes
        FROM expense e
        JOIN category c ON c.id = e.category_id
        JOIN payment_method pm ON pm.id = e.payment_method_id
        JOIN quincena q ON q.id = e.quincena_id
        LEFT JOIN installment_plan ip ON ip.id = e.installment_plan_id
        WHERE e.quincena_id = :quincenaId
        ORDER BY e.occurred_at DESC
    """)
    fun observeWithDetails(quincenaId: String): Flow<List<ExpenseWithDetails>>

    // ── Totales y agregaciones ──────────────────────────────────

    /**
     * Gasto total ejecutado (POSTED) en una quincena.
     * KPI principal del dashboard quincenal.
     */
    @Query("""
        SELECT COALESCE(SUM(amount_mxn), 0.0)
        FROM expense 
        WHERE quincena_id = :quincenaId AND status = 'POSTED'
    """)
    fun observePostedTotal(quincenaId: String): Flow<Double>

    /**
     * Gasto total planeado (PLANNED) pendiente de ejecutar.
     * Alimenta el indicador "Falta gastar" del dashboard.
     */
    @Query("""
        SELECT COALESCE(SUM(amount_mxn), 0.0)
        FROM expense 
        WHERE quincena_id = :quincenaId AND status = 'PLANNED'
    """)
    fun observePlannedTotal(quincenaId: String): Flow<Double>

    /**
     * Top N gastos por monto en una quincena (con joins).
     * Alimenta el contexto RAG del asistente IA.
     */
    @Query("""
        SELECT 
            e.id AS expenseId,
            date(e.occurred_at / 1000, 'unixepoch') AS date,
            e.concept,
            e.amount_mxn AS amountMxn,
            c.display_name AS categoryName,
            pm.display_name AS walletName
        FROM expense e
        JOIN category c ON c.id = e.category_id
        JOIN payment_method pm ON pm.id = e.payment_method_id
        WHERE e.quincena_id = :quincenaId AND e.status = 'POSTED'
        ORDER BY e.amount_mxn DESC
        LIMIT :limit
    """)
    suspend fun getTopExpenses(quincenaId: String, limit: Int = 15): List<TopExpense>

    // ── Queries por categoría ───────────────────────────────────

    /**
     * Gastos ejecutados de una categoría en una quincena.
     */
    @Query("""
        SELECT COALESCE(SUM(amount_mxn), 0.0)
        FROM expense 
        WHERE quincena_id = :quincenaId 
          AND category_id = :categoryId 
          AND status = 'POSTED'
    """)
    suspend fun getPostedTotalByCategory(quincenaId: String, categoryId: String): Double

    /**
     * Gastos por método de pago en la quincena activa.
     */
    @Query("""
        SELECT COALESCE(SUM(amount_mxn), 0.0)
        FROM expense 
        WHERE quincena_id = :quincenaId 
          AND payment_method_id = :paymentMethodId 
          AND status = 'POSTED'
    """)
    suspend fun getPostedTotalByWallet(quincenaId: String, paymentMethodId: String): Double

    // ── Queries por rango de fechas ─────────────────────────────

    /**
     * Gastos por rango de fechas (epoch millis).
     * Usado para analíticas de rango arbitrario.
     */
    @Query("""
        SELECT * FROM expense 
        WHERE household_id = :householdId 
          AND occurred_at BETWEEN :fromEpoch AND :toEpoch
          AND status = 'POSTED'
        ORDER BY occurred_at DESC
    """)
    suspend fun getByDateRange(householdId: String, fromEpoch: Long, toEpoch: Long): List<ExpenseEntity>

    // ── Queries para cuotas ─────────────────────────────────────

    /**
     * Gastos vinculados a un plan de cuotas.
     */
    @Query("""
        SELECT * FROM expense 
        WHERE installment_plan_id = :planId 
        ORDER BY installment_number ASC
    """)
    fun observeByInstallmentPlan(planId: String): Flow<List<ExpenseEntity>>

    // ── Queries para recurrencia ────────────────────────────────

    /**
     * Gastos originados por una plantilla recurrente.
     */
    @Query("""
        SELECT * FROM expense 
        WHERE recurrence_template_id = :templateId 
        ORDER BY occurred_at DESC
    """)
    suspend fun getByRecurrenceTemplate(templateId: String): List<ExpenseEntity>

    // ── Mutación de estado ──────────────────────────────────────

    /**
     * Transiciona un gasto de PLANNED a POSTED.
     * Típicamente seguido de un ajuste de saldo en PaymentMethodDao.
     */
    @Query("UPDATE expense SET status = 'POSTED' WHERE id = :id")
    suspend fun markAsPosted(id: String)

    @Query("UPDATE expense SET status = 'RECONCILED' WHERE id = :id")
    suspend fun markAsReconciled(id: String)

    /**
     * Cuenta gastos del día para la alerta "no_expense_today".
     */
    @Query("""
        SELECT COUNT(*) FROM expense 
        WHERE household_id = :householdId 
          AND occurred_at >= :dayStartEpoch 
          AND occurred_at < :dayEndEpoch
    """)
    suspend fun countExpensesToday(householdId: String, dayStartEpoch: Long, dayEndEpoch: Long): Int
}
