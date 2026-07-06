package mx.budget.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.entity.SyncQueueEntity

/**
 * DAO del outbox de sincronización.
 */
@Dao
interface SyncQueueDao {

    @Insert
    suspend fun enqueue(row: SyncQueueEntity): Long

    @Query("SELECT * FROM sync_queue ORDER BY created_at ASC")
    suspend fun getPending(): List<SyncQueueEntity>

    /**
     * Conteo reactivo del outbox. Lo observa el [mx.budget.data.sync.SyncManager]
     * para drenar **proactivamente al encolar** (antes solo drenaba al cambiar la
     * conectividad o al arrancar, lo que retrasaba el push si el usuario ya estaba
     * en línea). Emite en cada alta/baja de la cola.
     */
    @Query("SELECT COUNT(*) FROM sync_queue")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE sync_queue SET attempts = attempts + 1 WHERE id = :id")
    suspend fun incrementAttempts(id: Long)
}
