package mx.budget

import android.Manifest
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import mx.budget.ai.proactive.RetroAttributionEngine
import mx.budget.data.capture.BankCaptureManager
import mx.budget.data.local.dao.PendingCaptureDao
import mx.budget.data.repository.CategoryRepository
import mx.budget.data.repository.ExpenseRepository
import mx.budget.data.recurrence.RecurrenceMaterializer
import mx.budget.data.repository.MemberRepository
import mx.budget.data.repository.QuincenaRepository
import mx.budget.data.repository.RecurrenceRepository
import mx.budget.data.repository.WalletRepository
import mx.budget.data.local.dao.AttributionReviewDao
import mx.budget.data.local.dao.ExpenseDao
import mx.budget.data.local.dao.QuincenaDao
import mx.budget.data.settings.SettingsRepository
import mx.budget.service.WearSyncManager
import mx.budget.ui.calendar.CalendarViewModel
import mx.budget.ui.calendar.NewPlannedViewModel
import mx.budget.ui.calendar.RecurrenceViewModel
import mx.budget.ui.capture.CaptureViewModel
import mx.budget.ui.dashboard.DashboardViewModel
import mx.budget.ui.navigation.BudgetNavGraph
import mx.budget.ui.review.AttributionReviewViewModel
import mx.budget.ui.search.SearchViewModel
import mx.budget.ui.theme.BudgetAppTheme
import mx.budget.ui.wallets.WalletsViewModel

class MainActivity : ComponentActivity() {

    private val dashboardViewModel: DashboardViewModel by lazy {
        val app = application as BudgetApplication
        ViewModelProvider(this, DashboardViewModelFactory(
            app.quincenaRepository,
            app.expenseRepository,
            app.incomeRepository,
            app.memberRepository,
            app.householdId,
            app.database.attributionReviewDao(),
            app.database.expenseDao(),
            app.database.pendingCaptureDao(),
            app.bankCaptureManager,
            app.database.categoryDao(),
            app.proactiveReasoner
        ))[DashboardViewModel::class.java]
    }

    private val attributionReviewViewModel: AttributionReviewViewModel by lazy {
        val app = application as BudgetApplication
        ViewModelProvider(this, AttributionReviewViewModelFactory(
            app.database.attributionReviewDao(),
            app.database.expenseDao(),
            app.expenseRepository,
            app.memberRepository,
            app.householdId
        ))[AttributionReviewViewModel::class.java]
    }

    private val searchViewModel: SearchViewModel by lazy {
        val app = application as BudgetApplication
        ViewModelProvider(this, SearchViewModelFactory(
            app.expenseRepository,
            app.householdId
        ))[SearchViewModel::class.java]
    }

    private val calendarViewModel: CalendarViewModel by lazy {
        val app = application as BudgetApplication
        ViewModelProvider(this, CalendarViewModelFactory(
            applicationContext,
            app.database.expenseDao(),
            app.expenseRepository,
            app.settingsRepository,
            app.householdId
        ))[CalendarViewModel::class.java]
    }

    private val newPlannedViewModel: NewPlannedViewModel by lazy {
        val app = application as BudgetApplication
        ViewModelProvider(this, NewPlannedViewModelFactory(
            app.householdId,
            app.expenseRepository,
            app.quincenaRepository,
            app.database.quincenaDao(),
            app.categoryRepository,
            app.walletRepository,
            app.memberRepository,
            // Pagador default de sesión (roles v2), mismo criterio que la captura.
            sessionMemberId = app.linkedMemberId,
        ))[NewPlannedViewModel::class.java]
    }

    private val recurrenceViewModel: RecurrenceViewModel by lazy {
        val app = application as BudgetApplication
        ViewModelProvider(this, RecurrenceViewModelFactory(
            app.householdId,
            app.recurrenceRepository,
            app.categoryRepository,
            app.walletRepository,
            app.memberRepository,
            app.quincenaRepository,
            app.recurrenceMaterializer,
            app.database.expenseDao(),
            app.retroAttributionEngine,
            app.settingsRepository
        ))[RecurrenceViewModel::class.java]
    }

    private val walletsViewModel: WalletsViewModel by lazy {
        val app = application as BudgetApplication
        ViewModelProvider(this, WalletsViewModelFactory(
            app.walletRepository,
            app.transferRepository,
            app.incomeRepository,
            app.memberRepository,
            app.quincenaRepository,
            app.database.expenseDao(),
            app.householdId,
            app.savingsRepository,
            app.loanRepository,
            app.installmentRepository,
            app.database.statementImportDao()
        ))[WalletsViewModel::class.java]
    }

