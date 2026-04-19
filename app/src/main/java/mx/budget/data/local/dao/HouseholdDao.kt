package mx.budget.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.entity.HouseholdEntity

@Dao
interface HouseholdDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(household: HouseholdEntity)

    @Update
    suspend fun update(household: HouseholdEntity)

    @Query("SELECT * FROM household WHERE id = :id")
    suspend fun getById(id: String): HouseholdEntity?

    @Query("SELECT * FROM household WHERE id = :id")
    fun observeById(id: String): Flow<HouseholdEntity?>

    @Query("SELECT * FROM household LIMIT 1")
    suspend fun getDefault(): HouseholdEntity?

    @Query("SELECT * FROM household LIMIT 1")
    fun observeDefault(): Flow<HouseholdEntity?>
}
