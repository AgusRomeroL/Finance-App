package mx.budget.core.model

import kotlinx.serialization.Serializable







/**
 * Fuente de ingreso del hogar.
 *
 * Replica las celdas E4 (Sueldo Benjamín = $45,000) y
 * E5 (Sueldo Norma = $60,000) del Excel, pero modeladas
 * como entidades de primer orden con estado PLANNED/POSTED.
 *
 * Un ingreso PLANNED cuenta hacia [QuincenaEntity.projectedIncomeMxn].
 * Al confirmarse (POSTED), alimenta [QuincenaEntity.actualIncomeMxn].
 */
@Serializable
data class IncomeSource(
    
    val id: String = "",

    
    val householdId: String = "",

    
    val quincenaId: String = "",

    /** Miembro que genera el ingreso (Benjamín, Norma). */
    
    val memberId: String = "",

    /** Etiqueta: "Sueldo quincenal", "Honorarios", "Aguinaldo". */
    val label: String = "",

    
    val amountMxn: Double = 0.0,

    /** QUINCENAL, MONTHLY, IRREGULAR. */
    val cadence: String = "QUINCENAL",

    /** Fecha esperada de depósito (ISO YYYY-MM-DD). */
    
    val expectedDate: String = "",

    /** Cuenta donde se deposita el ingreso. */
    
    val paymentMethodId: String? = null,

    /** PLANNED o POSTED. */
    val status: String = "PLANNED",

    
    val createdAt: Long = 0L
)
