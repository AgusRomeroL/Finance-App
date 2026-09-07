package mx.budget.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.entity.QuincenaEntity
import mx.budget.data.local.result.QuincenaSnapshot

@Dao
interface QuincenaDao {

    @Query(
        """
        SELECT * FROM quincena
        WHERE household_id = :householdId AND status = 'ACTIVE'
        LIMIT 1
        """
    )
    fun observeActive(householdId: String): Flow<QuincenaEntity?>

    @Query(
        """
        SELECT * FROM quincena
        WHERE household_id = :householdId AND status = 'ACTIVE'
        LIMIT 1
        """
    )
    suspend fun getActive(householdId: String): QuincenaEntity?

    @Query("SELECT * FROM quincena WHERE id = :id")
    suspend fun getById(id: String): QuincenaEntity?

    /**
     * Quincena que contiene una fecha ISO `YYYY-MM-DD` (comparación lexicográfica
     * sobre `start_date`/`end_date`, válida por el formato ISO). Para asignar la
     * quincena de un gasto PLANNED manual en una fecha arbitraria (Fase 4 inc. 2b);
     * el caller cae a la quincena activa si no hay match.
     */
    @Query(
        """
        SELECT * FROM quincena
        WHERE household_id = :householdId
          AND start_date <= :isoDate AND end_date >= :isoDate
        LIMIT 1
        """
    )
    suspend fun getForDate(householdId: String, isoDate: String): QuincenaEntity?

    @Query(
        """
        SELECT * FROM quincena
        WHERE household_id = :householdId
        ORDER BY year DESC, month DESC, half DESC
        """
    )
    fun observeAll(householdId: String): Flow<List<QuincenaEntity>>

    @Query(
        """
        SELECT
            id                       AS quincenaId,
            label                    AS label,
            start_date               AS startDate,
            end_date                 AS endDate,
            projected_income_mxn     AS projectedIncomeMxn,
            projected_expenses_mxn   AS projectedExpensesMxn,
            actual_income_mxn        AS actualIncomeMxn,
            actual_expenses_mxn      AS actualExpensesMxn,
            (actual_income_mxn - actual_expenses_mxn) AS savingsMxn
        FROM quincena
        WHERE household_id = :householdId AND status = 'CLOSED'
        ORDER BY year DESC, month DESC, half DESC
        LIMIT :n
        """
    )
    fun observeClosedSnapshots(householdId: String, n: Int): Flow<List<QuincenaSnapshot>>

    @Query(
        """
        SELECT
            id                       AS quincenaId,
            label                    AS label,
            start_date               AS startDate,
            end_date                 AS endDate,
            projected_income_mxn     AS projectedIncomeMxn,
            projected_expenses_mxn   AS projectedExpensesMxn,
            actual_income_mxn        AS actualIncomeMxn,
            actual_expenses_mxn      AS actualExpensesMxn,
            (actual_income_mxn - actual_expenses_mxn) AS savingsMxn
        FROM quincena
        WHERE household_id = :householdId AND status = 'CLOSED'
        ORDER BY year DESC, month DESC, half DESC
        LIMIT :n
        """
    )
    suspend fun getClosedSnapshots(householdId: String, n: Int): List<QuincenaSnapshot>

    /**
     * Ultima quincena anterior a [beforeStartDate] que si tiene presupuesto
     * declarado. La usa el rollover para arrastrar el presupuesto a la quincena
     * nueva: sin esto nacia con ingreso y gasto proyectados en cero, y el
     * disponible del dashboard salia en negativo desde el primer dia del mes.
     *
     * Solo lectura: NO altera el esquema.
     */
    @Query(
        """
        SELECT * FROM quincena
        WHERE household_id = :householdId
          AND start_date < :beforeStartDate
          AND (projected_income_mxn > 0 OR projected_expenses_mxn > 0)
        ORDER BY start_date DESC
        LIMIT 1
        """
    )
    suspend fun getLatestWithBudget(householdId: String, beforeStartDate: String): QuincenaEntity?

    @Query("UPDATE quincena SET status = :status WHERE id = :quincenaId")
    suspend fun updateStatus(quincenaId: String, status: String)

    /**
     * Recalcula el snapshot `actual_expenses_mxn` de una quincena desde los gastos
     * POSTED (el snapshot es persistido y nadie lo recalcula en runtime; el seed
     * histórico lo actualiza tras insertar/borrar gastos). Tarea 4 estados v2.
     */
    @Query(
        """
        UPDATE quincena
        SET actual_expenses_mxn = (
                SELECT COALESCE(SUM(amount_mxn), 0) FROM expense
                WHERE quincena_id = :quincenaId AND status = 'POSTED'
            ),
            updated_at = :now
        WHERE id = :quincenaId
        """
    )
    suspend fun recalcActualExpenses(quincenaId: String, now: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(quincena: QuincenaEntity)

    /**
     * Upsert idempotente usado EXCLUSIVAMENTE por el pull (Firestore → Room).
     * No encola en `sync_queue`. Política de conflictos: REPLACE (last-write-wins).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(quincena: QuincenaEntity)

    @Update
    suspend fun update(quincena: QuincenaEntity)

    /** Borrado por id usado EXCLUSIVAMENTE por el pull (lápida o removal remoto). */
    @Query("DELETE FROM quincena WHERE id = :id")
    suspend fun deleteById(id: String)
}
