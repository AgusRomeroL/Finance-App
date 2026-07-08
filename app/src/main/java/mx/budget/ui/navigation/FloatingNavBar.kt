package mx.budget.ui.navigation

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.budget.ui.capture.VoiceCaptureActivity
import mx.budget.ui.dashboard.navItems

/**
 * Barra inferior flotante estilo Google Photos / Pixel Screenshots: un **pill**
 * flotante con las 4 pestañas top-level (Inicio / Calendario / Cuentas / Analíticas)
 * + micrófono de captura por voz + botón "+" de captura. Perfil NO va aquí: se abre
 * desde el avatar de la barra superior.
 *
 * Flota (no reserva espacio): el contenido de cada pantalla hace scroll por detrás.
 * Aplica `navigationBarsPadding()` para asentarse sobre la barra de navegación del
 * sistema (ahora transparente por el blend edge-to-edge).
 *
 * Movimiento expresivo (obligatorio): el indicador de la pestaña activa se expande
 * con un resorte (color + etiqueta que aparece); nada salta.
 */
@Composable
fun FloatingNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onCapture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Pill flotante con las 4 pestañas.
        Surface(
            modifier = Modifier.weight(1f),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = 6.dp,
            tonalElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                navItems.take(4).forEach { item ->
                    NavPillItem(
                        label = item.label,
                        icon = item.icon,
                        selected = currentRoute == item.route,
                        onClick = { onNavigate(item.route) },
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }

        // Micrófono: captura por voz en lenguaje natural (propose-then-confirm).
        CircleActionButton(
            container = MaterialTheme.colorScheme.surfaceContainerHigh,
            content = MaterialTheme.colorScheme.onSurfaceVariant,
            contentDescription = "Capturar por voz",
            icon = Icons.Filled.Mic,
            onClick = {
                context.startActivity(
                    Intent(context, VoiceCaptureActivity::class.java)
                        .putExtra(VoiceCaptureActivity.EXTRA_SOURCE, "VOICE")
                )
            },
        )
        // "+": captura de gasto.
        CircleActionButton(
            container = MaterialTheme.colorScheme.primary,
            content = MaterialTheme.colorScheme.onPrimary,
            contentDescription = "Capturar gasto",
            icon = Icons.Filled.Add,
            onClick = onCapture,
        )
    }
}

/**
 * Ítem del pill: ícono siempre; al seleccionarse el fondo se tiñe a
 * `primaryContainer` y la etiqueta se expande a un costado — ambos animados con
 * resorte (M3 expresivo).
 */
@Composable
private fun NavPillItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
        label = "navPillContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
        label = "navPillContent",
    )
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(22.dp),
        )
        AnimatedVisibility(
            visible = selected,
            enter = expandHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
            exit = shrinkHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = contentColor,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Botón de acción circular flotante (mic / "+"). */
@Composable
private fun CircleActionButton(
    container: Color,
    content: Color,
    contentDescription: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = container,
        shadowElevation = 6.dp,
        modifier = Modifier.size(52.dp).clip(CircleShape).clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = content,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
