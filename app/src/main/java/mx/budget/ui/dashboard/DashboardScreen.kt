package mx.budget.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.budget.data.local.entity.QuincenaEntity
import mx.budget.data.local.result.ExpenseWithDetails
import mx.budget.data.local.result.SpendByMember
import mx.budget.ui.capture.CaptureBottomSheet
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

// ─────────────────────────────────────────────────────────────────────────────
// Utilidades de formato
// ─────────────────────────────────────────────────────────────────────────────

private val mxnFormat: NumberFormat = NumberFormat.getCurrencyInstance(Locale("es", "MX"))

private fun Double.toMxn(): String = mxnFormat.format(this)

private fun Long.toShortDate(): String {
    val sdf = SimpleDateFormat("d MMM", Locale("es", "MX"))
    return sdf.format(Date(this))
}

// ─────────────────────────────────────────────────────────────────────────────
// Modelo de navegación lateral
// ─────────────────────────────────────────────────────────────────────────────

private data class NavDrawerItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

private val drawerItems = listOf(
    NavDrawerItem("Dashboard", Icons.Filled.Dashboard, "dashboard"),
    NavDrawerItem("Libro Mayor", Icons.Filled.List, "ledger"),
    NavDrawerItem("Cuentas", Icons.Filled.Wallet, "wallets"),
    NavDrawerItem("Analíticas", Icons.Filled.Analytics, "analytics"),
    NavDrawerItem("Perfil", Icons.Filled.Person, "profile")
)

