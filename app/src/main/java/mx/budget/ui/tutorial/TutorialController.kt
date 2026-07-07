package mx.budget.ui.tutorial

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Rect

/**
 * Estado + orquestación mínima del tutorial guiado (coach-marks / spotlight).
 *
 * Es una clase simple (NO ViewModel): sigue el estilo de DI manual + paso de parámetros del
 * resto de la app. Se `remember`ea una sola vez en `BudgetNavGraph`. Guarda el paso actual y
 * el registro de bounds de cada target; las decisiones de navegación / apertura de hoja las
 * ejecuta `TutorialOverlay` (que recompone con callbacks frescos), no esta clase.
 *
 * Contrato de robustez: si un [TutorialKey] no está registrado (sección renombrada o borrada,
 * o target reciclado fuera de pantalla), [windowRectFor] devuelve `null` y el overlay degrada a
 * un globo centrado — el tour nunca crashea. Ver `TUTORIAL.md`.
 *
 * @param steps      guion del tour ([TutorialSpec.steps]).
 * @param onMarkSeen se invoca al terminar o saltar (persistir `has_seen_tutorial = true`).
 */
class TutorialController(
    val steps: List<TutorialStep>,
    private val onMarkSeen: () -> Unit,
) {
    private data class TargetHandle(val windowRect: Rect, val scrollTo: (suspend () -> Unit)?)

    /** Bounds (en coordenadas de la ventana que hospeda al target) por clave. Snapshot-observable. */
    private val targets = mutableStateMapOf<TutorialKey, TargetHandle>()

    var isRunning by mutableStateOf(false)
        private set
    var index by mutableIntStateOf(0)
        private set

    /**
     * `true` mientras el tour corre → las pantallas muestran datos DEMO (ver TutorialDemoData).
     * Es lo mismo que [isRunning]; se expone con nombre propio para que los call-sites lean
     * `demoActive` (intención clara) al decidir si sustituyen su estado real.
     */
    val demoActive: Boolean get() = isRunning

    /** `true` si el arranque actual es la primera vez (auto), no un relanzamiento desde Perfil. */
    var firstRun: Boolean = true
        private set

    /** Aviso previo (solo relanzado desde Perfil): "verás datos de demostración temporales". */
    var pendingWarning by mutableStateOf(false)
        private set

    /** Invitación final (solo primera vez): "empieza tu Wallet". */
    var pendingInvitation by mutableStateOf(false)
        private set

    /** Claves que nunca resolvieron durante el tour actual (para el auto-chequeo de debug). */
    private val unresolvedKeys = mutableSetOf<TutorialKey>()

    val currentStep: TutorialStep? get() = steps.getOrNull(index)
    val progressLabel: String get() = "${index + 1} / ${steps.size}"
    val isLastStep: Boolean get() = index >= steps.lastIndex

    // ── Registro de targets (lo llama Modifier.tutorialTarget) ──────────────────
    fun registerTarget(key: TutorialKey, windowRect: Rect, scrollTo: (suspend () -> Unit)?) {
        targets[key] = TargetHandle(windowRect, scrollTo)
        unresolvedKeys.remove(key)
    }

    fun unregister(key: TutorialKey) {
        targets.remove(key)
    }

    /** Bounds del target en coordenadas de su ventana, o `null` si no está registrado. */
    fun windowRectFor(key: TutorialKey): Rect? = targets[key]?.windowRect

    fun scrollHookFor(key: TutorialKey): (suspend () -> Unit)? = targets[key]?.scrollTo

    /** El overlay lo llama cuando un paso agota el timeout sin resolver bounds. */
    fun markUnresolved(key: TutorialKey) {
        unresolvedKeys.add(key)
    }

    // ── Control del tour ────────────────────────────────────────────────────────
    /**
     * Inicia el tour. [firstRun] `true` (auto-arranque de primera vez) arranca directo con datos
     * demo; `false` (relanzado desde Perfil) muestra primero el aviso de datos demostrativos
     * ([pendingWarning]) y no arranca hasta [confirmWarning].
     */
    fun start(firstRun: Boolean = true) {
        this.firstRun = firstRun
        pendingInvitation = false
        if (firstRun) begin() else pendingWarning = true
    }

    private fun begin() {
        index = 0
        unresolvedKeys.clear()
        pendingWarning = false
        isRunning = true
    }

    /** Acepta el aviso de datos demostrativos (relanzado) → arranca el tour. */
    fun confirmWarning() = begin()

    /** Descarta el aviso sin iniciar el tour. */
    fun dismissWarning() {
        pendingWarning = false
    }

    /** Cierra la invitación final de primera vez. */
    fun dismissInvitation() {
        pendingInvitation = false
    }

    fun next() {
        if (isLastStep) finish() else index += 1
    }

    fun prev() {
        if (index > 0) index -= 1
    }

    fun skip() = finish()

    private fun finish() {
        isRunning = false
        if (unresolvedKeys.isNotEmpty()) {
            // Auto-chequeo: en debug delata claves de TutorialSpec sin tag en pantalla
            // (sección renombrada/borrada sin actualizar el tutorial). Ver TUTORIAL.md.
            Log.w("Tutorial", "Claves sin resolver en este tour: $unresolvedKeys")
        }
        onMarkSeen()
        // Primera vez: al terminar (o saltar) invita a empezar la Wallet real.
        if (firstRun) pendingInvitation = true
    }
}
