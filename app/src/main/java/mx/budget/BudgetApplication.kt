package mx.budget

import android.app.Application
import androidx.room.Room
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mx.budget.ai.proactive.BankNotificationParser
import mx.budget.ai.proactive.BankTemplates
import mx.budget.ai.proactive.ConceptCanonicalizer
import mx.budget.ai.proactive.NaturalLanguageCaptureParser
import mx.budget.ai.proactive.NlCaptureExtractor
import mx.budget.ai.proactive.ProactiveReasoner
import mx.budget.ai.proactive.RetroAttributionEngine
import mx.budget.ai.service.AiCoreManager
import mx.budget.ai.service.HybridLlm
import mx.budget.ai.service.LiteRtLmManager
import mx.budget.ai.service.OnDeviceLlm
import mx.budget.data.calendar.CalendarMirror
import mx.budget.data.capture.BankCaptureManager
import mx.budget.data.local.BudgetDatabase
import mx.budget.data.reminder.ReminderNotifier
import mx.budget.data.reminder.ReminderWorker
import mx.budget.data.work.CanonicalizeConceptsWorker
import mx.budget.data.work.RetroAttributionWorker
import mx.budget.data.repository.CategoryRepository
import mx.budget.data.repository.ExpenseRepository
import mx.budget.data.repository.IncomeRepository
import mx.budget.data.repository.MemberRepository
import mx.budget.data.repository.QuincenaRepository
import mx.budget.data.repository.RecurrenceRepository
import mx.budget.data.repository.TransferRepository
import mx.budget.data.repository.WalletRepository
import mx.budget.data.repository.impl.CategoryRepositoryImpl
import mx.budget.data.repository.impl.ExpenseRepositoryImpl
import mx.budget.data.repository.impl.MemberRepositoryImpl
import mx.budget.data.repository.impl.QuincenaRepositoryImpl
import mx.budget.data.repository.impl.IncomeRepositoryImpl
import mx.budget.data.repository.impl.RecurrenceRepositoryImpl
import mx.budget.data.repository.impl.TransferRepositoryImpl
import mx.budget.data.repository.impl.WalletRepositoryImpl
import mx.budget.data.recurrence.RecurrenceMaterializer
import mx.budget.data.settings.SettingsRepository
import mx.budget.data.sync.RemotePullSync
import mx.budget.data.sync.SyncManager
import kotlinx.coroutines.flow.first

/**
 * Aplicación base — contenedor manual de dependencias (sin Hilt).
 *
 * Arquitectura offline-first: los repositorios PÚBLICOS apuntan a las
 * implementaciones Room (fuente de verdad). Las implementaciones Firestore
 * se conservan como "lado nube" (`remote*Repository`) y solo las usa el
 * [SyncManager] para empujar (push) los cambios encolados en `sync_queue`.
 */
class BudgetApplication : Application() {

    lateinit var database: BudgetDatabase
        private set

    lateinit var quincenaRepository: QuincenaRepository
        private set

    lateinit var expenseRepository: ExpenseRepository
        private set

    lateinit var memberRepository: MemberRepository
        private set

    lateinit var walletRepository: WalletRepository
        private set

    /** Transferencias entre wallets / pago de tarjeta (RF-41). */
    lateinit var transferRepository: TransferRepository
        private set

    /** Ingresos que acreditan el saldo del wallet al postearse. */
    lateinit var incomeRepository: IncomeRepository
        private set

    lateinit var categoryRepository: CategoryRepository
        private set

    /** Plantillas de gasto recurrente (Apéndice G.2, Fase 0). */
    lateinit var recurrenceRepository: RecurrenceRepository
        private set

    /** Materializa gastos PLANNED desde las plantillas recurrentes (Apéndice G.2, Fase 1). */
    lateinit var recurrenceMaterializer: RecurrenceMaterializer
        private set

    /** Espejo opt-in de los PLANNED a un calendario dedicado (Apéndice G.2, Fase 6). */
    lateinit var calendarMirror: CalendarMirror
        private set

    // ── MVP Fase 3: analíticas + hoja de balance (local-only por ahora) ──────

