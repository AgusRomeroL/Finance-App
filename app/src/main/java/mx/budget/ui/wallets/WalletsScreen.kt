package mx.budget.ui.wallets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.budget.data.local.entity.PaymentMethodEntity
import mx.budget.data.local.result.ExpenseWithDetails
import mx.budget.data.local.result.TransferWithNames
import mx.budget.data.local.result.WalletBalanceInfo
import mx.budget.ui.dashboard.iconForCategory
import mx.budget.ui.theme.FinancialTone
import mx.budget.ui.theme.amountSemantic
import mx.budget.ui.theme.financeColors
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

// ── Helpers locales (mismo formato que el resto de las pantallas) ─────────────

private val mxnInt: NumberFormat = NumberFormat.getIntegerInstance(Locale("es", "MX"))
private fun Double.toMxn(): String = "$" + mxnInt.format(this.toLong())

private val dayFmt = java.text.SimpleDateFormat("EEE d MMM", Locale("es", "MX"))
private fun formatDay(epochMillis: Long): String =
    dayFmt.format(Date(epochMillis)).replaceFirstChar { it.uppercase() }

/**
 * Limpia la entrada de un monto: conserva dígitos, un solo punto decimal y hasta
 * 2 decimales. Evita entradas malformadas ("1.2.3") que rompían el parseo.
 */
private fun sanitizeAmountInput(raw: String): String {
    val filtered = raw.filter { it.isDigit() || it == '.' }
    val dot = filtered.indexOf('.')
    if (dot < 0) return filtered
    val intPart = filtered.substring(0, dot)
    val decPart = filtered.substring(dot + 1).filter { it.isDigit() }.take(2)
    return "$intPart.$decPart"
}

/** Kinds líquidos: el saldo es disponible, no deuda. */
private val LIQUID_KINDS = setOf("DEBIT_ACCOUNT", "CASH", "DIGITAL_WALLET", "EMPLOYER_SAVINGS_FUND")

private fun isLiquid(kind: String): Boolean = kind in LIQUID_KINDS

/** Secciones de la lista, agrupadas por tipo (kind), en orden de presentación. */
private data class WalletSection(val label: String, val kinds: Set<String>)

private val WALLET_SECTIONS = listOf(
    WalletSection("Disponible", LIQUID_KINDS),
    WalletSection("Tarjetas de crédito", setOf("CREDIT_CARD")),
    WalletSection("Tiendas departamentales", setOf("DEPARTMENT_STORE_CARD")),
    WalletSection("Meses sin intereses", setOf("BNPL_INSTALLMENT")),
)

private fun kindIcon(kind: String): ImageVector = when (kind) {
    "DEBIT_ACCOUNT" -> Icons.Filled.AccountBalance
    "CASH" -> Icons.Filled.Payments
    "DIGITAL_WALLET" -> Icons.Filled.AccountBalanceWallet
    "EMPLOYER_SAVINGS_FUND" -> Icons.Filled.Savings
    "CREDIT_CARD", "DEPARTMENT_STORE_CARD", "BNPL_INSTALLMENT" -> Icons.Filled.CreditCard
    else -> Icons.Filled.AccountBalanceWallet
}

