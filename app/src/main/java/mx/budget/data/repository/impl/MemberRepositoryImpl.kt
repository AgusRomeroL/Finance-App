package mx.budget.data.repository.impl

import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.dao.MemberDao
import mx.budget.data.local.entity.MemberEntity
import mx.budget.data.repository.MemberRepository

class MemberRepositoryImpl(
    private val dao: MemberDao
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

    override suspend fun insert(member: MemberEntity) = dao.insert(member)
    override suspend fun insertAll(members: List<MemberEntity>) = dao.insertAll(members)
    override suspend fun update(member: MemberEntity) = dao.update(member)
    override suspend fun delete(member: MemberEntity) = dao.delete(member)
}