    /** Agregaciones de solo lectura (Analíticas + RAG del asistente). */
    lateinit var analyticsRepository: mx.budget.data.repository.AnalyticsRepository
        private set

    /** Planes de cuotas / MSI. */
    lateinit var installmentRepository: mx.budget.data.repository.InstallmentRepository
        private set

    /** Préstamos otorgados por el hogar. */
    lateinit var loanRepository: mx.budget.data.repository.LoanRepository
        private set

    /** Metas de ahorro. */
    lateinit var savingsRepository: mx.budget.data.repository.SavingsRepository
        private set

    /**
     * Importación de estados de cuenta bancarios (Fase C, paquete C1). Extrae
     * texto local, lo analiza con LLM cloud (NVIDIA NIM) y reconcilia contra
     * wallet + planes MSI. La reconciliación NUNCA reescribe los gastos.
     */
    lateinit var statementImportManager: mx.budget.data.statements.StatementImportManager
        private set

    /** Implementación Firestore del repositorio de gastos (solo para push). */
    lateinit var remoteExpenseRepository: ExpenseRepository
        private set

    lateinit var syncManager: SyncManager
        private set

    /** Observador remoto Firestore → Room (sincronización multi-dispositivo). */
    lateinit var remotePullSync: RemotePullSync
        private set

    /**
     * ID del único household de esta instalación, resuelto dinámicamente desde
     * la base de datos en [onCreate]. Sustituye al literal hardcodeado
     * `"default_household"`: aunque hoy ambos coincidan, el código no debe
     * asumir el literal. Ya está resuelto y listo cuando MainActivity crea los
     * ViewModels (onCreate corre antes que cualquier Activity).
     */
    var householdId: String = "default_household"
        private set

    /** Preferencias de usuario (toggle de color dinámico, etc.). */
    lateinit var settingsRepository: SettingsRepository
        private set

    /** Autenticación Firebase (anónima + Google), Fase B. */
    lateinit var authManager: mx.budget.data.remote.AuthManager
        private set

    /** Pertenencia multi-hogar (roles, invites, propuestas) sobre Firestore, Fase B. */
    lateinit var membershipRepository: mx.budget.data.remote.MembershipRepository
        private set

    /**
     * Motor de inferencia de atribución (Apéndice F.3.5), reutilizable. Lo
     * construye el pipeline retroactivo (Feature B) dentro de su worker; aquí lo
     * exponemos también para la captura en vivo (Feature A, §F.4): el
     * [mx.budget.ui.capture.CaptureViewModel] lo consume para sugerir la
     * atribución mientras el usuario teclea el concepto.
     */
    lateinit var retroAttributionEngine: RetroAttributionEngine
        private set

    /**
     * Parser de notificaciones bancarias (Feature D, §F.6). `null` si el asset
     * `bank_templates.json` no se pudo cargar (la feature queda inerte, no crashea).
     */
    var bankNotificationParser: BankNotificationParser? = null
        private set

    /**
     * Orquestador de la captura bancaria (ingest/confirm/dismiss). Lo comparten el
     * listener, la notificación propia y el dashboard.
     */
    lateinit var bankCaptureManager: BankCaptureManager
        private set

    /**
     * Proveedor de ubicación on-device (Apéndice G.4). Lo comparten la captura
     * in-app (CaptureViewModel), la captura bancaria (BankCaptureManager) y el
     * detalle del gasto (añadir ubicación manual). Opt-in por nivel desde Perfil.
     */
    lateinit var locationProvider: mx.budget.data.location.LocationProvider
        private set

    /**
     * Razonador proactivo con Gemini Nano on-device (Capa 3, §F.8). Envuelve el
     * ranking SQL determinista de Feature C: el LLM re-prioriza y reexplica los
     * candidatos sin inventar gastos. Si el dispositivo no expone Gemini Nano
     * (emulador, chip sin Tensor, alpha no disponible) cae INTACTO al SQL. Lo
     * consume el [mx.budget.ui.dashboard.DashboardViewModel] al abrir el dashboard
     * (foreground-only: AICore bloquea inferencia en background).
     */
    lateinit var proactiveReasoner: ProactiveReasoner
        private set

