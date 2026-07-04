package mx.budget.data.repository.impl

import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.dao.SavingsGoalDao
import mx.budget.data.local.entity.SavingsGoalEntity
import mx.budget.data.repository.SavingsRepository

/**
 * Implementación Room del [SavingsRepository] — MVP Fase 3. Local-only por
 * ahora (sin outbox de sync; Fase 3.5 lo añadiría siguiendo el patrón TRANSFER).
 */
class SavingsRepositoryImpl(
    private val dao: SavingsGoalDao,
) : SavingsRepository {

    override fun observeAll(householdId: String): Flow<List<SavingsGoalEntity>> =
        dao.observeAll(householdId)

    override fun observeTotalSavings(householdId: String): Flow<Double> =
        dao.observeTotalSavings(householdId)

    override suspend fun getById(id: String): SavingsGoalEntity? = dao.getById(id)

    override suspend fun insert(goal: SavingsGoalEntity) = dao.insert(goal)

    override suspend fun update(goal: SavingsGoalEntity) = dao.update(goal)
}
