package mx.budget.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import mx.budget.ui.capture.CaptureViewModel
import mx.budget.ui.dashboard.DashboardScreen
import mx.budget.ui.dashboard.DashboardViewModel

// ─────────────────────────────────────────────────────────────────────────────
// Destinos de navegación
// ─────────────────────────────────────────────────────────────────────────────

object BudgetDestinations {
    const val DASHBOARD = "dashboard"
    const val LEDGER = "ledger"
    const val WALLETS = "wallets"
    const val ANALYTICS = "analytics"
    const val PROFILE = "profile"
}

// ─────────────────────────────────────────────────────────────────────────────
// BudgetNavGraph — Grafo de navegación principal
// ─────────────────────────────────────────────────────────────────────────────

/**
 * @param dashboardViewModel ViewModel del dashboard.
 * @param captureViewModel   ViewModel del modal de captura rápida.
 * @param windowWidthDp      Ancho de ventana en dp.
 */
@Composable
fun BudgetNavGraph(
    dashboardViewModel: DashboardViewModel,
    captureViewModel: CaptureViewModel,
    windowWidthDp: Int = 360
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = BudgetDestinations.DASHBOARD
    ) {
        composable(route = BudgetDestinations.DASHBOARD) {
            DashboardScreen(
                viewModel = dashboardViewModel,
                captureViewModel = captureViewModel,
                windowWidthDp = windowWidthDp.dp
            )
        }

        composable(route = BudgetDestinations.LEDGER) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Libro Mayor — Próxima fase")
            }
        }

        composable(route = BudgetDestinations.WALLETS) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Cuentas — Próxima fase")
            }
        }

        composable(route = BudgetDestinations.ANALYTICS) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Analíticas — Próxima fase")
            }
        }

        composable(route = BudgetDestinations.PROFILE) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Perfil — Próxima fase")
            }
        }
    }
}
