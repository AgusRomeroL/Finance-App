package mx.budget.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.budget.data.local.entity.QuincenaEntity
import mx.budget.data.local.result.ExpenseWithDetails
import mx.budget.data.local.result.SpendByMember
import mx.budget.ui.capture.CaptureBottomSheet
import mx.budget.ui.capture.CaptureViewModel
import mx.budget.ui.theme.FinancialTone
import mx.budget.ui.theme.amountSemantic
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// Formato y utilidades
// ─────────────────────────────────────────────────────────────────────────────

private val mxnInt: NumberFormat = NumberFormat.getIntegerInstance(Locale("es", "MX"))

/** "24,380" (sin símbolo, sin centavos) — para el KPI héroe con "$" y "MXN" aparte. */
private fun Double.toGrouped(): String = mxnInt.format(this.toLong())

/** "$24,380" — para montos en línea. */
private fun Double.toMxn(): String = "$" + this.toGrouped()

private val isoDate: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private val shortMonth = SimpleMonth()

private class SimpleMonth {
    private val fmt = java.text.SimpleDateFormat("d MMM", Locale("es", "MX"))
    fun format(epochMillis: Long): String = fmt.format(Date(epochMillis))
}

private fun Long.toShortDate(): String = shortMonth.format(this)

/** Parsea ISO yyyy-MM-dd de forma segura; null si falla. */
private fun parseIso(s: String?): LocalDate? =
    runCatching { LocalDate.parse(s, isoDate) }.getOrNull()

/** Rango "16 — 30 jun" a partir de las fechas ISO de la quincena. */
private fun quincenaRange(q: QuincenaEntity?): String {
    val start = parseIso(q?.startDate) ?: return ""
    val end = parseIso(q?.endDate) ?: return ""
    val mFmt = DateTimeFormatter.ofPattern("MMM", Locale("es", "MX"))
    val endMonth = end.format(mFmt).replace(".", "")
    return "${start.dayOfMonth} — ${end.dayOfMonth} $endMonth"
}

/** Estado calculado del progreso de la quincena (día actual, fracción, días restantes). */
private data class QuincenaProgress(
    val dayIndex: Int,
    val totalDays: Int,
    val fraction: Float,
    val daysRemaining: Int
)

private fun computeProgress(q: QuincenaEntity?): QuincenaProgress {
    val start = parseIso(q?.startDate)
    val end = parseIso(q?.endDate)
    if (start == null || end == null) return QuincenaProgress(1, 15, 0f, 0)
    val total = (ChronoUnit.DAYS.between(start, end) + 1).toInt().coerceAtLeast(1)
    val today = LocalDate.now()
    val rawDay = (ChronoUnit.DAYS.between(start, today) + 1).toInt()
    val day = rawDay.coerceIn(1, total)
    val remaining = (ChronoUnit.DAYS.between(today, end)).toInt().coerceIn(0, total)
    return QuincenaProgress(day, total, day.toFloat() / total, remaining)
}

