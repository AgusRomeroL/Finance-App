package mx.budget.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import mx.budget.data.local.entity.HouseholdEntity

/**
 * DAO del hogar activo.
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

    /** Nº de hogares (0 = instalación virgen, sin semilla: dispara el wizard). */
    @Query("SELECT COUNT(*) FROM household")
    suspend fun count(): Int

    /**
     * Alta o edición del hogar. REPLACE = idempotente si el wizard se reinicia.
     *
     * Desde la Fase 2 el hogar SÍ participa del sync: quien escribe por esta vía
     * debe estampar `updated_at` y encolar `HOUSEHOLD|UPSERT` en `sync_queue`
     * para que el push lo suba. La única excepción deliberada es el pull
     * ([mx.budget.data.sync.RemotePullSync]) y el arranque en frío
     * (`BudgetApplication.ensureLocalHousehold`), que escriben por DAO directo
     * justamente para NO encolar: encolar ahí creaba el bucle push-pull.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(household: HouseholdEntity)
}
