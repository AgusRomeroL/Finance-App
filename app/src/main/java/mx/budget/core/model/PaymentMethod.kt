package mx.budget.core.model

import kotlinx.serialization.Serializable







/**
 * Método de pago: cuentas bancarias, tarjetas, efectivo.
 *
 * Reemplaza las columnas N–U del Excel con un modelo normalizado
 * de conciliación. El [currentBalanceMxn] se actualiza en tiempo real
 * mediante triggers SQL al insertar gastos POSTED.
 *
 * Semilla: Banamex Débito, BBVA, Banamex Clásica, Mercado Pago,
 * Mercado Libre BNPL, Coppel, Liverpool, Sears, Walmart, Klar,
 * Efectivo, Ahorro Empresa.
 */
@Serializable
data class PaymentMethod(
    
    val id: String = "",

    
    val householdId: String = "",

    
    val displayName: String = "",

    /**
     * Tipo: DEBIT_ACCOUNT, CREDIT_CARD, DEPARTMENT_STORE_CARD,
     * BNPL_INSTALLMENT, DIGITAL_WALLET, CASH, EMPLOYER_SAVINGS_FUND.
     */
    val kind: String = "",

    /** Emisor bancario (ej. "Citibanamex", "BBVA México"). */
    val issuer: String? = null,

    /** Últimos 4 dígitos de la tarjeta. */
    val last4: String? = null,

    /** Día del mes del corte (1-31). Solo para tarjetas de crédito. */
    
    val cutoffDay: Int? = null,

    /** Día del mes de pago (1-31). Solo para tarjetas de crédito. */
    
    val dueDay: Int? = null,

    /** Límite de crédito. Null para débito/efectivo. */
    
    val creditLimitMxn: Double? = null,

    /**
     * Saldo actual calculado.
     * - Para débito/efectivo: saldo disponible (disminuye con gastos).
     * - Para crédito: deuda pendiente (aumenta con gastos).
     * Se actualiza via trigger SQL en cada INSERT de expense POSTED.
     */
    
    val currentBalanceMxn: Double = 0.0,

    /** Tasa de interés anual si aplica. */
    
    val interestApr: Double? = null,

    /** Miembro propietario de esta cuenta/tarjeta. */
    
    val ownerMemberId: String? = null,

    val isActive: Boolean = true
)
