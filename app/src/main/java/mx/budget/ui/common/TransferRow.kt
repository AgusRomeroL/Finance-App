package mx.budget.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.budget.ui.theme.FinancialTone
import mx.budget.ui.theme.amountSemantic

/**
 * Fila de una transferencia entre cuentas, compartida por Cuentas y Libro Mayor.
 *
 * Una transferencia no es un gasto: no consume presupuesto ni se atribuye a
 * nadie, solo mueve saldo de una cuenta a otra. Por eso usa el tono TRANSFER,
 * que no la pinta como ingreso ni como gasto y la anuncia como
 * "Transferencia" a los lectores de pantalla.
 *
 * Es puramente de presentacion: el texto del subtitulo y el del monto los
 * arma cada pantalla, porque Cuentas muestra pesos enteros y el Libro Mayor
 * muestra centavos.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransferRow(
    fromName: String,
    toName: String,
    amountText: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val sem = amountSemantic(FinancialTone.TRANSFER)
    val interaction = rememberPressInteractionSource()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interactionSource = interaction)
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .combinedClickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = { onClick?.invoke() },
                onLongClick = onLongClick,
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            sem.icon?.let {
                Icon(
                    it,
                    contentDescription = null,
                    tint = sem.color,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "$fromName \u2192 $toName",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            amountText,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = sem.color,
            maxLines = 1,
            modifier = Modifier.semantics { contentDescription = sem.description },
        )
    }
}
