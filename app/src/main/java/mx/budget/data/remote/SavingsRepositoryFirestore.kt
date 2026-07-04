package mx.budget.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import mx.budget.data.local.entity.SavingsGoalEntity
import mx.budget.data.repository.SavingsRepository

/**
 * Lado nube (Firestore) del [SavingsRepository] — MVP Fase 3.5: solo lo usa el
 * SyncManager para empujar metas. Subcolección `households/{hh}/savings_goal`.
 * Lecturas no-op (la app SIEMPRE lee de Room; el pull lo hace RemotePullSync).
 */
class SavingsRepositoryFirestore(
    private val firestore: FirebaseFirestore,
) : SavingsRepository {

    private fun collection(householdId: String) =
        firestore.collection("households").document(householdId).collection("savings_goal")

    override fun observeAll(householdId: String): Flow<List<SavingsGoalEntity>> = flowOf(emptyList())

    override fun observeTotalSavings(householdId: String): Flow<Double> = flowOf(0.0)

    override suspend fun getById(id: String): SavingsGoalEntity? = null

    override suspend fun insert(goal: SavingsGoalEntity) {
        collection(goal.householdId).document(goal.id).set(goal, SetOptions.merge()).await()
    }

    override suspend fun update(goal: SavingsGoalEntity) = insert(goal)
}
