package mx.budget.ui.capture

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import mx.budget.data.local.entity.MemberEntity
import mx.budget.data.local.entity.PaymentMethodEntity

// ─────────────────────────────────────────────────────────────────────────────
// CaptureBottomSheet — Modal expansible de captura de gasto
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Modal expansible de captura rápida de gasto.
 *
 * Transpila `ui_reference/focus_action_hybrid_layout_a/code.html` a
 * Jetpack Compose con Material 3 Expressive.
 *
 * **Estructura vertical** (de arriba a abajo):
 * 1. **Header** — título "Quick Capture" + botón close (IconButton)
 * 2. **AmountDisplay** — importe grande + campo de concepto underlined
 * 3. **SourceCarousel** — [LazyRow] de [WalletCard] snap-scrolleables
 * 4. **AttributionChips** — [FlowRow] de [FilterChip] para beneficiarios
 * 5. **NumPad** — Grid 3×4 de teclas numéricas
 * 6. **RegistrarButton** — CTA full-width con gradiente esmeralda
 *
 * El [CaptureViewModel] es el único propietario del estado; este Composable
 * es completamente stateless (todos los valores vienen como parámetros o del VM).
 *
 * @param viewModel  ViewModel con el estado del formulario e inserción atómica.
 *                   Por defecto usa una instancia dummy para preview.
 * @param onDismiss  Callback invocado cuando el usuario cierra el sheet
 *                   (botón close, gesto de swipe o registro exitoso).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CaptureBottomSheet(
    viewModel: CaptureViewModel? = null,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // ── Observación de estado del ViewModel ───────────────────────────────────
    val displayAmount by (viewModel?.displayAmount ?: dummyStateFlow("0.00"))
        .collectAsState()
    val concept by (viewModel?.concept ?: dummyStateFlow(""))
        .collectAsState()
    val wallets by (viewModel?.wallets ?: dummyStateFlow(emptyList<PaymentMethodEntity>()))
        .collectAsState()
    val selectedWalletId by (viewModel?.selectedWalletId ?: dummyStateFlow<String?>(null))
        .collectAsState()
    val members by (viewModel?.members ?: dummyStateFlow(emptyList<MemberEntity>()))
        .collectAsState()
    val selectedMemberIds by (viewModel?.selectedMemberIds ?: dummyStateFlow(emptySet<String>()))
        .collectAsState()
    val canRegister by (viewModel?.canRegister ?: dummyStateFlow(true))
        .collectAsState()
    val operationState by (viewModel?.operationState
        ?: dummyStateFlow<CaptureOperationState>(CaptureOperationState.Idle))
        .collectAsState()

    // ── Efectos secundarios de operationState ─────────────────────────────────
    LaunchedEffect(operationState) {
        when (val state = operationState) {
            is CaptureOperationState.Success -> {
                // Cerrar el sheet tras un registro exitoso
                sheetState.hide()
                viewModel?.onOperationStateConsumed()
                onDismiss()
            }
            is CaptureOperationState.Error -> {
                scope.launch {
                    snackbarHostState.showSnackbar(state.message)
                }
                viewModel?.onOperationStateConsumed()
            }
            else -> Unit
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            viewModel?.onDismiss()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            // Drag handle personalizado — pill pill esmeralda
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            )
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
            ) {
                // ── 1. Header ─────────────────────────────────────────────────
                CaptureHeader(
                    onClose = {
                        viewModel?.onDismiss()
                        onDismiss()
                    }
                )

                // ── 2. Display de importe + concepto ──────────────────────────
                AmountSection(
                    displayAmount = displayAmount,
                    concept = concept,
                    onConceptChange = { viewModel?.onConceptChange(it) }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // ── 3. SourceCarousel (wallets) ───────────────────────────────
                SourceSection(
                    wallets = wallets,
                    selectedWalletId = selectedWalletId,
                    onWalletSelected = { viewModel?.onWalletSelected(it) }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // ── 4. Attribution FilterChips (miembros) ─────────────────────
                AttributionSection(
                    members = members,
                    selectedMemberIds = selectedMemberIds,
                    onMemberToggled = { viewModel?.onMemberToggled(it) },
                    onSelectAll = { viewModel?.onSelectAllMembers() }
                )

                Spacer(modifier = Modifier.height(24.dp))

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // ── 5. NumPad ────────────────────────────────────────────────
                NumPadSection(
                    onKey = { key -> viewModel?.onNumpadKey(key) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ── 6. Botón Registrar ────────────────────────────────────────
                RegistrarButton(
                    enabled = canRegister,
                    isLoading = operationState is CaptureOperationState.Loading,
                    onClick = { viewModel?.onRegisterExpense() }
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-composables privados del modal
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Header del modal: título centrado + botón close.
 * Transpila el `<header>` del focus_action_hybrid_layout_a.
 */
@Composable
private fun CaptureHeader(onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Cerrar",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        Text(
            text = "Quick Capture",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary
        )

        // Spacer simétrico para centrar el título
        Box(modifier = Modifier.size(48.dp))
    }
}

/**
 * Display del importe y campo de concepto.
 *
 * Transpila la `<section>` de Amount del prototipo HTML:
 * - Símbolo `$` en color primary grande (displayLarge)
 * - Importe en texto gigante readonly (displayLarge)
 * - Campo concepto tipo underline (InputDecorationBox con border-bottom)
 */
@Composable
private fun AmountSection(
    displayAmount: String,
    concept: String,
    onConceptChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Etiqueta "AMOUNT"
        Text(
            text = "MONTO",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.8.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Importe grande con símbolo
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$",
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Light),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = formatAmount(displayAmount),
                style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Light),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Campo de concepto — decoración tipo underline del HTML
        OutlinedTextField(
            value = concept,
            onValueChange = onConceptChange,
            placeholder = {
                Text(
                    text = "Concepto",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            ),
            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
            modifier = Modifier
                .width(220.dp)
                .border(
                    width = 2.dp,
                    brush = Brush.verticalGradient(
                        listOf(Color.Transparent, MaterialTheme.colorScheme.primary)
                    ),
                    shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                )
        )
    }
}

/**
 * Carrusel horizontal de wallets (fuentes de pago).
 *
 * Transpila el `<!-- Source Horizontal Scroll -->` del HTML:
 * - [LazyRow] con snap scroll
 * - Íconos por tipo de wallet (DEBIT_ACCOUNT, CREDIT_CARD, CASH, etc.)
 * - Estado activo: borde primary + fondo tintado
 * - Estado inactivo: fondo surfaceContainerLow
 */
@Composable
private fun SourceSection(
    wallets: List<PaymentMethodEntity>,
    selectedWalletId: String?,
    onWalletSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Text(
            text = "FUENTE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.8.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (wallets.isEmpty()) {
            Text(
                text = "Sin métodos de pago registrados.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = wallets,
                    key = { it.id }
                ) { wallet ->
                    WalletCard(
                        wallet = wallet,
                        isSelected = wallet.id == selectedWalletId,
                        onClick = { onWalletSelected(wallet.id) }
                    )
                }
            }
        }
    }
}

/**
 * Tarjeta de método de pago seleccionable en el carrusel.
 *
 * Transpila el `<button class="snap-start flex-shrink-0 w-32 p-4...">` del HTML.
 * El ícono se mapea desde el campo `kind` de [PaymentMethodEntity].
 *
 * @param wallet     Entidad del método de pago.
 * @param isSelected Si `true`, aplica borde primary y tinte de fondo.
 * @param onClick    Callback de selección.
 */
@Composable
private fun WalletCard(
    wallet: PaymentMethodEntity,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val elevation by animateDpAsState(
        targetValue = if (isSelected) 4.dp else 0.dp,
        animationSpec = spring(),
        label = "wallet_elevation"
    )

    Card(
        modifier = Modifier
            .size(width = 120.dp, height = 110.dp)
            .shadow(elevation, shape = RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(
                2.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )
        } else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = walletKindIcon(wallet.kind),
                contentDescription = wallet.displayName,
                tint = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )

            Column {
                Text(
                    text = wallet.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = walletKindLabel(wallet.kind),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Sección de atribución con FilterChips por miembro.
 *
 * Transpila el bloque `<!-- Attribution Filter Chips -->` del HTML.
 * Usa [FlowRow] para envolver automáticamente los chips cuando no caben
 * en una sola línea (muchos miembros).
 *
 * Los chips activos usan `bg-primary text-on-primary`;
 * los inactivos usan `bg-surface-container-high text-on-surface`.
 *
 * @param members          Lista de miembros activos del hogar.
 * @param selectedMemberIds Set de IDs actualmente marcados como beneficiarios.
 * @param onMemberToggled  Callback al pulsar un chip individual.
 * @param onSelectAll      Callback para el chip "Todos / Familia".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AttributionSection(
    members: List<MemberEntity>,
    selectedMemberIds: Set<String>,
    onMemberToggled: (String) -> Unit,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Text(
            text = "ATRIBUCIÓN",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.8.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Chip individual por cada miembro activo
            members.forEach { member ->
                val isSelected = member.id in selectedMemberIds

                FilterChip(
                    selected = isSelected,
                    onClick = { onMemberToggled(member.id) },
                    label = {
                        Text(
                            text = member.displayName,
                            style = MaterialTheme.typography.labelLarge
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        // Activo: fondo primary + texto on-primary (transpila el HTML)
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        // Inactivo: fondo surfaceContainerHigh + texto on-surface
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        labelColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        selectedBorderWidth = 0.dp,
                        borderWidth = 0.dp
                    )
                )
            }

            // Chip especial "All / Family" — transpila el botón con ícono de grupo
            FilterChip(
                selected = members.isNotEmpty() && selectedMemberIds.size == members.size,
                onClick = onSelectAll,
                label = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Group,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Todos",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    labelColor = MaterialTheme.colorScheme.primary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = false,
                    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    borderWidth = 1.dp
                )
            )
        }
    }
}

/**
 * Teclado numérico integrado 3×4.
 *
 * Transpila el `<!-- Integrated Number Pad -->` del HTML.
 * Envía eventos de tecla como Strings al ViewModel:
 * - "0"-"9" → dígito
 * - "."     → separador decimal
 * - "DEL"   → borrar último carácter
 *
 * @param onKey Callback que recibe el carácter pulsado.
 */
@Composable
private fun NumPadSection(
    onKey: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(".", "0", "DEL")
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                row.forEach { key ->
                    NumPadKey(
                        key = key,
                        onClick = { onKey(key) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Tecla individual del teclado numérico.
 * "DEL" muestra el ícono de Backspace en lugar de texto.
 */
@Composable
private fun NumPadKey(
    key: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (key == "DEL") {
            Icon(
                imageVector = Icons.Filled.Backspace,
                contentDescription = "Borrar",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Text(
                text = key,
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Botón de acción primaria "Registrar".
 *
 * Transpila el `<!-- Primary Action Button -->` del HTML:
 * `bg-gradient-to-br from-primary to-primary-dim text-on-primary`
 *
 * Cuando [isLoading] es `true`, muestra [CircularProgressIndicator]
 * en lugar del texto del botón.
 *
 * @param enabled   Si `false`, el botón está desactivado (formulario incompleto).
 * @param isLoading Si `true`, muestra el spinner de inserción en curso.
 * @param onClick   Callback que dispara la inserción atómica en el ViewModel.
 */
@Composable
private fun RegistrarButton(
    enabled: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 4.dp,
            pressedElevation = 1.dp
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .padding(end = 4.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Registrar",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Utilidades privadas
// ─────────────────────────────────────────────────────────────────────────────

/** Mapea el tipo de wallet (kind) a un ícono de Material Icons. */
private fun walletKindIcon(kind: String): ImageVector = when (kind) {
    "DEBIT_ACCOUNT"          -> Icons.Filled.AccountBalance
    "CREDIT_CARD"            -> Icons.Filled.CreditCard
    "DEPARTMENT_STORE_CARD"  -> Icons.Filled.CreditCard
    "BNPL_INSTALLMENT"       -> Icons.Filled.Payments
    "DIGITAL_WALLET"         -> Icons.Filled.Wallet
    "CASH"                   -> Icons.Filled.Payments
    "EMPLOYER_SAVINGS_FUND"  -> Icons.Filled.Savings
    else                     -> Icons.Filled.AccountBalance
}

/** Etiqueta legible del tipo de wallet para el subtítulo del WalletCard. */
private fun walletKindLabel(kind: String): String = when (kind) {
    "DEBIT_ACCOUNT"          -> "Débito"
    "CREDIT_CARD"            -> "Crédito"
    "DEPARTMENT_STORE_CARD"  -> "Tienda"
    "BNPL_INSTALLMENT"       -> "A plazos"
    "DIGITAL_WALLET"         -> "Digital"
    "CASH"                   -> "Efectivo"
    "EMPLOYER_SAVINGS_FUND"  -> "Ahorro"
    else                     -> kind
}

/**
 * Formatea el raw amount string del ViewModel para el display grande.
 * Ej: "1234" → "1,234" | "1234.5" → "1,234.5" | "0" → "0.00"
 */
private fun formatAmount(raw: String): String {
    if (raw == "0") return "0.00"
    val parts = raw.split(".")
    val intPart = parts[0].toLongOrNull()?.let {
        String.format("%,d", it)
    } ?: parts[0]
    return if (parts.size > 1) "$intPart.${parts[1]}" else intPart
}

/**
 * Crea un [kotlinx.coroutines.flow.StateFlow] constante para uso en
 * previews y en el constructor sin ViewModel concreto.
 */
private fun <T> dummyStateFlow(value: T): kotlinx.coroutines.flow.StateFlow<T> =
    kotlinx.coroutines.flow.MutableStateFlow(value)
