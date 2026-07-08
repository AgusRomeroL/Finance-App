package mx.budget.data.installments

import java.time.LocalDate

/** Una cuota proyectada de un plan MSI. */
data class ScheduledInstallment(
    /** Número de cuota, 1-indexado (1..total). */
    val number: Int,
    val date: LocalDate,
    val amountMxn: Double,
)

/**
 * Lógica **pura** (sin Android/Room) para proyectar el calendario de un plan a
 * meses. El modelo `installment_plan` guarda `startDate`, `currentInstallment`,
 * `totalInstallments`, `installmentAmountMxn`, pero NO la fecha de cada cuota — el
 * DAO devuelve `nextDate = NULL`. Aquí se deriva: cada cuota cae un mes después de
 * la anterior, el día del mes = `dueDay` del wallet que la liquida (o el día de
 * `startDate` si no hay dueDay). Corrige el gap sin tocar SQL ni el esquema.
 */
object InstallmentSchedule {

    /**
     * @param startDate primera cuota (ISO YYYY-MM-DD).
     * @param totalInstallments total de cuotas del plan.
     * @param currentInstallment cuotas ya pagadas (0..total).
     * @param installmentAmountMxn monto por cuota.
     * @param dueDay día del mes de cargo del wallet que liquida (1..31), o null.
     */
    fun schedule(
        startDate: LocalDate,
        totalInstallments: Int,
        installmentAmountMxn: Double,
        dueDay: Int?,
    ): List<ScheduledInstallment> {
        if (totalInstallments <= 0) return emptyList()
        val dom = dueDay?.coerceIn(1, 31)
        return (1..totalInstallments).map { n ->
            val base = startDate.plusMonths((n - 1).toLong())
            val date = if (dom == null) base else clampDay(base.year, base.monthValue, dom)
            ScheduledInstallment(n, date, installmentAmountMxn)
        }
    }

    /** Cuotas aún no pagadas (número > currentInstallment). */
    fun remaining(
        startDate: LocalDate,
        totalInstallments: Int,
        currentInstallment: Int,
        installmentAmountMxn: Double,
        dueDay: Int?,
    ): List<ScheduledInstallment> =
        schedule(startDate, totalInstallments, installmentAmountMxn, dueDay)
            .filter { it.number > currentInstallment.coerceAtLeast(0) }

    fun remainingCount(totalInstallments: Int, currentInstallment: Int): Int =
        (totalInstallments - currentInstallment.coerceIn(0, totalInstallments)).coerceAtLeast(0)

    /** Fecha de la próxima cuota (la primera no pagada), o null si el plan terminó. */
    fun nextChargeDate(
        startDate: LocalDate,
        totalInstallments: Int,
        currentInstallment: Int,
        dueDay: Int?,
    ): LocalDate? = remaining(startDate, totalInstallments, currentInstallment, 0.0, dueDay)
        .firstOrNull()?.date

    /** Fecha estimada de la última cuota (fin del plan). */
    fun estimatedEndDate(startDate: LocalDate, totalInstallments: Int, dueDay: Int?): LocalDate? =
        schedule(startDate, totalInstallments, 0.0, dueDay).lastOrNull()?.date

    /** Parsea una fecha ISO tolerante (toma los primeros 10 chars); null si no parsea. */
    fun parseIso(iso: String?): LocalDate? =
        iso?.takeIf { it.length >= 10 }?.let { runCatching { LocalDate.parse(it.substring(0, 10)) }.getOrNull() }

    private fun clampDay(year: Int, month: Int, day: Int): LocalDate {
        val last = LocalDate.of(year, month, 1).lengthOfMonth()
        return LocalDate.of(year, month, day.coerceIn(1, last))
    }
}
