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
     * Observa las quincenas del hogar en un estado concreto, de la mas antigua
     * a la mas nueva. La usa el aviso de "pendiente de cierre".
     */
    fun observeByStatus(householdId: String, status: String): Flow<List<QuincenaEntity>>

    suspend fun getByStatus(householdId: String, status: String): List<QuincenaEntity>

    /**
     * Provisiona una quincena con **id determinista** `q-YYYY-MM-HALF` para que
     * dos dispositivos generen la misma y el pull no rompa la FK
     * `expense.quincena_id`. Antes acuniaba un UUID aleatorio, que era justo lo
     * contrario.
     *
     * @return ID de la quincena creada (o el de la existente).
     */
    suspend fun provision(
        householdId: String,
        year: Int,
        month: Int,
        half: String
    ): String

    /**
     * Transicion a ACTIVE.
     * Invariante: no puede haber otra ACTIVE en el mismo household.
     */
    suspend fun activate(quincenaId: String)

    /**
     * Transicion ACTIVE a CLOSING_REVIEW: la quincena vencio y espera cierre
     * manual. La escribe el rollover al pasar el ultimo dia del periodo.
     */
    suspend fun startClosingReview(quincenaId: String)

    /**
     * Transicion CLOSING_REVIEW a CLOSED (RF-32). Congela los totales reales y
     * bloquea la edicion de sus movimientos. [applyDecisions] corre dentro de
     * la misma transaccion: ahi la pantalla de cierre resuelve lo que quedo
     * planeado sin ejecutar.
     */
    suspend fun close(quincenaId: String, applyDecisions: suspend () -> Unit = {})

    /** Transicion CLOSED a CLOSING_REVIEW: descongela para corregir. */
    suspend fun reopen(quincenaId: String)

    /**
     * Recalcula los totales reales de la quincena desde los gastos y los
     * ingresos POSTED. Hasta la Fase 5 era un no-op y esas columnas mentian.
     */
    suspend fun recalculateActuals(quincenaId: String)
}
