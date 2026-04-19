package mx.budget.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Fuente de ingreso del hogar.
 *
 * Replica las celdas E4 (Sueldo Benjamín = $45,000) y
 * E5 (Sueldo Norma = $60,000) del Excel, pero modeladas
 * como entidades de primer orden con estado PLANNED/POSTED.
 *
 * Un ingreso PLANNED cuenta hacia [QuincenaEntity.projectedIncomeMxn].
 * Al confirmarse (POSTED), alimenta [QuincenaEntity.actualIncomeMxn].
 */
@Entity(
    tableName = "income_source",
    foreignKeys = [
        ForeignKey(
            entity = HouseholdEntity::class,
            parentColumns = ["id"],
            childColumns = ["household_id"]
        ),
        ForeignKey(
            entity = QuincenaEntity::class,
            parentColumns = ["id"],
            childColumns = ["quincena_id"]
        ),
        ForeignKey(
            entity = MemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["member_id"]
        ),
        ForeignKey(
            entity = PaymentMethodEntity::class,
            parentColumns = ["id"],
            childColumns = ["payment_method_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["household_id"]),
        Index(value = ["quincena_id"]),
        Index(value = ["member_id"]),
        Index(value = ["payment_method_id"])
    ]
)
data class IncomeSourceEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "household_id")
    val householdId: String,

    @ColumnInfo(name = "quincena_id")
    val quincenaId: String,

    /** Miembro que genera el ingreso (Benjamín, Norma). */
    @ColumnInfo(name = "member_id")
    val memberId: String,

    /** Etiqueta: "Sueldo quincenal", "Honorarios", "Aguinaldo". */
    val label: String,

    @ColumnInfo(name = "amount_mxn")
    val amountMxn: Double,

    /** QUINCENAL, MONTHLY, IRREGULAR. */
    val cadence: String = "QUINCENAL",

    /** Fecha esperada de depósito (ISO YYYY-MM-DD). */
    @ColumnInfo(name = "expected_date")
    val expectedDate: String,

    /** Cuenta donde se deposita el ingreso. */
    @ColumnInfo(name = "payment_method_id")
    val paymentMethodId: String? = null,

    /** PLANNED o POSTED. */
    val status: String = "PLANNED",

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
