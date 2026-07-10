package mx.budget.ui.wallets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import mx.budget.data.local.entity.PaymentMethodEntity
import mx.budget.ui.theme.financeColors
import java.text.NumberFormat
import java.util.Locale

/** Kinds líquidos: el saldo es disponible (aplica el aviso de saldo insuficiente). */
private val LIQUID_KINDS = setOf("DEBIT_ACCOUNT", "CASH", "DIGITAL_WALLET", "EMPLOYER_SAVINGS_FUND")

private val mxnFmt: NumberFormat = NumberFormat.getIntegerInstance(Locale("es", "MX"))
private fun Double.toMxnSigned(): String {
    val v = this.toLong()
    return if (v < 0) "−$" + mxnFmt.format(-v) else "$" + mxnFmt.format(v)
}

/**
 * Limpia la entrada de un monto: conserva dígitos, un solo punto decimal y hasta
 * 2 decimales. Evita entradas malformadas ("1.2.3") que rompían el parseo.
 */
private fun sanitizeAmountInput(raw: String): String {
    val filtered = raw.filter { it.isDigit() || it == '.' }
    val dot = filtered.indexOf('.')
    if (dot < 0) return filtered
    val intPart = filtered.substring(0, dot)
    val decPart = filtered.substring(dot + 1).filter { it.isDigit() }.take(2)
    return "$intPart.$decPart"
}

private fun String.toAmountOrNull(): Double? =
    sanitizeAmountInput(this).toDoubleOrNull()

/**
 * Transferencia entre cuentas / pago de tarjeta (RF-41). Mueve dinero de una
 * cuenta a otra sin contarlo como gasto; si el destino es una tarjeta de crédito,
 * reduce su deuda (pago). Sigue el patrón visual de [WalletFormSheet].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletTransferSheet(
    wallets: List<PaymentMethodEntity>,
    onSave: (fromId: String, toId: String, amount: Double, note: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var fromId by rememberSaveable { mutableStateOf<String?>(null) }
    var toId by rememberSaveable { mutableStateOf<String?>(null) }
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }

    val amountVal = amount.toAmountOrNull()
    val canSave = fromId != null && toId != null && fromId != toId && (amountVal ?: 0.0) > 0.0

    // Aviso suave de saldo insuficiente (solo cuentas líquidas): no bloquea, solo
    // advierte que la cuenta quedará en negativo (sobregiros reales existen).
    val fromWallet = wallets.firstOrNull { it.id == fromId }
    val overdraws = fromWallet != null && fromWallet.kind in LIQUID_KINDS &&
        amountVal != null && amountVal > fromWallet.currentBalanceMxn

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
                "Transferir",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "Mueve dinero entre cuentas sin contarlo como gasto. Para pagar una tarjeta, elígela como destino.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            WalletPicker("Desde", wallets, fromId) { fromId = it }
            WalletPicker("Hacia (o tarjeta a pagar)", wallets, toId) { toId = it }

            OutlinedTextField(
                value = amount,
                onValueChange = { amount = sanitizeAmountInput(it) },
                label = { Text("Monto (MXN)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Nota (opcional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (fromId != null && fromId == toId) {
                Text(
                    "El origen y el destino no pueden ser la misma cuenta.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.financeColors.expense,
                )
            }

            if (overdraws && fromWallet != null && amountVal != null) {
                Text(
                    "Saldo insuficiente — ${fromWallet.displayName} quedará en " +
                        "${(fromWallet.currentBalanceMxn - amountVal).toMxnSigned()}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.financeColors.warning,
                )
            }

            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                Button(
                    onClick = { onSave(fromId!!, toId!!, amountVal!!, note) },
                    enabled = canSave,
                    modifier = Modifier.weight(1f),
                ) { Text("Transferir") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WalletPicker(
    label: String,
    wallets: List<PaymentMethodEntity>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = wallets.firstOrNull { it.id == selectedId }?.displayName ?: ""
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
            wallets.forEach { w ->
                DropdownMenuItem(
                    text = { Text(w.displayName) },
                    onClick = { onSelect(w.id); expanded = false },
                )
            }
        }
    }
}
