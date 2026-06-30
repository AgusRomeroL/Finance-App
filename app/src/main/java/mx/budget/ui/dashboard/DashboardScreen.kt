package mx.budget.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.budget.ai.proactive.ProactiveSuggestion
import mx.budget.data.local.entity.PendingCaptureEntity
import mx.budget.data.local.entity.CategoryEntity
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
import kotlin.math.abs
import kotlin.math.roundToInt

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
    NavItem("calendar", Icons.Filled.CalendarMonth, "Calendario"),
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
    onOpenReview: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenSuggestions: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val pendingReviewCount by viewModel.pendingReviewCount.collectAsState()
    val proactiveSuggestions by viewModel.proactiveSuggestions.collectAsState()
    val bankCaptures by viewModel.pendingBankCaptures.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val selectedGroups by viewModel.selectedGroupIds.collectAsState()
    val isExpanded = windowWidthDp >= 600.dp
    var showCapture by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }

    if (showCapture) {
        CaptureBottomSheet(
            viewModel = captureViewModel,
            onDismiss = { showCapture = false }
        )
    }

    if (showFilterSheet) {
        FilterBottomSheet(
            groups = groups,
            selected = selectedGroups,
            onSave = { viewModel.setSelectedGroups(it); showFilterSheet = false },
            onDismiss = { showFilterSheet = false }
        )
    }

    val quincenaNav = QuincenaNav(
        onOlder = viewModel::viewOlderQuincena,
        onNewer = viewModel::viewNewerQuincena,
        onReset = viewModel::resetToActiveQuincena
    )

    val filterUi = FilterUi(
        groups = groups,
        selected = selectedGroups,
        onToggle = { id ->
            viewModel.setSelectedGroups(if (id in selectedGroups) selectedGroups - id else selectedGroups + id)
        },
        onOpenSheet = { showFilterSheet = true }
    )

    // [Registrar] de la sugerencia: prefila concepto + categoría en la captura
    // (el concepto dispara además el chip de atribución de Feature A) y abre el sheet.
    val onRegisterSuggestion: (ProactiveSuggestion) -> Unit = { suggestion ->
        captureViewModel?.onConceptChange(suggestion.concept)
        captureViewModel?.onCategorySelected(suggestion.categoryId)
        showCapture = true
    }

    if (isExpanded) {
        ExpandedDashboard(
            state = uiState,
            currentRoute = currentRoute,
            onNavigate = onNavigate,
            onCapture = { showCapture = true },
            onOpenSearch = onOpenSearch,
            pendingReviewCount = pendingReviewCount,
            onOpenReview = onOpenReview,
            quincenaNav = quincenaNav,
            filterUi = filterUi,
            proactiveSuggestions = proactiveSuggestions,
            onRegisterSuggestion = onRegisterSuggestion,
            onDismissSuggestion = viewModel::dismissProactiveSuggestion,
            onOpenSuggestions = onOpenSuggestions,
            bankCaptures = bankCaptures,
            onConfirmCapture = viewModel::confirmBankCapture,
            onDismissCapture = viewModel::dismissBankCapture
        )
    } else {
        CompactDashboard(
            state = uiState,
            currentRoute = currentRoute,
            onNavigate = onNavigate,
            onCapture = { showCapture = true },
            onOpenSearch = onOpenSearch,
            pendingReviewCount = pendingReviewCount,
            onOpenReview = onOpenReview,
            quincenaNav = quincenaNav,
            filterUi = filterUi,
            proactiveSuggestions = proactiveSuggestions,
            onRegisterSuggestion = onRegisterSuggestion,
            onDismissSuggestion = viewModel::dismissProactiveSuggestion,
            onOpenSuggestions = onOpenSuggestions,
            bankCaptures = bankCaptures,
            onConfirmCapture = viewModel::confirmBankCapture,
            onDismissCapture = viewModel::dismissBankCapture
        )
    }
}

/** Callbacks del navegador de quincenas (chip ‹ etiqueta ›). */
private data class QuincenaNav(
    val onOlder: () -> Unit,
    val onNewer: () -> Unit,
    val onReset: () -> Unit
)

/** Estado + callbacks de los filtros por grupo (pills + bottom sheet). */
private data class FilterUi(
    val groups: List<CategoryEntity>,
    val selected: Set<String>,
    val onToggle: (String) -> Unit,
    val onOpenSheet: () -> Unit
)

