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

    @Query("UPDATE quincena SET status = :status WHERE id = :quincenaId")
    suspend fun updateStatus(quincenaId: String, status: String)

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
}
