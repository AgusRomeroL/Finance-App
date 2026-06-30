package mx.budget.data.repository

import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.entity.WalletTransferEntity
import mx.budget.data.local.result.TransferWithNames

/**
 * Contrato de transferencias entre wallets (RF-41). Registrar/borrar una
 * transferencia ajusta el saldo de ambas cuentas en una transacción atómica;
 * no genera `Expense` ni afecta la quincena.
 */
interface TransferRepository {

    /** Historial de transferencias del hogar con nombres de cuentas. */
    fun observeTransfers(householdId: String): Flow<List<TransferWithNames>>

    /**
     * Registra una transferencia y mueve el saldo: el origen "saca" (líquido baja
     * / crédito sube deuda) y el destino "recibe" (líquido sube / crédito baja
     * deuda). Para pago de tarjeta, destino = la tarjeta de crédito.
     */
    suspend fun recordTransfer(transfer: WalletTransferEntity)

    /** Borra una transferencia y revierte el ajuste de saldo de ambas cuentas. */
    suspend fun deleteTransfer(transferId: String)
}
