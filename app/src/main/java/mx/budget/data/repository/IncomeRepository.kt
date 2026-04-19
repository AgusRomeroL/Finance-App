package mx.budget.data.repository

import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.entity.IncomeSourceEntity
import mx.budget.data.local.result.IncomeByMember

/**
 * Contrato del repositorio de fuentes de ingreso.
 */
interface IncomeRepository {

    /** Ingresos de una quincena. */
    fun observeByQuincena(quincenaId: String): Flow<List<IncomeSourceEntity>>

    /** Ingreso total confirmado (POSTED) de una quincena. */
    fun observePostedTotal(quincenaId: String): Flow<Double>

    /** Ingreso total esperado (PLANNED + POSTED) de una quincena. */
    fun observeProjectedTotal(quincenaId: String): Flow<Double>

    suspend fun getById(id: String): IncomeSourceEntity?

    /** Ingreso desglosado por miembro. */
    suspend fun getIncomeByMember(quincenaId: String): List<IncomeByMember>

    suspend fun insert(income: IncomeSourceEntity)

    suspend fun insertAll(incomes: List<IncomeSourceEntity>)

    suspend fun update(income: IncomeSourceEntity)

    /** Confirma que el ingreso fue recibido (PLANNED → POSTED). */
    suspend fun markAsPosted(incomeId: String)
}
