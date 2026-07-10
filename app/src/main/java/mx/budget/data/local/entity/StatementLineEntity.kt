package mx.budget.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Movimiento individual de un estado de cuenta importado (Fase 5 — pre-match).
 *
 * **Tabla LOCAL-ONLY** (como `pending_capture` y `statement_import`): NO se
 * sincroniza. Persiste cada línea del estado con su resultado de conciliación
 * contra los gastos existentes, para que:
 *
 * 1. **Re-importar el mismo PDF no duplique nada**: [lineFingerprint] es un hash
 *    determinista de (fecha, monto, descripción canónica) y el índice UNIQUE
 *    `(wallet_id, line_fingerprint)` hace la inserción idempotente.
 * 2. El vínculo movimiento→gasto quede auditado ([matchedExpenseId],
 *    [matchConfidence], [matchSource]) sin tocar JAMÁS la tabla `expense`.
 *
 * FK a `expense` con SET_NULL: si el gasto vinculado se borra, la línea vuelve a
 * quedar sin conciliar en vez de romperse.
 */
@Entity(
    tableName = "statement_line",
    foreignKeys = [
        ForeignKey(
            entity = ExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["matched_expense_id"],
            onDelete = ForeignKey.SET_NULL,
        )
    ],
    indices = [
        Index(value = ["wallet_id", "line_fingerprint"], unique = true),
        Index(value = ["matched_expense_id"]),
        Index(value = ["import_id"]),
    ],
)
data class StatementLineEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "household_id")
    val householdId: String,

    /** Wallet del estado de cuenta. NOT NULL: sin wallet no hay conciliación. */
    @ColumnInfo(name = "wallet_id")
    val walletId: String,

    /** Fila de auditoría `statement_import` a la que pertenece esta línea. */
    @ColumnInfo(name = "import_id")
    val importId: String,

    /** Hash determinista (fecha|monto|descripción canónica) — idempotencia. */
    @ColumnInfo(name = "line_fingerprint")
    val lineFingerprint: String,

    /** Fecha del movimiento en ISO YYYY-MM-DD, o null si el estado no la trae. */
    @ColumnInfo(name = "post_date")
    val postDate: String? = null,

    val description: String,

    /** Descripción canonicalizada (mismo pipeline que `expense.concept_canonical`). */
    @ColumnInfo(name = "description_canonical")
    val descriptionCanonical: String? = null,

    @ColumnInfo(name = "amount_mxn")
    val amountMxn: Double,

    /** `CHARGE` (cargo) — reservado `PAYMENT` por si luego se importan abonos. */
    @ColumnInfo(defaultValue = "'CHARGE'")
    val direction: String = "CHARGE",

    /** `PENDING` (sugerido/sin decidir) | `MATCHED` | `NEW` | `IGNORED`. */
    @ColumnInfo(name = "match_status", defaultValue = "'PENDING'")
    val matchStatus: String = "PENDING",

    /** Gasto existente vinculado (SET_NULL si el gasto se borra). */
    @ColumnInfo(name = "matched_expense_id")
    val matchedExpenseId: String? = null,

    /** Score 0..1 del match (local o NIM validado). */
    @ColumnInfo(name = "match_confidence")
    val matchConfidence: Double? = null,

    /** Quién propuso el vínculo: `LOCAL` | `NIM` | `USER`. */
    @ColumnInfo(name = "match_source")
    val matchSource: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at", defaultValue = "0")
    val updatedAt: Long = 0,
)
