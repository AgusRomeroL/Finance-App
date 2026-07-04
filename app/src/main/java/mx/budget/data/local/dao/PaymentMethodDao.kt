package mx.budget.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.entity.PaymentMethodEntity
import mx.budget.data.local.result.WalletBalanceInfo

@Dao
interface PaymentMethodDao {

    @Query(
        """
        SELECT
            id                  AS paymentMethodId,
            display_name        AS displayName,
            kind                AS kind,
            current_balance_mxn AS balance,
            credit_limit_mxn    AS creditLimit,
            CASE
                WHEN credit_limit_mxn IS NULL OR credit_limit_mxn = 0 THEN NULL
                ELSE (current_balance_mxn / credit_limit_mxn) * 100.0
            END                 AS utilizationPct
        FROM payment_method
        WHERE household_id = :householdId AND is_active = 1
        ORDER BY display_name ASC
        """
    )
    fun observeBalances(householdId: String): Flow<List<WalletBalanceInfo>>

    @Query(
        """
        SELECT * FROM payment_method
        WHERE household_id = :householdId AND is_active = 1
        ORDER BY display_name ASC
        """
    )
    fun observeActive(householdId: String): Flow<List<PaymentMethodEntity>>

    @Query("SELECT current_balance_mxn FROM payment_method WHERE id = :paymentMethodId")
    fun observeBalance(paymentMethodId: String): Flow<Double?>

    @Query(
        """
        SELECT COALESCE(SUM(current_balance_mxn), 0.0)
        FROM payment_method
        WHERE household_id = :householdId
          AND is_active = 1
          AND kind IN ('CREDIT_CARD', 'DEPARTMENT_STORE_CARD')
        """
    )
    fun observeTotalRevolvingDebt(householdId: String): Flow<Double>

    @Query("SELECT * FROM payment_method WHERE id = :id")
    suspend fun getById(id: String): PaymentMethodEntity?

    @Query(
        """
        SELECT * FROM payment_method
        WHERE household_id = :householdId AND is_active = 1
        ORDER BY display_name ASC
        """
    )
    suspend fun getActive(householdId: String): List<PaymentMethodEntity>

    @Query(
        """
        SELECT * FROM payment_method
        WHERE household_id = :householdId
          AND is_active = 1
          AND credit_limit_mxn IS NOT NULL
          AND credit_limit_mxn > 0
          AND (current_balance_mxn / credit_limit_mxn) * 100.0 >= :thresholdPct
        ORDER BY (current_balance_mxn / credit_limit_mxn) DESC
        """
    )
    suspend fun getHighUtilizationWallets(
        householdId: String,
        thresholdPct: Double
    ): List<PaymentMethodEntity>

    @Query("UPDATE payment_method SET current_balance_mxn = :newBalance, updated_at = :now WHERE id = :paymentMethodId")
    suspend fun updateBalance(paymentMethodId: String, newBalance: Double, now: Long = System.currentTimeMillis())

    /**
     * Ajuste relativo y atómico del saldo (Fase 2 — saldo guardado+mantenido).
     * Lo invoca [mx.budget.data.repository.impl.ExpenseRepositoryImpl] al postear,
     * editar, borrar o confirmar un gasto: el saldo parte del ancla declarada y se
     * mueve con cada gasto POSTED nuevo (los 793 sembrados no lo tocan).
     */
    @Query("UPDATE payment_method SET current_balance_mxn = current_balance_mxn + :delta, updated_at = :now WHERE id = :paymentMethodId")
    suspend fun adjustBalance(paymentMethodId: String, delta: Double, now: Long = System.currentTimeMillis())

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(paymentMethod: PaymentMethodEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(paymentMethods: List<PaymentMethodEntity>)

    /**
     * Upsert idempotente usado EXCLUSIVAMENTE por el pull (Firestore → Room).
     * El payment_method es el destino de la subcolección `wallets`.
     * No encola en `sync_queue`. Política de conflictos: REPLACE (last-write-wins).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(paymentMethod: PaymentMethodEntity)

    @Update
    suspend fun update(paymentMethod: PaymentMethodEntity)
}
