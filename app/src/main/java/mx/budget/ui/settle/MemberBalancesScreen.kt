package mx.budget.ui.settle

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.budget.data.local.entity.LoanEntity
import mx.budget.ui.theme.financeColors
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val mxn: NumberFormat = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
private fun Double.toMxn(): String = mxn.format(this)
private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("es", "MX"))
private val zone: ZoneId = ZoneId.of("America/Mexico_City")
private fun Long.toShortDate(): String =
    Instant.ofEpochMilli(this).atZone(zone).toLocalDate().format(dateFmt).replace(".", "")

/**
 * Pantalla "Cuentas entre miembros": **deudas explícitas y opt-in** en dos
 * sentidos, agrupadas por miembro.
 *
 * - **Por pagar** (el hogar le debe a un miembro que adelantó un gasto): sale de
 *   `expense.settlement_status = 'PENDING_REIMBURSEMENT'`. Acción "Marcar como
 *   pagado" → `REIMBURSED` (no mueve saldos).
 * - **Por cobrar** (un miembro le debe al hogar): sale de `loan.remaining_balance_mxn`.
 *   Acción "Abonar" (reutiliza el flujo de préstamos).
 *
 * Un mismo miembro puede tener deuda en ambos sentidos: se muestran las dos sin
 * netearlas. Redundancia no-cromática: etiquetas explícitas ("El hogar le debe" /
 * "le debe al hogar") + tono financiero (ingreso/alerta), nunca solo el color.
 * Motion expresivo (resortes) en aparición de filas y expansión de desgloses.
 */
@Composable
fun MemberBalancesScreen(
    viewModel: MemberBalancesViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(containerColor = MaterialTheme.colorScheme.surface) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .statusBarsPadding(),
        ) {
            Header(onBack = onBack)

            AnimatedVisibility(
                visible = state.isEmpty,
                enter = fadeIn(spring(stiffness = 380f)),
                exit = fadeOut(spring(stiffness = 380f)),
            ) {
                EmptyState()
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.rows, key = { it.memberId }) { row ->
                    MemberCard(
                        row = row,
                        onMarkReimbursed = viewModel::markReimbursed,
                        onLoanPayment = viewModel::applyLoanPayment,
                        modifier = Modifier.animateItem(
                            fadeInSpec = spring(stiffness = 380f),
                            fadeOutSpec = spring(stiffness = 380f),
                            placementSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack, "Volver",
                tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "CUENTAS ENTRE MIEMBROS",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.6.sp,
            )
            Text(
                "Deudas por saldar",
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light, fontSize = 26.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun MemberCard(
    row: MemberDebtRow,
    onMarkReimbursed: (String) -> Unit,
    onLoanPayment: (String, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(16.dp),
    ) {
        Text(
            row.name,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
        )
        Spacer(Modifier.height(4.dp))
        // Resumen de ambos saldos, visibles simultáneamente (sin netear).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (row.hasPayable) {
                DirectionChip(
                    modifier = Modifier.weight(1f),
                    label = "El hogar le debe",
                    amount = row.payableTotal,
                    tone = ChipTone.INCOME,
                )
            }
            if (row.hasReceivable) {
                DirectionChip(
                    modifier = Modifier.weight(1f),
                    label = "Le debe al hogar",
                    amount = row.receivableTotal,
                    tone = ChipTone.WARNING,
                )
            }
        }

        if (row.hasPayable) {
            Spacer(Modifier.height(14.dp))
            PayableSection(
                name = row.name,
                payables = row.payables,
                onMarkReimbursed = onMarkReimbursed,
            )
        }
        if (row.hasReceivable) {
            Spacer(Modifier.height(14.dp))
            ReceivableSection(
                name = row.name,
                loans = row.receivables,
                onLoanPayment = onLoanPayment,
            )
        }
    }
}

private enum class ChipTone { INCOME, WARNING }

@Composable
private fun DirectionChip(
    modifier: Modifier,
    label: String,
    amount: Double,
    tone: ChipTone,
) {
    val fc = MaterialTheme.financeColors
    val container = if (tone == ChipTone.INCOME) fc.incomeContainer else fc.warningContainer
    val onContainer = if (tone == ChipTone.INCOME) fc.onIncomeContainer else fc.onWarningContainer
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = onContainer,
            maxLines = 2,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            amount.toMxn(),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = onContainer,
            maxLines = 1,
        )
    }
}

