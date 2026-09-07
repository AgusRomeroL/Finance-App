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
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.delay
import mx.budget.wear.data.ExpenseSender
import mx.budget.wear.data.Outbox
import mx.budget.wear.data.PhoneLink
import mx.budget.wear.data.SyncStatus
import mx.budget.wear.data.WearCache
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Hub del reloj: cuatro superficies bajo un [SwipeDismissableNavHost]: Estado,
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
 * VIVO. Sin esto las pantallas leían el cache una sola vez al componer y se
 * quedaban en el $0 del arranque aunque el snapshot llegara segundos después.
 * El listener se desregistra al salir de composición (`awaitDispose`).
 */
@Composable
private fun cacheVersion(context: Context): Int {
    val version by produceState(initialValue = 0, context) {
        val prefs = context.getSharedPreferences(WearCache.PREFS, Context.MODE_PRIVATE)
        // Se reacciona a tres claves y no a las ocho del snapshot: la de la
        // versión (escrita LAST por el listener del push, así que un push equivale
        // a una sola recomposición), la del enlace con el teléfono y la de la cola
        // de envío. Las tres cambian lo que se ve; las demás viajan con la primera.
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == WearCache.K_CACHE_VERSION ||
                key == PhoneLink.K_PHONE_REACHABLE ||
                key == Outbox.K_OUTBOX
            ) {
                value++
            }
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
    val scope = rememberCoroutineScope()

    // El estado ANTIGUO no lo anuncia nadie: el dato envejece en silencio, sin
    // que cambie ninguna preferencia. Un tic por minuto mientras el hub está a
    // la vista basta, y al salir de composición se acaba solo.
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            tick++
        }
    }

    // Sonda de arranque: en el primer uso todavía no ha llegado ningún evento
    // de capability, así que el enlace se consulta una vez al abrir.
    LaunchedEffect(Unit) {
        PhoneLink.setReachable(context, PhoneLink.probe(context))
    }

    val balance = remember(version) { WearCache.balance(context) }
    val hasData = remember(version) { WearCache.hasData(context) }
    val label = remember(version) { WearCache.label(context) }
    val members = remember(version) { WearCache.memberSpend(context) }
    val maxTotal = remember(members) { members.maxOfOrNull { it.total }?.takeIf { it > 0 } ?: 1.0 }
    val status = remember(version, tick) { SyncStatus.current(context) }
    val statusLabel = remember(version, tick) { SyncStatus.shortLabel(context) }
    val queued = remember(version) { Outbox.size(context) }

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
        // Línea de estado: solo aparece cuando hay algo que decir. Con todo en
        // orden no ocupa ni un pixel, que es el caso casi siempre.
        if (statusLabel.isNotEmpty()) {
            item {
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.caption3,
                    color = if (status == SyncStatus.DESCONECTADO) {
                        MaterialTheme.colors.error
                    } else {
                        MaterialTheme.colors.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                )
            }
        }
        item {
            Text("DISPONIBLE", style = MaterialTheme.typography.caption2, textAlign = TextAlign.Center)
        }
        item {
            Text(
                // Sin snapshot no se pinta "$0": ese cero es el valor por defecto
                // de la preferencia, no un saldo, y confundir "no sé" con "no
                // queda nada" es la peor confusión posible en una app de dinero.
                text = if (hasData) WearCache.money(balance) else "$--",
                style = MaterialTheme.typography.display2,
                color = if (hasData) {
                    MaterialTheme.colors.primary
                } else {
                    MaterialTheme.colors.onSurfaceVariant
                },
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
        // Cola de envío: lo que se capturó sin teléfono a la vista. Un toque
        // fuerza el reintento, aunque también se drena sola al abrir el hub y en
        // cuanto el teléfono vuelve a estar cerca.
        if (queued > 0) {
            item {
                CompactChip(
                    onClick = { scope.launch { Outbox.drain(context, ExpenseSender(context)) } },
                    label = {
                        Text(
                            if (queued == 1) "1 por enviar" else "$queued por enviar",
                            maxLines = 1,
                        )
                    },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
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
        // Un chip por fila, no dos en un Row: a lo ancho de una pantalla redonda
        // los dos juntos no caben y "Movimientos" se cortaba en "Movimiento",
        // que es una palabra distinta y encima plausible. Apilados entran
        // completos y el area tactil crece.
        item {
            CompactChip(
                onClick = onMovimientos,
                label = { Text("Movimientos", maxLines = 1) },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
        item {
            CompactChip(
                onClick = onPendientes,
                label = { Text("Pendientes", maxLines = 1) },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun MemberBar(m: WearCache.MemberSpend, maxTotal: Double) {
    // Padding horizontal propio: la fila ocupaba todo el ancho y en una pantalla
    // redonda los extremos caen fuera del circulo, asi que el nombre perdia la
    // primera letra y el monto la ultima cifra ("$682" se leia "$68"). Un monto
    // recortado es un dato falso, igual que en el Fold.
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                m.name,
                style = MaterialTheme.typography.caption1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(WearCache.money(m.total), style = MaterialTheme.typography.caption1, maxLines = 1)
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
                    "Sin teléfono cerca. Inténtalo de nuevo.",
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.error,
                    textAlign = TextAlign.Center,
                    // Padding horizontal: sin el, el circulo recorta la primera y
                    // la ultima letra de cada linea del aviso.
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
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
