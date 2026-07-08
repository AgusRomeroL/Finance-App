package mx.budget.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.budget.data.local.entity.RecurrenceTemplateEntity
import mx.budget.data.recurrence.RecurrenceSuggestion
import mx.budget.ui.capture.AttributionDimension
import java.text.NumberFormat
import java.util.Locale

private val mxn: NumberFormat = NumberFormat.getIntegerInstance(Locale("es", "MX"))

/**
 * Gestión de plantillas recurrentes (Apéndice G.2, Fase 4 inc. 2c): lista de
 * plantillas (activas + pausadas) con pausar/reanudar/eliminar y un editor
 * (crear/editar) en hoja modal. Reusa [AttributionDimension] para los splits.
 */
@Composable
fun TemplatesScreen(
    viewModel: RecurrenceViewModel,
    onBack: () -> Unit,
) {
    val templates by viewModel.templates.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val editorVisible by viewModel.editorVisible.collectAsState()
    var deleting by remember { mutableStateOf<RecurrenceTemplateEntity?>(null) }

    if (editorVisible) TemplateEditorSheet(viewModel)

    deleting?.let { t ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Eliminar plantilla") },
            text = { Text("¿Eliminar «${t.concept}»? Los gastos ya materializados no se borran.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(t); deleting = null }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::openNew,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) { Icon(Icons.Filled.Add, "Nueva plantilla", tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("RECURRENTES", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.6.sp)
                    Text("Plantillas", style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light, fontSize = 28.sp), color = MaterialTheme.colorScheme.onSurface)
                }
            }

            if (templates.isEmpty() && suggestions.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Repeat, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Sin plantillas recurrentes.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (suggestions.isNotEmpty()) {
                        item(key = "sug_header") {
                            SectionLabel("SUGERIDAS POR TU HISTORIAL")
                        }
                        items(suggestions, key = { "sug_${it.canonicalKey}" }) { s ->
                            SuggestionCard(
                                s = s,
                                onAccept = { viewModel.acceptSuggestion(s) },
                                onDismiss = { viewModel.dismissSuggestion(s) },
                            )
                        }
                    }
                    if (templates.isNotEmpty()) {
                        if (suggestions.isNotEmpty()) item(key = "tpl_header") { SectionLabel("TUS PLANTILLAS") }
                        items(templates, key = { it.id }) { t ->
                            TemplateCard(
                                t = t,
                                onEdit = { viewModel.openEdit(t) },
                                onToggleActive = { if (t.isActive) viewModel.pause(t.id) else viewModel.resume(t.id) },
                                onDelete = { deleting = t },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateCard(
    t: RecurrenceTemplateEntity,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onEdit).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(t.concept, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
                Text(
                    "$${mxn.format(t.defaultAmountMxn.toLong())} · ${Cadence.fromCode(t.cadence).label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            if (!t.isActive) {
                Text(
                    "Pausada",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconChip(
                icon = if (t.isActive) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                label = if (t.isActive) "Pausar" else "Reanudar",
                onClick = onToggleActive,
            )
            IconChip(icon = Icons.Filled.Delete, label = "Eliminar", onClick = onDelete)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.4.sp,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

/** Tarjeta de plantilla sugerida por el detector (Fase 5): propose-then-confirm. */
@Composable
private fun SuggestionCard(s: RecurrenceSuggestion, onAccept: () -> Unit, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(s.concept, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 2)
                Text(s.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 2)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onAccept).padding(horizontal = 18.dp, vertical = 10.dp),
            ) { Text("Crear plantilla", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary) }
            Box(
                modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable(onClick = onDismiss).padding(horizontal = 18.dp, vertical = 10.dp),
            ) { Text("Descartar", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface) }
        }
    }
}

@Composable
private fun IconChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TemplateEditorSheet(viewModel: RecurrenceViewModel) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val concept by viewModel.concept.collectAsState()
    val amount by viewModel.amountText.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val wallets by viewModel.wallets.collectAsState()
    val membersAll by viewModel.members.collectAsState()
    val categoryId by viewModel.categoryId.collectAsState()
    val walletId by viewModel.walletId.collectAsState()
    val cadence by viewModel.cadence.collectAsState()
    val dayText by viewModel.dayText.collectAsState()
    val leadDays by viewModel.leadDays.collectAsState()
    val leadQuincenaStart by viewModel.leadQuincenaStart.collectAsState()
    val beneficiaryShares by viewModel.beneficiaryShares.collectAsState()
    val payerShares by viewModel.payerShares.collectAsState()
    val externalPayerEnabled by viewModel.externalPayerEnabled.collectAsState()
    val externalPayerMemberId by viewModel.externalPayerMemberId.collectAsState()
    val saveState by viewModel.saveState.collectAsState()

    val members = remember(membersAll) { membersAll.filter { !it.role.startsWith("EXTERNAL_") } }

    ModalBottomSheet(
        onDismissRequest = viewModel::closeEditor,
        sheetState = sheetState,
        sheetMaxWidth = 640.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp).padding(bottom = 24.dp).imePadding().navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Plantilla recurrente", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)

            OutlinedTextField(concept, viewModel::onConcept, label = { Text("Concepto") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(amount, viewModel::onAmount, label = { Text("Monto default (MXN)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())

            DropdownField("Categoría", categories.firstOrNull { it.id == categoryId }?.displayName ?: "Selecciona", categories.map { it.id to it.displayName }, viewModel::onCategory)
            DropdownField("Método de pago", wallets.firstOrNull { it.id == walletId }?.displayName ?: "Selecciona", wallets.map { it.id to it.displayName }, viewModel::onWallet)
            DropdownField("Cadencia", cadence.label, Cadence.entries.map { it.code to it.label }) { code -> viewModel.onCadence(Cadence.fromCode(code)) }

            OutlinedTextField(dayText, viewModel::onDay, label = { Text("Día del mes") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())

            // Lead de recordatorio
            Text("Recordar", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                leadPresets.forEach { (days, label) ->
                    LeadChip(label = label, selected = !leadQuincenaStart && leadDays == days) { viewModel.onLeadDays(days) }
                }
                LeadChip(label = "Inicio de quincena", selected = leadQuincenaStart) { viewModel.onLeadQuincenaStart() }
            }

            AttributionDimension(
                icon = Icons.Filled.Groups,
                iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                iconBg = MaterialTheme.colorScheme.secondaryContainer,
                title = "Beneficia a", members = members, shares = beneficiaryShares,
                onToggle = viewModel::onBeneficiaryToggle, onDelta = viewModel::onBeneficiaryDelta,
                onSelectAll = viewModel::onBeneficiaryAll, onClearAll = viewModel::onBeneficiaryClear,
            )
            AttributionDimension(
                icon = Icons.Filled.Payments,
                iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                iconBg = MaterialTheme.colorScheme.tertiaryContainer,
                title = "Pagó", members = members, shares = payerShares,
                onToggle = viewModel::onPayerToggle, onDelta = viewModel::onPayerDelta,
                onSelectAll = viewModel::onPayerAll, onClearAll = viewModel::onPayerClear,
            )

            // Reembolso recurrente: un tercero paga por adelantado y se le debe reembolsar.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        "Pagado por un tercero (reembolsable)",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "No toca tus cuentas; queda como cuenta por cobrar entre miembros.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = externalPayerEnabled,
                    onCheckedChange = viewModel::onExternalPayerToggle,
                )
            }
            if (externalPayerEnabled) {
                DropdownField(
                    "Quién pagó",
                    members.firstOrNull { it.id == externalPayerMemberId }?.displayName ?: "Selecciona",
                    members.map { it.id to it.displayName },
                    viewModel::onExternalPayerMember,
                )
            }

            (saveState as? TemplateSaveState.Error)?.let { Text(it.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

            androidx.compose.material3.Button(
                onClick = viewModel::save,
                enabled = saveState !is TemplateSaveState.Saving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (saveState is TemplateSaveState.Saving) "Guardando…" else "Guardar plantilla", fontSize = 15.sp) }
        }
    }
}

private val leadPresets = listOf(0 to "Mismo día", 1 to "1 día", 2 to "2 días", 3 to "3 días", 7 to "7 días")

@Composable
private fun LeadChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
    }
}
