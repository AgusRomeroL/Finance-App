package mx.budget.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Raíz del aislamiento de datos. Un hogar = un household.
 *
 * Todo dato financiero del sistema cuelga de esta entidad.
 * Diseñado para soportar múltiples hogares en el futuro,
 * aunque la UI v1 asume un solo household por instalación.
 */
@Entity(
    tableName = "household",
    indices = [Index(value = ["name"], unique = true)]
)
data class HouseholdEntity(
    @PrimaryKey
    val id: String,

    val name: String,

    /** Código ISO 4217. Fijo en "MXN" para v1. */
    val currency: String = "MXN",

    /** Zona horaria IANA del hogar. */
    val timezone: String = "America/Mexico_City",

    /** Ancla del calendario quincenal: "CALENDAR" o "BIWEEKLY". */
    @ColumnInfo(name = "quincena_anchor")
    val quincenaAnchor: String = "CALENDAR",

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
