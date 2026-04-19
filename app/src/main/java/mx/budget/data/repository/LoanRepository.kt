package mx.budget.data.repository

import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.entity.LoanEntity

/**
 * Contrato del repositorio de préstamos otorgados por el hogar.
 */
interface LoanRepository {

    fun observeAll(householdId: String): Flow<List<LoanEntity>>

    /** Total por cobrar (KPI). */
    fun observeTotalReceivable(householdId: String): Flow<Double>

    /** Préstamos con saldo pendiente (para intent GET_LOANS_RECEIVABLE). */
    suspend fun getOutstanding(householdId: String): List<LoanEntity>

    suspend fun getById(id: String): LoanEntity?

    suspend fun insert(loan: LoanEntity)

    suspend fun update(loan: LoanEntity)

    suspend fun delete(loan: LoanEntity)

    /** Registra un pago recibido del deudor. */
    suspend fun applyPayment(loanId: String, paymentMxn: Double)
}
