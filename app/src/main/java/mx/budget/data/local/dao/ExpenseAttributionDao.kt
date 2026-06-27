package mx.budget.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import mx.budget.data.local.entity.ExpenseAttributionEntity

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
}
