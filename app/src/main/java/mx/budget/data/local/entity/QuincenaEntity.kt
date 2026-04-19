package mx.budget.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Período operativo quincenal — ancla de agregación temporal.
 *
 * Cada quincena transita por el DFA:
 * PROVISIONED → ACTIVE → CLOSING_REVIEW → CLOSED
 *
 * Invariante: solo UNA quincena ACTIVE por household a la vez.
 *
 * La quincena materializa automáticamente gastos PLANNED desde
 * RecurrenceTemplate al transitar de PROVISIONED a ACTIVE.
 */
@Entity(
    tableName = "quincena",
    foreignKeys = [
        ForeignKey(
            entity = HouseholdEntity::class,
            parentColumns = ["id"],
            childColumns = ["household_id"]
        )
    ],
    indices = [
        Index(value = ["household_id", "year", "month", "half"], unique = true),
        Index(value = ["household_id", "start_date", "end_date"]),
        Index(value = ["household_id", "status"])
    ]
)
data class QuincenaEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "household_id")
    val householdId: String,

    val year: Int,

    /** Mes 1-12. */
    val month: Int,

    /** "FIRST" (1-15) o "SECOND" (16-fin). */
    val half: String,

    /** Fecha de inicio en formato ISO (YYYY-MM-DD). */
    @ColumnInfo(name = "start_date")
    val startDate: String,

    /** Fecha de fin en formato ISO (YYYY-MM-DD). */
    @ColumnInfo(name = "end_date")
    val endDate: String,

    /** Etiqueta legible: "Q1 Enero 2025", "Q2 Abril 2026". */
    val label: String,

    /**
     * Ingreso total presupuestado para esta quincena.
     * Snapshot tomado al crear/activar la quincena.
     */
    @ColumnInfo(name = "projected_income_mxn")
    val projectedIncomeMxn: Double = 0.0,

    /**
     * Gasto total presupuestado (suma de PLANNED al activar).
     */
    @ColumnInfo(name = "projected_expenses_mxn")
    val projectedExpensesMxn: Double = 0.0,

    /**
     * Ingreso real confirmado (suma de income sources POSTED).
     */
    @ColumnInfo(name = "actual_income_mxn")
    val actualIncomeMxn: Double = 0.0,

    /**
     * Gasto real ejecutado (suma de expenses POSTED).
     */
    @ColumnInfo(name = "actual_expenses_mxn")
    val actualExpensesMxn: Double = 0.0,

    /**
     * Estado: PROVISIONED, ACTIVE, CLOSING_REVIEW, CLOSED.
     */
    val status: String = "PROVISIONED",

    /**
     * Timestamp del cierre. Null si no está cerrada.
     * Una vez cerrada, todos los Expense vinculados son inmutables.
     */
    @ColumnInfo(name = "closed_at")
    val closedAt: Long? = null
)