    private val memberBalancesViewModel: mx.budget.ui.settle.MemberBalancesViewModel by lazy {
        val app = application as BudgetApplication
        ViewModelProvider(this, MemberBalancesViewModelFactory(
            app.expenseRepository,
            app.loanRepository,
            app.memberRepository,
            app.householdId,
        ))[mx.budget.ui.settle.MemberBalancesViewModel::class.java]
    }

    private val analyticsViewModel: mx.budget.ui.analytics.AnalyticsViewModel by lazy {
        val app = application as BudgetApplication
        ViewModelProvider(this, AnalyticsViewModelFactory(
            app.analyticsRepository,
            app.database.analyticsDao(),
            app.expenseRepository,
            app.quincenaRepository,
            app.incomeRepository,
            app.savingsRepository,
            app.installmentRepository,
            app.loanRepository,
            app.householdId
        ))[mx.budget.ui.analytics.AnalyticsViewModel::class.java]
    }

    private val aiAssistantViewModel: mx.budget.ai.AiAssistantViewModel by lazy {
        val app = application as BudgetApplication
        ViewModelProvider(this, AiAssistantViewModelFactory(app))[mx.budget.ai.AiAssistantViewModel::class.java]
    }

    private val ledgerViewModel: mx.budget.ui.ledger.LedgerViewModel by lazy {
        val app = application as BudgetApplication
        ViewModelProvider(this, LedgerViewModelFactory(
            app.expenseRepository,
            app.quincenaRepository,
            app.categoryRepository,
            app.walletRepository,
            app.householdId
        ))[mx.budget.ui.ledger.LedgerViewModel::class.java]
    }

    private val expenseDetailViewModel: mx.budget.ui.detail.ExpenseDetailViewModel by lazy {
        val app = application as BudgetApplication
        ViewModelProvider(this, ExpenseDetailViewModelFactory(
            app.expenseRepository,
            app.locationProvider,
            app.categoryRepository,
            app.walletRepository,
            app.memberRepository,
            app.householdId,
            app.membershipRepository
        ))[mx.budget.ui.detail.ExpenseDetailViewModel::class.java]
    }

    private val householdViewModel: mx.budget.ui.household.HouseholdViewModel by lazy {
        val app = application as BudgetApplication
        ViewModelProvider(this, HouseholdViewModelFactory(
            authManager = app.authManager,
            membershipRepository = app.membershipRepository,
            activeHouseholdId = app.householdId,
            memberRepository = app.memberRepository,
            onSignInWithGoogle = { app.linkGoogleAccount(this@MainActivity) },
            onSwitchHousehold = { hid ->
                // Re-ancla el sync en caliente y recrea la Activity para que todos
                // los ViewModels lean Room con el hogar nuevo (householdId es un
                // valor capturado en onCreate por muchos constructores).
                app.switchActiveHousehold(hid)
                recreate()
            },
        ))[mx.budget.ui.household.HouseholdViewModel::class.java]
    }

    private val onboardingViewModel: mx.budget.ui.onboarding.OnboardingViewModel by lazy {
        val app = application as BudgetApplication
        ViewModelProvider(this, OnboardingViewModelFactory(app))[mx.budget.ui.onboarding.OnboardingViewModel::class.java]
    }

    private val membersMasterViewModel: mx.budget.ui.masters.MembersMasterViewModel by lazy {
        val app = application as BudgetApplication
        ViewModelProvider(this, MembersMasterViewModelFactory(app.memberRepository, app.householdId))[mx.budget.ui.masters.MembersMasterViewModel::class.java]
    }

    private val categoriesMasterViewModel: mx.budget.ui.masters.CategoriesMasterViewModel by lazy {
        val app = application as BudgetApplication
        ViewModelProvider(this, CategoriesMasterViewModelFactory(app.categoryRepository, app.householdId))[mx.budget.ui.masters.CategoriesMasterViewModel::class.java]
    }

    private val incomeSourcesMasterViewModel: mx.budget.ui.masters.IncomeSourcesMasterViewModel by lazy {
        val app = application as BudgetApplication
        ViewModelProvider(this, IncomeSourcesMasterViewModelFactory(
            app.incomeRepository, app.memberRepository, app.quincenaRepository, app.householdId
        ))[mx.budget.ui.masters.IncomeSourcesMasterViewModel::class.java]
    }

