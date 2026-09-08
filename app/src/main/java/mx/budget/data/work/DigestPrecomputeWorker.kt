package mx.budget.data.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import mx.budget.BudgetApplication
import mx.budget.ai.rag.OpenAnalysisAnswerer
import java.util.concurrent.TimeUnit

/**
 * Deja caliente el digest del análisis abierto.
 *
 * Armarlo cuesta nueve consultas al ledger, y hasta ahora se pagaban en el hilo de
 * la pregunta, justo antes de una inferencia que en CPU ya es lenta de por sí. Este
 * worker lo arma una vez al día y lo guarda con la firma de la quincena; si nada
 * cambió, la pregunta lo reusa tal cual.
 *
 * No toca el LLM: solo consultas y texto. Por eso puede correr en background sin
 * riesgo de OOM ni del bloqueo de AICore.
 */
class DigestPrecomputeWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as BudgetApplication
        return try {
            val answerer = OpenAnalysisAnswerer(
                context = app,
                llm = app.onDeviceLlm,
                quincenaRepository = app.quincenaRepository,
                analyticsRepository = app.analyticsRepository,
                expenseRepository = app.expenseRepository,
                incomeRepository = app.incomeRepository,
                installmentRepository = app.installmentRepository,
                settings = app.settingsRepository,
            )
            answerer.precomputeDigest(app.householdId)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "open_analysis_digest"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DigestPrecomputeWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
