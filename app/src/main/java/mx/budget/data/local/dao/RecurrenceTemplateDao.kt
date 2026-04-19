package mx.budget.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.entity.RecurrenceTemplateEntity

@Dao
interface RecurrenceTemplateDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(template: RecurrenceTemplateEntity)

    @Update
    suspend fun update(template: RecurrenceTemplateEntity)

    @Delete
    suspend fun delete(template: RecurrenceTemplateEntity)

    @Query("SELECT * FROM recurrence_template WHERE id = :id")
    suspend fun getById(id: String): RecurrenceTemplateEntity?

    @Query("""
        SELECT * FROM recurrence_template 
        WHERE household_id = :householdId AND is_active = 1 
        ORDER BY concept
    """)
    fun observeActive(householdId: String): Flow<List<RecurrenceTemplateEntity>>

    @Query("""
        SELECT * FROM recurrence_template 
        WHERE household_id = :householdId AND is_active = 1
    """)
    suspend fun getActive(householdId: String): List<RecurrenceTemplateEntity>

    /**
     * Plantillas que deben materializarse para una cadencia dada.
     * Usado por el motor de provisión de quincenas.
     */
    @Query("""
        SELECT * FROM recurrence_template 
        WHERE household_id = :householdId 
          AND is_active = 1 
          AND (cadence = :cadence 
               OR cadence = 'QUINCENAL_EVERY' 
               OR cadence = 'MONTHLY_SPECIFIC_HALF')
    """)
    suspend fun getTemplatesForCadence(householdId: String, cadence: String): List<RecurrenceTemplateEntity>

    /**
     * Pausa/reanuda una plantilla sin eliminarla.
     */
    @Query("UPDATE recurrence_template SET is_active = :isActive WHERE id = :id")
    suspend fun setActive(id: String, isActive: Boolean)
}
