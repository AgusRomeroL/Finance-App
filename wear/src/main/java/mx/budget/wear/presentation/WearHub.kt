package mx.budget.wear.presentation

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import kotlinx.coroutines.launch
import mx.budget.wear.data.ExpenseSender
import mx.budget.wear.data.WearCache
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Hub del reloj: cuatro superficies bajo un [SwipeDismissableNavHost] — Estado,
 * Captura, Movimientos y Confirmar pendientes. Todo lee del cache local
 * ([WearCache]); el reloj no consulta Room ni red. Los cargos recomendados NO
 * viven aquí (son el Tile A), por decisión de producto.
 */
@Composable
fun WearHub() {
    val nav = rememberSwipeDismissableNavController()
    SwipeDismissableNavHost(navController = nav, startDestination = "home") {
        composable("home") {
            EstadoScreen(
                onCapture = { nav.navigate("captura/$TYPE_EXPENSE") },
                onMovimientos = { nav.navigate("movimientos") },
                onPendientes = { nav.navigate("pendientes") },
            )
        }
        composable("captura/{type}") { entry ->
            CapturaScreen(
                initialType = entry.arguments?.getString("type") ?: TYPE_EXPENSE,
                onSent = { nav.popBackStack() },
            )
        }
        composable("movimientos") { MovimientosScreen() }
        composable("pendientes") { PendientesScreen() }
    }
}

/**
 * Versión reactiva del cache: se incrementa cada vez que el push del teléfono
 * reescribe [WearCache] (SharedPreferences, vía `MobileSyncListenerService`).
 * Úsalo como clave de `remember` para releer los valores y reflejar la cifra EN
 * VIVO — sin esto las pantallas leían el cache una sola vez al componer y se
 * quedaban en el $0 del arranque aunque el snapshot llegara segundos después.
 * El listener se desregistra al salir de composición (`awaitDispose`).
 */
@Composable
private fun cacheVersion(context: Context): Int {
    val version by produceState(initialValue = 0, context) {
        val prefs = context.getSharedPreferences(WearCache.PREFS, Context.MODE_PRIVATE)
        // Reacciona SOLO a la clave de versión (escrita LAST por el listener del push),
        // no a cada una de las ~8 claves del snapshot → un push = una recomposición.
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == WearCache.K_CACHE_VERSION) value++
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return version
}

@Composable
private fun EstadoScreen(
    onCapture: () -> Unit,
    onMovimientos: () -> Unit,
    onPendientes: () -> Unit,
) {
    val context = LocalContext.current
    val version = cacheVersion(context)
    val balance = remember(version) { WearCache.balance(context) }
    val label = remember(version) { WearCache.label(context) }
    val members = remember(version) { WearCache.memberSpend(context) }
    val maxTotal = remember(members) { members.maxOfOrNull { it.total }?.takeIf { it > 0 } ?: 1.0 }

    ScalingLazyColumn(
        state = rememberScalingLazyListState(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item {
            Text(
                text = label,
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        item {
            Text("DISPONIBLE", style = MaterialTheme.typography.caption2, textAlign = TextAlign.Center)
        }
        item {
            Text(
                text = WearCache.money(balance),
                style = MaterialTheme.typography.display2,
                color = MaterialTheme.colors.primary,
                textAlign = TextAlign.Center,
            )
        }
        item {
            Button(
                onClick = onCapture,
                colors = ButtonDefaults.primaryButtonColors(),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) { Text("Registrar") }
        }
        if (members.isNotEmpty()) {
            item {
                Text(
                    "Gasto por miembro",
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            items(members) { m -> MemberBar(m, maxTotal) }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CompactChip(
                    onClick = onMovimientos,
                    label = { Text("Movimientos", maxLines = 1) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.weight(1f),
                )
                CompactChip(
                    onClick = onPendientes,
                    label = { Text("Pendientes", maxLines = 1) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MemberBar(m: WearCache.MemberSpend, maxTotal: Double) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(m.name, style = MaterialTheme.typography.caption1, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(WearCache.money(m.total), style = MaterialTheme.typography.caption1)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colors.surface),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((m.total / maxTotal).toFloat().coerceIn(0.02f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colors.primary),
            )
        }
    }
}

@Composable
private fun MovimientosScreen() {
    val context = LocalContext.current
    val version = cacheVersion(context)
    val movements = remember(version) { WearCache.movements(context) }

    ScalingLazyColumn(state = rememberScalingLazyListState(), modifier = Modifier.fillMaxWidth()) {
        item { Text("Movimientos", style = MaterialTheme.typography.title3, textAlign = TextAlign.Center) }
        if (movements.isEmpty()) {
            item {
                Text(
                    "Sin movimientos",
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            items(movements) { mv ->
                Chip(
                    onClick = {},
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(mv.concept, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    secondaryLabel = { Text("${WearCache.money(mv.amount)} · ${shortDate(mv.occurredAt)}") },
                )
            }
        }
    }
}

@Composable
private fun PendientesScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sender = remember { ExpenseSender(context) }
    val version = cacheVersion(context)
    // Se recrea con cada push del teléfono (nueva `version`), reconciliando la
    // bandeja con lo que ya no está pendiente; dentro de una versión, el
    // `remove(p)` optimista tras confirmar/descartar sigue siendo instantáneo.
    val pending = remember(version) {
        mutableStateListOf<WearCache.Pending>().apply { addAll(WearCache.pending(context)) }
    }
    // Error del último envío (el remove optimista se revierte si el mensaje no
    // llegó al teléfono). Se limpia con cada push nuevo (`version`) o al reintentar.
    var sendError by remember(version) { mutableStateOf(false) }

    /** Remove optimista + envío; si falla, reinserta el ítem donde estaba y avisa. */
    fun act(p: WearCache.Pending, send: suspend (String) -> Result<Unit>) {
        val index = pending.indexOf(p)
        pending.remove(p)
        scope.launch {
            val res = send(p.id)
            if (res.isFailure) {
                if (pending.none { it.id == p.id }) {
                    pending.add(index.coerceIn(0, pending.size), p)
                }
                sendError = true
            } else {
                sendError = false
            }
        }
    }

    ScalingLazyColumn(state = rememberScalingLazyListState(), modifier = Modifier.fillMaxWidth()) {
        item { Text("Pendientes", style = MaterialTheme.typography.title3, textAlign = TextAlign.Center) }
        if (sendError) {
            item {
                Text(
                    "Sin conexión con el teléfono — reintenta",
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                )
            }
        }
        if (pending.isEmpty()) {
            item {
                Text(
                    "Sin pendientes",
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            items(pending, key = { it.id }) { p ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(p.concept, style = MaterialTheme.typography.button, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        WearCache.money(p.amount),
                        style = MaterialTheme.typography.caption1,
                        color = MaterialTheme.colors.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        CompactChip(
                            onClick = { act(p, sender::confirmPending) },
                            label = { Text("Confirmar", maxLines = 1) },
                            colors = ChipDefaults.primaryChipColors(),
                            modifier = Modifier.weight(1f),
                        )
                        CompactChip(
                            onClick = { act(p, sender::discardPending) },
                            label = { Text("Descartar", maxLines = 1) },
                            colors = ChipDefaults.secondaryChipColors(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

private val DATE_FMT = SimpleDateFormat("d MMM", Locale("es", "MX"))
private fun shortDate(epoch: Long): String =
    if (epoch <= 0) "" else DATE_FMT.format(Date(epoch))