    private val statementImportViewModel: mx.budget.ui.statements.StatementImportViewModel by lazy {
        val app = application as BudgetApplication
        ViewModelProvider(this, StatementImportViewModelFactory(app))[mx.budget.ui.statements.StatementImportViewModel::class.java]
    }

    private val statementsChecklistViewModel: mx.budget.ui.statements.StatementsChecklistViewModel by lazy {
        val app = application as BudgetApplication
        ViewModelProvider(this, StatementsChecklistViewModelFactory(app))[mx.budget.ui.statements.StatementsChecklistViewModel::class.java]
    }

    private val captureViewModel: CaptureViewModel by lazy {
        val app = application as BudgetApplication
        ViewModelProvider(this, CaptureViewModelFactory(
            app.expenseRepository,
            app.quincenaRepository,
            app.walletRepository,
            app.memberRepository,
            app.categoryRepository,
            app.retroAttributionEngine,
            app.locationProvider,
            app.householdId,
            { id -> app.bankCaptureManager.markConfirmedExternally(id) },
            // A3: ingresos + recientes reales + autocompletado + quincena por fecha.
            incomeRepository = app.incomeRepository,
            expenseDao = app.database.expenseDao(),
            categoryDao = app.database.categoryDao(),
            quincenaDao = app.database.quincenaDao(),
            pendingCaptureDao = app.database.pendingCaptureDao(),
            membershipRepository = app.membershipRepository,
            // Pagador default de sesión (roles v2): quién ES esta persona en el hogar.
            sessionMemberId = app.linkedMemberId,
        ))[CaptureViewModel::class.java]
    }

