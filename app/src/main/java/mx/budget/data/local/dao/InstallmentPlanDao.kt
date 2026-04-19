package mx.budget.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.entity.InstallmentPlanEntity
import mx.budget.data.local.result.InstallmentSummary

@Dao
interface InstallmentPlanDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(plan: InstallmentPlanEntity)

    @Update
    suspend fun update(plan: InstallmentPlanEntity)

    @Query("SELECT * FROM installment_plan WHERE id = :id")
    suspend fun getById(id: String): InstallmentPlanEntity?

    /**
     * Planes activos con resumen de progreso.
     * Alimenta la pantalla "Cuotas activas" y el tile UpcomingInstallmentsTile.
     */
    @Query("""
        SELECT 
            ip.id AS planId,
            ip.display_name AS displayName,
            ip.current_installment AS currentInstallment,
            ip.total_installments AS totalInstallments,
            ip.installment_amount_mxn AS installmentAmountMxn,
            NULL AS nextDate,
            (ip.total_installments - ip.current_installment) * ip.installment_amount_mxn AS remainingMxn
        FROM installment_plan ip
        WHERE ip.household_id = :householdId AND ip.status = 'ACTIVE'
        ORDER BY remainingMxn DESC
    """)
    fun observeActiveSummaries(householdId: String): Flow<List<InstallmentSummary>>

    @Query("""
        SELECT * FROM installment_plan
        WHERE household_id = :householdId AND status = 'ACTIVE'
    """)
    fun observeActive(householdId: String): Flow<List<InstallmentPlanEntity>>

    @Query("""
        SELECT * FROM installment_plan
        WHERE household_id = :householdId AND status = 'ACTIVE'
    """)
    suspend fun getActive(householdId: String): List<InstallmentPlanEntity>

    /**
     * Avanza el contador de cuota y marca PAID_OFF si se completaron todas.
     */
    @Query("""
        UPDATE installment_plan SET 
            current_installment = current_installment + 1,
            status = CASE 
                WHEN current_installment + 1 >= total_installments THEN 'PAID_OFF' 
                ELSE 'ACTIVE' 
            END
        WHERE id = :id
    """)
    suspend fun advanceInstallment(id: String)

    /**
     * Monto total comprometido en cuotas activas.
     * KPI del dashboard: "Compromisos de cuotas pendientes".
     */
    @Query("""
        SELECT COALESCE(SUM(
            (total_installments - current_installment) * installment_amount_mxn
        ), 0.0)
        FROM installment_plan
        WHERE household_id = :householdId AND status = 'ACTIVE'
    """)
    fun observeTotalCommitment(householdId: String): Flow<Double>
}
