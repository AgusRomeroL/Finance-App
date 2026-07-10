package mx.budget.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Préstamo otorgado por el hogar a terceros.
 *
 * Modela las filas 116-120 del Excel: "Prestamo oficinas · jaudiel · 105000".
 * Flujo independiente del gasto regular, con saldo remanente y recordatorios.
 */
@Entity(
    tableName = "loan",
    foreignKeys = [
        ForeignKey(
            entity = HouseholdEntity::class,
            parentColumns = ["id"],
            childColumns = ["household_id"]
        ),
        ForeignKey(
            entity = MemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["debtor_member_id"]
        ),
        ForeignKey(
            entity = InstallmentPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["payment_schedule_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["household_id"]),
        Index(value = ["debtor_member_id"]),
        Index(value = ["payment_schedule_id"])
    ]
)
data class LoanEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "household_id")
    val householdId: String,

    /** Persona o entidad que debe dinero al hogar (ej. Jaudiel, oficinas). */
    @ColumnInfo(name = "debtor_member_id")
    val debtorMemberId: String,

    /** Capital original prestado. */
    @ColumnInfo(name = "principal_mxn")
    val principalMxn: Double,

    /** Saldo pendiente de cobro. Disminuye con cada pago recibido. */
    @ColumnInfo(name = "remaining_balance_mxn")
    val remainingBalanceMxn: Double,

    /** Intereses acordados (fijo, no anualizado). */
    @ColumnInfo(name = "agreed_interest_mxn")
    val agreedInterestMxn: Double = 0.0,

    /** Fecha en que se otorgó el préstamo (ISO YYYY-MM-DD). */
    @ColumnInfo(name = "issued_at")
    val issuedAt: String,

    /** Fecha límite de pago pactada, si existe. */
    @ColumnInfo(name = "due_at")
    val dueAt: String? = null,

    /** Plan de pagos vinculado si el deudor paga en cuotas. */
    @ColumnInfo(name = "payment_schedule_id")
    val paymentScheduleId: String? = null,

    val notes: String? = null,

    /**
     * Esquema de pago configurable (v15→v16). Todos NULLABLE: los préstamos
     * sembrados no lo declaran. Describen cómo el deudor liquidará la deuda:
     * número de pagos, frecuencia, monto por pago y fecha de inicio.
     */

    /** Número de pagos pactados (ej. 6 quincenales). Null = sin esquema. */
    @ColumnInfo(name = "payment_count")
    val paymentCount: Int? = null,

    /** Frecuencia del pago: WEEKLY | BIWEEKLY | MONTHLY | LUMP_SUM. */
    @ColumnInfo(name = "payment_frequency")
    val paymentFrequency: String? = null,

    /** Monto de cada pago (default sugerido = principal / paymentCount). */
    @ColumnInfo(name = "payment_amount_mxn")
    val paymentAmountMxn: Double? = null,

    /** Fecha del primer pago del esquema (ISO YYYY-MM-DD). */
    @ColumnInfo(name = "schedule_start_date")
    val scheduleStartDate: String? = null,

    /** Última modificación local (epoch millis), para LWW del sync (v11→v12). */
    @ColumnInfo(name = "updated_at", defaultValue = "0")
    val updatedAt: Long = 0
)
