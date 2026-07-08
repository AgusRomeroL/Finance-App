package mx.budget.ui.statements

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.budget.data.statements.DocumentKind
import mx.budget.data.statements.StatementMovement
import java.text.NumberFormat
import java.util.Locale

/**
 * Pantalla "Importar estado de cuenta" (Fase C, paquete C1).
 *
 * Flujo: elegir archivo (PDF/imagen) → progreso (extraer local / analizar cloud)
 * → preview EDITABLE de cabecera + movimientos/MSI → Aplicar. Sin API key,
 * dirige a Perfil. Estilo de tarjetas de Perfil; resiliente a fontScale + bold.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatementImportScreen(
    viewModel: StatementImportViewModel,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    val phase by viewModel.phase.collectAsStateWithLifecycle()
    val hasApiKey by viewModel.hasApiKey.collectAsStateWithLifecycle()
    val docType by viewModel.docType.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) viewModel.onFileChosen(uri) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    "FASE C",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.6.sp
                )
                Text(
                    "Importar estado de cuenta",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        AnimatedContent(
            targetState = phase,
            transitionSpec = {
                (fadeIn(spring(stiffness = 380f)) togetherWith fadeOut(spring(stiffness = 380f)))
            },
            label = "phase",
        ) { current ->
            when (current) {
                is ImportPhase.Idle -> IdleContent(hasApiKey, docType, viewModel::setDocType, onPick = { picker.launch("*/*") }, onOpenProfile = onOpenProfile)
                is ImportPhase.Extracting -> ProgressContent("Extrayendo texto del archivo…", "Todo local: el archivo no sale de tu teléfono.")
                is ImportPhase.Analyzing -> ProgressContent("Analizando con IA…", "Solo el texto viaja a la nube para estructurarlo.")
                is ImportPhase.Preview -> PreviewContent(viewModel)
                is ImportPhase.BuildingRewrite -> ProgressContent("Preparando la reescritura…", "Buscando pagos de tarjeta y sugiriendo categorías.")
                is ImportPhase.RewriteReview -> RewriteReviewContent(viewModel)
                is ImportPhase.Applied -> AppliedContent(current, onDone = onBack, onAnother = viewModel::reset)
                is ImportPhase.Error -> ErrorContent(current.message, onRetry = viewModel::reset, onOpenProfile = onOpenProfile)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IdleContent(
    hasApiKey: Boolean,
    docType: DocumentKind,
    onSelectDocType: (DocumentKind) -> Unit,
    onPick: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    val esEstado = docType == DocumentKind.BANK_STATEMENT
    Card {
        Text(
            "TIPO DE DOCUMENTO",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.4.sp,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DocumentKind.entries.forEach { kind ->
                FilterChip(
                    selected = docType == kind,
                    onClick = { onSelectDocType(kind) },
                    label = { Text(kind.displayName) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            if (esEstado) {
                "Sube tu estado de cuenta en PDF o imagen. Se extrae el texto en tu " +
                    "teléfono y solo ese texto se envía a la IA para estructurarlo. " +
                    "Reconcilia corte, límite de pago y planes a meses; y si eliges la " +
                    "cuenta, podrás reescribir los movimientos — nada se aplica sin tu " +
                    "confirmación."
            } else {
                "Sube el archivo que exportaste (CSV, ZIP, XML o JSON). Se lee en tu " +
                    "teléfono; solo el texto (producto/monto/fecha) viaja a la IA para " +
                    "clasificar categoría y beneficiario por producto. Revisa y confirma " +
                    "antes de aplicar — nada se guarda sin tu OK."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        if (!hasApiKey) {
            Text(
                "Falta configurar tu API key de NVIDIA.",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Pégala en Perfil para poder importar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Button(onClick = onOpenProfile, modifier = Modifier.fillMaxWidth()) {
                Text("Ir a Perfil")
            }
        } else {
            Button(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.UploadFile, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (esEstado) "Elegir archivo (PDF o imagen)" else "Elegir archivo (CSV, ZIP, XML, JSON)")
            }
        }
    }
}

@Composable
private fun ProgressContent(title: String, subtitle: String) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreviewContent(viewModel: StatementImportViewModel) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val wallets by viewModel.wallets.collectAsStateWithLifecycle()
    val selectedWalletId by viewModel.selectedWalletId.collectAsStateWithLifecycle()
    val decisions by viewModel.decisions.collectAsStateWithLifecycle()
    val matching by viewModel.matching.collectAsStateWithLifecycle()
    var showNewWallet by remember { mutableStateOf(false) }

    Column {
        // Wallet objetivo de la reconciliación
        Card {
            SectionLabel("APLICAR A LA CUENTA")
            Spacer(Modifier.height(4.dp))
            Text(
                "Elige a qué cuenta pertenece este estado. Se ajustan corte, límite " +
                    "de pago, saldo y tasa. Con una cuenta elegida podrás además " +
                    "reescribir los movimientos de la tarjeta (con tu confirmación).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                wallets.forEach { w ->
                    FilterChip(
                        selected = w.id == selectedWalletId,
                        onClick = { viewModel.selectWallet(if (w.id == selectedWalletId) null else w.id) },
                        label = { Text(w.displayName + (w.last4?.let { " ••$it" } ?: "")) },
                    )
                }
                // Crear una cuenta nueva desde el estado (ej. una tarjeta sin wallet).
                androidx.compose.material3.AssistChip(
                    onClick = { showNewWallet = true },
                    label = { Text("+ Nueva cuenta") },
                )
            }
        }

        if (showNewWallet) {
            mx.budget.ui.wallets.WalletFormSheet(
                initial = viewModel.newWalletFromDraft(),
                householdId = viewModel.householdId,
                onSave = { viewModel.createWalletAndSelect(it); showNewWallet = false },
                onDismiss = { showNewWallet = false },
            )
        }

        Spacer(Modifier.height(16.dp))

        Card {
            SectionLabel("DATOS DEL ESTADO DE CUENTA")
            Spacer(Modifier.height(12.dp))
            Field("Emisor", draft.emisor.orEmpty(), viewModel::updateEmisor)
            Field("Últimos 4 dígitos", draft.last4.orEmpty(), viewModel::updateLast4, numeric = true)
            Field("Periodo inicio (AAAA-MM-DD)", draft.periodo?.inicio.orEmpty(), viewModel::updatePeriodoInicio)
            Field("Periodo fin (AAAA-MM-DD)", draft.periodo?.fin.orEmpty(), viewModel::updatePeriodoFin)
            Field("Fecha de corte (AAAA-MM-DD)", draft.fechaCorte.orEmpty(), viewModel::updateFechaCorte)
            Field("Fecha límite de pago (AAAA-MM-DD)", draft.fechaLimitePago.orEmpty(), viewModel::updateFechaLimite)
            Field("Saldo total", draft.saldoTotal?.toString().orEmpty(), viewModel::updateSaldoTotal, numeric = true)
            Field("Pago mínimo", draft.pagoMinimo?.toString().orEmpty(), viewModel::updatePagoMinimo, numeric = true)
            Field("Pago para no generar intereses", draft.pagoNoIntereses?.toString().orEmpty(), viewModel::updatePagoNoIntereses, numeric = true)
            Field("Tasa anual (%)", draft.tasaAnual?.toString().orEmpty(), viewModel::updateTasaAnual, numeric = true)
        }

        Spacer(Modifier.height(16.dp))

        Card {
            SectionLabel("MOVIMIENTOS (${draft.movimientos.size})")
            Spacer(Modifier.height(4.dp))
            Text(
                "Cada movimiento se concilia contra tus gastos ya registrados: " +
                    "Vinculado no duplica nada; Nuevo va a la bandeja de captura " +
                    "para que lo confirmes; Ignorar lo omite. Los MSI crean o " +
                    "actualizan un plan a meses.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            if (matching) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Conciliando contra tus gastos…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
            } else if (selectedWalletId == null && draft.movimientos.isNotEmpty()) {
                Text(
                    "Elige la cuenta arriba para conciliar los movimientos.",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(12.dp))
            }
            if (draft.movimientos.isEmpty()) {
                Text(
                    "No se detectaron movimientos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                draft.movimientos.forEachIndexed { i, mov ->
                    MovementRow(
                        mov = mov,
                        decision = decisions[i],
                        onDecision = { status -> viewModel.setDecision(i, status) },
                        onChange = { t -> viewModel.updateMovement(i) { t(it) } },
                        onRemove = { viewModel.removeMovement(i) },
                    )
                    Spacer(Modifier.height(10.dp))
                }
                TextButton(onClick = viewModel::runPrematch, enabled = selectedWalletId != null && !matching) {
                    Text("Reconciliar de nuevo")
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        // Fase 5: aplicar la conciliación (pre-match) — sin cuenta elegida no hay
        // conciliación por línea posible, así que se bloquea.
        Button(
            onClick = viewModel::apply,
            enabled = selectedWalletId != null && !matching,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (selectedWalletId == null) "Elige una cuenta para aplicar" else "Aplicar conciliación")
        }
        // Ruta alterna: reescribir los movimientos de la tarjeta como gastos reales
        // (requiere cuenta elegida). Confirmación item por item en el siguiente paso.
        if (selectedWalletId != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = viewModel::continueFromPreview,
                enabled = !matching,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Reescribir movimientos de la tarjeta…")
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = viewModel::reset, modifier = Modifier.fillMaxWidth()) {
            Text("Cancelar")
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ── Paso "Reescribir movimientos" ───────────────────────────────────────────

@Composable
private fun RewriteReviewContent(viewModel: StatementImportViewModel) {
    val purchases by viewModel.rewritePurchases.collectAsStateWithLifecycle()
    val aggregates by viewModel.rewriteAggregates.collectAsStateWithLifecycle()
    val payerName by viewModel.rewritePayerName.collectAsStateWithLifecycle()
    val beneficiaryCount by viewModel.rewriteBeneficiaryCount.collectAsStateWithLifecycle()
    val wallets by viewModel.wallets.collectAsStateWithLifecycle()
    val selectedWalletId by viewModel.selectedWalletId.collectAsStateWithLifecycle()
    val walletName = wallets.firstOrNull { it.id == selectedWalletId }?.displayName ?: "la tarjeta"

    val selectedPurchases = purchases.filter { it.selected }
    val selectedAggregates = aggregates.filter { it.selected }
    val purchasesTotal = selectedPurchases.sumOf { it.item.montoMxn }
    val aggregatesTotal = selectedAggregates.sumOf { it.item.amountMxn }

    Column {
        // Resumen
        Card {
            SectionLabel("REESCRIBIR MOVIMIENTOS")
            Spacer(Modifier.height(8.dp))
            Text(
                buildString {
                    append("${selectedPurchases.size} compra(s) por ${mxn(purchasesTotal)} ")
                    append("se registrarán como gastos de $walletName")
                    if (selectedAggregates.isNotEmpty()) {
                        append(" y ${selectedAggregates.size} pago(s) por ${mxn(aggregatesTotal)} ")
                        append("se convertirán en transferencia a la tarjeta")
                    }
                    append(".")
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                buildString {
                    append("Las compras quedan a nombre de ")
                    append(if (beneficiaryCount > 0) "los $beneficiaryCount miembros por partes iguales" else "todos por partes iguales")
                    payerName?.let { append(", pagadas por $it") }
                    append(", y se marcan para revisar quién se benefició (Revisión de atribuciones o el detalle de cada gasto).")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Pagos agregados detectados → transferencia
        if (aggregates.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Card {
                SectionLabel("PAGOS DE TARJETA DETECTADOS (${aggregates.size})")
                Spacer(Modifier.height(4.dp))
                Text(
                    "Estos gastos parecen el pago agregado de la tarjeta. Al convertirlos " +
                        "se vuelven transferencia banco→tarjeta: dejan de contar como gasto " +
                        "y quedan como abono a la deuda.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                aggregates.forEachIndexed { i, entry ->
                    SelectableRow(
                        selected = entry.selected,
                        onToggle = { viewModel.toggleAggregate(i) },
                        title = entry.item.concept,
                        subtitle = "${isoDay(entry.item.occurredAt)} · desde ${entry.item.fromWalletName} · ${entry.item.categoryName}",
                        amount = mxn(entry.item.amountMxn),
                        badge = "→ transferencia",
                    )
                    if (i != aggregates.lastIndex) Spacer(Modifier.height(8.dp))
                }
            }
        }

        // Compras itemizadas → gastos
        Spacer(Modifier.height(16.dp))
        Card {
            SectionLabel("COMPRAS DEL ESTADO (${purchases.size})")
            Spacer(Modifier.height(4.dp))
            Text(
                "Cada compra marcada se inserta como gasto pagado con $walletName. " +
                    "Las de meses sin intereses vienen desmarcadas: ya se registran como plan a meses.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            if (purchases.isEmpty()) {
                Text(
                    "No hay compras para registrar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val members by viewModel.rewriteMembers.collectAsStateWithLifecycle()
                val categoryOptions by viewModel.rewriteCategories.collectAsStateWithLifecycle()
                purchases.forEachIndexed { i, entry ->
                    SelectableRow(
                        selected = entry.selected,
                        onToggle = { viewModel.togglePurchase(i) },
                        title = entry.item.concepto,
                        subtitle = listOfNotNull(
                            entry.item.fecha ?: "sin fecha",
                            entry.item.suggestedCategoryName ?: "categoría por decidir",
                        ).joinToString(" · "),
                        amount = mxn(entry.item.montoMxn),
                        badge = if (entry.item.esMsi) "MSI" else null,
                    )
                    // Chips de beneficiario + categoría (solo si la compra se insertará).
                    if (entry.selected) {
                        PurchaseAttributionRow(
                            members = members,
                            categories = categoryOptions,
                            selectedBeneficiaryIds = entry.item.suggestedBeneficiaryIds,
                            categoryName = entry.item.suggestedCategoryName,
                            onToggleBeneficiary = { id -> viewModel.togglePurchaseBeneficiary(i, id) },
                            onPickCategory = { id, name -> viewModel.updatePurchaseCategory(i, id, name) },
                        )
                    }
                    if (i != purchases.lastIndex) Spacer(Modifier.height(8.dp))
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Button(onClick = viewModel::applyRewrite, modifier = Modifier.fillMaxWidth()) {
            Text("Aplicar")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = viewModel::backToPreview, modifier = Modifier.fillMaxWidth()) {
            Text("Atrás")
        }
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * Bajo cada compra a insertar: chips de beneficiario (toggle, reparto equitativo
 * entre los seleccionados) + chip de categoría con dropdown. Precargados desde las
 * sugerencias del LLM; el ajuste es de bajo esfuerzo (un toque por miembro).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PurchaseAttributionRow(
    members: List<mx.budget.data.statements.RewriteMember>,
    categories: List<mx.budget.data.statements.RewriteMember>,
    selectedBeneficiaryIds: List<String>,
    categoryName: String?,
    onToggleBeneficiary: (String) -> Unit,
    onPickCategory: (String, String) -> Unit,
) {
    // Vacío = todos por igual (se refleja mostrando todos los chips activos).
    val effective = selectedBeneficiaryIds.ifEmpty { members.map { it.id } }.toSet()
    Column(modifier = Modifier.fillMaxWidth().padding(start = 40.dp, top = 4.dp, bottom = 4.dp)) {
        Text(
            "Beneficia a",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            members.forEach { m ->
                FilterChip(
                    selected = m.id in effective,
                    onClick = { onToggleBeneficiary(m.id) },
                    label = { Text(m.name) },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        var expanded by remember { mutableStateOf(false) }
        Box {
            androidx.compose.material3.AssistChip(
                onClick = { expanded = true },
                label = { Text(categoryName ?: "Elegir categoría") },
            )
            androidx.compose.material3.DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                categories.forEach { c ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(c.name) },
                        onClick = { onPickCategory(c.id, c.name); expanded = false },
                    )
                }
            }
        }
    }
}

/**
 * Fila seleccionable del paso de reescritura: checkbox + concepto + detalle +
 * monto. El fondo anima la selección con resorte (regla de motion expresivo).
 */
@Composable
private fun SelectableRow(
    selected: Boolean,
    onToggle: () -> Unit,
    title: String,
    subtitle: String,
    amount: String,
    badge: String?,
) {
    val background by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "rowBackground",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = selected, onCheckedChange = { onToggle() })
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                amount,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (badge != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** Formatea MXN para los resúmenes del paso de reescritura. */
private fun mxn(amount: Double): String =
    NumberFormat.getCurrencyInstance(Locale("es", "MX")).format(amount)

/** Fecha corta `YYYY-MM-DD` de un epoch millis en zona de México. */
private fun isoDay(epochMs: Long): String =
    java.time.Instant.ofEpochMilli(epochMs)
        .atZone(java.time.ZoneId.of("America/Mexico_City"))
        .toLocalDate()
        .toString()

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MovementRow(
    mov: StatementMovement,
    decision: StatementImportViewModel.MovementDecision?,
    onDecision: (String) -> Unit,
    onChange: ((StatementMovement) -> StatementMovement) -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Description, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(
                mov.fecha ?: "sin fecha",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Delete, "Quitar", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
            }
        }
        // Fase 5: decisión de conciliación. "Vinculado" solo si el pre-match
        // encontró pareja; su etiqueta muestra a qué gasto (fuente y confianza).
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (decision?.expenseId != null) {
                FilterChip(
                    selected = decision.status == "MATCHED",
                    onClick = { onDecision("MATCHED") },
                    label = { Text("Vinculado") },
                )
            }
            FilterChip(
                selected = decision?.status == "NEW" || decision == null,
                onClick = { onDecision("NEW") },
                label = { Text("Nuevo") },
            )
            FilterChip(
                selected = decision?.status == "IGNORED",
                onClick = { onDecision("IGNORED") },
                label = { Text("Ignorar") },
            )
        }
        if (decision?.expenseId != null && decision.status == "MATCHED") {
            Spacer(Modifier.height(4.dp))
            Text(
                buildString {
                    append("→ ")
                    append(decision.expenseLabel ?: decision.expenseId)
                    decision.confidence?.let { append("  (${(it * 100).toInt()} %") }
                    decision.source?.let { append(" · ${if (it == "NIM") "IA" else if (it == "LOCAL") "local" else "manual"}") }
                    if (decision.confidence != null) append(")")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Field("Concepto", mov.concepto.orEmpty(), { onChange { m -> m.copy(concepto = it.ifBlank { null }) } })
        Field("Monto", mov.monto?.toString().orEmpty(), { onChange { m -> m.copy(monto = it.toDoubleOrNull()) } }, numeric = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = mov.esMsi,
                onClick = { onChange { m -> m.copy(esMsi = !m.esMsi) } },
                label = { Text("Meses sin intereses") },
            )
        }
        if (mov.esMsi) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    Field("Núm. actual", mov.msiNumero?.toString().orEmpty(), { onChange { m -> m.copy(msiNumero = it.toIntOrNull()) } }, numeric = true)
                }
                Box(Modifier.weight(1f)) {
                    Field("Plazo total", mov.msiPlazo?.toString().orEmpty(), { onChange { m -> m.copy(msiPlazo = it.toIntOrNull()) } }, numeric = true)
                }
            }
        }
    }
}

@Composable
private fun AppliedContent(applied: ImportPhase.Applied, onDone: () -> Unit, onAnother: () -> Unit) {
    Card {
        Text(
            "Estado de cuenta aplicado",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            buildString {
                append("Se actualizaron los datos de la cuenta.")
                // Ruta de conciliación (pre-match).
                if (applied.linked > 0) append("\n· ${applied.linked} movimiento(s) vinculados a gastos existentes (sin duplicar).")
                if (applied.queuedNew > 0) append("\n· ${applied.queuedNew} movimiento(s) nuevos en la bandeja de captura para confirmar.")
                if (applied.ignored > 0) append("\n· ${applied.ignored} ignorado(s).")
                if (applied.duplicates > 0) append("\n· ${applied.duplicates} ya estaban conciliados de un import anterior (omitidos).")
                // Ruta de reescritura.
                if (applied.insertedCount > 0) {
                    append(
                        "\n· Se insertaron ${applied.insertedCount} compra(s) por " +
                            "${mxn(applied.insertedTotalMxn)} (pendientes de revisar atribución)."
                    )
                }
                if (applied.convertedCount > 0) {
                    append("\n· ${applied.convertedCount} pago(s) de tarjeta se convirtieron en transferencia.")
                }
                if (applied.msiCount > 0) append("\n· ${applied.msiCount} plan(es) a meses creados/actualizados.")
                if (applied.insertedCount == 0 && applied.convertedCount == 0) {
                    append("\nTus gastos no se modificaron.")
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Listo") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onAnother, modifier = Modifier.fillMaxWidth()) { Text("Importar otro") }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit, onOpenProfile: () -> Unit) {
    Card {
        Text(
            "No se pudo importar",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Reintentar") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onOpenProfile, modifier = Modifier.fillMaxWidth()) { Text("Abrir Perfil (API key)") }
    }
}

// ── Building blocks ─────────────────────────────────────────────────────────

@Composable
private fun Card(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(20.dp),
        content = content,
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.6.sp,
    )
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    numeric: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    )
}
