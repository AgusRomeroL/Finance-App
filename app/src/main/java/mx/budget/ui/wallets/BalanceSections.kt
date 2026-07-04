package mx.budget.ui.wallets

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import mx.budget.data.local.entity.InstallmentPlanEntity
import mx.budget.data.local.entity.LoanEntity
import mx.budget.data.local.entity.MemberEntity
import mx.budget.data.local.entity.SavingsGoalEntity
import mx.budget.ui.theme.financeColors
import java.text.NumberFormat
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// MVP Fase 3 — Secciones de "hoja de balance" para WalletsScreen:
// metas de ahorro, préstamos por cobrar y planes de cuotas (MSI).
// Se insertan como slot al final del LazyColumn de la lista de wallets.
// ─────────────────────────────────────────────────────────────────────────────

/** Secciones Ahorro / Préstamos / MSI, con fila "+ Nuevo" por sección. */
fun LazyListScope.balanceSheetSections(
    savings: List<SavingsGoalEntity>,
    loans: List<LoanEntity>,
    installments: List<InstallmentPlanEntity>,
    memberNames: Map<String, String>,
    money: NumberFormat,
    onEditSavings: (SavingsGoalEntity?) -> Unit,
    onEditLoan: (LoanEntity?) -> Unit,
    onEditInstallment: (InstallmentPlanEntity?) -> Unit,
) {
    // ── Metas de ahorro ───────────────────────────────────────────────────────
    item(key = "header_savings") { BalanceSectionHeader("Metas de ahorro") }
    items(savings.size, key = { "savings_${savings[it].id}" }) { i ->
        val g = savings[i]
        BalanceRowCard(onClick = { onEditSavings(g) }) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(g.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 2)
                    Text(
                        "${money.format(g.currentMxn)} / ${money.format(g.targetMxn)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(6.dp))
                val target = if (g.targetMxn > 0) (g.currentMxn / g.targetMxn).coerceIn(0.0, 1.0).toFloat() else 0f
                val fraction by animateFloatAsState(
                    targetValue = target,
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
                    label = "savingsFill",
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.financeColors.income),
                    )
                }
            }
        }
    }
    item(key = "add_savings") { AddRow("Nueva meta de ahorro") { onEditSavings(null) } }

    // ── Préstamos por cobrar ──────────────────────────────────────────────────
    item(key = "header_loans") { BalanceSectionHeader("Préstamos por cobrar") }
    items(loans.size, key = { "loan_${loans[it].id}" }) { i ->
        val l = loans[i]
        BalanceRowCard(onClick = { onEditLoan(l) }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        memberNames[l.debtorMemberId] ?: "Deudor",
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                    )
                    Text(
                        "Prestado ${money.format(l.principalMxn)}" + (l.dueAt?.let { " · vence $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        money.format(l.remainingBalanceMxn),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (l.remainingBalanceMxn > 0) MaterialTheme.financeColors.warning
                        else MaterialTheme.financeColors.income,
                        maxLines = 1,
                    )
                    Text(
                        if (l.remainingBalanceMxn > 0) "pendiente" else "liquidado",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    item(key = "add_loan") { AddRow("Nuevo préstamo") { onEditLoan(null) } }

    // ── Planes de cuotas (MSI) ────────────────────────────────────────────────
    // "Planes a meses" (no "Meses sin intereses") para no chocar con la sección
    // de wallets BNPL que ya usa ese rótulo arriba.
    item(key = "header_installments") { BalanceSectionHeader("Planes a meses (MSI)") }
    items(installments.size, key = { "plan_${installments[it].id}" }) { i ->
        val p = installments[i]
        BalanceRowCard(onClick = { onEditInstallment(p) }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(p.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                    Text(
                        "Cuota ${p.currentInstallment} de ${p.totalInstallments} · ${money.format(p.installmentAmountMxn)} c/u",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    money.format((p.totalInstallments - p.currentInstallment) * p.installmentAmountMxn),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.financeColors.expense,
                    maxLines = 1,
                )
            }
        }
    }
    item(key = "add_installment") { AddRow("Nuevo plan a meses") { onEditInstallment(null) } }
}

@Composable
private fun BalanceSectionHeader(label: String) {
    Text(
        label.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
    )
}

@Composable
private fun BalanceRowCard(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) { content() }
    }
}

@Composable
private fun AddRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sheets de alta/edición
// ─────────────────────────────────────────────────────────────────────────────

/** Alta/edición de meta de ahorro; editar `current` sirve como abono manual. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsGoalSheet(
    existing: SavingsGoalEntity?,
    onSave: (name: String, targetMxn: Double, currentMxn: Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var target by remember { mutableStateOf(existing?.targetMxn?.toPlainString() ?: "") }
    var current by remember { mutableStateOf(existing?.currentMxn?.toPlainString() ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetMaxWidth = 640.dp) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                if (existing == null) "Nueva meta de ahorro" else "Editar meta",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = target, onValueChange = { target = it.moneyInput() },
                label = { Text("Meta (MXN)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = current, onValueChange = { current = it.moneyInput() },
                label = { Text("Ahorrado hasta hoy (MXN)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                Button(
                    onClick = { onSave(name.trim(), target.toDoubleOrNull() ?: 0.0, current.toDoubleOrNull() ?: 0.0) },
                    enabled = name.isNotBlank() && (target.toDoubleOrNull() ?: 0.0) > 0,
                    modifier = Modifier.weight(1f),
                ) { Text("Guardar") }
            }
        }
    }
}

/** Alta/edición de préstamo, con abono ("Registrar pago") y borrado. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanSheet(
    existing: LoanEntity?,
    members: List<MemberEntity>,
    onSave: (debtorMemberId: String, principal: Double, notes: String?) -> Unit,
    onPayment: (loanId: String, amount: Double) -> Unit,
    onDelete: (LoanEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var debtorId by remember { mutableStateOf(existing?.debtorMemberId) }
    var principal by remember { mutableStateOf(existing?.principalMxn?.toPlainString() ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var payment by remember { mutableStateOf("") }
    val money = remember { NumberFormat.getCurrencyInstance(Locale("es", "MX")) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetMaxWidth = 640.dp) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                if (existing == null) "Nuevo préstamo" else "Préstamo",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.height(14.dp))
            MemberPicker(
                label = "Deudor",
                members = members,
                selectedId = debtorId,
                onSelect = { debtorId = it },
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = principal, onValueChange = { principal = it.moneyInput() },
                label = { Text("Monto prestado (MXN)") }, singleLine = true,
                enabled = existing == null, // el capital no se edita: se abona
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                label = { Text("Notas") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )

            if (existing != null) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "Pendiente: ${money.format(existing.remainingBalanceMxn)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.financeColors.warning,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = payment, onValueChange = { payment = it.moneyInput() },
                        label = { Text("Pago recibido") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            payment.toDoubleOrNull()?.let { onPayment(existing.id, it) }
                            payment = ""
                        },
                        enabled = (payment.toDoubleOrNull() ?: 0.0) > 0,
                    ) { Text("Abonar") }
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (existing != null) {
                    TextButton(onClick = { onDelete(existing) }, modifier = Modifier.weight(1f)) {
                        Text("Eliminar", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                Button(
                    onClick = { onSave(debtorId!!, principal.toDoubleOrNull() ?: 0.0, notes.ifBlank { null }) },
                    enabled = debtorId != null && (principal.toDoubleOrNull() ?: 0.0) > 0,
                    modifier = Modifier.weight(1f),
                ) { Text("Guardar") }
            }
        }
    }
}

/** Alta/edición de plan de cuotas, con acción "Avanzar cuota". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallmentSheet(
    existing: InstallmentPlanEntity?,
    onSave: (displayName: String, principal: Double, totalInstallments: Int, installmentAmount: Double, startDateIso: String) -> Unit,
    onAdvance: (planId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(existing?.displayName ?: "") }
    var principal by remember { mutableStateOf(existing?.principalMxn?.toPlainString() ?: "") }
    var total by remember { mutableStateOf(existing?.totalInstallments?.toString() ?: "") }
    var amount by remember { mutableStateOf(existing?.installmentAmountMxn?.toPlainString() ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetMaxWidth = 640.dp) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                if (existing == null) "Nuevo plan a meses" else "Plan a meses",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Nombre (\"TV Mercado Libre MSI\")") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = principal, onValueChange = { principal = it.moneyInput() },
                label = { Text("Monto financiado (MXN)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = total, onValueChange = { total = it.filter { c -> c.isDigit() } },
                    label = { Text("Cuotas") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it.moneyInput() },
                    label = { Text("Monto por cuota") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }

            if (existing != null) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "Va en la cuota ${existing.currentInstallment} de ${existing.totalInstallments}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onAdvance(existing.id) },
                    enabled = existing.currentInstallment < existing.totalInstallments,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Registrar cuota pagada (avanzar)") }
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                Button(
                    onClick = {
                        onSave(
                            name.trim(),
                            principal.toDoubleOrNull() ?: 0.0,
                            total.toIntOrNull() ?: 0,
                            amount.toDoubleOrNull() ?: 0.0,
                            existing?.startDate ?: java.time.LocalDate.now().toString(),
                        )
                    },
                    enabled = name.isNotBlank() && (total.toIntOrNull() ?: 0) > 0 &&
                        (amount.toDoubleOrNull() ?: 0.0) > 0,
                    modifier = Modifier.weight(1f),
                ) { Text("Guardar") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberPicker(
    label: String,
    members: List<MemberEntity>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = members.firstOrNull { it.id == selectedId }?.displayName ?: ""
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            members.forEach { m ->
                DropdownMenuItem(
                    text = { Text(m.displayName) },
                    onClick = { onSelect(m.id); expanded = false },
                )
            }
        }
    }
}

/** "250" en vez de "250.0" para precargar campos de monto. */
private fun Double.toPlainString(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString()

/** Sanea input de dinero: solo dígitos y un punto. */
private fun String.moneyInput(): String {
    val s = filter { it.isDigit() || it == '.' }
    return if (s.count { it == '.' } <= 1) s else s.dropLast(1)
}
