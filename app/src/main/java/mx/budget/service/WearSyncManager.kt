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

    private val dataClient = Wearable.getDataClient(context)

    fun startSyncObservation() {
        dashboardViewModel.uiState
            .onEach { state ->
                if (state is DashboardUiState.Success) {
                    syncBalanceToWear(
                        balance = state.balance,
                        quincenaLabel = state.quincena?.label ?: "Sin Quincena"
                    )
                }
            }
            .launchIn(appScope)
    }

    private fun syncBalanceToWear(balance: Double, quincenaLabel: String) {
        val putDataMapReq = PutDataMapRequest.create(WearPaths.PATH_BUDGET_SYNC)
        
        // Empaquetando variables atómicas en el mapa local
        putDataMapReq.dataMap.apply {
            putDouble(WearPaths.KEY_BALANCE_DISPONIBLE, balance)
            putString(WearPaths.KEY_QUINCENA_LABEL, quincenaLabel)
            putLong(WearPaths.KEY_TIMESTAMP, System.currentTimeMillis())
        }

        val putDataReq = putDataMapReq.asPutDataRequest()
        putDataReq.setUrgent() // Fuerza la actualización inmediata para Glance Tile

        // Transmisión transparente al Data Layer
        dataClient.putDataItem(putDataReq)
    }
}
