package mx.budget.service

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
                val parts = String(messageEvent.data).split("|")
                if (parts.size == 2) {
                    val amount = parts[0].toDoubleOrNull() ?: return
                    val concept = parts[1].trim()
                    app.captureNaturalLanguage("$concept $amount", "WATCH")
                }
            }
            WearPaths.PATH_NEW_NL -> {
                val text = String(messageEvent.data).trim()
                if (text.isNotEmpty()) app.captureNaturalLanguage(text, "WATCH")
            }
            WearPaths.PATH_NEW_INCOME -> {
                // Payload "amount|label". El ingreso NO va a la bandeja (es de gasto);
                // se inserta directo como PLANNED en la quincena activa.
                val parts = String(messageEvent.data).split("|")
                if (parts.size == 2) {
                    val amount = parts[0].toDoubleOrNull() ?: return
                    app.captureWatchIncome(amount, parts[1].trim())
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
}
