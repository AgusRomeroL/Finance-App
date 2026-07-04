package mx.budget.data.repository.impl

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.BudgetDatabase
import mx.budget.data.local.dao.LoanDao
import mx.budget.data.local.dao.SyncQueueDao
import mx.budget.data.local.entity.LoanEntity
import mx.budget.data.local.entity.SyncQueueEntity
import mx.budget.data.repository.LoanRepository

/**
 * Implementación Room del [LoanRepository] — MVP Fase 3.5: cada escritura
 * estampa `updated_at` y encola `LOAN` (UPSERT/DELETE) en `sync_queue` dentro
 * de la MISMA transacción (patrón TRANSFER). El pull escribe vía DAO directo.
 */
class LoanRepositoryImpl(
    private val dao: LoanDao,
    private val syncQueueDao: SyncQueueDao,
    private val db: BudgetDatabase,
) : LoanRepository {

    override fun observeAll(householdId: String): Flow<List<LoanEntity>> =
        dao.observeAll(householdId)

    override fun observeTotalReceivable(householdId: String): Flow<Double> =
        dao.observeTotalReceivable(householdId)

    override suspend fun getOutstanding(householdId: String): List<LoanEntity> =
        dao.getOutstanding(householdId)

    override suspend fun getById(id: String): LoanEntity? = dao.getById(id)

    override suspend fun insert(loan: LoanEntity) {
        db.withTransaction {
            dao.insert(loan.copy(updatedAt = System.currentTimeMillis()))
            enqueue(loan.id, "UPSERT")
        }
    }

    override suspend fun update(loan: LoanEntity) {
        db.withTransaction {
            dao.update(loan.copy(updatedAt = System.currentTimeMillis()))
            enqueue(loan.id, "UPSERT")
        }
    }

    override suspend fun delete(loan: LoanEntity) {
        db.withTransaction {
            dao.delete(loan)
            enqueue(loan.id, "DELETE")
        }
    }

    override suspend fun applyPayment(loanId: String, paymentMxn: Double) {
        db.withTransaction {
            val loan = dao.getById(loanId) ?: return@withTransaction
            val remaining = (loan.remainingBalanceMxn - paymentMxn).coerceAtLeast(0.0)
            dao.update(loan.copy(remainingBalanceMxn = remaining, updatedAt = System.currentTimeMillis()))
            enqueue(loanId, "UPSERT")
        }
    }

    private suspend fun enqueue(loanId: String, operation: String) {
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = "LOAN",
                entityId = loanId,
                operation = operation,
                createdAt = System.currentTimeMillis(),
            )
        )
    }
}
