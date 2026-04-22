package mx.budget.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import mx.budget.data.local.entity.MemberEntity
import mx.budget.data.repository.MemberRepository

class MemberRepositoryFirestore(
    private val firestore: FirebaseFirestore
) : MemberRepository {

    private fun getCollection(householdId: String) =
        firestore.collection("households").document(householdId).collection("members")

    override fun observeActiveMembers(householdId: String): Flow<List<MemberEntity>> = callbackFlow {
        val listener = getCollection(householdId)
            .whereEqualTo("isActive", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                if (snapshot != null) {
                    trySend(snapshot.documents.mapNotNull { it.toObject(MemberEntity::class.java) })
                }
            }
        awaitClose { listener.remove() }
    }

    override fun observeAllMembers(householdId: String): Flow<List<MemberEntity>> = callbackFlow {
        val listener = getCollection(householdId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                if (snapshot != null) {
                    trySend(snapshot.documents.mapNotNull { it.toObject(MemberEntity::class.java) })
                }
            }
        awaitClose { listener.remove() }
    }

    override suspend fun getById(id: String): MemberEntity? {
        val matches = firestore.collectionGroup("members").whereEqualTo("id", id).get().await()
        return matches.documents.firstOrNull()?.toObject(MemberEntity::class.java)
    }

    override suspend fun getByRole(householdId: String, role: String): List<MemberEntity> {
        val matches = getCollection(householdId).whereEqualTo("role", role).get().await()
        return matches.documents.mapNotNull { it.toObject(MemberEntity::class.java) }
    }

    override suspend fun searchByNameOrAlias(householdId: String, query: String): List<MemberEntity> {
        // Firestore lacks native fuzzy search. For now, fetch all active and filter client-side.
        val matches = getCollection(householdId).whereEqualTo("isActive", true).get().await()
        val members = matches.documents.mapNotNull { it.toObject(MemberEntity::class.java) }
        val lowerQuery = query.lowercase()
        return members.filter { m ->
            m.displayName.lowercase().contains(lowerQuery) || (m.shortAliases.lowercase().contains(lowerQuery) == true)
        }
    }

    override suspend fun insert(member: MemberEntity) {
        getCollection(member.householdId).document(member.id).set(member, SetOptions.merge()).await()
    }

    override suspend fun insertAll(members: List<MemberEntity>) {
        if (members.isEmpty()) return
        val batch = firestore.batch()
        members.forEach { m ->
            val ref = getCollection(m.householdId).document(m.id)
            batch.set(ref, m, SetOptions.merge())
        }
        batch.commit().await()
    }

    override suspend fun update(member: MemberEntity) {
        insert(member)
    }

    override suspend fun delete(member: MemberEntity) {
        getCollection(member.householdId).document(member.id).delete().await()
    }
}
