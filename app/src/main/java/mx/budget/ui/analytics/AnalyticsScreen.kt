package mx.budget.ui.analytics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.budget.data.local.entity.QuincenaEntity
import mx.budget.data.local.result.QuincenaSnapshot
import mx.budget.data.local.result.SpendByCategory
import mx.budget.ui.theme.financeColors
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.min

/**
 * Pantalla Analíticas — hub dinámico de widgets (rediseño jul-2026).
 *
 * Deja de ser un "reporte plano" de secciones: cada bloque es una tarjeta
 * (widget) con su propia visualización — resumen inteligente arriba, dona de
 * distribución del gasto, flujo ingreso/gasto, tendencia, presupuesto por
 * categoría (expandible), top conceptos, deuda e intereses. El asistente LLM
 * vive en el FAB "Preguntar" (AiChatSheet).
 *
 * Principios del brief: motion expresivo con springs en todo llenado/aparición,
 * redundancia no-cromática (etiquetas + % junto a cada color), resiliencia a
 * fontScale 1.3 + bold (sin anchos fijos de texto, maxLines generosos, wraps).
 */
@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel,
    onBack: () -> Unit,
    onOpenLedger: (() -> Unit)? = null,
    aiViewModel: mx.budget.ai.AiAssistantViewModel? = null,
) {
    val money = remember { NumberFormat.getCurrencyInstance(Locale("es", "MX")) }
    var chatOpen by remember { mutableStateOf(false) }

    val quincena by viewModel.activeQuincena.collectAsState()
    val byCategory by viewModel.spendByCategory.collectAsState()
    val trend by viewModel.trend.collectAsState()
    val topConcepts by viewModel.topConcepts.collectAsState()
    val debt by viewModel.debtConcentration.collectAsState()
    val interest by viewModel.interestByWallet.collectAsState()
    val totalSavings by viewModel.totalSavings.collectAsState()
    val totalCommitment by viewModel.totalCommitment.collectAsState()
    val totalReceivable by viewModel.totalReceivable.collectAsState()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
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

            // ── Widget: resumen inteligente ───────────────────────────────────
            item {
                SmartSummaryCard(
                    quincena = quincena,
                    byCategory = byCategory,
                    money = money,
                    onAsk = if (aiViewModel != null) ({ chatOpen = true }) else null,
                )
            }

            // ── Widget: KPIs de hoja de balance ───────────────────────────────
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiCard("Ahorro", money.format(totalSavings), Modifier.weight(1f))
                    KpiCard("Por cobrar", money.format(totalReceivable), Modifier.weight(1f))
                    KpiCard("MSI pendiente", money.format(totalCommitment), Modifier.weight(1f))
                }
            }

            // ── Widget: salud del presupuesto (medidor semicircular) ──────────
            item {
                val totalProjected = byCategory.sumOf { it.projected }
                val totalActual = byCategory.sumOf { it.actual }
                if (totalProjected > 0) {
                    WidgetCard(title = "Salud del presupuesto") {
                        BudgetGauge(
                            totalActual = totalActual,
                            totalProjected = totalProjected,
                            money = money,
                        )
                    }
                }
            }

            // ── Widget: distribución del gasto (dona) ─────────────────────────
            item {
                WidgetCard(title = "Distribución del gasto") {
                    val spent = byCategory.filter { it.actual > 0 }.sortedByDescending { it.actual }
                    if (spent.isEmpty()) EmptyHint("Sin gastos en la quincena activa.")
                    else SpendDonut(rows = spent, money = money)
                }
            }

            // ── Widget: flujo de la quincena (ingreso vs gasto) ───────────────
            quincena?.let { q ->
                item {
                    WidgetCard(title = "Flujo de la quincena") {
                        CashflowBars(
                            quincena = q,
                            actualExpenses = maxOf(q.actualExpensesMxn, byCategory.sumOf { it.actual }),
                            money = money,
                        )
                    }
                }
            }

            // ── Widget: ingreso vs gasto histórico (líneas) ───────────────────
            item {
                WidgetCard(title = "Ingreso vs gasto") {
                    if (trend.isEmpty()) EmptyHint("Aún no hay quincenas cerradas.")
                    else IncomeExpenseLineChart(trend = trend, money = money)
                }
            }

            // ── Widget: tendencia de gasto (barras) ───────────────────────────
            item {
                WidgetCard(title = "Tendencia de gasto") {
                    if (trend.isEmpty()) EmptyHint("Aún no hay quincenas cerradas.")
                    else TrendChart(trend = trend, money = money)
                }
            }

            // ── Widget: presupuesto vs gasto (expandible) ─────────────────────
            item {
                var expanded by rememberSaveable { mutableStateOf(false) }
                WidgetCard(title = "Presupuesto vs gasto") {
                    if (byCategory.isEmpty()) {
                        EmptyHint("Sin presupuesto en la quincena activa.")
                    } else {
                        val visible = if (expanded) byCategory else byCategory.take(6)
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            visible.forEach { row ->
                                CategoryBudgetBar(
                                    name = row.categoryName,
                                    projected = row.projected,
                                    actual = row.actual,
                                    pctExec = row.pctExec,
                                    money = money,
                                )
                            }
                            if (byCategory.size > 6) {
                                TextButton(onClick = { expanded = !expanded }) {
                                    Text(
                                        if (expanded) "Ver menos"
                                        else "Ver las ${byCategory.size} categorías",
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Widget: top conceptos ─────────────────────────────────────────
            item {
                WidgetCard(title = "Top conceptos de la quincena") {
                    if (topConcepts.isEmpty()) EmptyHint("Sin datos todavía.")
                    else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        topConcepts.forEach { c ->
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
                }
            }

            // ── Widget: deuda revolvente ──────────────────────────────────────
            item {
                WidgetCard(title = "Deuda revolvente por tarjeta") {
                    if (debt.isEmpty()) EmptyHint("Sin tarjetas activas con deuda.")
                    else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        debt.forEach { w ->
                            UtilizationBar(
                                name = w.displayName,
                                balance = w.balance,
                                utilizationPct = w.utilizationPct,
                                money = money,
                            )
                        }
                    }
                }
            }

            // ── Widget: intereses pagados ─────────────────────────────────────
            item {
                WidgetCard(title = "Intereses pagados (90 días)") {
                    if (interest.isEmpty()) EmptyHint("Sin intereses detectados — buena señal.")
                    else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        interest.forEach { w ->
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
        }

        // FAB del asistente — chat determinista + LLM si hay.
        if (aiViewModel != null) {
            androidx.compose.material3.ExtendedFloatingActionButton(
                onClick = { chatOpen = true },
                icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
                text = { Text("Preguntar") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
            )
        }
    }

    if (chatOpen && aiViewModel != null) {
        AiChatSheet(viewModel = aiViewModel, onDismiss = { chatOpen = false })
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Contenedor de widget
// ─────────────────────────────────────────────────────────────────────────────

/** Tarjeta contenedora de cada widget del hub: título + contenido en capa tonal. */
@Composable
private fun WidgetCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
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
                maxLines = 2,
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

// ─────────────────────────────────────────────────────────────────────────────
// Widget: resumen inteligente
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Resumen "IA" de la quincena. El texto se compone DETERMINISTA desde los datos
 * (misma filosofía que el dispatcher: el LLM nunca redacta cifras); el botón
 * abre el chat para preguntar libre.
 */
@Composable
private fun SmartSummaryCard(
    quincena: QuincenaEntity?,
    byCategory: List<SpendByCategory>,
    money: NumberFormat,
    onAsk: (() -> Unit)?,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "RESUMEN INTELIGENTE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                buildSmartSummary(quincena, byCategory, money),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (onAsk != null) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onAsk) {
                    Text(
                        "Pregúntale al asistente →",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

private fun buildSmartSummary(
    quincena: QuincenaEntity?,
    byCategory: List<SpendByCategory>,
    money: NumberFormat,
): String {
    if (quincena == null) return "Sin quincena activa."
    val sb = StringBuilder()

    // El actual de la quincena es un campo desnormalizado que puede ir detrás
    // del agregado real por categoría — usa el mayor de los dos.
    val actualExpenses = maxOf(quincena.actualExpensesMxn, byCategory.sumOf { it.actual })
    val available = quincena.actualIncomeMxn - actualExpenses
    sb.append(
        "Llevas ${money.format(actualExpenses)} gastados y " +
            "${money.format(quincena.actualIncomeMxn)} de ingreso — " +
            if (available >= 0) "disponible ${money.format(available)}."
            else "déficit de ${money.format(-available)}."
    )

    if (quincena.projectedExpensesMxn > 0) {
        val pct = (actualExpenses / quincena.projectedExpensesMxn * 100).toInt()
        sb.append(" Vas al $pct % del gasto proyectado.")
    }

    val top = byCategory.filter { it.actual > 0 }.maxByOrNull { it.actual }
    if (top != null) {
        sb.append(" La categoría más pesada es ${top.categoryName} (${money.format(top.actual)}).")
    }

    val over = byCategory.count { it.projected > 0 && it.actual > it.projected }
    if (over > 0) {
        sb.append(
            if (over == 1) " Hay 1 categoría excedida de presupuesto."
            else " Hay $over categorías excedidas de presupuesto."
        )
    }
    return sb.toString()
}

// ─────────────────────────────────────────────────────────────────────────────
// Widget: dona de distribución del gasto
// ─────────────────────────────────────────────────────────────────────────────

private const val DONUT_SLICES = 5

/**
 * Dona de distribución del gasto por categoría (top 5 + "Otros"), en Compose
 * puro (Canvas, sin lib de charts). El barrido se anima con spring y cada
 * rebanada lleva leyenda con nombre + monto + % (redundancia no-cromática).
 */
@Composable
private fun SpendDonut(rows: List<SpendByCategory>, money: NumberFormat) {
    val total = rows.sumOf { it.actual }
    if (total <= 0) {
        EmptyHint("Sin gastos en la quincena activa.")
        return
    }

    val top = rows.take(DONUT_SLICES)
    val othersTotal = rows.drop(DONUT_SLICES).sumOf { it.actual }
    val slices = buildList {
        top.forEach { add(it.categoryName to it.actual) }
        if (othersTotal > 0) add("Otros" to othersTotal)
    }

    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.financeColors.warning,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.financeColors.income,
        MaterialTheme.colorScheme.outline,
    )

    // Barrido global 0→1 con spring cuando cambian los datos.
    val sweep by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 120f),
        label = "donutSweep",
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(190.dp)) {
                val stroke = Stroke(width = 26.dp.toPx(), cap = StrokeCap.Butt)
                val inset = stroke.width / 2
                val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
                var startAngle = -90f
                slices.forEachIndexed { i, (_, value) ->
                    val fullSweep = (value / total * 360.0).toFloat()
                    // Gap visual de 2° entre rebanadas (si hay más de una).
                    val gap = if (slices.size > 1) 2f else 0f
                    drawArc(
                        color = palette[i % palette.size],
                        startAngle = startAngle,
                        sweepAngle = ((fullSweep - gap) * sweep).coerceAtLeast(0f),
                        useCenter = false,
                        style = stroke,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                    )
                    startAngle += fullSweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Total",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    money.format(total),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            slices.forEachIndexed { i, (name, value) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(palette[i % palette.size]),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                    )
                    Text(
                        "${money.format(value)} · ${(value / total * 100).toInt()} %",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Widget: flujo ingreso vs gasto de la quincena
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CashflowBars(quincena: QuincenaEntity, actualExpenses: Double, money: NumberFormat) {
    val maxRef = maxOf(quincena.actualIncomeMxn, actualExpenses, 1.0)
    val available = quincena.actualIncomeMxn - actualExpenses
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LabeledBar(
            label = "Ingreso",
            amountText = "+${money.format(quincena.actualIncomeMxn)}",
            fraction = (quincena.actualIncomeMxn / maxRef).toFloat(),
            color = MaterialTheme.financeColors.income,
        )
        LabeledBar(
            label = "Gasto",
            amountText = "−${money.format(actualExpenses)}",
            fraction = (actualExpenses / maxRef).toFloat(),
            color = MaterialTheme.financeColors.expense,
        )
        Text(
            if (available >= 0) "Disponible: ${money.format(available)}"
            else "Déficit: ${money.format(-available)}",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (available >= 0) MaterialTheme.financeColors.income
            else MaterialTheme.financeColors.expense,
        )
    }
}

@Composable
private fun LabeledBar(
    label: String,
    amountText: String,
    fraction: Float,
    color: Color,
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "cashflowFill",
    )
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                amountText,
                style = MaterialTheme.typography.bodyMedium,
                color = color,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(5.dp))
                    .background(color),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Widget: medidor semicircular de salud del presupuesto
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Gauge de arco (180°) que muestra el % global de presupuesto ejecutado en la
 * quincena. Compose puro (Canvas): arco de fondo + arco de avance con barrido
 * animado (spring). El color escala por umbral (primary → warning → expense).
 */
@Composable
private fun BudgetGauge(totalActual: Double, totalProjected: Double, money: NumberFormat) {
    val pct = if (totalProjected > 0) totalActual / totalProjected else 0.0
    val displayPct = (pct * 100).toInt()
    val animated by animateFloatAsState(
        targetValue = pct.coerceIn(0.0, 1.0).toFloat(),
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 120f),
        label = "gaugeSweep",
    )
    val arcColor = when {
        pct > 1.0 -> MaterialTheme.financeColors.expense
        pct >= 0.8 -> MaterialTheme.financeColors.warning
        else -> MaterialTheme.colorScheme.primary
    }
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.BottomCenter) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp),
            ) {
                val strokeW = 22.dp.toPx()
                val diameter = min(size.width - strokeW, (size.height - strokeW) * 2)
                val topLeft = Offset(
                    x = (size.width - diameter) / 2f,
                    y = size.height - diameter / 2f - strokeW / 2f,
                )
                val arcSize = Size(diameter, diameter)
                val stroke = Stroke(width = strokeW, cap = StrokeCap.Round)
                // Arco de fondo (semicírculo superior: 180° → 360°).
                drawArc(trackColor, 180f, 180f, false, topLeft = topLeft, size = arcSize, style = stroke)
                // Arco de avance.
                drawArc(arcColor, 180f, 180f * animated, false, topLeft = topLeft, size = arcSize, style = stroke)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                Text(
                    "$displayPct %",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "ejecutado",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "${money.format(totalActual)} de ${money.format(totalProjected)} presupuestados",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Widget: gráfico de líneas ingreso vs gasto (histórico)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Doble serie de líneas (ingreso + gasto) sobre las quincenas cerradas, en
 * Compose puro (Canvas + Path). Área tenue bajo la línea de gasto, puntos en
 * cada vértice y crecimiento animado desde la base. La brecha entre ambas
 * líneas es el ahorro/déficit — legible de un vistazo, más rico que las barras.
 */
@Composable
private fun IncomeExpenseLineChart(trend: List<QuincenaSnapshot>, money: NumberFormat) {
    val maxV = trend.maxOf { maxOf(it.actualIncomeMxn, it.actualExpensesMxn) }.coerceAtLeast(1.0)
    val incomeColor = MaterialTheme.financeColors.income
    val expenseColor = MaterialTheme.financeColors.expense
    val gridColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 120f),
        label = "lineGrow",
    )

    Column {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(150.dp),
        ) {
            val n = trend.size
            if (n == 0) return@Canvas
            val padY = 8.dp.toPx()
            val h = size.height - padY * 2
            val stepX = if (n > 1) size.width / (n - 1) else 0f
            fun px(i: Int): Float = if (n > 1) i * stepX else size.width / 2f
            fun py(v: Double): Float {
                val finalY = padY + h - (v / maxV * h).toFloat()
                val baseline = padY + h
                return baseline - (baseline - finalY) * progress
            }

            // Línea base.
            drawLine(gridColor, Offset(0f, padY + h), Offset(size.width, padY + h), strokeWidth = 2.dp.toPx())

            // Área tenue bajo el gasto.
            val area = Path().apply {
                moveTo(px(0), padY + h)
                trend.indices.forEach { lineTo(px(it), py(trend[it].actualExpensesMxn)) }
                lineTo(px(n - 1), padY + h)
                close()
            }
            drawPath(area, expenseColor.copy(alpha = 0.12f))

            // Líneas.
            fun seriesPath(value: (QuincenaSnapshot) -> Double): Path = Path().apply {
                trend.indices.forEach { i ->
                    val p = Offset(px(i), py(value(trend[i])))
                    if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                }
            }
            drawPath(seriesPath { it.actualExpensesMxn }, expenseColor, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            drawPath(seriesPath { it.actualIncomeMxn }, incomeColor, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))

            // Puntos en cada vértice.
            trend.indices.forEach { i ->
                drawCircle(expenseColor, 4.dp.toPx(), Offset(px(i), py(trend[i].actualExpensesMxn)))
                drawCircle(incomeColor, 4.dp.toPx(), Offset(px(i), py(trend[i].actualIncomeMxn)))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
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
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            LegendDot(incomeColor, "Ingreso")
            LegendDot(expenseColor, "Gasto")
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Componentes heredados (barras y tendencia)
// ─────────────────────────────────────────────────────────────────────────────

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
    Column {
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
