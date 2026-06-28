package mx.budget.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.entity.RecurrenceTemplateEntity

/**
 * DAO de las plantillas de gasto recurrente (Apéndice G.2, Fase 0).
 *
 * La tabla `recurrence_template` existe desde el esquema v1 pero estaba dormida
 * (sin DAO ni repo). Esta es la puerta de entrada: el calendario/recurrencia lee
 * y escribe plantillas; el materializador (Fase 1) consulta las activas por
 * cadencia para crear gastos PLANNED.
 */
@Dao
interface RecurrenceTemplateDao {

    /** Plantillas activas del hogar, ordenadas por próxima fecha esperada. */
    @Query(
        "SELECT * FROM recurrence_template WHERE household_id = :householdId AND is_active = 1 " +
            "ORDER BY next_expected_date IS NULL, next_expected_date"
    )
    fun observeActive(householdId: String): Flow<List<RecurrenceTemplateEntity>>

    @Query("SELECT * FROM recurrence_template WHERE id = :id")
    suspend fun getById(id: String): RecurrenceTemplateEntity?

    /** Plantillas activas que comparten una cadencia (Fase 1: materialización). */
    @Query(
        "SELECT * FROM recurrence_template WHERE household_id = :householdId " +
            "AND is_active = 1 AND cadence = :cadence"
    )
    suspend fun getActiveForCadence(householdId: String, cadence: String): List<RecurrenceTemplateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: RecurrenceTemplateEntity)

    @Update
    suspend fun update(template: RecurrenceTemplateEntity)

    @Delete
    suspend fun delete(template: RecurrenceTemplateEntity)

    /** Pausa/reanuda sin borrar (alimenta `pause`/`resume` del repo). */
    @Query("UPDATE recurrence_template SET is_active = :active WHERE id = :id")
    suspend fun setActive(id: String, active: Boolean)

    /**
     * Upsert idempotente reservado para el pull (Firestore → Room); NO encola en
     * `sync_queue` (anti-eco). Mismo patrón que los demás DAOs sincronizables.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(template: RecurrenceTemplateEntity)
}
