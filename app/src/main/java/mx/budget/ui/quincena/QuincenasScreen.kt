package mx.budget.ui.quincena

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import mx.budget.data.local.entity.QuincenaEntity
import mx.budget.data.quincena.QuincenaLifecycle
import mx.budget.ui.common.AppLocale
import mx.budget.ui.common.sanitizeAmountInput
import mx.budget.ui.common.toAmountInput
import mx.budget.ui.common.toAmountOrNull
import mx.budget.ui.common.pressScale
import mx.budget.ui.common.rememberPressInteractionSource
import mx.budget.ui.common.staggeredEntrance
import java.text.NumberFormat
import mx.budget.R

/**
 * Lista de todas las quincenas del hogar con su estado. Es la puerta para cerrar
 * una vencida, reabrir una cerrada, sacar su reporte y, desde la Fase 7b,
 * declarar su presupuesto: el lapiz de cada fila abre [PresupuestoQuincenaSheet].
 *
 * [canEdit] sigue el criterio del cierre: Dueno y Administrador, o cualquiera en
 * un hogar solo local. Una quincena cerrada no ofrece editar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuincenasScreen(
    quincenas: List<QuincenaEntity>,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
    canEdit: Boolean = false,
    budgetState: QuincenaBudgetState = QuincenaBudgetState(),
    onSaveBudget: (quincenaId: String, income: Double, expenses: Double) -> Unit = { _, _, _ -> },
    onBudgetConsumed: () -> Unit = {},
) {
    val money = NumberFormat.getCurrencyInstance(AppLocale)
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = quincenas.firstOrNull { it.id == editingId }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quincenas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Cerrar una quincena congela sus movimientos y deja su resumen fijo. " +
                        "Se puede reabrir cuando haga falta corregir algo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            itemsIndexed(quincenas, key = { _, q -> q.id }) { index, quincena ->
                FilaQuincena(
                    quincena = quincena,
                    index = index,
                    gasto = money.format(quincena.actualExpensesMxn),
                    presupuesto = presupuestoTexto(quincena, money),
                    onEdit = if (canEdit && quincena.status != QuincenaLifecycle.CLOSED) {
                        { editingId = quincena.id }
                    } else null,
                ) {
                    onOpen(quincena.id)
                }
            }
        }
    }

    if (editing != null) {
        PresupuestoQuincenaSheet(
            quincena = editing,
            state = budgetState,
            onSave = { income, expenses -> onSaveBudget(editing.id, income, expenses) },
            onDismiss = {
                editingId = null
                onBudgetConsumed()
            },
        )
    }
    LaunchedEffect(budgetState.savedId) {
        if (budgetState.savedId != null && budgetState.savedId == editingId) {
            editingId = null
            onBudgetConsumed()
        }
    }
}

/** "Presupuesto: entra $75,000 · gasto $72,070", o null si no hay presupuesto. */
private fun presupuestoTexto(q: QuincenaEntity, money: NumberFormat): String? {
    if (q.projectedIncomeMxn <= 0.0 && q.projectedExpensesMxn <= 0.0) return null
    return "Presupuesto: entra ${money.format(q.projectedIncomeMxn)} · gasto ${money.format(q.projectedExpensesMxn)}"
}

/**
 * Hoja para declarar el ingreso y el gasto proyectados de [quincena]. Acepta
 * centavos y no admite negativos. Se cierra sola cuando el guardado termina.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresupuestoQuincenaSheet(
    quincena: QuincenaEntity,
    state: QuincenaBudgetState,
    onSave: (income: Double, expenses: Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var income by rememberSaveable(quincena.id) {
        mutableStateOf(quincena.projectedIncomeMxn.takeIf { it > 0.0 }?.toAmountInput() ?: "")
    }
    var expenses by rememberSaveable(quincena.id) {
        mutableStateOf(quincena.projectedExpensesMxn.takeIf { it > 0.0 }?.toAmountInput() ?: "")
    }
    val incomeValue = if (income.isBlank()) 0.0 else income.toAmountOrNull()
    val expensesValue = if (expenses.isBlank()) 0.0 else expenses.toAmountOrNull()
    val valid = incomeValue != null && expensesValue != null
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetMaxWidth = 640.dp,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Presupuesto de la quincena",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                quincena.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = income,
                onValueChange = { income = sanitizeAmountInput(it) },
                label = { Text("Ingreso que esperas (MXN)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = expenses,
                onValueChange = { expenses = sanitizeAmountInput(it) },
                label = { Text("Gasto que planeas (MXN)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "El disponible parte del ingreso que esperas, hasta que entre más dinero. " +
                    "El ritmo compara lo gastado con el gasto que planeas. Las quincenas " +
                    "nuevas heredan este presupuesto.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = { onSave(incomeValue ?: 0.0, expensesValue ?: 0.0) },
                enabled = valid && !state.saving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(if (state.saving) "Guardando…" else "Guardar presupuesto")
            }
        }
    }
}

@Composable
private fun FilaQuincena(
    quincena: QuincenaEntity,
    index: Int,
    gasto: String,
    presupuesto: String?,
    onEdit: (() -> Unit)?,
    onClick: () -> Unit,
) {
    val interaction = rememberPressInteractionSource()
    val (etiqueta, color) = when (quincena.status) {
        QuincenaLifecycle.CLOSED -> "Cerrada" to MaterialTheme.colorScheme.onSurfaceVariant
        QuincenaLifecycle.CLOSING_REVIEW -> "Pendiente de cierre" to MaterialTheme.colorScheme.tertiary
        QuincenaLifecycle.ACTIVE -> "En curso" to MaterialTheme.colorScheme.primary
        else -> "Por comenzar" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .staggeredEntrance(index)
            .pressScale(interactionSource = interaction)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                etiqueta.uppercase(AppLocale),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                letterSpacing = 1.4.sp,
                color = color,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                quincena.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Gasto ejecutado: $gasto",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                presupuesto ?: "Sin presupuesto declarado",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onEdit != null) {
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.cd_edit_quincena_budget, quincena.label),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
