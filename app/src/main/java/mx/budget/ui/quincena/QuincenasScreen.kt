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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import mx.budget.data.local.entity.QuincenaEntity
import mx.budget.data.quincena.QuincenaLifecycle
import mx.budget.ui.common.AppLocale
import mx.budget.ui.common.pressScale
import mx.budget.ui.common.rememberPressInteractionSource
import mx.budget.ui.common.staggeredEntrance
import java.text.NumberFormat
import mx.budget.R

/**
 * Lista de todas las quincenas del hogar con su estado. Es la puerta para cerrar
 * una vencida, reabrir una cerrada y sacar su reporte.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuincenasScreen(
    quincenas: List<QuincenaEntity>,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
) {
    val money = NumberFormat.getCurrencyInstance(AppLocale)
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
                FilaQuincena(quincena, index, money.format(quincena.actualExpensesMxn)) {
                    onOpen(quincena.id)
                }
            }
        }
    }
}

@Composable
private fun FilaQuincena(
    quincena: QuincenaEntity,
    index: Int,
    gasto: String,
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
        }
        Spacer(Modifier.width(10.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
