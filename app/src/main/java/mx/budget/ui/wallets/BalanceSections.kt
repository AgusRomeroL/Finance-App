package mx.budget.ui.wallets

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import mx.budget.data.local.entity.InstallmentPlanEntity
import mx.budget.data.local.entity.LoanEntity
import mx.budget.data.local.entity.MemberEntity
import mx.budget.data.local.entity.PaymentMethodEntity
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
    walletNames: Map<String, String>,
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
                    loanScheduleSummary(l, money)?.let { summary ->
                        Text(
                            summary,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2,
                        )
                    }
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
        InstallmentPlanCard(
            plan = p,
            walletNames = walletNames,
            money = money,
            onClick = { onEditInstallment(p) },
        )
    }
    item(key = "add_installment") { AddRow("Nuevo plan a meses") { onEditInstallment(null) } }
}

/**
 * Tarjeta rica de un plan a meses / MSI. Muestra las tres cosas que interesan:
 *  (1) la tarjeta/cuenta con la que se financió la compra (`payment_method_id`),
 *  (2) las condiciones de compra (cuotas, monto por cuota, total, interés y avance),
 *  (3) cómo se va a pagar la tarjeta (cargo mensual desde `funding_payment_method_id`).
 *
 * Nota de esquema: `installment_plan` guarda `payment_method_id` (la tarjeta con la
 * que se hizo la compra) y `funding_payment_method_id` (la cuenta desde la que se
 * liquida el cargo mensual, v16→v17). "Tarjeta usada" usa el primero; "cómo se
 * paga" el segundo, cayendo a un texto genérico si aún no se eligió.
 */
@Composable
private fun InstallmentPlanCard(
    plan: InstallmentPlanEntity,
    walletNames: Map<String, String>,
    money: NumberFormat,
    onClick: () -> Unit,
) {
    val fc = MaterialTheme.financeColors
    val cardName = plan.paymentMethodId?.let { walletNames[it] }
    val fundingName = plan.fundingPaymentMethodId?.let { walletNames[it] }
    val paid = plan.currentInstallment.coerceIn(0, plan.totalInstallments)
    val total = plan.totalInstallments.coerceAtLeast(1)
    val remaining = (plan.totalInstallments - paid).coerceAtLeast(0) * plan.installmentAmountMxn
    val hasInterest = plan.interestRateApr != null && plan.interestRateApr != 0.0
    val interestLabel = if (hasInterest) "APR ${plan.interestRateApr!!.asPercent()}" else "Sin intereses (MSI)"

    val target = (paid.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val fraction by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "planFill",
    )

    BalanceRowCard(onClick = onClick) {
        Column(Modifier.fillMaxWidth()) {
            // Título + saldo restante ────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    plan.displayName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                )
                Spacer(Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        money.format(remaining),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = fc.expense,
                        maxLines = 1,
                    )
                    Text(
                        "restante",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // (1) Tarjeta usada ───────────────────────────────────────────────────
            Spacer(Modifier.height(12.dp))
            PlanInfoLine(
                icon = Icons.Filled.CreditCard,
                label = "Tarjeta usada",
                value = cardName ?: "Sin tarjeta vinculada",
            )

            // (2) Condiciones de compra: avance + interés ─────────────────────────
            Spacer(Modifier.height(12.dp))
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
                        .background(fc.expense),
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                val remainingCount = (plan.totalInstallments - paid).coerceAtLeast(0)
                Text(
                    if (remainingCount > 0) "Pago $paid de ${plan.totalInstallments} · faltan $remainingCount"
                    else "Pago $paid de ${plan.totalInstallments} · liquidado",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    interestLabel,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = if (hasInterest) fc.warning else fc.income,
                )
            }
            Spacer(Modifier.height(4.dp))
            val endDate = mx.budget.data.installments.InstallmentSchedule.parseIso(plan.startDate)
                ?.let { mx.budget.data.installments.InstallmentSchedule.estimatedEndDate(it, plan.totalInstallments, null) }
            Text(
                buildString {
                    append("${money.format(plan.installmentAmountMxn)} al mes × ${plan.totalInstallments} · total ${money.format(plan.principalMxn)}")
                    if (endDate != null && paid < plan.totalInstallments) {
                        append(" · termina ~${endDate.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale("es", "MX"))} ${endDate.year}")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )

            // (3) Cómo se paga la tarjeta ─────────────────────────────────────────
            Spacer(Modifier.height(12.dp))
            PlanInfoLine(
                icon = Icons.Filled.Payments,
                label = "Cómo se paga",
                value = if (fundingName != null)
                    "Cargo mensual de ${money.format(plan.installmentAmountMxn)} a $fundingName"
                else
                    "Se liquida desde la cuenta que elijas",
            )
        }
    }
}

/** Renglón etiquetado con icono para las secciones de un plan (tarjeta / pago). */
@Composable
private fun PlanInfoLine(icon: ImageVector, label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
            )
        }
    }
}

