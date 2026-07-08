package mx.budget.ui.wallets

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.budget.ui.theme.financeColors
import java.text.NumberFormat
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Panel de deuda por tarjeta (estados v2 Fase 5). Vive en Cuentas — es operativo:
 * saldo, utilización, tasa, pago mínimo y fecha límite del último estado importado.
 * Filas expandibles con resorte; sin alturas fijas (resiliente a fontScale alto).
 */
@Composable
fun DebtPanelCard(cards: List<CardDebt>, modifier: Modifier = Modifier) {
    if (cards.isEmpty()) return
    val money = remember { NumberFormat.getCurrencyInstance(Locale("es", "MX")) }
    val fc = MaterialTheme.financeColors
    val totalDebt = cards.sumOf { it.saldo }
    val totalMin = cards.mapNotNull { it.pagoMinimo }.sum()

    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Deuda de tarjetas",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    money.format(totalDebt),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = fc.expense,
                )
            }
            if (totalMin > 0) {
                Text(
                    "Pagos mínimos del mes: ${money.format(totalMin)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            cards.forEach { c -> DebtRow(c, money) }
        }
    }
}

@Composable
private fun DebtRow(c: CardDebt, money: NumberFormat) {
    val fc = MaterialTheme.financeColors
    var expanded by remember { mutableStateOf(false) }
    val util = (c.utilizationPct ?: 0.0).toFloat().coerceIn(0f, 100f) / 100f
    val fill by animateFloatAsState(util, spring(stiffness = Spring.StiffnessMediumLow), label = "util")
    val dueDays = c.fechaLimite?.let {
        runCatching { ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(it.take(10))) }.getOrNull()
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .animateContentSize(spring(dampingRatio = 0.8f, stiffness = 380f))
            .padding(vertical = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                c.name + (c.last4?.let { " ••$it" } ?: ""),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                modifier = Modifier.weight(1f),
            )
            Text(money.format(c.saldo), style = MaterialTheme.typography.bodyLarge, color = fc.expense)
        }
        if (c.utilizationPct != null) {
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Box(
                    Modifier.fillMaxWidth(fill).fillMaxHeight().clip(RoundedCornerShape(3.dp))
                        .background(if (util > 0.8f) fc.warning else fc.expense),
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "Uso ${c.utilizationPct.toInt()}%" + (c.apr?.let { " · tasa ${it.toInt()}%" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Detalle: pago mínimo + fecha límite (badge de urgencia).
        if (c.pagoMinimo != null || c.fechaLimite != null) {
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                c.fechaLimite?.let { fl ->
                    val urgent = dueDays != null && dueDays in 0..5
                    Text(
                        "límite $fl" + (dueDays?.let { d -> if (d >= 0) " (${d}d)" else " (vencido)" } ?: ""),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = if (urgent) fc.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                c.pagoMinimo?.let {
                    Text(
                        "mínimo ${money.format(it)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (expanded && c.pagoNoIntereses != null) {
            Text(
                "Sin intereses: ${money.format(c.pagoNoIntereses)}" +
                    (c.limite?.let { " · límite ${money.format(it)}" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
