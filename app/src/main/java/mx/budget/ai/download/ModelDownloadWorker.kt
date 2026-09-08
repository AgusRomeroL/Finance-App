package mx.budget.ai.download

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import mx.budget.BudgetApplication
import mx.budget.ai.service.LlmModelVariant
import mx.budget.ai.service.ModelCatalog
import mx.budget.ai.service.ModelStorage

/**
 * Descarga el modelo Gemma en segundo plano.
 *
 * Primer worker del repo con [Constraints], con servicio en primer plano y con
 * `setProgress`: son gigabytes, así que corre solo con Wi-Fi y con el teléfono
 * cargando, sobrevive a que el usuario salga de la app y publica su avance para que
 * Perfil lo pinte. Reanuda solo, porque el estado vive en el `.part` del disco.
 */
class ModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private val app get() = applicationContext as BudgetApplication

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(variant(), 0L, 0L)

    override suspend fun doWork(): Result {
        val variant = variant()
        setForeground(foregroundInfo(variant, 0L, 0L))

        val downloader = ModelDownloader()
        val manifest = downloader.fetchManifest().getOrElse {
            return fatal("No se pudo leer el catálogo de modelos: ${it.message ?: "sin detalle"}")
        }
        val expected = manifest[variant.id]
            ?: return fatal("${variant.displayName} todavía no está publicado.")

        val target = ModelStorage.file(applicationContext, variant)
        setProgress(progressData(ModelStorage.partialBytes(applicationContext, variant), expected.sizeBytes))

        val outcome = downloader.download(variant, expected, target) { written, total ->
            setProgress(progressData(written, total))
            runCatching { setForeground(foregroundInfo(variant, written, total)) }
        }

        return when (outcome) {
            is ModelDownloader.Outcome.Success -> {
                app.aiModelManager.onModelInstalled(variant)
                ModelDownloadNotifier.finished(
                    applicationContext,
                    "Asistente IA listo",
                    "${variant.displayName} quedó instalada en este dispositivo.",
                )
                Result.success(workDataOf(KEY_VARIANT to variant.id))
            }
            is ModelDownloader.Outcome.Retryable -> {
                if (runAttemptCount >= MAX_ATTEMPTS) fatal(outcome.message) else Result.retry()
            }
            is ModelDownloader.Outcome.Fatal -> fatal(outcome.message)
        }
    }

    private fun fatal(message: String): Result {
        ModelDownloadNotifier.finished(applicationContext, "No se pudo descargar el modelo", message)
        return Result.failure(workDataOf(KEY_ERROR to message))
    }

    private fun variant(): LlmModelVariant = ModelCatalog.byId(inputData.getString(KEY_VARIANT))

    private fun progressData(written: Long, total: Long): Data =
        workDataOf(KEY_BYTES to written, KEY_TOTAL to total, KEY_VARIANT to variant().id)

    private fun foregroundInfo(variant: LlmModelVariant, written: Long, total: Long): ForegroundInfo {
        val notification = ModelDownloadNotifier.progress(
            applicationContext,
            "Descargando ${variant.displayName}",
            written,
            total,
        )
        return ForegroundInfo(
            ModelDownloadNotifier.NOTIF_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    companion object {
        const val WORK_NAME = "model_download"
        const val KEY_VARIANT = "variant"
        const val KEY_BYTES = "bytes"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"

        private const val MAX_ATTEMPTS = 5

        fun enqueue(context: Context, variant: LlmModelVariant) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresCharging(true)
                .setRequiresStorageNotLow(true)
                .build()
            val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_VARIANT to variant.id))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
