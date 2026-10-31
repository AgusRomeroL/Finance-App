package mx.budget.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.budget.data.local.result.CategoryCapRow
import mx.budget.data.local.result.PlannedBreakdownRow
import mx.budget.data.quincena.CategoryCaps
import mx.budget.ui.common.AppLocale
import mx.budget.ui.common.AutoSizeAmountText
import mx.budget.ui.common.staggeredEntrance
import mx.budget.ui.theme.FinancialTone
import mx.budget.ui.theme.amountSemantic
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.roundToLong

private val pesos: NumberFormat = NumberFormat.getIntegerInstance(AppLocale)

private fun Double.aPesos(): String = "$" + pesos.format(roundToLong())

/**
 * Hoja "Lo reservado" (Fase 7b, alcance 4): qué aparta el héroe de su
 * disponible. Se abre al tocar "Reservado" o "Topes por gastar" en la resta.
 *
 * - **Planeados**: cada cargo con tono programado, su monto completo y, cuando
 *   su plantilla es mensual o bimestral, lo que de verdad carga esta quincena.
 *   La suma de lo que carga es el término "Reservado".
 * - **Topes por gastar**: lo que falta de cada tope por categoría, con el
 *   planeado de la categoría ya descontado. Su suma es el término "Topes".
 *
 * Solo lectura: corregir un planeado se hace desde el calendario y un tope desde
 * Categorías.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReservedBreakdownSheet(
    planned: List<PlannedBreakdownRow>,
    caps: List<CategoryCapRow>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val fecha = SimpleDateFormat("d MMM", AppLocale)
    val capsPendientes = caps.filter { CategoryCaps.pending(it) > 0.0 }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        sheetMaxWidth = 640.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Lo que aparta el disponible",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.semantics { heading() },
            )
            Text(
                "Este dinero ya tiene destino en la quincena. Por eso no aparece en el disponible.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Seccion("Planeados", planned.sumOf { it.prorated })
            if (planned.isEmpty()) {
                Vacio("No hay pagos planeados en esta quincena.")
            } else {
                planned.forEachIndexed { i, row ->
                    FilaPlaneado(row, fecha.format(Date(row.occurredAt)), Modifier.staggeredEntrance(i))
                }
            }

            Spacer(Modifier.height(6.dp))
            Seccion("Topes por gastar", CategoryCaps.totalPending(caps))
            when {
                caps.isEmpty() -> Vacio("Ninguna categoría tiene tope. Se ponen en Categorías.")
                capsPendientes.isEmpty() -> Vacio("Todos los topes ya se gastaron o se excedieron.")
                else -> capsPendientes.forEachIndexed { i, row ->
                    FilaTope(row, Modifier.staggeredEntrance(planned.size + i))
                }
            }
        }
    }
}

@Composable
private fun Seccion(titulo: String, total: Double) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            titulo,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        AutoSizeAmountText(
            text = total.aPesos(),
            baseStyle = MaterialTheme.typography.titleMedium,
            maxFontSp = 18f,
            minFontSp = 11f,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun Vacio(texto: String) {
    Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Un cargo planeado, con el tono programado (reloj, signo menos, "Programado"). */
@Composable
private fun FilaPlaneado(row: PlannedBreakdownRow, fecha: String, modifier: Modifier = Modifier) {
    val s = amountSemantic(FinancialTone.SCHEDULED)
    val prorrateado = row.weight < 1.0
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(s.container),
            contentAlignment = Alignment.Center,
        ) {
            s.icon?.let { Icon(it, contentDescription = s.description, tint = s.onContainer, modifier = Modifier.size(16.dp)) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(row.concept, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 3)
            Text(
                listOf(row.categoryName, fecha).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            if (prorrateado) {
                Text(
                    "Esta quincena carga ${row.prorated.aPesos()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        AutoSizeAmountText(
            text = "${s.sign}${row.amountMxn.aPesos()}",
            baseStyle = MaterialTheme.typography.titleSmall,
            maxFontSp = 16f,
            minFontSp = 11f,
            color = s.color,
        )
    }
}

/** Lo que falta de un tope, con lo ya gastado y su tope como contexto. */
@Composable
private fun FilaTope(row: CategoryCapRow, modifier: Modifier = Modifier) {
    val s = amountSemantic(FinancialTone.WARNING)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.categoryName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 3)
            Text(
                buildString {
                    append("Tope ${row.cap.aPesos()} · gastado ${row.spent.aPesos()}")
                    if (row.plannedProrated > 0.0) append(" · planeado ${row.plannedProrated.aPesos()}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
            )
        }
        Spacer(Modifier.width(10.dp))
        AutoSizeAmountText(
            text = CategoryCaps.pending(row).aPesos(),
            baseStyle = MaterialTheme.typography.titleSmall,
            maxFontSp = 16f,
            minFontSp = 11f,
            color = s.color,
        )
    }
}
