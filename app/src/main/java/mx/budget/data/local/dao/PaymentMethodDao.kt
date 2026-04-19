package mx.budget.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.entity.PaymentMethodEntity
import mx.budget.data.local.result.WalletBalanceInfo

@Dao
interface PaymentMethodDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(paymentMethod: PaymentMethodEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(paymentMethods: List<PaymentMethodEntity>)

    @Update
    suspend fun update(paymentMethod: PaymentMethodEntity)

    @Delete
    suspend fun delete(paymentMethod: PaymentMethodEntity)

    @Query("SELECT * FROM payment_method WHERE id = :id")
    suspend fun getById(id: String): PaymentMethodEntity?

    @Query("""
        SELECT * FROM payment_method 
        WHERE household_id = :householdId AND is_active = 1 
        ORDER BY display_name
    """)
    fun observeActive(householdId: String): Flow<List<PaymentMethodEntity>>

    @Query("""
        SELECT * FROM payment_method 
        WHERE household_id = :householdId AND is_active = 1
    """)
    suspend fun getActive(householdId: String): List<PaymentMethodEntity>

    /**
     * Saldo en tiempo real por wallet con cálculo de utilización de crédito.
     *
     * Para tarjetas de crédito/departamentales, calcula:
     * utilizacion_pct = (current_balance / credit_limit) * 100
     *
     * Alimenta WalletsScreen, WalletBalanceTile, y el contexto RAG.
     */
    @Query("""
        SELECT 
            id AS paymentMethodId,
            display_name AS displayName,
            kind,
            current_balance_mxn AS balance,
            credit_limit_mxn AS creditLimit,
            CASE 
                WHEN credit_limit_mxn IS NOT NULL AND credit_limit_mxn > 0 
                THEN ROUND(100.0 * current_balance_mxn / credit_limit_mxn, 1)
                ELSE NULL 
            END AS utilizationPct
        FROM payment_method
        WHERE household_id = :householdId AND is_active = 1
        ORDER BY 
            CASE kind 
                WHEN 'DEBIT_ACCOUNT' THEN 1
                WHEN 'CREDIT_CARD' THEN 2
                WHEN 'DIGITAL_WALLET' THEN 3
                WHEN 'DEPARTMENT_STORE_CARD' THEN 4
                WHEN 'BNPL_INSTALLMENT' THEN 5
                WHEN 'CASH' THEN 6
                ELSE 7
            END
    """)
    fun observeBalances(householdId: String): Flow<List<WalletBalanceInfo>>

    /**
     * Saldo de un wallet específico.
     * Reactivo: se actualiza cada vez que un Expense POSTED
     * modifica el current_balance_mxn via trigger SQL.
     */
    @Query("SELECT current_balance_mxn FROM payment_method WHERE id = :id")
    fun observeBalance(id: String): Flow<Double?>

    /**
     * Actualiza el saldo de un wallet directamente.
     * Usado por el trigger de inserción de gastos y por conciliación manual.
     */
    @Query("UPDATE payment_method SET current_balance_mxn = :newBalance WHERE id = :id")
    suspend fun updateBalance(id: String, newBalance: Double)

    /**
     * Ajuste incremental de saldo.
     * Para débito/efectivo: decrementa (amount positivo = gasto).
     * Para crédito: incrementa (la deuda sube con cada gasto).
     */
    @Query("""
        UPDATE payment_method 
        SET current_balance_mxn = current_balance_mxn + (
            CASE 
                WHEN kind IN ('CREDIT_CARD', 'DEPARTMENT_STORE_CARD', 'BNPL_INSTALLMENT')
                THEN :amount
                ELSE -:amount
            END
        )
        WHERE id = :id
    """)
    suspend fun adjustBalance(id: String, amount: Double)

    /**
     * Deuda revolvente total: suma de saldos de todas las tarjetas de crédito activas.
     * KPI del dashboard principal.
     */
    @Query("""
        SELECT COALESCE(SUM(current_balance_mxn), 0.0)
        FROM payment_method
        WHERE household_id = :householdId 
          AND is_active = 1
          AND kind IN ('CREDIT_CARD', 'DEPARTMENT_STORE_CARD', 'BNPL_INSTALLMENT')
    """)
    fun observeTotalRevolvingDebt(householdId: String): Flow<Double>

    /**
     * Wallets con utilización >= umbral (alerta de crédito).
     */
    @Query("""
        SELECT * FROM payment_method
        WHERE household_id = :householdId 
          AND is_active = 1
          AND credit_limit_mxn IS NOT NULL 
          AND credit_limit_mxn > 0
          AND (100.0 * current_balance_mxn / credit_limit_mxn) >= :thresholdPct
    """)
    suspend fun getHighUtilizationWallets(householdId: String, thresholdPct: Double = 70.0): List<PaymentMethodEntity>
}
