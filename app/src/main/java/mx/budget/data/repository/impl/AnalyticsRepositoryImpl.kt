package mx.budget.data.repository.impl

import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.dao.AnalyticsDao
import mx.budget.data.local.result.InterestByWallet
import mx.budget.data.local.result.SpendByCategory
import mx.budget.data.local.result.WalletBalanceInfo
import mx.budget.data.repository.AnalyticsRepository

/**
 * Implementación Room (solo lectura) del [AnalyticsRepository] — MVP Fase 3.
 * Delegación directa a [AnalyticsDao]; sin outbox (no hay escritura).
 */
class AnalyticsRepositoryImpl(
    private val dao: AnalyticsDao,
) : AnalyticsRepository {

    override fun observeSpendByCategory(
        householdId: String,
        quincenaId: String
    ): Flow<List<SpendByCategory>> = dao.observeSpendByCategory(householdId, quincenaId)

    override suspend fun getSpendByCategory(
        householdId: String,
        quincenaId: String
    ): List<SpendByCategory> = dao.getSpendByCategory(householdId, quincenaId)

    override suspend fun getInterestByWallet(
        householdId: String,
        fromEpoch: Long,
        toEpoch: Long
    ): List<InterestByWallet> = dao.getInterestByWallet(householdId, fromEpoch, toEpoch)

    override suspend fun getAvgSpendByCategoryHistorical(
        householdId: String,
        sinceDate: String,
        nQuincenas: Int
    ): List<SpendByCategory> =
        dao.getAvgSpendByCategoryHistorical(householdId, sinceDate, nQuincenas.coerceAtLeast(1))

    override suspend fun getAvgExpenseOverLastN(householdId: String, n: Int): Double =
        dao.getAvgExpenseOverLastN(householdId, n)

    override suspend fun getDebtConcentration(householdId: String): List<WalletBalanceInfo> =
        dao.getDebtConcentration(householdId)

    override suspend fun getCategoriesOverBudget(
        quincenaId: String,
        thresholdPct: Int
    ): List<SpendByCategory> = dao.getCategoriesOverBudget(quincenaId, thresholdPct)
}
