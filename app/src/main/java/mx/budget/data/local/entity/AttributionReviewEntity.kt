package mx.budget.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cola de revisión de atribuciones inferidas retroactivamente (Apéndice F.3.3).
 *
 * El [mx.budget.data.work.RetroAttributionWorker] infiere la distribución
 * BENEFICIARY/PAYER de un gasto a partir de su patrón histórico. Cuando la
 * confianza supera el umbral, la aplica directo; cuando no, crea una fila aquí
 * en estado `PENDING` para que el usuario la confirme, edite o ignore desde la
 * pantalla "Revisión de atribuciones".
 *
 * **Tabla LOCAL-ONLY**: NO se sincroniza a Firestore. Es estado efímero de
 * trabajo, no datos del hogar. La aplicación de una sugerencia confirmada sí
 * escribe en `expense_attribution` (que sí sincroniza), pero esta cola no.
 */
@Entity(
    tableName = "attribution_review",
    foreignKeys = [
        ForeignKey(
            entity = ExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["expense_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["status"]),
        Index(value = ["expense_id"])
    ]
)
data class AttributionReviewEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "expense_id")
    val expenseId: String,

    /** "BENEFICIARY" o "PAYER" — qué dimensión propone esta fila. */
    val role: String,

    /**
     * Distribución propuesta serializada como JSON: lista de
     * `{"memberId": "...", "shareBps": 10000}` que suma 10,000 bps.
     */
    @ColumnInfo(name = "suggested_json")
    val suggestedJson: String,

    /** Confianza de la sugerencia en [0.0, 1.0]. */
    val confidence: Double,

    /** Número de gastos históricos que respaldan la sugerencia. */
    @ColumnInfo(name = "sample_size")
    val sampleSize: Int,

    /** Concepto canónico que agrupa esta sugerencia (para revisar en lote). */
    @ColumnInfo(name = "concept_canonical")
    val conceptCanonical: String?,

    /** "PENDING", "CONFIRMED", "REJECTED", "EDITED". */
    val status: String = "PENDING",

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
