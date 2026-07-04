package mx.budget.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.entity.LoanEntity

/** DAO de préstamos otorgados por el hogar (MVP Fase 3). Tabla desde schema v1. */
@Dao
interface LoanDao {

    @Query("SELECT * FROM loan WHERE household_id = :householdId ORDER BY issued_at DESC")
    fun observeAll(householdId: String): Flow<List<LoanEntity>>

    @Query("SELECT COALESCE(SUM(remaining_balance_mxn), 0) FROM loan WHERE household_id = :householdId")
    fun observeTotalReceivable(householdId: String): Flow<Double>

    @Query("SELECT * FROM loan WHERE household_id = :householdId AND remaining_balance_mxn > 0 ORDER BY remaining_balance_mxn DESC")
    suspend fun getOutstanding(householdId: String): List<LoanEntity>

    @Query("SELECT * FROM loan WHERE id = :id")
    suspend fun getById(id: String): LoanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(loan: LoanEntity)

    @Update
    suspend fun update(loan: LoanEntity)

    @Delete
    suspend fun delete(loan: LoanEntity)
}
