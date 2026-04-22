package mx.budget.core.model

import kotlinx.serialization.Serializable







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
@Serializable
data class Expense(
    
    val id: String = "",

    
    val householdId: String = "",

    /**
     * Fecha/hora del gasto como epoch millis.
     * Resolución al segundo; la hora es relevante para
     * el predictor de franja horaria del motor determinista.
     */
    
    val occurredAt: Long = 0L,

    /** Quincena a la que pertenece (denormalizado para queries rápidas). */
    
    val quincenaId: String = "",

    
    val categoryId: String = "",

    /**
     * Descripción libre del gasto: "Netflix", "Gasolina camioneta",
     * "Despensa Costco". Truncado a 64 chars en el contexto RAG de IA.
     */
    val concept: String = "",

    /**
     * Monto total positivo en MXN.
     * La distribución entre pagadores se define en ExpenseAttribution.
     */
    
    val amountMxn: Double = 0.0,

    
    val paymentMethodId: String = "",

    /** Referencia a la plantilla que originó este gasto, si aplica. */
    
    val recurrenceTemplateId: String? = null,

    /** Plan de cuotas vinculado, si este gasto es una cuota. */
    
    val installmentPlanId: String? = null,

    /** Número de cuota dentro del plan (ej. 7 de 12). */
    
    val installmentNumber: Int? = null,

    /** Porción de capital de esta cuota. */
    
    val installmentPrincipalMxn: Double? = null,

    /** Porción de interés de esta cuota. */
    
    val installmentInterestMxn: Double? = null,

    /** PLANNED, POSTED, RECONCILED. */
    val status: String = "POSTED",

    val notes: String? = null,

    
    val createdAt: Long = 0L,

    /** Miembro que registró el gasto (auditoría). */
    
    val createdByMemberId: String? = null
)
