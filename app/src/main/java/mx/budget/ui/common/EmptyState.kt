package mx.budget.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import mx.budget.ui.theme.BudgetMotion

/**
 * Empty state reutilizable: icono + título + cuerpo opcional + CTA opcional que
 * lleva al usuario al SIGUIENTE PASO natural (journey guiado — el usuario nunca
 * debe adivinar qué hacer frente a una pantalla vacía).
 *
 * Entrada con fade+scale de resorte ([BudgetMotion]); respeta [LocalReducedMotion].
 * [compact] reduce paddings/tamaños para vacíos dentro de tarjetas pequeñas.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    ctaLabel: String? = null,
    onCta: (() -> Unit)? = null,
    compact: Boolean = false,
) {
    val reduced = LocalReducedMotion.current
    val appear = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduced) appear.animateTo(1f, animationSpec = BudgetMotion.standard())
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 20.dp else 48.dp)
            .graphicsLayer {
                val p = appear.value
                alpha = p
                scaleX = 0.94f + 0.06f * p
                scaleY = 0.94f + 0.06f * p
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(if (compact) 32.dp else 48.dp),
            )
            Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (body != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (ctaLabel != null && onCta != null) {
                Spacer(Modifier.height(if (compact) 12.dp else 16.dp))
                val interaction = rememberPressInteractionSource()
                FilledTonalButton(
                    onClick = onCta,
                    interactionSource = interaction,
                    modifier = Modifier.pressScale(interactionSource = interaction),
                ) { Text(ctaLabel) }
            }
        }
    }
}
