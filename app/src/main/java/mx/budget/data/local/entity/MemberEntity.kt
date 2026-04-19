package mx.budget.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
@Entity(
    tableName = "member",
    foreignKeys = [
        ForeignKey(
            entity = HouseholdEntity::class,
            parentColumns = ["id"],
            childColumns = ["household_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["household_id", "is_active"]),
        Index(value = ["household_id", "display_name"], unique = true)
    ]
)
data class MemberEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "household_id")
    val householdId: String,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    /**
     * Lista de aliases cortos serializada como JSON array.
     * Ej: ["Benji", "Benjamin"] para el miembro Benjamín.
     * Usado por el motor determinista para matching textual.
     */
    @ColumnInfo(name = "short_aliases")
    val shortAliases: String = "[]",

    /**
     * Rol del miembro: PAYER_ADULT, BENEFICIARY_DEPENDENT,
     * EXTERNAL_CREDITOR, EXTERNAL_DEBTOR, EXTERNAL_SERVICE.
     */
    val role: String,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    /**
     * Ingreso quincenal por defecto. Solo relevante para PAYER_ADULT.
     * Ej: 45000.0 (Benjamín), 60000.0 (Norma).
     */
    @ColumnInfo(name = "default_income_mxn")
    val defaultIncomeMxn: Double? = null,

    /**
     * Metadatos adicionales serializados como JSON.
     * Puede incluir: color, avatar, birthdate.
     */
    val meta: String = "{}"
)
