package mx.budget.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.entity.MemberEntity

@Dao
interface MemberDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(member: MemberEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(members: List<MemberEntity>)

    @Update
    suspend fun update(member: MemberEntity)

    @Delete
    suspend fun delete(member: MemberEntity)

    @Query("SELECT * FROM member WHERE id = :id")
    suspend fun getById(id: String): MemberEntity?

    @Query("SELECT * FROM member WHERE household_id = :householdId AND is_active = 1 ORDER BY display_name")
    fun observeActiveMembers(householdId: String): Flow<List<MemberEntity>>

    @Query("SELECT * FROM member WHERE household_id = :householdId ORDER BY display_name")
    fun observeAllMembers(householdId: String): Flow<List<MemberEntity>>

    @Query("SELECT * FROM member WHERE household_id = :householdId AND is_active = 1")
    suspend fun getActiveMembers(householdId: String): List<MemberEntity>

    @Query("SELECT * FROM member WHERE household_id = :householdId AND role = :role AND is_active = 1")
    suspend fun getByRole(householdId: String, role: String): List<MemberEntity>

    /** Busca miembros cuyo nombre o alias contenga la cadena dada (case insensitive). */
    @Query("""
        SELECT * FROM member 
        WHERE household_id = :householdId 
          AND is_active = 1
          AND (LOWER(display_name) LIKE '%' || LOWER(:query) || '%'
               OR LOWER(short_aliases) LIKE '%' || LOWER(:query) || '%')
        ORDER BY display_name
    """)
    suspend fun searchByNameOrAlias(householdId: String, query: String): List<MemberEntity>
}
