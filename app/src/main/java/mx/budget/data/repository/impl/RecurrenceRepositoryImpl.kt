package mx.budget.data.repository.impl

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.BudgetDatabase
import mx.budget.data.local.dao.RecurrenceTemplateDao
import mx.budget.data.local.dao.SyncQueueDao
import mx.budget.data.local.entity.RecurrenceTemplateEntity
import mx.budget.data.local.entity.SyncQueueEntity
import mx.budget.data.repository.RecurrenceRepository

/**
 * Implementación Room del repositorio de plantillas recurrentes (Apéndice G.2,
 * Fase 0). Es la fuente de verdad local (offline-first).
 *
 * Desde el paquete ANDROID-TEMPLATES (jul-2026) la tabla se sincroniza (el CRUD
 * de plantillas vive también en la web): cada escritura estampa `updated_at`
 * (LWW) y encola una fila `RECURRENCE` en `sync_queue` dentro de la MISMA
 * transacción (patrón SAVINGS/TRANSFER). El pull escribe vía DAO directo
 * (anti-eco), nunca por este repo.
 */
class RecurrenceRepositoryImpl(
    private val dao: RecurrenceTemplateDao,
    private val syncQueueDao: SyncQueueDao,
    private val db: BudgetDatabase,
) : RecurrenceRepository {

    override fun observeActive(householdId: String): Flow<List<RecurrenceTemplateEntity>> =
        dao.observeActive(householdId)

    override fun observeAll(householdId: String): Flow<List<RecurrenceTemplateEntity>> =
        dao.observeAll(householdId)

    override suspend fun getById(id: String): RecurrenceTemplateEntity? =
        dao.getById(id)

    override suspend fun getTemplatesForCadence(
        householdId: String,
        cadence: String,
    ): List<RecurrenceTemplateEntity> =
        dao.getActiveForCadence(householdId, cadence)

    override suspend fun insert(template: RecurrenceTemplateEntity) {
        db.withTransaction {
            dao.insert(template.copy(updatedAt = System.currentTimeMillis()))
            enqueue(template.id, "UPSERT")
        }
    }

    override suspend fun update(template: RecurrenceTemplateEntity) {
        db.withTransaction {
            dao.update(template.copy(updatedAt = System.currentTimeMillis()))
            enqueue(template.id, "UPSERT")
        }
    }

    override suspend fun delete(template: RecurrenceTemplateEntity) {
        db.withTransaction {
            dao.delete(template)
            enqueue(template.id, "DELETE")
        }
    }

    override suspend fun pause(templateId: String) = setActive(templateId, false)

    override suspend fun resume(templateId: String) = setActive(templateId, true)

    /**
     * Pausa/reanuda releyendo la fila para estamparle `updated_at` (el
     * `dao.setActive` a secas no lo bumpearía y el LWW del pull remoto podría
     * revertir el cambio). Id inexistente = no-op.
     */
    private suspend fun setActive(templateId: String, active: Boolean) {
        db.withTransaction {
            val template = dao.getById(templateId) ?: return@withTransaction
            dao.update(template.copy(isActive = active, updatedAt = System.currentTimeMillis()))
            enqueue(templateId, "UPSERT")
        }
    }

    /**
     * Sube las plantillas que solo existen en este teléfono (Fase 8). Las de la
     * semilla nacen con `updated_at = 0` y solo una edición las encolaba, así
     * que la nube tenía 4 de 32 y la web, que prorratea lo reservado y descarta
     * topes según la cadencia de cada plantilla, sacaba otro Disponible que el
     * héroe. Estampa la hora y encola cada una; al quedar con `updated_at > 0`
     * ya no vuelven a salir, así que llamarla en cada arranque es idempotente.
     */
    suspend fun publishNeverSynced(householdId: String): Int = db.withTransaction {
        val pending = dao.getNeverSynced(householdId)
        val now = System.currentTimeMillis()
        pending.forEach { template ->
            dao.update(template.copy(updatedAt = now))
            enqueue(template.id, "UPSERT")
        }
        pending.size
    }

    private suspend fun enqueue(templateId: String, operation: String) {
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = "RECURRENCE",
                entityId = templateId,
                operation = operation,
                createdAt = System.currentTimeMillis(),
            )
        )
    }
}
