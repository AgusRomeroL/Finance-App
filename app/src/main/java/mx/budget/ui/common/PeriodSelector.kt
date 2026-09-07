package mx.budget.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mx.budget.data.local.entity.QuincenaEntity
import java.time.LocalDate
import java.time.ZoneId

/**
 * Periodo de analisis, compartido por la dona por miembro de Analiticas y por el
 * balance entre adultos de "Cuentas entre miembros".
 *
 * El orden de la lista es el orden en pantalla. Cada pantalla decide cual es su
 * valor por defecto: Analiticas parte de [HISTORICO] y el balance entre adultos
 * de [QUINCENAL], porque ahi una cifra sin acotar deja de ser accionable.
 */
enum class MemberPeriod(val label: String) {
    HISTORICO("Historico"),
    ANUAL("Anual"),
    MENSUAL("Mensual"),
    QUINCENAL("Quincenal"),
}

/**
 * Rango `[startMs, endMs]` en epoch millis (zona de Mexico) del [period].
 *
 * Devuelve null solo cuando QUINCENAL no tiene quincena activa. HISTORICO abarca
 * todo, y por eso las pantallas que lo ofrecen deben advertir que esa cifra no es
 * un saldo cobrable.
 */
fun memberPeriodRangeMs(period: MemberPeriod, quincena: QuincenaEntity?): Pair<Long, Long>? {
    val zone = ZoneId.of("America/Mexico_City")
    fun startMs(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
    // Fin exclusivo a inclusivo: primer instante del dia siguiente menos 1 ms.
    fun endMs(exclusiveDay: LocalDate) = startMs(exclusiveDay) - 1
    return when (period) {
        MemberPeriod.HISTORICO -> 0L to Long.MAX_VALUE
        MemberPeriod.ANUAL -> {
            val today = LocalDate.now(zone)
            val start = LocalDate.of(today.year, 1, 1)
            startMs(start) to endMs(start.plusYears(1))
        }
        MemberPeriod.MENSUAL -> {
            val today = LocalDate.now(zone)
            val start = today.withDayOfMonth(1)
            startMs(start) to endMs(start.plusMonths(1))
        }
        MemberPeriod.QUINCENAL -> {
            if (quincena == null) return null
            val start = runCatching { LocalDate.parse(quincena.startDate) }.getOrNull() ?: return null
            val end = runCatching { LocalDate.parse(quincena.endDate) }.getOrNull() ?: return null
            startMs(start) to endMs(end.plusDays(1))
        }
    }
}

/**
 * Pills de periodo (Historico, Anual, Mensual, Quincenal).
 *
 * `FlowRow` de `FilterChip` para que reflowen a otra linea con fontScale alto mas
 * bold, en vez de recortarse. El `FilterChip` ya anima su seleccion.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemberPeriodPills(
    selected: MemberPeriod,
    onSelect: (MemberPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MemberPeriod.entries.forEach { period ->
            FilterChip(
                selected = period == selected,
                onClick = { onSelect(period) },
                label = { Text(period.label, maxLines = 1) },
            )
        }
    }
}
