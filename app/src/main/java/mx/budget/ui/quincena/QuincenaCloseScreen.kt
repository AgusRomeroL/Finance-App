package mx.budget.ui.quincena

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.budget.data.local.result.ExpenseWithDetails
import mx.budget.data.quincena.QuincenaLifecycle
import mx.budget.ui.common.AppLocale
import mx.budget.ui.common.staggeredEntrance
import mx.budget.ui.theme.BudgetMotion
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Cierre manual de quincena (RF-32): resume lo que se va a congelar, obliga a
 * decidir que pasa con lo planeado que nunca se ejecuto y, al confirmar, deja el
 * periodo en solo lectura. Tambien es la puerta para reabrirlo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuincenaCloseScreen(
    viewModel: QuincenaCloseViewModel,
    quincenaId: String,
    canManage: Boolean,
    onBack: () -> Unit,
    onExportPdf: ((String) -> Unit)? = null,
) {
    val state by viewModel.state.collectAsState()
    var confirmClose by remember { mutableStateOf(false) }

    LaunchedEffect(quincenaId, canManage) { viewModel.load(quincenaId, canManage) }

    val money = remember { NumberFormat.getCurrencyInstance(AppLocale) }
    val fechaFmt = remember { DateTimeFormatter.ofPattern("d 'de' MMMM", AppLocale) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.quincena?.label ?: "Cierre de quincena") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                EstadoDelPeriodo(state, fechaFmt)
            }

            state.figures?.let { cifras ->
                item {
                    TarjetaSeccion("LO QUE SE CONGELA") {
                        FilaCifra("Ingreso del periodo", money.format(cifras.income))
                        FilaCifra("Gasto ejecutado", money.format(cifras.spent))
                        FilaCifra("Reservado en planeados", money.format(cifras.reserved))
                        FilaCifra("Disponible", money.format(cifras.available), destacado = true)
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { (cifras.executionPct / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${cifras.executionPct} % del presupuesto ejercido",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (state.planned.isNotEmpty()) {
                item {
                    Column {
                        Text(
                            "PAGOS PLANEADOS SIN EJECUTAR",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            letterSpacing = 1.6.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Decide uno por uno. Nada se aplica hasta que confirmes el cierre.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!state.isClosed && state.canManage) {
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { viewModel.decideAll(PlannedDecision.MOVE) }) {
                                    Text("Mover todos")
                                }
                                TextButton(onClick = { viewModel.decideAll(PlannedDecision.DISCARD) }) {
                                    Text("Descartar todos")
                                }
                            }
                        }
                    }
                }
                itemsIndexed(state.planned) { index, fila ->
                    FilaPlaneado(
                        fila = fila,
                        index = index,
                        decision = state.decisions[fila.expenseId],
                        habilitado = !state.isClosed && state.canManage && !state.working,
                        money = money,
                        onDecide = { viewModel.decide(fila.expenseId, it) },
                    )
                }
            }

            if (state.byCategory.any { it.actual > 0.0 }) {
                item {
                    TarjetaSeccion("GASTO POR CATEGORÍA") {
                        state.byCategory.filter { it.actual > 0.0 }
                            .sortedByDescending { it.actual }
                            .take(12)
                            .forEach { FilaCifra(it.categoryName, money.format(it.actual)) }
                    }
                }
            }

            if (state.byBeneficiary.isNotEmpty()) {
                item {
                    TarjetaSeccion("QUIÉN CONSUMIÓ") {
                        state.byBeneficiary.forEach { FilaCifra(it.memberName, money.format(it.totalMxn)) }
                    }
                }
            }

            if (state.byPayer.isNotEmpty()) {
                item {
                    TarjetaSeccion("QUIÉN PAGÓ") {
                        state.byPayer.forEach { FilaCifra(it.memberName, money.format(it.totalMxn)) }
                    }
                }
            }

            if (state.balances.isNotEmpty()) {
                item {
                    TarjetaSeccion("SALDOS AL CIERRE") {
                        state.balances.forEach { FilaCifra(it.displayName, money.format(it.balance)) }
                    }
                }
            }

            item {
                AnimatedVisibility(
                    visible = state.error != null,
                    enter = fadeIn(BudgetMotion.standard()),
                    exit = fadeOut(BudgetMotion.standard()),
                ) {
                    Text(
                        state.error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            item {
                AccionesDeCierre(
                    state = state,
                    onCloseRequest = { confirmClose = true },
                    onReopen = viewModel::reopen,
                    onExportPdf = onExportPdf?.let { accion -> { accion(quincenaId) } },
                )
            }
        }
    }

    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text("¿Cerrar ${state.quincena?.label.orEmpty()}?") },
            text = {
                Text(
                    "Sus movimientos quedan en solo lectura y sus totales se congelan. " +
                        "Puedes reabrirla después si necesitas corregir algo."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClose = false
                    viewModel.close()
                }) { Text("Cerrar quincena") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClose = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun EstadoDelPeriodo(state: QuincenaCloseState, fechaFmt: DateTimeFormatter) {
    val quincena = state.quincena ?: return
    val inicio = runCatching { LocalDate.parse(quincena.startDate).format(fechaFmt) }.getOrDefault("")
    val fin = runCatching { LocalDate.parse(quincena.endDate).format(fechaFmt) }.getOrDefault("")
    val (etiqueta, color) = when (quincena.status) {
        QuincenaLifecycle.CLOSED -> "Cerrada" to MaterialTheme.colorScheme.onSurfaceVariant
        QuincenaLifecycle.CLOSING_REVIEW -> "Pendiente de cierre" to MaterialTheme.colorScheme.tertiary
        QuincenaLifecycle.ACTIVE -> "En curso" to MaterialTheme.colorScheme.primary
        else -> "Por comenzar" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column {
        Text(
            etiqueta.uppercase(AppLocale),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            letterSpacing = 1.6.sp,
            color = color,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Del $inicio al $fin",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TarjetaSeccion(titulo: String, contenido: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(20.dp)
    ) {
        Text(
            titulo,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            letterSpacing = 1.6.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        contenido()
    }
}

@Composable
private fun FilaCifra(etiqueta: String, valor: String, destacado: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            etiqueta,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            valor,
            style = if (destacado) MaterialTheme.typography.titleMedium
            else MaterialTheme.typography.bodyMedium,
            fontWeight = if (destacado) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun FilaPlaneado(
    fila: ExpenseWithDetails,
    index: Int,
    decision: PlannedDecision?,
    habilitado: Boolean,
    money: NumberFormat,
    onDecide: (PlannedDecision) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .staggeredEntrance(index)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(fila.concept, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                Text(
                    fila.categoryName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(money.format(fila.amountMxn), style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OpcionPlaneado("Mover", decision == PlannedDecision.MOVE, habilitado) {
                onDecide(PlannedDecision.MOVE)
            }
            OpcionPlaneado("Ya se pagó", decision == PlannedDecision.POST, habilitado) {
                onDecide(PlannedDecision.POST)
            }
            OpcionPlaneado("Descartar", decision == PlannedDecision.DISCARD, habilitado) {
                onDecide(PlannedDecision.DISCARD)
            }
        }
    }
}

@Composable
private fun OpcionPlaneado(
    texto: String,
    seleccionado: Boolean,
    habilitado: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = seleccionado,
        onClick = onClick,
        enabled = habilitado,
        label = { Text(texto) },
    )
}

@Composable
private fun AccionesDeCierre(
    state: QuincenaCloseState,
    onCloseRequest: () -> Unit,
    onReopen: () -> Unit,
    onExportPdf: (() -> Unit)?,
) {
    Column {
        state.blockedReason?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
        }
        if (state.isClosed) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onReopen,
                    enabled = state.canManage && !state.working,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Reabrir")
                }
                onExportPdf?.let {
                    Button(onClick = it, modifier = Modifier.weight(1f)) { Text("Reporte PDF") }
                }
            }
        } else {
            Button(
                onClick = onCloseRequest,
                enabled = state.canClose,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.working) "Cerrando…" else "Cerrar quincena")
            }
        }
    }
}
