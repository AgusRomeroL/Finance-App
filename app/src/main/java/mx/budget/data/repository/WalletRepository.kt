package mx.budget.data.repository

import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.entity.PaymentMethodEntity
import mx.budget.data.local.result.WalletBalanceInfo

/**
 * Contrato del repositorio de métodos de pago (wallets).
 *
 * Abstrae la conciliación de saldos en tiempo real, reemplazando
 * las columnas N-U del sistema Excel.
 */
interface WalletRepository {

    /** Observa todos los wallets activos con saldos y utilización. */
    fun observeBalances(householdId: String): Flow<List<WalletBalanceInfo>>

    /** Observa wallets activos como entidades completas. */
    fun observeActive(householdId: String): Flow<List<PaymentMethodEntity>>

    /** Saldo de un wallet específico (reactivo). */
    fun observeBalance(paymentMethodId: String): Flow<Double?>

    /** Deuda revolvente total (KPI). */
    fun observeTotalRevolvingDebt(householdId: String): Flow<Double>

    suspend fun getById(id: String): PaymentMethodEntity?

    suspend fun getActive(householdId: String): List<PaymentMethodEntity>

    /** Wallets con utilización de crédito >= umbral (para alertas). */
    suspend fun getHighUtilizationWallets(
        householdId: String,
        thresholdPct: Double = 70.0
    ): List<PaymentMethodEntity>

    suspend fun insert(paymentMethod: PaymentMethodEntity)

    suspend fun insertAll(paymentMethods: List<PaymentMethodEntity>)

    suspend fun update(paymentMethod: PaymentMethodEntity)

    /**
     * Conciliación manual: establece el saldo absoluto de un wallet.
     * Registra la diferencia como ajuste en el log de auditoría.
     */
    suspend fun reconcileBalance(paymentMethodId: String, newBalance: Double)
}
