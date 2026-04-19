package mx.budget.data.repository

import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.entity.CategoryEntity

/**
 * Contrato del repositorio de categorías jerárquicas.
 */
interface CategoryRepository {

    /** Categorías raíz (sin padre) del household. */
    fun observeRootCategories(householdId: String): Flow<List<CategoryEntity>>

    /** Hijos directos de una categoría padre. */
    fun observeChildren(parentId: String): Flow<List<CategoryEntity>>

    /** Todas las categorías del household en orden plano. */
    fun observeAll(householdId: String): Flow<List<CategoryEntity>>

    suspend fun getById(id: String): CategoryEntity?

    /** Búsqueda por código canónico (ej. "HOUSING.TELEFONO"). */
    suspend fun getByCode(householdId: String, code: String): CategoryEntity?

    /** Filtra por naturaleza económica (EXPENSE_FIXED, SAVINGS, etc.). */
    suspend fun getByKind(householdId: String, kind: String): List<CategoryEntity>

    suspend fun insert(category: CategoryEntity)

    suspend fun insertAll(categories: List<CategoryEntity>)

    suspend fun update(category: CategoryEntity)

    suspend fun delete(category: CategoryEntity)
}
