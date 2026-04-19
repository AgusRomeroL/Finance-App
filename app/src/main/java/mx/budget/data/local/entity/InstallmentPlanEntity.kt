package mx.budget.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Plan de pagos en cuotas con intereses.
 *
 * Reemplaza la numeración manual del Excel:
 * "Préstamo Omar 3", "Mercado libre 7 de 12", "Buró de crédito 2 de 3".
 *
 * El [currentInstallment] se incrementa automáticamente al postear
 * cada cuota. Al alcanzar [totalInstallments], se marca como PAID_OFF.
 *
 * Cuando se conoce la tasa [interestRateApr], se calcula la tabla de
 * amortización francesa para desglosar capital vs interés por cuota.
 */
@Entity(
    tableName = "installment_plan",
    foreignKeys = [
        ForeignKey(
            entity = HouseholdEntity::class,
            parentColumns = ["id"],
            childColumns = ["household_id"]
        ),
        ForeignKey(
            entity = MemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["creditor_member_id"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = PaymentMethodEntity::class,
            parentColumns = ["id"],
            childColumns = ["payment_method_id"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["household_id", "status"]),
        Index(value = ["creditor_member_id"]),
        Index(value = ["payment_method_id"]),
        Index(value = ["category_id"])
    ]
)
data class InstallmentPlanEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "household_id")
    val householdId: String,

    /** Nombre visible: "Préstamo Omar", "Mercado Libre MSI". */
    @ColumnInfo(name = "display_name")
    val displayName: String,

    /** Acreedor (ej. Omar). Null si es BNPL de tienda. */
    @ColumnInfo(name = "creditor_member_id")
    val creditorMemberId: String? = null,

    /** Método de pago vinculado (ej. Mercado Libre BNPL). */
    @ColumnInfo(name = "payment_method_id")
    val paymentMethodId: String? = null,

    /** Capital original prestado o financiado. */
    @ColumnInfo(name = "principal_mxn")
    val principalMxn: Double,

    /** Número total de cuotas pactadas. */
    @ColumnInfo(name = "total_installments")
    val totalInstallments: Int,

    /** Monto fijo de cada cuota. */
    @ColumnInfo(name = "installment_amount_mxn")
    val installmentAmountMxn: Double,

    /** Tasa anual si se conoce. Null para MSI sin interés. */
    @ColumnInfo(name = "interest_rate_apr")
    val interestRateApr: Double? = null,

    /** Fecha en que inició el plan (ISO YYYY-MM-DD). */
    @ColumnInfo(name = "start_date")
    val startDate: String,

    /**
     * Cuota actual (avance). Se incrementa al postear cada cuota.
     * Inicia en 0 y llega hasta [totalInstallments].
     */
    @ColumnInfo(name = "current_installment")
    val currentInstallment: Int = 0,

    /** ACTIVE, PAID_OFF, DEFAULTED. */
    val status: String = "ACTIVE",

    /** Categoría de gasto vinculada (LOANS.*). */
    @ColumnInfo(name = "category_id")
    val categoryId: String? = null
)
