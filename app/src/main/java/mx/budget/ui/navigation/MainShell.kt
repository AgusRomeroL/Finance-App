package mx.budget.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import mx.budget.ui.dashboard.NavigationRailCustom

/**
 * Shell persistente de las 4 pestañas top-level (Inicio, Calendario, Cuentas,
 * Analíticas). Perfil ya NO es pestaña: se abre desde el avatar de la barra superior
 * ([mx.budget.ui.common.AppTopBar]) como ruta secundaria.
 *
 * La barra de navegación vive AQUÍ, por fuera del contenido animado del NavHost:
 *  - en **compacto** es un [FloatingNavBar] (pill flotante + mic + "+") superpuesto
 *    al contenido, que scrollea por detrás (estilo Google Photos / Pixel Screenshots);
 *  - en **expandido** (Fold abierto, `windowWidthDp >= 600`) es un rail lateral fijo
 *    ([NavigationRailCustom]) con un FAB de captura.
 *
 * La barra NO se recrea ni se anima al cambiar de pestaña (queda fija); solo el
 * [content] (el NavHost interno) hace el cross-fade. En rutas secundarias
 * ([showBar] = false) el shell queda transparente: solo el contenido a pantalla
 * completa, con su propio botón atrás.
 *
 * @param currentRoute ruta activa (resalta la pestaña).
 * @param isExpanded   `windowWidthDp >= 600` → rail; si no, pill flotante.
 * @param showBar      si false (ruta secundaria) no dibuja barra: solo el contenido.
 * @param onNavigate   navega a una ruta top-level.
 * @param onCapture    abre la captura de gasto (botón "+" del pill / FAB del rail).
 * @param content      el NavHost interno (contenido de la ruta activa).
 */
@Composable
fun MainShell(
    currentRoute: String,
    isExpanded: Boolean,
    showBar: Boolean,
    onNavigate: (String) -> Unit,
    onCapture: () -> Unit,
    content: @Composable () -> Unit,
) {
    // Ruta secundaria: sin barra. El contenido llena la pantalla y maneja su propio
    // inset (cada pantalla trae su TopAppBar/back).
    if (!showBar) {
        Box(modifier = Modifier.fillMaxSize()) { content() }
        return
    }

    if (isExpanded) {
        // Rail lateral fijo + contenido a la derecha. Solo el rail aplica
        // statusBarsPadding (su logo no debe quedar bajo la barra de estado); cada
        // pantalla top-level maneja su propio inset superior.
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Box(modifier = Modifier.statusBarsPadding()) {
                NavigationRailCustom(
                    currentRoute = currentRoute,
                    onNavigate = onNavigate,
                    onCapture = onCapture,
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                content()
            }
        }
    } else {
        // Compacto: el contenido llena la pantalla (edge-to-edge) y el pill flotante
        // se superpone abajo. Cada pantalla top-level añade contentPadding inferior
        // para que su última fila scrollee por encima del pill sin quedar tapada.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            content()
            FloatingNavBar(
                currentRoute = currentRoute,
                onNavigate = onNavigate,
                onCapture = onCapture,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
