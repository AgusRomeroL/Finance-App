package mx.budget.ui.ledger

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import mx.budget.ui.common.ScreenHeader
import mx.budget.ui.common.pressScale
import mx.budget.ui.common.rememberPressInteractionSource
import mx.budget.ui.common.staggeredEntrance
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.budget.data.local.result.ExpenseWithDetails
import mx.budget.ui.theme.financeColors
import mx.budget.ui.tutorial.TutorialKey
import mx.budget.ui.tutorial.tutorialTarget
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Libro Mayor (MVP Fase 3) — historial completo paginado por quincena, con
 * chips de filtro (categoría / wallet) y detalle al tocar una fila (el sheet
 * de detalle reusa la Fase 1: ver, editar y borrar desde aquí).
 */
@Composable
fun LedgerScreen(
    viewModel: LedgerViewModel,
    onBack: () -> Unit,
    onOpenDetail: (ExpenseWithDetails) -> Unit,
    tutorialController: mx.budget.ui.tutorial.TutorialController? = null,
) {
    val money = remember { NumberFormat.getCurrencyInstance(Locale("es", "MX")) }
    val dateFmt = remember { SimpleDateFormat("EEE d MMM", Locale("es", "MX")) }

    val quincenas by viewModel.quincenas.collectAsState()
    val quincena by viewModel.effectiveQuincena.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val wallets by viewModel.wallets.collectAsState()
    val categoryFilter by viewModel.categoryFilter.collectAsState()
    val walletFilter by viewModel.walletFilter.collectAsState()
    val rawRows by viewModel.rows.collectAsState()
    // Tutorial: durante el tour muestra 4 movimientos DEMO (nunca tocan Room). Ver TUTORIAL.md.
    val rows = if (tutorialController?.demoActive == true)
        mx.budget.ui.tutorial.TutorialDemoData.ledgerRows else rawRows

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // Header: back + título + navegación de quincena (patrón del Dashboard).
        // statusBarsPadding es imprescindible: sin él, con la app edge-to-edge los
        // chevrones de quincena caían DENTRO del área tappable del status bar y el
        // sistema se comía los taps (navegación inusable) — P1 de auditoría runtime.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            ScreenHeader(
                eyebrow = quincena?.label,
                title = "Libro Mayor",
                modifier = Modifier.weight(1f),
            )
            // Chevrones ← → sobre la lista ordenada de quincenas.
            val ordered = remember(quincenas) { quincenas.sortedBy { it.startDate } }
            val idx = ordered.indexOfFirst { it.id == quincena?.id }
            IconButton(
                onClick = { if (idx > 0) viewModel.selectQuincena(ordered[idx - 1].id) },
                enabled = idx > 0,
            ) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Quincena anterior") }
            IconButton(
                onClick = { if (idx in 0 until ordered.lastIndex) viewModel.selectQuincena(ordered[idx + 1].id) },
                enabled = idx in 0 until ordered.lastIndex,
            ) { Icon(Icons.Filled.ChevronRight, contentDescription = "Quincena siguiente") }
        }

        // Chips de filtro: categorías con gasto + wallets.
        // TUTORIAL: LED_FILTERS — ver TUTORIAL.md
        val visibleCategories = remember(rows, categories, categoryFilter) {
            val usedCategoryIds = rows.map { it.categoryId }.toSet()
            categories.filter {
                it.id in usedCategoryIds || it.id == categoryFilter
            }
        }
        LazyRow(
            modifier = Modifier.tutorialTarget(TutorialKey.LED_FILTERS, tutorialController),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(visibleCategories, key = { "cat_${it.id}" }) { c ->
                FilterChip(
                    selected = categoryFilter == c.id,
                    onClick = {
                        viewModel.setCategoryFilter(if (categoryFilter == c.id) null else c.id)
                    },
                    label = { Text(c.displayName, maxLines = 1) },
                )
            }
            items(wallets, key = { "wal_${it.id}" }) { w ->
                FilterChip(
                    selected = walletFilter == w.displayName,
                    onClick = {
                        viewModel.setWalletFilter(if (walletFilter == w.displayName) null else w.displayName)
                    },
                    label = { Text(w.displayName, maxLines = 1) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Total visible (con filtros aplicados) — solo POSTED.
        val totalVisible = remember(rows) { rows.filter { it.status == "POSTED" }.sumOf { it.amountMxn } }
        Text(
            "${rows.size} movimientos · ${money.format(totalVisible)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(4.dp))

        if (rows.isEmpty()) {
            Text(
                "Sin movimientos en esta quincena con los filtros actuales.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(rows, key = { _, it -> it.expenseId }) { index, row ->
                    // TUTORIAL: LED_ROWS — ver TUTORIAL.md (ancla = la PRIMERA fila, no la
                    // lista completa: el spotlight de todo el LazyColumn quedaba sobredimensionado).
                    LedgerRow(
                        row = row,
                        money = money,
                        dateFmt = dateFmt,
                        onClick = { onOpenDetail(row) },
                        modifier = Modifier
                            .staggeredEntrance(index)
                            .then(
                                if (index == 0)
                                    Modifier.tutorialTarget(TutorialKey.LED_ROWS, tutorialController)
                                else Modifier
                            ),
                    )
                }
            }
        }
    }
}

/** Fila del libro mayor: capas tonales sin divisores (The Architectural Ledger). */
@Composable
private fun LedgerRow(
    row: ExpenseWithDetails,
    money: NumberFormat,
    dateFmt: SimpleDateFormat,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = rememberPressInteractionSource()
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interactionSource = interaction)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    row.concept,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                )
                Text(
                    "${dateFmt.format(row.occurredAt)} · ${row.categoryName} · ${row.paymentMethodName}" +
                        if (row.status == "PLANNED") " · Planeado" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "−" + money.format(row.amountMxn),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (row.status == "PLANNED") MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.financeColors.expense,
                maxLines = 1,
            )
        }
    }
}
