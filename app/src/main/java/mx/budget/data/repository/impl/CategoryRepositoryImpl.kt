package mx.budget.data.repository.impl

import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.dao.CategoryDao
import mx.budget.data.local.entity.CategoryEntity
import mx.budget.data.repository.CategoryRepository

class CategoryRepositoryImpl(
    private val dao: CategoryDao
) : CategoryRepository {

    override fun observeRootCategories(householdId: String): Flow<List<CategoryEntity>> =
        dao.observeRootCategories(householdId)

    override fun observeChildren(parentId: String): Flow<List<CategoryEntity>> =
        dao.observeChildren(parentId)

    override fun observeAll(householdId: String): Flow<List<CategoryEntity>> =
        dao.observeAll(householdId)

    override suspend fun getById(id: String): CategoryEntity? =
        dao.getById(id)

    override suspend fun getByCode(householdId: String, code: String): CategoryEntity? =
        dao.getByCode(householdId, code)

    override suspend fun getByKind(householdId: String, kind: String): List<CategoryEntity> =
        dao.getByKind(householdId, kind)

    override suspend fun insert(category: CategoryEntity) = dao.insert(category)
    override suspend fun insertAll(categories: List<CategoryEntity>) = dao.insertAll(categories)
    override suspend fun update(category: CategoryEntity) = dao.update(category)
    override suspend fun delete(category: CategoryEntity) = dao.delete(category)
}
