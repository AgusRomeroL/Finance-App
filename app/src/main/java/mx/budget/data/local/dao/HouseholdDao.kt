package mx.budget.data.local.dao

import androidx.room.Dao
import androidx.room.Query

/**
 * DAO de solo lectura para resolver el household activo.
 *
 * La UI v1 asume un único household por instalación (ver [mx.budget.data.local.entity.HouseholdEntity]).
 * Este DAO permite obtener su `id` dinámicamente en lugar de asumir el literal
 * `"default_household"`. Añadir un DAO NO altera el esquema/identityHash de Room
 * (no toca entidades ni la versión), por lo que es seguro respecto al asset precargado.
 */
@Dao
interface HouseholdDao {

    /** `id` del único household, o `null` si la tabla aún no tiene filas. */
    @Query("SELECT id FROM household LIMIT 1")
    suspend fun getSingleId(): String?
}
