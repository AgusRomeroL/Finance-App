package mx.budget.data.statements

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import mx.budget.BudgetApplication
import java.time.LocalDate
import java.time.ZoneId

/**
 * Recordatorio mensual de estados de cuenta por importar (Tarea 4).
 *
 * Corre periódicamente (24h). En cada corrida calcula, con
 * [StatementCycleTracker], qué tarjetas tienen su estado del ciclo PENDIENTE y, si
 * hay alguna nueva respecto a lo ya avisado, postea UNA notificación resumen. El
 * dedupe se persiste en DataStore (`statement_cycle_notified`) con clave
 * `walletId:corteISO`, podado al ciclo vigente para que el próximo corte re-avise.
 *
 * No usa alarmas exactas; el aviso mensual tolera el diferimiento de WorkManager
 * (Doze). La tarjeta del checklist no depende de este worker.
 */
class StatementReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as BudgetApplication
        val settings = app.settingsRepository
        return try {
            val today = LocalDate.now(ZoneId.of("America/Mexico_City"))
            val wallets = app.walletRepository.getActive(app.householdId)
            val lastImports = app.database.statementImportDao()
                .getLastImportEndByWallet(app.householdId)
                .associate { it.walletId to parseIso(it.lastPeriodEnd) }

            val statuses = StatementCycleTracker.compute(wallets, lastImports, today)
            val pending = StatementCycleTracker.pending(statuses)

            // Claves del ciclo vigente + las ya avisadas.
            val pendingKeys = pending.associateBy { "${it.walletId}:${it.expectedCutoff}" }
            val alreadyNotified = settings.getStatementCycleNotified()
            val newKeys = pendingKeys.keys - alreadyNotified

            if (newKeys.isNotEmpty()) {
                StatementReminderNotifier.notifyPending(
                    applicationContext,
                    pending.map { it.walletName },
                )
            }
            // Poda: el set queda = ciclo vigente (los cortes viejos caen y re-avisan
            // al próximo ciclo; los ya importados desaparecen de pending).
            settings.setStatementCycleNotified(pendingKeys.keys)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun parseIso(s: String?): LocalDate? =
        if (s.isNullOrBlank()) null else runCatching { LocalDate.parse(s.take(10)) }.getOrNull()

    companion object {
        const val UNIQUE_NAME = "statement_reminders"
    }
}