// ─────────────────────────────────────────────────────────────────────────────
// DashboardScreen — Pantalla principal
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Pantalla principal del presupuesto quincenal.
 *
 * Transpila `ui_reference/quincenal_dashboard_fold_inner/code.html` a
 * Jetpack Compose con Material 3 Expressive.
 *
 * **Layout adaptativo (foldable-aware)**:
 * - Pantallas < 600dp: columna única + BottomNavigationBar + FAB
 * - Pantallas ≥ 600dp: [PermanentNavigationDrawer] + layout de dos paneles
 *   - Panel izquierdo 40%: [LedgerPane] (libro mayor de transacciones)
 *   - Panel derecho 60%: [HealthPane] (KPIs + distribución por miembro)
 *
 * @param viewModel   ViewModel que provee los StateFlows del dashboard.
 * @param windowWidthDp Ancho disponible en dp — se usa para detectar el
 *                      breakpoint del layout de dos paneles (600dp).
 *                      En producción, obtener con `LocalConfiguration.current.screenWidthDp`.
 * @param onOpenCapture Callback que abre [CaptureBottomSheet].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    windowWidthDp: Dp = 360.dp,
    onOpenCapture: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val isExpandedScreen = windowWidthDp >= 600.dp

    var showCapture by remember { mutableStateOf(false) }
    var selectedRoute by remember { mutableStateOf("dashboard") }

    // ── CaptureBottomSheet overlay ────────────────────────────────────────────
    // Se renderiza al nivel del árbol del Scaffold para evitar que la
    // NavigationBar tape el contenido del sheet en pantallas compactas.
    if (showCapture) {
        CaptureBottomSheet(
            onDismiss = { showCapture = false }
        )
    }

    if (isExpandedScreen) {
        // ── Layout expandido: NavigationDrawer docked + dual pane ────────────
        PermanentNavigationDrawer(
            drawerContent = {
                PermanentDrawerSheet(
                    modifier = Modifier.width(256.dp),
                    drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    BudgetNavigationDrawerContent(
                        selectedRoute = selectedRoute,
                        onRouteSelected = { selectedRoute = it }
                    )
                }
            }
        ) {
            Scaffold(
                topBar = {
                    BudgetTopAppBar(isExpandedScreen = true)
                },
                floatingActionButton = {
                    BudgetFAB(onClick = { showCapture = true })
                },
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) { innerPadding ->
                when (val state = uiState) {
                    is DashboardUiState.Loading -> LoadingContent(
                        modifier = Modifier.padding(innerPadding)
                    )
                    is DashboardUiState.Error -> ErrorContent(
                        message = state.message,
                        modifier = Modifier.padding(innerPadding)
                    )
                    is DashboardUiState.Success -> {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            // Panel izquierdo: 40%
                            LedgerPane(
                                quincena = state.quincena,
                                transactions = state.transactions,
                                modifier = Modifier
                                    .weight(0.4f)
                                    .fillMaxHeight()
                            )

                            Divider(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(1.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
                            )

                            // Panel derecho: 60%
                            HealthPane(
                                quincena = state.quincena,
                                postedTotal = state.postedTotal,
                                plannedTotal = state.plannedTotal,
                                balance = state.balance,
                                memberDistribution = state.memberDistribution,
                                modifier = Modifier
                                    .weight(0.6f)
                                    .fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }
    } else {
        // ── Layout compacto: sin drawer, columna única ─────────────────────────
        Scaffold(
            topBar = {
                BudgetTopAppBar(isExpandedScreen = false)
            },
            bottomBar = {
                BudgetBottomNav(
                    selectedRoute = selectedRoute,
                    onRouteSelected = { selectedRoute = it }
                )
            },
            floatingActionButton = {
                BudgetFAB(onClick = { showCapture = true })
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) { innerPadding ->
            when (val state = uiState) {
                is DashboardUiState.Loading -> LoadingContent(
                    modifier = Modifier.padding(innerPadding)
                )
                is DashboardUiState.Error -> ErrorContent(
                    message = state.message,
                    modifier = Modifier.padding(innerPadding)
                )
                is DashboardUiState.Success -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // En pantalla compacta, el HealthPane va primero (KPIs visibles)
                        HealthPane(
                            quincena = state.quincena,
                            postedTotal = state.postedTotal,
                            plannedTotal = state.plannedTotal,
                            balance = state.balance,
                            memberDistribution = state.memberDistribution,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                        LedgerPane(
                            quincena = state.quincena,
                            transactions = state.transactions,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SubComponentes de la pantalla
// ─────────────────────────────────────────────────────────────────────────────

/**
 * TopAppBar con buscador central y avatar de usuario.
 * Transpila el `<header>` del prototipo HTML.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetTopAppBar(
    isExpandedScreen: Boolean,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }

    TopAppBar(
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        ),
        navigationIcon = {
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable { },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Buscar",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        title = {
            if (isExpandedScreen) {
                // Barra de búsqueda central tipo "Ask Gemini"
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Pregunta a Gemini sobre tu quincena...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            } else {
                Text(
                    text = "The Ledger",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        actions = {
            // Avatar de usuario
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable { },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "AI",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    )
}

/**
 * Contenido del NavigationDrawer docked (pantallas ≥ 600dp).
 * Transpila el `<nav>` del prototipo HTML con los items de menú.
 */
@Composable
private fun BudgetNavigationDrawerContent(
    selectedRoute: String,
    onRouteSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(vertical = 24.dp)
    ) {
        // Logo / avatar del hogar
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "FL",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Financial Ledger",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Premium Account",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Items de navegación
        drawerItems.forEach { item ->
            NavigationDrawerItem(
                icon = { Icon(imageVector = item.icon, contentDescription = item.label) },
                label = { Text(text = item.label, style = MaterialTheme.typography.labelLarge) },
                selected = selectedRoute == item.route,
                onClick = { onRouteSelected(item.route) },
                modifier = Modifier.padding(horizontal = 12.dp),
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    selectedIconColor = MaterialTheme.colorScheme.primary
                )
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "v2.4.0",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp)
        )
    }
}

/**
 * BottomNavigationBar para layout compacto (< 600dp).
 * Transpila el `<nav>` del final del prototipo HTML.
 */
@Composable
private fun BudgetBottomNav(
    selectedRoute: String,
    onRouteSelected: (String) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.9f),
        tonalElevation = 0.dp
    ) {
        drawerItems.take(4).forEach { item ->
            NavigationBarItem(
                icon = { Icon(imageVector = item.icon, contentDescription = item.label) },
                label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                selected = selectedRoute == item.route,
                onClick = { onRouteSelected(item.route) }
            )
        }
    }
}

/**
 * FAB flotante que abre el modal de captura rápida.
 * Transpila el `<button class="absolute bottom-8 right-8...">` del prototipo.
 */
@Composable
private fun BudgetFAB(onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = "Registrar gasto",
            modifier = Modifier.size(28.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LedgerPane — Panel izquierdo (40%)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Panel que contiene el libro mayor de transacciones recientes.
 *
 * Transpila la `<section class="w-full md:w-[40%]...">` del prototipo HTML.
 * Muestra la lista scrolleable de [TransactionCard] con animación de entrada.
 *
 * @param quincena     Quincena activa para el subtítulo de período.
 * @param transactions Lista de gastos con detalles (JOIN result).
 */
@Composable
fun LedgerPane(
    quincena: QuincenaEntity?,
    transactions: List<ExpenseWithDetails>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        // Encabezado
        Text(
            text = "Transacciones Recientes",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = quincena?.label ?: "Sin quincena activa",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (transactions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Sin gastos registrados.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = transactions,
                    key = { it.expenseId }
                ) { expense ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 4 }
                    ) {
                        TransactionCard(expense = expense)
                    }
                }
            }
        }
    }
}

/**
 * Tarjeta de transacción individual.
 *
 * Transpila el bloque `<div class="bg-surface-container-lowest rounded-lg p-5...">` del HTML.
 * Muestra ícono de categoría (color derivado de categoryColorHex), concepto,
 * wallet como badge, monto y fecha.
 *
 * @param expense Datos del gasto con JOIN resuelto.
 */
@Composable
private fun TransactionCard(
    expense: ExpenseWithDetails,
    modifier: Modifier = Modifier
) {
    // Clase de color basada en el status del gasto
    val amountColor = when (expense.status) {
        "PLANNED" -> MaterialTheme.colorScheme.onSurfaceVariant
        "RECONCILED" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurface
    }

    // Color del ícono de categoría
    val categoryBg = try {
        expense.categoryColorHex?.let { Color(android.graphics.Color.parseColor(it)) }
            ?: MaterialTheme.colorScheme.primaryContainer
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primaryContainer
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Fila superior: ícono + concepto + monto
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Ícono de categoría
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(categoryBg.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = expense.categoryCode.take(2).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = categoryBg
                        )
                    }

                    Column {
                        Text(
                            text = expense.concept,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = expense.paymentMethodName.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.8.sp
                        )
                    }
                }

                Text(
                    text = expense.amountMxn.toMxn(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = amountColor
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Fila inferior: chip de categoría + fecha
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Badge de categoría (transpila chip redondeado del HTML)
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                ) {
                    Text(
                        text = expense.categoryName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = expense.occurredAt.toShortDate(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HealthPane — Panel derecho (60%)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Panel de salud financiera con KPIs y gráfico de distribución por miembro.
 *
 * Transpila la `<section class="w-full md:w-[60%]...">` del prototipo HTML.
 *
 * @param quincena           Quincena activa (para projectedIncomeMxn).
 * @param postedTotal        Total ejecutado (POSTED) en MXN — "Total Spent".
 * @param plannedTotal       Total presupuestado (PLANNED) — "Total Budget".
 * @param balance            = projectedIncome - postedTotal — "Remaining".
 * @param memberDistribution Lista de gastos por miembro para el gráfico.
 */
@Composable
fun HealthPane(
    quincena: QuincenaEntity?,
    postedTotal: Double,
    plannedTotal: Double,
    balance: Double,
    memberDistribution: List<SpendByMember>,
    modifier: Modifier = Modifier
) {
    val totalBudget = quincena?.projectedIncomeMxn ?: (postedTotal + balance)
    val spentPct = if (totalBudget > 0) (postedTotal / totalBudget).coerceIn(0.0, 1.0) else 0.0

    LazyColumn(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Text(
                text = "Salud Financiera",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // ── SummaryCards Grid ─────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryCard(
                    label = "Presupuesto",
                    amount = totalBudget,
                    progress = 1.0f,
                    labelColor = MaterialTheme.colorScheme.primary,
                    progressColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                SummaryCard(
                    label = "Gastado",
                    amount = postedTotal,
                    progress = spentPct.toFloat(),
                    labelColor = MaterialTheme.colorScheme.error,
                    progressColor = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                SummaryCard(
                    label = "Disponible",
                    amount = balance,
                    progress = (1f - spentPct).toFloat(),
                    labelColor = MaterialTheme.colorScheme.secondary,
                    progressColor = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ── Gráfico de distribución por miembro ───────────────────────────────
        if (memberDistribution.isNotEmpty()) {
            item {
                MemberDistributionChart(
                    members = memberDistribution,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Tarjeta de KPI individual — transpila los 3 cards del prototipo HTML.
 *
 * Contiene:
 * - Etiqueta uppercase (labelSmall)
 * - Monto principal (headlineLarge)
 * - LinearProgressIndicator con elevación dinámica
 *
 * @param label         Etiqueta del KPI ("Presupuesto", "Gastado", "Disponible").
 * @param amount        Valor monetario en MXN.
 * @param progress      Fracción 0f-1f para el indicador de progreso.
 * @param labelColor    Color de la etiqueta (varía por rol semántico).
 * @param progressColor Color de la barra de progreso.
 */
@Composable
private fun SummaryCard(
    label: String,
    amount: Double,
    progress: Float,
    labelColor: Color,
    progressColor: Color,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 800),
        label = "progress_$label"
    )

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = amount.toMxn(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainer,
                strokeCap = StrokeCap.Round
            )
        }
    }
}

/**
 * Gráfico de barras verticales de distribución por miembro.
 *
 * Transpila el bloque `<!-- Member Distribution Chart Area -->` del HTML,
 * implementado con el Canvas de Compose en lugar de una librería externa.
 *
 * Paleta de colores por posición (rota si hay más de 4 miembros):
 * - 0: TertiaryContainer (ámbar) — "David 40%"
 * - 1: PrimaryContainer (verde) — "Norma 30%"
 * - 2: SecondaryContainer (verde claro) — "Pau 15%"
 * - 3: SurfaceContainerHigh (gris) — "Compartido 15%"
 *
 * @param members Lista de gastos por miembro (memberId, memberName, totalMxn).
 */
@Composable
fun MemberDistributionChart(
    members: List<SpendByMember>,
    modifier: Modifier = Modifier
) {
    val barColors = listOf(
        TertiaryContainer, PrimaryContainer, SecondaryContainer, SurfaceContainerHigh
    )

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Distribución por Miembro",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            val totalMxn = members.sumOf { it.totalMxn }.let { if (it == 0.0) 1.0 else it }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                members.take(6).forEachIndexed { index, member ->
                    val fraction = (member.totalMxn / totalMxn).toFloat().coerceIn(0f, 1f)
                    val pctDisplay = (fraction * 100).toInt()
                    val barColor = barColors.getOrElse(index) { barColors.last() }
                    val animatedFraction by animateFloatAsState(
                        targetValue = fraction,
                        animationSpec = tween(durationMillis = 800 + index * 100),
                        label = "bar_$index"
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Text(
                            text = member.totalMxn.toMxn(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(56.dp)
                                .weight(animatedFraction.coerceAtLeast(0.02f))
                                .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                                .background(barColor),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Text(
                                text = "$pctDisplay%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = member.memberName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Estados auxiliares de UI
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ErrorContent(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Error al cargar",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Alias de color locales para el gráfico de barras
// (evitar importación directa de Color.kt en el composable)
// ─────────────────────────────────────────────────────────────────────────────

private val TertiaryContainer = Color(0xFFFEBD63)
private val PrimaryContainer = Color(0xFF9CF6B9)
private val SecondaryContainer = Color(0xFFB5FFC3)
private val SurfaceContainerHigh = Color(0xFFE6E8E9)
