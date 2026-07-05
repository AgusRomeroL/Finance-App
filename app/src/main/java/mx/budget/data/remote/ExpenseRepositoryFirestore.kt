package mx.budget.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.tasks.await
import mx.budget.data.local.entity.ExpenseAttributionEntity
import mx.budget.data.local.entity.ExpenseEntity
import mx.budget.data.local.result.ExpenseWithDetails
import mx.budget.data.local.result.MemberSpendByCategory
import mx.budget.data.local.result.SpendByMember
import mx.budget.data.local.result.TopExpense
import mx.budget.data.repository.ExpenseRepository

class ExpenseRepositoryFirestore(
    private val firestore: FirebaseFirestore,
    /**
     * Household activo, para rutas directas (`households/{hh}/expenses/{id}`)
     * en operaciones que solo reciben el id (delete). Si es null se cae al
     * collectionGroup query legado.
     */
    private val householdId: String? = null,
) : ExpenseRepository {

    private fun getCollection(householdId: String) =
        firestore.collection("households").document(householdId).collection("expenses")

    override fun observeWithDetails(quincenaId: String): Flow<List<ExpenseWithDetails>> = callbackFlow {
        // Query to get expenses for the given quincenaId.
        val listener = firestore.collectionGroup("expenses")
            .whereEqualTo("quincenaId", quincenaId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                if (snapshot != null) {
                    val expenses = snapshot.documents.mapNotNull { it.toObject(ExpenseEntity::class.java) }
                    
                    // Simple join pattern (since list is small, we could just fetch categories globally, 
                    // or for now just return the expense details as null to avoid complex nested flows 
                    // until UI is adapted to NoSQL patterns)
                    val details = expenses.map { e ->
                        ExpenseWithDetails(
                            expenseId = e.id,
                            concept = e.concept,
                            amountMxn = e.amountMxn,
                            occurredAt = e.occurredAt,
                            status = e.status,
                            categoryId = e.categoryId,
                            categoryName = "Unknown",
                            categoryCode = "",
                            categoryColorHex = null,
                            paymentMethodName = "Unknown",
                            paymentMethodKind = "UNKNOWN",
                            quincenaLabel = "Q-XX",
                            installmentNumber = e.installmentNumber,
                            installmentTotal = null,
                            notes = e.notes
                        )
                    }
                    trySend(details)
                }
            }
        awaitClose { listener.remove() }
    }

    override fun searchWithDetails(householdId: String, query: String): Flow<List<ExpenseWithDetails>> =
        kotlinx.coroutines.flow.flowOf(emptyList())

    // No-op intencional: la app siempre lee atribuciones de Room; este impl es solo push.
    override suspend fun getAttributions(expenseId: String): List<ExpenseAttributionEntity> = emptyList()

    override fun observePostedTotal(quincenaId: String): Flow<Double> = callbackFlow {
        val listener = firestore.collectionGroup("expenses")
            .whereEqualTo("quincenaId", quincenaId)
            .whereEqualTo("status", "POSTED")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                if (snapshot != null) {
                    val total = snapshot.documents.sumOf { it.toObject(ExpenseEntity::class.java)?.amountMxn ?: 0.0 }
                    trySend(total)
                }
            }
        awaitClose { listener.remove() }
    }

    override fun observePlannedTotal(quincenaId: String): Flow<Double> = callbackFlow {
        val listener = firestore.collectionGroup("expenses")
            .whereEqualTo("quincenaId", quincenaId)
            .whereEqualTo("status", "PLANNED")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                if (snapshot != null) {
                    val total = snapshot.documents.sumOf { it.toObject(ExpenseEntity::class.java)?.amountMxn ?: 0.0 }
                    trySend(total)
                }
            }
        awaitClose { listener.remove() }
    }

    override fun observeSpendByMember(quincenaId: String): Flow<List<SpendByMember>> = callbackFlow {
        val listener = firestore.collectionGroup("expenses")
            .whereEqualTo("quincenaId", quincenaId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                if (snapshot != null) {
                    // Requires querying attributions too. 
                    // In NoSQL we embedded attributions. So if we map to a list we can calculate it.
                    trySend(emptyList()) // Simplified for now
                }
            }
        awaitClose { listener.remove() }
    }

    // El dashboard lee SIEMPRE de Room (fuente de verdad); este lado nube es solo
    // para sync. Stub consistente con observeSpendByMember de arriba.
    override fun observePaidByMember(quincenaId: String): Flow<List<SpendByMember>> = callbackFlow {
        val listener = firestore.collectionGroup("expenses")
            .whereEqualTo("quincenaId", quincenaId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                if (snapshot != null) trySend(emptyList())
            }
        awaitClose { listener.remove() }
    }

    // El dashboard lee SIEMPRE de Room (fuente de verdad); este lado nube es solo
    // para sync. Stub consistente con observeSpendByMember/observePaidByMember.
    override fun observePendingReimbursements(householdId: String): Flow<List<ExpenseWithDetails>> = callbackFlow {
        val listener = firestore.collectionGroup("expenses")
            .whereEqualTo("householdId", householdId)
            .whereEqualTo("settlementStatus", "PENDING_REIMBURSEMENT")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                if (snapshot != null) trySend(emptyList())
            }
        awaitClose { listener.remove() }
    }

    override fun observePendingReimbursementTotals(
        householdId: String
    ): Flow<List<mx.budget.data.local.result.PendingReimbursementByPayer>> = callbackFlow {
        val listener = firestore.collectionGroup("expenses")
            .whereEqualTo("householdId", householdId)
            .whereEqualTo("settlementStatus", "PENDING_REIMBURSEMENT")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                if (snapshot != null) trySend(emptyList())
            }
        awaitClose { listener.remove() }
    }

    // El repo PÚBLICO cableado es la impl Room (fuente de verdad); este método
    // nunca se invoca por la ruta nube. Devuelve la entidad determinista sin
    // persistir, por contrato de la interfaz.
    override suspend fun ensureExternalWallet(
        householdId: String
    ): mx.budget.data.local.entity.PaymentMethodEntity =
        mx.budget.data.local.entity.PaymentMethodEntity(
            id = "external-$householdId",
            householdId = householdId,
            displayName = "Pagado por terceros",
            kind = "EXTERNAL",
            currentBalanceMxn = 0.0,
            openingBalanceMxn = 0.0,
            isActive = true,
            updatedAt = 0L,
        )

    // Settlement (reembolso/absorción) lo resuelve la impl Room (fuente de verdad);
    // el estado resultante llega a la nube por el push del gasto editado. No-op aquí.
    override suspend fun reimburseFrom(expenseId: String, walletId: String) {}

    override suspend fun markAbsorbed(expenseId: String) {}

    override suspend fun getById(id: String): ExpenseEntity? {
        val matches = firestore.collectionGroup("expenses").whereEqualTo("id", id).get().await()
        return matches.documents.firstOrNull()?.toObject(ExpenseEntity::class.java)
    }

    override suspend fun getTopExpenses(quincenaId: String, limit: Int): List<TopExpense> {
        val matches = firestore.collectionGroup("expenses")
            .whereEqualTo("quincenaId", quincenaId)
            .orderBy("amountMxn", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .get().await()
            
        return matches.documents.mapNotNull { doc ->
            val e = doc.toObject(ExpenseEntity::class.java) ?: return@mapNotNull null
            TopExpense(
                expenseId = e.id,
                date = "1970-01-01",
                concept = e.concept,
                amountMxn = e.amountMxn,
                categoryName = "Unknown",
                walletName = "Unknown"
            )
        }
    }

    override suspend fun getSpendForMember(quincenaId: String, memberId: String): SpendByMember? {
        return null
    }

    override suspend fun getSpendByMemberAndCategory(fromEpoch: Long, toEpoch: Long): List<MemberSpendByCategory> {
        return emptyList()
    }

    override suspend fun getByDateRange(householdId: String, fromEpoch: Long, toEpoch: Long): List<ExpenseEntity> {
        val matches = getCollection(householdId)
            .whereGreaterThanOrEqualTo("occurredAt", fromEpoch)
            .whereLessThanOrEqualTo("occurredAt", toEpoch)
            .get().await()
        return matches.documents.mapNotNull { it.toObject(ExpenseEntity::class.java) }
    }

    override suspend fun insertWithAttributions(expense: ExpenseEntity, attributions: List<ExpenseAttributionEntity>) {
        val ref = getCollection(expense.householdId).document(expense.id)
        
        // In NoSQL it is better to embed attributions within the expense or subcollection
        val data = hashMapOf<String, Any>()
        data["expense"] = expense
        data["attributions"] = attributions
        
        ref.set(expense, SetOptions.merge()).await()
        attributions.forEach { attr ->
            ref.collection("attributions").document(attr.id).set(attr).await()
        }
    }

    override suspend fun updateWithAttributions(expense: ExpenseEntity, attributions: List<ExpenseAttributionEntity>) {
        insertWithAttributions(expense, attributions)
    }

    override suspend fun applyAttributionForRole(
        expenseId: String,
        role: String,
        sharesBps: Map<String, Int>
    ) {
        // No-op en el lado nube: el push de sync sube el gasto completo vía
        // insertWithAttributions (drenado por SyncManager), así que esta ruta
        // por-rol nunca se invoca aquí. Existe solo para cumplir el contrato.
    }

    override suspend fun deleteAndRevertBalance(expenseId: String) {
        // Delete remoto fiable (MVP Fase 2e): ruta directa por id (el id del doc
        // ES el id de la entidad) + borrado de la subcolección `attributions` en
        // batch (Firestore NO borra subcolecciones al borrar el padre). Fallback
        // al collectionGroup legado solo si no se conoce el household.
        val ref = if (householdId != null) {
            getCollection(householdId).document(expenseId)
        } else {
            firestore.collectionGroup("expenses").whereEqualTo("id", expenseId).get().await()
                .documents.firstOrNull()?.reference ?: return
        }
        val attribs = ref.collection("attributions").get().await()
        if (!attribs.isEmpty) {
            val batch = firestore.batch()
            attribs.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
        ref.delete().await()
    }

    override suspend fun postPlannedExpense(expenseId: String) {
        val matches = firestore.collectionGroup("expenses").whereEqualTo("id", expenseId).get().await()
        matches.documents.firstOrNull()?.reference?.update("status", "POSTED")?.await()
    }

    override suspend fun confirmPlanned(expenseId: String, actualAmountMxn: Double?) {
        val matches = firestore.collectionGroup("expenses").whereEqualTo("id", expenseId).get().await()
        val ref = matches.documents.firstOrNull()?.reference ?: return
        val updates = mutableMapOf<String, Any>("status" to "POSTED")
        if (actualAmountMxn != null) updates["amountMxn"] = actualAmountMxn
        ref.update(updates).await()
    }

    override suspend fun setLocation(
        expenseId: String,
        latitude: Double?,
        longitude: Double?,
        placeLabel: String?,
        source: String
    ) {
        // El push real lo hace el SyncManager reenviando el ExpenseEntity completo
        // (incluidas las columnas de ubicación). Este método existe para cumplir el
        // contrato; el lado nube se actualiza vía el UPSERT encolado en Room.
    }

    override suspend fun setOccurredAt(expenseId: String, occurredAt: Long) {
        // Igual que setLocation: el cambio viaja en el UPSERT del ExpenseEntity.
    }
}
