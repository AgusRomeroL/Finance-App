package mx.budget.service

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import mx.budget.BudgetApplication
import mx.budget.core.wear.WearPaths

/**
 * Servicio residente en el Móvil que escucha capturas entrantes desde el Reloj
 * (Apéndice G.3, superficie reloj).
 *
 * **Propose-then-confirm**: el reloj NO escribe en el ledger. Lo que llega cae en
 * la bandeja unificada `pending_capture` con `source=WATCH` y el usuario lo
 * confirma en el teléfono (chip "Reloj · captura"). Dos rutas, ambas reusando el
 * pipeline NL de §G.3 a través de [BudgetApplication.captureNaturalLanguage], que
 * corre en el `appScope` de la app (sobrevive a este servicio efímero):
 *  - [WearPaths.PATH_NEW_EXPENSE]: preset "amount|concept" → se rearma como frase.
 *  - [WearPaths.PATH_NEW_NL]: texto dictado en el reloj (el reloj NO corre LLM;
 *    el parseo/LLM viven en el teléfono).
 */
class BudgetWearListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val app = applicationContext as BudgetApplication
        when (messageEvent.path) {
            WearPaths.PATH_NEW_EXPENSE -> {
                // Payload "amount|concept" (preset del reloj). Lo rearmamos como una
                // frase NL ("concepto monto") que el parser determinista resuelve,
                // así reusamos un solo camino (sin tocar BankCaptureManager).
                // `limit = 2`: el monto va primero y no puede contener '|'; el resto
                // ES el concepto (un concepto dictado con '|' no debe descartarse).
                val raw = String(messageEvent.data)
                val parts = raw.split("|", limit = 2)
                val amount = parts.getOrNull(0)?.toDoubleOrNull()
                val concept = parts.getOrNull(1)?.trim()
                if (amount != null && !concept.isNullOrEmpty()) {
                    app.captureNaturalLanguage("$concept $amount", "WATCH")
                } else {
                    Log.w(TAG, "Payload de gasto del reloj no parsea (\"$raw\"); se ignora")
                }
            }
            WearPaths.PATH_NEW_NL -> {
                val text = String(messageEvent.data).trim()
                if (text.isNotEmpty()) app.captureNaturalLanguage(text, "WATCH")
            }
            WearPaths.PATH_NEW_INCOME -> {
                // Payload "amount|label". El ingreso NO va a la bandeja (es de gasto);
                // se inserta directo como PLANNED en la quincena activa. Mismo criterio
                // que el gasto: `limit = 2` para no descartar etiquetas con '|'.
                val raw = String(messageEvent.data)
                val parts = raw.split("|", limit = 2)
                val amount = parts.getOrNull(0)?.toDoubleOrNull()
                val label = parts.getOrNull(1)?.trim()
                if (amount != null && !label.isNullOrEmpty()) {
                    app.captureWatchIncome(amount, label)
                } else {
                    Log.w(TAG, "Payload de ingreso del reloj no parsea (\"$raw\"); se ignora")
                }
            }
            WearPaths.PATH_CONFIRM_PENDING -> {
                val id = String(messageEvent.data).trim()
                if (id.isNotEmpty()) app.confirmPendingFromWatch(id)
            }
            WearPaths.PATH_DISCARD_PENDING -> {
                val id = String(messageEvent.data).trim()
                if (id.isNotEmpty()) app.dismissPendingFromWatch(id)
            }
            WearPaths.PATH_REQUEST_SYNC -> {
                // Pull-on-open: el reloj pide un snapshot fresco al abrir (§G.3.3).
                // Re-empujamos el estado para que "Disponible" refleje la cifra real
                // sin depender de que el dashboard del teléfono esté en foreground.
                app.pushWearSnapshot()
            }
            else -> super.onMessageReceived(messageEvent)
        }
    }

    private companion object {
        const val TAG = "BudgetWearListener"
    }
}