/** Formatea una tasa anual (guardada como porcentaje, p. ej. 24.0) → "24%". */
private fun Double.asPercent(): String {
    val s = if (this % 1.0 == 0.0) toLong().toString() else "%.1f".format(this)
    return "$s%"
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LoanSheet(
    existing: LoanEntity?,
    members: List<MemberEntity>,
    onSave: (
        debtorMemberId: String,
        principal: Double,
        notes: String?,
        paymentCount: Int?,
        paymentFrequency: String?,
        paymentAmountMxn: Double?,
        scheduleStartDate: String?,
    ) -> Unit,
    onPayment: (loanId: String, amount: Double) -> Unit,
    onDelete: (LoanEntity) -> Unit,
    onCreateDebtor: (name: String, select: (String) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var debtorId by remember { mutableStateOf(existing?.debtorMemberId) }
    var principal by remember { mutableStateOf(existing?.principalMxn?.toPlainString() ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var payment by remember { mutableStateOf("") }
    val money = remember { NumberFormat.getCurrencyInstance(Locale("es", "MX")) }

    // ── Esquema de pago (v16) ────────────────────────────────────────────────
    var frequency by remember { mutableStateOf(existing?.paymentFrequency ?: "BIWEEKLY") }
    var count by remember { mutableStateOf(existing?.paymentCount?.toString() ?: "") }
    var perPayment by remember { mutableStateOf(existing?.paymentAmountMxn?.toPlainString() ?: "") }
    var amountEdited by remember { mutableStateOf(existing?.paymentAmountMxn != null) }
    var startDate by remember { mutableStateOf(existing?.scheduleStartDate ?: java.time.LocalDate.now().toString()) }
    var showDatePicker by remember { mutableStateOf(false) }

    val isLump = frequency == "LUMP_SUM"
    // Auto-sugerencia del monto por pago = principal / nº pagos (mientras el
    // usuario no lo edite a mano y no sea pago único).
    LaunchedEffect(principal, count, frequency) {
        if (!amountEdited && !isLump) {
            val p = principal.toDoubleOrNull()
            val n = count.toIntOrNull()
            if (p != null && n != null && n > 0) {
                val each = p / n
                perPayment = if (each % 1.0 == 0.0) each.toLong().toString() else "%.2f".format(each)
            }
        }
    }

    if (showDatePicker) {
        val initMillis = try {
            java.time.LocalDate.parse(startDate)
        } catch (e: Exception) {
            java.time.LocalDate.now()
        }.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        startDate = java.time.Instant.ofEpochMilli(ms)
                            .atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showDatePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") } },
        ) { DatePicker(state = pickerState) }
    }

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
            DebtorPicker(
                members = members,
                selectedId = debtorId,
                onSelect = { debtorId = it },
                onCreateDebtor = onCreateDebtor,
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

            // ── Esquema de pago ──────────────────────────────────────────────
            Spacer(Modifier.height(16.dp))
            Text(
                "Esquema de pago",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FrequencyChip("Semanal", "WEEKLY", frequency) { frequency = it }
                FrequencyChip("Quincenal", "BIWEEKLY", frequency) { frequency = it }
                FrequencyChip("Mensual", "MONTHLY", frequency) { frequency = it }
                FrequencyChip("Pago único", "LUMP_SUM", frequency) { frequency = it }
            }
            if (!isLump) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = count,
                        onValueChange = { count = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("N.º de pagos") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = perPayment,
                        onValueChange = { perPayment = it.moneyInput(); amountEdited = true },
                        label = { Text("Monto por pago") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showDatePicker = true }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.CalendarMonth, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        if (isLump) "Fecha de pago" else "Primer pago",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        formatShortDate(startDate),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

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
                    onClick = {
                        val principalValue = principal.toDoubleOrNull() ?: 0.0
                        val pc: Int? = if (isLump) 1 else count.toIntOrNull()
                        val pa: Double? = if (isLump) principalValue else perPayment.toDoubleOrNull()
                        onSave(
                            debtorId!!,
                            principalValue,
                            notes.ifBlank { null },
                            pc,
                            frequency,
                            pa,
                            startDate,
                        )
                    },
                    enabled = debtorId != null && (principal.toDoubleOrNull() ?: 0.0) > 0,
                    modifier = Modifier.weight(1f),
                ) { Text("Guardar") }
            }
        }
    }
}

/** Un chip de frecuencia del esquema de pago; resalta el seleccionado. */
@Composable
private fun FrequencyChip(
    label: String,
    value: String,
    selected: String,
    onSelect: (String) -> Unit,
) {
    FilterChip(
        selected = selected == value,
        onClick = { onSelect(value) },
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    )
}

/**
 * Selector de deudor que además permite **crear una persona en el momento**
 * (rol EXTERNAL_DEBTOR) reutilizando el camino del ViewModel. El deudor recién
 * creado queda seleccionado vía [onCreateDebtor]'s callback.
 */
@Composable
private fun DebtorPicker(
    members: List<MemberEntity>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onCreateDebtor: (name: String, select: (String) -> Unit) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth()) {
        MemberPicker(
            label = "Deudor",
            members = members,
            selectedId = selectedId,
            onSelect = onSelect,
        )
        Spacer(Modifier.height(4.dp))
        if (!creating) {
            TextButton(
                onClick = { creating = true },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Crear deudor…")
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Nombre del deudor") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        onCreateDebtor(newName) { id -> onSelect(id) }
                        newName = ""
                        creating = false
                    },
                    enabled = newName.isNotBlank(),
                ) { Text("Crear") }
            }
        }
    }
}

