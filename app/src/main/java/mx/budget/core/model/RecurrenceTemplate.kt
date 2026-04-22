package mx.budget.core.model

import kotlinx.serialization.Serializable







/**
 * Plantilla de gasto recurrente.
 *
 * Almacena patrones detectados automáticamente por el motor de recurrencia
 * o configurados manualmente por el usuario. Al activar cada quincena,
 * el sistema materializa instancias PLANNED desde plantillas activas.
 *
 * El [confidenceScore] refleja qué tan confiable es la plantilla:
 * crece monotónicamente con cada confirmación sin conflicto (EMA α=0.2),
 * se resetea al detectar cambio de atribución.
 */
@Serializable
data class RecurrenceTemplate(
    
    val id: String = "",

    
    val householdId: String = "",

    /** Concepto canónico: "Netflix", "Hipoteca", "Gasolina Camioneta". */
    val concept: String = "",

    
    val categoryId: String = "",

    /** Monto por defecto en MXN. */
    
    val defaultAmountMxn: Double = 0.0,

    
    val defaultPaymentMethodId: String? = null,

    /**
     * Cadencia: QUINCENAL_FIRST, QUINCENAL_SECOND, QUINCENAL_EVERY,
     * MONTHLY_SPECIFIC_HALF, BIMONTHLY, CUSTOM_CRON.
     */
    val cadence: String = "",

    /**
     * Detalle de cadencia como JSON.
     * Ej: {"day_of_month": 15, "drift_tolerance_days": 3}.
     */
    
    val cadenceDetail: String = "{}",

    /** Próxima fecha esperada de ocurrencia (ISO YYYY-MM-DD). */
    
    val nextExpectedDate: String? = null,

    /**
     * IDs de beneficiarios por defecto como JSON array.
     * Ej: ["uuid-pau", "uuid-david", "uuid-agus"].
     */
    
    val defaultBeneficiaryIds: String = "[]",

    /**
     * Distribución default del pagador como JSON object.
     * Ej: {"uuid-norma": 10000} (Norma paga 100%).
     */
    
    val defaultPayerSplit: String = "{}",

    val isActive: Boolean = true,

    /**
     * Score de confianza 0.0–1.0. Calculado por el motor de recurrencia:
     * 0.4 * sigmoid(n-3) + 0.3 * (1-interval_cv) + 0.3 * (1-amount_cv).
     */
    val confidenceScore: Double = 0.0,

    /**
     * IDs de los gastos que informaron el aprendizaje de esta plantilla.
     * JSON array para trazabilidad.
     */
    
    val learnedFromExpenseIds: String = "[]"
)
