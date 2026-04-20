package mx.budget.data.repository.impl

import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.dao.QuincenaDao
import mx.budget.data.local.entity.QuincenaEntity
import mx.budget.data.local.result.QuincenaSnapshot
import mx.budget.data.repository.QuincenaRepository

class QuincenaRepositoryImpl(
    private val dao: QuincenaDao
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

    override suspend fun provision(householdId: String, year: Int, month: Int, half: String): String {
        val id = java.util.UUID.randomUUID().toString()

        // Calcular fechas según la mitad del mes
        val paddedMonth = month.toString().padStart(2, '0')
        val startDay = if (half == "FIRST") "01" else "16"
        val lastDay = java.util.Calendar.getInstance().apply {
            set(year, month - 1, 1)
        }.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        val endDay = if (half == "FIRST") "15" else lastDay.toString().padStart(2, '0')

        val startDate = "$year-$paddedMonth-$startDay"
        val endDate   = "$year-$paddedMonth-$endDay"

        val monthNames = listOf("Ene","Feb","Mar","Abr","May","Jun",
                                "Jul","Ago","Sep","Oct","Nov","Dic")
        val halfNum = if (half == "FIRST") "Q1" else "Q2"
        val label = "$halfNum ${monthNames.getOrElse(month - 1) { paddedMonth }} $year"

        dao.insert(
            QuincenaEntity(
                id = id,
                householdId = householdId,
                year = year,
                month = month,
                half = half,
                startDate = startDate,
                endDate = endDate,
                label = label,
                status = "PROVISIONED"
            )
        )
        return id
    }

    override suspend fun activate(quincenaId: String) =
        dao.updateStatus(quincenaId, "ACTIVE")

    override suspend fun startClosingReview(quincenaId: String) =
        dao.updateStatus(quincenaId, "CLOSING_REVIEW")

    override suspend fun close(quincenaId: String) =
        dao.updateStatus(quincenaId, "CLOSED")

    override suspend fun recalculateActuals(quincenaId: String) {
        // No-op: los totales se actualizan vía triggers o consultas del DAO
    }
}
