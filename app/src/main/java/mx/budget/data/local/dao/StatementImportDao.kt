package mx.budget.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.entity.StatementImportEntity

/**
 * DAO de la auditoría de estados de cuenta importados (Fase C, paquete C1).
 */
@Dao
interface StatementImportDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: StatementImportEntity)

    /** Historial de imports del hogar, más recientes primero. */
    @Query("SELECT * FROM statement_import WHERE household_id = :householdId ORDER BY created_at DESC")
    fun observeAll(householdId: String): Flow<List<StatementImportEntity>>

    @Query("SELECT * FROM statement_import WHERE id = :id")
    suspend fun getById(id: String): StatementImportEntity?

    /** Marca la fila como reconciliada (vincula el wallet elegido y el instante). */
    @Query("UPDATE statement_import SET applied_at = :appliedAt, wallet_id = :walletId WHERE id = :id")
    suspend fun markApplied(id: String, walletId: String?, appliedAt: Long)
}
