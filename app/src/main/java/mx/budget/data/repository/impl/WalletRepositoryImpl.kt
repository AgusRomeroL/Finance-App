package mx.budget.data.repository.impl

import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.dao.PaymentMethodDao
import mx.budget.data.local.entity.PaymentMethodEntity
import mx.budget.data.local.result.WalletBalanceInfo
import mx.budget.data.repository.WalletRepository

class WalletRepositoryImpl(
    private val dao: PaymentMethodDao
) : WalletRepository {

    override fun observeBalances(householdId: String): Flow<List<WalletBalanceInfo>> =
        dao.observeBalances(householdId)

    override fun observeActive(householdId: String): Flow<List<PaymentMethodEntity>> =
        dao.observeActive(householdId)

    override fun observeBalance(paymentMethodId: String): Flow<Double?> =
        dao.observeBalance(paymentMethodId)

    override fun observeTotalRevolvingDebt(householdId: String): Flow<Double> =
        dao.observeTotalRevolvingDebt(householdId)

    override suspend fun getById(id: String): PaymentMethodEntity? =
        dao.getById(id)

    override suspend fun getActive(householdId: String): List<PaymentMethodEntity> =
        dao.getActive(householdId)

    override suspend fun getHighUtilizationWallets(
        householdId: String,
        thresholdPct: Double
    ): List<PaymentMethodEntity> =
        dao.getHighUtilizationWallets(householdId, thresholdPct)

    override suspend fun insert(paymentMethod: PaymentMethodEntity) =
        dao.insert(paymentMethod)

    override suspend fun insertAll(paymentMethods: List<PaymentMethodEntity>) =
        dao.insertAll(paymentMethods)

    override suspend fun update(paymentMethod: PaymentMethodEntity) =
        dao.update(paymentMethod)

    override suspend fun reconcileBalance(paymentMethodId: String, newBalance: Double) =
        dao.updateBalance(paymentMethodId, newBalance)
}
