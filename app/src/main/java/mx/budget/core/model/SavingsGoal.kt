package mx.budget.core.model

import kotlinx.serialization.Serializable







/**
 * Meta de ahorro del hogar.
 *
 * Vinculada a un método de pago (ej. tarjeta de ahorro, retirement fund)
 * con progreso y fecha objetivo opcionales.
 */
@Serializable
data class SavingsGoal(
    
    val id: String = "",

    
    val householdId: String = "",

    val name: String = "",

    
    val targetMxn: Double = 0.0,

    val currentMxn: Double = 0.0,

    
    val targetDate: String? = null,

    
    val linkedPaymentMethodId: String? = null
)