    /** Pide READ+WRITE_CALENDAR para el espejo (Fase 6); al conceder, activa y reconcilia. */
    private val calendarPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.WRITE_CALENDAR] == true &&
            result[Manifest.permission.READ_CALENDAR] == true
        if (granted) {
            val app = application as BudgetApplication
            lifecycleScope.launch {
                app.settingsRepository.setCalendarMirrorEnabled(true)
                app.calendarMirror.reconcile()
            }
        }
    }

    // Nivel de ubicación que el usuario pidió, recordado entre el grant de foreground
    // y el de background (§G.4: el background se solicita en un flujo aparte, Android 11+).
    private var pendingLocationTarget: String? = null

    /** Pide FINE+COARSE (solo-al-usar). Si el objetivo era persistente, escala a background. */
    private val foregroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val app = application as BudgetApplication
        if (granted) {
            val target = pendingLocationTarget ?: SettingsRepository.LOCATION_LEVEL_WHILE_IN_USE
            if (target == SettingsRepository.LOCATION_LEVEL_PERSISTENT &&
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
                !app.locationProvider.hasBackgroundPermission()
            ) {
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                lifecycleScope.launch { app.settingsRepository.setLocationCaptureLevel(target) }
            }
        }
        pendingLocationTarget = null
    }

    /** Pide ACCESS_BACKGROUND_LOCATION (persistente). Si se niega, cae a solo-al-usar. */
    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val app = application as BudgetApplication
        val level = if (granted) SettingsRepository.LOCATION_LEVEL_PERSISTENT
        else SettingsRepository.LOCATION_LEVEL_WHILE_IN_USE
        lifecycleScope.launch { app.settingsRepository.setLocationCaptureLevel(level) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge: barras del sistema transparentes para que la superficie de
        // la app se vea a través de ellas (blend). La APARIENCIA de los íconos
        // (claro/oscuro) la fija BudgetAppTheme según el tema Compose. Ambas barras
        // transparentes; en nav de 3 botones el scrim automático cuida el contraste.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        val windowWidthDp = resources.displayMetrics.let {
            (it.widthPixels / it.density).toInt()
        }
        val app = application as BudgetApplication
        val settings = app.settingsRepository
        // Empuja el saldo "Disponible" al reloj (tile Glance) mientras la app está
        // abierta (§G.3). Observa el dashboard; si no hay reloj emparejado, el
        // Data Layer simplemente cachea/no-op.
        // Scoped a STARTED: la observación (y los flujos Room del dashboard) se
        // detienen al pasar a segundo plano; el refresco en background lo cubre
        // ReminderWorker (~15 min).
        val wearSync = WearSyncManager(this, dashboardViewModel)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { wearSync.observe() }
        }
        setContent {
            // Toggle de color dinámico persistido (brief §2.1): Material You por
            // default; el verde sembrado #016E3E es el fallback (toggle off).
            // Valor inicial leído síncrono al arrancar → sin parpadeo de tema.
            val dynamicColor by settings.dynamicColor.collectAsState(initial = app.initialDynamicColor)
            val bankCaptureEnabled by settings.bankCaptureEnabled.collectAsState(initial = false)
            val reminderLeadDays by settings.reminderLeadDays.collectAsState(initial = 2)
            val calendarMirrorEnabled by settings.calendarMirrorEnabled.collectAsState(initial = false)
            val locationLevel by settings.locationCaptureLevel.collectAsState(
                initial = SettingsRepository.LOCATION_LEVEL_NONE
            )
            val nvidiaApiKey by settings.nvidiaApiKey.collectAsState(initial = "")
            // Tutorial guiado (coach-marks): auto-arranca la primera vez. Valor inicial leído
            // síncrono al arrancar → sin parpadeo. Ver ui/tutorial/ y TUTORIAL.md.
            val hasSeenTutorial by settings.hasSeenTutorial.collectAsState(initial = app.initialHasSeenTutorial)
            val scope = rememberCoroutineScope()
            BudgetAppTheme(dynamicColor = dynamicColor) {
                // Surface raíz: pinta colorScheme.background bajo TODO el NavHost.
                // Sin él, las pantallas que no traen Scaffold/Surface propio
                // (Analíticas, Libro Mayor) dibujan texto del tema sobre la
                // ventana del sistema — ilegible en dark mode.
                androidx.compose.material3.Surface(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background,
                ) {
                BudgetNavGraph(
                    dashboardViewModel = dashboardViewModel,
                    captureViewModel = captureViewModel,
                    expenseDetailViewModel = expenseDetailViewModel,
                    attributionReviewViewModel = attributionReviewViewModel,
                    searchViewModel = searchViewModel,
                    calendarViewModel = calendarViewModel,
                    newPlannedViewModel = newPlannedViewModel,
                    recurrenceViewModel = recurrenceViewModel,
                    walletsViewModel = walletsViewModel,
                    memberBalancesViewModel = memberBalancesViewModel,
                    analyticsViewModel = analyticsViewModel,
                    ledgerViewModel = ledgerViewModel,
                    aiAssistantViewModel = aiAssistantViewModel,
                    windowWidthDp = windowWidthDp,
                    dynamicColor = dynamicColor,
                    onDynamicColorChange = { enabled -> scope.launch { settings.setDynamicColor(enabled) } },
                    onRenormalize = { app.enqueueRetroLabeling(replace = true) },
                    bankCaptureEnabled = bankCaptureEnabled,
                    onBankCaptureToggle = { enabled -> scope.launch { settings.setBankCaptureEnabled(enabled) } },
                    onGrantNotificationAccess = {
                        startActivity(android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                    },
                    reminderLeadDays = reminderLeadDays,
                    onReminderLeadChange = { days -> scope.launch { settings.setReminderLeadDays(days) } },
                    calendarMirrorEnabled = calendarMirrorEnabled,
                    onCalendarMirrorToggle = { enabled ->
                        if (enabled) {
                            if (app.calendarMirror.hasPermission()) {
                                scope.launch {
                                    settings.setCalendarMirrorEnabled(true)
                                    app.calendarMirror.reconcile()
                                }
                            } else {
                                calendarPermLauncher.launch(
                                    arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                                )
                            }
                        } else {
                            scope.launch {
                                settings.setCalendarMirrorEnabled(false)
                                app.calendarMirror.disableAndPurge()
                            }
                        }
                    },
                    locationLevel = locationLevel,
                    onLocationLevelChange = { level ->
                        when (level) {
                            SettingsRepository.LOCATION_LEVEL_NONE ->
                                scope.launch { settings.setLocationCaptureLevel(SettingsRepository.LOCATION_LEVEL_NONE) }

                            SettingsRepository.LOCATION_LEVEL_WHILE_IN_USE ->
                                if (app.locationProvider.hasForegroundPermission()) {
                                    scope.launch { settings.setLocationCaptureLevel(SettingsRepository.LOCATION_LEVEL_WHILE_IN_USE) }
                                } else {
                                    pendingLocationTarget = SettingsRepository.LOCATION_LEVEL_WHILE_IN_USE
                                    foregroundLocationLauncher.launch(
                                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                                    )
                                }

                            SettingsRepository.LOCATION_LEVEL_PERSISTENT -> when {
                                !app.locationProvider.hasForegroundPermission() -> {
                                    pendingLocationTarget = SettingsRepository.LOCATION_LEVEL_PERSISTENT
                                    foregroundLocationLauncher.launch(
                                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                                    )
                                }
                                !app.locationProvider.hasBackgroundPermission() ->
                                    backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                                else ->
                                    scope.launch { settings.setLocationCaptureLevel(SettingsRepository.LOCATION_LEVEL_PERSISTENT) }
                            }
                        }
                    },
                    householdViewModel = householdViewModel,
                    onboardingViewModel = onboardingViewModel,
                    membersMasterViewModel = membersMasterViewModel,
                    categoriesMasterViewModel = categoriesMasterViewModel,
                    incomeSourcesMasterViewModel = incomeSourcesMasterViewModel,
                    startOnboarding = app.needsOnboarding,
                    statementImportViewModel = statementImportViewModel,
                    statementsChecklistViewModel = statementsChecklistViewModel,
                    nvidiaApiKey = nvidiaApiKey,
                    onNvidiaApiKeyChange = { key -> scope.launch { settings.setNvidiaApiKey(key) } },
                    startTutorial = !hasSeenTutorial,
                    onTutorialSeen = {
                        // Snapshot de proceso + persistencia: ver markTutorialSeenInProcess.
                        app.markTutorialSeenInProcess()
                        scope.launch { settings.setHasSeenTutorial(true) }
                    },
                )
                }
            }
        }
    }
}

