package mx.budget.data.installments

import mx.budget.data.local.dao.ExpenseDao
import mx.budget.data.local.dao.MemberDao
import mx.budget.data.local.dao.QuincenaDao
import mx.budget.data.local.entity.ExpenseAttributionEntity
import mx.budget.data.local.entity.ExpenseEntity
import mx.budget.data.repository.ExpenseRepository
import mx.budget.data.repository.InstallmentRepository
import mx.budget.data.repository.WalletRepository
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/**
 * Proyecta las cuotas MSI restantes como gastos **PLANNED** en la quincena ACTIVA,
 * para que aparezcan en el calendario y disparen recordatorios (CalendarViewModel y
 * ReminderWorker ya operan sobre PLANNED — Confirmar/Editar/Posponer). No existe hoy
 * ninguna proyección temporal de installments; esto la crea sin tocar el esquema.
 *
 * Idempotente por `(installment_plan_id, installment_number)`: si ya existe un gasto
 * de esa cuota en CUALQUIER status no inserta otro. PLANNED no ajusta saldo. Al
 * confirmar la cuota real desde un estado de cuenta, el import convierte el PLANNED
 * en POSTED (dedupe por el mismo par).
 */
class InstallmentMaterializer(
    private val householdId: String,
    private val installmentRepository: InstallmentRepository,
    private val walletRepository: WalletRepository,
    private val quincenaDao: QuincenaDao,
    private val expenseDao: ExpenseDao,
    private val expenseRepository: ExpenseRepository,
    private val memberDao: MemberDao,
) {
    suspend fun materialize() {
        val active = quincenaDao.getActive(householdId) ?: return
        val start = InstallmentSchedule.parseIso(active.startDate) ?: return
        val end = InstallmentSchedule.parseIso(active.endDate) ?: return

        val members = memberDao.getActiveMembers(householdId)
            .filter { !it.role.startsWith("EXTERNAL_") }
        if (members.isEmpty()) return
        val payerId = members.firstOrNull { it.displayName.lowercase().contains("norma") }?.id
            ?: members.first().id
        val benBps = equalSplit(members.map { it.id })

        val dueByWallet = walletRepository.getActive(householdId).associate { it.id to it.dueDay }
        val plans = installmentRepository.getActive(householdId)
            .filter { it.status == "ACTIVE" && it.paymentMethodId != null }

        for (plan in plans) {
            val startDate = InstallmentSchedule.parseIso(plan.startDate) ?: continue
            val dueDay = dueByWallet[plan.paymentMethodId]
            val remaining = InstallmentSchedule.remaining(
                startDate = startDate,
                totalInstallments = plan.totalInstallments,
                currentInstallment = plan.currentInstallment,
                installmentAmountMxn = plan.installmentAmountMxn,
                dueDay = dueDay,
            )
            for (cuota in remaining) {
                if (cuota.date < start || cuota.date > end) continue
                if (expenseDao.getByPlanAndNumber(householdId, plan.id, cuota.number) != null) continue
                val expId = det("msiplan:${plan.id}:${cuota.number}")
                val expense = ExpenseEntity(
                    id = expId,
                    householdId = householdId,
                    occurredAt = cuota.date.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
                    quincenaId = active.id,
                    categoryId = plan.categoryId ?: continue,
                    concept = "${plan.displayName} (cuota ${cuota.number}/${plan.totalInstallments})".take(64),
                    amountMxn = cuota.amountMxn,
                    paymentMethodId = plan.paymentMethodId!!,
                    installmentPlanId = plan.id,
                    installmentNumber = cuota.number,
                    status = "PLANNED",
                    notes = "Cuota MSI proyectada",
                    createdAt = System.currentTimeMillis(),
                )
                val attrs = benBps.map { (mid, bps) ->
                    ExpenseAttributionEntity(det("attrp:$expId:BEN:$mid"), expId, mid, "BENEFICIARY", bps, round2(cuota.amountMxn * bps / 10000.0))
                } + ExpenseAttributionEntity(det("attrp:$expId:PAY:$payerId"), expId, payerId, "PAYER", 10000, cuota.amountMxn)
                runCatching { expenseRepository.insertWithAttributions(expense, attrs) }
            }
        }
    }

    private fun equalSplit(ids: List<String>): Map<String, Int> {
        if (ids.isEmpty()) return emptyMap()
        val base = 10000 / ids.size
        val rem = 10000 - base * ids.size
        return ids.mapIndexed { i, id -> id to (base + if (i == 0) rem else 0) }.toMap()
    }

    private fun round2(v: Double): Double = kotlin.math.round(v * 100) / 100.0
    private fun det(s: String): String = UUID.nameUUIDFromBytes(s.toByteArray()).toString()
}
