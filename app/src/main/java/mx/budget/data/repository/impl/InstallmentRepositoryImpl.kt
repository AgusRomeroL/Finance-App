package mx.budget.data.repository.impl

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.BudgetDatabase
import mx.budget.data.local.dao.InstallmentPlanDao
import mx.budget.data.local.dao.SyncQueueDao
import mx.budget.data.local.entity.InstallmentPlanEntity
import mx.budget.data.local.entity.SyncQueueEntity
import mx.budget.data.local.result.InstallmentSummary
import mx.budget.data.repository.InstallmentRepository

/**
 * Implementación Room del [InstallmentRepository] — MVP Fase 3.5: cada
 * escritura estampa `updated_at` y encola `INSTALLMENT` en `sync_queue` dentro
 * de la MISMA transacción (patrón TRANSFER). El pull escribe vía DAO directo.
 */
class InstallmentRepositoryImpl(
    private val dao: InstallmentPlanDao,
    private val syncQueueDao: SyncQueueDao,
    private val db: BudgetDatabase,
) : InstallmentRepository {

    override fun observeActiveSummaries(householdId: String): Flow<List<InstallmentSummary>> =
        dao.observeActiveSummaries(householdId)

    override fun observeTotalCommitment(householdId: String): Flow<Double> =
        dao.observeTotalCommitment(householdId)

    override fun observeActive(householdId: String): Flow<List<InstallmentPlanEntity>> =
        dao.observeActive(householdId)

    override suspend fun getById(id: String): InstallmentPlanEntity? = dao.getById(id)

    override suspend fun getActive(householdId: String): List<InstallmentPlanEntity> =
        dao.getActive(householdId)

    override suspend fun insert(plan: InstallmentPlanEntity) {
        db.withTransaction {
            dao.insert(plan.copy(updatedAt = System.currentTimeMillis()))
            enqueue(plan.id)
        }
    }

    override suspend fun update(plan: InstallmentPlanEntity) {
        db.withTransaction {
            dao.update(plan.copy(updatedAt = System.currentTimeMillis()))
            enqueue(plan.id)
        }
    }

    override suspend fun advanceInstallment(planId: String) {
        db.withTransaction {
            val plan = dao.getById(planId) ?: return@withTransaction
            if (plan.status != "ACTIVE") return@withTransaction
            val next = (plan.currentInstallment + 1).coerceAtMost(plan.totalInstallments)
            dao.update(
                plan.copy(
                    currentInstallment = next,
                    status = if (next >= plan.totalInstallments) "PAID_OFF" else plan.status,
                    updatedAt = System.currentTimeMillis(),
                )
            )
            enqueue(planId)
        }
    }

    private suspend fun enqueue(planId: String) {
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = "INSTALLMENT",
                entityId = planId,
                operation = "UPSERT",
                createdAt = System.currentTimeMillis(),
            )
        )
    }
}
