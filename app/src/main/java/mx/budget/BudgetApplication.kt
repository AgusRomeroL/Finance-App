package mx.budget

import android.app.Application
import androidx.room.Room
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mx.budget.ai.proactive.BankNotificationParser
import mx.budget.ai.proactive.BankTemplates
import mx.budget.ai.proactive.ConceptCanonicalizer
import mx.budget.ai.proactive.RetroAttributionEngine
import mx.budget.data.capture.BankCaptureManager
import mx.budget.data.local.BudgetDatabase
import mx.budget.data.work.CanonicalizeConceptsWorker
import mx.budget.data.work.RetroAttributionWorker
import mx.budget.data.repository.CategoryRepository
import mx.budget.data.repository.ExpenseRepository
import mx.budget.data.repository.MemberRepository
import mx.budget.data.repository.QuincenaRepository
import mx.budget.data.repository.WalletRepository
import mx.budget.data.repository.impl.CategoryRepositoryImpl
import mx.budget.data.repository.impl.ExpenseRepositoryImpl
import mx.budget.data.repository.impl.MemberRepositoryImpl
import mx.budget.data.repository.impl.QuincenaRepositoryImpl
import mx.budget.data.repository.impl.WalletRepositoryImpl
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

    lateinit var categoryRepository: CategoryRepository
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
    lateinit var householdId: String
        private set

    /** Preferencias de usuario (toggle de color dinámico, etc.). */
    lateinit var settingsRepository: SettingsRepository
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

    /** Valor inicial del toggle de color dinámico, leído una vez al arrancar. */
    var initialDynamicColor: Boolean = true
        private set

    /** Scope de larga vida para tareas de sincronización en background. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
                BudgetDatabase.MIGRATION_3_4
            )
            .build()

        // Resolución del household activo (un solo household por instalación).
        // runBlocking es aceptable aquí: es una única query de una fila por
        // clave primaria (instantánea) que corre una sola vez durante onCreate,
        // ANTES de que exista cualquier Activity/ViewModel, así que no bloquea
        // ninguna UI ni se ejecuta en un hot path. Fallback al literal histórico
        // por si la DB aún no tuviera datos sembrados.
        householdId = runBlocking { database.householdDao().getSingleId() }
            ?: "default_household"

        // Preferencias persistidas + lectura inicial síncrona (una sola vez, antes
        // de cualquier Activity) para que el tema arranque sin parpadeo.
        settingsRepository = SettingsRepository(this)
        initialDynamicColor = runBlocking { settingsRepository.dynamicColor.first() }

        // DAOs de la fuente de verdad local.
        val expenseDao = database.expenseDao()
        val attributionDao = database.expenseAttributionDao()
        val syncQueueDao = database.syncQueueDao()

        // Repositorios públicos = implementaciones Room (offline-first).
        quincenaRepository = QuincenaRepositoryImpl(database.quincenaDao())
        memberRepository = MemberRepositoryImpl(database.memberDao())
        walletRepository = WalletRepositoryImpl(database.paymentMethodDao())
        categoryRepository = CategoryRepositoryImpl(database.categoryDao())
        expenseRepository = ExpenseRepositoryImpl(
            dao = expenseDao,
            attributionDao = attributionDao,
            syncQueueDao = syncQueueDao,
            db = database
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

        // Captura desde notificaciones bancarias (Feature D, §F.6). El parser lee la
        // allowlist/plantillas del asset; si falla, queda null y la feature se desactiva.
        bankNotificationParser = runCatching {
            val json = assets.open("ai/bank_templates.json").bufferedReader().use { it.readText() }
            BankNotificationParser(BankTemplates.fromJson(json))
        }.getOrNull()
        BankCaptureManager.ensureChannel(this)
        bankCaptureManager = BankCaptureManager(
            context = this,
            pendingDao = database.pendingBankCaptureDao(),
            paymentMethodDao = database.paymentMethodDao(),
            categoryDao = database.categoryDao(),
            expenseDao = expenseDao,
            memberDao = database.memberDao(),
            quincenaRepository = quincenaRepository,
            expenseRepository = expenseRepository,
            engine = retroAttributionEngine,
            householdId = householdId,
        )

        // Lado nube (Firestore) — usado únicamente por el SyncManager para push.
        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        remoteExpenseRepository = mx.budget.data.remote.ExpenseRepositoryFirestore(firestore)

        // Arranca el drenado del outbox (por conectividad + intento inicial).
        syncManager = SyncManager(
            context = this,
            scope = appScope,
            syncQueueDao = syncQueueDao,
            expenseDao = expenseDao,
            attributionDao = attributionDao,
            remoteExpenseRepository = remoteExpenseRepository
        )

        // Dirección PULL (Firestore → Room). Se arranca como objeto
        // independiente (lo más simple): comparte el mismo `appScope` y la
        // misma instancia de Firestore que el push. Escribe SOLO vía DAO
        // (anti-eco), así que nunca re-encola en `sync_queue`. Firestore SDK
        // cachea offline, por lo que registrar los listeners aquí es seguro
        // aunque no haya red al arrancar.
        remotePullSync = RemotePullSync(
            firestore = firestore,
            db = database,
            scope = appScope,
            householdId = householdId
        )
        remotePullSync.start()

        // Pipeline de normalización/atribución retroactiva (Apéndice F.3). Se encola
        // una sola vez por instalación; WorkManager persiste el trabajo, así que el
        // flag se marca tras encolar. Re-ejecutable manualmente desde Perfil.
        scheduleRetroLabelingIfNeeded()
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
