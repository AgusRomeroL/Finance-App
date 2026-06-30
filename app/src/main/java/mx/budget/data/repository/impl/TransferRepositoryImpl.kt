package mx.budget.data.repository.impl

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.BudgetDatabase
import mx.budget.data.local.dao.PaymentMethodDao
import mx.budget.data.local.dao.SyncQueueDao
import mx.budget.data.local.dao.WalletTransferDao
import mx.budget.data.local.entity.SyncQueueEntity
import mx.budget.data.local.entity.WalletTransferEntity
import mx.budget.data.local.result.TransferWithNames
import mx.budget.data.repository.TransferRepository

/**
 * Implementación Room del [TransferRepository]. Reutiliza
 * [PaymentMethodDao.adjustBalance] (Fase 2) para mover los saldos de origen y
 * destino dentro de la misma transacción que persiste la transferencia, y encola
 * en `sync_queue` la transferencia (TRANSFER) y ambos wallets (WALLET) para push.
 */
class TransferRepositoryImpl(
    private val transferDao: WalletTransferDao,
    private val paymentMethodDao: PaymentMethodDao,
    private val syncQueueDao: SyncQueueDao,
    private val db: BudgetDatabase,
) : TransferRepository {

    private val creditKinds = setOf("CREDIT_CARD", "DEPARTMENT_STORE_CARD", "BNPL_INSTALLMENT")

    override fun observeTransfers(householdId: String): Flow<List<TransferWithNames>> =
        transferDao.observeWithNames(householdId)

    override suspend fun recordTransfer(transfer: WalletTransferEntity) {
        db.withTransaction {
            transferDao.insert(transfer)
            applyFlow(transfer.fromPaymentMethodId, transfer.amountMxn, inflow = false)
            applyFlow(transfer.toPaymentMethodId, transfer.amountMxn, inflow = true)
            enqueue("TRANSFER", transfer.id, "UPSERT")
        }
    }

    override suspend fun deleteTransfer(transferId: String) {
        db.withTransaction {
            val t = transferDao.getById(transferId) ?: return@withTransaction
            // Revierte: el origen vuelve a recibir, el destino vuelve a sacar.
            applyFlow(t.fromPaymentMethodId, t.amountMxn, inflow = true)
            applyFlow(t.toPaymentMethodId, t.amountMxn, inflow = false)
            transferDao.delete(t)
            enqueue("TRANSFER", transferId, "DELETE")
        }
    }

    /**
     * Aplica el efecto de un flujo de [amount] sobre [paymentMethodId] y encola el
     * wallet para push. Entrada (inflow): líquido SUBE saldo, crédito BAJA deuda.
     */
    private suspend fun applyFlow(paymentMethodId: String, amount: Double, inflow: Boolean) {
        val kind = paymentMethodDao.getById(paymentMethodId)?.kind ?: return
        val inDelta = if (kind in creditKinds) -amount else amount
        paymentMethodDao.adjustBalance(paymentMethodId, if (inflow) inDelta else -inDelta)
        enqueue("WALLET", paymentMethodId, "UPSERT")
    }

    private suspend fun enqueue(entityType: String, entityId: String, operation: String) {
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = entityType,
                entityId = entityId,
                operation = operation,
                createdAt = System.currentTimeMillis(),
            )
        )
    }
}
