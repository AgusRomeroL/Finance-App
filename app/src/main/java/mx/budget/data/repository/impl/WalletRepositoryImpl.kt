package mx.budget.data.repository.impl

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.BudgetDatabase
import mx.budget.data.local.dao.PaymentMethodDao
import mx.budget.data.local.dao.SyncQueueDao
import mx.budget.data.local.entity.PaymentMethodEntity
import mx.budget.data.local.entity.SyncQueueEntity
import mx.budget.data.local.result.WalletBalanceInfo
import mx.budget.data.repository.WalletRepository

/**
 * Implementación Room (fuente de verdad) del [WalletRepository].
 *
 * Las altas/ediciones de wallet por el usuario se persisten localmente y encolan
 * una fila en `sync_queue` (entity_type `WALLET`) dentro de la MISMA transacción,
 * igual que [ExpenseRepositoryImpl]. El pull/upsert desde Firestore NO pasa por
 * aquí (usa el DAO directo vía `RemotePullSync`), evitando el bucle push↔pull.
 */
class WalletRepositoryImpl(
    private val dao: PaymentMethodDao,
    private val syncQueueDao: SyncQueueDao,
    private val db: BudgetDatabase,
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

    override suspend fun insert(paymentMethod: PaymentMethodEntity) {
        db.withTransaction {
            dao.insert(paymentMethod.copy(updatedAt = System.currentTimeMillis()))
            enqueueSync(paymentMethod.id)
        }
    }

    override suspend fun insertAll(paymentMethods: List<PaymentMethodEntity>) =
        dao.insertAll(paymentMethods)

    override suspend fun update(paymentMethod: PaymentMethodEntity) {
        db.withTransaction {
            dao.update(paymentMethod.copy(updatedAt = System.currentTimeMillis()))
            enqueueSync(paymentMethod.id)
        }
    }

    override suspend fun reconcileBalance(paymentMethodId: String, newBalance: Double) {
        db.withTransaction {
            dao.updateBalance(paymentMethodId, newBalance)
            enqueueSync(paymentMethodId)
        }
    }

    private suspend fun enqueueSync(walletId: String) {
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = "WALLET",
                entityId = walletId,
                operation = "UPSERT",
                createdAt = System.currentTimeMillis(),
            )
        )
    }
}