    /**
     * Motor LLM on-device híbrido (AICore → LiteRT-LM → no-disponible), §F.8.
     * Compartido por el [proactiveReasoner] (Capa 3) y el [nlCaptureExtractor]
     * (captura NL, §G.3). Único punto de construcción del LLM en runtime.
     */
    lateinit var onDeviceLlm: OnDeviceLlm
        private set

    /**
     * Extractor de captura en lenguaje natural (§G.3). LLM opcional → parser
     * determinista garantizado. Lo consume [bankCaptureManager.ingestText] para
     * voz in-app / widget / reloj.
     */
    lateinit var nlCaptureExtractor: NlCaptureExtractor
        private set

    /** Valor inicial del toggle de color dinámico, leído una vez al arrancar. */
    var initialDynamicColor: Boolean = true
        private set

    /**
     * `true` si la instalación está vacía (sin hogar/miembros/gastos) y hay que
     * mostrar el **wizard de onboarding** (paquete B2). Resuelto síncrono en
     * [onCreate] (mismo patrón que [householdId]); lo lee [MainActivity] para
     * decidir el `startDestination` del NavHost. La instalación de Norma (semilla
     * de 793 gastos) NUNCA lo dispara: entra directo al dashboard.
     */
    var needsOnboarding: Boolean = false
        private set

    /** Scope de larga vida para tareas de sincronización en background. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Instancia Firestore compartida (guardada para re-anclar el sync, Fase B). */
    private lateinit var firestore: com.google.firebase.firestore.FirebaseFirestore

