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
import androidx.compose.runtime.collectAsState
import mx.budget.ui.calendar.CalendarScreen
import mx.budget.ui.calendar.CalendarViewModel
import mx.budget.ui.calendar.NewPlannedViewModel
import mx.budget.ui.calendar.RecurrenceViewModel
import mx.budget.ui.calendar.TemplatesScreen
import mx.budget.ui.capture.CaptureViewModel
import mx.budget.ui.dashboard.DashboardScreen
import mx.budget.ui.dashboard.DashboardViewModel
import mx.budget.ui.profile.ProfileScreen
import mx.budget.ui.review.AttributionReviewScreen
import mx.budget.ui.review.AttributionReviewViewModel
import mx.budget.ui.search.SearchResultsScreen
import mx.budget.ui.search.SearchViewModel
import mx.budget.ui.suggestions.AllSuggestionsScreen

// ─────────────────────────────────────────────────────────────────────────────
// Destinos de navegación
// ─────────────────────────────────────────────────────────────────────────────

object BudgetDestinations {
    const val DASHBOARD = "dashboard"
    const val CALENDAR = "calendar"
    const val TEMPLATES = "templates"
    const val LEDGER = "ledger"
    const val WALLETS = "wallets"
    const val ANALYTICS = "analytics"
    const val PROFILE = "profile"
    const val ATTRIBUTION_REVIEW = "attribution_review"
    const val SEARCH = "search"
    const val SUGGESTIONS = "suggestions"
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
    attributionReviewViewModel: AttributionReviewViewModel,
    searchViewModel: SearchViewModel,
    calendarViewModel: CalendarViewModel,
    newPlannedViewModel: NewPlannedViewModel,
    recurrenceViewModel: RecurrenceViewModel,
    windowWidthDp: Int = 360,
    dynamicColor: Boolean = true,
    onDynamicColorChange: (Boolean) -> Unit = {},
    onRenormalize: () -> Unit = {},
    bankCaptureEnabled: Boolean = false,
    onBankCaptureToggle: (Boolean) -> Unit = {},
    onGrantNotificationAccess: () -> Unit = {},
    reminderLeadDays: Int = 2,
    onReminderLeadChange: (Int) -> Unit = {}
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
                onNavigate = onNavigate,
                onOpenReview = { onNavigate(BudgetDestinations.ATTRIBUTION_REVIEW) },
                onOpenSearch = { onNavigate(BudgetDestinations.SEARCH) },
                onOpenSuggestions = { onNavigate(BudgetDestinations.SUGGESTIONS) }
            )
        }

        composable(route = BudgetDestinations.SEARCH) {
            SearchResultsScreen(
                searchViewModel = searchViewModel,
                dashboardViewModel = dashboardViewModel,
                captureViewModel = captureViewModel,
                onBack = { onNavigate(BudgetDestinations.DASHBOARD) }
            )
        }

        composable(route = BudgetDestinations.SUGGESTIONS) {
            AllSuggestionsScreen(
                dashboardViewModel = dashboardViewModel,
                captureViewModel = captureViewModel,
                onBack = { onNavigate(BudgetDestinations.DASHBOARD) }
            )
        }

        composable(route = BudgetDestinations.ATTRIBUTION_REVIEW) {
            AttributionReviewScreen(
                viewModel = attributionReviewViewModel,
                onBack = { onNavigate(BudgetDestinations.DASHBOARD) }
            )
        }

        composable(route = BudgetDestinations.CALENDAR) {
            CalendarScreen(
                viewModel = calendarViewModel,
                newPlannedViewModel = newPlannedViewModel,
                onBack = { onNavigate(BudgetDestinations.DASHBOARD) },
                onOpenTemplates = { onNavigate(BudgetDestinations.TEMPLATES) }
            )
        }

        composable(route = BudgetDestinations.TEMPLATES) {
            TemplatesScreen(
                viewModel = recurrenceViewModel,
                onBack = { onNavigate(BudgetDestinations.CALENDAR) }
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
            val pendingReviewCount by dashboardViewModel.pendingReviewCount.collectAsState()
            ProfileScreen(
                dynamicColor = dynamicColor,
                onDynamicColorChange = onDynamicColorChange,
                onBack = { onNavigate(BudgetDestinations.DASHBOARD) },
                pendingReviewCount = pendingReviewCount,
                onOpenReview = { onNavigate(BudgetDestinations.ATTRIBUTION_REVIEW) },
                onRenormalize = onRenormalize,
                bankCaptureEnabled = bankCaptureEnabled,
                onBankCaptureToggle = onBankCaptureToggle,
                onGrantNotificationAccess = onGrantNotificationAccess,
                reminderLeadDays = reminderLeadDays,
                onReminderLeadChange = onReminderLeadChange
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