/** Factory del checklist "Estados del mes" (Tarea 4 — alimentación mensual). */
class StatementsChecklistViewModelFactory(
    private val app: BudgetApplication,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return mx.budget.ui.statements.StatementsChecklistViewModel(
            walletRepository = app.walletRepository,
            statementImportDao = app.database.statementImportDao(),
            householdId = app.householdId,
        ) as T
    }
}

/** Factory del importador de estados de cuenta (Fase C, paquete C1). */
class StatementImportViewModelFactory(
    private val app: BudgetApplication,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return mx.budget.ui.statements.StatementImportViewModel(
            manager = app.statementImportManager,
            walletRepository = app.walletRepository,
            settings = app.settingsRepository,
            householdId = app.householdId,
        ) as T
    }
}

/** Factory para HouseholdViewModel (Fase B — cuenta y grupos). */
class HouseholdViewModelFactory(
    private val authManager: mx.budget.data.remote.AuthManager,
    private val membershipRepository: mx.budget.data.remote.MembershipRepository,
    private val activeHouseholdId: String,
    /** Members locales (Room) para el selector de invitación nominada (roles v2). */
    private val memberRepository: MemberRepository,
    private val onSignInWithGoogle: suspend () -> Boolean,
    private val onSwitchHousehold: (String) -> Unit,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return mx.budget.ui.household.HouseholdViewModel(
            authManager = authManager,
            membershipRepository = membershipRepository,
            activeHouseholdId = activeHouseholdId,
            memberRepository = memberRepository,
            onSignInWithGoogle = onSignInWithGoogle,
            onSwitchHousehold = onSwitchHousehold,
        ) as T
    }
}

/** Factory del wizard de onboarding (paquete B2). Cablea repos + DAO + hook nube. */
class OnboardingViewModelFactory(
    private val app: BudgetApplication,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return mx.budget.ui.onboarding.OnboardingViewModel(
            appContext = app,
            householdId = app.householdId,
            householdDao = app.database.householdDao(),
            memberRepository = app.memberRepository,
            walletRepository = app.walletRepository,
            categoryRepository = app.categoryRepository,
            quincenaRepository = app.quincenaRepository,
            quincenaDao = app.database.quincenaDao(),
            onCreateCloudHousehold = { name -> app.registerOnboardingHouseholdInCloud(name) },
        ) as T
    }
}

/** Factory del CRUD de miembros (paquete B2). */
class MembersMasterViewModelFactory(
    private val memberRepository: MemberRepository,
    private val householdId: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return mx.budget.ui.masters.MembersMasterViewModel(memberRepository, householdId) as T
    }
}

/** Factory del CRUD de categorías (paquete B2). */
class CategoriesMasterViewModelFactory(
    private val categoryRepository: CategoryRepository,
    private val householdId: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return mx.budget.ui.masters.CategoriesMasterViewModel(categoryRepository, householdId) as T
    }
}

/** Factory del CRUD de fuentes de ingreso (paquete B2). */
class IncomeSourcesMasterViewModelFactory(
    private val incomeRepository: mx.budget.data.repository.IncomeRepository,
    private val memberRepository: MemberRepository,
    private val quincenaRepository: QuincenaRepository,
    private val householdId: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return mx.budget.ui.masters.IncomeSourcesMasterViewModel(
            incomeRepository, memberRepository, quincenaRepository, householdId
        ) as T
    }
}

