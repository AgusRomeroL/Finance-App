package mx.budget.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.budget.ui.theme.AppShapes
import mx.budget.ui.theme.FinancialTone
import mx.budget.ui.theme.amountSemantic

/**
 * Tarjeta KPI unificada (Cuentas + Analíticas): etiqueta pequeña arriba y
 * monto en `titleLarge.SemiBold` debajo, sobre el contenedor tonal del
 * [FinancialTone] vía [amountSemantic] (container/onContainer — con la
 * redundancia no-cromática resuelta por tono, no por color suelto).
 *
 * @param amount    monto a formatear con [toMxn]; se ignora si [valueText] != null.
 * @param valueText texto ya formateado (p. ej. moneda con centavos); tiene
 *                  prioridad sobre [amount].
 */
@Composable
fun KpiCard(
    label: String,
    amount: Double? = null,
    tone: FinancialTone = FinancialTone.NEUTRAL,
    valueText: String? = null,
    modifier: Modifier = Modifier,
) {
    val sem = amountSemantic(tone)
    Column(
        modifier = modifier
            .clip(AppShapes.card)
            .background(sem.container)
            .padding(16.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = sem.onContainer,
            maxLines = 2,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            valueText ?: amount?.toMxn() ?: "",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = sem.onContainer,
            maxLines = 1,
        )
    }
}
