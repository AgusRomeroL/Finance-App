package mx.budget.ui.masters

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.budget.data.local.entity.MemberEntity
import mx.budget.ui.common.LocalSessionMemberId
import mx.budget.ui.common.youLabel

private val ROLE_LABELS = listOf(
    "PAYER_ADULT" to "Adulto (paga)",
    "BENEFICIARY_DEPENDENT" to "Dependiente",
    "EXTERNAL_CREDITOR" to "Acreedor externo",
    "EXTERNAL_DEBTOR" to "Deudor externo",
    "EXTERNAL_SERVICE" to "Proveedor",
)

private fun roleLabel(role: String) = ROLE_LABELS.firstOrNull { it.first == role }?.second ?: role

/** CRUD de miembros del hogar (paquete B2). Local-only (sync de MEMBER pendiente). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MembersScreen(viewModel: MembersMasterViewModel, onBack: () -> Unit) {
    val members by viewModel.members.collectAsState()
    var editing by remember { mutableStateOf<MemberEntity?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    MasterScaffold(
        eyebrow = "ADMINISTRAR",
        title = "Miembros",
        onBack = onBack,
        onAdd = { showAdd = true },
    ) {
        if (members.isEmpty()) {
            EmptyHint("Aún no hay miembros. Agrega al menos un adulto que paga.")
        }
        // Identidad de sesión: el member vinculado a esta sesión se ve "(Tú)".
        val sessionId = LocalSessionMemberId.current
        members.forEach { m ->
            MasterRow(
                title = youLabel(m.displayName, m.id, sessionId) + if (!m.isActive) " · inactivo" else "",
                subtitle = roleLabel(m.role),
                onClick = { editing = m },
            )
        }
    }

    if (showAdd) {
        MemberDialog(
            initial = null,
            onSave = { name, role, _ -> viewModel.addMember(name, role); showAdd = false },
            onDismiss = { showAdd = false },
        )
    }
    editing?.let { m ->
        MemberDialog(
            initial = m,
            onSave = { name, role, active ->
                viewModel.updateMember(m, name, role, active); editing = null
            },
            onDeactivate = { viewModel.deactivate(m); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemberDialog(
    initial: MemberEntity?,
    onSave: (name: String, role: String, active: Boolean) -> Unit,
    onDeactivate: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial?.displayName ?: "") }
    var role by rememberSaveable { mutableStateOf(initial?.role ?: "PAYER_ADULT") }
    var active by rememberSaveable { mutableStateOf(initial?.isActive ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nuevo miembro" else "Editar miembro") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text("Rol", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ROLE_LABELS.forEach { (r, label) ->
                        SelectChip(label = label, selected = r == role, onClick = { role = r })
                    }
                }
                if (initial != null) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SelectChip(label = if (active) "Activo" else "Inactivo", selected = active, onClick = { active = !active })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, role, active) },
                enabled = name.isNotBlank(),
            ) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                if (onDeactivate != null && active) {
                    TextButton(onClick = onDeactivate) { Text("Desactivar") }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}

// ── Piezas compartidas de los CRUD de maestros ───────────────────────────────

@Composable
internal fun MasterScaffold(
    eyebrow: String,
    title: String,
    onBack: () -> Unit,
    onAdd: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    eyebrow,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.6.sp
                )
                Text(
                    title,
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light, fontSize = 30.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (onAdd != null) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onAdd),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Add, "Agregar", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        content()
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
internal fun MasterRow(title: String, subtitle: String, colorDot: androidx.compose.ui.graphics.Color? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (colorDot != null) {
            Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(colorDot))
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.Edit, "Editar", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable
internal fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 12.dp),
    )
}

@Composable
internal fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}
