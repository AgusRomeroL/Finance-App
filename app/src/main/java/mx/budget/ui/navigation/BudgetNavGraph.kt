package mx.budget.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import mx.budget.ui.capture.CaptureViewModel
import mx.budget.ui.dashboard.DashboardScreen
import mx.budget.ui.dashboard.DashboardViewModel
import mx.budget.ui.profile.ProfileScreen

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
    windowWidthDp: Int = 360,
    dynamicColor: Boolean = true,
    onDynamicColorChange: (Boolean) -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: BudgetDestinations.DASHBOARD

    val onNavigate: (String) -> Unit = { route ->
        if (route != currentRoute) {
            navController.navigate(route) {
                // Pop to start destination to avoid building up a back stack
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = BudgetDestinations.DASHBOARD
    ) {
        composable(route = BudgetDestinations.DASHBOARD) {
            DashboardScreen(
                viewModel = dashboardViewModel,
                captureViewModel = captureViewModel,
                windowWidthDp = windowWidthDp.dp,
                currentRoute = currentRoute,
                onNavigate = onNavigate
            )
        }

        composable(route = BudgetDestinations.LEDGER) {
            PlaceholderScreen("Libro Mayor", onNavigate)
        }

        composable(route = BudgetDestinations.WALLETS) {
            PlaceholderScreen("Cuentas y Wallets", onNavigate)
        }

        composable(route = BudgetDestinations.ANALYTICS) {
            PlaceholderScreen("Analíticas e IA", onNavigate)
        }

        composable(route = BudgetDestinations.PROFILE) {
            ProfileScreen(
                dynamicColor = dynamicColor,
                onDynamicColorChange = onDynamicColorChange,
                onBack = { onNavigate(BudgetDestinations.DASHBOARD) }
            )
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String, onNavigate: (String) -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Esta pantalla está programada para la siguiente fase.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = { onNavigate(BudgetDestinations.DASHBOARD) }) {
                Text("Volver al Dashboard")
            }
        }
    }
}

