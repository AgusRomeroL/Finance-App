package mx.budget.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import mx.budget.data.local.entity.PaymentMethodEntity
import mx.budget.data.local.result.WalletBalanceInfo
import mx.budget.data.repository.WalletRepository

class WalletRepositoryFirestore(
    private val firestore: FirebaseFirestore
) : WalletRepository {

    private fun getCollection(householdId: String) =
        firestore.collection("households").document(householdId).collection("wallets")

    override fun observeBalances(householdId: String): Flow<List<WalletBalanceInfo>> = callbackFlow {
        val listener = getCollection(householdId)
            .whereEqualTo("isActive", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                if (snapshot != null) {
                    val elements = snapshot.documents.mapNotNull { doc ->
                        val entity = doc.toObject(PaymentMethodEntity::class.java) ?: return@mapNotNull null
                        val limit = entity.creditLimitMxn
                        WalletBalanceInfo(
                            paymentMethodId = entity.id,
                            displayName = entity.displayName,
                            kind = entity.kind,
                            balance = entity.currentBalanceMxn,
                            creditLimit = limit?.takeIf { it > 0 },
                            utilizationPct = if (limit != null && limit > 0) (entity.currentBalanceMxn / limit) * 100 else null
                        )
                    }
                    trySend(elements)
                }
            }
        awaitClose { listener.remove() }
    }

    override fun observeActive(householdId: String): Flow<List<PaymentMethodEntity>> = callbackFlow {
        val listener = getCollection(householdId)
            .whereEqualTo("isActive", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                if (snapshot != null) {
                    trySend(snapshot.documents.mapNotNull { it.toObject(PaymentMethodEntity::class.java) })
                }
            }
        awaitClose { listener.remove() }
    }

    override fun observeBalance(paymentMethodId: String): Flow<Double?> = callbackFlow {
        val listener = firestore.collectionGroup("wallets")
            .whereEqualTo("id", paymentMethodId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                if (snapshot != null) {
                    val balance = snapshot.documents.firstOrNull()?.toObject(PaymentMethodEntity::class.java)?.currentBalanceMxn
                    trySend(balance)
                }
            }
        awaitClose { listener.remove() }
    }

    override fun observeTotalRevolvingDebt(householdId: String): Flow<Double> = callbackFlow {
        val listener = getCollection(householdId)
            .whereEqualTo("kind", "CREDIT") // Assuming CREDIT is revolving
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                if (snapshot != null) {
                    val debt = snapshot.documents.sumOf { 
                        it.toObject(PaymentMethodEntity::class.java)?.currentBalanceMxn ?: 0.0 
                    }
                    trySend(debt)
                }
            }
        awaitClose { listener.remove() }
    }

    override suspend fun getById(id: String): PaymentMethodEntity? {
        // Find across all wallets
        val matches = firestore.collectionGroup("wallets").whereEqualTo("id", id).get().await()
        return matches.documents.firstOrNull()?.toObject(PaymentMethodEntity::class.java)
    }

    override suspend fun getActive(householdId: String): List<PaymentMethodEntity> {
        val matches = getCollection(householdId).whereEqualTo("isActive", true).get().await()
        return matches.documents.mapNotNull { it.toObject(PaymentMethodEntity::class.java) }
    }

    override suspend fun getHighUtilizationWallets(householdId: String, thresholdPct: Double): List<PaymentMethodEntity> {
        val active = getActive(householdId)
        return active.filter { 
            val limit = it.creditLimitMxn
            limit != null && limit > 0 && ((it.currentBalanceMxn / limit) * 100 >= thresholdPct)
        }
    }

    override suspend fun insert(paymentMethod: PaymentMethodEntity) {
        getCollection(paymentMethod.householdId).document(paymentMethod.id).set(paymentMethod, SetOptions.merge()).await()
    }

    override suspend fun insertAll(paymentMethods: List<PaymentMethodEntity>) {
        if (paymentMethods.isEmpty()) return
        val batch = firestore.batch()
        paymentMethods.forEach { p ->
            val ref = getCollection(p.householdId).document(p.id)
            batch.set(ref, p, SetOptions.merge())
        }
        batch.commit().await()
    }

    override suspend fun update(paymentMethod: PaymentMethodEntity) {
        insert(paymentMethod)
    }

    override suspend fun reconcileBalance(paymentMethodId: String, newBalance: Double) {
        val matches = firestore.collectionGroup("wallets").whereEqualTo("id", paymentMethodId).get().await()
        matches.documents.firstOrNull()?.reference?.update("currentBalanceMxn", newBalance)?.await()
    }
}
