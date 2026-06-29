package mx.budget.ui.calendar

import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import mx.budget.data.local.result.ExpenseWithDetails
import mx.budget.ui.dashboard.iconForCategory
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

private val ZONE: ZoneId = ZoneId.of("America/Mexico_City")

private val mxnInt: NumberFormat = NumberFormat.getIntegerInstance(Locale("es", "MX"))
private fun Double.toMxn(): String = "$" + mxnInt.format(this.toLong())

private val dayFmt = java.text.SimpleDateFormat("EEE d MMM", Locale("es", "MX"))
private fun formatDay(epochMillis: Long): String =
    dayFmt.format(Date(epochMillis)).replaceFirstChar { it.uppercase() }

private val selectedDayFmt = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es", "MX"))
internal fun LocalDate.formatLong(): String =
    format(selectedDayFmt).replaceFirstChar { it.uppercase() }

/** Día (zona México) de un gasto a partir de su `occurred_at` (epoch millis). */
private fun epochToLocalDate(epochMillis: Long): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(ZONE).toLocalDate()

/**
 * Pantalla de Calendario (Apéndice G.2, Fase 4): timeline de gastos `PLANNED`
 * ordenados por fecha, con las acciones de confirmación. Reusa el flujo de
 * Fases 2/3 a través del [CalendarViewModel]. Animaciones de resorte (Material
 * Expressive, mandato transversal) en aparición/reordenamiento/descarte de filas.
 *
 * Fase 4: vista de mes estilo Google Calendar arriba (con marcadores en los días
 * que tienen pagos) + timeline de PLANNED abajo, filtrable al tocar un día. Las
 * acciones reusan el flujo de Fases 2/3. La gestión de plantillas recurrentes
 * (CRUD) y el preset de lead son incrementos posteriores.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    newPlannedViewModel: NewPlannedViewModel,
    onBack: () -> Unit,
) {
    val planned by viewModel.planned.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<ExpenseWithDetails?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    val today = remember { LocalDate.now(ZONE) }
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var selected by remember { mutableStateOf<LocalDate?>(null) }

    if (showAdd) {
        NewPlannedSheet(viewModel = newPlannedViewModel, onDismiss = { showAdd = false })
    }

    val paymentDays = remember(planned) { planned.mapTo(HashSet()) { epochToLocalDate(it.occurredAt) } }
    val visible = remember(planned, selected) {
        selected?.let { sel -> planned.filter { epochToLocalDate(it.occurredAt) == sel } } ?: planned
    }

    editing?.let { item ->
        EditAmountDialog(
            item = item,
            onConfirm = { amount ->
                viewModel.confirmWithAmount(item.expenseId, amount)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    newPlannedViewModel.start(selected ?: today)
                    showAdd = true
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(Icons.Filled.Add, "Nuevo pago planeado", tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
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
                title = selected?.let { "Pagos: ${it.formatLong()}" } ?: "${planned.size} planeados",
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "month") {
                    Column {
                        MonthCalendar(
                            month = month,
                            today = today,
                            selected = selected,
                            paymentDays = paymentDays,
                            onPrevMonth = { month = month.minusMonths(1) },
                            onNextMonth = { month = month.plusMonths(1) },
                            // Tocar el día seleccionado de nuevo limpia el filtro.
                            onSelectDay = { d -> selected = if (selected == d) null else d },
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                }

                if (visible.isEmpty()) {
                    item(key = "empty") { EmptyRow(filtered = selected != null) }
                } else {
                    items(visible, key = { it.expenseId }) { item ->
                        PlannedCard(
                            item = item,
                            onConfirm = { viewModel.confirm(item.expenseId) },
                            onEdit = { editing = item },
                            onPostpone = {
                                viewModel.postpone(item.expenseId)
                                scope.launch { snackbar.showSnackbar("Recordatorio pospuesto") }
                            },
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
    }
}

@Composable
private fun Header(onBack: () -> Unit, title: String) {
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
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                "CALENDARIO",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.6.sp,
            )
            Text(
                title,
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light, fontSize = 28.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlannedCard(
    item: ExpenseWithDetails,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    onPostpone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
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
                    iconForCategory(item.categoryCode),
                    contentDescription = item.categoryName,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.concept,
                    style = MaterialTheme.typography.titleMedium,
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
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(Modifier.height(12.dp))

        // FlowRow: con fontScale alto + bold los botones reflowan en vez de recortarse.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionChip("Confirmar", filled = true, onClick = onConfirm)
            ActionChip("Editar", filled = false, onClick = onEdit)
            ActionChip("Posponer", filled = false, onClick = onPostpone)
        }
    }
}

@Composable
private fun ActionChip(label: String, filled: Boolean, onClick: () -> Unit) {
    val bg = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
    val fg = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

@Composable
private fun EditAmountDialog(
    item: ExpenseWithDetails,
    onConfirm: (Double?) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(item.amountMxn.toLong().toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirmar gasto") },
        text = {
            Column {
                Text(
                    item.concept,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { new -> text = new.filter { it.isDigit() || it == '.' } },
                    label = { Text("Monto real (MXN)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.toDoubleOrNull()) }) { Text("Confirmar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

@Composable
private fun EmptyRow(filtered: Boolean) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.EventAvailable,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (filtered) "Sin pagos este día." else "No hay gastos planeados.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
