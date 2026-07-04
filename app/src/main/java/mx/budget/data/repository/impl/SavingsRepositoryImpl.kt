package mx.budget.data.repository.impl

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.BudgetDatabase
import mx.budget.data.local.dao.SavingsGoalDao
import mx.budget.data.local.dao.SyncQueueDao
import mx.budget.data.local.entity.SavingsGoalEntity
import mx.budget.data.local.entity.SyncQueueEntity
import mx.budget.data.repository.SavingsRepository

/**
 * Implementación Room del [SavingsRepository] — MVP Fase 3.5: cada escritura
 * estampa `updated_at` y encola una fila `SAVINGS` en `sync_queue` dentro de la
 * MISMA transacción (patrón TRANSFER). El pull escribe vía DAO directo.
 */
class SavingsRepositoryImpl(
    private val dao: SavingsGoalDao,
    private val syncQueueDao: SyncQueueDao,
    private val db: BudgetDatabase,
) : SavingsRepository {

    override fun observeAll(householdId: String): Flow<List<SavingsGoalEntity>> =
        dao.observeAll(householdId)

    override fun observeTotalSavings(householdId: String): Flow<Double> =
        dao.observeTotalSavings(householdId)

    override suspend fun getById(id: String): SavingsGoalEntity? = dao.getById(id)

    override suspend fun insert(goal: SavingsGoalEntity) {
        db.withTransaction {
            dao.insert(goal.copy(updatedAt = System.currentTimeMillis()))
            enqueue(goal.id)
        }
    }

    override suspend fun update(goal: SavingsGoalEntity) {
        db.withTransaction {
            dao.update(goal.copy(updatedAt = System.currentTimeMillis()))
            enqueue(goal.id)
        }
    }

    private suspend fun enqueue(goalId: String) {
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = "SAVINGS",
                entityId = goalId,
                operation = "UPSERT",
                createdAt = System.currentTimeMillis(),
            )
        )
    }
}
