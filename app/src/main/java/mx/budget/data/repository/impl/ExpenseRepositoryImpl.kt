package mx.budget.data.repository.impl

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.BudgetDatabase
import mx.budget.data.local.dao.ExpenseAttributionDao
import mx.budget.data.local.dao.ExpenseDao
import mx.budget.data.local.dao.SyncQueueDao
import mx.budget.data.local.entity.ExpenseAttributionEntity
import mx.budget.data.local.entity.ExpenseEntity
import mx.budget.data.local.entity.PaymentMethodEntity
import mx.budget.data.local.entity.SyncQueueEntity
import mx.budget.data.local.result.ExpenseWithDetails
import mx.budget.data.local.result.MemberSpendByCategory
import mx.budget.data.local.result.NettingAttributionRow
import mx.budget.data.local.result.PendingReimbursementByPayer
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

    override fun observePendingReimbursements(householdId: String): Flow<List<ExpenseWithDetails>> =
        dao.observePendingReimbursements(householdId)

    override fun observePendingReimbursementTotals(householdId: String): Flow<List<PendingReimbursementByPayer>> =
        dao.observePendingReimbursementTotals(householdId)

    override fun observeNettingRows(householdId: String): Flow<List<NettingAttributionRow>> =
        attributionDao.observeNettingRows(householdId)

    override suspend fun markNetted(expenseIds: List<String>) {
        if (expenseIds.isEmpty()) return
        db.withTransaction {
            val now = System.currentTimeMillis()
            expenseIds.forEach { id ->
                dao.markNetted(id, now)
                // El estado NETTED viaja en el UPSERT del gasto (FirestoreMappers ya
                // serializa settlementStatus). No se ajusta ningún saldo de wallet.
                enqueueSync(id, "UPSERT")
            }
        }
    }

    override suspend fun getById(id: String): ExpenseEntity? =
        dao.getById(id)

    override suspend fun getAttributions(expenseId: String): List<ExpenseAttributionEntity> =
        attributionDao.getByExpenseId(expenseId)

    override suspend fun getTopExpenses(quincenaId: String, limit: Int): List<TopExpense> =
        dao.getTopExpenses(quincenaId, limit)

    override suspend fun getSpendForMember(quincenaId: String, memberId: String): SpendByMember? =
        db.analyticsDao().getSpendForMember(quincenaId, memberId)

    override suspend fun getSpendByMemberAndCategory(
        fromEpoch: Long,
        toEpoch: Long
    ): List<MemberSpendByCategory> = db.analyticsDao().getSpendByMemberAndCategory(fromEpoch, toEpoch)

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
            dao.insert(expense.copy(updatedAt = System.currentTimeMillis()))
            attributionDao.deleteByExpenseId(expense.id)
            attributionDao.insertAll(attributions)
            if (expense.status == "POSTED") {
                applyToWallet(expense.paymentMethodId, expense.amountMxn, posting = true)
            }
            enqueueSync(expense.id, "UPSERT")
        }
    }

    override suspend fun updateWithAttributions(
        expense: ExpenseEntity,
        attributions: List<ExpenseAttributionEntity>
    ) {
        db.withTransaction {
            // Saldo guardado+mantenido: revierte el efecto del estado anterior y
            // aplica el del nuevo. Cubre cambios de monto, de wallet y de status
            // (POSTED↔PLANNED) sin doble conteo.
            val old = dao.getById(expense.id)
            if (old != null && old.status == "POSTED") {
                applyToWallet(old.paymentMethodId, old.amountMxn, posting = false)
            }
            dao.update(expense.copy(updatedAt = System.currentTimeMillis()))
            attributionDao.deleteByExpenseId(expense.id)
            attributionDao.insertAll(attributions)
            if (expense.status == "POSTED") {
                applyToWallet(expense.paymentMethodId, expense.amountMxn, posting = true)
            }
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
            // Las atribuciones viajan con su gasto: subir el timestamp del gasto
            // para que el LWW remoto no pise este cambio con un doc viejo.
            dao.update(expense.copy(updatedAt = System.currentTimeMillis()))
            enqueueSync(expenseId, "UPSERT")
        }
    }

    override suspend fun deleteAndRevertBalance(expenseId: String) {
        db.withTransaction {
            val expense = dao.getById(expenseId) ?: return@withTransaction
            if (expense.status == "POSTED") {
                applyToWallet(expense.paymentMethodId, expense.amountMxn, posting = false)
            }
            dao.delete(expense)
            enqueueSync(expenseId, "DELETE")
        }
    }

    override suspend fun setLocation(
        expenseId: String,
        latitude: Double?,
        longitude: Double?,
        placeLabel: String?,
        source: String
    ) {
        db.withTransaction {
            val expense = dao.getById(expenseId) ?: return@withTransaction
            dao.update(
                expense.copy(
                    latitude = latitude,
                    longitude = longitude,
                    placeLabel = placeLabel,
                    locationSource = source,
                    updatedAt = System.currentTimeMillis()
                )
            )
            enqueueSync(expenseId, "UPSERT")
        }
    }

    override suspend fun setOccurredAt(expenseId: String, occurredAt: Long) {
        db.withTransaction {
            val expense = dao.getById(expenseId) ?: return@withTransaction
            dao.update(expense.copy(occurredAt = occurredAt, updatedAt = System.currentTimeMillis()))
            enqueueSync(expenseId, "UPSERT")
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

    override suspend fun postPlannedExpense(expenseId: String) =
        confirmPlanned(expenseId, actualAmountMxn = null)

    override suspend fun confirmPlanned(expenseId: String, actualAmountMxn: Double?) {
        db.withTransaction {
            val expense = dao.getById(expenseId) ?: return@withTransaction
            if (expense.status != "PLANNED") return@withTransaction

            val newAmount = actualAmountMxn ?: expense.amountMxn
            dao.update(expense.copy(status = "POSTED", amountMxn = newAmount, updatedAt = System.currentTimeMillis()))

            // Saldo guardado+mantenido: el gasto pasa de PLANNED (sin efecto) a
            // POSTED, así que aplica el efecto del monto real al wallet.
            applyToWallet(expense.paymentMethodId, newAmount, posting = true)

            // Re-escala las atribuciones al monto real (los bps no cambian). insertAll
            // es REPLACE: conserva los ids y solo actualiza el share_amount_mxn.
            if (newAmount != expense.amountMxn) {
                val rescaled = attributionDao.getByExpenseId(expenseId)
                    .map { it.copy(shareAmountMxn = newAmount * it.shareBps / 10_000.0) }
                if (rescaled.isNotEmpty()) attributionDao.insertAll(rescaled)
            }
            enqueueSync(expenseId, "UPSERT")
        }
    }

    // ── Saldo guardado+mantenido (Fase 2) ───────────────────────────────────────
    //
    // El saldo del wallet parte del ancla (opening_balance, Fase 1) y se mueve con
    // cada gasto POSTED nuevo. En débito/efectivo/digital/ahorro el gasto BAJA el
    // saldo; en crédito/departamental/BNPL SUBE la deuda. Los 793 gastos sembrados
    // no pasan por aquí (se insertaron por el asset), así que no afectan el saldo:
    // queda exactamente "del saldo declarado hacia adelante".

    private val creditKinds = setOf("CREDIT_CARD", "DEPARTMENT_STORE_CARD", "BNPL_INSTALLMENT")

    /** Kind del wallet virtual "Pagado por terceros" (Fase B, B3). */
    private val externalKind = "EXTERNAL"

    /**
     * Aplica ([posting]=true) o revierte ([posting]=false) el efecto de un gasto
     * POSTED de [amount] sobre el saldo del wallet [paymentMethodId]. No-op si el
     * wallet no existe o si es el wallet virtual EXTERNAL (un gasto adelantado por un
     * tercero NO mueve ningún saldo real; solo cuenta para presupuesto/beneficiarios).
     */
    private suspend fun applyToWallet(paymentMethodId: String, amount: Double, posting: Boolean) {
        val kind = db.paymentMethodDao().getById(paymentMethodId)?.kind ?: return
        if (kind == externalKind) return  // Fase B: el wallet virtual no afecta saldos reales
        val effect = if (kind in creditKinds) amount else -amount  // crédito sube deuda; líquido baja
        db.paymentMethodDao().adjustBalance(paymentMethodId, if (posting) effect else -effect)
        // El saldo cambió: empuja el wallet a la nube (push-sync). FIFO deduplica: el
        // drain lee el snapshot final del wallet desde Room.
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = "WALLET",
                entityId = paymentMethodId,
                operation = "UPSERT",
                createdAt = System.currentTimeMillis()
            )
        )
    }

    // ── "Alguien más pagó" / reembolsos (Fase B, paquete B3) ────────────────────

    override suspend fun ensureExternalWallet(householdId: String): PaymentMethodEntity {
        val id = "external-$householdId"
        val dao = db.paymentMethodDao()
        dao.getById(id)?.let { return it }
        val wallet = PaymentMethodEntity(
            id = id,
            householdId = householdId,
            displayName = "Pagado por terceros",
            kind = externalKind,
            currentBalanceMxn = 0.0,
            openingBalanceMxn = 0.0,
            isActive = true,
            updatedAt = System.currentTimeMillis(),
        )
        db.withTransaction {
            dao.insert(wallet)
            syncQueueDao.enqueue(
                SyncQueueEntity(
                    entityType = "WALLET",
                    entityId = id,
                    operation = "UPSERT",
                    createdAt = System.currentTimeMillis(),
                )
            )
        }
        return wallet
    }

    override suspend fun reimburseFrom(expenseId: String, walletId: String) {
        db.withTransaction {
            val expense = dao.getById(expenseId) ?: return@withTransaction
            // Revierte el efecto del wallet anterior (si POSTED). Si venía cargado al
            // wallet virtual EXTERNAL, applyToWallet es no-op; si ya estaba en un wallet
            // real, se revierte para no doble-contar al cambiar de cuenta.
            if (expense.status == "POSTED") {
                applyToWallet(expense.paymentMethodId, expense.amountMxn, posting = false)
            }
            val updated = expense.copy(
                paymentMethodId = walletId,
                settlementStatus = "REIMBURSED",
                updatedAt = System.currentTimeMillis(),
            )
            dao.update(updated)
            // Aplica el cargo al wallet real destino (EXTERNAL→real: aquí SÍ mueve saldo).
            if (updated.status == "POSTED") {
                applyToWallet(walletId, updated.amountMxn, posting = true)
            }
            enqueueSync(expenseId, "UPSERT")
        }
    }

    override suspend fun markAbsorbed(expenseId: String) {
        db.withTransaction {
            val expense = dao.getById(expenseId) ?: return@withTransaction
            dao.update(
                expense.copy(
                    settlementStatus = "ABSORBED",
                    updatedAt = System.currentTimeMillis(),
                )
            )
            enqueueSync(expenseId, "UPSERT")
        }
    }
}
