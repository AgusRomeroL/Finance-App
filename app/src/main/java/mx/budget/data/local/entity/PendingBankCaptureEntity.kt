package mx.budget.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Captura bancaria pendiente de confirmar (Feature D, §F.6).
 *
 * Una notificación de un banco de la allowlist se parsea on-device y aterriza
 * aquí en estado `PENDING`. La fila es el **puente persistente** entre el
 * [mx.budget.service.BankNotificationListenerService] (que corre aunque la app
 * esté cerrada) y la UI: el dashboard la observa y ofrece confirmar/descartar.
 *
 * **Tabla LOCAL-ONLY**: NO se sincroniza a Firestore (es estado de trabajo, no
 * datos del hogar). Al confirmar se inserta un `expense` real (que sí sincroniza)
 * y esta fila pasa a `CONFIRMED`. Sin FK: la captura existe antes que el gasto.
 */
@Entity(
    tableName = "pending_bank_capture",
    indices = [Index(value = ["status"])]
)
data class PendingBankCaptureEntity(
    @PrimaryKey
    val id: String,

    /** id del banco en `bank_templates.json` (ej. "bbva"). */
    @ColumnInfo(name = "bank_id")
    val bankId: String,

    /** Nombre legible del banco ("BBVA"). */
    @ColumnInfo(name = "bank_name")
    val bankName: String,

    /** Package de la app emisora (auditoría/allowlist). */
    @ColumnInfo(name = "bank_package")
    val bankPackage: String,

    @ColumnInfo(name = "amount_mxn")
    val amountMxn: Double,

    /** Comercio crudo extraído ("OXXO"). Se usa como concepto del gasto al confirmar. */
    val merchant: String,

    /** Últimos 4 dígitos detectados (hint de wallet), o null. */
    val last4: String? = null,

    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long,

    /** Wallet resuelto por last4/issuer al ingresar la captura, o null si no hubo match. */
    @ColumnInfo(name = "suggested_wallet_id")
    val suggestedWalletId: String? = null,

    /** Categoría inferida (modal por comercio histórico) o fallback, o null. */
    @ColumnInfo(name = "suggested_category_id")
    val suggestedCategoryId: String? = null,

    /** "PENDING", "CONFIRMED", "DISMISSED". */
    val status: String = "PENDING",

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