/** Factory para DashboardViewModel */
class DashboardViewModelFactory(
    private val quincenaRepository: QuincenaRepository,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: mx.budget.data.repository.IncomeRepository,
    private val memberRepository: MemberRepository,
    private val householdId: String,
    private val attributionReviewDao: AttributionReviewDao,
    private val expenseDao: ExpenseDao,
    private val pendingCaptureDao: PendingCaptureDao,
    private val bankCaptureManager: BankCaptureManager,
    private val categoryDao: mx.budget.data.local.dao.CategoryDao,
    private val proactiveReasoner: mx.budget.ai.proactive.ProactiveReasoner,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DashboardViewModel(
            quincenaRepository = quincenaRepository,
            expenseRepository = expenseRepository,
            incomeRepository = incomeRepository,
            memberRepository = memberRepository,
            householdId = householdId,
            attributionReviewDao = attributionReviewDao,
            expenseDao = expenseDao,
            pendingCaptureDao = pendingCaptureDao,
            bankCaptureManager = bankCaptureManager,
            categoryDao = categoryDao,
            proactiveReasoner = proactiveReasoner,
        ) as T
    }
}

/** Factory para AttributionReviewViewModel (Feature B). */
class AttributionReviewViewModelFactory(
    private val attributionReviewDao: AttributionReviewDao,
    private val expenseDao: ExpenseDao,
    private val expenseRepository: ExpenseRepository,
    private val memberRepository: MemberRepository,
    private val householdId: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AttributionReviewViewModel(
            reviewDao = attributionReviewDao,
            expenseDao = expenseDao,
            expenseRepository = expenseRepository,
            memberRepository = memberRepository,
            householdId = householdId,
        ) as T
    }
}

/** Factory para RecurrenceViewModel (Fase 4 inc. 2c: CRUD de plantillas). */
class RecurrenceViewModelFactory(
    private val householdId: String,
    private val recurrenceRepository: RecurrenceRepository,
    private val categoryRepository: CategoryRepository,
    private val walletRepository: WalletRepository,
    private val memberRepository: MemberRepository,
    private val quincenaRepository: QuincenaRepository,
    private val materializer: RecurrenceMaterializer,
    private val expenseDao: ExpenseDao,
    private val retroAttributionEngine: RetroAttributionEngine,
    private val settingsRepository: SettingsRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return RecurrenceViewModel(
            householdId = householdId,
            recurrenceRepository = recurrenceRepository,
            categoryRepository = categoryRepository,
            walletRepository = walletRepository,
            memberRepository = memberRepository,
            quincenaRepository = quincenaRepository,
            materializer = materializer,
            expenseDao = expenseDao,
            retroAttributionEngine = retroAttributionEngine,
            settingsRepository = settingsRepository,
        ) as T
    }
}

/** Factory para NewPlannedViewModel (Fase 4 inc. 2b: pago manual PLANNED). */
class NewPlannedViewModelFactory(
    private val householdId: String,
    private val expenseRepository: ExpenseRepository,
    private val quincenaRepository: QuincenaRepository,
    private val quincenaDao: QuincenaDao,
    private val categoryRepository: CategoryRepository,
    private val walletRepository: WalletRepository,
    private val memberRepository: MemberRepository,
    /** Pagador default de sesión (roles v2): `BudgetApplication.linkedMemberId`. */
    private val sessionMemberId: String? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return NewPlannedViewModel(
            householdId = householdId,
            expenseRepository = expenseRepository,
            quincenaRepository = quincenaRepository,
            quincenaDao = quincenaDao,
            categoryRepository = categoryRepository,
            walletRepository = walletRepository,
            memberRepository = memberRepository,
            sessionMemberId = sessionMemberId,
        ) as T
    }
}

/** Factory para CalendarViewModel (Fase 4: timeline de gastos PLANNED). */
class CalendarViewModelFactory(
    private val appContext: android.content.Context,
    private val expenseDao: ExpenseDao,
    private val expenseRepository: ExpenseRepository,
    private val settingsRepository: SettingsRepository,
    private val householdId: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CalendarViewModel(
            appContext = appContext,
            expenseDao = expenseDao,
            expenseRepository = expenseRepository,
            settingsRepository = settingsRepository,
            householdId = householdId,
        ) as T
    }
}

/** Factory para SearchViewModel (búsqueda de movimientos). */
class SearchViewModelFactory(
    private val expenseRepository: ExpenseRepository,
    private val householdId: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SearchViewModel(
            expenseRepository = expenseRepository,
            householdId = householdId,
        ) as T
    }
}

