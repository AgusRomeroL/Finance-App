package mx.budget.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.entity.ExpenseEntity
import mx.budget.data.local.result.ExpenseWithDetails
import mx.budget.data.local.result.TopExpense

/**
 * DAO del ledger de gastos.
 *
 * Las proyecciones de lectura ([ExpenseWithDetails], [TopExpense]) se
 * construyen con JOINs explícitos y alias que mapean a los campos de
 * los POJOs en `data.local.result`.
 */
@Dao
interface ExpenseDao {

    @Query(
        """
        SELECT
            e.id                       AS expenseId,
            e.concept                  AS concept,
            e.amount_mxn               AS amountMxn,
            e.occurred_at              AS occurredAt,
            e.status                   AS status,
            e.category_id              AS categoryId,
            c.display_name             AS categoryName,
            c.code                     AS categoryCode,
            c.color_hex                AS categoryColorHex,
            pm.display_name            AS paymentMethodName,
            pm.kind                    AS paymentMethodKind,
            q.label                    AS quincenaLabel,
            e.installment_number       AS installmentNumber,
            ip.total_installments      AS installmentTotal,
            e.notes                    AS notes
        FROM expense e
        INNER JOIN category c        ON c.id = e.category_id
        INNER JOIN payment_method pm ON pm.id = e.payment_method_id
        INNER JOIN quincena q        ON q.id = e.quincena_id
        LEFT JOIN installment_plan ip ON ip.id = e.installment_plan_id
        WHERE e.quincena_id = :quincenaId
        ORDER BY e.occurred_at DESC
        """
    )
    fun observeWithDetails(quincenaId: String): Flow<List<ExpenseWithDetails>>

    /**
     * Búsqueda de movimientos por texto (concepto o nombre de categoría), scope
     * household (búsqueda global, patrón Pixel Screenshots). Alimenta la barra de
     * búsqueda inferior. Mismas columnas/JOINs que [observeWithDetails].
     */
    @Query(
        """
        SELECT
            e.id                       AS expenseId,
            e.concept                  AS concept,
            e.amount_mxn               AS amountMxn,
            e.occurred_at              AS occurredAt,
            e.status                   AS status,
            e.category_id              AS categoryId,
            c.display_name             AS categoryName,
            c.code                     AS categoryCode,
            c.color_hex                AS categoryColorHex,
            pm.display_name            AS paymentMethodName,
            pm.kind                    AS paymentMethodKind,
            q.label                    AS quincenaLabel,
            e.installment_number       AS installmentNumber,
            ip.total_installments      AS installmentTotal,
            e.notes                    AS notes
        FROM expense e
        INNER JOIN category c        ON c.id = e.category_id
        INNER JOIN payment_method pm ON pm.id = e.payment_method_id
        INNER JOIN quincena q        ON q.id = e.quincena_id
        LEFT JOIN installment_plan ip ON ip.id = e.installment_plan_id
        WHERE e.household_id = :householdId
          AND (e.concept LIKE '%' || :q || '%' COLLATE NOCASE
               OR c.display_name LIKE '%' || :q || '%' COLLATE NOCASE)
        ORDER BY e.occurred_at DESC
        LIMIT :limit
        """
    )
    fun searchWithDetails(householdId: String, q: String, limit: Int = 100): Flow<List<ExpenseWithDetails>>

    @Query(
        """
        SELECT COALESCE(SUM(amount_mxn), 0.0)
        FROM expense
        WHERE quincena_id = :quincenaId AND status = 'POSTED'
        """
    )
    fun observePostedTotal(quincenaId: String): Flow<Double>

    @Query(
        """
        SELECT COALESCE(SUM(amount_mxn), 0.0)
        FROM expense
        WHERE quincena_id = :quincenaId AND status = 'PLANNED'
        """
    )
    fun observePlannedTotal(quincenaId: String): Flow<Double>

    @Query("SELECT * FROM expense WHERE id = :id")
    suspend fun getById(id: String): ExpenseEntity?

    /**
     * Cuántos gastos (de cualquier estado) ya provienen de [templateId] en
     * [quincenaId]. Idempotencia de la materialización (Apéndice G.2, Fase 1):
     * incluir POSTED evita re-crear un PLANNED si el usuario ya confirmó el pago.
     */
    @Query(
        "SELECT COUNT(*) FROM expense " +
            "WHERE recurrence_template_id = :templateId AND quincena_id = :quincenaId"
    )
    suspend fun countForTemplateInQuincena(templateId: String, quincenaId: String): Int

    /**
     * Todos los gastos del hogar — usado por el pipeline de canonicalización
     * retroactiva (Apéndice F.3.4) para recalcular `concept_canonical` en lote.
     */
    @Query("SELECT * FROM expense WHERE household_id = :householdId")
    suspend fun getAll(householdId: String): List<ExpenseEntity>

    /** Persiste la clave canónica calculada para un gasto. */
    @Query("UPDATE expense SET concept_canonical = :canonical WHERE id = :id")
    suspend fun updateConceptCanonical(id: String, canonical: String?)

    @Query(
        """
        SELECT
            e.id            AS expenseId,
            CAST(e.occurred_at AS TEXT) AS date,
            e.concept       AS concept,
            e.amount_mxn    AS amountMxn,
            c.display_name  AS categoryName,
            pm.display_name AS walletName
        FROM expense e
        INNER JOIN category c        ON c.id = e.category_id
        INNER JOIN payment_method pm ON pm.id = e.payment_method_id
        WHERE e.quincena_id = :quincenaId
        ORDER BY e.amount_mxn DESC
        LIMIT :limit
        """
    )
    suspend fun getTopExpenses(quincenaId: String, limit: Int): List<TopExpense>

    @Query(
        """
        SELECT * FROM expense
        WHERE household_id = :householdId
          AND occurred_at BETWEEN :fromEpoch AND :toEpoch
        ORDER BY occurred_at DESC
        """
    )
    suspend fun getByDateRange(
        householdId: String,
        fromEpoch: Long,
        toEpoch: Long
    ): List<ExpenseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: ExpenseEntity)

    /**
     * Upsert idempotente usado EXCLUSIVAMENTE por el pull (Firestore → Room).
     * No encola en `sync_queue` (a diferencia de los repos públicos), evitando
     * el bucle push↔pull. Política de conflictos: REPLACE (last-write-wins).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(expense: ExpenseEntity)

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Delete
    suspend fun delete(expense: ExpenseEntity)
}
