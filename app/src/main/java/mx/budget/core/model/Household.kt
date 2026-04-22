package mx.budget.core.model

import kotlinx.serialization.Serializable






/**
 * Raíz del aislamiento de datos. Un hogar = un household.
 *
 * Todo dato financiero del sistema cuelga de esta entidad.
 * Diseñado para soportar múltiples hogares en el futuro,
 * aunque la UI v1 asume un solo household por instalación.
 */
@Serializable
data class Household(
    
    val id: String = "",

    val name: String = "",

    /** Código ISO 4217. Fijo en "MXN" para v1. */
    val currency: String = "MXN",

    /** Zona horaria IANA del hogar. */
    val timezone: String = "America/Mexico_City",

    /** Ancla del calendario quincenal: "CALENDAR" o "BIWEEKLY". */
    
    val quincenaAnchor: String = "CALENDAR",

    
    val createdAt: Long = 0L
)
