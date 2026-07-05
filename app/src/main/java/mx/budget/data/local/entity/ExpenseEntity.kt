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
        Index(value = ["created_by_member_id"]),
        Index(value = ["concept_canonical"]),
        Index(value = ["external_payer_member_id"])
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

    /**
     * Clave canónica del concepto, calculada por el pipeline de normalización
     * retroactiva (Apéndice F.3.2). Agrupa variantes de escritura del mismo
     * gasto ("Colegiatura Santi" ≡ "Santiago Colegiatura") para inferir y
     * sugerir atribución. `null` mientras el canonicalizador no lo ha procesado.
     */
    @ColumnInfo(name = "concept_canonical")
    val conceptCanonical: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    /** Miembro que registró el gasto (auditoría). */
    @ColumnInfo(name = "created_by_member_id")
    val createdByMemberId: String? = null,

    // ── Ubicación opcional (Apéndice G.4) ───────────────────────────────────────
    // Espejo de las columnas de `pending_capture`. Se llenan al capturar (foreground
    // → CAPTURE), al confirmar una captura de banco dentro de ventana (CONFIRM) o
    // a mano desde el detalle (MANUAL). Null/sin permiso = NONE. Migración v6→v7.

    val latitude: Double? = null,

    val longitude: Double? = null,

    /** Etiqueta legible reverse-geocodeada on-device; null si falla (solo coords). */
    @ColumnInfo(name = "place_label")
    val placeLabel: String? = null,

    /** `CAPTURE | CONFIRM | MANUAL | NONE` — procedencia de la ubicación (§G.4.2). */
    @ColumnInfo(name = "location_source")
    val locationSource: String? = null,

    // ── "Alguien más pagó" (v13→v14, Fase B) ────────────────────────────────────
    // Un gasto puede haberlo pagado un tercero (un hijo, un familiar) que NO es una
    // cuenta del hogar. Cuenta para presupuesto/beneficiarios pero se carga a un
    // wallet sintético kind='EXTERNAL' que NO afecta saldos reales.

    /**
     * Estado de liquidación del gasto pagado por un tercero:
     * - `NONE`: gasto normal, pagado desde un wallet real (caso por defecto).
     * - `PENDING_REIMBURSEMENT`: lo pagó un tercero; el hogar aún no decide.
     * - `ABSORBED`: lo pagó un tercero y NO se le repondrá (queda informativo).
     * - `REIMBURSED`: el hogar ya le repuso (el gasto se movió a un wallet real).
     */
    @ColumnInfo(name = "settlement_status", defaultValue = "'NONE'")
    val settlementStatus: String = "NONE",

    /**
     * Miembro (típicamente EXTERNAL_* o un dependiente) que desembolsó el dinero
     * cuando `settlement_status != NONE`. Sin FK a propósito (mismo criterio que
     * `sync_queue`): el gasto debe sobrevivir aunque el miembro se elimine.
     */
    @ColumnInfo(name = "external_payer_member_id")
    val externalPayerMemberId: String? = null,

    /**
     * Última modificación local (epoch millis) — base de la resolución de
     * conflictos LWW del sync multi-dispositivo (MVP Fase 2). Los repos la
     * estampan en cada escritura; el pull remoto solo aplica un documento si su
     * `updatedAt` es mayor que el local. `0` = nunca editado tras la migración
     * (los sembrados y docs legados jamás pisan una edición local). v10→v11.
     */
    @ColumnInfo(name = "updated_at", defaultValue = "0")
    val updatedAt: Long = 0
)
