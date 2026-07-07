package mx.budget.ui.statements

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.budget.data.statements.StatementMovement

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
                is ImportPhase.Idle -> IdleContent(hasApiKey, onPick = { picker.launch("*/*") }, onOpenProfile = onOpenProfile)
                is ImportPhase.Extracting -> ProgressContent("Extrayendo texto del archivo…", "Todo local: el archivo no sale de tu teléfono.")
                is ImportPhase.Analyzing -> ProgressContent("Analizando con IA…", "Solo el texto viaja a la nube para estructurarlo.")
                is ImportPhase.Preview -> PreviewContent(viewModel)
                is ImportPhase.Applied -> AppliedContent(current.outcome, onDone = onBack, onAnother = viewModel::reset)
                is ImportPhase.Error -> ErrorContent(current.message, onRetry = viewModel::reset, onOpenProfile = onOpenProfile)
            }
        }
    }
}

@Composable
private fun IdleContent(hasApiKey: Boolean, onPick: () -> Unit, onOpenProfile: () -> Unit) {
    Card {
        Text(
            "Sube tu estado de cuenta en PDF o imagen. Se extrae el texto en tu " +
                "teléfono y solo ese texto se envía a la IA para estructurarlo. " +
                "Nunca modifica tus gastos: solo añade corte, límite de pago y planes a meses.",
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
                Text("Elegir archivo (PDF o imagen)")
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

    Column {
        // Wallet objetivo de la reconciliación
        Card {
            SectionLabel("APLICAR A LA CUENTA")
            Spacer(Modifier.height(4.dp))
            Text(
                "Elige a qué cuenta pertenece este estado. Se ajustan corte, límite " +
                    "de pago, saldo y tasa; los gastos no se tocan.",
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
            }
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
        // Fase 5: sin cuenta elegida no hay conciliación posible — se bloquea.
        Button(
            onClick = viewModel::apply,
            enabled = selectedWalletId != null && !matching,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (selectedWalletId == null) "Elige una cuenta para aplicar" else "Aplicar")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = viewModel::reset, modifier = Modifier.fillMaxWidth()) {
            Text("Cancelar")
        }
        Spacer(Modifier.height(16.dp))
    }
}

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
private fun AppliedContent(
    outcome: mx.budget.data.statements.StatementImportManager.ApplyOutcome,
    onDone: () -> Unit,
    onAnother: () -> Unit,
) {
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
                if (outcome.linked > 0) append("\n· ${outcome.linked} movimiento(s) vinculados a gastos existentes (sin duplicar).")
                if (outcome.queuedNew > 0) append("\n· ${outcome.queuedNew} movimiento(s) nuevos en la bandeja de captura para confirmar.")
                if (outcome.ignored > 0) append("\n· ${outcome.ignored} ignorado(s).")
                if (outcome.duplicates > 0) append("\n· ${outcome.duplicates} ya estaban conciliados de un import anterior (omitidos).")
                if (outcome.msiTouched > 0) append("\n· ${outcome.msiTouched} plan(es) a meses creados/actualizados.")
                append("\nTus gastos no se modificaron.")
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