/**
 * Pantalla Wallets ("Cuentas"): saldos por wallet agrupados por tipo + KPIs, y
 * al tocar un wallet sus movimientos (gastos cargados). Solo lectura.
 *
 * Adaptativa: en compacto los movimientos abren un [ModalBottomSheet]; en
 * expandido (≥600dp) un panel lateral muestra el detalle del wallet seleccionado.
 * Movimiento de resorte (Material Expressive) en barras y aparición de paneles.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletsScreen(
    viewModel: WalletsViewModel,
    windowWidthDp: Dp,
    onBack: () -> Unit,
) {
    val balances by viewModel.balances.collectAsState()
    val revolvingDebt by viewModel.revolvingDebt.collectAsState()
    val liquidTotal by viewModel.liquidTotal.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val movements by viewModel.movements.collectAsState()
    val entities by viewModel.entities.collectAsState()
    val members by viewModel.members.collectAsState()
    val transfers by viewModel.transfers.collectAsState()

    val expanded = windowWidthDp >= 600.dp

    // Formulario de alta/edición: null = cerrado; Some(null) = nuevo; Some(wallet) = editar.
    var showForm by remember { mutableStateOf(false) }
    var formInitial by remember { mutableStateOf<PaymentMethodEntity?>(null) }
    val openEdit: (String) -> Unit = { id ->
        formInitial = entities.firstOrNull { it.id == id }
        showForm = true
    }
    // Diálogo de conciliación manual (RF-42) sobre el wallet seleccionado.
    var showReconcile by remember { mutableStateOf(false) }
    // Hoja de transferencia / pago de tarjeta (RF-41).
    var showTransfer by remember { mutableStateOf(false) }
    // Hoja de registrar ingreso (acredita el wallet).
    var showIncome by remember { mutableStateOf(false) }
    // Transferencia marcada para borrar (confirma antes de revertir saldos).
    var transferToDelete by remember { mutableStateOf<TransferWithNames?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { formInitial = null; showForm = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Nueva cuenta") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .statusBarsPadding(),
        ) {
            Header(
                onBack = onBack,
                onIncome = { showIncome = true },
                onTransfer = { showTransfer = true },
            )

            if (expanded) {
                Row(modifier = Modifier.fillMaxSize()) {
                    WalletList(
                        balances = balances,
                        liquidTotal = liquidTotal,
                        revolvingDebt = revolvingDebt,
                        selectedId = selected?.paymentMethodId,
                        onSelect = viewModel::selectWallet,
                        onEdit = openEdit,
                        transfers = transfers,
                        onTransferLongPress = { transferToDelete = it },
                        modifier = Modifier.weight(0.62f).fillMaxHeight(),
                    )
                    DetailPane(
                        wallet = selected,
                        movements = movements,
                        onEdit = openEdit,
                        onReconcile = { showReconcile = true },
                        modifier = Modifier.weight(0.38f).fillMaxHeight(),
                    )
                }
            } else {
                WalletList(
                    balances = balances,
                    liquidTotal = liquidTotal,
                    revolvingDebt = revolvingDebt,
                    selectedId = null,
                    onSelect = viewModel::selectWallet,
                    onEdit = openEdit,
                    transfers = transfers,
                    onTransferLongPress = { transferToDelete = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (showForm) {
        WalletFormSheet(
            initial = formInitial,
            householdId = viewModel.household,
            onSave = { wallet ->
                viewModel.saveWallet(wallet)
                showForm = false
            },
            onDismiss = { showForm = false },
        )
    }

    val toReconcile = selected
    if (showReconcile && toReconcile != null) {
        ReconcileDialog(
            wallet = toReconcile,
            onConfirm = { newBalance ->
                viewModel.reconcileWallet(toReconcile.paymentMethodId, newBalance)
                showReconcile = false
            },
            onDismiss = { showReconcile = false },
        )
    }

    if (showTransfer) {
        WalletTransferSheet(
            wallets = entities,
            onSave = { fromId, toId, amount, note ->
                viewModel.recordTransfer(fromId, toId, amount, note, System.currentTimeMillis())
                showTransfer = false
            },
            onDismiss = { showTransfer = false },
        )
    }

    if (showIncome) {
        IncomeSheet(
            wallets = entities,
            members = members,
            onSave = { walletId, memberId, amount, label, dateIso ->
                viewModel.recordIncome(walletId, memberId, amount, label, dateIso)
                showIncome = false
            },
            onDismiss = { showIncome = false },
        )
    }

    transferToDelete?.let { t ->
        DeleteTransferDialog(
            transfer = t,
            onConfirm = { viewModel.deleteTransfer(t.id); transferToDelete = null },
            onDismiss = { transferToDelete = null },
        )
    }

    // Compacto: detalle en bottom sheet.
    if (!expanded && selected != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { viewModel.clearSelection() },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            MovementsPanel(
                wallet = selected,
                movements = movements,
                onEdit = { selected?.let { openEdit(it.paymentMethodId) } },
                onReconcile = { showReconcile = true },
                // Acota la altura: el LazyColumn interno necesita un máximo finito
                // dentro del ColumnScope del sheet (si no, mide con altura infinita).
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Header(onBack: () -> Unit, onIncome: () -> Unit, onTransfer: () -> Unit) {
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
                "CUENTAS",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.6.sp,
            )
            Text(
                "Saldos",
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light, fontSize = 28.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onIncome),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.TrendingUp, "Registrar ingreso",
                tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onTransfer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.SwapHoriz, "Transferir o pagar tarjeta",
                tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun WalletList(
    balances: List<WalletBalanceInfo>,
    liquidTotal: Double,
    revolvingDebt: Double,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onEdit: (String) -> Unit,
    transfers: List<TransferWithNames>,
    onTransferLongPress: (TransferWithNames) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Agrupa por sección preservando el orden definido; el resto cae en "Otras".
    val grouped = remember(balances) {
        val matched = WALLET_SECTIONS.mapNotNull { sec ->
            val items = balances.filter { it.kind in sec.kinds }
            if (items.isEmpty()) null else sec.label to items
        }
        val known = WALLET_SECTIONS.flatMap { it.kinds }.toSet()
        val rest = balances.filter { it.kind !in known }
        if (rest.isEmpty()) matched else matched + ("Otras cuentas" to rest)
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "kpis") {
            KpiRow(liquidTotal = liquidTotal, revolvingDebt = revolvingDebt)
            Spacer(Modifier.height(6.dp))
        }

        if (balances.isEmpty()) {
            item(key = "empty") { EmptyState() }
        }

        grouped.forEach { (label, items) ->
            item(key = "header_$label") {
                SectionHeader(label)
            }
            items(items, key = { it.paymentMethodId }) { w ->
                WalletCard(
                    wallet = w,
                    selected = w.paymentMethodId == selectedId,
                    onClick = { onSelect(w.paymentMethodId) },
                    onLongClick = { onEdit(w.paymentMethodId) },
                    modifier = Modifier.animateItem(
                        fadeInSpec = spring(stiffness = 380f),
                        fadeOutSpec = spring(stiffness = 380f),
                        placementSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
                    ),
                )
            }
        }

        // Historial de transferencias (RF-41). Household-wide; long-press para borrar.
        if (transfers.isNotEmpty()) {
            item(key = "header_transfers") {
                SectionHeader("Transferencias")
            }
            items(transfers, key = { it.id }) { t ->
                TransferRow(
                    transfer = t,
                    onLongClick = { onTransferLongPress(t) },
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

@Composable
private fun KpiRow(liquidTotal: Double, revolvingDebt: Double) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        KpiCard(
            label = "Disponible",
            amount = liquidTotal,
            tone = FinancialTone.INCOME,
            modifier = Modifier.weight(1f),
        )
        KpiCard(
            label = "Deuda de tarjetas",
            amount = revolvingDebt,
            tone = FinancialTone.EXPENSE,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun KpiCard(label: String, amount: Double, tone: FinancialTone, modifier: Modifier = Modifier) {
    val sem = amountSemantic(tone)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(sem.container)
            .padding(16.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = sem.onContainer,
            maxLines = 2,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            amount.toMxn(),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = sem.onContainer,
            maxLines = 1,
        )
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        label.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WalletCard(
    wallet: WalletBalanceInfo,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer
    else MaterialTheme.colorScheme.surfaceContainer
    val liquid = isLiquid(wallet.kind)
    // El saldo: líquido = neutral (disponible); crédito/deuda = tono de gasto.
    val tone = if (liquid) FinancialTone.NEUTRAL else FinancialTone.EXPENSE
    val sem = amountSemantic(tone)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    kindIcon(wallet.kind),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                wallet.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 2,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                wallet.balance.toMxn(),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = sem.color,
                maxLines = 1,
            )
        }

        // Barra de utilización para wallets con límite de crédito.
        val limit = wallet.creditLimit
        if (limit != null && limit > 0) {
            Spacer(Modifier.height(12.dp))
            UtilizationBar(pct = wallet.utilizationPct ?: 0.0, limit = limit)
        }
    }
}

@Composable
private fun UtilizationBar(pct: Double, limit: Double) {
    val fraction = (pct / 100.0).coerceIn(0.0, 1.0).toFloat()
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "utilization",
    )
    val fc = MaterialTheme.financeColors
    val barColor = when {
        pct >= 70.0 -> fc.expense
        pct >= 50.0 -> fc.warning
        else -> fc.income
    }
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated)
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(barColor),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "${pct.toInt()}% de ${limit.toMxn()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** Panel lateral (expandido): contenedor tonal con el detalle del wallet. */
@Composable
private fun DetailPane(
    wallet: WalletBalanceInfo?,
    movements: List<ExpenseWithDetails>,
    onEdit: (String) -> Unit,
    onReconcile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(start = 4.dp, end = 20.dp, top = 8.dp, bottom = 28.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        AnimatedVisibility(
            visible = wallet != null,
            enter = fadeIn(spring(stiffness = 380f)),
            exit = fadeOut(spring(stiffness = 380f)),
        ) {
            MovementsPanel(
                wallet = wallet,
                movements = movements,
                onEdit = { wallet?.let { onEdit(it.paymentMethodId) } },
                onReconcile = onReconcile,
                modifier = Modifier.fillMaxSize().padding(16.dp),
            )
        }
        if (wallet == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Selecciona una cuenta",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MovementsPanel(
    wallet: WalletBalanceInfo?,
    movements: List<ExpenseWithDetails>,
    onEdit: () -> Unit,
    onReconcile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    wallet?.displayName ?: "",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                )
                Text(
                    wallet?.balance?.toMxn() ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HeaderAction(Icons.Filled.Balance, "Conciliar saldo", onReconcile)
            Spacer(Modifier.width(8.dp))
            HeaderAction(Icons.Filled.Edit, "Editar cuenta", onEdit)
        }
        Spacer(Modifier.height(12.dp))
        if (movements.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Sin movimientos en esta cuenta.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(movements, key = { it.expenseId }) { m -> MovementRow(m) }
            }
        }
    }
}

