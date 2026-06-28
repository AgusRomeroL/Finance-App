package mx.budget.data.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mx.budget.BudgetApplication

/** Constantes de las acciones de la notificación de captura bancaria. */
object BankCaptureActions {
    const val CONFIRM = "mx.budget.action.BANK_CAPTURE_CONFIRM"
    const val DISMISS = "mx.budget.action.BANK_CAPTURE_DISMISS"
    const val EXTRA_CAPTURE_ID = "captureId"
}

/**
 * Recibe los taps de [Registrar]/[Descartar] de la notificación propia (Feature D)
 * y delega en el [BankCaptureManager]. Usa `goAsync()` para sobrevivir al trabajo
 * de DB tras devolver el control del `onReceive`.
 */
class BankCaptureActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val captureId = intent.getStringExtra(BankCaptureActions.EXTRA_CAPTURE_ID) ?: return
        val manager = (context.applicationContext as? BudgetApplication)?.bankCaptureManager ?: return
        val action = intent.action ?: return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    BankCaptureActions.CONFIRM -> manager.confirm(captureId)
                    BankCaptureActions.DISMISS -> manager.dismiss(captureId)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