/** Resumen legible del esquema de pago de un préstamo, o null si no lo tiene. */
private fun loanScheduleSummary(l: LoanEntity, money: NumberFormat): String? {
    val freq = l.paymentFrequency ?: return null
    val amt = l.paymentAmountMxn?.let { money.format(it) }
    val start = l.scheduleStartDate?.let { formatShortDate(it) }
    if (freq == "LUMP_SUM") {
        val head = "Pago único" + (amt?.let { " de $it" } ?: "")
        return listOfNotNull(head, start?.let { "para $it" }).joinToString(" · ")
    }
    val word = when (freq) {
        "WEEKLY" -> "semanales"
        "BIWEEKLY" -> "quincenales"
        "MONTHLY" -> "mensuales"
        else -> return null
    }
    val head = buildString {
        append(l.paymentCount?.let { "$it pagos $word" } ?: "Pagos $word")
        if (amt != null) append(" de $amt")
    }
    return listOfNotNull(head, start?.let { "desde $it" }).joinToString(" · ")
}

/** ISO "2026-07-08" → "8 jul" (es-MX, sin punto). */
private fun formatShortDate(iso: String): String = try {
    java.time.LocalDate.parse(iso)
        .format(java.time.format.DateTimeFormatter.ofPattern("d MMM", Locale("es", "MX")))
        .replace(".", "")
} catch (e: Exception) {
    iso
}

/** Alta/edición de plan de cuotas, con acción "Avanzar cuota". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallmentSheet(
    existing: InstallmentPlanEntity?,
    wallets: List<PaymentMethodEntity>,
    onSave: (displayName: String, principal: Double, totalInstallments: Int, installmentAmount: Double, startDateIso: String, paymentMethodId: String?, fundingPaymentMethodId: String?) -> Unit,
    onAdvance: (planId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(existing?.displayName ?: "") }
    var principal by remember { mutableStateOf(existing?.principalMxn?.toPlainString() ?: "") }
    var total by remember { mutableStateOf(existing?.totalInstallments?.toString() ?: "") }
    var amount by remember { mutableStateOf(existing?.installmentAmountMxn?.toPlainString() ?: "") }
    var cardWalletId by remember { mutableStateOf(existing?.paymentMethodId) }
    var fundingWalletId by remember { mutableStateOf(existing?.fundingPaymentMethodId) }

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

            Spacer(Modifier.height(10.dp))
            WalletPicker(
                label = "Tarjeta de la compra",
                wallets = wallets,
                selectedId = cardWalletId,
                onSelect = { cardWalletId = it },
            )
            Spacer(Modifier.height(10.dp))
            WalletPicker(
                label = "Se paga desde",
                wallets = wallets,
                selectedId = fundingWalletId,
                onSelect = { fundingWalletId = it },
            )

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
                            cardWalletId,
                            fundingWalletId,
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

/** Selector de wallet (cuenta/tarjeta) reutilizable, con opción "Sin definir". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WalletPicker(
    label: String,
    wallets: List<PaymentMethodEntity>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = wallets.firstOrNull { it.id == selectedId }?.displayName ?: ""
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text("Sin definir") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Sin definir") },
                onClick = { onSelect(null); expanded = false },
            )
            wallets.forEach { w ->
                DropdownMenuItem(
                    text = { Text(w.displayName) },
                    onClick = { onSelect(w.id); expanded = false },
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
