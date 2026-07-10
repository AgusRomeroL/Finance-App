package mx.budget.ui.wallets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import mx.budget.data.local.entity.MemberEntity
import mx.budget.data.local.entity.PaymentMethodEntity
import mx.budget.ui.common.ColorPickerDialog
import mx.budget.ui.common.LocalSessionMemberId
import mx.budget.ui.common.youLabel
import java.time.LocalDate
import java.time.ZoneId

private val ZONE: ZoneId = ZoneId.of("America/Mexico_City")

private fun String.toAmount(): Double? =
    filter { it.isDigit() || it == '.' }.toDoubleOrNull()

/**
 * Registrar ingreso: un depósito (sueldo, honorarios) que **acredita** la cuenta
 * destino. Espejo de [WalletTransferSheet]. La fecha se fija a hoy (zona México);
 * el ingreso se crea POSTED en la quincena activa.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomeSheet(
    wallets: List<PaymentMethodEntity>,
    members: List<MemberEntity>,
    onSave: (walletId: String, memberId: String, amount: Double, label: String, dateIso: String, colorHex: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val today = remember { LocalDate.now(ZONE).toString() }

    var walletId by rememberSaveable { mutableStateOf<String?>(null) }
    var memberId by rememberSaveable { mutableStateOf<String?>(null) }
    var amount by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }
    var colorHex by rememberSaveable { mutableStateOf<String?>(null) }
    var showColorPicker by remember { mutableStateOf(false) }

    val amountVal = amount.toAmount()
    val canSave = walletId != null && memberId != null && (amountVal ?: 0.0) > 0.0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetMaxWidth = 640.dp,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                // imePadding: sin esto el teclado tapa el campo enfocado (el
                // ModalBottomSheet no reajusta por IME por sí solo).
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                "Registrar ingreso",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "Un depósito que sube el saldo de la cuenta donde cae (sueldo, honorarios…).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Monto (MXN)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OptionPicker("Cuenta de depósito", wallets.map { it.id to it.displayName }, walletId) { walletId = it }
            // Identidad de sesión: tu member se pinta "(Tú)" en el selector.
            val sessionId = LocalSessionMemberId.current
            OptionPicker(
                "Miembro (quién lo recibe)",
                members.map { it.id to youLabel(it.displayName, it.id, sessionId) },
                memberId,
            ) { memberId = it }
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Etiqueta (opcional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Color de identidad del ingreso (paquete A4): punto + inicial en la lista.
            val dotColor = remember(colorHex) {
                runCatching { colorHex?.let { Color(android.graphics.Color.parseColor(it)) } }.getOrNull()
            } ?: MaterialTheme.colorScheme.primary
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(dotColor),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Color del ingreso",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                TextButton(onClick = { showColorPicker = true }) { Text("Cambiar") }
            }

            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                Button(
                    onClick = { onSave(walletId!!, memberId!!, amountVal!!, label, today, colorHex) },
                    enabled = canSave,
                    modifier = Modifier.weight(1f),
                ) { Text("Registrar") }
            }
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            title = "Color del ingreso",
            selectedHex = colorHex,
            onSelect = { colorHex = it; showColorPicker = false },
            onDismiss = { showColorPicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionPicker(
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
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { onSelect(id); expanded = false },
                )
            }
        }
    }
}