    override fun onCreate() {
        super.onCreate()

        database = Room.databaseBuilder(
            this,
            BudgetDatabase::class.java,
            "budget.db"
        )
            .createFromAsset("budget_database.db")
            .addMigrations(
                BudgetDatabase.MIGRATION_1_2,
                BudgetDatabase.MIGRATION_2_3,
                BudgetDatabase.MIGRATION_3_4,
                BudgetDatabase.MIGRATION_4_5,
                BudgetDatabase.MIGRATION_5_6,
                BudgetDatabase.MIGRATION_6_7,
                BudgetDatabase.MIGRATION_7_8,
                BudgetDatabase.MIGRATION_8_9,
                BudgetDatabase.MIGRATION_9_10,
                BudgetDatabase.MIGRATION_10_11,
                BudgetDatabase.MIGRATION_11_12,
                BudgetDatabase.MIGRATION_12_13,
                BudgetDatabase.MIGRATION_13_14,
                BudgetDatabase.MIGRATION_14_15
            )
            .build()

        // Preferencias persistidas + lectura inicial síncrona (una sola vez, antes
        // de cualquier Activity) para que el tema arranque sin parpadeo.
        settingsRepository = SettingsRepository(this)
        initialDynamicColor = runBlocking { settingsRepository.dynamicColor.first() }

        // Resolución del household activo (Fase B — multi-tenant):
        // 1) si el usuario eligió un hogar activo (DataStore), se usa ese;
        // 2) si no, el fallback histórico: el único hogar sembrado (getSingleId),
        //    y en última instancia el literal "default_household".
        // runBlocking es aceptable aquí: consultas instantáneas que corren una
        // sola vez durante onCreate, ANTES de cualquier Activity/ViewModel.
        householdId = runBlocking {
            settingsRepository.getActiveHouseholdId()
                ?: database.householdDao().getSingleId()
                ?: "default_household"
        }

        // DAOs de la fuente de verdad local.
        val expenseDao = database.expenseDao()
        val attributionDao = database.expenseAttributionDao()
        val syncQueueDao = database.syncQueueDao()

        // Repositorios públicos = implementaciones Room (offline-first).
        quincenaRepository = QuincenaRepositoryImpl(database.quincenaDao())
        // Desde v14 los miembros se escriben localmente (wizard, CRUD de maestros):
        // el repo estampa updated_at y encola MEMBER en el outbox.
        memberRepository = MemberRepositoryImpl(
            dao = database.memberDao(),
            syncQueueDao = syncQueueDao,
            db = database
        )
        walletRepository = WalletRepositoryImpl(
            dao = database.paymentMethodDao(),
            syncQueueDao = syncQueueDao,
            db = database
        )
        // Desde v13 las categorías se escriben localmente (alta inline, color):
        // el repo estampa updated_at y encola CATEGORY en el outbox.
        categoryRepository = CategoryRepositoryImpl(
            dao = database.categoryDao(),
            syncQueueDao = syncQueueDao,
            db = database
        )
        recurrenceRepository = RecurrenceRepositoryImpl(database.recurrenceTemplateDao())
        transferRepository = TransferRepositoryImpl(
            transferDao = database.walletTransferDao(),
            paymentMethodDao = database.paymentMethodDao(),
            syncQueueDao = syncQueueDao,
            db = database
        )
        incomeRepository = IncomeRepositoryImpl(
            dao = database.incomeSourceDao(),
            paymentMethodDao = database.paymentMethodDao(),
            syncQueueDao = syncQueueDao,
            db = database
        )
        // MVP Fase 3: analíticas + hoja de balance. Desde Fase 3.5 los repos de
        // balance encolan en sync_queue (patrón TRANSFER).
        analyticsRepository = mx.budget.data.repository.impl.AnalyticsRepositoryImpl(database.analyticsDao())
        installmentRepository = mx.budget.data.repository.impl.InstallmentRepositoryImpl(database.installmentPlanDao(), syncQueueDao, database)
        loanRepository = mx.budget.data.repository.impl.LoanRepositoryImpl(database.loanDao(), syncQueueDao, database)
        savingsRepository = mx.budget.data.repository.impl.SavingsRepositoryImpl(database.savingsGoalDao(), syncQueueDao, database)
        expenseRepository = ExpenseRepositoryImpl(
            dao = expenseDao,
            attributionDao = attributionDao,
            syncQueueDao = syncQueueDao,
            db = database
        )

        // Fase C (paquete C1): importar estados de cuenta con LLM cloud. Extractor
        // local (PDFBox/ML Kit) + cliente NVIDIA NIM (la key se lee del DataStore
        // en cada llamada, nunca hardcodeada) + reconciliación contra wallet/MSI.
        statementImportManager = mx.budget.data.statements.StatementImportManager(
            extractor = mx.budget.data.statements.StatementTextExtractor(this),
            nimClient = mx.budget.data.statements.NvidiaNimClient(
                apiKeyProvider = { settingsRepository.getNvidiaApiKey() },
            ),
            walletRepository = walletRepository,
            installmentRepository = installmentRepository,
            statementImportDao = database.statementImportDao(),
            householdId = householdId,
        )

        // Motor de atribución para la captura en vivo (Feature A). Se construye
        // igual que dentro del RetroAttributionWorker: el canonicalizer toma un
        // snapshot de los miembros del hogar (estables en runtime). runBlocking es
        // aceptable aquí por el mismo motivo que householdId: una sola query, antes
        // de que exista cualquier ViewModel.
        val activeMembers = runBlocking { database.memberDao().getActiveMembers(householdId) }
        retroAttributionEngine = RetroAttributionEngine(
            attributionDao = attributionDao,
            canonicalizer = ConceptCanonicalizer(activeMembers)
        )

        // Detección de primer arranque (paquete B2). La DB está "efectivamente
        // vacía" si NO hay hogar poblado, NO hay miembros activos y NO hay gastos.
        // Con la semilla (793 gastos, hogar + miembros) esto es false → dashboard
        // directo (caso Norma). runBlocking: 3 consultas instantáneas, una sola vez.
        needsOnboarding = runBlocking {
            val hasHousehold = database.householdDao().count() > 0
            val hasExpenses = expenseDao.getAll(householdId).isNotEmpty()
            !hasHousehold && activeMembers.isEmpty() && !hasExpenses
        }

        // Captura desde notificaciones bancarias (Feature D, §F.6). El parser lee la
        // allowlist/plantillas del asset; si falla, queda null y la feature se desactiva.
        bankNotificationParser = runCatching {
            val json = assets.open("ai/bank_templates.json").bufferedReader().use { it.readText() }
            BankNotificationParser(BankTemplates.fromJson(json))
        }.getOrNull()
        // Capa 3 (§F.8): razonamiento proactivo con Gemini Nano. AiCoreManager es
        // el primer consumidor real de AICore en runtime. El reasoner razona
        // SOBRE los candidatos del motor SQL (Feature C), nunca genera gastos; si
        // AICore no está, el dashboard se comporta idéntico a Feature C. El prompt
        // se carga del asset (fallback a "" → el reasoner igual cae al SQL).
        val proactiveSystemPrompt = runCatching {
            assets.open("ai/proactive_system_prompt.es.txt").bufferedReader().use { it.readText() }
        }.getOrDefault("")
        // Híbrido: AICore (TPU, cuando Google lo provisione) → LiteRT-LM (Gemma
        // local, corre en Tensor G4 del Fold de Norma) → no-disponible. Único
        // constructor del LLM en runtime; lo comparten Capa 3 y la captura NL.
        onDeviceLlm = HybridLlm(AiCoreManager(this), LiteRtLmManager(this))
        proactiveReasoner = ProactiveReasoner(
            llm = onDeviceLlm,
            systemPrompt = proactiveSystemPrompt,
        )

        // Captura en lenguaje natural (§G.3): LLM opcional → parser determinista.
        val nlCapturePrompt = runCatching {
            assets.open("ai/nl_capture_prompt.es.txt").bufferedReader().use { it.readText() }
        }.getOrDefault("")
        nlCaptureExtractor = NlCaptureExtractor(
            parser = NaturalLanguageCaptureParser(),
            llm = onDeviceLlm,
            systemPrompt = nlCapturePrompt,
        )

        // Proveedor de ubicación on-device (Apéndice G.4). Construido antes que la
        // captura bancaria porque ésta lo usa para el fix al confirmar/ingresar.
        locationProvider = mx.budget.data.location.LocationProvider(
            context = this,
            settings = settingsRepository,
        )

        BankCaptureManager.ensureChannel(this)
        bankCaptureManager = BankCaptureManager(
            context = this,
            pendingDao = database.pendingCaptureDao(),
            paymentMethodDao = database.paymentMethodDao(),
            categoryDao = database.categoryDao(),
            expenseDao = expenseDao,
            memberDao = database.memberDao(),
            quincenaRepository = quincenaRepository,
            expenseRepository = expenseRepository,
            engine = retroAttributionEngine,
            householdId = householdId,
            nlCaptureExtractor = nlCaptureExtractor,
            locationProvider = locationProvider,
        )

        // Materializador de recurrentes → gastos PLANNED (Apéndice G.2, Fase 1).
        recurrenceMaterializer = RecurrenceMaterializer(
            recurrenceDao = database.recurrenceTemplateDao(),
            expenseRepository = expenseRepository,
            expenseDao = expenseDao,
            paymentMethodDao = database.paymentMethodDao(),
            memberDao = database.memberDao(),
            householdId = householdId,
        )

        // Lado nube (Firestore) — usado únicamente por el SyncManager para push.
        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        this.firestore = firestore
        authManager = mx.budget.data.remote.AuthManager(this)
        membershipRepository = mx.budget.data.remote.MembershipRepository(firestore)
        remoteExpenseRepository = mx.budget.data.remote.ExpenseRepositoryFirestore(firestore, householdId)
        val remoteWalletRepository = mx.budget.data.remote.WalletRepositoryFirestore(firestore)
        val remoteTransferRepository = mx.budget.data.remote.TransferRepositoryFirestore(firestore)
        val remoteIncomeRepository = mx.budget.data.remote.IncomeRepositoryFirestore(firestore)
        // Hoja de balance (MVP Fase 3.5).
        val remoteSavingsRepository = mx.budget.data.remote.SavingsRepositoryFirestore(firestore)
        val remoteLoanRepository = mx.budget.data.remote.LoanRepositoryFirestore(firestore, householdId)
        val remoteInstallmentRepository = mx.budget.data.remote.InstallmentRepositoryFirestore(firestore)
        // Categorías (v13): push de altas/ediciones locales (color, alta inline).
        val remoteCategoryRepository = mx.budget.data.remote.CategoryRepositoryFirestore(firestore)
        // Miembros (v14): push de altas/ediciones locales (wizard, CRUD de maestros).
        val remoteMemberRepository = mx.budget.data.remote.MemberRepositoryFirestore(firestore)

        // Arranca el drenado del outbox (por conectividad + intento inicial).
        syncManager = SyncManager(
            context = this,
            scope = appScope,
            syncQueueDao = syncQueueDao,
            expenseDao = expenseDao,
            attributionDao = attributionDao,
            remoteExpenseRepository = remoteExpenseRepository,
            paymentMethodDao = database.paymentMethodDao(),
            remoteWalletRepository = remoteWalletRepository,
            transferDao = database.walletTransferDao(),
            remoteTransferRepository = remoteTransferRepository,
            incomeSourceDao = database.incomeSourceDao(),
            remoteIncomeRepository = remoteIncomeRepository,
            savingsGoalDao = database.savingsGoalDao(),
            remoteSavingsRepository = remoteSavingsRepository,
            loanDao = database.loanDao(),
            remoteLoanRepository = remoteLoanRepository,
            installmentPlanDao = database.installmentPlanDao(),
            remoteInstallmentRepository = remoteInstallmentRepository,
            categoryDao = database.categoryDao(),
            remoteCategoryRepository = remoteCategoryRepository,
            memberDao = database.memberDao(),
            remoteMemberRepository = remoteMemberRepository
        )

        // Dirección PULL (Firestore → Room). Comparte `appScope` y la misma
        // instancia de Firestore que el push. Escribe SOLO vía DAO (anti-eco).
        remotePullSync = RemotePullSync(
            firestore = firestore,
            db = database,
            scope = appScope,
            householdId = householdId
        )

        // Auth anónima ANTES de arrancar pull/push: sin usuario autenticado,
        // Firestore rechaza todo (PERMISSION_DENIED) y los snapshot listeners
        // mueren sin re-registrarse. Por eso el sign-in va primero; recién
        // después registramos los listeners del pull y drenamos el outbox del
        // push. Firestore cachea offline, así que es seguro aunque no haya red.
        appScope.launch {
            authManager.signInAnonymously()
            remotePullSync.start()
            syncManager.drain()
        }

        // Pipeline de normalización/atribución retroactiva (Apéndice F.3). Se encola
        // una sola vez por instalación; WorkManager persiste el trabajo, así que el
        // flag se marca tras encolar. Re-ejecutable manualmente desde Perfil.
        scheduleRetroLabelingIfNeeded()

        // Materializa los PLANNED faltantes de la quincena activa (idempotente).
        // Trigger de arranque (§G.2 Fase 1); el de "activación de quincena" se
        // engancha cuando exista el rollover automático.
        materializeRecurringForActiveQuincena()

        // Recordatorios de gastos PLANNED (§G.2 Fase 3). Canal + trabajo periódico.
        ReminderNotifier.ensureChannel(this)
        scheduleReminders()

        // Espejo Google Calendar (§G.2 Fase 6). Best-effort: si está activado y hay
        // permiso, reconcilia los PLANNED al arrancar; si no, no hace nada.
        calendarMirror = CalendarMirror(
            context = this,
            settings = settingsRepository,
            expenseDao = expenseDao,
            householdId = householdId,
        )
        appScope.launch {
            if (runCatching { settingsRepository.calendarMirrorEnabled.first() }.getOrDefault(false)) {
                calendarMirror.reconcile()
            }
        }
    }

