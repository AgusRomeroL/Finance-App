package mx.budget.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import mx.budget.data.local.entity.MemberEntity
import mx.budget.ui.capture.AttributionDimension
import mx.budget.ui.theme.FinancialTone
import mx.budget.ui.theme.amountSemantic
import mx.budget.ui.theme.financeColors

// ─────────────────────────────────────────────────────────────────────────────
// AttributionReviewScreen — "Revisión de atribuciones" (Feature B, Apéndice F.3.7)
// ─────────────────────────────────────────────────────────────────────────────
//
// Sistema "The Architectural Ledger": capas tonales, radios 28dp, sin líneas.
// Agrupa la cola por concepto canónico; cada grupo se aplica/edita/ignora en lote.
// La confianza usa redundancia no-cromática (icono + etiqueta + color).

@Composable
fun AttributionReviewScreen(
    viewModel: AttributionReviewViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val members by viewModel.members.collectAsState()

    var editing by remember { mutableStateOf<ReviewGroup?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
    ) {
        Header(onBack = onBack)

        // Cuenta GASTOS pendientes (no grupos) para coincidir con el badge del dashboard.
        SubHeader(pendingCount = state.pending.sumOf { it.expenseCount })

        when {
            state.loading -> Unit
            state.isEmpty -> EmptyState()
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(state.pending, key = { it.key }) { group ->
                    PendingGroupCard(
                        group = group,
                        onApply = { viewModel.applyGroup(group) },
                        onEdit = { editing = group },
                        onIgnore = { viewModel.ignoreGroup(group) }
                    )
                }
                if (state.autoApplied.isNotEmpty()) {
                    item(key = "auto_header") {
                        SectionLabel("Aplicados automáticamente")
                    }
                    items(state.autoApplied, key = { it.key }) { group ->
                        AutoAppliedGroupCard(
                            group = group,
                            onAcknowledge = { viewModel.acknowledgeGroup(group) },
                            onRevert = { viewModel.revertGroup(group) }
                        )
                    }
                }
            }
        }
    }

    editing?.let { group ->
        EditAttributionSheet(
            group = group,
            members = members,
            onDismiss = { editing = null },
            onSave = { benef, payer ->
                viewModel.applyEdited(group, benef, payer)
                editing = null
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun Header(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
            Eyebrow("Inteligencia · normalización")
            Text(
                "Revisión de atribuciones",
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light, fontSize = 28.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SubHeader(pendingCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp)
            .padding(bottom = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Agrupados por concepto similar",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (pendingCount > 0) {
            Text(
                "$pendingCount por revisar",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.6.sp,
        modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 2.dp)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Tarjeta de grupo PENDIENTE
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PendingGroupCard(
    group: ReviewGroup,
    onApply: () -> Unit,
    onEdit: () -> Unit,
    onIgnore: () -> Unit
) {
    Card {
        GroupHeader(group)

        Spacer(Modifier.height(16.dp))
        group.beneficiary?.let {
            RoleBlock("Beneficiario sugerido", Icons.Filled.Favorite, it)
            Spacer(Modifier.height(12.dp))
        }
        group.payer?.let {
            RoleBlock("Pagador sugerido", Icons.Filled.Payments, it)
            Spacer(Modifier.height(12.dp))
        }

        BasisLine(group)

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val hasSuggestion = group.beneficiary != null || group.payer != null
            if (hasSuggestion) {
                PrimaryButton(
                    text = "Aplicar a ${group.expenseCount}",
                    modifier = Modifier.weight(1f),
                    onClick = onApply
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            SecondaryButton(text = if (group.band == ConfidenceBand.BAJA) "Asignar" else "Editar", onClick = onEdit)
            GhostButton(text = "Ignorar", onClick = onIgnore)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AutoAppliedGroupCard(
    group: ReviewGroup,
    onAcknowledge: () -> Unit,
    onRevert: () -> Unit
) {
    Card {
        GroupHeader(group)

        Spacer(Modifier.height(14.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            group.beneficiary?.shares?.forEach { MemberPill(it) }
            group.payer?.shares?.forEach { MemberPill(it) }
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val inc = MaterialTheme.financeColors.income
            Icon(Icons.Filled.AutoAwesome, null, tint = inc, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "Auto-aplicado · ${confidencePct(group)} % de confianza",
                style = MaterialTheme.typography.bodySmall,
                color = inc,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // "Visto" = aceptar la auto-aplicación (la conserva y la saca de la lista).
            PrimaryButton(
                text = "Visto",
                modifier = Modifier.weight(1f),
                leadingIcon = Icons.Filled.CheckCircle,
                onClick = onAcknowledge
            )
            GhostButton(text = "Revertir", onClick = onRevert)
        }
    }
}

@Composable
private fun GroupHeader(group: ReviewGroup) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                group.label,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${group.expenseCount} gastos agrupados",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(10.dp))
        ConfidenceBadge(group.band)
    }
}

@Composable
private fun ConfidenceBadge(band: ConfidenceBand) {
    val (label, icon, container, content) = when (band) {
        ConfidenceBand.ALTA -> {
            val s = amountSemantic(FinancialTone.INCOME)
            BadgeStyle("Alta", Icons.Filled.CheckCircle, s.container, s.onContainer)
        }
        ConfidenceBand.MEDIA -> {
            val s = amountSemantic(FinancialTone.WARNING)
            BadgeStyle("Media", Icons.Filled.PriorityHigh, s.container, s.onContainer)
        }
        ConfidenceBand.BAJA -> BadgeStyle(
            "Baja", Icons.Filled.Remove,
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = content, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
            color = content,
            letterSpacing = 0.6.sp
        )
    }
}

private data class BadgeStyle(
    val label: String,
    val icon: ImageVector,
    val container: Color,
    val content: Color
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoleBlock(title: String, icon: ImageVector, role: RoleSuggestionUi) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.4.sp
            )
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            role.shares.forEach { MemberPill(it) }
        }
    }
}

@Composable
private fun MemberPill(share: MemberShareUi) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                share.name.take(1).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.width(7.dp))
        Text(share.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Text(
            "${share.pct}%",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun BasisLine(group: ReviewGroup) {
    val sample = group.beneficiary?.sampleSize ?: group.payer?.sampleSize ?: 0
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Insights, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            if (sample > 0) "Basado en $sample gastos · ${confidencePct(group)} % de confianza"
            else "Sin patrón claro — asígnalo manualmente",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun confidencePct(group: ReviewGroup): Int = (group.confidence * 100).toInt()

// ─────────────────────────────────────────────────────────────────────────────
// Hoja de edición — reutiliza el editor de % compartido (AttributionDimension)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditAttributionSheet(
    group: ReviewGroup,
    members: List<MemberEntity>,
    onDismiss: () -> Unit,
    onSave: (beneficiaryPct: Map<String, Int>?, payerPct: Map<String, Int>?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var benefShares by remember(group.key) {
        mutableStateOf<Map<String, Int>>(
            group.beneficiary?.shares?.associate { it.memberId to it.pct } ?: emptyMap()
        )
    }
    var payerShares by remember(group.key) {
        mutableStateOf<Map<String, Int>>(
            group.payer?.shares?.associate { it.memberId to it.pct } ?: emptyMap()
        )
    }

    val benefValid = group.beneficiary == null || benefShares.values.sum() == 100
    val payerValid = group.payer == null || payerShares.values.sum() == 100
    val canSave = benefValid && payerValid && (benefShares.isNotEmpty() || payerShares.isNotEmpty())

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetMaxWidth = 640.dp,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Eyebrow("Editar reparto")
            Spacer(Modifier.height(4.dp))
            Text(
                group.label,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                "Aplica a ${group.expenseCount} gastos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))

            if (group.beneficiary != null) {
                AttributionDimension(
                    icon = Icons.Filled.Favorite,
                    iconTint = MaterialTheme.financeColors.income,
                    iconBg = MaterialTheme.financeColors.incomeContainer,
                    title = "Beneficia a · consume",
                    members = members,
                    shares = benefShares,
                    onToggle = { benefShares = toggle(benefShares, it) },
                    onDelta = { id, d -> benefShares = delta(benefShares, id, d) },
                    onSelectAll = { benefShares = equalSplit(members.map { it.id }) },
                    onClearAll = { benefShares = emptyMap() }
                )
                Spacer(Modifier.height(20.dp))
            }
            if (group.payer != null) {
                AttributionDimension(
                    icon = Icons.Filled.Payments,
                    iconTint = MaterialTheme.colorScheme.onSurface,
                    iconBg = MaterialTheme.colorScheme.surfaceContainerHighest,
                    title = "Pagó · adelantó",
                    members = members,
                    shares = payerShares,
                    onToggle = { payerShares = toggle(payerShares, it) },
                    onDelta = { id, d -> payerShares = delta(payerShares, id, d) },
                    onSelectAll = null,
                    onClearAll = null
                )
                Spacer(Modifier.height(20.dp))
            }

            PrimaryButton(
                text = "Guardar reparto",
                modifier = Modifier.fillMaxWidth(),
                enabled = canSave,
                onClick = {
                    onSave(
                        group.beneficiary?.let { benefShares },
                        group.payer?.let { payerShares }
                    )
                }
            )
        }
    }
}

// Helpers de edición de %, mismo modelo que CaptureViewModel (reparto equitativo).
private fun equalSplit(ids: Collection<String>): Map<String, Int> {
    val list = ids.toList()
    if (list.isEmpty()) return emptyMap()
    val base = 100 / list.size
    val remainder = 100 - base * list.size
    return list.mapIndexed { i, id -> id to if (i == list.lastIndex) base + remainder else base }.toMap()
}

private fun toggle(shares: Map<String, Int>, memberId: String): Map<String, Int> {
    val ids = shares.keys.let { if (memberId in it) it - memberId else it + memberId }
    return equalSplit(ids)
}

private fun delta(shares: Map<String, Int>, memberId: String, d: Int): Map<String, Int> {
    if (memberId !in shares) return shares
    return shares + (memberId to (shares.getValue(memberId) + d).coerceIn(0, 100))
}

// ─────────────────────────────────────────────────────────────────────────────
// Botones y helpers de UI
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(50))
            .background(if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val content = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        if (leadingIcon != null) {
            Icon(leadingIcon, null, tint = content, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = content,
            maxLines = 1, softWrap = false
        )
    }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(46.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1, softWrap = false
        )
    }
}

@Composable
private fun GhostButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(46.dp)
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, softWrap = false
        )
    }
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.financeColors.income, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text(
                "Todo en orden",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "No hay atribuciones por revisar. Re-normaliza el historial desde Perfil si quieres recalcular.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Tarjeta-sección tonal con radio 28dp (regla No-Line). */
@Composable
private fun Card(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(20.dp),
        content = content
    )
}

@Composable
private fun Eyebrow(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.6.sp
    )
}
