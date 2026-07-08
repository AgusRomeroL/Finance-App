package mx.budget.data.statements

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import mx.budget.BudgetApplication
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Recordatorio de fecha límite de pago por tarjeta (estados v2 Fase 5).
 *
 * Corre cada 24h. Fuente: la fila `statement_import` más reciente y completa por
 * wallet (trae `fecha_limite_pago`, `pago_minimo`, `pago_no_intereses`). Si la fecha
 * ya pasó, proyecta la siguiente con `payment_method.dueDay`. Notifica a T-3 y T-1
 * días; dedupe por `walletId:fechaISO:tramo` en DataStore.
 */
class PaymentDueReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as BudgetApplication
        val settings = app.settingsRepository
        return try {
            val today = LocalDate.now(ZoneId.of("America/Mexico_City"))
            val money = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
            val wallets = app.walletRepository.getActive(app.householdId)
                .filter { StatementCycleTracker.isStatementCard(it.kind) }
            val latest = app.database.statementImportDao()
                .getLatestFullByWallet(app.householdId)
                .associateBy { it.walletId }

            val already = settings.getPaymentDueNotified()
            val fresh = HashSet(already)

            for (w in wallets) {
                val imp = latest[w.id]
                val dueDate = nextDue(imp?.fechaLimitePago, w.dueDay, today) ?: continue
                val days = ChronoUnit.DAYS.between(today, dueDate)
                val tramo = when (days) {
                    3L -> "T3"
                    1L -> "T1"
                    0L -> "T0"
                    else -> continue
                }
                val key = "${w.id}:$dueDate:$tramo"
                if (key in already) continue
                val detail = buildString {
                    imp?.pagoMinimo?.let { append("mínimo ${money.format(it)}") }
                    imp?.pagoNoIntereses?.let {
                        if (isNotEmpty()) append(" · ")
                        append("sin intereses ${money.format(it)}")
                    }
                    if (isEmpty()) append("Revisa tu estado de cuenta.")
                }
                PaymentDueNotifier.notifyDue(applicationContext, w.id, w.displayName, dueDate.toString(), detail)
                fresh += key
            }
            // Poda: conserva solo claves de fechas futuras/vigentes (evita crecer sin fin).
            settings.setPaymentDueNotified(fresh.filter { it.substringAfter(':').substringBefore(':') >= today.toString() }.toSet())
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    /** Fecha límite vigente: la del último estado si es futura; si no, proyecta con dueDay. */
    private fun nextDue(fechaLimite: String?, dueDay: Int?, today: LocalDate): LocalDate? {
        val fromStatement = fechaLimite?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
        if (fromStatement != null && !fromStatement.isBefore(today)) return fromStatement
        val d = dueDay ?: return null
        // Próximo día `d` del mes en o después de hoy.
        val thisMonth = clamp(today.year, today.monthValue, d)
        return if (!thisMonth.isBefore(today)) thisMonth
        else today.plusMonths(1).let { clamp(it.year, it.monthValue, d) }
    }

    private fun clamp(year: Int, month: Int, day: Int): LocalDate {
        val last = LocalDate.of(year, month, 1).lengthOfMonth()
        return LocalDate.of(year, month, day.coerceIn(1, last))
    }

    companion object {
        const val UNIQUE_NAME = "payment_due_reminders"
    }
}
