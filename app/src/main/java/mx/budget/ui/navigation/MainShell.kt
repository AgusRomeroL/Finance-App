package mx.budget.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import mx.budget.ui.dashboard.BottomNavCustom
import mx.budget.ui.dashboard.NavigationRailCustom
import mx.budget.ui.tutorial.TutorialController
import mx.budget.ui.tutorial.TutorialKey
import mx.budget.ui.tutorial.tutorialTarget

/**
 * Shell persistente de los 5 destinos de nivel superior (Inicio, Calendario, Cuentas,
 * Analíticas, Perfil).
 *
 * La barra de navegación (rail en expandido, bottom nav en compacto) vive AQUÍ, por
 * fuera del contenido animado del NavHost interno, de modo que:
 *  - la barra NO se recrea ni se anima al cambiar de pestaña (queda fija);
 *  - solo el [content] hace el cross-fade (fade-through) que aplica el NavHost.
 *
 * Reutiliza las mismas primitivas de barra que ya usaba el Dashboard
 * (`NavigationRailCustom` / `BottomNavCustom`, ahora `internal`) — no las duplica.
 *
 * IMPORTANTE — persistencia: este shell (y por tanto la barra) se coloca POR FUERA del
 * `NavHost` que anima el contenido. Como el shell ocupa una posición estable en el árbol
 * de composición para TODAS las rutas top-level, la barra no se destruye ni se recompone
 * al cambiar de pestaña: solo el [content] (el NavHost) hace el cross-fade. Para las rutas
 * secundarias (drill-down) se pasa [showBar] = false y el shell queda transparente (solo
 * el contenido, a pantalla completa, con su propio botón atrás).
 *
 * @param currentRoute ruta activa (resalta la pestaña).
 * @param isExpanded   `windowWidthDp >= 600` → rail; si no, bottom nav.
 * @param showBar      si false (ruta secundaria) no dibuja barra: solo el contenido.
 * @param onNavigate   navega a una ruta top-level.
 * @param content      el NavHost interno (contenido de la ruta activa).
 */
@Composable
fun MainShell(
    currentRoute: String,
    isExpanded: Boolean,
    showBar: Boolean,
    onNavigate: (String) -> Unit,
    tutorialController: TutorialController? = null,
    content: @Composable () -> Unit
) {
    // Ruta secundaria: sin barra. El contenido llena la pantalla y maneja su propio
    // status/navigation bar padding (cada pantalla trae su TopAppBar con botón atrás).
    if (!showBar) {
        Box(modifier = Modifier.fillMaxSize()) { content() }
        return
    }

    if (isExpanded) {
        // Rail lateral fijo + contenido a la derecha. NO se aplica statusBarsPadding al
        // Row: cada pantalla top-level ya maneja su propio inset superior (Calendar/
        // Wallets/Analytics/Profile con statusBarsPadding; Dashboard con el suyo). Solo el
        // rail lo aplica, para que su logo no quede bajo la barra de estado. Aplicarlo aquí
        // duplicaría el inset en las 4 pantallas que ya lo traen.
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            // TUTORIAL: DASH_NAV — ver TUTORIAL.md
            Box(modifier = Modifier.statusBarsPadding().tutorialTarget(TutorialKey.DASH_NAV, tutorialController)) {
                NavigationRailCustom(currentRoute = currentRoute, onNavigate = onNavigate)
            }
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                content()
            }
        }
    } else {
        // Bottom nav fija abajo (aplica su propio navigationBarsPadding internamente) +
        // contenido arriba. Cada pantalla top-level maneja su propio status bar padding.
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            bottomBar = {
                // TUTORIAL: DASH_NAV — ver TUTORIAL.md
                Box(Modifier.tutorialTarget(TutorialKey.DASH_NAV, tutorialController)) {
                    BottomNavCustom(currentRoute = currentRoute, onNavigate = onNavigate)
                }
            }
        ) { inner ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
            ) {
                content()
            }
        }
    }
}