// ─────────────────────────────────────────────────────────────────────────────
// Layout expandido (Fold interno)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ExpandedDashboard(
    state: DashboardUiState,
    currentRoute: String,
    onNavigate: ((String) -> Unit)?,
    onCapture: () -> Unit,
    onOpenSearch: () -> Unit,
    pendingReviewCount: Int,
    onOpenReview: () -> Unit,
    quincenaNav: QuincenaNav,
    filterUi: FilterUi,
    proactiveSuggestions: List<ProactiveSuggestion>,
    onRegisterSuggestion: (ProactiveSuggestion) -> Unit,
    onDismissSuggestion: (String) -> Unit,
    onOpenSuggestions: () -> Unit,
    bankCaptures: List<PendingCaptureEntity>,
    onConfirmCapture: (String) -> Unit,
    onDismissCapture: (String) -> Unit
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
                            onOpenReview = onOpenReview,
                            canViewOlder = state.canViewOlder,
                            canViewNewer = state.canViewNewer,
                            viewingActive = state.viewingActive,
                            quincenaNav = quincenaNav
                        )
                        if (state.viewingActive && (bankCaptures.isNotEmpty() || proactiveSuggestions.isNotEmpty())) {
                            Spacer(Modifier.height(18.dp))
                            SuggestionsSection(
                                bankCaptures = bankCaptures,
                                proactiveSuggestions = proactiveSuggestions,
                                isExpanded = true,
                                onSeeMore = onOpenSuggestions,
                                onConfirmCapture = onConfirmCapture,
                                onDismissCapture = onDismissCapture,
                                onRegisterSuggestion = onRegisterSuggestion,
                                onDismissSuggestion = onDismissSuggestion
                            )
                        }
                        if (state.viewingActive) {
                            Spacer(Modifier.height(18.dp))
                            FilterPillsRow(
                                groups = filterUi.groups,
                                selectedGroupIds = filterUi.selected,
                                onToggle = filterUi.onToggle,
                                onOpenSheet = filterUi.onOpenSheet
                            )
                        }
                        Spacer(Modifier.height(22.dp))
                        BentoPanes(state = state, modifier = Modifier.fillMaxSize())
                    }
                    // Barra inferior: búsqueda + mic + "+" (reemplaza el FAB).
                    BottomActionBar(
                        query = "",
                        onQueryChange = {},
                        onPlus = onCapture,
                        readOnly = true,
                        onActivate = onOpenSearch,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
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
 * vía `rememberSaveable` (brief C13). Anclada en 55/45, arrastrable en [0.45, 0.78].
 * (Default 55/45, no 62/38: a fontScale alto + bold del dispositivo real, 38% dejaba
 * el panel de transacciones tan angosto que los conceptos se cortaban a "Com…".)
 */
@Composable
private fun BentoPanes(state: DashboardUiState.Success, modifier: Modifier = Modifier) {
    var fraction by rememberSaveable { mutableStateOf(0.55f) }
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
    onOpenSearch: () -> Unit,
    pendingReviewCount: Int,
    onOpenReview: () -> Unit,
    quincenaNav: QuincenaNav,
    filterUi: FilterUi,
    proactiveSuggestions: List<ProactiveSuggestion>,
    onRegisterSuggestion: (ProactiveSuggestion) -> Unit,
    onDismissSuggestion: (String) -> Unit,
    onOpenSuggestions: () -> Unit,
    bankCaptures: List<PendingCaptureEntity>,
    onConfirmCapture: (String) -> Unit,
    onDismissCapture: (String) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            Column {
                BottomActionBar(
                    query = "",
                    onQueryChange = {},
                    onPlus = onCapture,
                    readOnly = true,
                    onActivate = onOpenSearch
                )
                BottomNavCustom(currentRoute = currentRoute, onNavigate = onNavigate)
            }
        }
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
                                onOpenReview = onOpenReview,
                                canViewOlder = state.canViewOlder,
                                canViewNewer = state.canViewNewer,
                                viewingActive = state.viewingActive,
                                quincenaNav = quincenaNav
                            )
                        }
                        if (state.viewingActive && (bankCaptures.isNotEmpty() || proactiveSuggestions.isNotEmpty())) {
                            item {
                                SuggestionsSection(
                                    bankCaptures = bankCaptures,
                                    proactiveSuggestions = proactiveSuggestions,
                                    isExpanded = false,
                                    onSeeMore = onOpenSuggestions,
                                    onConfirmCapture = onConfirmCapture,
                                    onDismissCapture = onDismissCapture,
                                    onRegisterSuggestion = onRegisterSuggestion,
                                    onDismissSuggestion = onDismissSuggestion
                                )
                            }
                        }
                        item { CollapsedHealthCard(state = state) }
                        if (state.viewingActive) {
                            item {
                                FilterPillsRow(
                                    groups = filterUi.groups,
                                    selectedGroupIds = filterUi.selected,
                                    onToggle = filterUi.onToggle,
                                    onOpenSheet = filterUi.onOpenSheet
                                )
                            }
                        }
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
    onOpenReview: () -> Unit = {},
    canViewOlder: Boolean = false,
    canViewNewer: Boolean = false,
    viewingActive: Boolean = true,
    quincenaNav: QuincenaNav? = null
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
        Column(modifier = Modifier.weight(1f)) {
            Eyebrow(eyebrow, maxLines = 1)
            Spacer(Modifier.height(8.dp))
            Text(
                "Salud financiera",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Light,
                    fontSize = if (expanded) 36.sp else 26.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (quincenaNav != null) {
                QuincenaNavChip(
                    label = shortQuincenaLabel(quincena),
                    canViewOlder = canViewOlder,
                    canViewNewer = canViewNewer,
                    viewingActive = viewingActive,
                    nav = quincenaNav
                )
            }
            if (pendingReviewCount > 0) {
                ReviewBadge(count = pendingReviewCount, onClick = onOpenReview, compact = !expanded)
            }
            // Los íconos de búsqueda/filtros del header se quitaron: la búsqueda vive en
            // la barra inferior y los filtros en su propia sección de pills.
        }
    }
}

