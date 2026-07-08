package mx.budget.ui.settle

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowRightAlt
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.budget.ui.theme.FinancialTone
import mx.budget.ui.theme.amountSemantic
import java.text.NumberFormat
import java.util.Locale

private val mxn: NumberFormat = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
private fun Double.toMxn(): String = mxn.format(this)

/**
 * Pantalla "Cuentas entre miembros": muestra el neto que se deben entre sí los
 * miembros del hogar (computado de las atribuciones de gasto) y permite saldarlo.
 *
 * Redundancia no-cromática ([amountSemantic]): "te deben" lleva signo + y tono
 * de ingreso; "debes" lleva − y tono de gasto — el significado nunca depende
 * solo del color. Motion expresivo (resortes) en aparición de filas y estados.
 */
@Composable
fun MemberBalancesScreen(
    viewModel: MemberBalancesViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    var confirmGlobal by remember { mutableStateOf(false) }

    Scaffold(containerColor = MaterialTheme.colorScheme.surface) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .statusBarsPadding(),
        ) {
            Header(onBack = onBack)

            AnimatedVisibility(
                visible = state.isSettled,
                enter = fadeIn(spring(stiffness = 380f)),
                exit = fadeOut(spring(stiffness = 380f)),
            ) {
                SettledState()
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.memberNets.isNotEmpty()) {
                    item(key = "nets") {
                        MemberNetsStrip(state.memberNets)
                        Spacer(Modifier.height(4.dp))
                    }
                }

                if (state.pairs.isNotEmpty()) {
                    item(key = "header_pairs") {
                        SectionHeader("Quién le debe a quién")
                    }
                }

                items(state.pairs, key = { it.debtorId + "→" + it.creditorId }) { pair ->
                    PairRow(
                        pair = pair,
                        onSettle = { viewModel.settlePair(pair) },
                        modifier = Modifier.animateItem(
                            fadeInSpec = spring(stiffness = 380f),
                            fadeOutSpec = spring(stiffness = 380f),
                            placementSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
                        ),
                    )
                }

                if (state.pairs.isNotEmpty()) {
                    item(key = "settle_all") {
                        Spacer(Modifier.height(8.dp))
                        GlobalSettleCard(onClick = { confirmGlobal = true })
                    }
                }
            }
        }
    }

    if (confirmGlobal) {
        AlertDialog(
            onDismissRequest = { confirmGlobal = false },
            icon = { Icon(Icons.Filled.Handshake, contentDescription = null) },
            title = { Text("Saldar todas las cuentas") },
            text = {
                Text(
                    "Marcará como saldados todos los gastos que hoy generan deudas " +
                        "entre miembros. Los saldos quedarán en cero. No mueve dinero " +
                        "de ninguna cuenta.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.settleAll(state.allExpenseIds)
                    confirmGlobal = false
                }) { Text("Saldar todo") }
            },
            dismissButton = {
                TextButton(onClick = { confirmGlobal = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun Header(onBack: () -> Unit) {
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
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack, "Volver",
                tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "CUENTAS ENTRE MIEMBROS",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.6.sp,
            )
            Text(
                "Quién debe a quién",
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light, fontSize = 26.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun MemberNetsStrip(nets: List<MemberNet>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(nets, key = { it.memberId }) { net ->
            val positive = net.net >= 0
            val sem = amountSemantic(if (positive) FinancialTone.INCOME else FinancialTone.EXPENSE)
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(sem.container)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    net.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = sem.onContainer,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    // Signo tipográfico redundante al color (accesibilidad).
                    sem.sign + kotlin.math.abs(net.net).toMxn(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = sem.onContainer,
                    maxLines = 1,
                )
                Text(
                    if (positive) "le deben" else "debe",
                    style = MaterialTheme.typography.labelSmall,
                    color = sem.onContainer,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun PairRow(
    pair: PairDebt,
    onSettle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sem = amountSemantic(FinancialTone.EXPENSE)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowRightAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                // "Norma le debe a Agustín" — sujeto/verbo explícitos, sin depender del color.
                Text(
                    buildString {
                        append(pair.debtorName)
                        append(" le debe a ")
                        append(pair.creditorName)
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    sem.description, // "Gasto" → etiqueta semántica; el detalle real es el monto
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                sem.sign + pair.amount.toMxn(),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = sem.color,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!pair.fullySettleable) {
                Text(
                    "Solo desde \"Saldar todo\"",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            TextButton(
                onClick = onSettle,
                enabled = pair.fullySettleable,
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Saldar")
            }
        }
    }
}

@Composable
private fun GlobalSettleCard(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Handshake,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Saldar todas las cuentas",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                "Deja a todos a mano. No mueve dinero.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun SettledState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.Balance,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Nadie se debe nada",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Las cuentas entre los miembros están a mano.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        label.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
    )
}
