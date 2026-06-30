package mx.budget.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import mx.budget.data.local.entity.IncomeSourceEntity
import mx.budget.data.local.result.IncomeByMember
import mx.budget.data.repository.IncomeRepository

/**
 * Lado nube (Firestore) del [IncomeRepository]: solo lo usa el SyncManager para
 * empujar ingresos. Subcolección `households/{householdId}/income_source`.
 * Las lecturas/agregados son no-op (la app SIEMPRE lee de Room).
 */
class IncomeRepositoryFirestore(
    private val firestore: FirebaseFirestore
) : IncomeRepository {

    private fun collection(householdId: String) =
        firestore.collection("households").document(householdId).collection("income_source")

    override fun observeByQuincena(quincenaId: String): Flow<List<IncomeSourceEntity>> = flowOf(emptyList())
    override fun observePostedTotal(quincenaId: String): Flow<Double> = flowOf(0.0)
    override fun observeProjectedTotal(quincenaId: String): Flow<Double> = flowOf(0.0)

    override suspend fun getById(id: String): IncomeSourceEntity? =
        firestore.collectionGroup("income_source")
            .whereEqualTo("id", id).get().await()
            .documents.firstOrNull()?.toObject(IncomeSourceEntity::class.java)

    override suspend fun getIncomeByMember(quincenaId: String): List<IncomeByMember> = emptyList()

    override suspend fun insert(income: IncomeSourceEntity) {
        collection(income.householdId).document(income.id)
            .set(income, SetOptions.merge()).await()
    }

    override suspend fun insertAll(incomes: List<IncomeSourceEntity>) {
        if (incomes.isEmpty()) return
        val batch = firestore.batch()
        incomes.forEach { income ->
            batch.set(collection(income.householdId).document(income.id), income, SetOptions.merge())
        }
        batch.commit().await()
    }

    override suspend fun update(income: IncomeSourceEntity) = insert(income)

    override suspend fun markAsPosted(incomeId: String) {
        firestore.collectionGroup("income_source")
            .whereEqualTo("id", incomeId).get().await()
            .documents.firstOrNull()?.reference?.update("status", "POSTED")?.await()
    }
}
