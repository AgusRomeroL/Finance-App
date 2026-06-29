package mx.budget.ui.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.budget.ui.capture.AttributionDimension
import java.time.Instant
import java.time.ZoneOffset

/**
 * Hoja "Nuevo pago planeado" (Apéndice G.2, Fase 4 inc. 2b). Form completo de un
 * `PLANNED` manual: concepto, monto, categoría, método de pago, fecha y atribución
 * de beneficiarios/pagadores (reusa [AttributionDimension], el control compartido
 * con la captura y la revisión). Se cierra al guardar con éxito.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewPlannedSheet(
    viewModel: NewPlannedViewModel,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val concept by viewModel.concept.collectAsState()
    val amount by viewModel.amountText.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val wallets by viewModel.wallets.collectAsState()
    val membersAll by viewModel.members.collectAsState()
    val categoryId by viewModel.categoryId.collectAsState()
    val walletId by viewModel.walletId.collectAsState()
    val date by viewModel.date.collectAsState()
    val beneficiaryShares by viewModel.beneficiaryShares.collectAsState()
    val payerShares by viewModel.payerShares.collectAsState()
    val state by viewModel.state.collectAsState()

    val members = remember(membersAll) { membersAll.filter { !it.role.startsWith("EXTERNAL_") } }
    var showDatePicker by remember { mutableStateOf(false) }

    // Cierra la hoja cuando el guardado tuvo éxito.
    LaunchedEffect(state) {
        if (state is NewPlannedState.Saved) {
            viewModel.consumeState()
            onDismiss()
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        viewModel.onDate(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDatePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") } },
        ) { DatePicker(state = pickerState) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetMaxWidth = 640.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .imePadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Nuevo pago planeado",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )

            OutlinedTextField(
                value = concept,
                onValueChange = viewModel::onConcept,
                label = { Text("Concepto") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = amount,
                onValueChange = viewModel::onAmount,
                label = { Text("Monto (MXN)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Categoría
            DropdownField(
                label = "Categoría",
                selectedText = categories.firstOrNull { it.id == categoryId }?.displayName ?: "Selecciona",
                options = categories.map { it.id to it.displayName },
                onSelect = viewModel::onCategory,
            )

            // Método de pago
            DropdownField(
                label = "Método de pago",
                selectedText = wallets.firstOrNull { it.id == walletId }?.displayName ?: "Selecciona",
                options = wallets.map { it.id to it.displayName },
                onSelect = viewModel::onWallet,
            )

            // Fecha
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text("Fecha", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(date.formatLong(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            // Atribución
            AttributionDimension(
                icon = Icons.Filled.Groups,
                iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                iconBg = MaterialTheme.colorScheme.secondaryContainer,
                title = "Beneficia a",
                members = members,
                shares = beneficiaryShares,
                onToggle = viewModel::onBeneficiaryToggle,
                onDelta = viewModel::onBeneficiaryDelta,
                onSelectAll = viewModel::onBeneficiaryAll,
                onClearAll = viewModel::onBeneficiaryClear,
            )
            AttributionDimension(
                icon = Icons.Filled.Payments,
                iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                iconBg = MaterialTheme.colorScheme.tertiaryContainer,
                title = "Pagó",
                members = members,
                shares = payerShares,
                onToggle = viewModel::onPayerToggle,
                onDelta = viewModel::onPayerDelta,
                onSelectAll = viewModel::onPayerAll,
                onClearAll = viewModel::onPayerClear,
            )

            (state as? NewPlannedState.Error)?.let { err ->
                Text(err.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = viewModel::save,
                enabled = state !is NewPlannedState.Saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state is NewPlannedState.Saving) "Guardando…" else "Guardar pago planeado", fontSize = 15.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DropdownField(
    label: String,
    selectedText: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (id, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onSelect(id)
                        expanded = false
                    },
                )
            }
        }
    }
}
