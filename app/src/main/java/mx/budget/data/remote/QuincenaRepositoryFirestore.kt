package mx.budget.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import mx.budget.data.local.entity.QuincenaEntity
import mx.budget.data.local.result.QuincenaSnapshot
import mx.budget.data.repository.QuincenaRepository
import java.util.UUID

class QuincenaRepositoryFirestore(
    private val firestore: FirebaseFirestore
) : QuincenaRepository {

    private fun getCollection(householdId: String) =
        firestore.collection("households").document(householdId).collection("quincenas")

    override fun observeActive(householdId: String): Flow<QuincenaEntity?> = callbackFlow {
        val listener = getCollection(householdId)
            .whereEqualTo("status", "ACTIVE")
            .limit(1)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                if (snapshot != null) {
                    trySend(snapshot.documents.firstOrNull()?.toObject(QuincenaEntity::class.java))
                }
            }
        awaitClose { listener.remove() }
    }

    override suspend fun getActive(householdId: String): QuincenaEntity? {
        val matches = getCollection(householdId).whereEqualTo("status", "ACTIVE").limit(1).get().await()
        return matches.documents.firstOrNull()?.toObject(QuincenaEntity::class.java)
    }

    override suspend fun getById(id: String): QuincenaEntity? {
        val matches = firestore.collectionGroup("quincenas").whereEqualTo("id", id).get().await()
        return matches.documents.firstOrNull()?.toObject(QuincenaEntity::class.java)
    }

    override fun observeAll(householdId: String): Flow<List<QuincenaEntity>> = callbackFlow {
        val listener = getCollection(householdId)
            .orderBy("startDate", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                if (snapshot != null) {
                    trySend(snapshot.documents.mapNotNull { it.toObject(QuincenaEntity::class.java) })
                }
            }
        awaitClose { listener.remove() }
    }

    override fun observeClosedSnapshots(householdId: String, n: Int): Flow<List<QuincenaSnapshot>> = callbackFlow {
        val listener = getCollection(householdId)
            .whereEqualTo("status", "CLOSED")
            .orderBy("startDate", Query.Direction.DESCENDING)
            .limit(n.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                if (snapshot != null) {
                    val snapshots = snapshot.documents.mapNotNull { doc ->
                        val entity = doc.toObject(QuincenaEntity::class.java) ?: return@mapNotNull null
                        QuincenaSnapshot(
                            quincenaId = entity.id,
                            label = entity.label,
                            startDate = entity.startDate,
                            endDate = entity.endDate,
                            projectedIncomeMxn = entity.projectedIncomeMxn,
                            projectedExpensesMxn = entity.projectedExpensesMxn,
                            actualIncomeMxn = entity.actualIncomeMxn,
                            actualExpensesMxn = entity.actualExpensesMxn,
                            savingsMxn = entity.actualIncomeMxn - entity.actualExpensesMxn
                        )
                    }
                    trySend(snapshots)
                }
            }
        awaitClose { listener.remove() }
    }

    override suspend fun getClosedSnapshots(householdId: String, n: Int): List<QuincenaSnapshot> {
        val matches = getCollection(householdId)
            .whereEqualTo("status", "CLOSED")
            .orderBy("startDate", Query.Direction.DESCENDING)
            .limit(n.toLong())
            .get()
            .await()
            
        return matches.documents.mapNotNull { doc ->
            val entity = doc.toObject(QuincenaEntity::class.java) ?: return@mapNotNull null
            QuincenaSnapshot(
                quincenaId = entity.id,
                label = entity.label,
                startDate = entity.startDate,
                endDate = entity.endDate,
                projectedIncomeMxn = entity.projectedIncomeMxn,
                projectedExpensesMxn = entity.projectedExpensesMxn,
                actualIncomeMxn = entity.actualIncomeMxn,
                actualExpensesMxn = entity.actualExpensesMxn,
                savingsMxn = entity.actualIncomeMxn - entity.actualExpensesMxn
            )
        }
    }

    override suspend fun provision(householdId: String, year: Int, month: Int, half: String): String {
        // Logic to provision would normally query recurrence templates.
        // For now, just create the entity as PROVISIONED.
        val id = "q_\${UUID.randomUUID()}"
        val entity = QuincenaEntity(
            id = id,
            householdId = householdId,
            year = year,
            month = month,
            half = half,
            status = "PROVISIONED",
            label = "\$month/\$year \$half",
            startDate = "2026-04-01", // Demo value
            endDate = "2026-04-15" // Demo value
        )
        getCollection(householdId).document(id).set(entity, SetOptions.merge()).await()
        return id
    }

    override suspend fun activate(quincenaId: String) {
        firestore.collectionGroup("quincenas").whereEqualTo("id", quincenaId).get().await()
            .documents.firstOrNull()?.reference?.update("status", "ACTIVE")?.await()
    }

    override suspend fun startClosingReview(quincenaId: String) {
        firestore.collectionGroup("quincenas").whereEqualTo("id", quincenaId).get().await()
            .documents.firstOrNull()?.reference?.update("status", "CLOSING_REVIEW")?.await()
    }

    override suspend fun close(quincenaId: String) {
        firestore.collectionGroup("quincenas").whereEqualTo("id", quincenaId).get().await()
            .documents.firstOrNull()?.reference?.update("status", "CLOSED", "closedAt", System.currentTimeMillis())?.await()
    }

    override suspend fun recalculateActuals(quincenaId: String) {
        // Here we would ideally calculate actuals via an aggregation query or client-side.
        // Firebase Cloud Functions is best for this, but for now we keep it simple.
    }
}
