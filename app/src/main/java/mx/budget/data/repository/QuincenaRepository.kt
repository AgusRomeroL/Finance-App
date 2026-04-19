package mx.budget.data.repository

import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.entity.QuincenaEntity
import mx.budget.data.local.result.QuincenaSnapshot

/**
 * Contrato del repositorio de quincenas.
 *
 * Abstrae el acceso a datos de la capa de persistencia para
 * la máquina de estados del ciclo quincenal y las analíticas históricas.
 */
interface QuincenaRepository {

    /**
     * Observa la quincena activa del household.
     * Emite null si no hay ninguna activa (raro, solo al inicio).
     */
    fun observeActive(householdId: String): Flow<QuincenaEntity?>

    /** Obtiene la quincena activa del household. */
    suspend fun getActive(householdId: String): QuincenaEntity?

    /** Obtiene una quincena por su ID. */
    suspend fun getById(id: String): QuincenaEntity?

    /** Observa todas las quincenas del household (timeline). */
    fun observeAll(householdId: String): Flow<List<QuincenaEntity>>

    /**
     * Snapshots de las últimas N quincenas cerradas.
     * Alimenta el pronóstico de liquidez y el análisis de varianza.
     */
    fun observeClosedSnapshots(householdId: String, n: Int = 6): Flow<List<QuincenaSnapshot>>

    suspend fun getClosedSnapshots(householdId: String, n: Int = 6): List<QuincenaSnapshot>

    /**
     * Provisiona una nueva quincena: crea la entidad con status PROVISIONED
     * y materializa los gastos PLANNED desde las plantillas recurrentes
     * que apliquen a esta cadencia.
     *
     * @return ID de la quincena creada.
     */
    suspend fun provision(
        householdId: String,
        year: Int,
        month: Int,
        half: String
    ): String

    /**
     * Transición PROVISIONED → ACTIVE.
     * Invariante: no puede haber otra ACTIVE en el mismo household.
     */
    suspend fun activate(quincenaId: String)

    /**
     * Transición ACTIVE → CLOSING_REVIEW.
     * El usuario verifica gastos pendientes antes de cerrar.
     */
    suspend fun startClosingReview(quincenaId: String)

    /**
     * Transición CLOSING_REVIEW → CLOSED.
     * Congela todos los datos. Snapshot de KPIs finales.
     */
    suspend fun close(quincenaId: String)

    /**
     * Recalcula los totales actuales de la quincena
     * sumando todos los POSTED + income POSTED.
     */
    suspend fun recalculateActuals(quincenaId: String)
}
