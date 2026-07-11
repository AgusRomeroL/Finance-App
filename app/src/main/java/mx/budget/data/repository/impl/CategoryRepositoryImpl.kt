package mx.budget.data.repository.impl

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.BudgetDatabase
import mx.budget.data.local.dao.CategoryDao
import mx.budget.data.local.entity.CategoryEntity
import mx.budget.data.local.entity.SyncQueueEntity
import mx.budget.data.repository.CategoryRepository

/**
 * Implementación Room (fuente de verdad). Desde v13 las categorías se escriben
 * localmente (alta inline en captura, edición de color): cada escritura estampa
 * `updated_at` (LWW del sync) y encola `CATEGORY|UPSERT` en el outbox, mismo
 * patrón que expense/payment_method. El pull NUNCA pasa por aquí (anti-eco):
 * escribe vía `CategoryDao.upsert` directo.
 */
class CategoryRepositoryImpl(
    private val dao: CategoryDao,
    private val syncQueueDao: mx.budget.data.local.dao.SyncQueueDao,
    private val db: BudgetDatabase
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

    override suspend fun insert(category: CategoryEntity) = db.withTransaction {
        dao.insert(category.copy(updatedAt = System.currentTimeMillis()))
        enqueueSync(category.id)
    }

    override suspend fun insertAll(categories: List<CategoryEntity>) = db.withTransaction {
        val now = System.currentTimeMillis()
        dao.insertAll(categories.map { it.copy(updatedAt = now) })
        categories.forEach { enqueueSync(it.id) }
    }

    /**
     * Idempotente: si el household ya tiene AL MENOS una categoría, no-op absoluto
     * (nunca duplica ni toca los grupos sembrados del Excel). La comprobación del
     * conteo y las inserciones corren en la MISMA transacción para evitar una
     * carrera si dos arranques concurrieran. Cada fila estampa `updated_at` y
     * encola `CATEGORY|UPSERT` (mismo camino que el alta inline de captura).
     */
    override suspend fun seedDefaultsIfEmpty(householdId: String) = db.withTransaction {
        if (dao.countByHousehold(householdId) > 0) return@withTransaction
        val now = System.currentTimeMillis()
        val defaults = mx.budget.data.local.DefaultCategoryCatalog.build(householdId)
        dao.insertAll(defaults.map { it.copy(updatedAt = now) })
        defaults.forEach { enqueueSync(it.id) }
    }

    override suspend fun update(category: CategoryEntity) = db.withTransaction {
        dao.update(category.copy(updatedAt = System.currentTimeMillis()))
        enqueueSync(category.id)
    }

    override suspend fun delete(category: CategoryEntity) = dao.delete(category)

    private suspend fun enqueueSync(categoryId: String) {
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = "CATEGORY",
                entityId = categoryId,
                operation = "UPSERT",
                createdAt = System.currentTimeMillis()
            )
        )
    }
}
