package mx.budget.core.model

import kotlinx.serialization.Serializable







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
@Serializable
data class Quincena(
    
    val id: String = "",

    
    val householdId: String = "",

    val year: Int = 0,

    /** Mes 1-12. */
    val month: Int = 0,

    /** "FIRST" (1-15) o "SECOND" (16-fin). */
    val half: String = "",

    /** Fecha de inicio en formato ISO (YYYY-MM-DD). */
    
    val startDate: String = "",

    /** Fecha de fin en formato ISO (YYYY-MM-DD). */
    
    val endDate: String = "",

    /** Etiqueta legible: "Q1 Enero 2025", "Q2 Abril 2026". */
    val label: String = "",

    /**
     * Ingreso total presupuestado para esta quincena.
     * Snapshot tomado al crear/activar la quincena.
     */
    
    val projectedIncomeMxn: Double = 0.0,

    /**
     * Gasto total presupuestado (suma de PLANNED al activar).
     */
    
    val projectedExpensesMxn: Double = 0.0,

    /**
     * Ingreso real confirmado (suma de income sources POSTED).
     */
    
    val actualIncomeMxn: Double = 0.0,

    /**
     * Gasto real ejecutado (suma de expenses POSTED).
     */
    
    val actualExpensesMxn: Double = 0.0,

    /**
     * Estado: PROVISIONED, ACTIVE, CLOSING_REVIEW, CLOSED.
     */
    val status: String = "PROVISIONED",

    /**
     * Timestamp del cierre. Null si no está cerrada.
     * Una vez cerrada, todos los Expense vinculados son inmutables.
     */
    
    val closedAt: Long? = null
)