/** Navegador de quincenas: ‹ etiqueta › — ‹ va a la más antigua, › a la más reciente. */
@Composable
private fun QuincenaNavChip(
    label: String,
    canViewOlder: Boolean,
    canViewNewer: Boolean,
    viewingActive: Boolean,
    nav: QuincenaNav
) {
    val bg = if (viewingActive) MaterialTheme.colorScheme.surfaceContainerHigh
    else MaterialTheme.colorScheme.primaryContainer
    val fg = if (viewingActive) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onPrimaryContainer
    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChevronButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Quincena anterior", enabled = canViewOlder, tint = fg, onClick = nav.onOlder)
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = fg,
            maxLines = 1, softWrap = false,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = !viewingActive, onClick = nav.onReset)
                .padding(horizontal = 6.dp, vertical = 4.dp)
        )
        ChevronButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Quincena siguiente", enabled = canViewNewer, tint = fg, onClick = nav.onNewer)
    }
}

@Composable
private fun ChevronButton(icon: ImageVector, description: String, enabled: Boolean, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon, description,
            tint = if (enabled) tint else tint.copy(alpha = 0.3f),
            modifier = Modifier.size(20.dp)
        )
    }
}

/** Etiqueta corta de quincena para el chip: "Q2 Jun '26" (robusta al formato del label). */
private fun shortQuincenaLabel(q: QuincenaEntity?): String {
    if (q == null) return "Quincena"
    val parts = q.label.split(" ")
    val qn = parts.getOrNull(0).orEmpty()
    val mon = parts.getOrNull(1)?.take(3).orEmpty()
    val yy = parts.getOrNull(2)?.takeLast(2).orEmpty()
    return listOf(qn, mon, if (yy.isNotBlank()) "'$yy" else "").filter { it.isNotBlank() }.joinToString(" ")
}

/**
 * Pastilla de sugerencias por revisar (Feature B) → entra a la pantalla de revisión.
 * En [compact] muestra solo el icono + número (ahorra ancho junto al chip de quincena).
 */
@Composable
private fun ReviewBadge(count: Int, onClick: () -> Unit, compact: Boolean = false) {
    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 12.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Insights, "Revisar atribuciones",
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            if (compact) "$count" else "$count por revisar",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1, softWrap = false
        )
    }
}

