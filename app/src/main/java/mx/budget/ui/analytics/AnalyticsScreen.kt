package mx.budget.ui.analytics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.budget.data.local.result.QuincenaSnapshot
import mx.budget.ui.theme.financeColors
import java.text.NumberFormat
import java.util.Locale

/**
 * Pantalla Analíticas (MVP Fase 3) — reemplaza el placeholder.
 *
 * Secciones: KPIs de hoja de balance, presupuesto-vs-gasto por categoría
 * (barras horizontales, patrón del Dashboard), tendencia de quincenas cerradas
 * (barras Compose puras, sin lib de charts), top conceptos, concentración de
 * deuda revolvente e intereses pagados. Redundancia no-cromática en todos los
 * semánticos (etiquetas + %), animación spring en el llenado de barras.
 */
@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel,
    onBack: () -> Unit,
    onOpenLedger: (() -> Unit)? = null,
) {
    val money = remember { NumberFormat.getCurrencyInstance(Locale("es", "MX")) }

    val quincena by viewModel.activeQuincena.collectAsState()
    val byCategory by viewModel.spendByCategory.collectAsState()
    val trend by viewModel.trend.collectAsState()
    val topConcepts by viewModel.topConcepts.collectAsState()
    val debt by viewModel.debtConcentration.collectAsState()
    val interest by viewModel.interestByWallet.collectAsState()
    val totalSavings by viewModel.totalSavings.collectAsState()
    val totalCommitment by viewModel.totalCommitment.collectAsState()
    val totalReceivable by viewModel.totalReceivable.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 24.dp, end = 24.dp, top = 12.dp, bottom = 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "Analíticas",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    quincena?.let {
                        Text(
                            it.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                onOpenLedger?.let {
                    IconButton(onClick = it) {
                        Icon(
                            Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = "Libro Mayor",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        // ── KPIs de hoja de balance ───────────────────────────────────────────
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiCard("Ahorro", money.format(totalSavings), Modifier.weight(1f))
                KpiCard("Por cobrar", money.format(totalReceivable), Modifier.weight(1f))
                KpiCard("MSI pendiente", money.format(totalCommitment), Modifier.weight(1f))
            }
        }

        // ── Presupuesto vs gasto por categoría ────────────────────────────────
        item { SectionTitle("Presupuesto vs gasto por categoría") }
        if (byCategory.isEmpty()) {
            item { EmptyHint("Sin gastos en la quincena activa.") }
        } else {
            items(byCategory.size) { i ->
                val row = byCategory[i]
                CategoryBudgetBar(
                    name = row.categoryName,
                    projected = row.projected,
                    actual = row.actual,
                    pctExec = row.pctExec,
                    money = money,
                )
            }
        }

        // ── Tendencia quincenal ───────────────────────────────────────────────
        item { SectionTitle("Tendencia (quincenas cerradas)") }
        item {
            if (trend.isEmpty()) EmptyHint("Aún no hay quincenas cerradas.")
            else TrendChart(trend = trend, money = money)
        }

        // ── Top conceptos ─────────────────────────────────────────────────────
        item { SectionTitle("Top conceptos de la quincena") }
        if (topConcepts.isEmpty()) {
            item { EmptyHint("Sin datos todavía.") }
        } else {
            items(topConcepts.size) { i ->
                val c = topConcepts[i]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            c.concept,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                        )
                        Text(
                            "${c.timesCount} ${if (c.timesCount == 1) "movimiento" else "movimientos"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        money.format(c.totalMxn),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.financeColors.expense,
                        maxLines = 1,
                    )
                }
            }
        }

        // ── Concentración de deuda ────────────────────────────────────────────
        item { SectionTitle("Deuda revolvente por tarjeta") }
        if (debt.isEmpty()) {
            item { EmptyHint("Sin tarjetas activas con deuda.") }
        } else {
            items(debt.size) { i ->
                val w = debt[i]
                UtilizationBar(
                    name = w.displayName,
                    balance = w.balance,
                    utilizationPct = w.utilizationPct,
                    money = money,
                )
            }
        }

        // ── Intereses pagados ─────────────────────────────────────────────────
        item { SectionTitle("Intereses pagados (90 días)") }
        if (interest.isEmpty()) {
            item { EmptyHint("Sin intereses detectados — buena señal.") }
        } else {
            items(interest.size) { i ->
                val w = interest[i]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(w.paymentMethodName, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "%.1f %% de lo pagado".format(w.interestPct),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        money.format(w.interestPaidMxn),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.financeColors.warning,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Componentes
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun KpiCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

/** Barra horizontal presupuesto-vs-gasto con llenado animado (spring). */
@Composable
private fun CategoryBudgetBar(
    name: String,
    projected: Double,
    actual: Double,
    pctExec: Int,
    money: NumberFormat,
) {
    val over = projected > 0 && actual > projected
    val fillColor = when {
        over -> MaterialTheme.financeColors.expense
        pctExec >= 80 -> MaterialTheme.financeColors.warning
        else -> MaterialTheme.colorScheme.primary
    }
    val targetFraction = when {
        projected > 0 -> (actual / projected).coerceIn(0.0, 1.0).toFloat()
        actual > 0 -> 1f
        else -> 0f
    }
    val fraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "budgetFill",
    )

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 2,
            )
            Text(
                if (projected > 0) "${money.format(actual)} / ${money.format(projected)}"
                else money.format(actual),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(5.dp))
                    .background(fillColor),
            )
        }
        if (projected > 0) {
            Spacer(Modifier.height(2.dp))
            Text(
                if (over) "Excede el presupuesto · $pctExec %" else "$pctExec % del presupuesto",
                style = MaterialTheme.typography.labelSmall,
                color = if (over) MaterialTheme.financeColors.expense
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Barras de tendencia (gasto real por quincena cerrada) en Compose puro. */
@Composable
private fun TrendChart(trend: List<QuincenaSnapshot>, money: NumberFormat) {
    val maxExpense = trend.maxOf { it.actualExpensesMxn }.coerceAtLeast(1.0)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                trend.forEach { q ->
                    val target = (q.actualExpensesMxn / maxExpense).toFloat().coerceIn(0.02f, 1f)
                    val h by animateFloatAsState(
                        targetValue = target,
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
                        label = "trendBar",
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(h)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(
                                if (q.actualExpensesMxn > q.actualIncomeMxn)
                                    MaterialTheme.financeColors.expense
                                else MaterialTheme.colorScheme.primary
                            ),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                trend.forEach { q ->
                    Text(
                        q.label.substringBefore(" "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            val last = trend.last()
            Text(
                "Última cerrada: gasto ${money.format(last.actualExpensesMxn)} · ingreso ${money.format(last.actualIncomeMxn)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Barra de utilización de crédito por tarjeta (módulo E). */
@Composable
private fun UtilizationBar(
    name: String,
    balance: Double,
    utilizationPct: Double?,
    money: NumberFormat,
) {
    val pct = utilizationPct?.coerceIn(0.0, 100.0)
    val target = ((pct ?: 0.0) / 100.0).toFloat()
    val fraction by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "utilFill",
    )
    val color = when {
        pct == null -> MaterialTheme.colorScheme.primary
        pct >= 80 -> MaterialTheme.financeColors.expense
        pct >= 50 -> MaterialTheme.financeColors.warning
        else -> MaterialTheme.colorScheme.primary
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 2)
            Text(
                money.format(balance) + (pct?.let { " · ${it.toInt()} %" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (pct != null) {
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(5.dp))
                        .background(color),
                )
            }
        }
    }
}
