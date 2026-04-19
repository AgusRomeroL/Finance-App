package mx.budget.data.repository

import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.entity.SavingsGoalEntity

/**
 * Contrato del repositorio de metas de ahorro.
 */
interface SavingsRepository {

    fun observeAll(householdId: String): Flow<List<SavingsGoalEntity>>

    /** Ahorro acumulado total (KPI del dashboard). */
    fun observeTotalSavings(householdId: String): Flow<Double>

    suspend fun getById(id: String): SavingsGoalEntity?

    suspend fun insert(goal: SavingsGoalEntity)

    suspend fun update(goal: SavingsGoalEntity)
}
