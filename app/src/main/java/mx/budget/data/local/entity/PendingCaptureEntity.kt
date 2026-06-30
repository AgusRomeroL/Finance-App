package mx.budget.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Captura pendiente de confirmar — **bandeja unificada** (Apéndice G.1).
 *
 * Generaliza la antigua `pending_bank_capture` (Feature D) en una sola cola que
 * alimentan TODAS las fuentes de captura. El campo [source] discrimina el origen;
 * la lógica de confirmar/descartar es común a todas. Cada fuente llena los campos
 * que le aplican y deja el resto en null.
 *
 * **Tabla LOCAL-ONLY**: NO se sincroniza a Firestore (es estado de trabajo, no
 * datos del hogar). Al confirmar se inserta un `expense` real (que sí sincroniza)
 * y esta fila pasa a `CONFIRMED`. Sin FK: la captura existe antes que el gasto.
 *
 * Principio rector: **propose-then-confirm**. Nada toca el ledger hasta que el
 * usuario confirma; la no-confirmación (`DISMISSED`) es señal negativa implícita.
 */
@Entity(
    tableName = "pending_capture",
    indices = [Index(value = ["status"])]
)
data class PendingCaptureEntity(
    @PrimaryKey
    val id: String,

    /** Origen de la captura: `BANK | CALENDAR | VOICE | WIDGET | WATCH` (§G.1). */
    val source: String = "BANK",

    @ColumnInfo(name = "amount_mxn")
    val amountMxn: Double,

    /** Concepto/comercio crudo. Para `BANK` = el comercio extraído ("OXXO"). */
    val concept: String,

    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long,

    /** Wallet resuelto al ingresar la captura, o null si no hubo match. */
    @ColumnInfo(name = "suggested_wallet_id")
    val suggestedWalletId: String? = null,

    /** Categoría inferida (modal por concepto histórico) o fallback, o null. */
    @ColumnInfo(name = "suggested_category_id")
    val suggestedCategoryId: String? = null,

    /** "PENDING", "CONFIRMED", "DISMISSED". */
    val status: String = "PENDING",

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    // ── Metadata específica de la fuente (nullable) ─────────────────────────────

    /** BANK: id del banco en `bank_templates.json` (ej. "bbva"). */
    @ColumnInfo(name = "bank_id")
    val bankId: String? = null,

    /** BANK: nombre legible del banco ("BBVA"). */
    @ColumnInfo(name = "bank_name")
    val bankName: String? = null,

    /** BANK: package de la app emisora (auditoría/allowlist). */
    @ColumnInfo(name = "bank_package")
    val bankPackage: String? = null,

    /** BANK: últimos 4 dígitos detectados (hint de wallet). */
    val last4: String? = null,

    /** VOICE/WIDGET/WATCH: texto crudo dictado/escrito por el usuario. */
    @ColumnInfo(name = "raw_text")
    val rawText: String? = null,

    /** CALENDAR: regla de recurrencia que originó esta ocurrencia. */
    @ColumnInfo(name = "recurrence_id")
    val recurrenceId: String? = null,

    // ── Captura rica de lenguaje natural (§G.3) ─────────────────────────────────
    // El LLM con contexto del hogar puede asignar beneficiarios/pagadores y notas
    // desde la frase. Se guardan resueltos (memberId→bps como JSON) para prellenar
    // la hoja de captura al confirmar (propose-then-confirm con revisión).

    /** VOICE/WIDGET/WATCH: atribución BENEFICIARY como JSON `{memberId: bps}`, o null. */
    @ColumnInfo(name = "suggested_beneficiary_json")
    val suggestedBeneficiaryJson: String? = null,

    /** VOICE/WIDGET/WATCH: atribución PAYER como JSON `{memberId: bps}`, o null. */
    @ColumnInfo(name = "suggested_payer_json")
    val suggestedPayerJson: String? = null,

    /** Nota libre extraída de la frase (detalle que no es el concepto), o null. */
    val notes: String? = null,

    // ── Ubicación opcional (§G.4.2) ─────────────────────────────────────────────

    val latitude: Double? = null,

    val longitude: Double? = null,

    @ColumnInfo(name = "place_label")
    val placeLabel: String? = null,

    /** `CAPTURE | CONFIRM | MANUAL | NONE` — procedencia de la ubicación (§G.4.2). */
    @ColumnInfo(name = "location_source")
    val locationSource: String? = null,
)

/**
 * `true` si la captura trae datos ricos del LLM (beneficiarios/pagadores/notas)
 * que ameritan una revisión en la hoja prellenada antes de registrar. Las
 * capturas simples (banco, o NL sin atribución) se confirman directo.
 */
val PendingCaptureEntity.hasRichCapture: Boolean
    get() = !suggestedBeneficiaryJson.isNullOrBlank() ||
        !suggestedPayerJson.isNullOrBlank() ||
        !notes.isNullOrBlank()
