package mx.budget.data.repository

import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.entity.MemberEntity

/**
 * Contrato del repositorio de miembros del hogar.
 */
interface MemberRepository {

    fun observeActiveMembers(householdId: String): Flow<List<MemberEntity>>

    fun observeAllMembers(householdId: String): Flow<List<MemberEntity>>

    suspend fun getById(id: String): MemberEntity?

    suspend fun getByRole(householdId: String, role: String): List<MemberEntity>

    /** Búsqueda fuzzy por nombre o alias (para el motor de atribución). */
    suspend fun searchByNameOrAlias(householdId: String, query: String): List<MemberEntity>

    suspend fun insert(member: MemberEntity)

    suspend fun insertAll(members: List<MemberEntity>)

    suspend fun update(member: MemberEntity)

    suspend fun delete(member: MemberEntity)
}
