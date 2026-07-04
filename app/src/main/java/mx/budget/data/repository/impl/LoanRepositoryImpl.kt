package mx.budget.data.repository.impl

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.BudgetDatabase
import mx.budget.data.local.dao.LoanDao
import mx.budget.data.local.entity.LoanEntity
import mx.budget.data.repository.LoanRepository

/**
 * Implementación Room del [LoanRepository] — MVP Fase 3. Local-only por ahora
 * (sin outbox de sync; Fase 3.5 lo añadiría siguiendo el patrón TRANSFER).
 */
class LoanRepositoryImpl(
    private val dao: LoanDao,
    private val db: BudgetDatabase,
) : LoanRepository {

    override fun observeAll(householdId: String): Flow<List<LoanEntity>> =
        dao.observeAll(householdId)

    override fun observeTotalReceivable(householdId: String): Flow<Double> =
        dao.observeTotalReceivable(householdId)

    override suspend fun getOutstanding(householdId: String): List<LoanEntity> =
        dao.getOutstanding(householdId)

    override suspend fun getById(id: String): LoanEntity? = dao.getById(id)

    override suspend fun insert(loan: LoanEntity) = dao.insert(loan)

    override suspend fun update(loan: LoanEntity) = dao.update(loan)

    override suspend fun delete(loan: LoanEntity) = dao.delete(loan)

    override suspend fun applyPayment(loanId: String, paymentMxn: Double) {
        db.withTransaction {
            val loan = dao.getById(loanId) ?: return@withTransaction
            val remaining = (loan.remainingBalanceMxn - paymentMxn).coerceAtLeast(0.0)
            dao.update(loan.copy(remainingBalanceMxn = remaining))
        }
    }
}
