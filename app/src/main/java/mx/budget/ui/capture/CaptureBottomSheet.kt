package mx.budget.ui.capture

import mx.budget.ui.tutorial.TutorialKey
import mx.budget.ui.tutorial.tutorialTarget
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// CaptureBottomSheet — rediseño "Architectural Ledger" (frame 03) + paquete A3
// ─────────────────────────────────────────────────────────────────────────────
//
// Hoja acotada a 640dp centrada en pantallas grandes (brief D2), con:
//  · Toggle Gasto/Ingreso funcional (AnimatedContent con resortes M3)
//  · Monto + keypad
//  · Categoría = recientes REALES + búsqueda + crear inline + acordeón (brief C10)
//  · Atribución en dos dimensiones: "Beneficia a" / "Pagó" (brief C12)
//  · Fecha con DatePicker M3 (quincena determinista por fecha)
//  · Modo Review (SP-A1): prellenado + campos "Por decidir"
//  · Footer con resumen vivo + CTA Guardar
// Stateless: el CaptureViewModel es el único dueño del estado.

/** Resorte espacial estándar del sheet (Material Expressive). */
private fun <T> captureSpring() = spring<T>(dampingRatio = 0.8f, stiffness = 380f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureBottomSheet(
    viewModel: CaptureViewModel? = null,
    onDismiss: () -> Unit,
    mode: CaptureSheetMode = CaptureSheetMode.New,
    // Tutorial guiado: la hoja hospeda su propio overlay (problema de ventanas del
    // ModalBottomSheet). null = sin tour activo. Ver TUTORIAL.md.
    tutorialController: mx.budget.ui.tutorial.TutorialController? = null,
    tutorialCurrentRoute: String = "dashboard",
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
    val rawMembers by (viewModel?.members ?: dummyStateFlow(emptyList<MemberEntity>())).collectAsState()
    val beneficiaryShares by (viewModel?.beneficiaryShares ?: dummyStateFlow(emptyMap<String, Int>())).collectAsState()
    val payerShares by (viewModel?.payerShares ?: dummyStateFlow(emptyMap<String, Int>())).collectAsState()
    val attributionSuggestion by (viewModel?.attributionSuggestion
        ?: dummyStateFlow<CaptureSuggestion?>(null)).collectAsState()
    val canRegister by (viewModel?.canRegister ?: dummyStateFlow(false)).collectAsState()
    val notes by (viewModel?.notes ?: dummyStateFlow("")).collectAsState()
    val operationState by (viewModel?.operationState
        ?: dummyStateFlow<CaptureOperationState>(CaptureOperationState.Idle)).collectAsState()
    // A3: nuevos estados
    val captureKind by (viewModel?.captureKind ?: dummyStateFlow(CaptureKind.EXPENSE)).collectAsState()
    val selectedDate by (viewModel?.selectedDate ?: dummyStateFlow<LocalDate?>(null)).collectAsState()
    val incomeMemberId by (viewModel?.incomeMemberId ?: dummyStateFlow<String?>(null)).collectAsState()
    val rawRecentCategories by (viewModel?.recentCategories ?: dummyStateFlow(emptyList<CategoryEntity>())).collectAsState()
    val categorySearchResults by (viewModel?.categorySearchResults ?: dummyStateFlow(emptyList<CategoryEntity>())).collectAsState()
    val unresolvedFields by (viewModel?.unresolvedFields ?: dummyStateFlow(emptySet<CaptureField>())).collectAsState()
    // Fase B/B3: "¿Quién pagó?" con tercero.
    val allMembers by (viewModel?.allMembers ?: dummyStateFlow(emptyList<MemberEntity>())).collectAsState()

    // Tutorial: durante el tour se muestran miembros/categorías DEMO para que la atribución y la
    // tarjeta de categoría tengan contenido (nunca tocan Room). Ver TUTORIAL.md.
    val demo = tutorialController?.demoActive == true
    val members = if (demo) mx.budget.ui.tutorial.TutorialDemoData.members else rawMembers
    val recentCategories = if (demo) mx.budget.ui.tutorial.TutorialDemoData.recentCategories else rawRecentCategories
    val thirdPartyPayerId by (viewModel?.thirdPartyPayerId ?: dummyStateFlow<String?>(null)).collectAsState()
    val thirdPartyMode by (viewModel?.thirdPartyMode
        ?: dummyStateFlow(CaptureViewModel.ThirdPartyMode.REIMBURSE)).collectAsState()

    var showDatePicker by remember { mutableStateOf(false) }

    // Aplica el modo de apertura (SP-A1) una vez por instancia de modo.
    LaunchedEffect(mode, viewModel) { viewModel?.applyMode(mode) }

    LaunchedEffect(operationState) {
        when (val s = operationState) {
            is CaptureOperationState.Success -> {
                sheetState.hide()
                // Resetea el formulario para la siguiente captura (recientes frescas).
                viewModel?.onDismiss()
                onDismiss()
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
      Box {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.Transparent
        ) { inner ->
            Column(
                modifier = Modifier
                    .padding(inner)
                    .imePadding()
            ) {
                // TUTORIAL: CAP_KIND_TOGGLE — ver TUTORIAL.md
                Box(Modifier.tutorialTarget(TutorialKey.CAP_KIND_TOGGLE, tutorialController)) {
                    CaptureHeader(
                        kind = captureKind,
                        isReview = mode is CaptureSheetMode.Review,
                        onKindChange = { viewModel?.onKindChange(it) },
                        onClose = { viewModel?.onDismiss(); onDismiss() }
                    )
                }

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
                    // TUTORIAL: CAP_AMOUNT_KEYPAD — ver TUTORIAL.md
                    Box(Modifier.tutorialTarget(TutorialKey.CAP_AMOUNT_KEYPAD, tutorialController)) {
                        AmountCard(
                            displayAmount = displayAmount,
                            concept = concept,
                            kind = captureKind,
                            onConceptChange = { viewModel?.onConceptChange(it) },
                            onKey = { viewModel?.onNumpadKey(it) },
                            onRegister = { viewModel?.onRegister() },
                            canRegister = canRegister,
                            amountPending = CaptureField.AMOUNT in unresolvedFields,
                            conceptPending = CaptureField.CONCEPT in unresolvedFields
                        )
                    }
                    Spacer(Modifier.height(14.dp))

                    // Conmutación Gasto/Ingreso con resortes M3 (A3 §2).
                    AnimatedContent(
                        targetState = captureKind,
                        transitionSpec = {
                            (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                slideInVertically(animationSpec = captureSpring()) { it / 12 })
                                .togetherWith(fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)))
                                .using(SizeTransform(clip = false) { _, _ -> captureSpring() })
                        },
                        label = "captureKindContent"
                    ) { kind ->
                        when (kind) {
                            CaptureKind.EXPENSE -> Column {
                                // Sugerencia de atribución aprendida del historial
                                // (Feature A, §F.4): visible pero ignorable.
                                AnimatedVisibility(visible = attributionSuggestion != null) {
                                    attributionSuggestion?.let { suggestion ->
                                        Column {
                                            AttributionSuggestionChip(
                                                suggestion = suggestion,
                                                members = members,
                                                onApply = { viewModel?.applySuggestion() }
                                            )
                                            Spacer(Modifier.height(14.dp))
                                        }
                                    }
                                }
                                // TUTORIAL: CAP_CATEGORY — ver TUTORIAL.md
                                Box(Modifier.tutorialTarget(TutorialKey.CAP_CATEGORY, tutorialController)) {
                                    CategoryCard(
                                        categories = categories,
                                        recents = recentCategories,
                                        searchResults = categorySearchResults,
                                        selectedCategoryId = selectedCategoryId,
                                        pending = CaptureField.CATEGORY in unresolvedFields,
                                        onSelect = { viewModel?.onCategorySelected(it) },
                                        onQueryChange = { viewModel?.onCategoryQueryChange(it) },
                                        onCreateCategory = { name, parentId ->
                                            viewModel?.onCreateCategory(name, parentId)
                                        }
                                    )
                                }
                                Spacer(Modifier.height(14.dp))
                                WalletsCard(
                                    title = "Fuente de pago",
                                    wallets = wallets,
                                    selectedWalletId = selectedWalletId,
                                    pending = CaptureField.WALLET in unresolvedFields,
                                    onSelect = { viewModel?.onWalletSelected(it) }
                                )
                                Spacer(Modifier.height(14.dp))
                                // Atribución visible = solo "Beneficia a". El pagador (casi
                                // siempre el adulto dueño de la cuenta) se autodefine y vive
                                // bajo "Más" para overridear/repartir.
                                // TUTORIAL: CAP_ATTRIBUTION — ver TUTORIAL.md
                                Box(Modifier.tutorialTarget(TutorialKey.CAP_ATTRIBUTION, tutorialController)) {
                                    BeneficiaryCard(
                                        members = members,
                                        beneficiaryShares = beneficiaryShares,
                                        pending = CaptureField.BENEFICIARY in unresolvedFields,
                                        onToggle = { viewModel?.onBeneficiaryToggled(it) },
                                        onDelta = { id, d -> viewModel?.onBeneficiaryShareDelta(id, d) },
                                        onSelectAll = { viewModel?.onSelectAllMembers() },
                                        onClearAll = { viewModel?.onClearMembers() }
                                    )
                                }
                                Spacer(Modifier.height(14.dp))
                                MoreSection(
                                    members = members,
                                    payerShares = payerShares,
                                    onPayerToggle = { viewModel?.onPayerToggled(it) },
                                    onPayerDelta = { id, d -> viewModel?.onPayerShareDelta(id, d) },
                                    notes = notes,
                                    onNotesChange = { viewModel?.onNotesChange(it) },
                                    selectedDate = selectedDate,
                                    onPickDate = { showDatePicker = true },
                                    onResetDate = { viewModel?.onDateSelected(null) },
                                    datePending = CaptureField.DATE in unresolvedFields,
                                    payerPending = CaptureField.PAYER in unresolvedFields,
                                    // Fase B/B3: pagó un tercero.
                                    allMembers = allMembers,
                                    thirdPartyPayerId = thirdPartyPayerId,
                                    thirdPartyMode = thirdPartyMode,
                                    onThirdPartySelected = { viewModel?.onThirdPartyPayerSelected(it) },
                                    onThirdPartyMode = { viewModel?.onThirdPartyModeChange(it) },
                                    onCreateExternalPayer = { viewModel?.onCreateExternalPayer(it) }
                                )
                            }

                            CaptureKind.INCOME -> Column {
                                WalletsCard(
                                    title = "Cuenta destino",
                                    wallets = wallets,
                                    selectedWalletId = selectedWalletId,
                                    onSelect = { viewModel?.onWalletSelected(it) }
                                )
                                Spacer(Modifier.height(14.dp))
                                IncomeMemberCard(
                                    members = members,
                                    selectedMemberId = incomeMemberId,
                                    onSelect = { viewModel?.onIncomeMemberSelected(it) }
                                )
                                Spacer(Modifier.height(14.dp))
                                IncomeDateCard(
                                    selectedDate = selectedDate,
                                    onPickDate = { showDatePicker = true },
                                    onResetDate = { viewModel?.onDateSelected(null) }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                CaptureFooter(
                    kind = captureKind,
                    categories = categories,
                    selectedCategoryId = selectedCategoryId,
                    displayAmount = displayAmount,
                    members = members,
                    beneficiaryShares = beneficiaryShares,
                    incomeMemberId = incomeMemberId,
                    wallets = wallets,
                    selectedWalletId = selectedWalletId,
                    selectedDate = selectedDate,
                    enabled = canRegister,
                    isLoading = operationState is CaptureOperationState.Loading,
                    onRegister = { viewModel?.onRegister() }
                )
            }
        }
        // Overlay del tutorial DENTRO de la hoja: dibuja el spotlight sobre los targets
        // de captura en el espacio de coordenadas de la propia ventana del sheet.
        if (tutorialController != null) {
            mx.budget.ui.tutorial.TutorialOverlay(
                controller = tutorialController,
                currentRoute = tutorialCurrentRoute,
                onNavigate = {},
                groupFilter = { it.requiresCaptureSheet },
                orchestrate = false,
                modifier = Modifier.matchParentSize(),
            )
        }
      }
    }

    // DatePicker M3 (A3 §5): fecha del movimiento; la quincena se resuelve
    // determinista en el VM (q-YYYY-MM-FIRST|SECOND).
    if (showDatePicker) {
        val initialMillis = (selectedDate ?: LocalDate.now())
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        // El DatePicker trabaja en UTC-días: la conversión inversa
                        // también debe ser UTC para no correr un día.
                        viewModel?.onDateSelected(
                            Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        )
                    }
                    showDatePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = datePickerState, showModeToggle = false)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Header — título + toggle Gasto/Ingreso funcional
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CaptureHeader(
    kind: CaptureKind,
    isReview: Boolean,
    onKindChange: (CaptureKind) -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
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
                CapLabel(if (isReview) "Revisión" else "Nuevo movimiento")
                Text(
                    when {
                        isReview -> "Completar captura"
                        kind == CaptureKind.INCOME -> "Registrar ingreso"
                        else -> "Capturar gasto"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2
                )
            }
        }
        // Segmentado Gasto/Ingreso — conmutación real (A3 §2). En Review se
        // oculta: se revisa un gasto, no se cambia de tipo a media revisión.
        if (!isReview) {
            Spacer(Modifier.width(12.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                KindOption(
                    label = "Gasto",
                    selected = kind == CaptureKind.EXPENSE,
                    selectedColor = MaterialTheme.financeColors.expense,
                    onClick = { onKindChange(CaptureKind.EXPENSE) }
                )
                KindOption(
                    label = "Ingreso",
                    selected = kind == CaptureKind.INCOME,
                    selectedColor = MaterialTheme.financeColors.income,
                    onClick = { onKindChange(CaptureKind.INCOME) }
                )
            }
        }
    }
}

@Composable
private fun KindOption(
    label: String,
    selected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        animationSpec = captureSpring(),
        label = "kindOptionBg"
    )
    val fg by animateColorAsState(
        targetValue = if (selected) selectedColor else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = captureSpring(),
        label = "kindOptionFg"
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = fg,
            maxLines = 1
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Monto + keypad
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AmountCard(
    displayAmount: String,
    concept: String,
    kind: CaptureKind,
    onConceptChange: (String) -> Unit,
    onKey: (String) -> Unit,
    onRegister: () -> Unit,
    canRegister: Boolean,
    amountPending: Boolean = false,
    conceptPending: Boolean = false
) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CapLabel("Monto")
                if (amountPending) {
                    Spacer(Modifier.width(8.dp))
                    PendingBadge()
                }
            }
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
        if (conceptPending) {
            PendingBadge()
            Spacer(Modifier.height(6.dp))
        }
        // Concepto — junto al monto (no escondido tras "Más"); opcional en gasto,
        // etiqueta del ingreso en modo INCOME.
        OutlinedTextField(
            value = concept,
            onValueChange = onConceptChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    if (kind == CaptureKind.INCOME) "Etiqueta (ej. Sueldo, Honorarios)"
                    else "Concepto (opcional)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = if (conceptPending) MaterialTheme.financeColors.warning else Color.Transparent,
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
// Categoría: recientes reales + búsqueda + crear inline + acordeón
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryCard(
    categories: List<CategoryEntity>,
    recents: List<CategoryEntity>,
    searchResults: List<CategoryEntity>,
    selectedCategoryId: String?,
    pending: Boolean,
    onSelect: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onCreateCategory: (name: String, parentId: String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var expandedGroup by rememberSaveable { mutableStateOf<String?>(null) }
    // Nombre de la categoría en proceso de creación (null = no se está creando):
    // al elegirlo se muestra el selector de grupo padre (A3 §4).
    var createName by rememberSaveable { mutableStateOf<String?>(null) }

    val leaves = remember(categories) { categories.filter { it.parentId != null } }
    val parents = remember(categories) {
        categories.filter { it.parentId == null }.sortedBy { it.sortOrder }
    }
    val parentsById = remember(parents) { parents.associateBy { it.id } }
    val groups = remember(leaves) { leaves.groupBy { it.parentId } }
    val selected = leaves.firstOrNull { it.id == selectedCategoryId }

    val normalizedQuery = query.trim()
    // Resultados del DAO (searchLeavesByName, con debounce); fallback inmediato
    // en memoria mientras llega la primera emisión.
    val matches = if (normalizedQuery.isBlank()) emptyList()
    else searchResults.ifEmpty {
        leaves.filter { it.displayName.contains(normalizedQuery, ignoreCase = true) }.take(8)
    }
    val exactExists = normalizedQuery.isNotBlank() &&
        leaves.any { it.displayName.equals(normalizedQuery, ignoreCase = true) }

    Card {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CapLabel("Categoría")
                    if (pending) {
                        Spacer(Modifier.width(8.dp))
                        PendingBadge()
                    }
                }
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
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 160.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Recientes REALES: últimas categorías usadas en gastos POSTED (A3 §3).
        if (recents.isNotEmpty()) {
            CapMicroLabel("Recientes")
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recents.forEach { cat -> CategoryChip(cat.displayName, cat.id == selectedCategoryId) { onSelect(cat.id) } }
            }
            Spacer(Modifier.height(14.dp))
        }

        // Búsqueda (con autocompletado anti-duplicados vía DAO)
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                createName = null
                onQueryChange(it)
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Buscar o crear categoría…", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = if (pending) MaterialTheme.financeColors.warning else Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )

        Spacer(Modifier.height(12.dp))

        if (normalizedQuery.isNotBlank()) {
            AnimatedContent(
                targetState = createName,
                transitionSpec = {
                    (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                        slideInVertically(animationSpec = captureSpring()) { it / 12 })
                        .togetherWith(fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)))
                        .using(SizeTransform(clip = false) { _, _ -> captureSpring() })
                },
                label = "categoryCreateFlow"
            ) { creating ->
                if (creating == null) {
                    Column {
                        // Resultados / "¿Quisiste decir…?" (anti-duplicados, A3 §4)
                        if (matches.isNotEmpty()) {
                            CapMicroLabel(if (exactExists) "Coincidencias" else "¿Quisiste decir…?")
                            Spacer(Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                matches.forEach { cat -> CategoryChip(cat.displayName, cat.id == selectedCategoryId) { onSelect(cat.id) } }
                            }
                        } else {
                            Text("Sin coincidencias", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        // Crear inline solo si NO hay match exacto.
                        if (!exactExists) {
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .clickable { createName = normalizedQuery }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Crear \"$normalizedQuery\"",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                } else {
                    Column {
                        // Paso 2 de la creación: elegir el grupo padre.
                        CapMicroLabel("Nueva: \"$creating\" — elige el grupo")
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            parents.forEach { parent ->
                                CategoryChip(parent.displayName, false) {
                                    onCreateCategory(creating, parent.id)
                                    createName = null
                                    query = ""
                                    onQueryChange("")
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Cancelar",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { createName = null }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
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
// Wallets (fuente de pago / cuenta destino)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WalletsCard(
    title: String,
    wallets: List<PaymentMethodEntity>,
    selectedWalletId: String?,
    pending: Boolean = false,
    onSelect: (String) -> Unit
) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CapLabel(title)
                if (pending) {
                    Spacer(Modifier.width(8.dp))
                    PendingBadge()
                }
            }
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
    pending: Boolean = false,
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CapLabel("Beneficia a")
                    if (pending) {
                        Spacer(Modifier.width(8.dp))
                        PendingBadge()
                    }
                }
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
// Modo Ingreso: miembro que genera + fecha (A3 §2)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IncomeMemberCard(
    members: List<MemberEntity>,
    selectedMemberId: String?,
    onSelect: (String) -> Unit
) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.financeColors.incomeContainer),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Person, null, tint = MaterialTheme.financeColors.income, modifier = Modifier.size(15.dp)) }
            Spacer(Modifier.width(8.dp))
            Column {
                CapLabel("Lo genera")
                Spacer(Modifier.height(2.dp))
                Text("Miembro que recibe este ingreso", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (members.isEmpty()) {
            Text("Sin miembros.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                members.forEach { m ->
                    CategoryChip(m.displayName, m.id == selectedMemberId) { onSelect(m.id) }
                }
            }
        }
    }
}

@Composable
private fun IncomeDateCard(
    selectedDate: LocalDate?,
    onPickDate: () -> Unit,
    onResetDate: () -> Unit
) {
    Card {
        CapLabel("Fecha del ingreso")
        Spacer(Modifier.height(10.dp))
        DateRow(
            selectedDate = selectedDate,
            onPick = onPickDate,
            onReset = onResetDate,
            pending = false
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Fecha compartida (gasto e ingreso) — abre el DatePicker M3
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DateRow(
    selectedDate: LocalDate?,
    onPick: () -> Unit,
    onReset: () -> Unit,
    pending: Boolean
) {
    val formatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy", Locale("es", "MX")) }
    val label = selectedDate?.format(formatter)
        ?: "Hoy · ${LocalDate.now().format(formatter)}"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onPick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.Event, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(15.dp)) }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CapMicroLabel("Fecha")
                if (pending) {
                    Spacer(Modifier.width(8.dp))
                    PendingBadge()
                }
            }
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
        }
        if (selectedDate != null) {
            Text(
                "Hoy",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onReset)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            "Cambiar",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// "Más" — Pagó (default = adulto dueño de la cuenta) + fecha (DatePicker) + notas
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoreSection(
    members: List<MemberEntity>,
    payerShares: Map<String, Int>,
    onPayerToggle: (String) -> Unit,
    onPayerDelta: (String, Int) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit,
    selectedDate: LocalDate?,
    onPickDate: () -> Unit,
    onResetDate: () -> Unit,
    datePending: Boolean = false,
    payerPending: Boolean = false,
    // Fase B/B3: "¿Quién pagó?" con tercero.
    allMembers: List<MemberEntity> = emptyList(),
    thirdPartyPayerId: String? = null,
    thirdPartyMode: CaptureViewModel.ThirdPartyMode = CaptureViewModel.ThirdPartyMode.REIMBURSE,
    onThirdPartySelected: (String?) -> Unit = {},
    onThirdPartyMode: (CaptureViewModel.ThirdPartyMode) -> Unit = {},
    onCreateExternalPayer: (String) -> Unit = {}
) {
    // Abre por defecto si ya hay nota (p.ej. prellenada desde una captura NL), si
    // hay campos por decidir aquí dentro (Review) o si pagó un tercero.
    val autoOpen = notes.isNotBlank() || datePending || payerPending || thirdPartyPayerId != null
    var open by rememberSaveable(autoOpen) { mutableStateOf(autoOpen) }
    val payerNames = members.filter { it.id in payerShares.keys }.joinToString(", ") { it.displayName }
    val formatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy", Locale("es", "MX")) }
    val dateSummary = selectedDate?.format(formatter)
        ?: "Hoy, ${LocalDate.now().format(formatter)}"
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Más", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface)
                        if (datePending || payerPending) {
                            Spacer(Modifier.width(8.dp))
                            PendingBadge()
                        }
                    }
                    Text(
                        (if (payerNames.isNotEmpty()) "Pagó: $payerNames" else "Pagó: definir") + " · $dateSummary · Notas",
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
                if (payerPending) {
                    PendingBadge()
                    Spacer(Modifier.height(8.dp))
                }
                // Fase B/B3: ¿pagó un tercero? Con tercero seleccionado, ese tercero ES
                // el pagador, así que el editor normal de % por miembro se oculta.
                ThirdPartyPayerSection(
                    allMembers = allMembers,
                    selectedPayerId = thirdPartyPayerId,
                    mode = thirdPartyMode,
                    onSelected = onThirdPartySelected,
                    onModeChange = onThirdPartyMode,
                    onCreate = onCreateExternalPayer,
                )
                Spacer(Modifier.height(18.dp))
                AnimatedVisibility(visible = thirdPartyPayerId == null) {
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
                    }
                }
                // Fecha — por defecto hoy; el DatePicker M3 permite cambiarla y la
                // quincena se resuelve determinista según la fecha (A3 §5).
                DateRow(
                    selectedDate = selectedDate,
                    onPick = onPickDate,
                    onReset = onResetDate,
                    pending = datePending
                )
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
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// "¿Quién pagó?" con tercero (Fase B, B3)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Selector opcional de tercero que adelantó el gasto. Sin selección = pago normal
 * (wallet real). Al elegir un tercero (cualquier miembro, dependiente, EXTERNAL_* o
 * uno nuevo tecleado), el gasto se carga al wallet virtual "Pagado por terceros" sin
 * mover saldos reales, y aparece el toggle reembolsar / absorber.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThirdPartyPayerSection(
    allMembers: List<MemberEntity>,
    selectedPayerId: String?,
    mode: CaptureViewModel.ThirdPartyMode,
    onSelected: (String?) -> Unit,
    onModeChange: (CaptureViewModel.ThirdPartyMode) -> Unit,
    onCreate: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val normalizedQuery = query.trim()
    val matches = if (normalizedQuery.isBlank()) allMembers
    else allMembers.filter { it.displayName.contains(normalizedQuery, ignoreCase = true) }
    val exactExists = allMembers.any { it.displayName.equals(normalizedQuery, ignoreCase = true) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Groups, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(15.dp)) }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                CapMicroLabel("Pagó un tercero")
                Text(
                    "Un hijo o alguien que no es cuenta del hogar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Chips de terceros + "Nadie" (pago normal).
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryChip("Nadie / cuenta del hogar", selectedPayerId == null) { onSelected(null) }
            matches.take(8).forEach { m ->
                CategoryChip(m.displayName, m.id == selectedPayerId) { onSelected(m.id) }
            }
        }

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Buscar o crear un tercero…", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        )
        if (normalizedQuery.isNotBlank() && !exactExists) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable { onCreate(normalizedQuery); query = "" }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Añadir \"$normalizedQuery\" como tercero",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Toggle reembolsar / absorber (solo con tercero seleccionado).
        AnimatedVisibility(visible = selectedPayerId != null) {
            Column {
                Spacer(Modifier.height(12.dp))
                CapMicroLabel("¿Se le repone?")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryChip(
                        "Se le va a reembolsar",
                        mode == CaptureViewModel.ThirdPartyMode.REIMBURSE
                    ) { onModeChange(CaptureViewModel.ThirdPartyMode.REIMBURSE) }
                    CategoryChip(
                        "Lo absorbe él",
                        mode == CaptureViewModel.ThirdPartyMode.ABSORB
                    ) { onModeChange(CaptureViewModel.ThirdPartyMode.ABSORB) }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Footer: resumen vivo + Guardar (adaptado al modo, A3 §2)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CaptureFooter(
    kind: CaptureKind,
    categories: List<CategoryEntity>,
    selectedCategoryId: String?,
    displayAmount: String,
    members: List<MemberEntity>,
    beneficiaryShares: Map<String, Int>,
    incomeMemberId: String?,
    wallets: List<PaymentMethodEntity>,
    selectedWalletId: String?,
    selectedDate: LocalDate?,
    enabled: Boolean,
    isLoading: Boolean,
    onRegister: () -> Unit
) {
    val formatter = remember { DateTimeFormatter.ofPattern("d MMM", Locale("es", "MX")) }
    val hasAmount = displayAmount != "0.00" && displayAmount != "0"
    val summary = if (kind == CaptureKind.INCOME) {
        val memberName = members.firstOrNull { it.id == incomeMemberId }?.displayName
        val walletName = wallets.firstOrNull { it.id == selectedWalletId }?.displayName
        buildString {
            append("Ingreso")
            if (hasAmount) append(" · $").append(displayAmount)
            append(" · ").append(memberName ?: "¿Quién?")
            append(" → ").append(walletName ?: "elige cuenta")
            selectedDate?.let { append(" · ").append(it.format(formatter)) }
        }
    } else {
        val catName = categories.firstOrNull { it.id == selectedCategoryId }?.displayName
        val beneficiaries = members.filter { it.id in beneficiaryShares.keys }.joinToString(", ") { it.displayName }
        buildString {
            append(catName ?: "Elige categoría")
            if (hasAmount) append(" · $").append(displayAmount)
            if (beneficiaries.isNotEmpty()) append(" · ").append(beneficiaries)
            selectedDate?.let { append(" · ").append(it.format(formatter)) }
        }
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

// ─────────────────────────────────────────────────────────────────────────────
// Helpers de UI
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Distintivo "Por decidir" del modo Review (SP-A1): marca los campos que el
 * origen no pudo determinar. Redundancia no-cromática: icono + texto, no solo
 * color de énfasis.
 */
@Composable
private fun PendingBadge() {
    val warn = amountSemantic(FinancialTone.WARNING)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(warn.container)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.PriorityHigh, null, tint = warn.onContainer, modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            "POR DECIDIR",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = warn.onContainer,
            letterSpacing = 1.2.sp,
            maxLines = 1, softWrap = false
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
