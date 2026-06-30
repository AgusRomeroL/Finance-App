package mx.budget.service

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import mx.budget.core.wear.WearPaths
import mx.budget.ui.dashboard.DashboardUiState
import mx.budget.ui.dashboard.DashboardViewModel

/**
 * Gestor pasivo residente en el módulo móvil.
 * Observa el estado principal de la Dashboard y retransmite
 * asincrónicamente el presupuesto restante (Balance) hacia los nodos acoplados.
 */
class WearSyncManager(
    private val context: Context,
    private val dashboardViewModel: DashboardViewModel,
    private val appScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    fun startSyncObservation() {
        dashboardViewModel.uiState
            .onEach { state ->
                if (state is DashboardUiState.Success) {
                    pushSnapshot(
                        context = context,
                        balance = state.balance,
                        quincenaLabel = state.quincena?.label ?: "Sin Quincena"
                    )
                }
            }
            .launchIn(appScope)
    }

    companion object {
        /**
         * Empuja un snapshot de saldo al reloj (Data Layer → tile Glance), sin
         * depender de ningún ViewModel/Activity. Lo usan dos productores:
         *  - [startSyncObservation] mientras la app está en foreground (live).
         *  - [mx.budget.data.reminder.ReminderWorker] cada ~15 min (process-alive,
         *    incluso con la app cerrada) → el tile no queda obsoleto (§G.3).
         *
         * `setUrgent()` fuerza la entrega inmediata. Best-effort: si no hay reloj
         * emparejado, el Data Layer cachea/no-op.
         */
        fun pushSnapshot(context: Context, balance: Double, quincenaLabel: String) {
            val req = PutDataMapRequest.create(WearPaths.PATH_BUDGET_SYNC)
            req.dataMap.apply {
                putDouble(WearPaths.KEY_BALANCE_DISPONIBLE, balance)
                putString(WearPaths.KEY_QUINCENA_LABEL, quincenaLabel)
                putLong(WearPaths.KEY_TIMESTAMP, System.currentTimeMillis())
            }
            Wearable.getDataClient(context).putDataItem(req.asPutDataRequest().setUrgent())
        }
    }
}
