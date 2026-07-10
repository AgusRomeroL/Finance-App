package mx.budget.ui.tutorial

import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Spec de movimiento universal de la app (Material Expressive). */
private val SpotSpring = spring<Rect>(dampingRatio = 0.8f, stiffness = 380f)

/** Tiempo máximo de espera a que un target registre sus bounds antes de degradar a globo centrado. */
private const val RESOLVE_TIMEOUT_MS = 800L

/**
 * Overlay del tutorial guiado: oscurece la pantalla, recorta un "hueco" (spotlight) sobre el
 * elemento vivo y muestra un globo explicativo con Atrás / Siguiente / Saltar.
 *
 * Se usa en DOS instancias que comparten el mismo [controller] (ver `TUTORIAL.md`):
 *  - **principal** (`orchestrate = true`), envuelve el NavHost en `BudgetNavGraph`; cubre las
 *    pantallas normales. Es quien navega entre rutas y abre/cierra la hoja de captura.
 *  - **dentro de la hoja** (`orchestrate = false`), hijo del `CaptureBottomSheet`; resuelve el
 *    problema de ventanas separadas del `ModalBottomSheet` dibujando en su propio espacio.
 *
 * @param groupFilter decide qué pasos pinta esta instancia (p. ej. `{ !it.requiresCaptureSheet }`
 *                    en la principal y `{ it.requiresCaptureSheet }` en la de la hoja).
 */
@Composable
fun TutorialOverlay(
    controller: TutorialController,
    currentRoute: String,
    onNavigate: (String) -> Unit,
    groupFilter: (TutorialStep) -> Boolean,
    orchestrate: Boolean,
    modifier: Modifier = Modifier,
    onRequestOpenCapture: () -> Unit = {},
    onRequestCloseCapture: () -> Unit = {},
) {
    // ── Orquestación (solo la instancia principal): navegar + abrir/cerrar la hoja ──
    if (orchestrate) {
        LaunchedEffect(controller.index, controller.isRunning) {
            if (!controller.isRunning) {
                onRequestCloseCapture()
                return@LaunchedEffect
            }
            val step = controller.currentStep ?: return@LaunchedEffect
            if (step.requiresCaptureSheet) {
                onRequestOpenCapture()
            } else {
                onRequestCloseCapture()
                if (step.route != currentRoute) onNavigate(step.route)
            }
        }
    }

    val step = controller.currentStep
    val mine = controller.isRunning && step != null && groupFilter(step)
    if (!mine || step == null) return

    // Origen de esta ventana (main o sheet) para convertir bounds-en-ventana → locales del overlay.
    var hostOffset by remember { mutableStateOf(Offset.Zero) }

    // Espera a que el target registre bounds; si agota el timeout, degrada a globo centrado.
    var timedOut by remember(step.key) { mutableStateOf(false) }
    LaunchedEffect(step.key) {
        timedOut = false
        controller.scrollHookFor(step.key)?.invoke()
        val resolved = withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
            snapshotFlow { controller.windowRectFor(step.key) }.filterNotNull().first()
        }
        if (resolved == null) {
            timedOut = true
            controller.markUnresolved(step.key)
        }
    }

    val liveWin = controller.windowRectFor(step.key)
    val localTarget: Rect? = liveWin?.translate(-hostOffset.x, -hostOffset.y)
    val showSpot = localTarget != null

    // Anima el hueco entre targets; conserva el último rect para deslizar suavemente.
    var lastRect by remember { mutableStateOf(Rect.Zero) }
    LaunchedEffect(localTarget) { if (localTarget != null) lastRect = localTarget }
    val spotRect by animateRectAsState(localTarget ?: lastRect, SpotSpring, label = "tutorialSpot")

    val scrim = MaterialTheme.colorScheme.scrim.copy(alpha = 0.74f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { hostOffset = it.positionInWindow() }
            .then(
                // Bloquea toques al UI de abajo salvo pasos con interacción permitida.
                if (step.allowTargetInteraction) Modifier
                else Modifier.pointerInput(step.key) { detectTapGestures { } }
            )
    ) {
        val screenH = constraints.maxHeight.toFloat()

        // ── Scrim + recorte del spotlight ──
        Canvas(Modifier.fillMaxSize()) {
            if (showSpot) {
                val pad = 8.dp.toPx()
                val r = Rect(
                    spotRect.left - pad,
                    spotRect.top - pad,
                    spotRect.right + pad,
                    spotRect.bottom + pad,
                )
                val path = Path().apply {
                    addRoundRect(RoundRect(r, CornerRadius(22.dp.toPx(), 22.dp.toPx())))
                }
                clipPath(path, clipOp = ClipOp.Difference) {
                    drawRect(scrim)
                }
            } else {
                drawRect(scrim)
            }
        }

        // ── Globo explicativo ──
        val placeBelow = when (step.calloutAnchor) {
            CalloutAnchor.Above -> false
            CalloutAnchor.Below -> true
            // Auto: si el spotlight está en la mitad superior, el globo va abajo (y viceversa).
            CalloutAnchor.Auto -> showSpot && spotRect.center.y < screenH * 0.5f
        }
        val alignment = when {
            !showSpot -> Alignment.Center
            placeBelow -> Alignment.BottomCenter
            else -> Alignment.TopCenter
        }

        CalloutCard(
            step = step,
            progressLabel = controller.progressLabel,
            isFirst = controller.index == 0,
            isLast = controller.isLastStep,
            onPrev = controller::prev,
            onNext = controller::next,
            onSkip = controller::skip,
            modifier = Modifier
                .align(alignment)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .widthIn(max = 420.dp),
        )
    }
}

@Composable
private fun CalloutCard(
    step: TutorialStep,
    progressLabel: String,
    isFirst: Boolean,
    isLast: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = progressLabel,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = step.title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = step.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onSkip) { Text("Saltar") }
                Spacer(Modifier.width(8.dp))
                Spacer(Modifier.weight(1f))
                if (!isFirst) {
                    TextButton(onClick = onPrev) { Text("Atrás") }
                    Spacer(Modifier.width(8.dp))
                }
                Button(onClick = onNext) {
                    Text(if (isLast) "Listo" else "Siguiente")
                }
            }
        }
    }
}