/** Ramp monocromático verde (matiz de marca) derivado del primary dinámico. */
@Composable
private fun memberColors(count: Int): List<Color> {
    val primary = MaterialTheme.colorScheme.primary
    val target = Color(0xFF8C968A) // neutral verdoso para aclarar sin salir de la familia
    return (0 until count).map { i ->
        val f = (i * 0.15f).coerceAtMost(0.62f)
        lerp(primary, target, f)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Modelo de navegación
// ─────────────────────────────────────────────────────────────────────────────

private data class NavItem(val route: String, val icon: ImageVector, val label: String)

private val navItems = listOf(
    NavItem("dashboard", Icons.Filled.Dashboard, "Inicio"),
    NavItem("ledger", Icons.AutoMirrored.Filled.ReceiptLong, "Libro"),
    NavItem("wallets", Icons.Filled.AccountBalanceWallet, "Cuentas"),
    NavItem("analytics", Icons.Filled.Insights, "Analíticas"),
    NavItem("profile", Icons.Filled.Person, "Perfil")
)

// ─────────────────────────────────────────────────────────────────────────────
// DashboardScreen — punto de entrada adaptativo
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Pantalla principal del presupuesto quincenal, rediseñada según el sistema
 * "The Architectural Ledger" (ver `ui_reference/claude_design/`).
 *
 * - **Expandido (Fold abierto, ≥ 600dp):** rail de iconos custom + Bento 62/38
 *   (salud financiera | transacciones), KPI héroe "Disponible para gastar".
 * - **Compacto (Fold cerrado, < 600dp):** bottom nav + columna única scrollable.
 *
 * Toda la jerarquía recae en capas tonales y espacio (regla No-Line); cero
 * divisores de 1px. Los colores provienen del tema (dinámico o verde fallback).
 */
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    captureViewModel: CaptureViewModel? = null,
    windowWidthDp: Dp = 360.dp,
    currentRoute: String = "dashboard",
    onNavigate: ((String) -> Unit)? = null,
    onOpenReview: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val pendingReviewCount by viewModel.pendingReviewCount.collectAsState()
    val isExpanded = windowWidthDp >= 600.dp
    var showCapture by remember { mutableStateOf(false) }

    if (showCapture) {
        CaptureBottomSheet(
            viewModel = captureViewModel,
            onDismiss = { showCapture = false }
        )
    }

    if (isExpanded) {
        ExpandedDashboard(
            state = uiState,
            currentRoute = currentRoute,
            onNavigate = onNavigate,
            onCapture = { showCapture = true },
            pendingReviewCount = pendingReviewCount,
            onOpenReview = onOpenReview
        )
    } else {
        CompactDashboard(
            state = uiState,
            currentRoute = currentRoute,
            onNavigate = onNavigate,
            onCapture = { showCapture = true },
            pendingReviewCount = pendingReviewCount,
            onOpenReview = onOpenReview
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Layout expandido (Fold interno)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ExpandedDashboard(
    state: DashboardUiState,
    currentRoute: String,
    onNavigate: ((String) -> Unit)?,
    onCapture: () -> Unit,
    pendingReviewCount: Int,
    onOpenReview: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
    ) {
        NavigationRailCustom(currentRoute = currentRoute, onNavigate = onNavigate)

        Box(modifier = Modifier.fillMaxSize()) {
            when (state) {
                is DashboardUiState.Loading -> LoadingContent()
                is DashboardUiState.Error -> ErrorContent(state.message)
                is DashboardUiState.Success -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 30.dp, top = 22.dp, end = 32.dp, bottom = 24.dp)
                    ) {
                        DashboardHeader(
                            quincena = state.quincena,
                            expanded = true,
                            pendingReviewCount = pendingReviewCount,
                            onOpenReview = onOpenReview
                        )
                        Spacer(Modifier.height(22.dp))
                        BentoPanes(state = state, modifier = Modifier.fillMaxSize())
                    }
                    // FAB extendido
                    ExtendedCaptureFab(
                        onClick = onCapture,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 32.dp, bottom = 30.dp)
                    )
                }
            }
        }
    }
}

/**
 * Bento de dos paneles con **divisor arrastrable** y proporción persistida.
 *
 * Implementado con un handle custom (`draggable`) en vez de
 * `SupportingPaneScaffold`/`PaneExpansionState` por estabilidad (el brief D3/C7
 * marca esa API como inestable). La proporción sobrevive el plegado/recreación
 * vía `rememberSaveable` (brief C13). Anclada en 62/38, arrastrable en [0.45, 0.78].
 */
@Composable
private fun BentoPanes(state: DashboardUiState.Success, modifier: Modifier = Modifier) {
    var fraction by rememberSaveable { mutableStateOf(0.62f) }
    BoxWithConstraints(modifier = modifier) {
        val totalPx = constraints.maxWidth.toFloat()
        val dragState = rememberDraggableState { delta ->
            if (totalPx > 0f) fraction = (fraction + delta / totalPx).coerceIn(0.45f, 0.78f)
        }
        Row(modifier = Modifier.fillMaxSize()) {
            MainHealthPane(
                state = state,
                modifier = Modifier.weight(fraction).fillMaxHeight()
            )
            PaneDragHandle(dragState)
            TransactionsPane(
                transactions = state.transactions,
                modifier = Modifier.weight(1f - fraction).fillMaxHeight()
            )
        }
    }
}

