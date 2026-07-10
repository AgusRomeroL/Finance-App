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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import mx.budget.data.local.entity.IncomeSourceEntity
import mx.budget.data.local.entity.MemberEntity
import mx.budget.ui.common.ColorPickerDialog
import mx.budget.ui.common.LocalSessionMemberId
import mx.budget.ui.common.youLabel

private val CADENCE_LABELS = listOf(
    "QUINCENAL" to "Quincenal",
    "MONTHLY" to "Mensual",
    "IRREGULAR" to "Irregular",
)

private fun cadenceLabel(c: String) = CADENCE_LABELS.firstOrNull { it.first == c }?.second ?: c
private fun parseColorInc(hex: String?): Color? =
    hex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }

/**
 * CRUD de fuentes de ingreso de la quincena activa (paquete B2). Sincronizable
 * (encola INCOME). Las cuentas y su alta/edición viven en la pantalla Cuentas.
 */
@Composable
fun IncomeSourcesScreen(viewModel: IncomeSourcesMasterViewModel, onBack: () -> Unit) {
    val sources by viewModel.sources.collectAsState()
    val members by viewModel.members.collectAsState()
    val hasQuincena by viewModel.hasActiveQuincena.collectAsState()
    var editing by remember { mutableStateOf<IncomeSourceEntity?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    MasterScaffold(
        eyebrow = "ADMINISTRAR",
        title = "Ingresos",
        onBack = onBack,
        onAdd = if (hasQuincena && members.isNotEmpty()) ({ showAdd = true }) else null,
    ) {
        if (!hasQuincena) {
            EmptyHint("No hay una quincena activa. Se creará al abrir la app; vuelve a intentarlo.")
        } else if (members.isEmpty()) {
            EmptyHint("Primero agrega al menos un miembro en Administrar · Miembros.")
        } else if (sources.isEmpty()) {
            EmptyHint("Aún no hay ingresos en esta quincena. Agrega el primero con el botón +.")
        }
        // Identidad de sesión: el member vinculado a esta sesión se ve "(Tú)".
        val sessionId = LocalSessionMemberId.current
        sources.forEach { s ->
            val memberName = members.firstOrNull { it.id == s.memberId }
                ?.let { youLabel(it.displayName, it.id, sessionId) } ?: "—"
            MasterRow(
                title = "${s.label} · $${s.amountMxn.toLong()}",
                subtitle = "$memberName · ${cadenceLabel(s.cadence)} · ${if (s.status == "POSTED") "Recibido" else "Planeado"}",
                colorDot = parseColorInc(s.colorHex),
                onClick = { editing = s },
            )
        }
    }

    if (showAdd) {
        IncomeDialog(
            initial = null,
            members = members,
            onSave = { memberId, label, amount, cadence, color ->
                viewModel.addSource(memberId, label, amount, cadence, color); showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }
    editing?.let { s ->
        IncomeDialog(
            initial = s,
            members = members,
            onSave = { memberId, label, amount, cadence, color ->
                viewModel.updateSource(s, memberId, label, amount, cadence, color); editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IncomeDialog(
    initial: IncomeSourceEntity?,
    members: List<MemberEntity>,
    onSave: (memberId: String, label: String, amount: Double, cadence: String, colorHex: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var label by rememberSaveable { mutableStateOf(initial?.label ?: "") }
    var amount by rememberSaveable { mutableStateOf(initial?.amountMxn?.toLong()?.toString() ?: "") }
    var memberId by rememberSaveable { mutableStateOf(initial?.memberId ?: members.firstOrNull()?.id) }
    var cadence by rememberSaveable { mutableStateOf(initial?.cadence ?: "QUINCENAL") }
    var colorHex by rememberSaveable { mutableStateOf(initial?.colorHex) }
    var showColor by rememberSaveable { mutableStateOf(false) }

    val amountVal = amount.filter { it.isDigit() || it == '.' }.toDoubleOrNull()
    val canSave = memberId != null && (amountVal ?: 0.0) > 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nuevo ingreso" else "Editar ingreso") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Etiqueta (Sueldo, Honorarios…)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Monto (MXN)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text("Miembro", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                // Identidad de sesión: tu member se pinta "(Tú)" también aquí.
                val sessionId = LocalSessionMemberId.current
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    members.forEach { m ->
                        SelectChip(label = youLabel(m.displayName, m.id, sessionId), selected = m.id == memberId, onClick = { memberId = m.id })
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Cadencia", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CADENCE_LABELS.forEach { (c, lbl) ->
                        SelectChip(label = lbl, selected = c == cadence, onClick = { cadence = c })
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(parseColorInc(colorHex) ?: MaterialTheme.colorScheme.primary))
                        Spacer(Modifier.width(12.dp))
                        Text("Color", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    }
                    TextButton(onClick = { showColor = true }) { Text("Cambiar") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(memberId!!, label, amountVal!!, cadence, colorHex) },
                enabled = canSave,
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )

    if (showColor) {
        ColorPickerDialog(
            title = "Color del ingreso",
            selectedHex = colorHex,
            onSelect = { colorHex = it; showColor = false },
            onDismiss = { showColor = false },
        )
    }
}
