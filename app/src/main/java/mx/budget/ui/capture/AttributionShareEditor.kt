package mx.budget.ui.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.budget.data.local.entity.MemberEntity
import mx.budget.ui.common.LocalSessionMemberId
import mx.budget.ui.common.youLabel
import mx.budget.ui.theme.financeColors

// ─────────────────────────────────────────────────────────────────────────────
// AttributionShareEditor — editor de % por miembro REUTILIZABLE
// ─────────────────────────────────────────────────────────────────────────────
//
// Una dimensión de atribución (beneficiarios o pagadores): encabezado con el
// estado de la suma y una rejilla de chips de miembros. Cada miembro seleccionado
// muestra un stepper de % (±5); tocar el avatar/nombre lo quita. Si [onSelectAll]
// no es null, antepone un chip "Todos".
//
// Extraído de CaptureBottomSheet para que tanto la Captura (Feature de registro)
// como la pantalla "Revisión de atribuciones" (Feature B, Apéndice F.3.7) compartan
// exactamente el mismo control de reparto — una sola fuente de verdad de UX.

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AttributionDimension(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    members: List<MemberEntity>,
    shares: Map<String, Int>,
    onToggle: (String) -> Unit,
    onDelta: (String, Int) -> Unit,
    onSelectAll: (() -> Unit)?,
    onClearAll: (() -> Unit)?
) {
    val sum = shares.values.sum()
    val allSelected = members.isNotEmpty() && shares.size == members.size
    // Identidad de sesión: el member vinculado a esta sesión se pinta "Nombre (Tú)"
    // en TODOS los usos del editor (captura, pago planeado, edición, revisión).
    val sessionId = LocalSessionMemberId.current

    // Encabezado + estado de la suma (redundancia: color + texto)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(26.dp).clip(CircleShape).background(iconBg),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = iconTint, modifier = Modifier.size(15.dp)) }
            Spacer(Modifier.width(8.dp))
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.5.sp),
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = 1.4.sp
            )
        }
        val (statusText, statusColor) = when {
            shares.isEmpty() -> "Selecciona" to MaterialTheme.colorScheme.onSurfaceVariant
            sum == 100 -> "100 %" to MaterialTheme.financeColors.income
            sum < 100 -> "Falta ${100 - sum} %" to MaterialTheme.financeColors.warning
            else -> "Excede ${sum - 100} %" to MaterialTheme.financeColors.expense
        }
        Text(statusText, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = statusColor, maxLines = 1, softWrap = false)
    }

    Spacer(Modifier.height(10.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (onSelectAll != null) {
            AllChip(selected = allSelected) { if (allSelected) onClearAll?.invoke() else onSelectAll() }
        }
        members.forEach { m ->
            val pct = shares[m.id]
            val label = youLabel(m.displayName, m.id, sessionId)
            if (pct == null) {
                UnselectedMemberChip(label) { onToggle(m.id) }
            } else {
                SelectedShareChip(
                    name = label,
                    pct = pct,
                    onRemove = { onToggle(m.id) },
                    onMinus = { onDelta(m.id, -5) },
                    onPlus = { onDelta(m.id, 5) }
                )
            }
        }
    }
}

@Composable
private fun AllChip(selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (selected) Icons.Filled.Check else Icons.Filled.Groups, null,
            tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(if (selected) 16.dp else 18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Todos",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

@Composable
private fun UnselectedMemberChip(name: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

/** Chip de miembro seleccionado con stepper de % editable. */
@Composable
private fun SelectedShareChip(name: String, pct: Int, onRemove: () -> Unit, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Región de quitar (avatar + nombre)
        Row(
            modifier = Modifier
                .clickable(onClick = onRemove)
                .padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(name.take(1).uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onPrimary)
            }
            Spacer(Modifier.width(8.dp))
            Text(name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1)
        }
        // Stepper ±
        StepButton(Icons.Filled.Remove, "Menos", onMinus)
        Text(
            "$pct %",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.widthIn(min = 34.dp),
            maxLines = 1, softWrap = false
        )
        StepButton(Icons.Filled.Add, "Más", onPlus)
        Spacer(Modifier.width(6.dp))
    }
}

@Composable
private fun StepButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(2.dp)
            .size(26.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(15.dp))
    }
}
