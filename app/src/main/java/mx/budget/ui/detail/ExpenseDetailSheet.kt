package mx.budget.ui.detail

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Hoja de detalle de un gasto (Apéndice G.4.1 / G.4.3).
 *
 * Superficie mínima para **ver lugar + hora** y editarlos: cambiar la fecha/hora
 * (G.4.1) y **añadir ubicación a mano** (G.4.3) cuando el gasto no la tiene. El
 * resto del gasto (concepto, monto, categoría, wallet) se muestra como contexto de
 * solo lectura.
 *
 * Movimiento Material Expressive: la zona de ubicación transiciona con resorte
 * espacial al pasar de "sin ubicación" a "con ubicación".
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDetailSheet(
    viewModel: ExpenseDetailViewModel,
) {
    val state by viewModel.state.collectAsState()
    val detail = state ?: return
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val money = remember { NumberFormat.getCurrencyInstance(Locale("es", "MX")) }
    val dateTimeFmt = remember { SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale("es", "MX")) }

    ModalBottomSheet(
        onDismissRequest = { viewModel.dismiss() },
        sheetState = sheetState,
        sheetMaxWidth = 640.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            // Encabezado: concepto + monto.
            Text(
                detail.row.concept,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                money.format(detail.row.amountMxn),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${detail.row.categoryName} · ${detail.row.paymentMethodName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(20.dp))
            Divider()
            Spacer(Modifier.height(16.dp))

            // ── Hora (G.4.1) ──────────────────────────────────────────────────
            DetailRow(
                label = "Fecha y hora",
                value = dateTimeFmt.format(detail.occurredAt),
                actionLabel = "Editar",
                actionIcon = Icons.Filled.Edit,
                onAction = { showDateTimePicker(context, detail.occurredAt, viewModel::setOccurredAt) },
            )

            Spacer(Modifier.height(16.dp))

            // ── Ubicación (G.4.2 / G.4.3) ─────────────────────────────────────
            Text(
                "Ubicación",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            AnimatedContent(
                targetState = detail.hasLocation,
                transitionSpec = {
                    (fadeIn(spring(stiffness = 380f)) togetherWith fadeOut(spring(stiffness = 380f)))
                },
                label = "locationArea",
            ) { hasLocation ->
                if (hasLocation) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    detail.placeLabel
                                        ?: "%.5f, %.5f".format(detail.latitude, detail.longitude),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            TextButton(onClick = viewModel::removeLocation) { Text("Quitar") }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = viewModel::addLocation,
                        enabled = !detail.locating,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (detail.locating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Obteniendo ubicación…")
                        } else {
                            Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Añadir ubicación")
                        }
                    }
                }
            }
        }
    }
}

/** Fila etiqueta/valor con un botón de acción a la derecha. */
@Composable
private fun DetailRow(
    label: String,
    value: String,
    actionLabel: String,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        TextButton(onClick = onAction) {
            Icon(actionIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(actionLabel)
        }
    }
}

/**
 * Encadena el DatePickerDialog y el TimePickerDialog de plataforma para editar
 * `occurred_at` (G.4.1). Pragmático y fiable; respeta la zona horaria local.
 */
private fun showDateTimePicker(
    context: android.content.Context,
    currentMillis: Long,
    onPicked: (Long) -> Unit,
) {
    val cal = Calendar.getInstance().apply { timeInMillis = currentMillis }
    DatePickerDialog(
        context,
        { _, year, month, day ->
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    val picked = Calendar.getInstance().apply {
                        set(year, month, day, hour, minute, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onPicked(picked.timeInMillis)
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                true,
            ).show()
        },
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH),
        cal.get(Calendar.DAY_OF_MONTH),
    ).show()
}
