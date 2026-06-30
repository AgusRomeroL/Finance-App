package mx.budget.ui.wallets

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import mx.budget.data.local.entity.PaymentMethodEntity
import java.util.UUID

// Tipos de wallet con etiqueta en español, en orden de presentación.
private val KIND_OPTIONS = listOf(
    "DEBIT_ACCOUNT" to "Débito",
    "CASH" to "Efectivo",
    "DIGITAL_WALLET" to "Digital",
    "EMPLOYER_SAVINGS_FUND" to "Fondo de ahorro",
    "CREDIT_CARD" to "Tarjeta de crédito",
    "DEPARTMENT_STORE_CARD" to "Tienda departamental",
    "BNPL_INSTALLMENT" to "Meses sin intereses",
)

private val CREDIT_KINDS = setOf("CREDIT_CARD", "DEPARTMENT_STORE_CARD", "BNPL_INSTALLMENT")

private fun String.toDoubleOrNullClean(): Double? =
    filter { it.isDigit() || it == '.' }.toDoubleOrNull()

private fun String.toIntOrNullClean(): Int? =
    filter { it.isDigit() }.toIntOrNull()

/**
 * Alta/edición de un wallet (Fase 1 — ancla del saldo). `initial = null` = nuevo.
 * Captura el **saldo inicial** declarado por el usuario (en crédito = deuda actual)
 * y, para tipos de crédito, límite/corte/pago/APR. Sigue el patrón de
 * `CaptureBottomSheet`/`NewPlannedSheet` (ModalBottomSheet acotado a 640dp).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WalletFormSheet(
    initial: PaymentMethodEntity?,
    householdId: String,
    onSave: (PaymentMethodEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by rememberSaveable { mutableStateOf(initial?.displayName ?: "") }
    var kind by rememberSaveable { mutableStateOf(initial?.kind ?: "DEBIT_ACCOUNT") }
    var opening by rememberSaveable {
        mutableStateOf(initial?.openingBalanceMxn?.takeIf { it != 0.0 }?.toLong()?.toString() ?: "")
    }
    var issuer by rememberSaveable { mutableStateOf(initial?.issuer ?: "") }
    var last4 by rememberSaveable { mutableStateOf(initial?.last4 ?: "") }
    var limit by rememberSaveable { mutableStateOf(initial?.creditLimitMxn?.toLong()?.toString() ?: "") }
    var cutoff by rememberSaveable { mutableStateOf(initial?.cutoffDay?.toString() ?: "") }
    var due by rememberSaveable { mutableStateOf(initial?.dueDay?.toString() ?: "") }
    var apr by rememberSaveable { mutableStateOf(initial?.interestApr?.toString() ?: "") }

    val isCredit = kind in CREDIT_KINDS
    val canSave = name.isNotBlank()

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
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                if (initial == null) "Nueva cuenta" else "Editar cuenta",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Grupo "Tipo": la etiqueta se mantiene pegada a sus chips (10dp),
            // mientras el espaciado mayor (18dp) separa las secciones del form.
            // verticalArrangement en la FlowRow es clave: sin él, las filas de
            // chips que envuelven (con fontScale alto + bold) se tocan.
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Tipo",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    KIND_OPTIONS.forEach { (k, label) ->
                        KindChip(label = label, selected = k == kind, onClick = { kind = k })
                    }
                }
            }

            OutlinedTextField(
                value = opening,
                onValueChange = { opening = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text(if (isCredit) "Deuda actual (MXN)" else "Saldo inicial (MXN)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (isCredit) {
                OutlinedTextField(
                    value = limit,
                    onValueChange = { limit = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Límite de crédito (MXN)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = cutoff,
                        onValueChange = { cutoff = it.filter { c -> c.isDigit() } },
                        label = { Text("Día corte") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = due,
                        onValueChange = { due = it.filter { c -> c.isDigit() } },
                        label = { Text("Día pago") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = issuer,
                        onValueChange = { issuer = it },
                        label = { Text("Emisor") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = last4,
                        onValueChange = { last4 = it.filter { c -> c.isDigit() }.take(4) },
                        label = { Text("Últimos 4") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = apr,
                    onValueChange = { apr = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Tasa anual % (APR)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                Button(
                    onClick = {
                        val openingVal = opening.toDoubleOrNullClean() ?: 0.0
                        onSave(
                            PaymentMethodEntity(
                                id = initial?.id ?: UUID.randomUUID().toString(),
                                householdId = householdId,
                                displayName = name.trim(),
                                kind = kind,
                                issuer = if (isCredit) issuer.ifBlank { null } else null,
                                last4 = if (isCredit) last4.ifBlank { null } else null,
                                cutoffDay = if (isCredit) cutoff.toIntOrNullClean() else null,
                                dueDay = if (isCredit) due.toIntOrNullClean() else null,
                                creditLimitMxn = if (isCredit) limit.toDoubleOrNullClean() else null,
                                // Fase 1: el saldo mostrado lee current_balance_mxn, así que
                                // current arranca = saldo inicial declarado. La Fase 2 lo derivará.
                                currentBalanceMxn = openingVal,
                                openingBalanceMxn = openingVal,
                                interestApr = if (isCredit) apr.toDoubleOrNullClean() else null,
                                ownerMemberId = initial?.ownerMemberId,
                                isActive = true,
                            )
                        )
                    },
                    enabled = canSave,
                    modifier = Modifier.weight(1f),
                ) { Text("Guardar") }
            }
        }
    }
}

@Composable
private fun KindChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceContainerHighest
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface
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
