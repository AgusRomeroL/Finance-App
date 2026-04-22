package mx.budget.core.model

import kotlinx.serialization.Serializable







/**
 * Plan de pagos en cuotas con intereses.
 *
 * Reemplaza la numeración manual del Excel:
 * "Préstamo Omar 3", "Mercado libre 7 de 12", "Buró de crédito 2 de 3".
 *
 * El [currentInstallment] se incrementa automáticamente al postear
 * cada cuota. Al alcanzar [totalInstallments], se marca como PAID_OFF.
 *
 * Cuando se conoce la tasa [interestRateApr], se calcula la tabla de
 * amortización francesa para desglosar capital vs interés por cuota.
 */
@Serializable
data class InstallmentPlan(
    
    val id: String = "",

    
    val householdId: String = "",

    /** Nombre visible: "Préstamo Omar", "Mercado Libre MSI". */
    
    val displayName: String = "",

    /** Acreedor (ej. Omar). Null si es BNPL de tienda. */
    
    val creditorMemberId: String? = null,

    /** Método de pago vinculado (ej. Mercado Libre BNPL). */
    
    val paymentMethodId: String? = null,

    /** Capital original prestado o financiado. */
    
    val principalMxn: Double = 0.0,

    /** Número total de cuotas pactadas. */
    
    val totalInstallments: Int = 0,

    /** Monto fijo de cada cuota. */
    
    val installmentAmountMxn: Double = 0.0,

    /** Tasa anual si se conoce. Null para MSI sin interés. */
    
    val interestRateApr: Double? = null,

    /** Fecha en que inició el plan (ISO YYYY-MM-DD). */
    
    val startDate: String = "",

    /**
     * Cuota actual (avance). Se incrementa al postear cada cuota.
     * Inicia en 0 y llega hasta [totalInstallments].
     */
    val currentInstallment: Int = 0,

    /** ACTIVE, PAID_OFF, DEFAULTED. */
    val status: String = "ACTIVE",

    /** Categoría de gasto vinculada (LOANS.*). */
    
    val categoryId: String? = null
)
