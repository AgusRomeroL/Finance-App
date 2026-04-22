package mx.budget.core.model

import kotlinx.serialization.Serializable







/**
 * Préstamo otorgado por el hogar a terceros.
 *
 * Modela las filas 116-120 del Excel: "Prestamo oficinas · jaudiel · 105000".
 * Flujo independiente del gasto regular, con saldo remanente y recordatorios.
 */
@Serializable
data class Loan(
    
    val id: String = "",

    
    val householdId: String = "",

    /** Persona o entidad que debe dinero al hogar (ej. Jaudiel, oficinas). */
    
    val debtorMemberId: String = "",

    /** Capital original prestado. */
    
    val principalMxn: Double = 0.0,

    /** Saldo pendiente de cobro. Disminuye con cada pago recibido. */
    
    val remainingBalanceMxn: Double = 0.0,

    /** Intereses acordados (fijo, no anualizado). */
    val agreedInterestMxn: Double = 0.0,

    /** Fecha en que se otorgó el préstamo (ISO YYYY-MM-DD). */
    
    val issuedAt: String = "",

    /** Fecha límite de pago pactada, si existe. */
    
    val dueAt: String? = null,

    /** Plan de pagos vinculado si el deudor paga en cuotas. */
    
    val paymentScheduleId: String? = null,

    val notes: String? = null
)
