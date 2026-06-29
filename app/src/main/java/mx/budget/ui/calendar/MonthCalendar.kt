package mx.budget.ui.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthLabelFmt = DateTimeFormatter.ofPattern("LLLL yyyy", Locale("es", "MX"))
private val weekdays = listOf("L", "M", "M", "J", "V", "S", "D") // lunes-primero

/**
 * Vista de mes estilo Google Calendar (Apéndice G.2, Fase 4 inc. 2).
 *
 * Cuadrícula 6×7 lunes-primero con navegación de mes. Marca el día de hoy (anillo),
 * el día seleccionado (círculo relleno) y los días con gastos `PLANNED` (punto).
 * Tocar un día lo selecciona para filtrar la lista de abajo; tocar el seleccionado
 * de nuevo limpia el filtro (lo maneja el caller).
 */
@Composable
fun MonthCalendar(
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate?,
    paymentDays: Set<LocalDate>,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Encabezado: ‹  Junio 2026  ›
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavArrow(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Mes anterior", onPrevMonth)
            AnimatedContent(
                targetState = month,
                transitionSpec = {
                    (fadeIn(spring(stiffness = 380f)) togetherWith fadeOut(spring(stiffness = 380f)))
                },
                modifier = Modifier.weight(1f),
                label = "monthLabel",
            ) { m ->
                Text(
                    m.format(monthLabelFmt).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            NavArrow(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Mes siguiente", onNextMonth)
        }

        Spacer(Modifier.height(8.dp))

        // Cabecera de días de la semana.
        Row(Modifier.fillMaxWidth()) {
            weekdays.forEach { d ->
                Text(
                    d,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // 6 filas × 7 columnas, lunes-primero.
        val firstOfMonth = month.atDay(1)
        val leading = firstOfMonth.dayOfWeek.value - 1 // lunes=1 → 0 huecos
        val gridStart = firstOfMonth.minusDays(leading.toLong())
        for (week in 0 until 6) {
            Row(Modifier.fillMaxWidth()) {
                for (dow in 0 until 7) {
                    val date = gridStart.plusDays((week * 7 + dow).toLong())
                    DayCell(
                        date = date,
                        inMonth = date.monthValue == month.monthValue,
                        isToday = date == today,
                        isSelected = date == selected,
                        hasPayment = date in paymentDays,
                        onClick = { onSelectDay(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun NavArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    hasPayment: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val numberColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(3.dp)
            .clip(CircleShape)
            .then(
                when {
                    isSelected -> Modifier.background(MaterialTheme.colorScheme.primary)
                    isToday -> Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    else -> Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 14.sp,
                ),
                color = numberColor,
                textAlign = TextAlign.Center,
            )
            // Punto marcador de pagos (oculto bajo el relleno del día seleccionado).
            if (hasPayment && !isSelected) {
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}
