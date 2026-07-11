package mx.budget.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import mx.budget.ui.theme.BudgetMotion

/**
 * Feedback de press: escala el elemento a [pressedScale] mientras está presionado
 * y lo devuelve a 1f al soltar, con el spres de [BudgetMotion.press].
 *
 * Es el detalle de "feel" de mayor leverage (Kowalski/Apple: *responder al toque
 * al instante*). Aplica a cualquier cosa presionable: teclas del keypad, botones,
 * cards, chips, steppers, filas de lista.
 *
 * **Orden en la cadena de Modifier:** aplica `pressScale` ANTES de `background`/
 * `clip`/`border` para que el escalado envuelva el pintado y no lo recorte:
 * ```
 * Modifier
 *     .pressScale(interactionSource = source)
 *     .clip(shape)
 *     .background(color)
 *     .clickable(interactionSource = source, indication = …) { … }
 * ```
 * Comparte el MISMO [interactionSource] con el `clickable` para que el press se
 * detecte. Si el `clickable` no expone su source, pásale uno creado aquí a ambos.
 *
 * **Reduced-motion:** si [LocalReducedMotion] está activo, no anima (queda a 1f) —
 * el movimiento es exactamente lo que reduced-motion pide quitar.
 *
 * @param pressedScale escala en press (Kowalski: sutil, 0.95–0.98). Default 0.97.
 * @param interactionSource el mismo source que consume el `clickable`/`toggleable`.
 */
fun Modifier.pressScale(
    pressedScale: Float = 0.97f,
    interactionSource: MutableInteractionSource,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val reduced = LocalReducedMotion.current
    val target = if (pressed && !reduced) pressedScale else 1f
    val scale by animateFloatAsState(
        targetValue = target,
        animationSpec = BudgetMotion.press(),
        label = "pressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Recuerda un [MutableInteractionSource] para compartir entre `pressScale` y el
 * `clickable`/`toggleable` de un mismo elemento presionable.
 */
@Composable
fun rememberPressInteractionSource(): MutableInteractionSource =
    remember { MutableInteractionSource() }
