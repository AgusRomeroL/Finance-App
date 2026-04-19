package mx.budget.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tabla de intersección para atribución fraccionada de gastos.
 *
 * Implementa el modelo dual de atribución del hogar:
 * - **BENEFICIARY**: quién consume / recibe el bien o servicio.
 * - **PAYER**: quién desembolsa el dinero.
 *
 * Cada expense tiene DOS particiones independientes en esta tabla.
 * Ambas deben sumar exactamente 10,000 basis points (= 100%).
 *
 * Ejemplo — "Seguro Pau, David, Agus" $3,500, Norma paga:
 * ```
 * (expense, Pau,     BENEFICIARY, 3333 bps, $1,166.55)
 * (expense, David,   BENEFICIARY, 3333 bps, $1,166.55)
 * (expense, Agustín, BENEFICIARY, 3334 bps, $1,166.90)
 * (expense, Norma,   PAYER,      10000 bps, $3,500.00)
 * ```
 */
@Entity(
    tableName = "expense_attribution",
    foreignKeys = [
        ForeignKey(
            entity = ExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["expense_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["member_id"]
        )
    ],
    indices = [
        Index(value = ["expense_id", "role"]),
        Index(value = ["member_id", "role"])
    ]
)
data class ExpenseAttributionEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "expense_id")
    val expenseId: String,

    @ColumnInfo(name = "member_id")
    val memberId: String,

    /** "BENEFICIARY" o "PAYER". */
    val role: String,

    /**
     * Participación en basis points: 0–10,000 (donde 10,000 = 100%).
     * Para un expense_id + role dado, la suma de todos los registros
     * debe ser exactamente 10,000.
     */
    @ColumnInfo(name = "share_bps")
    val shareBps: Int,

    /**
     * Monto proporcional en MXN = expense.amount_mxn * share_bps / 10000.
     * Pre-calculado para evitar divisiones en queries analíticas.
     */
    @ColumnInfo(name = "share_amount_mxn")
    val shareAmountMxn: Double
)
