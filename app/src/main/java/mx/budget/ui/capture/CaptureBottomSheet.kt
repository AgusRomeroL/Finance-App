package mx.budget.ui.capture

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import mx.budget.data.local.entity.CategoryEntity
import mx.budget.data.local.entity.MemberEntity
import mx.budget.data.local.entity.PaymentMethodEntity
import mx.budget.ui.theme.FinancialTone
import mx.budget.ui.theme.amountSemantic
import mx.budget.ui.theme.financeColors
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// CaptureBottomSheet — rediseño "Architectural Ledger" (frame 03)
// ─────────────────────────────────────────────────────────────────────────────
//
// Hoja acotada a 640dp centrada en pantallas grandes (brief D2), con:
//  · Monto + keypad
//  · Categoría = recientes + búsqueda + acordeón de grupos (brief C10)
//  · Atribución en dos dimensiones: "Beneficia a" / "Pagó" (brief C12)
//  · Footer con resumen vivo + CTA Guardar
// Stateless: el CaptureViewModel es el único dueño del estado.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureBottomSheet(
    viewModel: CaptureViewModel? = null,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val displayAmount by (viewModel?.displayAmount ?: dummyStateFlow("0.00")).collectAsState()
    val concept by (viewModel?.concept ?: dummyStateFlow("")).collectAsState()
    val wallets by (viewModel?.wallets ?: dummyStateFlow(emptyList<PaymentMethodEntity>())).collectAsState()
    val selectedWalletId by (viewModel?.selectedWalletId ?: dummyStateFlow<String?>(null)).collectAsState()
    val categories by (viewModel?.categories ?: dummyStateFlow(emptyList<CategoryEntity>())).collectAsState()
    val selectedCategoryId by (viewModel?.selectedCategoryId ?: dummyStateFlow<String?>(null)).collectAsState()
    val members by (viewModel?.members ?: dummyStateFlow(emptyList<MemberEntity>())).collectAsState()
    val beneficiaryShares by (viewModel?.beneficiaryShares ?: dummyStateFlow(emptyMap<String, Int>())).collectAsState()
    val payerShares by (viewModel?.payerShares ?: dummyStateFlow(emptyMap<String, Int>())).collectAsState()
    val attributionSuggestion by (viewModel?.attributionSuggestion
        ?: dummyStateFlow<CaptureSuggestion?>(null)).collectAsState()
    val canRegister by (viewModel?.canRegister ?: dummyStateFlow(false)).collectAsState()
    val notes by (viewModel?.notes ?: dummyStateFlow("")).collectAsState()
    val operationState by (viewModel?.operationState
        ?: dummyStateFlow<CaptureOperationState>(CaptureOperationState.Idle)).collectAsState()

    LaunchedEffect(operationState) {
        when (val s = operationState) {
            is CaptureOperationState.Success -> {
                sheetState.hide(); viewModel?.onOperationStateConsumed(); onDismiss()
            }
            is CaptureOperationState.Error -> {
                scope.launch { snackbarHostState.showSnackbar(s.message) }
                viewModel?.onOperationStateConsumed()
            }
            else -> Unit
        }
    }

    ModalBottomSheet(
        onDismissRequest = { viewModel?.onDismiss(); onDismiss() },
        sheetState = sheetState,
        sheetMaxWidth = 640.dp, // acota y centra en el Fold interno (brief D2)
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.Transparent
        ) { inner ->
            Column(
                modifier = Modifier
                    .padding(inner)
                    .imePadding()
            ) {
                CaptureHeader(onClose = { viewModel?.onDismiss(); onDismiss() })

                // Contenido scrollable — weight(1f) (fill) acota el alto del scroll
                // exactamente al espacio entre header y footer, alineando pintura y
                // área táctil (con fill=false el scroll medía su alto intrínseco y
                // creaba una zona muerta de touch en la parte baja del sheet).
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                ) {
                    AmountCard(
                        displayAmount = displayAmount,
                        concept = concept,
                        onConceptChange = { viewModel?.onConceptChange(it) },
                        onKey = { viewModel?.onNumpadKey(it) },
                        onRegister = { viewModel?.onRegisterExpense() },
                        canRegister = canRegister
                    )
                    // Sugerencia de atribución aprendida del historial (Feature A,
                    // §F.4): visible pero ignorable; "Aplicar" rellena los % sin
                    // auto-confirmar. AnimatedVisibility evita saltos del sheet.
                    AnimatedVisibility(visible = attributionSuggestion != null) {
                        attributionSuggestion?.let { suggestion ->
                            Column {
                                Spacer(Modifier.height(14.dp))
                                AttributionSuggestionChip(
                                    suggestion = suggestion,
                                    members = members,
                                    onApply = { viewModel?.applySuggestion() }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    CategoryCard(
                        categories = categories,
                        selectedCategoryId = selectedCategoryId,
                        onSelect = { viewModel?.onCategorySelected(it) }
                    )
                    Spacer(Modifier.height(14.dp))
                    WalletsCard(
                        wallets = wallets,
                        selectedWalletId = selectedWalletId,
                        onSelect = { viewModel?.onWalletSelected(it) }
                    )
                    Spacer(Modifier.height(14.dp))
                    // Atribución visible = solo "Beneficia a". El pagador (casi
                    // siempre el adulto dueño de la cuenta) se autodefine y vive
                    // bajo "Más" para overridear/repartir.
                    BeneficiaryCard(
                        members = members,
                        beneficiaryShares = beneficiaryShares,
                        onToggle = { viewModel?.onBeneficiaryToggled(it) },
                        onDelta = { id, d -> viewModel?.onBeneficiaryShareDelta(id, d) },
                        onSelectAll = { viewModel?.onSelectAllMembers() },
                        onClearAll = { viewModel?.onClearMembers() }
                    )
                    Spacer(Modifier.height(14.dp))
                    MoreSection(
                        members = members,
                        payerShares = payerShares,
                        onPayerToggle = { viewModel?.onPayerToggled(it) },
                        onPayerDelta = { id, d -> viewModel?.onPayerShareDelta(id, d) },
                        notes = notes,
                        onNotesChange = { viewModel?.onNotesChange(it) }
                    )
                    Spacer(Modifier.height(16.dp))
                }

                CaptureFooter(
                    categories = categories,
                    selectedCategoryId = selectedCategoryId,
                    displayAmount = displayAmount,
                    members = members,
                    beneficiaryShares = beneficiaryShares,
                    enabled = canRegister,
                    isLoading = operationState is CaptureOperationState.Loading,
                    onRegister = { viewModel?.onRegisterExpense() }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CaptureHeader(onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Close, "Cerrar", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                CapLabel("Nuevo movimiento")
                Text(
                    "Capturar gasto",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        // Segmentado Gasto/Ingreso (visual; el VM registra gastos)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    "Gasto",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.financeColors.expense
                )
            }
            Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)) {
                Text(
                    "Ingreso",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Monto + keypad
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AmountCard(
    displayAmount: String,
    concept: String,
    onConceptChange: (String) -> Unit,
    onKey: (String) -> Unit,
    onRegister: () -> Unit,
    canRegister: Boolean
) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CapLabel("Monto")
            CapLabel("MXN")
        }
        Spacer(Modifier.height(8.dp))
        // Cifra: entero en onSurface, decimales atenuados
        val (intPart, decPart) = splitAmount(displayAmount)
        Row(verticalAlignment = Alignment.Top) {
            Text(
                "$",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                intPart,
                style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Light, fontSize = 64.sp, letterSpacing = (-1).sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                decPart,
                style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Light, fontSize = 64.sp, letterSpacing = (-1).sp),
                color = MaterialTheme.colorScheme.outlineVariant,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(12.dp))
        // Concepto — junto al monto (no escondido tras "Más"); opcional.
        OutlinedTextField(
            value = concept,
            onValueChange = onConceptChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Concepto (opcional)", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )
        Spacer(Modifier.height(16.dp))
        Keypad(onKey = onKey, onRegister = onRegister, canRegister = canRegister)
    }
}

@Composable
private fun Keypad(onKey: (String) -> Unit, onRegister: () -> Unit, canRegister: Boolean) {
    val rows = listOf(
        listOf("1", "2", "3", "DEL"),
        listOf("4", "5", "6", "."),
        listOf("7", "8", "9", "000")
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    KeypadKey(key, Modifier.weight(1f)) {
                        if (key == "000") repeat(3) { onKey("0") } else onKey(key)
                    }
                }
            }
        }
        // Última fila: 0 ancho + ✓ confirmar
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            KeypadKey("0", Modifier.weight(3f)) { onKey("0") }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (canRegister) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                    .clickable(enabled = canRegister, onClick = onRegister),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Check, "Registrar",
                    tint = if (canRegister) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun KeypadKey(key: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (key == "DEL") {
            Icon(Icons.AutoMirrored.Filled.Backspace, "Borrar", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        } else {
            Text(
                key,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Normal),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Categoría: recientes + búsqueda + acordeón
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryCard(
    categories: List<CategoryEntity>,
    selectedCategoryId: String?,
    onSelect: (String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var expandedGroup by rememberSaveable { mutableStateOf<String?>(null) }

    val leaves = remember(categories) { categories.filter { it.parentId != null } }
    val parentsById = remember(categories) { categories.filter { it.parentId == null }.associateBy { it.id } }
    val groups = remember(leaves) { leaves.groupBy { it.parentId } }
    val recents = remember(leaves) { leaves.sortedBy { it.sortOrder }.take(5) }
    val selected = leaves.firstOrNull { it.id == selectedCategoryId }

    val normalizedQuery = query.trim().lowercase()
    val matches = if (normalizedQuery.isBlank()) emptyList()
    else leaves.filter { it.displayName.lowercase().contains(normalizedQuery) }.take(8)

    Card {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                CapLabel("Categoría")
                Spacer(Modifier.height(3.dp))
                Text(
                    "Empieza por las recientes o busca",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected != null) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        selected.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1, softWrap = false
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Recientes
        if (recents.isNotEmpty()) {
            CapMicroLabel("Recientes")
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recents.forEach { cat -> CategoryChip(cat.displayName, cat.id == selectedCategoryId) { onSelect(cat.id) } }
            }
            Spacer(Modifier.height(14.dp))
        }

        // Búsqueda
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Buscar categoría…", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )

        Spacer(Modifier.height(12.dp))

        if (normalizedQuery.isNotBlank()) {
            // Resultados de búsqueda
            if (matches.isEmpty()) {
                Text("Sin coincidencias", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    matches.forEach { cat -> CategoryChip(cat.displayName, cat.id == selectedCategoryId) { onSelect(cat.id) } }
                }
            }
        } else {
            // Acordeón de grupos
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                groups.forEach { (parentId, children) ->
                    val parentName = parentsById[parentId]?.displayName ?: "Otros"
                    val isOpen = expandedGroup == parentId
                    AccordionGroup(
                        title = parentName,
                        count = children.size,
                        open = isOpen,
                        onToggle = { expandedGroup = if (isOpen) null else parentId },
                        children = children,
                        selectedId = selectedCategoryId,
                        onSelect = onSelect
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccordionGroup(
    title: String,
    count: Int,
    open: Boolean,
    onToggle: () -> Unit,
    children: List<CategoryEntity>,
    selectedId: String?,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onToggle)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            Icon(
                if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)
            )
        }
        AnimatedVisibility(visible = open) {
            Column {
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    children.forEach { cat -> CategoryChip(cat.displayName, cat.id == selectedId) { onSelect(cat.id) } }
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Wallets (fuente de pago)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WalletsCard(
    wallets: List<PaymentMethodEntity>,
    selectedWalletId: String?,
    onSelect: (String) -> Unit
) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CapLabel("Fuente de pago")
            CapLabel("Saldo")
        }
        Spacer(Modifier.height(12.dp))
        if (wallets.isEmpty()) {
            Text("Sin métodos de pago.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                wallets.take(6).forEach { w ->
                    WalletCard(w, w.id == selectedWalletId) { onSelect(w.id) }
                }
            }
        }
    }
}

@Composable
private fun WalletCard(wallet: PaymentMethodEntity, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val sub = if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .width(132.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Icon(walletKindIcon(wallet.kind), null, tint = fg, modifier = Modifier.size(20.dp))
            if (selected) Icon(Icons.Filled.CheckCircle, "Seleccionado", tint = fg, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(walletKindLabel(wallet.kind).uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = sub, letterSpacing = 1.2.sp, maxLines = 1)
        Text(wallet.displayName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("$" + wallet.currentBalanceMxn.toGrouped(), style = MaterialTheme.typography.bodySmall, color = sub, maxLines = 1)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Atribución: Beneficia a (visible) · Pagó (vive en "Más")
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BeneficiaryCard(
    members: List<MemberEntity>,
    beneficiaryShares: Map<String, Int>,
    onToggle: (String) -> Unit,
    onDelta: (String, Int) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit
) {
    val complete = beneficiaryShares.isNotEmpty() && beneficiaryShares.values.sum() == 100

    Card {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                CapLabel("Beneficia a")
                Spacer(Modifier.height(3.dp))
                Text("Quién consume este gasto", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val warn = amountSemantic(FinancialTone.WARNING)
            val inc = amountSemantic(FinancialTone.INCOME)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (complete) inc.container else warn.container)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (complete) Icons.Filled.Check else Icons.Filled.PriorityHigh, null,
                    tint = if (complete) inc.onContainer else warn.onContainer, modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (complete) "100 % asignado" else "Falta asignar",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (complete) inc.onContainer else warn.onContainer,
                    maxLines = 1, softWrap = false
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        AttributionDimension(
            icon = Icons.Filled.Favorite,
            iconTint = MaterialTheme.financeColors.income,
            iconBg = MaterialTheme.financeColors.incomeContainer,
            title = "Beneficia a · consume",
            members = members,
            shares = beneficiaryShares,
            onToggle = onToggle,
            onDelta = onDelta,
            onSelectAll = onSelectAll,
            onClearAll = onClearAll
        )
    }
}

// El editor de % por miembro (AttributionDimension + chips) vive ahora en
// AttributionShareEditor.kt (mismo paquete), compartido con la pantalla de
// "Revisión de atribuciones" (Feature B).

// ─────────────────────────────────────────────────────────────────────────────
// "Más" — Pagó (default = adulto dueño de la cuenta) + fecha (hoy) + notas
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoreSection(
    members: List<MemberEntity>,
    payerShares: Map<String, Int>,
    onPayerToggle: (String) -> Unit,
    onPayerDelta: (String, Int) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit
) {
    // Abre por defecto si ya hay nota (p.ej. prellenada desde una captura NL), para
    // que el usuario la vea sin tener que expandir.
    var open by rememberSaveable(notes.isNotBlank()) { mutableStateOf(notes.isNotBlank()) }
    val payerNames = members.filter { it.id in payerShares.keys }.joinToString(", ") { it.displayName }
    val today = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale("es", "MX"))) }
    val payerOk = payerShares.isNotEmpty() && payerShares.values.sum() == 100

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 22.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Tune, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Más", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        (if (payerNames.isNotEmpty()) "Pagó: $payerNames" else "Pagó: definir") + " · Hoy, $today · Notas",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (payerOk) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.financeColors.warning,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        AnimatedVisibility(visible = open) {
            Column {
                AttributionDimension(
                    icon = Icons.Filled.Payments,
                    iconTint = MaterialTheme.colorScheme.onSurface,
                    iconBg = MaterialTheme.colorScheme.surfaceContainerHighest,
                    title = "Pagó · adelantó",
                    members = members,
                    shares = payerShares,
                    onToggle = onPayerToggle,
                    onDelta = onPayerDelta,
                    onSelectAll = null,
                    onClearAll = null
                )
                Spacer(Modifier.height(18.dp))
                // Fecha — por defecto hoy (día del registro)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Event, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(15.dp)) }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        CapMicroLabel("Fecha")
                        Text("Hoy · $today", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Spacer(Modifier.height(18.dp))
                // Notas libres (detalle que no es el concepto). Las captura NL puede
                // prellenarlas desde la frase ("nota: aniversario").
                CapMicroLabel("Notas")
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = onNotesChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Detalle opcional…") },
                    minLines = 1,
                    maxLines = 3,
                )
                Spacer(Modifier.height(12.dp))
                Text("Elegir otra fecha: próximamente.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Footer: resumen vivo + Guardar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CaptureFooter(
    categories: List<CategoryEntity>,
    selectedCategoryId: String?,
    displayAmount: String,
    members: List<MemberEntity>,
    beneficiaryShares: Map<String, Int>,
    enabled: Boolean,
    isLoading: Boolean,
    onRegister: () -> Unit
) {
    val catName = categories.firstOrNull { it.id == selectedCategoryId }?.displayName
    val beneficiaries = members.filter { it.id in beneficiaryShares.keys }.joinToString(", ") { it.displayName }
    val summary = buildString {
        append(catName ?: "Elige categoría")
        if (displayAmount != "0.00" && displayAmount != "0") append(" · $").append(displayAmount)
        if (beneficiaries.isNotEmpty()) append(" · ").append(beneficiaries)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            CapMicroLabel("Resumen")
            Spacer(Modifier.height(2.dp))
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .height(52.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(enabled = enabled && !isLoading, onClick = onRegister)
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            } else {
                Text(
                    "Guardar",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers de UI
// ─────────────────────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────────────────────────────────────
// Chip de sugerencia de atribución (Feature A, §F.4)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Tarjeta tonal con la atribución inferida del historial. Combina BENEFICIARY y
 * PAYER en una sola superficie, pero solo muestra el lado que superó el umbral
 * (el otro puede ser `null` en el caso mixto). Redundancia no-cromática: icono
 * de sugerencia + etiquetas textuales explícitas, no depende del color.
 */
@Composable
private fun AttributionSuggestionChip(
    suggestion: CaptureSuggestion,
    members: List<MemberEntity>,
    onApply: () -> Unit,
) {
    fun nameOf(id: String): String = members.firstOrNull { it.id == id }?.displayName ?: "—"

    // "Santi" si un solo miembro al 100%; "Santi 60% · Norma 40%" si es repartido.
    fun describe(distribution: Map<String, Int>): String {
        val entries = distribution.entries.sortedByDescending { it.value }
        return if (entries.size == 1) nameOf(entries.first().key)
        else entries.joinToString(" · ") { "${nameOf(it.key)} ${it.value / 100}%" }
    }

    val parts = buildList {
        suggestion.beneficiary?.let { add("Beneficia a ${describe(it.distribution)}") }
        suggestion.payer?.let { add("Pagó ${describe(it.distribution)}") }
    }
    val basis = suggestion.beneficiary?.basis ?: suggestion.payer?.basis ?: ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.AutoAwesome,
            contentDescription = "Sugerencia",
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                parts.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.SemiBold
            )
            if (basis.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    basis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "Aplicar",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onApply)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

/** Tarjeta-sección con superficie tonal y radio 28dp (regla No-Line). */
@Composable
private fun Card(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(22.dp),
        content = content
    )
}

@Composable
private fun CapLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.6.sp
    )
}

@Composable
private fun CapMicroLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.4.sp
    )
}

private val grouping: NumberFormat = NumberFormat.getIntegerInstance(Locale("es", "MX"))
private fun Double.toGrouped(): String = grouping.format(this.toLong())

/** Separa "1,840.00" → ("1,840", ".00"). */
private fun splitAmount(display: String): Pair<String, String> {
    val raw = display.replace(",", "")
    val dot = raw.indexOf('.')
    val intRaw = if (dot >= 0) raw.substring(0, dot) else raw
    val decRaw = if (dot >= 0) raw.substring(dot) else ""
    val intGrouped = intRaw.toLongOrNull()?.let { grouping.format(it) } ?: intRaw
    val dec = when {
        decRaw.isEmpty() -> ""
        decRaw == "." -> "."
        else -> decRaw
    }
    return intGrouped to dec
}

private fun walletKindIcon(kind: String): ImageVector = when (kind) {
    "DEBIT_ACCOUNT" -> Icons.Filled.AccountBalanceWallet
    "CREDIT_CARD", "DEPARTMENT_STORE_CARD" -> Icons.Filled.CreditCard
    "BNPL_INSTALLMENT" -> Icons.Filled.SwapHoriz
    "DIGITAL_WALLET" -> Icons.Filled.AccountBalanceWallet
    "CASH" -> Icons.Filled.Payments
    "EMPLOYER_SAVINGS_FUND" -> Icons.Filled.Savings
    else -> Icons.Filled.AccountBalanceWallet
}

private fun walletKindLabel(kind: String): String = when (kind) {
    "DEBIT_ACCOUNT" -> "Débito"
    "CREDIT_CARD" -> "Crédito"
    "DEPARTMENT_STORE_CARD" -> "Tienda"
    "BNPL_INSTALLMENT" -> "A plazos"
    "DIGITAL_WALLET" -> "Digital"
    "CASH" -> "Efectivo"
    "EMPLOYER_SAVINGS_FUND" -> "Ahorro"
    else -> kind
}

private fun <T> dummyStateFlow(value: T): kotlinx.coroutines.flow.StateFlow<T> =
    kotlinx.coroutines.flow.MutableStateFlow(value)
