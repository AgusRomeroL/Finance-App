package mx.budget.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatColorReset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.google.android.material.color.MaterialColors

// ─────────────────────────────────────────────────────────────────────────────
// ColorPickerDialog — paleta de 12 swatches armonizados al primary del tema
// (paquete A4). Reutilizable: color de categoría (detalle de gasto) y color de
// fuentes de ingreso (Wallets/IncomeSheet).
// ─────────────────────────────────────────────────────────────────────────────

/** Paleta base (agradable, cubre el círculo cromático) con nombre accesible. */
private val BASE_PALETTE: List<Pair<String, Color>> = listOf(
    "Rojo" to Color(0xFFE53935),
    "Naranja" to Color(0xFFFB8C00),
    "Ámbar" to Color(0xFFFFB300),
    "Verde" to Color(0xFF43A047),
    "Teal" to Color(0xFF00897B),
    "Cian" to Color(0xFF00ACC1),
    "Azul" to Color(0xFF1E88E5),
    "Índigo" to Color(0xFF3949AB),
    "Violeta" to Color(0xFF8E24AA),
    "Rosa" to Color(0xFFD81B60),
    "Marrón" to Color(0xFF6D4C41),
    "Gris" to Color(0xFF757575),
)

/** "#RRGGBB" (mayúsculas) del color, sin alfa. */
private fun Color.toHex(): String = String.format("#%06X", 0xFFFFFF and toArgb())

/**
 * Diálogo M3 con 12 swatches **armonizados al primary del tema** (HCT/Blend con
 * tope ≤15°, mismo mecanismo que [mx.budget.ui.theme.FinanceColors]) y la opción
 * "Quitar color". El hex persistido es el del color YA armonizado (lo que el
 * usuario ve es lo que se guarda). Grid adaptable ([FlowRow]) — resiliente a
 * fontScale alto + bold.
 *
 * @param title       Título del diálogo (ej. `Color de "Súper"`).
 * @param selectedHex Hex actualmente persistido ("#RRGGBB"), o null si no hay.
 * @param onSelect    Selección del usuario: hex nuevo, o `null` = quitar color.
 * @param onDismiss   Cierre sin cambios.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(
    title: String,
    selectedHex: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    // Armoniza una sola vez por color de tema (barato: 12 blends HCT).
    val palette = remember(primary) {
        val p = primary.toArgb()
        BASE_PALETTE.map { (name, base) ->
            name to Color(MaterialColors.harmonize(base.toArgb(), p))
        }
    }
    val normalizedSelected = selectedHex?.uppercase()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    palette.forEach { (name, color) ->
                        val hex = color.toHex()
                        ColorSwatch(
                            name = name,
                            color = color,
                            selected = hex == normalizedSelected,
                            onClick = { onSelect(hex) },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                // "Quitar color": vuelve al color por defecto del tema (null).
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelect(null) }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.FormatColorReset,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Quitar color",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (normalizedSelected == null) {
                        Spacer(Modifier.weight(1f))
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Seleccionado",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

/**
 * Swatch circular de 44dp (target táctil AA). El seleccionado lleva check con
 * aparición de resorte (Material Expressive) + anillo de contraste; redundancia
 * no-cromática vía `contentDescription` con el nombre del color.
 */
@Composable
private fun ColorSwatch(
    name: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val checkScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "swatchCheck",
    )
    val onColor = if (color.luminance() > 0.5f) Color.Black else Color.White
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                else Modifier
            )
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = if (selected) "$name, seleccionado" else name
            },
        contentAlignment = Alignment.Center,
    ) {
        if (checkScale > 0.01f) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = onColor,
                modifier = Modifier
                    .size(22.dp)
                    .scale(checkScale),
            )
        }
    }
}