    /**
     * Captura en lenguaje natural (§G.3): lanza el ingest en el [appScope] de la
     * aplicación, NO en el scope del Activity que la origina. La
     * [mx.budget.ui.capture.VoiceCaptureActivity] se cierra inmediatamente tras
     * disparar la captura; si el insert corriera en su `lifecycleScope`, se
     * cancelaría al destruirse la Activity antes de completar la suspensión.
     */
    fun captureNaturalLanguage(rawText: String, source: String) {
        appScope.launch { bankCaptureManager.ingestText(rawText, source) }
    }

    // ── Fase B: multi-tenant (Google Sign-In + re-anclaje de sync) ──────────────

    /**
     * Re-ancla la sincronización al [newHouseholdId] elegido por el usuario y lo
     * persiste como hogar activo.
     *
     * **Qué queda REACTIVO sin reiniciar la app:** la dirección PULL (Firestore →
     * Room) — se paran los listeners viejos y se arranca un [RemotePullSync] nuevo
     * apuntado al hogar nuevo; y el DRAIN del push, que reintenta el outbox.
     *
     * **Qué requiere REINICIO de la app:** `app.householdId` es un valor leído por
     * MUCHÍSIMos constructores de repos/ViewModels en onCreate/MainActivity, así
     * que las pantallas ya montadas siguen consultando Room con el hogar anterior
     * hasta que el proceso se recree. Por eso, además de re-anclar el sync, se
     * actualiza el campo y se recomienda relanzar la Activity (la UI de selector
     * lo hace tras llamar aquí). Documentado en el reporte del paquete B1.
     */
    fun switchActiveHousehold(newHouseholdId: String) {
        if (newHouseholdId == householdId) return
        householdId = newHouseholdId
        appScope.launch {
            settingsRepository.setActiveHouseholdId(newHouseholdId)
            // Re-ancla el PULL al nuevo hogar (stop viejo → new(hid).start()).
            runCatching { remotePullSync.stop() }
            remotePullSync = RemotePullSync(
                firestore = firestore,
                db = database,
                scope = appScope,
                householdId = newHouseholdId,
            )
            remotePullSync.start()
            // Reintenta el outbox; los pushes derivan la colección del propio
            // entity.householdId, así que no requieren reconstruir el SyncManager.
            runCatching { syncManager.drain() }
        }
    }

