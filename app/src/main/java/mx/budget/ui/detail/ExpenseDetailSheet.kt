package mx.budget.ui.detail

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import mx.budget.data.local.entity.MemberEntity
import mx.budget.ui.capture.AttributionDimension
import mx.budget.ui.theme.financeColors
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Hoja de detalle de un gasto (Apéndice G.4 + edición/borrado completo, MVP Fase 1).
 *
 * Dos modos con transición animada (resorte Material Expressive):
 * - **Vista**: concepto, monto, categoría·wallet, reparto actual por rol, fecha/hora
 *   editable (G.4.1) y ubicación (G.4.2/G.4.3), con acciones Editar / Eliminar.
 * - **Edición**: concepto, monto, categoría, wallet y atribuciones por rol con el
 *   mismo [AttributionDimension] de la captura. Guardar exige que ambos roles sumen
 *   100 %; el repositorio revierte/aplica el saldo del wallet transaccionalmente.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDetailSheet(
    viewModel: ExpenseDetailViewModel,
) {
    val state by viewModel.state.collectAsState()
    val detail = state ?: return
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val categories by viewModel.categories.collectAsState()
    val wallets by viewModel.wallets.collectAsState()
    val members by viewModel.members.collectAsState()

    val money = remember { NumberFormat.getCurrencyInstance(Locale("es", "MX")) }
    val dateTimeFmt = remember { SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale("es", "MX")) }

    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = { viewModel.dismiss() },
        sheetState = sheetState,
        sheetMaxWidth = 640.dp,
    ) {
        AnimatedContent(
            targetState = detail.editing,
            transitionSpec = {
                (fadeIn(spring(stiffness = 380f)) togetherWith fadeOut(spring(stiffness = 380f)))
            },
            label = "detailMode",
        ) { editing ->
            if (editing) {
                EditModeContent(
                    detail = detail,
                    viewModel = viewModel,
                    categories = categories,
                    wallets = wallets,
                    members = members,
                )
            } else {
                ViewModeContent(
                    detail = detail,
                    viewModel = viewModel,
                    members = members,
                    money = money,
                    dateTimeFmt = dateTimeFmt,
                    onEdit = viewModel::startEdit,
                    onDeleteRequest = { confirmDelete = true },
                    onPickDateTime = {
                        showDateTimePicker(context, detail.occurredAt, viewModel::setOccurredAt)
                    },
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Eliminar gasto?") },
            text = {
                Text(
                    buildString {
                        append("Se eliminará \"${detail.row.concept}\" (${money.format(detail.row.amountMxn)}).")
                        if (detail.row.status == "POSTED") {
                            append(" Se revertirá su efecto en el saldo de ${detail.row.paymentMethodName}.")
                        }
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.delete()
                    },
                ) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") }
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Modo vista
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ViewModeContent(
    detail: ExpenseDetailState,
    viewModel: ExpenseDetailViewModel,
    members: List<MemberEntity>,
    money: NumberFormat,
    dateTimeFmt: SimpleDateFormat,
    onEdit: () -> Unit,
    onDeleteRequest: () -> Unit,
    onPickDateTime: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
    ) {
        // Encabezado: concepto + monto.
        Text(
            detail.row.concept,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            money.format(detail.row.amountMxn),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "${detail.row.categoryName} · ${detail.row.paymentMethodName}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Reparto actual por rol (solo lectura; se edita en modo edición).
        val benefSummary = attributionSummary(detail, members, "BENEFICIARY")
        val payerSummary = attributionSummary(detail, members, "PAYER")
        if (benefSummary != null || payerSummary != null) {
            Spacer(Modifier.height(8.dp))
            benefSummary?.let {
                Text(
                    "Beneficia a: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            payerSummary?.let {
                Text(
                    "Pagó: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        // ── Hora (G.4.1) ──────────────────────────────────────────────────
        DetailRow(
            label = "Fecha y hora",
            value = dateTimeFmt.format(detail.occurredAt),
            actionLabel = "Editar",
            actionIcon = Icons.Filled.Edit,
            onAction = onPickDateTime,
        )

        Spacer(Modifier.height(16.dp))

        // ── Ubicación (G.4.2 / G.4.3) ─────────────────────────────────────
        Text(
            "Ubicación",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        AnimatedContent(
            targetState = detail.hasLocation,
            transitionSpec = {
                (fadeIn(spring(stiffness = 380f)) togetherWith fadeOut(spring(stiffness = 380f)))
            },
            label = "locationArea",
        ) { hasLocation ->
            if (hasLocation) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                detail.placeLabel
                                    ?: "%.5f, %.5f".format(detail.latitude, detail.longitude),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        TextButton(onClick = viewModel::removeLocation) { Text("Quitar") }
                    }
                }
            } else {
                OutlinedButton(
                    onClick = viewModel::addLocation,
                    enabled = !detail.locating,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (detail.locating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Obteniendo ubicación…")
                    } else {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Añadir ubicación")
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        // ── Acciones (Fase 1): editar / eliminar ──────────────────────────
        detail.editError?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onDeleteRequest,
                enabled = detail.entity != null && !detail.saving,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Eliminar", color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = onEdit,
                enabled = detail.entity != null && !detail.saving,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Editar")
            }
        }
    }
}

/** "Norma 50 % · Santi 50 %" para un rol, o null si no hay atribuciones. */
private fun attributionSummary(
    detail: ExpenseDetailState,
    members: List<MemberEntity>,
    role: String,
): String? {
    val rows = detail.attributions.filter { it.role == role }
    if (rows.isEmpty()) return null
    return rows.joinToString(" · ") { row ->
        val name = members.firstOrNull { it.id == row.memberId }?.displayName ?: "¿?"
        "$name ${row.shareBps / 100} %"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Modo edición (Fase 1)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun EditModeContent(
    detail: ExpenseDetailState,
    viewModel: ExpenseDetailViewModel,
    categories: List<mx.budget.data.local.entity.CategoryEntity>,
    wallets: List<mx.budget.data.local.entity.PaymentMethodEntity>,
    members: List<MemberEntity>,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
    ) {
        Text(
            "Editar gasto",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = detail.draftConcept,
            onValueChange = viewModel::onDraftConcept,
            label = { Text("Concepto") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = detail.draftAmount,
            onValueChange = viewModel::onDraftAmount,
            label = { Text("Monto (MXN)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        EntityPicker(
            label = "Categoría",
            options = categories.map { it.id to it.displayName },
            selectedId = detail.draftCategoryId,
            onSelect = viewModel::onDraftCategory,
        )
        Spacer(Modifier.height(12.dp))

        EntityPicker(
            label = "Wallet",
            options = wallets.map { it.id to it.displayName },
            selectedId = detail.draftWalletId,
            onSelect = viewModel::onDraftWallet,
        )

        Spacer(Modifier.height(20.dp))

        AttributionDimension(
            icon = Icons.Filled.Favorite,
            iconTint = MaterialTheme.financeColors.income,
            iconBg = MaterialTheme.financeColors.incomeContainer,
            title = "Beneficia a · consume",
            members = members,
            shares = detail.beneficiaryShares,
            onToggle = viewModel::onBeneficiaryToggled,
            onDelta = viewModel::onBeneficiaryDelta,
            onSelectAll = viewModel::onBeneficiaryAll,
            onClearAll = viewModel::onBeneficiaryClear,
        )
        Spacer(Modifier.height(18.dp))
        AttributionDimension(
            icon = Icons.Filled.Payments,
            iconTint = MaterialTheme.colorScheme.onSurface,
            iconBg = MaterialTheme.colorScheme.surfaceContainerHighest,
            title = "Pagó · adelantó",
            members = members,
            shares = detail.payerShares,
            onToggle = viewModel::onPayerToggled,
            onDelta = viewModel::onPayerDelta,
            onSelectAll = null,
            onClearAll = null,
        )

        detail.editError?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(
                onClick = viewModel::cancelEdit,
                enabled = !detail.saving,
                modifier = Modifier.weight(1f),
            ) { Text("Cancelar") }
            Button(
                onClick = viewModel::saveEdit,
                enabled = detail.canSave && !detail.saving,
                modifier = Modifier.weight(1f),
            ) {
                if (detail.saving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Guardar")
            }
        }
    }
}

/** Dropdown genérico id→nombre (mismo patrón que WalletPicker de transferencias). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun EntityPicker(
    label: String,
    options: List<Pair<String, String>>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = options.firstOrNull { it.first == selectedId }?.second ?: ""
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { onSelect(id); expanded = false },
                )
            }
        }
    }
}

/** Fila etiqueta/valor con un botón de acción a la derecha. */
@Composable
private fun DetailRow(
    label: String,
    value: String,
    actionLabel: String,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        TextButton(onClick = onAction) {
            Icon(actionIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(actionLabel)
        }
    }
}

/**
 * Encadena el DatePickerDialog y el TimePickerDialog de plataforma para editar
 * `occurred_at` (G.4.1). Pragmático y fiable; respeta la zona horaria local.
 */
private fun showDateTimePicker(
    context: android.content.Context,
    currentMillis: Long,
    onPicked: (Long) -> Unit,
) {
    val cal = Calendar.getInstance().apply { timeInMillis = currentMillis }
    DatePickerDialog(
        context,
        { _, year, month, day ->
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    val picked = Calendar.getInstance().apply {
                        set(year, month, day, hour, minute, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onPicked(picked.timeInMillis)
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                true,
            ).show()
        },
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH),
        cal.get(Calendar.DAY_OF_MONTH),
    ).show()
}
