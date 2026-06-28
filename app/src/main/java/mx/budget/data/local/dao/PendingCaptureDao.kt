package mx.budget.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.entity.PendingCaptureEntity

/**
 * DAO de la bandeja unificada de capturas pendientes (Apéndice G.1).
 */
@Dao
interface PendingCaptureDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: PendingCaptureEntity)

    /** Capturas por confirmar, más recientes primero. Alimenta el chip del dashboard. */
    @Query("SELECT * FROM pending_capture WHERE status = 'PENDING' ORDER BY created_at DESC")
    fun observePending(): Flow<List<PendingCaptureEntity>>

    /** Conteo de pendientes (para badge/automatizaciones). */
    @Query("SELECT COUNT(*) FROM pending_capture WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM pending_capture WHERE id = :id")
    suspend fun getById(id: String): PendingCaptureEntity?

    @Query("UPDATE pending_capture SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    /** Limpieza opcional: descarta capturas viejas no resueltas. */
    @Query("DELETE FROM pending_capture WHERE status = 'PENDING' AND created_at < :beforeEpoch")
    suspend fun deleteStalePending(beforeEpoch: Long)
}
