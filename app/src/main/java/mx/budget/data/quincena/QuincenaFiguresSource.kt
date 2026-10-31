package mx.budget.data.quincena

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import mx.budget.data.local.dao.AnalyticsDao
import mx.budget.data.local.dao.QuincenaDao
import mx.budget.data.local.entity.QuincenaEntity
import mx.budget.data.local.result.CategoryCapRow

/**
 * Los dos terminos que la Fase 7b sumo a la convencion de [QuincenaFigures]:
 * los topes por gastar y el arrastre de la quincena anterior.
 *
 * Cada superficie ya tenia sus propias fuentes de ingreso, gastado y reservado;
 * lo que comparten es esto, para que dashboard, resumen de Analiticas,
 * asistente y reloj lleguen al mismo disponible. El hogar se toma de la propia
 * quincena, asi que no hay que reconstruir esta clase al cambiar de hogar.
 */
class QuincenaFiguresSource(
    private val analyticsDao: AnalyticsDao,
    private val quincenaDao: QuincenaDao,
) {

    /** Topes por gastar y arrastre de una quincena, con las filas que los explican. */
    data class Extras(
        val caps: List<CategoryCapRow> = emptyList(),
        val carry: CarryOver? = null,
    ) {
        val capsPending: Double get() = CategoryCaps.totalPending(caps)
        val carryOver: Double get() = carry?.effective ?: 0.0
    }

    fun observeExtras(quincena: QuincenaEntity): Flow<Extras> {
        val previousDate = previousQuincenaDate(quincena)
        val previous: Flow<QuincenaEntity?> =
            if (previousDate == null) flowOf(null)
            else quincenaDao.observeForDate(quincena.householdId, previousDate)
        return combine(
            analyticsDao.observeCategoryCaps(quincena.householdId, quincena.id),
            previous,
        ) { caps, prev -> Extras(caps, carryOverFrom(prev)) }
    }

    suspend fun extrasFor(quincena: QuincenaEntity): Extras {
        val caps = analyticsDao.getCategoryCaps(quincena.householdId, quincena.id)
        val prev = previousQuincenaDate(quincena)?.let { quincenaDao.getForDate(quincena.householdId, it) }
        return Extras(caps, carryOverFrom(prev))
    }
}