@Composable
private fun Eyebrow(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines: Int = Int.MAX_VALUE,
    modifier: Modifier = Modifier
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
        color = color,
        letterSpacing = 1.4.sp,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroKpi(state: DashboardUiState.Success) {
    val q = state.quincena
    val income = (q?.projectedIncomeMxn?.takeIf { it > 0.0 } ?: q?.actualIncomeMxn ?: 0.0)
    val spent = state.postedTotal
    val planned = state.plannedTotal
    val hasPlanned = planned > 0.0
    val gross = income - spent          // histórico: Ingresos − Gastos POSTED
    val net = gross - planned           // budget-aware: además reserva lo PLANNED (G.2.4)
    // Toggle de presentación. Default = neto, según el propósito quincenal (reservar lo previsto
    // ANTES de pagarlo). Solo aplica si hay PLANNED; sin ellos el KPI se comporta como siempre.
    var showNet by rememberSaveable { mutableStateOf(true) }
    val shown = if (hasPlanned && showNet) net else gross
    // Interpola la cifra al alternar el modo — movimiento expresivo (M3), nunca un salto.
    val animatedShown by animateFloatAsState(
        targetValue = shown.toFloat(),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "heroAmount",
    )
    val progress = remember(q?.id) { computeProgress(q) }

    Column {
        // El mes/quincena NO se repite aquí: ya está en el header superior, en el
        // chip de navegación y en "Día N de M" abajo. Repetirlo colisionaba con
        // este eyebrow a fontScale alto ("GASTAR" + "JUNIO" encimados).
        Eyebrow("Disponible para gastar")
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
                animatedShown.toDouble().toGrouped(),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Light,
                    fontSize = 66.sp,
                    letterSpacing = (-1).sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, softWrap = false
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "MXN",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, softWrap = false,
                modifier = Modifier.padding(top = 18.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        // Fórmula: Ingresos − Gastos. FlowRow para que "Gastos $X" baje como unidad
        // (no "Gastos"/"$X" partido) cuando no cabe a fontScale alto + panel angosto.
        FlowRow(verticalArrangement = Arrangement.Center) {
            val incomeC = amountSemantic(FinancialTone.INCOME)
            val expenseC = amountSemantic(FinancialTone.EXPENSE)
            Text(
                "Ingresos ${income.toMxn()}",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = incomeC.color,
                maxLines = 1, softWrap = false
            )
            Text("  −  ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Gastos ${spent.toMxn()}",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = expenseC.color,
                maxLines = 1, softWrap = false
            )
            // Término "Reservado" sólo en modo neto: lo PLANNED no es ingreso ni gasto ejecutado,
            // por eso tono neutral (tertiary) y la etiqueta porta el significado, no el color.
            AnimatedVisibility(visible = hasPlanned && showNet) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("  −  ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "Reservado ${planned.toMxn()}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1, softWrap = false
                    )
                }
            }
        }
        if (hasPlanned) {
            Spacer(Modifier.height(14.dp))
            ReserveToggle(showNet = showNet, onChange = { showNet = it })
        }
        Spacer(Modifier.height(20.dp))
        QuincenaRhythm(progress = progress)
        Spacer(Modifier.height(16.dp))
        RitmoCard(
            quincena = state.quincena,
            postedTotal = state.postedTotal,
            viewingActive = state.viewingActive
        )
    }
}

/**
 * Segmentado mini para el KPI (G.2.4): "Neto" reserva lo PLANNED del periodo; "Bruto" sólo resta
 * lo ya pagado (POSTED). Redundancia no-cromática: el segmento activo va relleno + en negrita,
 * no depende sólo del color.
 */
@Composable
private fun ReserveToggle(showNet: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(3.dp)
    ) {
        ReserveSegment("Neto", selected = showNet) { onChange(true) }
        ReserveSegment("Bruto", selected = !showNet) { onChange(false) }
    }
}

@Composable
private fun ReserveSegment(label: String, selected: Boolean, onClick: () -> Unit) {
    val base = Modifier.clip(RoundedCornerShape(50))
    val withBg = if (selected) base.background(MaterialTheme.colorScheme.primary) else base
    Text(
        label,
        style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        ),
        color = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1, softWrap = false,
        modifier = withBg
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    )
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
// Sugerencias inteligentes — carrusel (Features C + D)
// ─────────────────────────────────────────────────────────────────────────────

