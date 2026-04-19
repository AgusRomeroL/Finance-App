package mx.budget.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.entity.QuincenaEntity
import mx.budget.data.local.result.QuincenaSnapshot

@Dao
interface QuincenaDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(quincena: QuincenaEntity)

    @Update
    suspend fun update(quincena: QuincenaEntity)

    @Query("SELECT * FROM quincena WHERE id = :id")
    suspend fun getById(id: String): QuincenaEntity?

    @Query("SELECT * FROM quincena WHERE id = :id")
    fun observeById(id: String): Flow<QuincenaEntity?>

    /**
     * Quincena activa actual del household.
     * Invariante del sistema: máximo UNA quincena ACTIVE por household.
     */
    @Query("""
        SELECT * FROM quincena 
        WHERE household_id = :householdId AND status = 'ACTIVE' 
        LIMIT 1
    """)
    suspend fun getActive(householdId: String): QuincenaEntity?

    /**
     * Observa la quincena activa. Emite un nuevo valor cuando:
     * - Se activa una nueva quincena.
     * - Se actualizan los montos projectados/actuales.
     * - Se transita a CLOSING_REVIEW o CLOSED.
     */
    @Query("""
        SELECT * FROM quincena 
        WHERE household_id = :householdId AND status = 'ACTIVE' 
        LIMIT 1
    """)
    fun observeActive(householdId: String): Flow<QuincenaEntity?>

    /**
     * Últimas N quincenas cerradas, orden cronológico descendente.
     * Alimenta el módulo D (varianza) y el pronóstico de liquidez.
     */
    @Query("""
        SELECT * FROM quincena 
        WHERE household_id = :householdId AND status = 'CLOSED' 
        ORDER BY start_date DESC 
        LIMIT :n
    """)
    suspend fun getLastNClosed(householdId: String, n: Int): List<QuincenaEntity>

    /**
     * Snapshots resumidos de las últimas N quincenas cerradas.
     * Calcula el ahorro como ingreso_real - gasto_real.
     */
    @Query("""
        SELECT 
            id AS quincenaId,
            label,
            start_date AS startDate,
            end_date AS endDate,
            projected_income_mxn AS projectedIncomeMxn,
            projected_expenses_mxn AS projectedExpensesMxn,
            actual_income_mxn AS actualIncomeMxn,
            actual_expenses_mxn AS actualExpensesMxn,
            (actual_income_mxn - actual_expenses_mxn) AS savingsMxn
        FROM quincena
        WHERE household_id = :householdId AND status = 'CLOSED'
        ORDER BY start_date DESC
        LIMIT :n
    """)
    suspend fun getClosedSnapshots(householdId: String, n: Int): List<QuincenaSnapshot>

    @Query("""
        SELECT 
            id AS quincenaId,
            label,
            start_date AS startDate,
            end_date AS endDate,
            projected_income_mxn AS projectedIncomeMxn,
            projected_expenses_mxn AS projectedExpensesMxn,
            actual_income_mxn AS actualIncomeMxn,
            actual_expenses_mxn AS actualExpensesMxn,
            (actual_income_mxn - actual_expenses_mxn) AS savingsMxn
        FROM quincena
        WHERE household_id = :householdId AND status = 'CLOSED'
        ORDER BY start_date DESC
        LIMIT :n
    """)
    fun observeClosedSnapshots(householdId: String, n: Int): Flow<List<QuincenaSnapshot>>

    /** Histórico completo para la timeline del household (módulo F). */
    @Query("""
        SELECT * FROM quincena 
        WHERE household_id = :householdId 
        ORDER BY start_date DESC
    """)
    fun observeAll(householdId: String): Flow<List<QuincenaEntity>>

    /** Transición de estado de la quincena. */
    @Query("UPDATE quincena SET status = :newStatus WHERE id = :id")
    suspend fun updateStatus(id: String, newStatus: String)

    /** Cierre de quincena: marca status=CLOSED y guarda timestamp. */
    @Query("UPDATE quincena SET status = 'CLOSED', closed_at = :closedAt WHERE id = :id")
    suspend fun close(id: String, closedAt: Long)

    /**
     * Actualiza los totales actuales de la quincena.
     * Se invoca después de insertar/editar/eliminar un expense o income.
     */
    @Query("""
        UPDATE quincena SET 
            actual_expenses_mxn = :actualExpenses,
            actual_income_mxn = :actualIncome
        WHERE id = :id
    """)
    suspend fun updateActuals(id: String, actualExpenses: Double, actualIncome: Double)

    /**
     * Busca una quincena existente por año, mes y mitad.
     * Evita duplicados al provisionar.
     */
    @Query("""
        SELECT * FROM quincena 
        WHERE household_id = :householdId 
          AND year = :year AND month = :month AND half = :half
    """)
    suspend fun findByPeriod(householdId: String, year: Int, month: Int, half: String): QuincenaEntity?
}
