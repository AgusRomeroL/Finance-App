package mx.budget.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import mx.budget.data.local.entity.CategoryEntity
import mx.budget.data.repository.CategoryRepository

class CategoryRepositoryFirestore(
    private val firestore: FirebaseFirestore
) : CategoryRepository {

    private fun getCollection(householdId: String) = 
        firestore.collection("households").document(householdId).collection("categories")

    override fun observeRootCategories(householdId: String): Flow<List<CategoryEntity>> = callbackFlow {
        val listener = getCollection(householdId)
            .whereEqualTo("parentId", null)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                if (snapshot != null) {
                    val elements = snapshot.documents.mapNotNull { it.toObject(CategoryEntity::class.java) }
                    trySend(elements)
                }
            }
        awaitClose { listener.remove() }
    }

    override fun observeChildren(parentId: String): Flow<List<CategoryEntity>> = callbackFlow {
        // Warning: needs householdId, but interface doesn't have it.
        // Assuming global household for now or we must search across all.
        // In personal finance app, user likely only belongs to one household.
        val listener = firestore.collectionGroup("categories")
            .whereEqualTo("parentId", parentId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                if (snapshot != null) {
                    trySend(snapshot.documents.mapNotNull { it.toObject(CategoryEntity::class.java) })
                }
            }
        awaitClose { listener.remove() }
    }

    override fun observeAll(householdId: String): Flow<List<CategoryEntity>> = callbackFlow {
        val listener = getCollection(householdId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                if (snapshot != null) {
                    trySend(snapshot.documents.mapNotNull { it.toObject(CategoryEntity::class.java) })
                }
            }
        awaitClose { listener.remove() }
    }

    override suspend fun getById(id: String): CategoryEntity? {
        // Needs collectionGroup or household ID
        val matches = firestore.collectionGroup("categories").whereEqualTo("id", id).get().await()
        return matches.documents.firstOrNull()?.toObject(CategoryEntity::class.java)
    }

    override suspend fun getByCode(householdId: String, code: String): CategoryEntity? {
        val matches = getCollection(householdId).whereEqualTo("canonicalCode", code).get().await()
        return matches.documents.firstOrNull()?.toObject(CategoryEntity::class.java)
    }

    override suspend fun getByKind(householdId: String, kind: String): List<CategoryEntity> {
        val matches = getCollection(householdId).whereEqualTo("economicNature", kind).get().await()
        return matches.documents.mapNotNull { it.toObject(CategoryEntity::class.java) }
    }

    override suspend fun insert(category: CategoryEntity) {
        getCollection(category.householdId).document(category.id).set(category, SetOptions.merge()).await()
    }

    override suspend fun insertAll(categories: List<CategoryEntity>) {
        if (categories.isEmpty()) return
        val batch = firestore.batch()
        categories.forEach { cat ->
            val ref = getCollection(cat.householdId).document(cat.id)
            batch.set(ref, cat, SetOptions.merge())
        }
        batch.commit().await()
    }

    override suspend fun update(category: CategoryEntity) {
        insert(category) // Merges in firestore
    }

    /**
     * No-op en el lado nube: el sembrado de defaults es una decisión LOCAL de
     * arranque (offline-first). El push de esas altas ocurre por el outbox/sync,
     * no directamente aquí.
     */
    override suspend fun seedDefaultsIfEmpty(householdId: String) {
        // intencionalmente vacío
    }

    override suspend fun delete(category: CategoryEntity) {
        getCollection(category.householdId).document(category.id).delete().await()
    }
}
