package mx.budget.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.entity.SavingsGoalEntity

/** DAO de metas de ahorro (MVP Fase 3). Tabla desde schema v1. */
@Dao
interface SavingsGoalDao {

    @Query("SELECT * FROM savings_goal WHERE household_id = :householdId ORDER BY name")
    fun observeAll(householdId: String): Flow<List<SavingsGoalEntity>>

    @Query("SELECT COALESCE(SUM(current_mxn), 0) FROM savings_goal WHERE household_id = :householdId")
    fun observeTotalSavings(householdId: String): Flow<Double>

    @Query("SELECT * FROM savings_goal WHERE id = :id")
    suspend fun getById(id: String): SavingsGoalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(goal: SavingsGoalEntity)

    @Update
    suspend fun update(goal: SavingsGoalEntity)

    /** Borrado por id usado EXCLUSIVAMENTE por el pull (removal remoto). */
    @Query("DELETE FROM savings_goal WHERE id = :id")
    suspend fun deleteById(id: String)
}
