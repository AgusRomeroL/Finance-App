package mx.budget.data.capture

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.withTimeoutOrNull
import mx.budget.BudgetApplication

/**
 * Fase 2 del flujo de captura NL **crear-inmediato + enriquecer-async** (D1,
 * paquete A2).
 *
 * La captura ya existe (creada síncrona por [BankCaptureManager.ingestText] con el
 * parser determinista y `enrich_status='ENRICHING'`, pintada como "creando…" con
 * acciones bloqueadas). Este worker completa categoría/atribución con heurísticas
 * y —solo si AICore está disponible— la pasada LLM rica, sin bloquear al usuario y
 * SIN cargar Gemma (la carga de 3.7 GB en el hilo de captura era la causa del
 * OOM-kill reportado tras dictar).
 *
 * **Watchdog:** el enriquecimiento corre bajo [withTimeoutOrNull] de 10 s. Pase lo
 * que pase (timeout, excepción, o que el worker muera y se reintente), la captura
 * termina `READY` (vía [BankCaptureManager.enrichCapture], que ya escribe READY, o
 * por la red de seguridad [mx.budget.data.local.dao.PendingCaptureDao.markEnrichReady])
 * para que la tarjeta nunca quede atascada en "creando…".
 *
 * Expedited (con fallback a normal si no hay cuota) porque es trabajo de primer
 * plano percibido: el usuario está viendo la tarjeta esperando poder confirmarla.
 */
class EnrichCaptureWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val captureId = inputData.getString(KEY_CAPTURE_ID) ?: return Result.success()
        val app = applicationContext as BudgetApplication
        val manager = app.bankCaptureManager
        val pendingDao = app.database.pendingCaptureDao()

        // El enriquecimiento es best-effort; el watchdog acota la espera del LLM.
        withTimeoutOrNull(TIMEOUT_MS) {
            runCatching { manager.enrichCapture(captureId) }
                .onFailure { Log.w(TAG, "enrichCapture($captureId) falló", it) }
        } ?: Log.w(TAG, "enriquecimiento de $captureId excedió ${TIMEOUT_MS}ms; se marca READY")

        // Red de seguridad: si enrichCapture no llegó a escribir READY (timeout,
        // excepción, o la fila ya no estaba ENRICHING), garantízalo igual. Idempotente.
        runCatching { pendingDao.markEnrichReady(captureId) }
        return Result.success()
    }

    companion object {
        private const val TAG = "EnrichCaptureWorker"
        private const val KEY_CAPTURE_ID = "capture_id"
        private const val TIMEOUT_MS = 10_000L

        /** Encola el enriquecimiento de una captura recién creada. */
        fun enqueue(context: Context, captureId: String) {
            val request = OneTimeWorkRequestBuilder<EnrichCaptureWorker>()
                .setInputData(workDataOf(KEY_CAPTURE_ID to captureId))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "enrich_capture_$captureId",
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
