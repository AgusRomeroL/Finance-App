package mx.budget.data.repository.impl

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.BudgetDatabase
import mx.budget.data.local.dao.IncomeSourceDao
import mx.budget.data.local.dao.PaymentMethodDao
import mx.budget.data.local.dao.SyncQueueDao
import mx.budget.data.local.entity.IncomeSourceEntity
import mx.budget.data.local.entity.SyncQueueEntity
import mx.budget.data.local.result.IncomeByMember
import mx.budget.data.repository.IncomeRepository

/**
 * Implementación Room del [IncomeRepository]. Al **postear** un ingreso con cuenta
 * de depósito (`paymentMethodId`), acredita su saldo reutilizando
 * [PaymentMethodDao.adjustBalance] (Fase 2) con la misma lógica de *inflow* que
 * las transferencias (RF-41): una entrada SUBE el saldo en cuentas líquidas y
 * BAJA la deuda en crédito. Los ingresos sembrados por el ETL no pasan por aquí,
 * así que no se re-acreditan: el saldo se mueve "de hoy hacia adelante".
 */
class IncomeRepositoryImpl(
    private val dao: IncomeSourceDao,
    private val paymentMethodDao: PaymentMethodDao,
    private val syncQueueDao: SyncQueueDao,
    private val db: BudgetDatabase,
) : IncomeRepository {

    private val creditKinds = setOf("CREDIT_CARD", "DEPARTMENT_STORE_CARD", "BNPL_INSTALLMENT")

    override fun observeByQuincena(quincenaId: String): Flow<List<IncomeSourceEntity>> =
        dao.observeByQuincena(quincenaId)

    override fun observePostedTotal(quincenaId: String): Flow<Double> =
        dao.observePostedTotal(quincenaId)

    override fun observeProjectedTotal(quincenaId: String): Flow<Double> =
        dao.observeProjectedTotal(quincenaId)

    override suspend fun getById(id: String): IncomeSourceEntity? = dao.getById(id)

    override suspend fun getIncomeByMember(quincenaId: String): List<IncomeByMember> =
        dao.getIncomeByMember(quincenaId)

    override suspend fun insert(income: IncomeSourceEntity) {
        db.withTransaction {
            dao.insert(income)
            if (income.status == "POSTED") creditWallet(income.paymentMethodId, income.amountMxn, posting = true)
            enqueue("INCOME", income.id, "UPSERT")
        }
    }

    override suspend fun insertAll(incomes: List<IncomeSourceEntity>) =
        dao.insertAll(incomes)

    override suspend fun update(income: IncomeSourceEntity) {
        db.withTransaction {
            // Revierte el efecto del estado anterior y aplica el nuevo (cambios de
            // monto/cuenta/status sin doble conteo).
            val old = dao.getById(income.id)
            if (old != null && old.status == "POSTED") {
                creditWallet(old.paymentMethodId, old.amountMxn, posting = false)
            }
            dao.update(income)
            if (income.status == "POSTED") creditWallet(income.paymentMethodId, income.amountMxn, posting = true)
            enqueue("INCOME", income.id, "UPSERT")
        }
    }

    override suspend fun markAsPosted(incomeId: String) {
        db.withTransaction {
            val income = dao.getById(incomeId) ?: return@withTransaction
            if (income.status == "POSTED") return@withTransaction
            dao.update(income.copy(status = "POSTED"))
            creditWallet(income.paymentMethodId, income.amountMxn, posting = true)
            enqueue("INCOME", incomeId, "UPSERT")
        }
    }

    /**
     * Acredita ([posting]=true) o revierte el ingreso de [amount] sobre [pmId] y
     * encola el wallet para push.
     */
    private suspend fun creditWallet(pmId: String?, amount: Double, posting: Boolean) {
        if (pmId == null) return
        val kind = paymentMethodDao.getById(pmId)?.kind ?: return
        val inDelta = if (kind in creditKinds) -amount else amount  // entrada: líquido sube, crédito baja deuda
        paymentMethodDao.adjustBalance(pmId, if (posting) inDelta else -inDelta)
        enqueue("WALLET", pmId, "UPSERT")
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