/** Factory para WalletsViewModel (pantalla Cuentas: saldos por wallet + movimientos). */
class WalletsViewModelFactory(
    private val walletRepository: WalletRepository,
    private val transferRepository: mx.budget.data.repository.TransferRepository,
    private val incomeRepository: mx.budget.data.repository.IncomeRepository,
    private val memberRepository: MemberRepository,
    private val quincenaRepository: QuincenaRepository,
    private val expenseDao: ExpenseDao,
    private val householdId: String,
    private val savingsRepository: mx.budget.data.repository.SavingsRepository? = null,
    private val loanRepository: mx.budget.data.repository.LoanRepository? = null,
    private val installmentRepository: mx.budget.data.repository.InstallmentRepository? = null,
    private val statementImportDao: mx.budget.data.local.dao.StatementImportDao? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return WalletsViewModel(
            walletRepository = walletRepository,
            transferRepository = transferRepository,
            incomeRepository = incomeRepository,
            memberRepository = memberRepository,
            quincenaRepository = quincenaRepository,
            expenseDao = expenseDao,
            householdId = householdId,
            savingsRepository = savingsRepository,
            loanRepository = loanRepository,
            installmentRepository = installmentRepository,
            statementImportDao = statementImportDao,
        ) as T
    }
}

/** Factory para MemberBalancesViewModel ("Cuentas entre miembros" — deudas explícitas). */
class MemberBalancesViewModelFactory(
    private val expenseRepository: ExpenseRepository,
    private val loanRepository: mx.budget.data.repository.LoanRepository,
    private val memberRepository: MemberRepository,
    private val householdId: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return mx.budget.ui.settle.MemberBalancesViewModel(
            expenseRepository = expenseRepository,
            loanRepository = loanRepository,
            memberRepository = memberRepository,
            householdId = householdId,
        ) as T
    }
}

/** Factory para AnalyticsViewModel (MVP Fase 3). */
class AnalyticsViewModelFactory(
    private val analyticsRepository: mx.budget.data.repository.AnalyticsRepository,
    private val analyticsDao: mx.budget.data.local.dao.AnalyticsDao,
    private val expenseRepository: mx.budget.data.repository.ExpenseRepository,
    private val quincenaRepository: QuincenaRepository,
    private val incomeRepository: mx.budget.data.repository.IncomeRepository,
    private val savingsRepository: mx.budget.data.repository.SavingsRepository,
    private val installmentRepository: mx.budget.data.repository.InstallmentRepository,
    private val loanRepository: mx.budget.data.repository.LoanRepository,
    private val householdId: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return mx.budget.ui.analytics.AnalyticsViewModel(
            analyticsRepository = analyticsRepository,
            analyticsDao = analyticsDao,
            expenseRepository = expenseRepository,
            quincenaRepository = quincenaRepository,
            incomeRepository = incomeRepository,
            savingsRepository = savingsRepository,
            installmentRepository = installmentRepository,
            loanRepository = loanRepository,
            householdId = householdId,
        ) as T
    }
}

/**
 * Factory del asistente reactivo (MVP Fase 4). Construye el pipeline completo
 * desde el contenedor manual: RAG (OnDeviceLlm compartido = HybridLlm) +
 * IntentDispatcher con repos reales + AliasResolver fresco por dispatch.
 */
