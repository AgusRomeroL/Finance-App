package mx.budget.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import mx.budget.data.local.entity.InstallmentPlanEntity
import mx.budget.data.local.result.InstallmentSummary
import mx.budget.data.repository.InstallmentRepository

/**
 * Lado nube (Firestore) del [InstallmentRepository] — MVP Fase 3.5: solo lo usa
 * el SyncManager para empujar planes. Subcolección `households/{hh}/installment_plan`.
 * Lecturas no-op (la app SIEMPRE lee de Room; el pull lo hace RemotePullSync).
 */
class InstallmentRepositoryFirestore(
    private val firestore: FirebaseFirestore,
) : InstallmentRepository {

    private fun collection(householdId: String) =
        firestore.collection("households").document(householdId).collection("installment_plan")

    override fun observeActiveSummaries(householdId: String): Flow<List<InstallmentSummary>> =
        flowOf(emptyList())

    override fun observeTotalCommitment(householdId: String): Flow<Double> = flowOf(0.0)

    override fun observeActive(householdId: String): Flow<List<InstallmentPlanEntity>> =
        flowOf(emptyList())

    override suspend fun getById(id: String): InstallmentPlanEntity? = null

    override suspend fun getActive(householdId: String): List<InstallmentPlanEntity> = emptyList()

    override suspend fun insert(plan: InstallmentPlanEntity) {
        collection(plan.householdId).document(plan.id).set(plan, SetOptions.merge()).await()
    }

    override suspend fun update(plan: InstallmentPlanEntity) = insert(plan)

    override suspend fun advanceInstallment(planId: String) {
        // No-op: el push del UPSERT (documento completo con el contador avanzado)
        // lo hace insert() al drenar el outbox.
    }
}
