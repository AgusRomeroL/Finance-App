package mx.budget.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.google.android.horologist.compose.layout.AppScaffold
import kotlinx.coroutines.launch
import mx.budget.wear.data.ExpenseSender
import mx.budget.wear.data.Outbox
import mx.budget.wear.data.PhoneLink
import mx.budget.wear.presentation.WearHub

/**
 * Punto de entrada del hub del reloj. [AppScaffold] inyecta el `TimeText` global;
 * el contenido es el [WearHub] (Estado / Captura / Movimientos / Pendientes) sobre
 * un `SwipeDismissableNavHost`. Los Tiles (Recomendados / Captura) son superficies
 * aparte declaradas en el manifest, no se lanzan desde aquí.
 *
 * **Pull-on-open (§G.3.3):** en cada `onResume` el reloj pide al teléfono un
 * snapshot fresco ([ExpenseSender.requestSync]). Sin esto, al abrir la app antes de
 * que el teléfono hubiera empujado (su primer arranque emparejado) el cache está
 * vacío y "Disponible" queda en $0. Con el pull, el teléfono re-empuja, el
 * [mx.budget.wear.data.MobileSyncListenerService] repuebla el cache y la UI,
 * que ahora observa `SharedPreferences`, refleja la cifra real en vivo.
 */
class MainActivity : ComponentActivity() {

    private val expenseSender by lazy { ExpenseSender(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppScaffold {
                WearHub()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            // El resultado del pull ES la sonda de conexión: un requestSync que
            // falla significa que no hay teléfono alcanzable. Antes se tragaba en
            // un runCatching y la pantalla enseñaba un $0 sin explicar nada.
            val ok = runCatching { expenseSender.requestSync() }.getOrNull()?.isSuccess == true
            PhoneLink.setReachable(applicationContext, ok)
            // Y si volvió el teléfono, se vacía lo que se capturó sin él.
            if (ok) runCatching { Outbox.drain(applicationContext, expenseSender) }
        }
    }
}
