package mx.budget.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.entity.IncomeSourceEntity
import mx.budget.data.local.result.IncomeByMember

@Dao
interface IncomeSourceDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(income: IncomeSourceEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(incomes: List<IncomeSourceEntity>)

    @Update
    suspend fun update(income: IncomeSourceEntity)

    @Query("SELECT * FROM income_source WHERE id = :id")
    suspend fun getById(id: String): IncomeSourceEntity?

    @Query("""
        SELECT * FROM income_source 
        WHERE quincena_id = :quincenaId 
        ORDER BY expected_date
    """)
    fun observeByQuincena(quincenaId: String): Flow<List<IncomeSourceEntity>>

    /**
     * Ingreso total confirmado (POSTED) de una quincena.
     * Alimenta el balance quincenal: income - expenses = balance.
     */
    @Query("""
        SELECT COALESCE(SUM(amount_mxn), 0.0)
        FROM income_source 
        WHERE quincena_id = :quincenaId AND status = 'POSTED'
    """)
    fun observePostedTotal(quincenaId: String): Flow<Double>

    /**
     * Ingreso total esperado (PLANNED + POSTED) de una quincena.
     */
    @Query("""
        SELECT COALESCE(SUM(amount_mxn), 0.0)
        FROM income_source 
        WHERE quincena_id = :quincenaId
    """)
    fun observeProjectedTotal(quincenaId: String): Flow<Double>

    /**
     * Ingreso por miembro en una quincena.
     */
    @Query("""
        SELECT 
            m.id AS memberId,
            m.display_name AS memberName,
            COALESCE(SUM(i.amount_mxn), 0.0) AS totalMxn,
            i.status
        FROM income_source i
        JOIN member m ON m.id = i.member_id
        WHERE i.quincena_id = :quincenaId
        GROUP BY m.id, i.status
        ORDER BY totalMxn DESC
    """)
    suspend fun getIncomeByMember(quincenaId: String): List<IncomeByMember>

    @Query("UPDATE income_source SET status = 'POSTED' WHERE id = :id")
    suspend fun markAsPosted(id: String)
}
