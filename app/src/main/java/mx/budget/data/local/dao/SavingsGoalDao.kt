package mx.budget.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.entity.SavingsGoalEntity

@Dao
interface SavingsGoalDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(goal: SavingsGoalEntity)

    @Update
    suspend fun update(goal: SavingsGoalEntity)

    @Query("SELECT * FROM savings_goal WHERE id = :id")
    suspend fun getById(id: String): SavingsGoalEntity?

    @Query("""
        SELECT * FROM savings_goal
        WHERE household_id = :householdId
        ORDER BY name
    """)
    fun observeAll(householdId: String): Flow<List<SavingsGoalEntity>>

    /**
     * Ahorro acumulado total (KPI del dashboard principal).
     */
    @Query("""
        SELECT COALESCE(SUM(current_mxn), 0.0)
        FROM savings_goal
        WHERE household_id = :householdId
    """)
    fun observeTotalSavings(householdId: String): Flow<Double>
}