    /**
     * Vincula la cuenta de Google (desde Perfil) y migra la pertenencia: registra
     * al uid como OWNER del hogar activo y fija su perfil. Conserva el uid anónimo
     * si es posible (link) para no perder la propiedad de `default_household`.
     *
     * @param activityContext contexto de Activity (Credential Manager necesita ventana).
     * @return `true` si quedó autenticado con Google.
     */
    suspend fun linkGoogleAccount(activityContext: android.content.Context): Boolean {
        val user = authManager.signInWithGoogle(activityContext) ?: return false
        val uid = user.uid
        val displayName = user.displayName ?: user.email ?: "Yo"
        membershipRepository.upsertUserProfile(
            uid = uid,
            displayName = user.displayName,
            email = user.email,
            photoUrl = user.photoUrl?.toString(),
        )
        // Migración de la instalación de Norma: el hogar activo (típicamente el
        // sembrado) queda reclamado por este uid como OWNER.
        membershipRepository.claimExistingHousehold(
            householdId = householdId,
            uid = uid,
            displayName = displayName,
        )
        return true
    }

    /**
     * Registra el hogar recién creado en el wizard de onboarding (paquete B2) en la
     * nube, SOLO si el usuario ya vinculó su cuenta de Google. Si sigue anónimo, es
     * no-op: el hogar vive local (Room) y se subirá cuando vincule la cuenta desde
     * Perfil (`linkGoogleAccount` reclama el hogar activo). Best-effort.
     */
    suspend fun registerOnboardingHouseholdInCloud(name: String) {
        val user = authManager.getCurrentUser() ?: return
        if (user.isAnonymous) return
        runCatching {
            membershipRepository.createHousehold(
                name = name,
                uid = user.uid,
                displayName = user.displayName ?: user.email ?: "Yo",
            )
        }
    }

