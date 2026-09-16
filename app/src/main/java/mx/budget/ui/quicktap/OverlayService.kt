package mx.budget.ui.quicktap

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mx.budget.BudgetApplication
import mx.budget.CaptureViewModelFactory
import mx.budget.R
import mx.budget.ui.capture.CaptureViewModel
import mx.budget.ui.common.LocalSessionMemberId
import mx.budget.ui.theme.BudgetAppTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf

/**
 * Pinta el panel de Quick Tap encima de la app que estuviera en pantalla
 * (especificación §3.3, componente B).
 *
 * Un servicio no es un `Activity`: no trae ciclo de vida, ni almacén de
 * ViewModels, ni registro de estado guardado, y Compose los exige los tres para
 * componer. Esta clase los provee ella misma y se los cuelga a la vista raíz.
 *
 * Es un servicio en primer plano de tipo `specialUse` porque Android no tiene un
 * tipo para "superposición de captura" y exige declarar uno; la notificación que
 * eso obliga a mostrar es además la forma de cerrar el panel si algo se atora.
 */
class OverlayService :
    Service(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var panel: ComposeView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            // Sin permiso no hay panel. El punto de entrada ya lo comprueba;
            // esto es la red por si el permiso se revoca con el servicio vivo.
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACCION_CERRAR) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (panel != null) return START_NOT_STICKY

        arrancarEnPrimerPlano()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        mostrarPanel(intent?.getDoubleExtra(EXTRA_MONTO, 0.0)?.takeIf { it > 0.0 })
        return START_NOT_STICKY
    }

    private fun mostrarPanel(montoInicial: Double?) {
        val app = application as BudgetApplication
        val manager = getSystemService(WindowManager::class.java) ?: return
        windowManager = manager

        val viewModel = ViewModelProvider(
            this,
            CaptureViewModelFactory(
                app.expenseRepository,
                app.quincenaRepository,
                app.walletRepository,
                app.memberRepository,
                app.categoryRepository,
                app.retroAttributionEngine,
                app.locationProvider,
                app.householdId,
                incomeRepository = app.incomeRepository,
                expenseDao = app.database.expenseDao(),
                categoryDao = app.database.categoryDao(),
                quincenaDao = app.database.quincenaDao(),
                pendingCaptureDao = app.database.pendingCaptureDao(),
                sessionMemberId = app.linkedMemberId,
            ),
        )[CaptureViewModel::class.java]

        val parametros = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // No enfocable de entrada: el panel no debe robar el teclado ni las
            // pulsaciones de la app de abajo hasta que se toque el importe.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (24 * resources.displayMetrics.density).toInt()
        }

        val vista = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
        }
        panel = vista

        // El panel se pinta YA, con la lista de sugerencias vacía, y se rellena
        // cuando el historial termina de leerse. Esperar a la consulta antes de
        // añadir la ventana metía segundos entre el gesto y el primer frame, que
        // es justo lo contrario de lo que este camino promete.
        val sugerencias = mutableStateOf(emptyList<QuickSuggestion>())
        montoInicial?.let(viewModel::setAmount)

        vista.setContent {
            BudgetAppTheme {
                CompositionLocalProvider(LocalSessionMemberId provides app.linkedMemberId) {
                    QuickCapturePanel(
                        viewModel = viewModel,
                        suggestions = sugerencias.value,
                        onDismiss = { cerrar() },
                        onSaved = { cerrar() },
                        onRequestFocus = { permitirTeclado() },
                    )
                }
            }
        }
        runCatching { manager.addView(vista, parametros) }
            .onFailure {
                Log.w(TAG, "No se pudo pintar el panel de captura", it)
                stopSelf()
                return
            }

        scope.launch {
            val encontradas = withContext(Dispatchers.IO) {
                QuickSuggestions.forNow(app.database.expenseDao(), app.householdId)
            }
            sugerencias.value = encontradas
            // Lo predicho que pide la especificación: categoría, cuenta y
            // beneficiario del gasto que el hogar suele hacer a esta hora. Solo
            // rellena los chips, nunca el importe: adivinar cuánto gastaste
            // sería inventar un dato.
            encontradas.firstOrNull()?.let { prediccion ->
                viewModel.onCategorySelected(prediccion.categoryId)
                viewModel.onWalletSelected(prediccion.walletId)
                viewModel.onSelectAllMembers()
            }
        }
    }

    /**
     * El panel nace no enfocable para no robarle las pulsaciones a la app de
     * abajo. Al tocar el importe hay que devolverle el foco, o el teclado no
     * aparece.
     */
    private fun permitirTeclado() {
        val vista = panel ?: return
        val manager = windowManager ?: return
        val parametros = vista.layoutParams as? WindowManager.LayoutParams ?: return
        parametros.flags = parametros.flags and
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        runCatching { manager.updateViewLayout(vista, parametros) }
    }

    private fun cerrar() {
        stopSelf()
    }

    private fun arrancarEnPrimerPlano() {
        crearCanal()
        val cerrar = PendingIntent.getService(
            this,
            0,
            Intent(this, OverlayService::class.java).setAction(ACCION_CERRAR),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notificacion: Notification = NotificationCompat.Builder(this, CANAL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Gasto rápido")
            .setContentText("El panel de captura está abierto.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(0, "Cerrar", cerrar)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID,
                notificacion,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIF_ID, notificacion)
        }
    }

    private fun crearCanal() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CANAL, "Captura rápida", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Avisa mientras el panel de gasto rápido está en pantalla."
            }
        )
    }

    override fun onDestroy() {
        panel?.let { vista ->
            runCatching { windowManager?.removeView(vista) }
        }
        panel = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "OverlayService"
        private const val CANAL = "quick_capture"
        private const val NOTIF_ID = 4801
        const val ACCION_CERRAR = "mx.budget.action.CLOSE_QUICK_PANEL"

        /** `true` si el panel flotante se puede pintar en este momento. */
        fun puedeMostrarse(context: Context): Boolean = Settings.canDrawOverlays(context)

        /** Importe que llega escrito en el enlace `mx.budget://capture`. */
        const val EXTRA_MONTO = "mx.budget.extra.AMOUNT"

        fun abrir(context: Context, montoMxn: Double? = null) {
            val intent = Intent(context, OverlayService::class.java)
            montoMxn?.let { intent.putExtra(EXTRA_MONTO, it) }
            context.startForegroundService(intent)
        }
    }
}
