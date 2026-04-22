package mx.budget.core.model

import kotlinx.serialization.Serializable







/**
 * Miembro del hogar o tercero vinculado.
 *
 * Cubre cinco roles: adultos pagadores (Benjamín, Norma),
 * dependientes (Pau, David, Agustín, Santi), acreedores (Omar),
 * deudores (Jaudiel) y proveedores de servicio (Araceli).
 *
 * Los [shortAliases] permiten fuzzy matching en el motor de atribución
 * y en el AliasResolver del módulo de IA.
 */
@Serializable
data class Member(
    
    val id: String = "",

    
    val householdId: String = "",

    
    val displayName: String = "",

    /**
     * Lista de aliases cortos serializada como JSON array.
     * Ej: ["Benji", "Benjamin"] para el miembro Benjamín.
     * Usado por el motor determinista para matching textual.
     */
    
    val shortAliases: String = "[]",

    /**
     * Rol del miembro: PAYER_ADULT, BENEFICIARY_DEPENDENT,
     * EXTERNAL_CREDITOR, EXTERNAL_DEBTOR, EXTERNAL_SERVICE.
     */
    val role: String = "",

    val isActive: Boolean = true,

    /**
     * Ingreso quincenal por defecto. Solo relevante para PAYER_ADULT.
     * Ej: 45000.0 (Benjamín), 60000.0 (Norma).
     */
    
    val defaultIncomeMxn: Double? = null,

    /**
     * Metadatos adicionales serializados como JSON.
     * Puede incluir: color, avatar, birthdate.
     */
    val meta: String = "{}"
)
