package mx.budget.data.budget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import mx.budget.BudgetApplication
import mx.budget.data.quincena.quincenaProgress
import java.util.concurrent.TimeUnit

/**
 * Revisa los topes por categoria de la quincena activa y avisa al 80 %, al 100 %
 * y antes, cuando el pronostico pasa el tope (Fase 7b, alcance 2).
 *
 * Corre de dos formas:
 * - **Periodico**, cada 15 minutos, para lo que llega por el pull desde otro
 *   telefono o de un estado de cuenta.
 * - **Al momento**, con [requestCheck], unos segundos despues de que cambia lo
 *   gastado en la quincena (lo observa [BudgetApplication]): el aviso llega con
 *   el gasto que cruzo el umbral y no hasta 15 minutos despues. Ademas sirve para
 *   verificar en el emulador, donde `jobscheduler run -f` no salta el periodo
 *   minimo de WorkManager.
 *
 * Una vez por categoria, quincena y nivel, con la marca en DataStore podada a la
 * quincena activa. Solo se marca lo que de verdad se publico.
 */
class CategoryBudgetAlertWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as BudgetApplication
        val settings = app.settingsRepository
        return try {
            if (!settings.budgetAlertsEnabled.first()) return Result.success()
            val quincena = app.database.quincenaDao().getActive(app.householdId) ?: return Result.success()
            val caps = app.database.analyticsDao().getCategoryCaps(quincena.householdId, quincena.id)
            val progress = quincenaProgress(quincena.startDate, quincena.endDate)

            val prefix = quincena.id + "|"
            val sent = settings.getBudgetAlertsSent().filterTo(HashSet()) { it.startsWith(prefix) }
            for (row in caps) {
                val projected = CategoryForecast.projected(row.spent, progress.fraction, progress.dayIndex)
                val level = CategoryForecast.level(row.spent, row.cap, projected) ?: continue
                // Un nivel ya avisado, o uno mayor, calla este.
                val yaAvisado = BudgetAlertLevel.entries
                    .filter { it >= level }
                    .any { "$prefix${row.categoryId}|${it.name}" in sent }
                if (yaAvisado) continue
                if (BudgetAlertNotifier.notify(applicationContext, quincena, row, level, projected)) {
                    BudgetAlertLevel.entries.filter { it <= level }
                        .forEach { sent += "$prefix${row.categoryId}|${it.name}" }
                }
            }
            settings.setBudgetAlertsSent(sent)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "category_budget_alerts"
        const val CHECK_NAME = "category_budget_check"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<CategoryBudgetAlertWorker>(15, TimeUnit.MINUTES).build(),
            )
        }

        /** Revision unica en unos segundos; varias peticiones seguidas se funden en una. */
        fun requestCheck(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                CHECK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<CategoryBudgetAlertWorker>()
                    .setInitialDelay(3, TimeUnit.SECONDS)
                    .build(),
            )
        }
    }
}
