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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(income: IncomeSourceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(incomes: List<IncomeSourceEntity>)

    @Update
    suspend fun update(income: IncomeSourceEntity)

    /** Borrado por id usado EXCLUSIVAMENTE por el pull (removal remoto); no toca saldos. */
    @Query("DELETE FROM income_source WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM income_source WHERE id = :id")
    suspend fun getById(id: String): IncomeSourceEntity?

    @Query(
        """
        SELECT * FROM income_source
        WHERE quincena_id = :quincenaId
        ORDER BY expected_date ASC
        """
    )
    fun observeByQuincena(quincenaId: String): Flow<List<IncomeSourceEntity>>

    @Query(
        """
        SELECT COALESCE(SUM(amount_mxn), 0.0)
        FROM income_source
        WHERE quincena_id = :quincenaId AND status = 'POSTED'
        """
    )
    fun observePostedTotal(quincenaId: String): Flow<Double>

    @Query(
        """
        SELECT COALESCE(SUM(amount_mxn), 0.0)
        FROM income_source
        WHERE quincena_id = :quincenaId
        """
    )
    fun observeProjectedTotal(quincenaId: String): Flow<Double>

    @Query(
        """
        SELECT
            i.member_id      AS memberId,
            m.display_name   AS memberName,
            SUM(i.amount_mxn) AS totalMxn,
            i.status         AS status
        FROM income_source i
        INNER JOIN member m ON m.id = i.member_id
        WHERE i.quincena_id = :quincenaId
        GROUP BY i.member_id, i.status
        ORDER BY totalMxn DESC
        """
    )
    suspend fun getIncomeByMember(quincenaId: String): List<IncomeByMember>
}
