package mx.budget.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import mx.budget.data.local.entity.HouseholdEntity

/**
 * DAO de solo lectura para resolver el household activo.
 *
 * La UI v1 asume un único household por instalación (ver [mx.budget.data.local.entity.HouseholdEntity]).
 * Este DAO permite obtener su `id` dinámicamente en lugar de asumir el literal
 * `"default_household"`. Añadir un DAO (o un método `@Insert`) NO altera el
 * esquema/identityHash de Room (no toca entidades ni la versión), por lo que es
 * seguro respecto al asset precargado.
 */
@Dao
interface HouseholdDao {

    /** `id` del único household, o `null` si la tabla aún no tiene filas. */
    @Query("SELECT id FROM household LIMIT 1")
    suspend fun getSingleId(): String?

    /** Fila del hogar por id, o null si no existe localmente (multi-hogar). */
    @Query("SELECT * FROM household WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): HouseholdEntity?

    /** Nº de hogares (0 = instalación virgen, sin semilla — dispara el wizard). */
    @Query("SELECT COUNT(*) FROM household")
    suspend fun count(): Int

    /**
     * Alta del hogar en el wizard de onboarding (paquete B2). Local-only: el push
     * de HOUSEHOLD aún no está cableado en el SyncManager (la parte nube la cubre
     * MembershipRepository.createHousehold). REPLACE = idempotente si el wizard se
     * reinicia.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(household: HouseholdEntity)
}
