package mx.budget.data.repository

import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.entity.InstallmentPlanEntity
import mx.budget.data.local.result.InstallmentSummary

/**
 * Contrato del repositorio de planes de cuotas.
 *
 * Reemplaza la numeración manual del Excel con un sistema
 * de contador automático y liquidación.
 */
interface InstallmentRepository {

    /** Resúmenes de planes activos con progreso. */
    fun observeActiveSummaries(householdId: String): Flow<List<InstallmentSummary>>

    /** Total comprometido en cuotas pendientes (KPI). */
    fun observeTotalCommitment(householdId: String): Flow<Double>

    fun observeActive(householdId: String): Flow<List<InstallmentPlanEntity>>

    suspend fun getById(id: String): InstallmentPlanEntity?

    suspend fun getActive(householdId: String): List<InstallmentPlanEntity>

    /** Crea un nuevo plan de cuotas. */
    suspend fun insert(plan: InstallmentPlanEntity)

    /** Actualiza datos del plan (monto, tasa, etc.). */
    suspend fun update(plan: InstallmentPlanEntity)

    /**
     * Avanza el contador de cuota.
     * Si se alcanza el total → marca como PAID_OFF automáticamente.
     */
    suspend fun advanceInstallment(planId: String)
}
