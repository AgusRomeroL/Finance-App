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
 * **No participa en el sync** (local-only, sin `updated_at`): es un rastro de la
 * importación en ESTE dispositivo, no un dato del hogar que otros necesiten. La
 * reconciliación en sí (corte/límite del wallet, planes MSI) SÍ se sincroniza a
 * través de los repos existentes (`WalletRepository`, `InstallmentRepository`).
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
)
