package mx.budget.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Auditoría de un **estado de cuenta bancario importado** (Fase C, paquete C1).
 *
 * Registra el resultado del pipeline "extraer texto local → LLM cloud (NVIDIA
 * NIM) → JSON estructurado → reconciliar". Es una tabla de **auditoría**: guarda
 * los datos que el LLM extrajo (más el `payloadJson` crudo) para trazabilidad, y
 * marca [appliedAt] cuando el usuario confirma la reconciliación.
 *
 * **SÍ participa en el sync desde la Fase 2** (kind `STATEMENT`, LWW por
 * [updatedAt]). Era local-only por diseño y eso rompía el checklist mensual
 * "Estados del mes": Agustín y Norma comparten el seguimiento, pero cada
 * dispositivo llevaba su propia lista y el mismo estado se marcaba dos veces.
 *
 * [payloadJson] NO viaja a la nube: el documento remoto lleva solo la cabecera.
 * El checklist, la tarjeta de deuda y el recordatorio de pago se alimentan de
 * `wallet_id`, `applied_at`, `periodo_fin`, `fecha_corte`, `fecha_limite_pago` y
 * los importes; el texto crudo que devolvió el LLM se queda en el dispositivo que
 * importó el PDF, que es el único que puede reabrir esa revisión. Al llegar por el
 * pull el campo se rellena con `"{}"`.
 *
 * `statement_line` sigue siendo local-only: es el detalle de trabajo del cotejo,
 * no estado compartido.
 *
 * Sin FK a `payment_method`: la fila puede persistir aunque el wallet se borre, y
 * el import se registra antes de que el usuario elija el wallet definitivo.
 * Mismo criterio que `sync_queue` / `pending_capture`.
 */
@Entity(
    tableName = "statement_import",
    indices = [Index(value = ["household_id"])]
)
data class StatementImportEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "household_id")
    val householdId: String,

    /** Wallet (payment_method) al que se aplicó la reconciliación. Null hasta aplicar. */
    @ColumnInfo(name = "wallet_id")
    val walletId: String? = null,

    /** Emisor detectado (ej. "Citibanamex", "BBVA"). */
    val emisor: String? = null,

    /** Últimos 4 dígitos de la tarjeta/cuenta. */
    val last4: String? = null,

    /** Inicio del periodo del estado de cuenta (ISO YYYY-MM-DD). */
    @ColumnInfo(name = "periodo_inicio")
    val periodoInicio: String? = null,

    /** Fin del periodo del estado de cuenta (ISO YYYY-MM-DD). */
    @ColumnInfo(name = "periodo_fin")
    val periodoFin: String? = null,

    /** Fecha de corte (ISO YYYY-MM-DD). */
    @ColumnInfo(name = "fecha_corte")
    val fechaCorte: String? = null,

    /** Fecha límite de pago (ISO YYYY-MM-DD). */
    @ColumnInfo(name = "fecha_limite_pago")
    val fechaLimitePago: String? = null,

    /** Saldo total al corte. */
    @ColumnInfo(name = "saldo_total")
    val saldoTotal: Double? = null,

    /** Pago mínimo. */
    @ColumnInfo(name = "pago_minimo")
    val pagoMinimo: Double? = null,

    /** Pago para no generar intereses. */
    @ColumnInfo(name = "pago_no_intereses")
    val pagoNoIntereses: Double? = null,

    /** Tasa anual (%) declarada en el estado de cuenta. */
    @ColumnInfo(name = "tasa_anual")
    val tasaAnual: Double? = null,

    /** JSON completo que devolvió el LLM (auditoría íntegra de la extracción). */
    @ColumnInfo(name = "payload_json")
    val payloadJson: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    /** Epoch millis en que se aplicó la reconciliación (null = solo importado). */
    @ColumnInfo(name = "applied_at")
    val appliedAt: Long? = null,

    /** Última modificación local (epoch millis) para LWW del sync (v20 a v21). */
    @ColumnInfo(name = "updated_at", defaultValue = "0")
    val updatedAt: Long = 0,
)
