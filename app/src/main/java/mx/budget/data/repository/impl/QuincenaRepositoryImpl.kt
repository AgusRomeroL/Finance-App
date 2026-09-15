package mx.budget.data.repository.impl

import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.dao.QuincenaDao
import mx.budget.data.local.entity.QuincenaEntity
import mx.budget.data.local.result.QuincenaSnapshot
import mx.budget.data.quincena.QuincenaLifecycle
import mx.budget.data.quincena.QuincenaRollover
import mx.budget.data.repository.QuincenaRepository
import java.time.LocalDate

/**
 * Implementacion Room (fuente de verdad) del [QuincenaRepository].
 *
 * Las transiciones delegan en [QuincenaLifecycle], que sella `updated_at` y
 * encola la fila `QUINCENA` en el outbox. Antes llamaban a
 * `QuincenaDao.updateStatus`, que no toca esa marca: el cambio de estado no
 * llegaba nunca al otro dispositivo y el LWW del pull lo revertia.
 */
class QuincenaRepositoryImpl(
    private val dao: QuincenaDao,
    private val lifecycle: QuincenaLifecycle = QuincenaLifecycle(dao),
) : QuincenaRepository {

    override fun observeActive(householdId: String) =
        dao.observeActive(householdId)

    override suspend fun getActive(householdId: String) =
        dao.getActive(householdId)

    override suspend fun getById(id: String) =
        dao.getById(id)

    override fun observeAll(householdId: String): Flow<List<QuincenaEntity>> =
        dao.observeAll(householdId)

    override fun observeClosedSnapshots(householdId: String, n: Int): Flow<List<QuincenaSnapshot>> =
        dao.observeClosedSnapshots(householdId, n)

    override suspend fun getClosedSnapshots(householdId: String, n: Int): List<QuincenaSnapshot> =
        dao.getClosedSnapshots(householdId, n)

    override fun observeByStatus(householdId: String, status: String): Flow<List<QuincenaEntity>> =
        dao.observeByStatus(householdId, status)

    override suspend fun getByStatus(householdId: String, status: String): List<QuincenaEntity> =
        dao.getByStatus(householdId, status)

    override suspend fun provision(householdId: String, year: Int, month: Int, half: String): String {
        val day = if (half == "FIRST") 1 else 16
        val fecha = LocalDate.of(year, month, day)
        // Reutiliza el builder determinista del rollover en vez de replicar el
        // calculo de fechas, el nombre del mes y la etiqueta.
        val quincena = QuincenaRollover(dao, householdId).ensureForDate(fecha)
        return quincena.id
    }

    override suspend fun activate(quincenaId: String) {
        val quincena = dao.getById(quincenaId) ?: return
        lifecycle.activate(quincena)
    }

    override suspend fun startClosingReview(quincenaId: String) {
        val quincena = dao.getById(quincenaId) ?: return
        lifecycle.markPendingClose(quincena)
    }

    override suspend fun close(quincenaId: String, applyDecisions: suspend () -> Unit) =
        lifecycle.close(quincenaId, applyDecisions = applyDecisions)

    override suspend fun reopen(quincenaId: String) =
        lifecycle.reopen(quincenaId)

    override suspend fun recalculateActuals(quincenaId: String) =
        lifecycle.recalcActuals(quincenaId)
}
