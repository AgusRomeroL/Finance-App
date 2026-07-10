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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mx.budget.ui.tutorial.TutorialController
import mx.budget.ui.tutorial.TutorialOverlay
import mx.budget.ui.tutorial.TutorialSpec
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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
import mx.budget.ui.capture.CaptureBottomSheet
import mx.budget.ui.capture.CaptureSheetMode
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
    const val MEMBER_BALANCES = "member_balances"
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
    const val STATEMENTS_MONTH = "statements_month"
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
    memberBalancesViewModel: mx.budget.ui.settle.MemberBalancesViewModel? = null,
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
    statementsChecklistViewModel: mx.budget.ui.statements.StatementsChecklistViewModel? = null,
    nvidiaApiKey: String = "",
    onNvidiaApiKeyChange: (String) -> Unit = {},
    startTutorial: Boolean = false,
    onTutorialSeen: () -> Unit = {},
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: BudgetDestinations.DASHBOARD

    // Captura hoisted al grafo: el "+" del pill flotante / rail (MainShell) y las
    // sugerencias del dashboard abren el MISMO sheet desde aquí. null = cerrado.
    var captureMode by remember { mutableStateOf<CaptureSheetMode?>(null) }
    val onOpenCapture: (CaptureSheetMode) -> Unit = { captureMode = it }

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

    // Rutas de nivel superior: llevan barra persistente (pill flotante / rail) provista
    // por MainShell y comparten la transición fade-through (M3) entre sí. Perfil YA NO
    // es top-level: se abre desde el avatar de la barra superior como ruta secundaria.
    val topLevelRoutes = remember {
        setOf(
            BudgetDestinations.DASHBOARD,
            BudgetDestinations.CALENDAR,
            BudgetDestinations.WALLETS,
            BudgetDestinations.ANALYTICS,
        )
    }
    val isTopLevel = currentRoute in topLevelRoutes
    val isExpanded = windowWidthDp >= 600

    // ── Tutorial guiado (coach-marks / spotlight) — ver ui/tutorial/ y TUTORIAL.md ──
    // El controller se recuerda una sola vez; el overlay (abajo) ejecuta la navegación
    // y apertura de la hoja de captura con callbacks frescos.
    val tutorialController = remember { TutorialController(TutorialSpec.steps, onMarkSeen = onTutorialSeen) }
    // Señal para abrir el CaptureBottomSheet (estado local del Dashboard) durante el tour.
    var tutorialCaptureOpen by remember { mutableStateOf(false) }
    // Auto-arranque la primera vez: solo cuando ya estamos en el Dashboard (una instalación
    // fresca ve el tour tras salir del onboarding de datos). Latch para no re-arrancar.
    var tutorialStarted by remember { mutableStateOf(false) }
    LaunchedEffect(startTutorial, currentRoute) {
        if (startTutorial && !tutorialStarted && currentRoute == BudgetDestinations.DASHBOARD) {
            tutorialStarted = true
            tutorialController.start(firstRun = true)
        }
    }

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
    Box(Modifier.fillMaxSize()) {
    MainShell(
        currentRoute = currentRoute,
        isExpanded = isExpanded,
        showBar = isTopLevel,
        onNavigate = onNavigate,
        onCapture = { onOpenCapture(CaptureSheetMode.New) },
        tutorialController = tutorialController,
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
                onOpenSuggestions = { onNavigate(BudgetDestinations.SUGGESTIONS) },
                onOpenCapture = onOpenCapture,
                onOpenProfile = { onNavigate(BudgetDestinations.PROFILE) },
                tutorialController = tutorialController,
                tutorialCaptureOpen = tutorialCaptureOpen,
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
                detailViewModel = expenseDetailViewModel,
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
                onOpenTemplates = { onNavigate(BudgetDestinations.TEMPLATES) },
                tutorialController = tutorialController
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
                    tutorialController = tutorialController,
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
                onBack = { onNavigate(BudgetDestinations.DASHBOARD) },
                onOpenMemberBalances = if (memberBalancesViewModel != null) {
                    { onNavigate(BudgetDestinations.MEMBER_BALANCES) }
                } else null,
                tutorialController = tutorialController,
            )
        }

        composable(
            route = BudgetDestinations.MEMBER_BALANCES,
            enterTransition = slideEnter, exitTransition = slideExit,
            popEnterTransition = slideEnter, popExitTransition = slideExit,
        ) {
            if (memberBalancesViewModel != null) {
                mx.budget.ui.settle.MemberBalancesScreen(
                    viewModel = memberBalancesViewModel,
                    onBack = { onNavigate(BudgetDestinations.WALLETS) },
                )
            } else {
                PlaceholderScreen("Cuentas entre miembros", onNavigate)
            }
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
                    tutorialController = tutorialController,
                )
            } else {
                PlaceholderScreen("Analíticas e IA", onNavigate)
            }
        }

        composable(
            route = BudgetDestinations.PROFILE,
            enterTransition = slideEnter, exitTransition = slideExit,
            popEnterTransition = slideEnter, popExitTransition = slideExit,
        ) {
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
                    {
                        onNavigate(
                            if (statementsChecklistViewModel != null) BudgetDestinations.STATEMENTS_MONTH
                            else BudgetDestinations.STATEMENTS
                        )
                    }
                } else null,
                onShowTutorial = { tutorialController.start(firstRun = false) },
                tutorialController = tutorialController,
            )
        }

        composable(
            route = "${BudgetDestinations.STATEMENTS}?walletId={walletId}",
            arguments = listOf(
                navArgument("walletId") {
                    type = NavType.StringType; nullable = true; defaultValue = null
                }
            ),
            enterTransition = slideEnter, exitTransition = slideExit,
            popEnterTransition = slideEnter, popExitTransition = slideExit,
        ) { backStackEntry ->
            val walletId = backStackEntry.arguments?.getString("walletId")
            if (statementImportViewModel != null) {
                // Entrada desde el checklist: resetea y preselecciona el wallet (el
                // VM es activity-scoped, así que el reset explícito es obligatorio).
                androidx.compose.runtime.LaunchedEffect(walletId) {
                    if (walletId != null) {
                        statementImportViewModel.reset()
                        statementImportViewModel.presetWallet(walletId)
                    }
                }
                val backRoute =
                    if (walletId != null && statementsChecklistViewModel != null)
                        BudgetDestinations.STATEMENTS_MONTH
                    else BudgetDestinations.PROFILE
                mx.budget.ui.statements.StatementImportScreen(
                    viewModel = statementImportViewModel,
                    onBack = { onNavigate(backRoute) },
                    onOpenProfile = { onNavigate(BudgetDestinations.PROFILE) },
                )
            } else {
                PlaceholderScreen("Importar estado de cuenta", onNavigate)
            }
        }

        composable(
            route = BudgetDestinations.STATEMENTS_MONTH,
            enterTransition = slideEnter, exitTransition = slideExit,
            popEnterTransition = slideEnter, popExitTransition = slideExit,
        ) {
            if (statementsChecklistViewModel != null) {
                val statuses by statementsChecklistViewModel.statuses.collectAsState()
                val progress by statementsChecklistViewModel.progress.collectAsState()
                mx.budget.ui.statements.StatementsChecklistScreen(
                    statuses = statuses,
                    imported = progress.first,
                    total = progress.second,
                    onImportWallet = { wid ->
                        onNavigate("${BudgetDestinations.STATEMENTS}?walletId=$wid")
                    },
                    onImportAny = { onNavigate(BudgetDestinations.STATEMENTS) },
                    onBack = { onNavigate(BudgetDestinations.PROFILE) },
                )
            } else {
                PlaceholderScreen("Estados del mes", onNavigate)
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

    // Overlay principal del tutorial: cubre todas las pantallas normales y orquesta la
    // navegación + apertura de la hoja de captura. La hoja hospeda su propio overlay
    // (problema de ventanas del ModalBottomSheet, ver TUTORIAL.md).
    TutorialOverlay(
        controller = tutorialController,
        currentRoute = currentRoute,
        onNavigate = onNavigate,
        groupFilter = { !it.requiresCaptureSheet },
        orchestrate = true,
        onRequestOpenCapture = { tutorialCaptureOpen = true },
        onRequestCloseCapture = {
            tutorialCaptureOpen = false
            // El sheet real vive hoisted aquí (captureMode); bajar la señal no basta
            // para cerrarlo. Se cierra SOLO si el tour sigue corriendo — fuera del
            // tutorial no debemos descartar una hoja que el usuario abrió a mano.
            if (tutorialController.isRunning) captureMode = null
        },
    )

    // Aviso previo al relanzar desde Perfil: los datos del tour son de demostración.
    if (tutorialController.pendingWarning) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { tutorialController.dismissWarning() },
            title = { Text("Datos de demostración") },
            text = {
                Text(
                    "Durante el tutorial verás datos de ejemplo, no los de tu presupuesto real. " +
                        "Al terminar, tu información vuelve intacta."
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { tutorialController.confirmWarning() }) {
                    Text("Entendido")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { tutorialController.dismissWarning() }) {
                    Text("Cancelar")
                }
            },
        )
    }

    // Invitación final de la primera vez: empezar la Wallet real.
    if (tutorialController.pendingInvitation) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { tutorialController.dismissInvitation() },
            title = { Text("¡Listo para empezar!") },
            text = {
                Text(
                    "Eso es todo. Ahora registra tus propios movimientos y haz tuyo el presupuesto. " +
                        "Puedes volver a ver el tutorial cuando quieras desde Perfil."
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { tutorialController.dismissInvitation() }) {
                    Text("Comenzar")
                }
            },
        )
    }
    // Sheet de captura hoisted: overlay global sobre cualquier ruta (lo abre el "+"
    // del pill/rail y las sugerencias del dashboard).
    captureMode?.let { mode ->
        CaptureBottomSheet(
            viewModel = captureViewModel,
            onDismiss = {
                captureMode = null
                // Válvula del tutorial: descartar la hoja a media sección de captura
                // termina el tour limpio (sale del modo demo) en vez de dejar la app
                // varada en "quincena de ejemplo" sin overlay.
                if (tutorialController.isRunning &&
                    tutorialController.currentStep?.requiresCaptureSheet == true
                ) {
                    tutorialController.skip()
                }
            },
            mode = mode,
            // Sin el controller, el overlay interno del sheet nunca se compone y el
            // tutorial moría mudo en el paso 6 (P1 de auditoría runtime): al
            // hoistear la hoja desde DashboardScreen se perdió este cableado.
            tutorialController = tutorialController,
            tutorialCurrentRoute = currentRoute,
        )
    }
    } // Box
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

