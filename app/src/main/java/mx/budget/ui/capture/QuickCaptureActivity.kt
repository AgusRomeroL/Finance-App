package mx.budget.ui.capture

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import mx.budget.BudgetApplication
import mx.budget.CaptureViewModelFactory
import mx.budget.ui.common.LocalSessionMemberId
import mx.budget.ui.quicktap.OverlayPermissionScreen
import mx.budget.ui.quicktap.OverlayService
import mx.budget.ui.theme.BudgetAppTheme

/**
 * Punto de entrada de la captura rápida: Quick Tap, el mosaico de Ajustes
 * rápidos, el acceso directo del lanzador, el widget y el enlace
 * `mx.budget://capture` (especificación §3.3 y §3.5).
 *
 * Decide cuál de las tres formas mostrar, en este orden:
 *
 * 1. Con permiso para mostrarse sobre otras apps, lanza el panel flotante y se
 *    quita de en medio. Esa es la experiencia que la especificación pide: anotar
 *    sin salir de lo que estabas haciendo.
 * 2. Sin permiso y sin habérselo preguntado nunca, explica para qué sirve antes
 *    de pedirlo. Nadie concede un permiso de superposición a ciegas.
 * 3. Si ya dijo "ahora no", abre la hoja completa. La degradación tiene que
 *    servir igual, no ser un castigo.
 */
class QuickCaptureActivity : ComponentActivity() {

    private val app: BudgetApplication get() = application as BudgetApplication

