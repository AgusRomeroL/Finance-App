package mx.budget.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import mx.budget.ui.theme.BudgetMotion

/**
 * Entrada escalonada (Kowalski *stagger*): el elemento aparece con fade + un leve
 * desplazamiento vertical, retrasado `index * 40ms` respecto al anterior, creando
 * una cascada más natural que "todo aparece de golpe".
 *
 * **Solo en la carga inicial.** El valor de [enabled] se congela en la primera
 * composición de cada item: los items que ya existen al montar la lista animan;
 * los que se componen luego al hacer scroll (o tras marcar la carga como
 * consumida) renderizan directo en su estado final. Así el scroll nunca se siente
 * lento — la regla de Kowalski: el stagger es decorativo, jamás bloquea.
 *
 * **Reduced-motion:** si [LocalReducedMotion] está activo, no anima.
 *
 * Uso típico en un `LazyColumn`:
 * ```
 * itemsIndexed(rows) { index, row ->
 *     Box(Modifier.staggeredEntrance(index)) { Row(row) }
 * }
 * ```
 *
 * @param index posición del item; el retraso se topa en [BudgetMotion.ListStaggerCap]
 *   para que colas largas no acumulen un retraso perceptible.
 * @param enabled `false` para desactivar (p. ej. tras consumir la carga inicial).
 */
fun Modifier.staggeredEntrance(index: Int, enabled: Boolean = true): Modifier = composed {
    val reduced = LocalReducedMotion.current
    // Congela la condición en la primera composición de ESTE item.
    val active = remember { enabled && !reduced }
    if (!active) {
        Modifier
    } else {
        val progress = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            val delayMs = index.coerceAtMost(BudgetMotion.ListStaggerCap) *
                BudgetMotion.ListStaggerMillis
            delay(delayMs.toLong())
            progress.animateTo(1f, animationSpec = BudgetMotion.standard())
        }
        Modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * 12.dp.toPx()
        }
    }
}
