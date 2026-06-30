package mx.budget.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Transferencia entre wallets (RF-41): mueve dinero de una cuenta a otra **sin
 * contabilizarlo como gasto**. Cubre también el **pago de tarjeta** (transferir
 * de una cuenta líquida a una de crédito reduce su deuda).
 *
 * Al registrarse ajusta el saldo de ambos wallets en la misma transacción
 * (`current_balance_mxn`), igual que el modelo guardado+mantenido de los gastos
 * (Fase 2): el origen "saca" (líquido baja / crédito sube deuda) y el destino
 * "recibe" (líquido sube / crédito baja deuda). No es un `Expense`, así que no
 * afecta los totales de la quincena ni la atribución por miembro.
 */
@Entity(
    tableName = "wallet_transfer",
    foreignKeys = [
        ForeignKey(
            entity = HouseholdEntity::class,
            parentColumns = ["id"],
            childColumns = ["household_id"]
        ),
        ForeignKey(
            entity = PaymentMethodEntity::class,
            parentColumns = ["id"],
            childColumns = ["from_payment_method_id"]
        ),
        ForeignKey(
            entity = PaymentMethodEntity::class,
            parentColumns = ["id"],
            childColumns = ["to_payment_method_id"]
        )
    ],
    indices = [
        Index(value = ["household_id"]),
        Index(value = ["from_payment_method_id"]),
        Index(value = ["to_payment_method_id"])
    ]
)
data class WalletTransferEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "household_id")
    val householdId: String,

    /** Cuenta de origen (de donde sale el dinero). */
    @ColumnInfo(name = "from_payment_method_id")
    val fromPaymentMethodId: String,

    /** Cuenta de destino (a donde llega; si es crédito, paga/reduce deuda). */
    @ColumnInfo(name = "to_payment_method_id")
    val toPaymentMethodId: String,

    @ColumnInfo(name = "amount_mxn")
    val amountMxn: Double,

    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long,

    /** Nota opcional ("Pago tarjeta", "Retiro cajero"). */
    val note: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