    private val captureViewModel: CaptureViewModel by lazy {
        ViewModelProvider(
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
                // A3: paridad con MainActivity (ingresos, recientes reales,
                // autocompletado de categoría y quincena por fecha).
                incomeRepository = app.incomeRepository,
                expenseDao = app.database.expenseDao(),
                categoryDao = app.database.categoryDao(),
                quincenaDao = app.database.quincenaDao(),
                pendingCaptureDao = app.database.pendingCaptureDao(),
                // Pagador default de sesión (roles v2), paridad con MainActivity.
                sessionMemberId = app.linkedMemberId,
            ),
        )[CaptureViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val arranque = SystemClock.elapsedRealtime()

        val enlace = intent?.data
        val montoDelEnlace = enlace?.getQueryParameter("amount")
            ?.replace(",", "")?.toDoubleOrNull()

        if (OverlayService.puedeMostrarse(this)) {
            // El panel vive en su propio proceso de composición con su propio
            // ViewModel, así que lo que trae el enlace viaja por el intent y no
            // por el ViewModel de esta actividad.
            OverlayService.abrir(this, montoDelEnlace)
            registrarArranque(arranque, "panel")
            finish()
            return
        }

        val prefill = enlace?.let(::leerEnlace)

        setContent {
            BudgetAppTheme {
                var explicando by remember { mutableStateOf(!yaPreguntamos()) }
                CompositionLocalProvider(LocalSessionMemberId provides app.linkedMemberId) {
                    if (explicando) {
                        OverlayPermissionScreen(
                            onGrant = {
                                marcarPreguntado()
                                abrirAjustesDeSuperposicion()
                            },
                            onSkip = {
                                marcarPreguntado()
                                explicando = false
                            },
                        )
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = androidx.compose.ui.graphics.Color.Transparent,
                        ) {
                            CaptureBottomSheet(
                                viewModel = captureViewModel,
                                mode = prefill ?: CaptureSheetMode.New,
                                onDismiss = { finish() },
                            )
                        }
                    }
                }
            }
        }
        registrarArranque(arranque, "hoja")
    }

    override fun onResume() {
        super.onResume()
        // Vuelve de Ajustes con el permiso concedido: el panel es lo que pidió.
        if (OverlayService.puedeMostrarse(this)) {
            OverlayService.abrir(this)
            finish()
        }
    }

    /**
     * `mx.budget://capture?amount=85.00&category=comida&wallet=efectivo&beneficiary=self`
     *
     * Solo se resuelve lo que llega escrito; lo que falte lo decide la hoja como
     * en cualquier otra captura. Los nombres se buscan sin distinguir mayúsculas
     * ni acentos, porque quien escribe un atajo no consulta el identificador.
     */
    private fun leerEnlace(uri: Uri): CaptureSheetMode? {
        if (uri.scheme != "mx.budget") return null
        val monto = uri.getQueryParameter("amount")?.replace(",", "")?.toDoubleOrNull()
        val categoria = uri.getQueryParameter("category")
        val cuenta = uri.getQueryParameter("wallet")
        val concepto = uri.getQueryParameter("concept")
        if (monto == null && categoria == null && cuenta == null && concepto == null) return null

        lifecycleScope.launch {
            val categorias = runCatching { app.database.categoryDao().getAll(app.householdId) }
                .getOrDefault(emptyList())
            val categoryId = categoria?.let { buscado ->
                categorias.firstOrNull { it.displayName.equals(buscado, ignoreCase = true) }
                    ?: categorias.firstOrNull { it.code.equals(buscado, ignoreCase = true) }
                    ?: categorias.firstOrNull { it.id == buscado }
            }?.id
            if (monto != null) captureViewModel.setAmount(monto)
            concepto?.let(captureViewModel::onConceptChange)
            categoryId?.let(captureViewModel::onCategorySelected)
            if (cuenta != null) {
                val cuentas = runCatching {
                    app.walletRepository.observeActive(app.householdId).first()
                }.getOrDefault(emptyList())
                cuentas.firstOrNull {
                    it.displayName.equals(cuenta, ignoreCase = true) || it.id == cuenta
                }?.let { captureViewModel.onWalletSelected(it.id) }
            }
        }
        return CaptureSheetMode.New
    }

    private fun abrirAjustesDeSuperposicion() {
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"),
                )
            )
        }.onFailure { Log.w(TAG, "No se pudo abrir el ajuste de superposición", it) }
    }

    private fun yaPreguntamos(): Boolean =
        getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(CLAVE_PREGUNTADO, false)

    private fun marcarPreguntado() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean(CLAVE_PREGUNTADO, true)
            .apply()
    }

    /**
     * Traza `QuickCapture.coldStart` (§3.6). La meta es P95 por debajo de 600 ms
     * desde el gesto hasta que hay algo en pantalla.
     *
     * Se miden DOS tiempos porque significan cosas distintas y confundirlos daría
     * una cifra bonita y falsa: `actividad` es lo que tarda este punto de entrada
     * en resolver a dónde ir, y `proceso` es lo que de verdad espera la persona,
     * que incluye el arranque de la aplicación cuando el proceso estaba muerto.
     * En frío manda el segundo.
     */
    private fun registrarArranque(inicioMs: Long, camino: String) {
        val enLaActividad = SystemClock.elapsedRealtime() - inicioMs
        if (primeraVezEnEsteProceso) {
            primeraVezEnEsteProceso = false
            // Mismo reloj a los dos lados: getStartUptimeMillis va en uptimeMillis,
            // que no cuenta el sueño profundo. Restarlo de elapsedRealtime daba en
            // el emulador la cifra correcta y en un teléfono real las horas que
            // llevaba dormido (13 millones de ms en el Pixel 7).
            val desdeElProceso = SystemClock.uptimeMillis() - Process.getStartUptimeMillis()
            Log.i(
                TAG,
                "QuickCapture.coldStart camino=$camino actividadMs=$enLaActividad procesoMs=$desdeElProceso",
            )
        } else {
            // Con el proceso vivo, el tiempo del proceso ya no significa nada:
            // sería su antigüedad, no lo que esperó la persona.
            Log.i(TAG, "QuickCapture.warmStart camino=$camino actividadMs=$enLaActividad")
        }
    }

    companion object {
        private const val TAG = "QuickCapture"
        private const val PREFS = "quick_capture"
        private const val CLAVE_PREGUNTADO = "overlay_asked"

        /** Acción propia, la que dispara el mosaico y el acceso directo. */
        const val ACCION_CAPTURA_RAPIDA = "mx.budget.action.QUICK_CAPTURE"

        /** Solo el primer arranque del proceso mide el tiempo del proceso. */
        private var primeraVezEnEsteProceso = true
    }
}