/** Un ítem de sugerencia: captura bancaria (D) o sugerencia proactiva (C). */
internal sealed interface SmartSuggestionItem {
    val key: String

    data class Bank(val capture: PendingCaptureEntity) : SmartSuggestionItem {
        override val key get() = "bank:${capture.id}"
    }

    data class Proactive(val suggestion: ProactiveSuggestion) : SmartSuggestionItem {
        override val key get() = "proactive:${suggestion.canonicalKey}"
    }
}

/** Combina capturas (D, primero) + proactivas (C) en una sola lista de ítems. */
internal fun buildSuggestionItems(
    bankCaptures: List<PendingCaptureEntity>,
    proactiveSuggestions: List<ProactiveSuggestion>
): List<SmartSuggestionItem> = buildList {
    bankCaptures.forEach { add(SmartSuggestionItem.Bank(it)) }
    proactiveSuggestions.forEach { add(SmartSuggestionItem.Proactive(it)) }
}

/**
 * Sección "Sugerencias" (Features C + D), estilo Collections de Pixel Screenshots:
 * encabezado con chevron ">" (→ pantalla "Todas las sugerencias") y una fila de
 * tarjetas con conteo adaptable. Compacto muestra hasta 3 (si hay más: 2 + "Ver
 * más"); Fold hasta 5 (si hay más: 4 + "Ver más"). El tile "Ver más" también navega.
 */
@Composable
private fun SuggestionsSection(
    bankCaptures: List<PendingCaptureEntity>,
    proactiveSuggestions: List<ProactiveSuggestion>,
    isExpanded: Boolean,
    onSeeMore: () -> Unit,
    onConfirmCapture: (String) -> Unit,
    onDismissCapture: (String) -> Unit,
    onRegisterSuggestion: (ProactiveSuggestion) -> Unit,
    onDismissSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = buildSuggestionItems(bankCaptures, proactiveSuggestions)
    if (items.isEmpty()) return

    val cap = if (isExpanded) 5 else 3
    val overflow = items.size > cap
    val shown = if (overflow) items.take(cap - 1) else items
    val cardW = 300.dp

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Eyebrow("Sugerencias")
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClick = onSeeMore),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward, "Ver todas las sugerencias",
                    tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            shown.forEach { item ->
                Box(Modifier.width(cardW)) {
                    SmartSuggestionCard(item, onConfirmCapture, onDismissCapture, onRegisterSuggestion, onDismissSuggestion)
                }
            }
            if (overflow) {
                SeeMoreTile(remaining = items.size - shown.size, onClick = onSeeMore)
            }
        }
    }
}

