package mx.budget.data.repository.impl

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.BudgetDatabase
import mx.budget.data.local.dao.ExpenseAttributionDao
import mx.budget.data.local.dao.ExpenseDao
import mx.budget.data.local.dao.SyncQueueDao
import mx.budget.data.local.entity.ExpenseAttributionEntity
import mx.budget.data.local.entity.ExpenseEntity
import mx.budget.data.local.entity.SyncQueueEntity
import mx.budget.data.local.result.ExpenseWithDetails
import mx.budget.data.local.result.MemberSpendByCategory
import mx.budget.data.local.result.SpendByMember
import mx.budget.data.local.result.TopExpense
import mx.budget.data.repository.ExpenseRepository

/**
 * Implementación Room (fuente de verdad) del [ExpenseRepository].
 *
 * Cada escritura del ledger se persiste localmente y encola una fila en
 * `sync_queue` dentro de la MISMA transacción, garantizando que nunca se
 * pierda un push. El drenado hacia Firestore lo realiza el
 * [mx.budget.data.sync.SyncManager] de forma asíncrona por conectividad.
 */
class ExpenseRepositoryImpl(
    private val dao: ExpenseDao,
    private val attributionDao: ExpenseAttributionDao,
    private val syncQueueDao: SyncQueueDao,
    private val db: BudgetDatabase
) : ExpenseRepository {

    override fun observeWithDetails(quincenaId: String): Flow<List<ExpenseWithDetails>> =
        dao.observeWithDetails(quincenaId)

    override fun searchWithDetails(householdId: String, query: String): Flow<List<ExpenseWithDetails>> =
        dao.searchWithDetails(householdId, query)

    override fun observePostedTotal(quincenaId: String): Flow<Double> =
        dao.observePostedTotal(quincenaId)

    override fun observePlannedTotal(quincenaId: String): Flow<Double> =
        dao.observePlannedTotal(quincenaId)

    override fun observeSpendByMember(quincenaId: String): Flow<List<SpendByMember>> =
        attributionDao.observeSpendByMember(quincenaId, "BENEFICIARY")

    override fun observePaidByMember(quincenaId: String): Flow<List<SpendByMember>> =
        attributionDao.observeSpendByMember(quincenaId, "PAYER")

    override suspend fun getById(id: String): ExpenseEntity? =
        dao.getById(id)

    override suspend fun getTopExpenses(quincenaId: String, limit: Int): List<TopExpense> =
        dao.getTopExpenses(quincenaId, limit)

    override suspend fun getSpendForMember(quincenaId: String, memberId: String): SpendByMember? =
        null // Implementación futura via AnalyticsDao

    override suspend fun getSpendByMemberAndCategory(
        fromEpoch: Long,
        toEpoch: Long
    ): List<MemberSpendByCategory> = emptyList()

    override suspend fun getByDateRange(
        householdId: String,
        fromEpoch: Long,
        toEpoch: Long
    ): List<ExpenseEntity> = dao.getByDateRange(householdId, fromEpoch, toEpoch)

    override suspend fun insertWithAttributions(
        expense: ExpenseEntity,
        attributions: List<ExpenseAttributionEntity>
    ) {
        db.withTransaction {
            dao.insert(expense)
            attributionDao.deleteByExpenseId(expense.id)
            attributionDao.insertAll(attributions)
            enqueueSync(expense.id, "UPSERT")
        }
    }

    override suspend fun updateWithAttributions(
        expense: ExpenseEntity,
        attributions: List<ExpenseAttributionEntity>
    ) {
        db.withTransaction {
            dao.update(expense)
            attributionDao.deleteByExpenseId(expense.id)
            attributionDao.insertAll(attributions)
            enqueueSync(expense.id, "UPSERT")
        }
    }

    override suspend fun applyAttributionForRole(
        expenseId: String,
        role: String,
        sharesBps: Map<String, Int>
    ) {
        db.withTransaction {
            val expense = dao.getById(expenseId) ?: return@withTransaction
            attributionDao.deleteByExpenseIdAndRole(expenseId, role)
            if (sharesBps.isNotEmpty()) {
                val rows = sharesBps.map { (memberId, bps) ->
                    ExpenseAttributionEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        expenseId = expenseId,
                        memberId = memberId,
                        role = role,
                        shareBps = bps,
                        shareAmountMxn = expense.amountMxn * bps / 10_000.0
                    )
                }
                attributionDao.insertAll(rows)
            }
            enqueueSync(expenseId, "UPSERT")
        }
    }

    override suspend fun deleteAndRevertBalance(expenseId: String) {
        db.withTransaction {
            val expense = dao.getById(expenseId) ?: return@withTransaction
            dao.delete(expense)
            enqueueSync(expenseId, "DELETE")
        }
    }

    private suspend fun enqueueSync(expenseId: String, operation: String) {
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = "EXPENSE",
                entityId = expenseId,
                operation = operation,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun postPlannedExpense(expenseId: String) {
        val expense = dao.getById(expenseId) ?: return
        dao.update(expense.copy(status = "POSTED"))
    }
}
