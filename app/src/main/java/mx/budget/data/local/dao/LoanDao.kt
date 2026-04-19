package mx.budget.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.entity.LoanEntity

@Dao
interface LoanDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(loan: LoanEntity)

    @Update
    suspend fun update(loan: LoanEntity)

    @Delete
    suspend fun delete(loan: LoanEntity)

    @Query("SELECT * FROM loan WHERE id = :id")
    suspend fun getById(id: String): LoanEntity?

    @Query("""
        SELECT * FROM loan 
        WHERE household_id = :householdId 
        ORDER BY remaining_balance_mxn DESC
    """)
    fun observeAll(householdId: String): Flow<List<LoanEntity>>

    /**
     * Préstamos con saldo pendiente (aún deben dinero al hogar).
     * Alimenta el intent GET_LOANS_RECEIVABLE del asistente IA.
     */
    @Query("""
        SELECT * FROM loan 
        WHERE household_id = :householdId AND remaining_balance_mxn > 0
        ORDER BY remaining_balance_mxn DESC
    """)
    suspend fun getOutstanding(householdId: String): List<LoanEntity>

    /**
     * Registra un pago recibido del deudor.
     */
    @Query("""
        UPDATE loan 
        SET remaining_balance_mxn = remaining_balance_mxn - :paymentMxn
        WHERE id = :id
    """)
    suspend fun applyPayment(id: String, paymentMxn: Double)

    /**
     * Total por cobrar (KPI).
     */
    @Query("""
        SELECT COALESCE(SUM(remaining_balance_mxn), 0.0)
        FROM loan
        WHERE household_id = :householdId AND remaining_balance_mxn > 0
    """)
    fun observeTotalReceivable(householdId: String): Flow<Double>
}
