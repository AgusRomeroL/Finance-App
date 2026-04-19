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

    val notes: String? = null
)
