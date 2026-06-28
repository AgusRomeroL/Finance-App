package mx.budget.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.budget.ai.proactive.CategoryEmojiFallback
import mx.budget.data.local.entity.CategoryEntity
import mx.budget.ui.components.MonochromeEmoji

/**
 * Fila horizontal de filtros (estilo Pixel Screenshots): un botón de filtros SIEMPRE
 * a la izquierda que abre el [FilterBottomSheet], seguido de un pill por grupo de
 * categoría. Tocar un pill alterna su selección directamente. Cada pill muestra el
 * emoji monocromo del grupo ([emojiFor]) + su nombre. Selección animada con spring.
 */
@Composable
fun FilterPillsRow(
    groups: List<CategoryEntity>,
    selectedGroupIds: Set<String>,
    onToggle: (String) -> Unit,
    onOpenSheet: () -> Unit,
    modifier: Modifier = Modifier,
    emojiFor: (CategoryEntity) -> String = {
        it.suggestedEmoji?.takeIf { e -> e.isNotBlank() } ?: CategoryEmojiFallback.forCode(it.code)
    }
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterButton(active = selectedGroupIds.isNotEmpty(), onClick = onOpenSheet)
        groups.forEach { group ->
            FilterPill(
                emoji = emojiFor(group),
                label = group.displayName,
                selected = group.id in selectedGroupIds,
                onClick = { onToggle(group.id) }
            )
        }
    }
}

@Composable
private fun FilterButton(active: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "filterBtnBg"
    )
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.Tune, "Filtrar movimientos",
            tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun FilterPill(emoji: String, label: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "pillBg"
    )
    val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .height(44.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MonochromeEmoji(emoji = emoji)
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = fg,
            maxLines = 1, softWrap = false
        )
    }
}