    /**
     * Ingreso manual capturado en el reloj (§G.3). A diferencia del gasto, el
     * ingreso NO pasa por la bandeja `pending_capture` (orientada a gasto): se
     * inserta directo como `PLANNED` en la quincena activa (el usuario lo postea
     * después en el teléfono). Corre en el [appScope] (sobrevive al listener
     * efímero del reloj). Al terminar refresca el snapshot del reloj.
     */
    fun captureWatchIncome(amountMxn: Double, label: String) {
        if (amountMxn <= 0.0) return
        appScope.launch {
            runCatching {
                val quincena = quincenaRepository.getActive(householdId) ?: return@launch
                val members = database.memberDao().getActiveMembers(householdId)
                val memberId = members.firstOrNull { it.role == "PAYER_ADULT" }?.id
                    ?: members.firstOrNull()?.id ?: return@launch
                val today = java.time.LocalDate
                    .now(java.time.ZoneId.of("America/Mexico_City")).toString()
                incomeRepository.insert(
                    mx.budget.data.local.entity.IncomeSourceEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        householdId = householdId,
                        quincenaId = quincena.id,
                        memberId = memberId,
                        label = label.trim().ifBlank { "Ingreso" }.take(64),
                        amountMxn = amountMxn,
                        expectedDate = today,
                        status = "PLANNED",
                        createdAt = System.currentTimeMillis(),
                    )
                )
            }
            mx.budget.service.WearSnapshotBuilder.push(this@BudgetApplication)
        }
    }

    /**
     * Confirma una captura de la bandeja desde el reloj: reusa el mismo camino
     * agnóstico a la fuente que el chip del dashboard ([BankCaptureManager.confirm]),
     * que arma el gasto POSTED con atribución inferida. Refresca el snapshot del
     * reloj al terminar (para que la lista de pendientes baje el item).
     */
    fun confirmPendingFromWatch(captureId: String) {
        appScope.launch {
            runCatching { bankCaptureManager.confirm(captureId) }
            mx.budget.service.WearSnapshotBuilder.push(this@BudgetApplication)
        }
    }

    /** Descarta una captura de la bandeja desde el reloj ([BankCaptureManager.dismiss]). */
    fun dismissPendingFromWatch(captureId: String) {
        appScope.launch {
            runCatching { bankCaptureManager.dismiss(captureId) }
            mx.budget.service.WearSnapshotBuilder.push(this@BudgetApplication)
        }
    }

    /**
     * Agenda el [ReminderWorker] periódico (piso 15 min de WorkManager; NO
     * `SCHEDULE_EXACT_ALARM`). `KEEP`: respeta una agenda existente entre arranques
     * para no reiniciar el contador del periodo en cada apertura de la app.
     */
    private fun scheduleReminders() {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ReminderWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Rollover + materialización al arrancar (idempotente): primero garantiza
     * que exista la quincena ACTIVE de HOY (la semilla termina en jun-2026 —
     * sin esto la app queda "SIN QUINCENA ACTIVA" desde jul-2026) y luego crea
     * los PLANNED faltantes de esa quincena.
     */
    private fun materializeRecurringForActiveQuincena() {
        appScope.launch {
            val active = runCatching {
                mx.budget.data.quincena.QuincenaRollover(
                    dao = database.quincenaDao(),
                    householdId = householdId,
                ).ensureActiveForToday()
            }.getOrNull() ?: quincenaRepository.getActive(householdId) ?: return@launch
            runCatching { recurrenceMaterializer.materialize(active) }
        }
    }

    /**
     * Encola la cadena de normalización retroactiva si aún no se ha hecho.
     * `CanonicalizeConceptsWorker` (recalcula claves canónicas) → luego
     * `RetroAttributionWorker` (infiere atribución faltante).
     */
    private fun scheduleRetroLabelingIfNeeded() {
        appScope.launch {
            val done = runCatching { settingsRepository.retroLabelingDone.first() }.getOrDefault(false)
            if (done) return@launch
            enqueueRetroLabeling(replace = false)
            settingsRepository.setRetroLabelingDone(true)
        }
    }

    /**
     * Encola la cadena de normalización retroactiva. Expuesto para el botón
     * "Re-normalizar historial" de Perfil.
     *
     * @param replace si `true`, reemplaza una corrida en curso (acción manual);
     *  si `false`, respeta la existente (arranque automático).
     */
    fun enqueueRetroLabeling(replace: Boolean) {
        val canonicalize = OneTimeWorkRequestBuilder<CanonicalizeConceptsWorker>().build()
        val attribute = OneTimeWorkRequestBuilder<RetroAttributionWorker>()
            // En re-normalización manual (replace) se revierten y recomputan los
            // AUTO_APPLIED previos; el arranque automático (KEEP) los preserva.
            .setInputData(workDataOf(RetroAttributionWorker.KEY_FORCE to replace))
            .build()
        WorkManager.getInstance(this)
            .beginUniqueWork(
                CanonicalizeConceptsWorker.UNIQUE_NAME,
                if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                canonicalize
            )
            .then(attribute)
            .enqueue()
    }
}
