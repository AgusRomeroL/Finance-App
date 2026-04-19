package mx.budget.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Meta de ahorro del hogar.
 *
 * Vinculada a un método de pago (ej. tarjeta de ahorro, retirement fund)
 * con progreso y fecha objetivo opcionales.
 */
@Entity(
    tableName = "savings_goal",
    foreignKeys = [
        ForeignKey(
            entity = HouseholdEntity::class,
            parentColumns = ["id"],
            childColumns = ["household_id"]
        ),
        ForeignKey(
            entity = PaymentMethodEntity::class,
            parentColumns = ["id"],
            childColumns = ["linked_payment_method_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["household_id"]),
        Index(value = ["linked_payment_method_id"])
    ]
)
data class SavingsGoalEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "household_id")
    val householdId: String,

    val name: String,

    @ColumnInfo(name = "target_mxn")
    val targetMxn: Double,

    @ColumnInfo(name = "current_mxn")
    val currentMxn: Double = 0.0,

    @ColumnInfo(name = "target_date")
    val targetDate: String? = null,

    @ColumnInfo(name = "linked_payment_method_id")
    val linkedPaymentMethodId: String? = null
)