@Composable
private fun MovementRow(item: ExpenseWithDetails) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                iconForCategory(item.categoryCode),
                contentDescription = item.categoryName,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.concept,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
            )
            Text(
                "${formatDay(item.occurredAt)} · ${item.categoryName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            item.amountMxn.toMxn(),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/**
 * Fila de una transferencia en el historial (RF-41). Muestra origen → destino,
 * fecha, nota y (si el destino es crédito) la etiqueta "Pago de tarjeta".
 * Long-press dispara la confirmación de borrado.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransferRow(
    transfer: TransferWithNames,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val credit = !isLiquid(transfer.toKind)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .combinedClickable(onClick = {}, onLongClick = onLongClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.SwapHoriz,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${transfer.fromName} → ${transfer.toName}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
            )
            val subtitle = buildString {
                append(formatDay(transfer.occurredAt))
                if (credit) append(" · Pago de tarjeta")
                transfer.note?.takeIf { it.isNotBlank() }?.let { append(" · "); append(it) }
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            transfer.amountMxn.toMxn(),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/** Confirma el borrado de una transferencia (revierte el saldo de ambas cuentas). */
@Composable
private fun DeleteTransferDialog(
    transfer: TransferWithNames,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Borrar transferencia") },
        text = {
            Text(
                "Se eliminará la transferencia de ${transfer.fromName} a ${transfer.toName} " +
                    "por ${transfer.amountMxn.toMxn()} y se revertirá el saldo de ambas cuentas.",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Borrar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun HeaderAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Conciliación manual (RF-42): fija el saldo real del wallet. En crédito pide la
 * deuda real. Reusa el patrón de `EditAmountDialog` del calendario.
 */
@Composable
private fun ReconcileDialog(
    wallet: WalletBalanceInfo,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val credit = !isLiquid(wallet.kind)
    var text by remember { mutableStateOf(wallet.balance.toLong().toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Conciliar saldo") },
        text = {
            Column {
                Text(
                    wallet.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { new -> text = sanitizeAmountInput(new) },
                    label = { Text(if (credit) "Deuda real (MXN)" else "Saldo real (MXN)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { text.toDoubleOrNull()?.let(onConfirm) },
                enabled = text.toDoubleOrNull() != null,
            ) { Text("Conciliar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.AccountBalanceWallet,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "No hay cuentas registradas.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