class AiAssistantViewModelFactory(
    private val app: BudgetApplication,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val rag = mx.budget.ai.rag.LedgerRagUseCase(
            context = app,
            llm = app.onDeviceLlm,
            quincenaRepository = app.quincenaRepository,
            expenseRepository = app.expenseRepository,
            analyticsRepository = app.analyticsRepository,
            walletRepository = app.walletRepository,
            installmentRepository = app.installmentRepository,
            memberRepository = app.memberRepository,
        )
        val resolverProvider: suspend () -> mx.budget.ai.dispatch.AliasResolver = {
            mx.budget.ai.dispatch.AliasResolver(
                members = app.memberRepository.observeActiveMembers(app.householdId).first(),
                categories = app.categoryRepository.observeAll(app.householdId).first(),
                wallets = app.walletRepository.getActive(app.householdId),
                installments = app.installmentRepository.getActive(app.householdId),
            )
        }
        val dispatcher = mx.budget.ai.dispatch.IntentDispatcher(
            resolverProvider = resolverProvider,
            analyticsRepository = app.analyticsRepository,
            expenseRepository = app.expenseRepository,
            incomeRepository = app.incomeRepository,
            walletRepository = app.walletRepository,
            installmentRepository = app.installmentRepository,
            quincenaRepository = app.quincenaRepository,
            memberRepository = app.memberRepository,
            householdId = app.householdId,
        )
        val openAnalysis = mx.budget.ai.rag.OpenAnalysisAnswerer(
            context = app,
            llm = app.onDeviceLlm,
            quincenaRepository = app.quincenaRepository,
            analyticsRepository = app.analyticsRepository,
            expenseRepository = app.expenseRepository,
            installmentRepository = app.installmentRepository,
        )
        return mx.budget.ai.AiAssistantViewModel(
            llm = app.onDeviceLlm,
            ledgerRagUseCase = rag,
            dispatcher = dispatcher,
            openAnalysisAnswerer = openAnalysis,
            defaultHouseholdId = app.householdId,
            suggestedQuestionEngine = mx.budget.ai.suggest.SuggestedQuestionEngine(
                analyticsRepository = app.analyticsRepository,
                quincenaRepository = app.quincenaRepository,
                settingsRepository = app.settingsRepository,
                llm = app.onDeviceLlm,
            ),
        ) as T
    }
}

/** Factory para LedgerViewModel (MVP Fase 3). */
class LedgerViewModelFactory(
    private val expenseRepository: ExpenseRepository,
    private val quincenaRepository: QuincenaRepository,
    private val categoryRepository: CategoryRepository,
    private val walletRepository: WalletRepository,
    private val householdId: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return mx.budget.ui.ledger.LedgerViewModel(
            expenseRepository = expenseRepository,
            quincenaRepository = quincenaRepository,
            categoryRepository = categoryRepository,
            walletRepository = walletRepository,
            householdId = householdId,
        ) as T
    }
}

/** Factory para ExpenseDetailViewModel (detalle: ubicación/hora §G.4 + edición/borrado Fase 1). */
class ExpenseDetailViewModelFactory(
    private val expenseRepository: ExpenseRepository,
    private val locationProvider: mx.budget.data.location.LocationProvider,
    private val categoryRepository: CategoryRepository,
    private val walletRepository: WalletRepository,
    private val memberRepository: MemberRepository,
    private val householdId: String,
    private val membershipRepository: mx.budget.data.remote.MembershipRepository? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return mx.budget.ui.detail.ExpenseDetailViewModel(
            expenseRepository = expenseRepository,
            locationProvider = locationProvider,
            categoryRepository = categoryRepository,
            walletRepository = walletRepository,
            memberRepository = memberRepository,
            householdId = householdId,
            membershipRepository = membershipRepository,
        ) as T
    }
}

class CaptureViewModelFactory(
    private val expenseRepository: ExpenseRepository,
    private val quincenaRepository: QuincenaRepository,
    private val walletRepository: WalletRepository,
    private val memberRepository: MemberRepository,
    private val categoryRepository: CategoryRepository,
    private val retroAttributionEngine: RetroAttributionEngine,
    private val locationProvider: mx.budget.data.location.LocationProvider,
    private val householdId: String,
    private val onPendingConfirmed: (suspend (String) -> Unit)? = null,
    // A3 (defaults null: QuickCaptureActivity y otros call sites siguen compilando)
    private val incomeRepository: mx.budget.data.repository.IncomeRepository? = null,
    private val expenseDao: ExpenseDao? = null,
    private val categoryDao: mx.budget.data.local.dao.CategoryDao? = null,
    private val quincenaDao: QuincenaDao? = null,
    private val pendingCaptureDao: mx.budget.data.local.dao.PendingCaptureDao? = null,
    private val membershipRepository: mx.budget.data.remote.MembershipRepository? = null,
    /** Pagador default de sesión (roles v2): `BudgetApplication.linkedMemberId`. */
    private val sessionMemberId: String? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CaptureViewModel(
            expenseRepository = expenseRepository,
            quincenaRepository = quincenaRepository,
            walletRepository = walletRepository,
            memberRepository = memberRepository,
            categoryRepository = categoryRepository,
            retroAttributionEngine = retroAttributionEngine,
            locationProvider = locationProvider,
            householdId = householdId,
            onPendingConfirmed = onPendingConfirmed,
            incomeRepository = incomeRepository,
            expenseDao = expenseDao,
            categoryDao = categoryDao,
            quincenaDao = quincenaDao,
            pendingCaptureDao = pendingCaptureDao,
            membershipRepository = membershipRepository,
            sessionMemberId = sessionMemberId,
        ) as T
    }
}