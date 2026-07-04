package mx.budget.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.entity.InstallmentPlanEntity
import mx.budget.data.local.result.InstallmentSummary

/** DAO de planes de cuotas / MSI (MVP Fase 3). Tabla desde schema v1. */
@Dao
interface InstallmentPlanDao {

    @Query(
        """
        SELECT p.id AS planId,
               p.display_name AS displayName,
               p.current_installment AS currentInstallment,
               p.total_installments AS totalInstallments,
               p.installment_amount_mxn AS installmentAmountMxn,
               NULL AS nextDate,
               (p.total_installments - p.current_installment) * p.installment_amount_mxn AS remainingMxn
        FROM installment_plan p
        WHERE p.household_id = :householdId AND p.status = 'ACTIVE'
        ORDER BY p.display_name
        """
    )
    fun observeActiveSummaries(householdId: String): Flow<List<InstallmentSummary>>

    @Query(
        """
        SELECT COALESCE(SUM((total_installments - current_installment) * installment_amount_mxn), 0)
        FROM installment_plan
        WHERE household_id = :householdId AND status = 'ACTIVE'
        """
    )
    fun observeTotalCommitment(householdId: String): Flow<Double>

    @Query("SELECT * FROM installment_plan WHERE household_id = :householdId AND status = 'ACTIVE' ORDER BY display_name")
    fun observeActive(householdId: String): Flow<List<InstallmentPlanEntity>>

    @Query("SELECT * FROM installment_plan WHERE id = :id")
    suspend fun getById(id: String): InstallmentPlanEntity?

    @Query("SELECT * FROM installment_plan WHERE household_id = :householdId AND status = 'ACTIVE' ORDER BY display_name")
    suspend fun getActive(householdId: String): List<InstallmentPlanEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(plan: InstallmentPlanEntity)

    @Update
    suspend fun update(plan: InstallmentPlanEntity)

    /** Borrado por id usado EXCLUSIVAMENTE por el pull (removal remoto). */
    @Query("DELETE FROM installment_plan WHERE id = :id")
    suspend fun deleteById(id: String)
}
