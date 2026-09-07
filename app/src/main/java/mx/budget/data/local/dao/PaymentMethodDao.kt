package mx.budget.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.entity.PaymentMethodEntity
import mx.budget.data.local.result.WalletBalanceDrift
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

    /**
     * Saldo GUARDADO frente al saldo DERIVADO de los movimientos, por cuenta.
     *
     * El guardado se mueve por deltas locales y viaja como snapshot con LWW, asi
     * que puede quedar en un numero que ya no cuadra con la lista de gastos: es
     * la deriva multi-dispositivo. El derivado es `opening_balance_mxn` mas los
     * movimientos POSTERIORES a `balance_anchor_at`, o sea funcion pura del
     * estado de Room; dos dispositivos que ya convergieron en los movimientos
     * calculan el mismo numero y detectan la misma diferencia.
     *
     * El signo sale de la misma regla que usan los repos al mover el saldo: una
     * entrada sube el saldo liquido y baja la deuda revolvente. Por eso el
     * multiplicador es -1 en credito, departamental y BNPL, y +1 en el resto.
     *
     * Se excluye la cartera virtual EXTERNAL ("Pagado por terceros"): sus gastos
     * NO mueven ningun saldo real (`ExpenseRepositoryImpl.applyToWallet` sale
     * antes), asi que compararla daria una divergencia permanente y falsa.
     *
     * Los ingresos se filtran por `created_at` porque `income_source` no tiene
     * fecha de ocurrencia; es una aproximacion aceptable porque el ingreso se
     * captura dentro de su propia quincena. Gastos y transferencias si usan
     * `occurred_at`, que es lo que el usuario entiende por "despues del saldo
     * que declare".
     *
     * Anadir un @Query no cambia el esquema ni el identityHash.
     */
    @Query(
        """
        SELECT
            pm.id                  AS paymentMethodId,
            pm.current_balance_mxn AS storedBalance,
            pm.balance_anchor_at   AS anchorAt,
            pm.opening_balance_mxn + (
                CASE WHEN pm.kind IN ('CREDIT_CARD', 'DEPARTMENT_STORE_CARD', 'BNPL_INSTALLMENT')
                     THEN -1.0 ELSE 1.0 END
            ) * (
                COALESCE((
                    SELECT SUM(i.amount_mxn) FROM income_source i
                    WHERE i.payment_method_id = pm.id AND i.status = 'POSTED'
                      AND i.created_at > pm.balance_anchor_at
                ), 0.0)
                + COALESCE((
                    SELECT SUM(t.amount_mxn) FROM wallet_transfer t
                    WHERE t.to_payment_method_id = pm.id
                      AND t.occurred_at > pm.balance_anchor_at
                ), 0.0)
                - COALESCE((
                    SELECT SUM(e.amount_mxn) FROM expense e
                    WHERE e.payment_method_id = pm.id AND e.status = 'POSTED'
                      AND e.occurred_at > pm.balance_anchor_at
                ), 0.0)
                - COALESCE((
                    SELECT SUM(t.amount_mxn) FROM wallet_transfer t
                    WHERE t.from_payment_method_id = pm.id
                      AND t.occurred_at > pm.balance_anchor_at
                ), 0.0)
            )                      AS derivedBalance
        FROM payment_method pm
        WHERE pm.household_id = :householdId
          AND pm.is_active = 1
          AND pm.kind <> 'EXTERNAL'
        """
    )
    fun observeDerivedBalances(householdId: String): Flow<List<WalletBalanceDrift>>

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
     * Escritura ABSOLUTA del saldo que ademas RE-ANCLA: fija el saldo guardado,
     * lo copia al saldo declarado y mueve la fecha del ancla a [now].
     *
     * Es lo que convierte la conciliacion en una correccion de verdad: tras
     * llamarla el saldo derivado ([observeDerivedBalances]) vuelve a coincidir
     * con el guardado por construccion, porque ya no queda ningun movimiento
     * posterior al ancla. La usan la conciliacion manual de Cuentas y la
     * aplicacion de un estado de cuenta.
     */
    @Query(
        """
        UPDATE payment_method
        SET current_balance_mxn = :newBalance,
            opening_balance_mxn = :newBalance,
            balance_anchor_at   = :now,
            updated_at          = :now
        WHERE id = :paymentMethodId
        """
    )
    suspend fun reanchorBalance(
        paymentMethodId: String,
        newBalance: Double,
        now: Long = System.currentTimeMillis(),
    )

    /**
     * Ajuste relativo y atómico del saldo (Fase 2: saldo guardado+mantenido).
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

    /** Borrado por id usado EXCLUSIVAMENTE por el pull (lápida o removal remoto). */
    @Query("DELETE FROM payment_method WHERE id = :id")
    suspend fun deleteById(id: String)
}