/** "Por pagar": gastos que el miembro adelantó; el hogar decide reponerlos. */
@Composable
private fun PayableSection(
    name: String,
    payables: List<PayableExpense>,
    onMarkReimbursed: (String) -> Unit,
) {
    var expanded by rememberSaveable(name) { mutableStateOf(true) }
    SectionHeader(
        title = "El hogar le debe a $name",
        subtitle = "${payables.size} ${if (payables.size == 1) "gasto adelantado" else "gastos adelantados"}",
        expanded = expanded,
        onToggle = { expanded = !expanded },
    )
    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically(spring(dampingRatio = 0.85f, stiffness = 320f)) + fadeIn(),
        exit = shrinkVertically(spring(dampingRatio = 0.85f, stiffness = 320f)) + fadeOut(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(8.dp))
            payables.forEach { p ->
                PayableRow(p = p, onMarkReimbursed = { onMarkReimbursed(p.expenseId) })
            }
        }
    }
}

@Composable
private fun PayableRow(p: PayableExpense, onMarkReimbursed: () -> Unit) {
    val fc = MaterialTheme.financeColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    p.concept,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                )
                Text(
                    p.occurredAt.toShortDate(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                p.amount.toMxn(),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = fc.income,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onMarkReimbursed) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Marcar como pagado")
            }
        }
    }
}

/** "Por cobrar": préstamos vivos que el miembro le debe al hogar. */
@Composable
private fun ReceivableSection(
    name: String,
    loans: List<LoanEntity>,
    onLoanPayment: (String, Double) -> Unit,
) {
    var expanded by rememberSaveable("recv_$name") { mutableStateOf(true) }
    SectionHeader(
        title = "$name le debe al hogar",
        subtitle = "${loans.size} ${if (loans.size == 1) "préstamo" else "préstamos"}",
        expanded = expanded,
        onToggle = { expanded = !expanded },
    )
    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically(spring(dampingRatio = 0.85f, stiffness = 320f)) + fadeIn(),
        exit = shrinkVertically(spring(dampingRatio = 0.85f, stiffness = 320f)) + fadeOut(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(8.dp))
            loans.forEach { l ->
                LoanRow(l = l, onPayment = { amount -> onLoanPayment(l.id, amount) })
            }
        }
    }
}

@Composable
private fun LoanRow(l: LoanEntity, onPayment: (Double) -> Unit) {
    val fc = MaterialTheme.financeColors
    var payment by remember(l.id) { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Prestado ${l.principalMxn.toMxn()}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                )
                loanScheduleSummary(l)?.let { summary ->
                    Text(
                        summary,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    l.remainingBalanceMxn.toMxn(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = fc.warning,
                    maxLines = 1,
                )
                Text(
                    "pendiente",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = payment,
                onValueChange = { s -> payment = s.filter { it.isDigit() || it == '.' } },
                label = { Text("Abono recibido") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    payment.toDoubleOrNull()?.let { onPayment(it) }
                    payment = ""
                },
                enabled = (payment.toDoubleOrNull() ?: 0.0) > 0,
            ) { Text("Abonar") }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (expanded) "Ocultar" else "Mostrar",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.Balance,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Nadie se debe nada",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Cuando alguien más pague un gasto del hogar (captura → \"Pagó un tercero\") " +
                "o registres un préstamo, aparecerá aquí.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

/** Resumen legible del esquema de pago de un préstamo, o null si no lo tiene. */
private fun loanScheduleSummary(l: LoanEntity): String? {
    val freq = l.paymentFrequency ?: return l.dueAt?.let { "Vence $it" }
    val amt = l.paymentAmountMxn?.toMxn()
    if (freq == "LUMP_SUM") {
        return "Pago único" + (amt?.let { " de $it" } ?: "")
    }
    val word = when (freq) {
        "WEEKLY" -> "semanales"
        "BIWEEKLY" -> "quincenales"
        "MONTHLY" -> "mensuales"
        else -> return null
    }
    return buildString {
        append(l.paymentCount?.let { "$it pagos $word" } ?: "Pagos $word")
        if (amt != null) append(" de $amt")
    }
}
