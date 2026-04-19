package mx.budget.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Método de pago: cuentas bancarias, tarjetas, efectivo.
 *
 * Reemplaza las columnas N–U del Excel con un modelo normalizado
 * de conciliación. El [currentBalanceMxn] se actualiza en tiempo real
 * mediante triggers SQL al insertar gastos POSTED.
 *
 * Semilla: Banamex Débito, BBVA, Banamex Clásica, Mercado Pago,
 * Mercado Libre BNPL, Coppel, Liverpool, Sears, Walmart, Klar,
 * Efectivo, Ahorro Empresa.
 */
@Entity(
    tableName = "payment_method",
    foreignKeys = [
        ForeignKey(
            entity = HouseholdEntity::class,
            parentColumns = ["id"],
            childColumns = ["household_id"]
        ),
        ForeignKey(
            entity = MemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["owner_member_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["household_id"]),
        Index(value = ["owner_member_id"])
    ]
)
data class PaymentMethodEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "household_id")
    val householdId: String,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    /**
     * Tipo: DEBIT_ACCOUNT, CREDIT_CARD, DEPARTMENT_STORE_CARD,
     * BNPL_INSTALLMENT, DIGITAL_WALLET, CASH, EMPLOYER_SAVINGS_FUND.
     */
    val kind: String,

    /** Emisor bancario (ej. "Citibanamex", "BBVA México"). */
    val issuer: String? = null,

    /** Últimos 4 dígitos de la tarjeta. */
    val last4: String? = null,

    /** Día del mes del corte (1-31). Solo para tarjetas de crédito. */
    @ColumnInfo(name = "cutoff_day")
    val cutoffDay: Int? = null,

    /** Día del mes de pago (1-31). Solo para tarjetas de crédito. */
    @ColumnInfo(name = "due_day")
    val dueDay: Int? = null,

    /** Límite de crédito. Null para débito/efectivo. */
    @ColumnInfo(name = "credit_limit_mxn")
    val creditLimitMxn: Double? = null,

    /**
     * Saldo actual calculado.
     * - Para débito/efectivo: saldo disponible (disminuye con gastos).
     * - Para crédito: deuda pendiente (aumenta con gastos).
     * Se actualiza via trigger SQL en cada INSERT de expense POSTED.
     */
    @ColumnInfo(name = "current_balance_mxn")
    val currentBalanceMxn: Double = 0.0,

    /** Tasa de interés anual si aplica. */
    @ColumnInfo(name = "interest_apr")
    val interestApr: Double? = null,

    /** Miembro propietario de esta cuenta/tarjeta. */
    @ColumnInfo(name = "owner_member_id")
    val ownerMemberId: String? = null,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true
)
