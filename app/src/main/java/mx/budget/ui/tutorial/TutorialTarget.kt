package mx.budget.ui.tutorial

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Marca este composable como una sección resaltable por el tutorial guiado.
 *
 * Registra los bounds del elemento (en coordenadas de su ventana) en [controller] cada vez que
 * se posiciona, y los da de baja al salir de la composición (scroll/recycle/navegación). El
 * overlay lee esos bounds para dibujar el spotlight. Ver `TUTORIAL.md`.
 *
 * Uso — encadenar al final del `Modifier` del elemento:
 * ```
 * Modifier
 *     .clip(...)
 *     .tutorialTarget(TutorialKey.DASH_HERO_KPI, tutorialController)  // TUTORIAL: ver TUTORIAL.md
 * ```
 *
 * [controller] es nullable a propósito: en pantallas donde el tour no está activo (o en
 * previews) se pasa `null` y el modifier es un no-op — así cada `tutorialTarget(...)` puede
 * escribirse incondicionalmente en el `Modifier` sin envolturas.
 *
 * **Auto-scroll:** el modifier adjunta un `BringIntoViewRequester` y registra por defecto un
 * `scrollTo` que trae el elemento a la vista (`requester.bringIntoView()`) antes de resaltarlo.
 * Funciona con `verticalScroll` (hoja de captura) y `LazyColumn` (calendario/ledger) sin threading
 * manual del scroll state. Sobre un elemento ya visible es no-op. Se puede sobreescribir con
 * [scrollTo] si se necesita un comportamiento específico.
 *
 * @param scrollTo hook opcional que sustituye al auto-scroll por defecto.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tutorialTarget(
    key: TutorialKey,
    controller: TutorialController?,
    scrollTo: (suspend () -> Unit)? = null,
): Modifier {
    val c = controller ?: return this
    return composed {
        val requester = remember { BringIntoViewRequester() }
        val effectiveScroll = scrollTo ?: suspend { requester.bringIntoView() }
        DisposableEffect(key) {
            onDispose { c.unregister(key) }
        }
        Modifier
            .bringIntoViewRequester(requester)
            .onGloballyPositioned { coords ->
                if (coords.isAttached) {
                    c.registerTarget(key, coords.boundsInWindow(), effectiveScroll)
                }
            }
    }
}
