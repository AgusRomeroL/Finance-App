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
 *
 * **Contrato de sync — tabla SINCRONIZADA (paquete ANDROID-TEMPLATES, jul-2026;
 * antes era local-only):** el CRUD de plantillas vive también en la web, así
 * que la tabla entra al contrato bidireccional estándar:
 *
 * - **Push:** cada escritura de [mx.budget.data.repository.impl.RecurrenceRepositoryImpl]
 *   estampa [updatedAt] y encola `RECURRENCE|UPSERT` (o `DELETE`) en
 *   `sync_queue`; el `SyncManager` la drena hacia
 *   [mx.budget.data.remote.RecurrenceRepositoryFirestore], subcolección
 *   `households/{hid}/recurrence_template` (campos camelCase, doc id = [id]).
 * - **Pull:** `RemotePullSync` escucha esa subcolección y aplica vía DAO
 *   directo (anti-eco) con gate LWW por [updatedAt]; los borrados llegan como
 *   REMOVED o como lápida (`deletedAt`), igual que savings/loan.
 * - **Seed:** `scripts/admin/purge_and_reseed.py` NO siembra esta colección
 *   (decisión vigente): los dispositivos convergen por sync — los ids son
 *   uuid5 deterministas del ETL, así que la misma plantilla creada en dos
 *   dispositivos colisiona en el mismo doc en vez de duplicarse.
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
    val learnedFromExpenseIds: String = "[]",

    /**
     * Tercero que paga por adelantado este gasto recurrente (reembolsable).
     * Null = nadie externo (comportamiento normal). Al materializar, la
     * instancia PLANNED usa el wallet externo y atribuye el 100% del pago a
     * este miembro (v16→v17).
     */
    @ColumnInfo(name = "default_external_payer_member_id")
    val defaultExternalPayerMemberId: String? = null,

    /**
     * Estado de liquidación por defecto de las instancias materializadas:
     * NONE | PENDING_REIMBURSEMENT. Se propaga a `expense.settlement_status`.
     */
    @ColumnInfo(name = "default_settlement_status", defaultValue = "'NONE'")
    val defaultSettlementStatus: String = "NONE",

    /**
     * Última modificación local (epoch ms) — LWW del sync bidireccional
     * (v18→v19). `0` = fila legada/sembrada que nunca pisa una edición local.
     */
    @ColumnInfo(name = "updated_at", defaultValue = "0")
    val updatedAt: Long = 0L
)
