package mx.budget.data.repository.impl

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.BudgetDatabase
import mx.budget.data.local.dao.MemberDao
import mx.budget.data.local.dao.SyncQueueDao
import mx.budget.data.local.entity.MemberEntity
import mx.budget.data.local.entity.SyncQueueEntity
import mx.budget.data.repository.MemberRepository

/**
 * Implementación Room (fuente de verdad). Desde la Fase B los miembros se
 * escriben localmente (wizard de onboarding, CRUD de maestros): cada escritura
 * estampa `updated_at` (LWW del sync, v14) y encola `MEMBER|UPSERT` en el outbox,
 * mismo patrón que expense/category. El pull NUNCA pasa por aquí (anti-eco):
 * escribe vía `MemberDao.upsert` directo con gate LWW.
 */
class MemberRepositoryImpl(
    private val dao: MemberDao,
    private val syncQueueDao: SyncQueueDao,
    private val db: BudgetDatabase
) : MemberRepository {

    override fun observeActiveMembers(householdId: String): Flow<List<MemberEntity>> =
        dao.observeActiveMembers(householdId)

    override fun observeAllMembers(householdId: String): Flow<List<MemberEntity>> =
        dao.observeAllMembers(householdId)

    override suspend fun getById(id: String): MemberEntity? =
        dao.getById(id)

    override suspend fun getByRole(householdId: String, role: String): List<MemberEntity> =
        dao.getByRole(householdId, role)

    override suspend fun searchByNameOrAlias(householdId: String, query: String): List<MemberEntity> =
        dao.getActiveMembers(householdId).filter { member ->
            member.displayName.contains(query, ignoreCase = true)
        }

    override suspend fun insert(member: MemberEntity) = db.withTransaction {
        dao.insert(member.copy(updatedAt = System.currentTimeMillis()))
        enqueueSync(member.id)
    }

    override suspend fun insertAll(members: List<MemberEntity>) = db.withTransaction {
        val now = System.currentTimeMillis()
        dao.insertAll(members.map { it.copy(updatedAt = now) })
        members.forEach { enqueueSync(it.id) }
    }

    override suspend fun update(member: MemberEntity) = db.withTransaction {
        dao.update(member.copy(updatedAt = System.currentTimeMillis()))
        enqueueSync(member.id)
    }

    override suspend fun delete(member: MemberEntity) = dao.delete(member)

    private suspend fun enqueueSync(memberId: String) {
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = "MEMBER",
                entityId = memberId,
                operation = "UPSERT",
                createdAt = System.currentTimeMillis()
            )
        )
    }
}
