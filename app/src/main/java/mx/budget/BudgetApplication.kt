package mx.budget

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import mx.budget.data.local.BudgetDatabase
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
            .addMigrations(BudgetDatabase.MIGRATION_1_2)
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
    }
}
