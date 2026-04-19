package mx.budget.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Plantilla de gasto recurrente.
 *
 * Almacena patrones detectados automáticamente por el motor de recurrencia
 * o configurados manualmente por el usuario. Al activar cada quincena,
 * el sistema materializa instancias PLANNED desde plantillas activas.
 *
 * El [confidenceScore] refleja qué tan confiable es la plantilla:
 * crece monotónicamente con cada confirmación sin conflicto (EMA α=0.2),
 * se resetea al detectar cambio de atribución.
 */
@Entity(
    tableName = "recurrence_template",
    foreignKeys = [
        ForeignKey(
            entity = HouseholdEntity::class,
            parentColumns = ["id"],
            childColumns = ["household_id"]
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"]
        ),
        ForeignKey(
            entity = PaymentMethodEntity::class,
            parentColumns = ["id"],
            childColumns = ["default_payment_method_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["household_id", "is_active"]),
        Index(value = ["category_id"]),
        Index(value = ["default_payment_method_id"])
    ]
)
data class RecurrenceTemplateEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "household_id")
    val householdId: String,

    /** Concepto canónico: "Netflix", "Hipoteca", "Gasolina Camioneta". */
    val concept: String,

    @ColumnInfo(name = "category_id")
    val categoryId: String,

    /** Monto por defecto en MXN. */
    @ColumnInfo(name = "default_amount_mxn")
    val defaultAmountMxn: Double,

    @ColumnInfo(name = "default_payment_method_id")
    val defaultPaymentMethodId: String? = null,

    /**
     * Cadencia: QUINCENAL_FIRST, QUINCENAL_SECOND, QUINCENAL_EVERY,
     * MONTHLY_SPECIFIC_HALF, BIMONTHLY, CUSTOM_CRON.
     */
    val cadence: String,

    /**
     * Detalle de cadencia como JSON.
     * Ej: {"day_of_month": 15, "drift_tolerance_days": 3}.
     */
    @ColumnInfo(name = "cadence_detail")
    val cadenceDetail: String = "{}",

    /** Próxima fecha esperada de ocurrencia (ISO YYYY-MM-DD). */
    @ColumnInfo(name = "next_expected_date")
    val nextExpectedDate: String? = null,

    /**
     * IDs de beneficiarios por defecto como JSON array.
     * Ej: ["uuid-pau", "uuid-david", "uuid-agus"].
     */
    @ColumnInfo(name = "default_beneficiary_ids")
    val defaultBeneficiaryIds: String = "[]",

    /**
     * Distribución default del pagador como JSON object.
     * Ej: {"uuid-norma": 10000} (Norma paga 100%).
     */
    @ColumnInfo(name = "default_payer_split")
    val defaultPayerSplit: String = "{}",

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    /**
     * Score de confianza 0.0–1.0. Calculado por el motor de recurrencia:
     * 0.4 * sigmoid(n-3) + 0.3 * (1-interval_cv) + 0.3 * (1-amount_cv).
     */
    @ColumnInfo(name = "confidence_score")
    val confidenceScore: Double = 0.0,

    /**
     * IDs de los gastos que informaron el aprendizaje de esta plantilla.
     * JSON array para trazabilidad.
     */
    @ColumnInfo(name = "learned_from_expense_ids")
    val learnedFromExpenseIds: String = "[]"
)
