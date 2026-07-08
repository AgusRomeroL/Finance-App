package mx.budget.ui.statements

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.budget.data.statements.StatementCycleStatus
import mx.budget.data.statements.WalletStatementStatus

/**
 * Checklist "Estados del mes": una fila por tarjeta con su estado del ciclo
 * (✅ importado / ⏳ pendiente / — sin corte), progreso arriba, y CTA por fila que
 * abre el import con ese wallet preseleccionado. Encadena: al volver, la lista se
 * recalcula reactivamente y la tarjeta importada pasa a ✅.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatementsChecklistScreen(
    statuses: List<WalletStatementStatus>,
    imported: Int,
    total: Int,
    onImportWallet: (String) -> Unit,
    onImportAny: () -> Unit,
    onBack: () -> Unit,
) {
    val target = if (total > 0) imported.toFloat() / total else 1f
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "statementProgress",
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Estados del mes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (total > 0) "$imported de $total importados este ciclo"
                        else "Sin tarjetas con corte conocido todavía",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.size(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = "Sube cada estado y la app reescribe el pago como abono a la " +
                            "tarjeta y registra las compras.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(statuses, key = { it.walletId }) { st ->
                WalletStatusRow(st, onImportWallet)
            }

            item {
                TextButton(onClick = onImportAny, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.UploadFile, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Importar otro estado (elegir cuenta)")
                }
            }
        }
    }
}

@Composable
private fun WalletStatusRow(
    st: WalletStatementStatus,
    onImportWallet: (String) -> Unit,
) {
    val (icon, tint, label) = when (st.status) {
        StatementCycleStatus.IMPORTED -> Triple(
            Icons.Filled.CheckCircle,
            MaterialTheme.colorScheme.primary,
            st.lastImportPeriodEnd?.let { "Importado hasta $it" } ?: "Importado",
        )
        StatementCycleStatus.PENDING -> Triple(
            Icons.Filled.RadioButtonUnchecked,
            MaterialTheme.colorScheme.error,
            st.expectedCutoff?.let { "Pendiente · corte $it" } ?: "Pendiente",
        )
        StatementCycleStatus.NO_CUTOFF -> Triple(
            Icons.Filled.HelpOutline,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Sin corte conocido — impórtalo una vez",
        )
    }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    st.walletName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (st.status != StatementCycleStatus.IMPORTED) {
                TextButton(onClick = { onImportWallet(st.walletId) }) {
                    Text(if (st.status == StatementCycleStatus.PENDING) "Importar" else "Subir")
                }
            }
        }
    }
}
