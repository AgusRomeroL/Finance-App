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

    /**
     * Filas pendientes de push en orden FIFO, EXCLUYENDO las fallidas
     * definitivas (dead-letter). Una fila con `attempts >= maxAttempts` se
     * considera venenosa (p. ej. doc rechazado por las reglas de Firestore):
     * deja de reintentarse y de bloquear al resto de la cola, pero NO se borra
     * — el dato local sigue intacto en su tabla de origen y la fila queda como
     * evidencia diagnosticable. No hay columna de estado: el criterio
     * dead-letter es el propio contador `attempts`, así el esquema Room no
     * cambia (sin migración).
     */
    @Query("SELECT * FROM sync_queue WHERE attempts < :maxAttempts ORDER BY created_at ASC")
    suspend fun getPending(maxAttempts: Int): List<SyncQueueEntity>

    /**
     * Conteo reactivo del outbox accionable (excluye dead-letter, mismo
     * criterio que [getPending]). Lo observa el
     * [mx.budget.data.sync.SyncManager] para drenar **proactivamente al
     * encolar** (antes solo drenaba al cambiar la conectividad o al arrancar,
     * lo que retrasaba el push si el usuario ya estaba en línea). Emite en
     * cada alta/baja de la cola; al excluir las filas fallidas definitivas se
     * evitan drenados espurios cuando solo queda veneno en la cola.
     */
    @Query("SELECT COUNT(*) FROM sync_queue WHERE attempts < :maxAttempts")
    fun observeCount(maxAttempts: Int): Flow<Int>

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE sync_queue SET attempts = attempts + 1 WHERE id = :id")
    suspend fun incrementAttempts(id: Long)
}
