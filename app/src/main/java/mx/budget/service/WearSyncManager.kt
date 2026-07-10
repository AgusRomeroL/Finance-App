package mx.budget.service

import android.content.Context
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import mx.budget.ui.dashboard.DashboardUiState
import mx.budget.ui.dashboard.DashboardViewModel

/**
 * Gestor pasivo residente en el módulo móvil. Observa el estado principal de la
 * Dashboard y retransmite el snapshot completo del presupuesto (saldo +
 * recomendados + movimientos + bandeja + gasto por miembro + próximos pagos)
 * hacia los nodos acoplados vía [WearSnapshotBuilder].
 *
 * **Rendimiento:** [observe] DEBE invocarse dentro de `repeatOnLifecycle(STARTED)`
 * (ver `MainActivity`) para que la colección —y con ella los flujos Room del
 * dashboard, que usan `WhileSubscribed`— se detenga al pasar a segundo plano. El
 * `debounce` coalesce ráfagas de emisiones (varios totales cambiando a la vez) en
 * un único push. El refresco con la app cerrada lo cubre `ReminderWorker` (~15 min).
 */
class WearSyncManager(
    private val context: Context,
    private val dashboardViewModel: DashboardViewModel,
) {

    @OptIn(FlowPreview::class)
    suspend fun observe() {
        dashboardViewModel.uiState
            // Coalesce ráfagas: al abrir el dashboard los 5 flujos Room emiten casi
            // a la vez; sin debounce se dispararían 5 builds de snapshot seguidos.
            .debounce(750)
            // collectLatest: si llega otro estado mientras se arma el snapshot
            // (consulta Room), cancela el anterior y rehace con el más reciente.
            .collectLatest { state ->
                if (state is DashboardUiState.Success) {
                    WearSnapshotBuilder.push(context)
                }
            }
    }
}
