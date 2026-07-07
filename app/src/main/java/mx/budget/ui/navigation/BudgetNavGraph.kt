package mx.budget.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.collectAsState
import mx.budget.ui.analytics.AnalyticsScreen
import mx.budget.ui.analytics.AnalyticsViewModel
import mx.budget.ui.calendar.CalendarScreen
import mx.budget.ui.detail.ExpenseDetailSheet
import mx.budget.ui.ledger.LedgerScreen
import mx.budget.ui.ledger.LedgerViewModel
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
import mx.budget.ui.wallets.WalletsScreen
import mx.budget.ui.wallets.WalletsViewModel

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
    const val HOUSEHOLD = "household"
    const val ONBOARDING = "onboarding"
    const val MASTERS_MEMBERS = "masters_members"
    const val MASTERS_CATEGORIES = "masters_categories"
    const val MASTERS_INCOME = "masters_income"
    const val STATEMENTS = "statements"
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
    expenseDetailViewModel: mx.budget.ui.detail.ExpenseDetailViewModel? = null,
    attributionReviewViewModel: AttributionReviewViewModel,
    searchViewModel: SearchViewModel,
    calendarViewModel: CalendarViewModel,
    newPlannedViewModel: NewPlannedViewModel,
    recurrenceViewModel: RecurrenceViewModel,
    walletsViewModel: WalletsViewModel,
    analyticsViewModel: AnalyticsViewModel? = null,
    ledgerViewModel: LedgerViewModel? = null,
    aiAssistantViewModel: mx.budget.ai.AiAssistantViewModel? = null,
    windowWidthDp: Int = 360,
    dynamicColor: Boolean = true,
    onDynamicColorChange: (Boolean) -> Unit = {},
    onRenormalize: () -> Unit = {},
    bankCaptureEnabled: Boolean = false,
    onBankCaptureToggle: (Boolean) -> Unit = {},
    onGrantNotificationAccess: () -> Unit = {},
    reminderLeadDays: Int = 2,
    onReminderLeadChange: (Int) -> Unit = {},
    calendarMirrorEnabled: Boolean = false,
    onCalendarMirrorToggle: (Boolean) -> Unit = {},
    locationLevel: String = "NONE",
    onLocationLevelChange: (String) -> Unit = {},
    householdViewModel: mx.budget.ui.household.HouseholdViewModel? = null,
    onboardingViewModel: mx.budget.ui.onboarding.OnboardingViewModel? = null,
    membersMasterViewModel: mx.budget.ui.masters.MembersMasterViewModel? = null,
    categoriesMasterViewModel: mx.budget.ui.masters.CategoriesMasterViewModel? = null,
    incomeSourcesMasterViewModel: mx.budget.ui.masters.IncomeSourcesMasterViewModel? = null,
    startOnboarding: Boolean = false,
    statementImportViewModel: mx.budget.ui.statements.StatementImportViewModel? = null,
    nvidiaApiKey: String = "",
    onNvidiaApiKeyChange: (String) -> Unit = {},
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

    // Rutas de nivel superior: llevan barra persistente (rail/bottom nav) provista por
    // MainShell y comparten la transición fade-through (M3) entre sí.
    val topLevelRoutes = remember {
        setOf(
            BudgetDestinations.DASHBOARD,
            BudgetDestinations.CALENDAR,
            BudgetDestinations.WALLETS,
            BudgetDestinations.ANALYTICS,
            BudgetDestinations.PROFILE,
        )
    }
    val isTopLevel = currentRoute in topLevelRoutes
    val isExpanded = windowWidthDp >= 600

    // Fade-through (M3) para destinos top-level no relacionados: el saliente se desvanece
    // rápido (90ms) y el entrante aparece con un leve scaleIn (0.92→1) tras 90ms. La barra
    // del shell NO se anima (está por fuera del NavHost); solo cruza el contenido.
    val fadeThroughEnter: AnimatedContentTransitionScope<*>.() -> androidx.compose.animation.EnterTransition = {
        fadeIn(animationSpec = tween(210, delayMillis = 90)) +
            scaleIn(initialScale = 0.92f, animationSpec = tween(210, delayMillis = 90))
    }
    val fadeThroughExit: AnimatedContentTransitionScope<*>.() -> androidx.compose.animation.ExitTransition = {
        fadeOut(animationSpec = tween(90))
    }
    // Slide horizontal para drill-down (rutas secundarias): entran empujando desde la
    // derecha — se sienten como un "push" apropiado para navegación jerárquica.
    val slideEnter: AnimatedContentTransitionScope<*>.() -> androidx.compose.animation.EnterTransition = {
        slideInHorizontally(animationSpec = tween(280)) { it } + fadeIn(tween(280))
    }
    val slideExit: AnimatedContentTransitionScope<*>.() -> androidx.compose.animation.ExitTransition = {
        slideOutHorizontally(animationSpec = tween(280)) { it / 4 } + fadeOut(tween(200))
    }

    // Shell persistente POR FUERA del NavHost: la barra de 5 pestañas vive aquí y no se
    // recrea al cambiar de pestaña; solo el NavHost (content) hace el cross-fade. En rutas
    // secundarias showBar=false → el shell queda transparente (contenido a pantalla completa).
    MainShell(
        currentRoute = currentRoute,
        isExpanded = isExpanded,
        showBar = isTopLevel,
        onNavigate = onNavigate,
    ) {
    NavHost(
        navController = navController,
        startDestination = if (startOnboarding && onboardingViewModel != null)
            BudgetDestinations.ONBOARDING else BudgetDestinations.DASHBOARD,
        // Default = fade-through (top-level). Las secundarias sobreescriben con slide.
        enterTransition = fadeThroughEnter,
        exitTransition = fadeThroughExit,
        popEnterTransition = fadeThroughEnter,
        popExitTransition = fadeThroughExit,
    ) {
        composable(route = BudgetDestinations.ONBOARDING) {
            if (onboardingViewModel != null) {
                mx.budget.ui.onboarding.OnboardingScreen(
                    viewModel = onboardingViewModel,
                    onFinished = {
                        navController.navigate(BudgetDestinations.DASHBOARD) {
                            popUpTo(BudgetDestinations.ONBOARDING) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
        }

        composable(route = BudgetDestinations.DASHBOARD) {
            DashboardScreen(
                viewModel = dashboardViewModel,
                captureViewModel = captureViewModel,
                detailViewModel = expenseDetailViewModel,
                windowWidthDp = windowWidthDp.dp,
                currentRoute = currentRoute,
                onNavigate = onNavigate,
                onOpenReview = { onNavigate(BudgetDestinations.ATTRIBUTION_REVIEW) },
                onOpenSearch = { onNavigate(BudgetDestinations.SEARCH) },
                onOpenSuggestions = { onNavigate(BudgetDestinations.SUGGESTIONS) }
            )
        }

        composable(
            route = BudgetDestinations.SEARCH,
            enterTransition = slideEnter, exitTransition = slideExit,
            popEnterTransition = slideEnter, popExitTransition = slideExit,
        ) {
            SearchResultsScreen(
                searchViewModel = searchViewModel,
                dashboardViewModel = dashboardViewModel,
                captureViewModel = captureViewModel,
                onBack = { onNavigate(BudgetDestinations.DASHBOARD) }
            )
        }

        composable(
            route = BudgetDestinations.SUGGESTIONS,
            enterTransition = slideEnter, exitTransition = slideExit,
            popEnterTransition = slideEnter, popExitTransition = slideExit,
        ) {
            AllSuggestionsScreen(
                dashboardViewModel = dashboardViewModel,
                captureViewModel = captureViewModel,
                onBack = { onNavigate(BudgetDestinations.DASHBOARD) }
            )
        }

        composable(
            route = BudgetDestinations.ATTRIBUTION_REVIEW,
            enterTransition = slideEnter, exitTransition = slideExit,
            popEnterTransition = slideEnter, popExitTransition = slideExit,
        ) {
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

        composable(
            route = BudgetDestinations.TEMPLATES,
            enterTransition = slideEnter, exitTransition = slideExit,
            popEnterTransition = slideEnter, popExitTransition = slideExit,
        ) {
            TemplatesScreen(
                viewModel = recurrenceViewModel,
                onBack = { onNavigate(BudgetDestinations.CALENDAR) }
            )
        }

        composable(
            route = BudgetDestinations.LEDGER,
            enterTransition = slideEnter, exitTransition = slideExit,
            popEnterTransition = slideEnter, popExitTransition = slideExit,
        ) {
            if (ledgerViewModel != null) {
                LedgerScreen(
                    viewModel = ledgerViewModel,
                    onBack = { onNavigate(BudgetDestinations.DASHBOARD) },
                    onOpenDetail = { row -> expenseDetailViewModel?.open(row) },
                )
                // Detalle Fase 1 (ver/editar/borrar) reutilizado desde el ledger.
                expenseDetailViewModel?.let { ExpenseDetailSheet(it) }
            } else {
                PlaceholderScreen("Libro Mayor", onNavigate)
            }
        }

        composable(route = BudgetDestinations.WALLETS) {
            WalletsScreen(
                viewModel = walletsViewModel,
                windowWidthDp = windowWidthDp.dp,
                onBack = { onNavigate(BudgetDestinations.DASHBOARD) }
            )
        }

        composable(route = BudgetDestinations.ANALYTICS) {
            if (analyticsViewModel != null) {
                AnalyticsScreen(
                    viewModel = analyticsViewModel,
                    onBack = { onNavigate(BudgetDestinations.DASHBOARD) },
                    onOpenLedger = if (ledgerViewModel != null) {
                        { onNavigate(BudgetDestinations.LEDGER) }
                    } else null,
                    aiViewModel = aiAssistantViewModel,
                )
            } else {
                PlaceholderScreen("Analíticas e IA", onNavigate)
            }
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
                onReminderLeadChange = onReminderLeadChange,
                calendarMirrorEnabled = calendarMirrorEnabled,
                onCalendarMirrorToggle = onCalendarMirrorToggle,
                locationLevel = locationLevel,
                onLocationLevelChange = onLocationLevelChange,
                onOpenHousehold = if (householdViewModel != null) {
                    { onNavigate(BudgetDestinations.HOUSEHOLD) }
                } else null,
                onManageMembers = if (membersMasterViewModel != null) {
                    { onNavigate(BudgetDestinations.MASTERS_MEMBERS) }
                } else null,
                onManageCategories = if (categoriesMasterViewModel != null) {
                    { onNavigate(BudgetDestinations.MASTERS_CATEGORIES) }
                } else null,
                onManageIncome = if (incomeSourcesMasterViewModel != null) {
                    { onNavigate(BudgetDestinations.MASTERS_INCOME) }
                } else null,
                onManageWallets = { onNavigate(BudgetDestinations.WALLETS) },
                nvidiaApiKey = nvidiaApiKey,
                onNvidiaApiKeyChange = onNvidiaApiKeyChange,
                onImportStatement = if (statementImportViewModel != null) {
                    { onNavigate(BudgetDestinations.STATEMENTS) }
                } else null,
            )
        }

        composable(
            route = BudgetDestinations.STATEMENTS,
            enterTransition = slideEnter, exitTransition = slideExit,
            popEnterTransition = slideEnter, popExitTransition = slideExit,
        ) {
            if (statementImportViewModel != null) {
                mx.budget.ui.statements.StatementImportScreen(
                    viewModel = statementImportViewModel,
                    onBack = { onNavigate(BudgetDestinations.PROFILE) },
                    onOpenProfile = { onNavigate(BudgetDestinations.PROFILE) },
                )
            } else {
                PlaceholderScreen("Importar estado de cuenta", onNavigate)
            }
        }

        composable(
            route = BudgetDestinations.MASTERS_MEMBERS,
            enterTransition = slideEnter, exitTransition = slideExit,
            popEnterTransition = slideEnter, popExitTransition = slideExit,
        ) {
            if (membersMasterViewModel != null) {
                mx.budget.ui.masters.MembersScreen(
                    viewModel = membersMasterViewModel,
                    onBack = { onNavigate(BudgetDestinations.PROFILE) },
                )
            }
        }

        composable(
            route = BudgetDestinations.MASTERS_CATEGORIES,
            enterTransition = slideEnter, exitTransition = slideExit,
            popEnterTransition = slideEnter, popExitTransition = slideExit,
        ) {
            if (categoriesMasterViewModel != null) {
                mx.budget.ui.masters.CategoriesScreen(
                    viewModel = categoriesMasterViewModel,
                    onBack = { onNavigate(BudgetDestinations.PROFILE) },
                )
            }
        }

        composable(
            route = BudgetDestinations.MASTERS_INCOME,
            enterTransition = slideEnter, exitTransition = slideExit,
            popEnterTransition = slideEnter, popExitTransition = slideExit,
        ) {
            if (incomeSourcesMasterViewModel != null) {
                mx.budget.ui.masters.IncomeSourcesScreen(
                    viewModel = incomeSourcesMasterViewModel,
                    onBack = { onNavigate(BudgetDestinations.PROFILE) },
                )
            }
        }

        composable(
            route = BudgetDestinations.HOUSEHOLD,
            enterTransition = slideEnter, exitTransition = slideExit,
            popEnterTransition = slideEnter, popExitTransition = slideExit,
        ) {
            if (householdViewModel != null) {
                mx.budget.ui.household.HouseholdScreen(
                    viewModel = householdViewModel,
                    onBack = { onNavigate(BudgetDestinations.PROFILE) },
                )
            } else {
                PlaceholderScreen("Cuenta y grupos", onNavigate)
            }
        }
    }
    } // MainShell
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

