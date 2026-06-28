package mx.budget.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.entity.ExpenseAttributionEntity
import mx.budget.data.local.result.SpendByMember

/**
 * DAO de la tabla puente de atribuciones de gasto.
 */
@Dao
interface ExpenseAttributionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<ExpenseAttributionEntity>)

    @Query("DELETE FROM expense_attribution WHERE expense_id = :expenseId")
    suspend fun deleteByExpenseId(expenseId: String)

    @Query("SELECT * FROM expense_attribution WHERE expense_id = :expenseId")
    suspend fun getByExpenseId(expenseId: String): List<ExpenseAttributionEntity>

    @Query(
        """
        SELECT
            m.id                          AS memberId,
            m.display_name                AS memberName,
            SUM(ea.share_amount_mxn)      AS totalMxn,
            COUNT(DISTINCT ea.expense_id) AS expenseCount
        FROM expense_attribution ea
        INNER JOIN member  m ON m.id  = ea.member_id
        INNER JOIN expense e ON e.id  = ea.expense_id
        WHERE e.quincena_id = :quincenaId
          AND ea.role       = 'BENEFICIARY'
          AND e.status      = 'POSTED'
        GROUP BY ea.member_id, m.display_name
        ORDER BY totalMxn DESC
        """
    )
    fun observeSpendByMember(quincenaId: String): Flow<List<SpendByMember>>
}
