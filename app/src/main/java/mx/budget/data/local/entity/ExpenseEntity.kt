package mx.budget.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Registro atómico de gasto — evento inmutable del ledger.
 *
 * Cada fila del sistema Excel colapsa en un Expense con relaciones
 * normalizadas a categoría, método de pago, quincena y opcionalmente
 * a una plantilla recurrente o plan de cuotas.
 *
 * El campo [status] controla el flujo PLANNED → POSTED → RECONCILED:
 * - PLANNED: materializado desde plantilla, aún no ejecutado.
 * - POSTED: confirmado por el usuario, afecta saldos de wallet.
 * - RECONCILED: casado con estado de cuenta bancario.
 *
 * La atribución a beneficiarios y pagadores vive en [ExpenseAttributionEntity]
 * como tabla puente N:M con reparto en basis points.
 */
@Entity(
    tableName = "expense",
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
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"]
        ),
        ForeignKey(
            entity = PaymentMethodEntity::class,
            parentColumns = ["id"],
            childColumns = ["payment_method_id"]
        ),
        ForeignKey(
            entity = RecurrenceTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["recurrence_template_id"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = InstallmentPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["installment_plan_id"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = MemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["created_by_member_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["quincena_id", "status"]),
        Index(value = ["household_id", "occurred_at"]),
        Index(value = ["category_id", "occurred_at"]),
        Index(value = ["payment_method_id"]),
        Index(value = ["recurrence_template_id"]),
        Index(value = ["installment_plan_id"]),
        Index(value = ["created_by_member_id"])
    ]
)
data class ExpenseEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "household_id")
    val householdId: String,

    /**
     * Fecha/hora del gasto como epoch millis.
     * Resolución al segundo; la hora es relevante para
     * el predictor de franja horaria del motor determinista.
     */
    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long,

    /** Quincena a la que pertenece (denormalizado para queries rápidas). */
    @ColumnInfo(name = "quincena_id")
    val quincenaId: String,

    @ColumnInfo(name = "category_id")
    val categoryId: String,

    /**
     * Descripción libre del gasto: "Netflix", "Gasolina camioneta",
     * "Despensa Costco". Truncado a 64 chars en el contexto RAG de IA.
     */
    val concept: String,

    /**
     * Monto total positivo en MXN.
     * La distribución entre pagadores se define en ExpenseAttribution.
     */
    @ColumnInfo(name = "amount_mxn")
    val amountMxn: Double,

    @ColumnInfo(name = "payment_method_id")
    val paymentMethodId: String,

    /** Referencia a la plantilla que originó este gasto, si aplica. */
    @ColumnInfo(name = "recurrence_template_id")
    val recurrenceTemplateId: String? = null,

    /** Plan de cuotas vinculado, si este gasto es una cuota. */
    @ColumnInfo(name = "installment_plan_id")
    val installmentPlanId: String? = null,

    /** Número de cuota dentro del plan (ej. 7 de 12). */
    @ColumnInfo(name = "installment_number")
    val installmentNumber: Int? = null,

    /** Porción de capital de esta cuota. */
    @ColumnInfo(name = "installment_principal_mxn")
    val installmentPrincipalMxn: Double? = null,

    /** Porción de interés de esta cuota. */
    @ColumnInfo(name = "installment_interest_mxn")
    val installmentInterestMxn: Double? = null,

    /** PLANNED, POSTED, RECONCILED. */
    val status: String = "POSTED",

    val notes: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    /** Miembro que registró el gasto (auditoría). */
    @ColumnInfo(name = "created_by_member_id")
    val createdByMemberId: String? = null
)
