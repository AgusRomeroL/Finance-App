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

    @Query(
        """
        SELECT * FROM member
        WHERE household_id = :householdId AND is_active = 1
        ORDER BY display_name ASC
        """
    )
    fun observeActiveMembers(householdId: String): Flow<List<MemberEntity>>

    @Query(
        """
        SELECT * FROM member
        WHERE household_id = :householdId
        ORDER BY display_name ASC
        """
    )
    fun observeAllMembers(householdId: String): Flow<List<MemberEntity>>

    @Query(
        """
        SELECT * FROM member
        WHERE household_id = :householdId AND is_active = 1
        ORDER BY display_name ASC
        """
    )
    suspend fun getActiveMembers(householdId: String): List<MemberEntity>

    @Query("SELECT * FROM member WHERE id = :id")
    suspend fun getById(id: String): MemberEntity?

    @Query(
        """
        SELECT * FROM member
        WHERE household_id = :householdId AND role = :role
        ORDER BY display_name ASC
        """
    )
    suspend fun getByRole(householdId: String, role: String): List<MemberEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(member: MemberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(members: List<MemberEntity>)

    /**
     * Upsert idempotente usado EXCLUSIVAMENTE por el pull (Firestore → Room).
     * No encola en `sync_queue`. Política de conflictos: REPLACE (last-write-wins).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(member: MemberEntity)

    @Update
    suspend fun update(member: MemberEntity)

    @Delete
    suspend fun delete(member: MemberEntity)
}