/** Tile final "Ver más" con el conteo restante; navega a la pantalla de sugerencias. */
@Composable
private fun SeeMoreTile(remaining: Int, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(132.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Ver más",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
        if (remaining > 0) {
            Text(
                "+$remaining",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Renderiza la tarjeta completa según el tipo de sugerencia (reutilizada por la pantalla). */
@Composable
internal fun SmartSuggestionCard(
    item: SmartSuggestionItem,
    onConfirmCapture: (String) -> Unit,
    onDismissCapture: (String) -> Unit,
    onRegisterSuggestion: (ProactiveSuggestion) -> Unit,
    onDismissSuggestion: (String) -> Unit
) {
    when (item) {
        is SmartSuggestionItem.Bank -> BankCaptureChip(
            capture = item.capture,
            onConfirm = { onConfirmCapture(item.capture.id) },
            onDismiss = { onDismissCapture(item.capture.id) }
        )
        is SmartSuggestionItem.Proactive -> ProactiveSuggestionChip(
            suggestion = item.suggestion,
            onRegister = { onRegisterSuggestion(item.suggestion) },
            onDismiss = { onDismissSuggestion(item.suggestion.canonicalKey) }
        )
    }
}

/**
 * Fila de acciones de una sugerencia: dos botones a **ancho igual** (`weight`) con
 * texto centrado, para que SIEMPRE quepan y muestren su etiqueta aunque la tarjeta
 * activa sea angosta (en el carrusel card-based ocupa solo parte del ancho). El
 * primario es píldora rellena; el secundario, tonal sutil.
 */
@Composable
private fun SuggestionActionRow(
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    secondaryContentColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onPrimary)
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                primaryLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1, textAlign = TextAlign.Center
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(secondaryContentColor.copy(alpha = 0.12f))
                .clickable(onClick = onSecondary)
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                secondaryLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = secondaryContentColor,
                maxLines = 1, textAlign = TextAlign.Center
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Captura desde notificación bancaria (Feature D, §F.6)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Banner de una captura bancaria detectada: "BBVA · $480 en OXXO · ¿Registrar?".
 * Propose-then-confirm: [Registrar] inserta el gasto con atribución inferida;
 * [Descartar] lo ignora. Redundancia color+texto+icono; vive en la app.
 */
@Composable
private fun BankCaptureChip(
    capture: PendingCaptureEntity,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Etiqueta + icono según la fuente de la captura (bandeja unificada §G.1):
    // banco, voz, widget, reloj o calendario comparten el mismo chip.
    val (eyebrowText, chipIcon, iconDesc) = when (capture.source) {
        "VOICE" -> Triple("Por voz · captura", Icons.Filled.Mic, "Captura por voz")
        "WIDGET" -> Triple("Widget · captura", Icons.Filled.Add, "Captura desde widget")
        "WATCH" -> Triple("Reloj · captura", Icons.Filled.Watch, "Captura desde reloj")
        "CALENDAR" -> Triple("Calendario · recordatorio", Icons.Filled.Event, "Recordatorio")
        else -> Triple(
            "${capture.bankName ?: "Cargo"} · cargo detectado",
            Icons.Filled.AccountBalanceWallet,
            "Cargo bancario",
        )
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    chipIcon, iconDesc,
                    tint = MaterialTheme.colorScheme.onTertiary, modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Eyebrow(eyebrowText, color = MaterialTheme.colorScheme.onTertiaryContainer, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Text(
                    "${capture.amountMxn.toMxn()} en ${capture.concept}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    "¿Registrar este gasto?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.78f),
                    minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        SuggestionActionRow(
            primaryLabel = "Registrar",
            onPrimary = onConfirm,
            secondaryLabel = "Descartar",
            onSecondary = onDismiss,
            secondaryContentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sugerencia proactiva (Feature C, §F.5)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Banner proactivo en el tope del dashboard: sugiere el gasto que el hogar suele
 * registrar ahora (hora + día de semana + día de quincena). No invasivo: vive en
 * la app (nunca push), descartable con "Ahora no" (señal negativa implícita).
 * Redundancia no-cromática: icono + texto explican el motivo sin depender del color.
 */
@Composable
private fun ProactiveSuggestionChip(
    suggestion: ProactiveSuggestion,
    onRegister: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.AutoAwesome, "Sugerencia",
                    tint = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Eyebrow("Sugerencia", color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Text(
                    "¿Registrar \"${suggestion.concept}\"?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    suggestion.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f),
                    minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        SuggestionActionRow(
            primaryLabel = "Registrar",
            onPrimary = onRegister,
            secondaryLabel = "Ahora no",
            onSecondary = onDismiss,
            secondaryContentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

/**
 * Tarjeta de **Ritmo**: compara el gasto ejecutado con el ritmo lineal esperado
 * por el tiempo transcurrido de la quincena, y muestra el gasto disponible por día.
 *
 * "% por debajo/encima del gasto previsto" = (gastado − presupuesto×fracción_tiempo)
 * / (presupuesto×fracción_tiempo). El presupuesto es `projectedExpensesMxn` (con
 * fallback al ingreso). Redundancia no-cromática: icono (flecha) + texto + color.
 */
@Composable
private fun RitmoCard(
    quincena: QuincenaEntity?,
    postedTotal: Double,
    viewingActive: Boolean,
    modifier: Modifier = Modifier
) {
    val income = (quincena?.projectedIncomeMxn?.takeIf { it > 0.0 } ?: quincena?.actualIncomeMxn ?: 0.0)
    val budget = (quincena?.projectedExpensesMxn?.takeIf { it > 0.0 } ?: income)
    val progress = remember(quincena?.id) { computeProgress(quincena) }
    val available = income - postedTotal
    val expectedByNow = budget * progress.fraction
    val deviation = if (expectedByNow > 0.0) (postedTotal - expectedByNow) / expectedByNow else 0.0
    val pct = (abs(deviation) * 100).roundToInt()
    val onTrack = pct < 1
    val below = deviation < 0
    val good = onTrack || below
    val showPerDay = viewingActive && progress.daysRemaining > 0
    val perDay = if (progress.daysRemaining > 0) available / progress.daysRemaining else available

    val sem = amountSemantic(if (good) FinancialTone.INCOME else FinancialTone.WARNING)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(CircleShape).background(sem.container),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (below) Icons.AutoMirrored.Filled.TrendingDown else Icons.AutoMirrored.Filled.TrendingUp,
                "Ritmo de gasto", tint = sem.color, modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Eyebrow("Ritmo", maxLines = 1)
            Spacer(Modifier.height(2.dp))
            val msg = buildAnnotatedString {
                append("Vas ")
                withStyle(SpanStyle(color = sem.color, fontWeight = FontWeight.SemiBold)) {
                    append(if (onTrack) "al ritmo" else "$pct % ${if (below) "por debajo" else "por encima"}")
                }
                append(" del gasto previsto")
            }
            Text(
                msg,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (showPerDay) perDay.toMxn() else available.toMxn(),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, softWrap = false
            )
            Eyebrow(if (showPerDay) "Por día" else "Disponible", maxLines = 1)
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
        // Título + toggle APILADOS: a fontScale alto + bold del dispositivo real, el
        // toggle "Beneficiario/Pagador" lado a lado con el título no cabe y recortaba
        // el eyebrow a "GAST…". En su propia línea siempre cabe.
        Eyebrow("Gasto por miembro", maxLines = 2)
        Spacer(Modifier.height(4.dp))
        Text(
            "${data.sumOf { it.totalMxn }.toMxn()} · ${data.size} miembros",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(12.dp))
        SegmentedToggle(
            options = listOf("Beneficiario", "Pagador"),
            selectedIndex = if (showPayer) 1 else 0,
            onSelect = { showPayer = it == 1 }
        )
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
internal fun TransactionRow(tx: ExpenseWithDetails, alternate: Boolean = false) {
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
                maxLines = 2, overflow = TextOverflow.Ellipsis
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollapsedHealthCard(state: DashboardUiState.Success) {
    val q = state.quincena
    val income = (q?.projectedIncomeMxn?.takeIf { it > 0.0 } ?: q?.actualIncomeMxn ?: 0.0)
    val spent = state.postedTotal
    val planned = state.plannedTotal
    val hasPlanned = planned > 0.0
    val gross = income - spent
    val net = gross - planned           // budget-aware: reserva lo PLANNED (G.2.4)
    var showNet by rememberSaveable { mutableStateOf(true) }
    val shown = if (hasPlanned && showNet) net else gross
    val animatedShown by animateFloatAsState(
        targetValue = shown.toFloat(),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "heroAmountCompact",
    )
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // weight(1f) en el eyebrow: el rango ya no puede empujarlo y encimarse
            // a fontScale alto; si no caben, el eyebrow envuelve en su propio espacio.
            Eyebrow("Disponible para gastar", modifier = Modifier.weight(1f))
            Text(
                quincenaRange(q),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, softWrap = false
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
                animatedShown.toDouble().toGrouped(),
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
        FlowRow(verticalArrangement = Arrangement.Center) {
            Text(
                "${incomeC.sign} ${income.toMxn()}",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = incomeC.color, maxLines = 1, softWrap = false
            )
            Text("   ", style = MaterialTheme.typography.bodySmall)
            Text(
                "${expenseC.sign} ${spent.toMxn()}",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = expenseC.color, maxLines = 1, softWrap = false
            )
            AnimatedVisibility(visible = hasPlanned && showNet) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("   ", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "− ${planned.toMxn()} reservado",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.tertiary, maxLines = 1, softWrap = false
                    )
                }
            }
        }
        if (hasPlanned) {
            Spacer(Modifier.height(12.dp))
            ReserveToggle(showNet = showNet, onChange = { showNet = it })
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
        RitmoCard(
            quincena = state.quincena,
            postedTotal = state.postedTotal,
            viewingActive = state.viewingActive
        )
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
