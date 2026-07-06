package mx.budget.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val MEMBER_ROLES = listOf(
    "PAYER_ADULT" to "Adulto (paga)",
    "BENEFICIARY_DEPENDENT" to "Dependiente",
)

private val WALLET_KINDS = listOf(
    "DEBIT_ACCOUNT" to "Débito",
    "CREDIT_CARD" to "Crédito",
    "CASH" to "Efectivo",
)

private val STEP_TITLES = listOf(
    "Tu hogar",
    "Miembros",
    "Cuentas",
    "Categorías",
    "Listo",
)

/**
 * Wizard de onboarding (paquete B2). Cinco pasos con motion expresivo (springs +
 * `AnimatedContent` deslizante). Resiliente a fontScale alto + bold: sin anchos
 * fijos, chips en `FlowRow`, columnas con scroll. Al terminar, invoca [onFinished].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onFinished: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.finished) {
        if (state.finished) onFinished()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        // Header + progreso.
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state.step > 0) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(enabled = !state.busy) { viewModel.back() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
            }
            Column {
                Text(
                    "CONFIGURACIÓN INICIAL · PASO ${state.step + 1} DE ${STEP_TITLES.size}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.4.sp
                )
                Text(
                    STEP_TITLES[state.step],
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light, fontSize = 30.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { (state.step + 1) / STEP_TITLES.size.toFloat() },
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)),
        )
        Spacer(Modifier.height(20.dp))

        // Cuerpo del paso (con scroll + transición deslizante).
        AnimatedContent(
            targetState = state.step,
            transitionSpec = {
                val forward = targetState > initialState
                val dir = if (forward) 1 else -1
                (slideInHorizontally(spring()) { w -> dir * w } + fadeIn(spring())) togetherWith
                    (slideOutHorizontally(spring()) { w -> -dir * w } + fadeOut(spring()))
            },
            modifier = Modifier.weight(1f),
            label = "onboardingStep",
        ) { step ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                when (step) {
                    0 -> StepHousehold(state, viewModel)
                    1 -> StepMembers(state, viewModel)
                    2 -> StepWallets(state, viewModel)
                    3 -> StepCategories(state, viewModel)
                    else -> StepSummary(state)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Barra de acción.
        val canAdvance = when (state.step) {
            0 -> state.householdName.isNotBlank()
            1 -> state.hasPayerAdult
            else -> true
        }
        if (state.step < STEP_TITLES.lastIndex) {
            Button(
                onClick = { viewModel.next() },
                enabled = canAdvance && !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Continuar") }
            if (state.step == 2 || state.step == 3) {
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { viewModel.next() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Omitir por ahora")
                }
            }
        } else {
            Button(
                onClick = { viewModel.finish() },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.busy) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Empezar a usar la app")
                }
            }
        }
    }
}

// ── Paso 1: household ────────────────────────────────────────────────────────

@Composable
private fun StepHousehold(state: OnboardingViewModel.UiState, vm: OnboardingViewModel) {
    Text(
        "Dale un nombre a tu presupuesto familiar. Podrás compartirlo con tu familia más adelante.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(20.dp))
    OutlinedTextField(
        value = state.householdName,
        onValueChange = vm::setHouseholdName,
        label = { Text("Nombre del hogar") },
        placeholder = { Text("Familia Romero") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        modifier = Modifier.fillMaxWidth(),
    )
}

// ── Paso 2: miembros ─────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepMembers(state: OnboardingViewModel.UiState, vm: OnboardingViewModel) {
    Text(
        "Agrega a las personas del hogar. Necesitas al menos un adulto que paga.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))

    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("PAYER_ADULT") }

    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Nombre") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MEMBER_ROLES.forEach { (r, label) ->
            Chip(label = label, selected = r == role, onClick = { role = r })
        }
    }
    Spacer(Modifier.height(10.dp))
    OutlinedButton(
        onClick = { vm.addMember(name, role); name = "" },
        enabled = name.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Agregar miembro")
    }

    Spacer(Modifier.height(16.dp))
    state.members.forEach { m ->
        ListRow(
            title = m.name,
            subtitle = MEMBER_ROLES.firstOrNull { it.first == m.role }?.second ?: m.role,
            onRemove = { vm.removeMember(m.localId) },
        )
    }
}

// ── Paso 3: wallets ──────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepWallets(state: OnboardingViewModel.UiState, vm: OnboardingViewModel) {
    Text(
        "Agrega tus cuentas y tarjetas con su saldo actual. Puedes hacerlo después en Cuentas.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))

    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("DEBIT_ACCOUNT") }
    var balance by remember { mutableStateOf("") }

    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Nombre de la cuenta") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        WALLET_KINDS.forEach { (k, label) ->
            Chip(label = label, selected = k == kind, onClick = { kind = k })
        }
    }
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = balance,
        onValueChange = { balance = it.filter { c -> c.isDigit() || c == '.' } },
        label = { Text(if (kind == "CREDIT_CARD") "Deuda actual (MXN)" else "Saldo inicial (MXN)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    OutlinedButton(
        onClick = {
            vm.addWallet(name, kind, balance.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 0.0)
            name = ""; balance = ""
        },
        enabled = name.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Agregar cuenta")
    }

    Spacer(Modifier.height(16.dp))
    state.wallets.forEach { w ->
        ListRow(
            title = w.name,
            subtitle = "${WALLET_KINDS.firstOrNull { it.first == w.kind }?.second ?: w.kind} · $${w.openingBalance.toLong()}",
            onRemove = { vm.removeWallet(w.localId) },
        )
    }
}

// ── Paso 4: categorías ───────────────────────────────────────────────────────

@Composable
private fun StepCategories(state: OnboardingViewModel.UiState, vm: OnboardingViewModel) {
    Text(
        "Elige las categorías que usarás. Puedes ajustar la lista cuando quieras.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    // Agrupa por padre: encabezado de grupo + sus hojas con checkbox.
    val groups = state.categories.filter { it.parentCode == null }
    groups.forEach { group ->
        Text(
            group.name.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.4.sp,
            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
        )
        state.categories.filter { it.parentCode == group.code }.forEach { leaf ->
            CategoryCheckRow(
                name = leaf.name,
                checked = leaf.selected,
                onToggle = { vm.toggleCategory(leaf.code) },
            )
        }
    }
}

// ── Paso 5: resumen ──────────────────────────────────────────────────────────

@Composable
private fun StepSummary(state: OnboardingViewModel.UiState) {
    val selectedCats = state.categories.count { it.selected && it.parentCode != null }
    Text(
        "Todo listo. Esto es lo que vamos a crear:",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    SummaryLine("Hogar", state.householdName.ifBlank { "Mi hogar" })
    SummaryLine("Miembros", "${state.members.size}")
    SummaryLine("Cuentas", "${state.wallets.size}")
    SummaryLine("Categorías", "$selectedCats")
    Spacer(Modifier.height(16.dp))
    Text(
        "Se activará la quincena de hoy automáticamente.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(value, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.primary)
    }
}

// ── Piezas compartidas ───────────────────────────────────────────────────────

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

@Composable
private fun ListRow(title: String, subtitle: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Close, "Quitar", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun CategoryCheckRow(name: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
    }
}
