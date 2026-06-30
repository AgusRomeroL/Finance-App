package mx.budget

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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
            app.memberRepository
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
            app.householdId
        ))[WalletsViewModel::class.java]
    }

    private val expenseDetailViewModel: mx.budget.ui.detail.ExpenseDetailViewModel by lazy {
        val app = application as BudgetApplication
        ViewModelProvider(this, ExpenseDetailViewModelFactory(
            app.expenseRepository,
            app.locationProvider
        ))[mx.budget.ui.detail.ExpenseDetailViewModel::class.java]
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
            { id -> app.bankCaptureManager.markConfirmedExternally(id) }
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
        super.onCreate(savedInstanceState)
        val windowWidthDp = resources.displayMetrics.let {
            (it.widthPixels / it.density).toInt()
        }
        val app = application as BudgetApplication
        val settings = app.settingsRepository
        // Empuja el saldo "Disponible" al reloj (tile Glance) mientras la app está
        // abierta (§G.3). Observa el dashboard; si no hay reloj emparejado, el
        // Data Layer simplemente cachea/no-op.
        WearSyncManager(this, dashboardViewModel, lifecycleScope).startSyncObservation()
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
            val scope = rememberCoroutineScope()
            BudgetAppTheme(dynamicColor = dynamicColor) {
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
                    }
                )
            }
        }
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
        ) as T
    }
}

/** Factory para CaptureViewModel */
/** Factory para ExpenseDetailViewModel (detalle de gasto: ubicación + hora, §G.4). */
class ExpenseDetailViewModelFactory(
    private val expenseRepository: ExpenseRepository,
    private val locationProvider: mx.budget.data.location.LocationProvider,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return mx.budget.ui.detail.ExpenseDetailViewModel(
            expenseRepository = expenseRepository,
            locationProvider = locationProvider,
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
        ) as T
    }
}