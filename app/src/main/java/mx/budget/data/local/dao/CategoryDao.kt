package mx.budget.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.entity.CategoryEntity

@Dao
interface CategoryDao {

    @Query(
        """
        SELECT * FROM category
        WHERE household_id = :householdId AND parent_id IS NULL
        ORDER BY sort_order ASC
        """
    )
    fun observeRootCategories(householdId: String): Flow<List<CategoryEntity>>

    @Query(
        """
        SELECT * FROM category
        WHERE parent_id = :parentId
        ORDER BY sort_order ASC
        """
    )
    fun observeChildren(parentId: String): Flow<List<CategoryEntity>>

    @Query(
        """
        SELECT * FROM category
        WHERE household_id = :householdId
        ORDER BY sort_order ASC
        """
    )
    fun observeAll(householdId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    /** Conteo de categorías del hogar (guarda del sembrado de defaults). */
    @Query("SELECT COUNT(*) FROM category WHERE household_id = :householdId")
    suspend fun countByHousehold(householdId: String): Int

    /** Todas las categorías del hogar (resolución de categoría en captura bancaria). */
    @Query("SELECT * FROM category WHERE household_id = :householdId ORDER BY sort_order ASC")
    suspend fun getAll(householdId: String): List<CategoryEntity>

    @Query("SELECT * FROM category WHERE household_id = :householdId AND code = :code")
    suspend fun getByCode(householdId: String, code: String): CategoryEntity?

    @Query(
        """
        SELECT * FROM category
        WHERE household_id = :householdId AND kind = :kind
        ORDER BY sort_order ASC
        """
    )
    suspend fun getByKind(householdId: String, kind: String): List<CategoryEntity>

    /**
     * Búsqueda simple por nombre entre hojas (categorías asignables a gastos),
     * para el autocompletado anti-duplicados del alta inline en captura (A3).
     * LIKE es case-insensitive para ASCII; no pliega acentos (limitación
     * conocida y aceptable para nombres cortos).
     */
    @Query(
        """
        SELECT * FROM category
        WHERE household_id = :householdId
          AND parent_id IS NOT NULL
          AND display_name LIKE '%' || :query || '%'
        ORDER BY display_name ASC
        LIMIT :limit
        """
    )
    suspend fun searchLeavesByName(householdId: String, query: String, limit: Int): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    /**
     * Upsert idempotente usado EXCLUSIVAMENTE por el pull (Firestore → Room).
     * No encola en `sync_queue`. Política de conflictos: REPLACE (last-write-wins).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: CategoryEntity)

    @Update
    suspend fun update(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)
}
