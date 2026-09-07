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
     * Saldo guardado y mantenido. En débito, efectivo, digital y ahorro es el
     * dinero disponible; en crédito, departamental y BNPL es la deuda.
     *
     * NO hay triggers SQL (el comentario que lo afirmaba era falso): lo mueven
     * los repos con `adjustBalance` en cada gasto, ingreso o transferencia, y lo
     * fijan de golpe con `updateBalance` la conciliación manual y la aplicación
     * de un estado de cuenta.
     *
     * Se mantiene por deltas locales pero viaja a Firestore como snapshot con
     * LWW, así que puede derivar entre dispositivos. La deriva no se previene:
     * se hace visible comparando contra [openingBalanceMxn] más los movimientos
     * posteriores a [balanceAnchorAt] (ver `PaymentMethodDao.observeDerivedBalances`).
     */
    @ColumnInfo(name = "current_balance_mxn")
    val currentBalanceMxn: Double = 0.0,

    /**
     * Saldo declarado en el ancla: el punto de partida de la identidad contable
     * `saldo = saldo_inicial + Σ entradas − Σ salidas`, contando solo los
     * movimientos posteriores a [balanceAnchorAt]. Migración v7 a v8.
     *
     * Hasta la Fase 2 era un campo muerto: nadie lo usaba para calcular nada y
     * el formulario de la cuenta lo copiaba encima del saldo actual en cada
     * edición, de modo que renombrar una tarjeta le reseteaba el saldo.
     */
    @ColumnInfo(name = "opening_balance_mxn", defaultValue = "0")
    val openingBalanceMxn: Double = 0.0,

    /**
     * Instante (epoch millis) hasta el cual los movimientos ya están contenidos
     * en [openingBalanceMxn]. Todo gasto, ingreso o transferencia posterior es lo
     * que separa el saldo declarado del saldo actual. Migración v20 a v21.
     *
     * Lo re-estampa cada escritura absoluta del saldo (alta de la cuenta,
     * conciliación manual, aplicación de un estado de cuenta): tras cualquiera de
     * ellas el saldo derivado vuelve a coincidir con el guardado por construcción.
     * En 0 significa "sin ancla declarada" y el saldo derivado no se compara.
     */
    @ColumnInfo(name = "balance_anchor_at", defaultValue = "0")
    val balanceAnchorAt: Long = 0,

    /** Tasa de interés anual si aplica. */
    @ColumnInfo(name = "interest_apr")
    val interestApr: Double? = null,

    /** Miembro propietario de esta cuenta/tarjeta. */
    @ColumnInfo(name = "owner_member_id")
    val ownerMemberId: String? = null,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    /** Última modificación local (epoch millis), para LWW del sync (v10→v11). */
    @ColumnInfo(name = "updated_at", defaultValue = "0")
    val updatedAt: Long = 0
)
