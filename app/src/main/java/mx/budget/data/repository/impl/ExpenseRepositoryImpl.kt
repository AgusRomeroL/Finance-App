package mx.budget.data.repository.impl

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import mx.budget.data.local.dao.ExpenseDao
import mx.budget.data.local.entity.ExpenseAttributionEntity
import mx.budget.data.local.entity.ExpenseEntity
import mx.budget.data.local.result.ExpenseWithDetails
import mx.budget.data.local.result.MemberSpendByCategory
import mx.budget.data.local.result.SpendByMember
import mx.budget.data.local.result.TopExpense
import mx.budget.data.repository.ExpenseRepository

class ExpenseRepositoryImpl(
    private val dao: ExpenseDao
) : ExpenseRepository {

    override fun observeWithDetails(quincenaId: String): Flow<List<ExpenseWithDetails>> =
        dao.observeWithDetails(quincenaId)

    override fun observePostedTotal(quincenaId: String): Flow<Double> =
        dao.observePostedTotal(quincenaId)

    override fun observePlannedTotal(quincenaId: String): Flow<Double> =
        dao.observePlannedTotal(quincenaId)

    override fun observeSpendByMember(quincenaId: String): Flow<List<SpendByMember>> =
        // Derivado de los gastos con detalles — stub observacional
        flow { emit(emptyList()) }

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
        dao.insert(expense)
        // TODO: insertar atribuciones en transacción atómica
    }

    override suspend fun updateWithAttributions(
        expense: ExpenseEntity,
        attributions: List<ExpenseAttributionEntity>
    ) {
        dao.update(expense)
    }

    override suspend fun deleteAndRevertBalance(expenseId: String) {
        val expense = dao.getById(expenseId) ?: return
        dao.delete(expense)
    }

    override suspend fun postPlannedExpense(expenseId: String) {
        val expense = dao.getById(expenseId) ?: return
        dao.update(expense.copy(status = "POSTED"))
    }
}
