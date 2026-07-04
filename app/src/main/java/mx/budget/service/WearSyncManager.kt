package mx.budget.service

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import mx.budget.core.wear.WearPaths
import mx.budget.ui.dashboard.DashboardUiState
import mx.budget.ui.dashboard.DashboardViewModel

/**
 * Gestor pasivo residente en el módulo móvil.
 * Observa el estado principal de la Dashboard y retransmite asincrónicamente el
 * snapshot completo del presupuesto (saldo + recomendados + movimientos +
 * bandeja + gasto por miembro) hacia los nodos acoplados vía [WearSnapshotBuilder].
 */
class WearSyncManager(
    private val context: Context,
    private val dashboardViewModel: DashboardViewModel,
    private val appScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    fun startSyncObservation() {
        appScope.launch {
            // collectLatest: si el dashboard emite de nuevo mientras se arma un
            // snapshot (consulta Room), se cancela el anterior y se rehace con el
            // estado más reciente — evita apilar builds redundantes.
            dashboardViewModel.uiState.collectLatest { state ->
                if (state is DashboardUiState.Success) {
                    WearSnapshotBuilder.push(context)
                }
            }
        }
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
