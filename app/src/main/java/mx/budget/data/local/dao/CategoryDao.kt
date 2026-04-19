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

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Update
    suspend fun update(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("SELECT * FROM category WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    /** Todas las categorías raíz (sin padre) del household, ordenadas. */
    @Query("""
        SELECT * FROM category 
        WHERE household_id = :householdId AND parent_id IS NULL 
        ORDER BY sort_order, display_name
    """)
    fun observeRootCategories(householdId: String): Flow<List<CategoryEntity>>

    /** Hijos directos de una categoría padre. */
    @Query("""
        SELECT * FROM category 
        WHERE parent_id = :parentId 
        ORDER BY sort_order, display_name
    """)
    fun observeChildren(parentId: String): Flow<List<CategoryEntity>>

    /** Todas las categorías del household en orden jerárquico plano. */
    @Query("""
        SELECT * FROM category 
        WHERE household_id = :householdId 
        ORDER BY sort_order, code
    """)
    fun observeAll(householdId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category WHERE household_id = :householdId")
    suspend fun getAll(householdId: String): List<CategoryEntity>

    /** Busca por código canónico exacto (ej. "HOUSING.TELEFONO"). */
    @Query("SELECT * FROM category WHERE household_id = :householdId AND code = :code")
    suspend fun getByCode(householdId: String, code: String): CategoryEntity?

    /** Filtra por tipo económico (ej. EXPENSE_FIXED, SAVINGS). */
    @Query("""
        SELECT * FROM category 
        WHERE household_id = :householdId AND kind = :kind 
        ORDER BY sort_order
    """)
    suspend fun getByKind(householdId: String, kind: String): List<CategoryEntity>
}