/** Divisor (gutter 24dp) con grabber visible; arrastrable en horizontal. */
@Composable
private fun PaneDragHandle(dragState: DraggableState) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(24.dp)
            .draggable(state = dragState, orientation = Orientation.Horizontal)
            .semantics { contentDescription = "Ajustar ancho de paneles" },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Layout compacto (Fold externo)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CompactDashboard(
    state: DashboardUiState,
    currentRoute: String,
    onNavigate: ((String) -> Unit)?,
    onCapture: () -> Unit,
    pendingReviewCount: Int,
    onOpenReview: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = { BottomNavCustom(currentRoute = currentRoute, onNavigate = onNavigate) }
    ) { inner ->
        Box(modifier = Modifier.fillMaxSize().padding(inner)) {
            when (state) {
                is DashboardUiState.Loading -> LoadingContent()
                is DashboardUiState.Error -> ErrorContent(state.message)
                is DashboardUiState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        item {
                            DashboardHeader(
                                quincena = state.quincena,
                                expanded = false,
                                pendingReviewCount = pendingReviewCount,
                                onOpenReview = onOpenReview
                            )
                        }
                        item { CollapsedHealthCard(state = state) }
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Eyebrow("Transacciones recientes")
                                Text(
                                    "Ver todo",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        items(state.transactions.take(8), key = { it.expenseId }) { tx ->
                            TransactionRow(tx)
                        }
                    }
                    CircularCaptureFab(
                        onClick = onCapture,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 20.dp, bottom = 20.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Navegación: rail custom (expandido) + bottom nav (compacto)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Rail de iconos de 80dp. Mitigación de descubribilidad (brief D1):
 * el item ACTIVO muestra etiqueta de texto bajo el icono; todos llevan
 * `contentDescription`. Glifo de marca arriba, avatar de perfil abajo.
 */
@Composable
private fun NavigationRailCustom(
    currentRoute: String,
    onNavigate: ((String) -> Unit)?
) {
    Column(
        modifier = Modifier
            .width(80.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo de marca (cuadrado redondeado + icono → claramente "app", no cuenta)
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.AccountBalance,
                contentDescription = "Presupuesto del hogar",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.height(24.dp))

        navItems.dropLast(1).forEach { item ->
            RailItem(
                item = item,
                selected = currentRoute == item.route,
                onClick = { onNavigate?.invoke(item.route) }
            )
        }

        Spacer(Modifier.weight(1f))

        // Avatar de perfil (anclado abajo)
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable { onNavigate?.invoke("profile") },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "AS",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun RailItem(item: NavItem, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
        if (selected) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = item.label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = 1.2.sp
            )
        }
    }
}

@Composable
private fun BottomNavCustom(currentRoute: String, onNavigate: ((String) -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .navigationBarsPadding()
            .padding(top = 10.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.Top
    ) {
        navItems.forEach { item ->
            val selected = currentRoute == item.route
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onNavigate?.invoke(item.route) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 56.dp, height = 30.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        item.icon,
                        contentDescription = item.label,
                        tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    item.label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                    ),
                    color = if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DashboardHeader(
    quincena: QuincenaEntity?,
    expanded: Boolean,
    pendingReviewCount: Int = 0,
    onOpenReview: () -> Unit = {}
) {
    val eyebrow = buildString {
        append(quincena?.label ?: "Sin quincena activa")
        val range = quincenaRange(quincena)
        if (expanded && range.isNotEmpty()) append(" · ").append(range)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Column {
            Eyebrow(eyebrow)
            Spacer(Modifier.height(8.dp))
            Text(
                "Salud financiera",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Light,
                    fontSize = if (expanded) 36.sp else 26.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (pendingReviewCount > 0) {
                ReviewBadge(count = pendingReviewCount, onClick = onOpenReview)
            }
            if (expanded) {
                HeaderIconButton(Icons.Filled.Search, "Buscar")
            }
            HeaderIconButton(Icons.Filled.Tune, "Filtros")
        }
    }
}

/** Pastilla "N por revisar" → entra a la pantalla de revisión de atribuciones (Feature B). */
@Composable
private fun ReviewBadge(count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Insights, "Revisar atribuciones",
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "$count por revisar",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1, softWrap = false
        )
    }
}

@Composable
private fun HeaderIconButton(icon: ImageVector, description: String) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Eyebrow(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines: Int = Int.MAX_VALUE
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
        color = color,
        letterSpacing = 1.4.sp,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Panel principal: salud financiera (KPI héroe + ritmo + barras por miembro)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MainHealthPane(state: DashboardUiState.Success, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(36.dp)
    ) {
        HeroKpi(state = state)
        Spacer(Modifier.height(24.dp))
        MemberDistributionSection(
            beneficiary = state.beneficiaryDistribution,
            payer = state.payerDistribution,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun HeroKpi(state: DashboardUiState.Success) {
    val q = state.quincena
    val income = (q?.projectedIncomeMxn?.takeIf { it > 0.0 } ?: q?.actualIncomeMxn ?: 0.0)
    val spent = state.postedTotal
    val available = income - spent
    val progress = remember(q?.id) { computeProgress(q) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Eyebrow("Disponible para gastar")
            Eyebrow(q?.label ?: "")
        }
        Spacer(Modifier.height(12.dp))
        // Cifra héroe: $ + número grande + MXN
        Row(verticalAlignment = Alignment.Top) {
            Text(
                "$",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                available.toGrouped(),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Light,
                    fontSize = 76.sp,
                    letterSpacing = (-1).sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "MXN",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 18.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        // Fórmula: Ingresos − Gastos · periodo
        Row(verticalAlignment = Alignment.CenterVertically) {
            val incomeC = amountSemantic(FinancialTone.INCOME)
            val expenseC = amountSemantic(FinancialTone.EXPENSE)
            Text(
                "Ingresos ${income.toMxn()}",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = incomeC.color
            )
            Text("  −  ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Gastos ${spent.toMxn()}",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = expenseC.color
            )
        }
        Spacer(Modifier.height(20.dp))
        QuincenaRhythm(progress = progress)
    }
}

@Composable
private fun QuincenaRhythm(progress: QuincenaProgress) {
    val pct = (progress.fraction * 100).toInt()
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                "Día ${progress.dayIndex} de ${progress.totalDays} · $pct %",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, softWrap = false,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Quedan ${progress.daysRemaining} días",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, softWrap = false
            )
        }
        Spacer(Modifier.height(10.dp))
        // Barra de progreso con marcador "hoy"
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.fraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Distribución por miembro: barras horizontales ordenadas + toggle
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MemberDistributionSection(
    beneficiary: List<SpendByMember>,
    payer: List<SpendByMember>,
    modifier: Modifier = Modifier
) {
    var showPayer by rememberSaveable { mutableStateOf(false) }
    val data = if (showPayer) payer else beneficiary
    val total = data.sumOf { it.totalMxn }.let { if (it == 0.0) 1.0 else it }
    val colors = memberColors(data.size.coerceAtLeast(1))

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Eyebrow("Gasto por miembro", maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${data.sumOf { it.totalMxn }.toMxn()} · ${data.size} miembros",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            SegmentedToggle(
                options = listOf("Beneficiario", "Pagador"),
                selectedIndex = if (showPayer) 1 else 0,
                onSelect = { showPayer = it == 1 }
            )
        }
        Spacer(Modifier.height(22.dp))

        if (data.isEmpty()) {
            Text(
                "Aún no hay gastos atribuidos en esta quincena.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Renderiza TODOS los miembros con gasto, no un top-5: el header dice
                // "N miembros" y cortar a 5 ocultaba al de menor consumo (p. ej. Santiago)
                // sin forma de verlo (las pantallas de desglose siguen siendo placeholder).
                data.forEachIndexed { i, m ->
                    MemberBarRow(
                        member = m,
                        fraction = (m.totalMxn / total).toFloat().coerceIn(0f, 1f),
                        color = colors[i.coerceIn(0, colors.lastIndex)]
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Ordenado de mayor a menor consumo en la quincena",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Ver desglose",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1, softWrap = false
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward, null,
                        tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MemberBarRow(member: SpendByMember, fraction: Float, color: Color) {
    val share = (fraction * 100).toInt()
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Avatar + nombre (flexible)
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Text(
                member.memberName.take(1).uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1.1f)) {
            Text(
                member.memberName,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                "${member.expenseCount} mov.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(12.dp))
        // Barra (flexible)
        Box(
            modifier = Modifier
                .weight(1.3f)
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceAtLeast(0.02f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(7.dp))
                    .background(color)
            )
        }
        Spacer(Modifier.width(12.dp))
        // Monto + % (ajustado al contenido)
        Column(horizontalAlignment = Alignment.End) {
            Text(
                member.totalMxn.toMxn(),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Normal),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, softWrap = false
            )
            Text(
                "$share %",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.2.sp
            )
        }
    }
}

@Composable
private fun SegmentedToggle(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                    ),
                    color = if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Panel lateral: transacciones recientes (sin líneas, capas tonales alternas)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TransactionsPane(transactions: List<ExpenseWithDetails>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 28.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Eyebrow("Transacciones recientes")
                Spacer(Modifier.height(4.dp))
                Text(
                    "Últimos movimientos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.FilterList, "Filtrar", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(16.dp))

        if (transactions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Sin gastos registrados.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = rememberLazyListState(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(bottom = 88.dp) // espacio para que el FAB no tape la última fila
            ) {
                itemsIndexed(transactions, key = { _, it -> it.expenseId }) { index, tx ->
                    TransactionRow(tx, alternate = index % 2 == 1)
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(tx: ExpenseWithDetails, alternate: Boolean = false) {
    val tone = if (tx.status == "PLANNED") FinancialTone.NEUTRAL else FinancialTone.EXPENSE
    val sem = amountSemantic(tone)
    val rowBg = if (alternate) MaterialTheme.colorScheme.surfaceContainerHighest
    else MaterialTheme.colorScheme.surfaceContainer
    val avatarBg = remember(tx.categoryColorHex) {
        runCatching { tx.categoryColorHex?.let { Color(android.graphics.Color.parseColor(it)) } }
            .getOrNull()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(rowBg)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(avatarBg ?: MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                tx.categoryCode.take(1).uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                tx.concept,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${tx.categoryName} · ${tx.occurredAt.toShortDate()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics { contentDescription = sem.description }
        ) {
            sem.icon?.let {
                Icon(it, null, tint = sem.color, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(2.dp))
            }
            Text(
                "${sem.sign}${tx.amountMxn.toMxn()}",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (tone == FinancialTone.NEUTRAL) MaterialTheme.colorScheme.onSurface else sem.color,
                maxLines = 1, softWrap = false
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tarjeta de salud colapsada (compacto)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CollapsedHealthCard(state: DashboardUiState.Success) {
    val q = state.quincena
    val income = (q?.projectedIncomeMxn?.takeIf { it > 0.0 } ?: q?.actualIncomeMxn ?: 0.0)
    val spent = state.postedTotal
    val available = income - spent
    val progress = remember(q?.id) { computeProgress(q) }
    val pct = (progress.fraction * 100).toInt()
    val incomeC = amountSemantic(FinancialTone.INCOME)
    val expenseC = amountSemantic(FinancialTone.EXPENSE)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(22.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Eyebrow("Disponible para gastar")
            Text(
                quincenaRange(q),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Top) {
            Text(
                "$",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Light),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                available.toGrouped(),
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Light, fontSize = 52.sp
                ),
                color = MaterialTheme.colorScheme.onSurface, maxLines = 1
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "MXN",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row {
            Text(
                "${incomeC.sign} ${income.toMxn()}",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = incomeC.color
            )
            Text("   ", style = MaterialTheme.typography.bodySmall)
            Text(
                "${expenseC.sign} ${spent.toMxn()}",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = expenseC.color
            )
        }
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.fraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Día ${progress.dayIndex} de ${progress.totalDays} · $pct %",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "${progress.daysRemaining} días restantes",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(14.dp))
        // Ritmo
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(incomeC.container),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.TrendingUp, "Ritmo", tint = incomeC.color, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Eyebrow("Ritmo")
                Spacer(Modifier.height(2.dp))
                Text(
                    "Te quedan ${(available / progress.daysRemaining.coerceAtLeast(1)).toMxn()} por día",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FABs
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ExtendedCaptureFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(start = 22.dp, end = 26.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            "Capturar gasto",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@Composable
private fun CircularCaptureFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Add, "Capturar gasto", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Estados auxiliares
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ErrorContent(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Error al cargar",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
