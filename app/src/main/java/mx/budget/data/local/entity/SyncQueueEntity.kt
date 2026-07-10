package mx.budget.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Outbox de sincronización (offline-first).
 *
 * Cada escritura local sobre el ledger encola una fila aquí; el
 * [mx.budget.data.sync.SyncManager] la drena hacia Firestore cuando hay
 * conexión. Room es la fuente de verdad: el push es eventual e idempotente.
 *
 * Sin foreign keys a propósito — la cola debe sobrevivir aunque la entidad
 * referenciada cambie, y un DELETE debe poder sincronizarse después de que
 * la fila original ya no exista localmente.
 */
@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Tipo de entidad encolada, p.ej. "EXPENSE". */
    @ColumnInfo(name = "entity_type")
    val entityType: String,

    /** Id de la entidad afectada (PK de la tabla de origen). */
    @ColumnInfo(name = "entity_id")
    val entityId: String,

    /** "UPSERT" o "DELETE". */
    val operation: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    /**
     * Reintentos acumulados. Además de diagnóstico, es el criterio dead-letter:
     * al alcanzar el máximo (ver `SyncManager.MAX_ATTEMPTS`) la fila se
     * considera fallida definitiva y [mx.budget.data.local.dao.SyncQueueDao.getPending]
     * deja de devolverla — así una fila venenosa no bloquea el push del resto.
     */
    val attempts: Int = 0
)
