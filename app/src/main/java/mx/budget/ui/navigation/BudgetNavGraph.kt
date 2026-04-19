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
import mx.budget.ui.dashboard.DashboardScreen
import mx.budget.ui.dashboard.DashboardViewModel

// ─────────────────────────────────────────────────────────────────────────────
// Destinos de navegación
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Destinos de la navegación principal de la aplicación.
 *
 * Se definen como constantes de objeto para evitar strings dispersos
 * y facilitar la extensión con argumentos tipados en fases futuras.
 */
object BudgetDestinations {
    /** Pantalla principal con KPIs y libro mayor. */
    const val DASHBOARD = "dashboard"

    /** Libro mayor completo (próxima fase). */
    const val LEDGER = "ledger"

    /** Pantalla de wallets y conciliación (próxima fase). */
    const val WALLETS = "wallets"

    /** Pantalla de analíticas históricas (próxima fase). */
    const val ANALYTICS = "analytics"

    /** Perfil y configuración del hogar (próxima fase). */
    const val PROFILE = "profile"
}

// ─────────────────────────────────────────────────────────────────────────────
// BudgetNavGraph — Grafo de navegación principal
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Grafo de navegación de la aplicación de presupuesto familiar.
 *
 * Punto de entrada de Compose Navigation. El [NavHost] controla las
 * transiciones entre pantallas; cada destino corresponde a una pantalla
 * completa de nivel superior.
 *
 * **Integración de ViewModels**:
 * Los ViewModels se instancian y se pasan como parámetros para mantener
 * los Composables puros y testeables.
 *
 * **Foldable awareness**:
 * El ancho de ventana se pasa a [DashboardScreen] para activar el
 * layout de dos paneles en pantallas ≥ 600dp.
 *
 * @param dashboardViewModel ViewModel del dashboard inyectado externamente.
 * @param windowWidthDp      Ancho disponible de la ventana en dp.
 *                           Default conservador = 360dp (pantalla compacta).
 */
@Composable
fun BudgetNavGraph(
    dashboardViewModel: DashboardViewModel,
    windowWidthDp: Int = 360
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = BudgetDestinations.DASHBOARD
    ) {
        // ── Dashboard ─────────────────────────────────────────────────────────
        composable(route = BudgetDestinations.DASHBOARD) {
            DashboardScreen(
                viewModel = dashboardViewModel,
                windowWidthDp = windowWidthDp.dp
            )
        }

        // ── Ledger (próxima fase) ─────────────────────────────────────────────
        composable(route = BudgetDestinations.LEDGER) {
            // TODO: Fase 3 — LedgerScreen con tabla paginada y búsqueda
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Libro Mayor — Próxima fase")
            }
        }

        // ── Wallets (próxima fase) ────────────────────────────────────────────
        composable(route = BudgetDestinations.WALLETS) {
            // TODO: Fase 3 — WalletsScreen con conciliación de saldos
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Cuentas — Próxima fase")
            }
        }

        // ── Analytics (próxima fase) ──────────────────────────────────────────
        composable(route = BudgetDestinations.ANALYTICS) {
            // TODO: Fase 3 — AnalyticsScreen con módulos A-F
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Analíticas — Próxima fase")
            }
        }

        // ── Profile (próxima fase) ────────────────────────────────────────────
        composable(route = BudgetDestinations.PROFILE) {
            // TODO: Fase 3 — ProfileScreen con configuración de hogar
            androidx.compose.foundation.layout.Box(
                modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                androidx.compose.material3.Text("Perfil — Próxima fase")
            }
        }
    }
}

// Extensión para .fillMaxSize() sin imports adicionales en los stubs
private fun androidx.compose.ui.Modifier.fillMaxSize() =
    this.then(androidx.compose.ui.Modifier)
