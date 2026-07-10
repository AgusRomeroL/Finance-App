package mx.budget.wear.presentation

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.launch
import mx.budget.wear.data.ExpenseSender
import mx.budget.wear.data.WearCache

/**
 * Captura manual en el reloj: teclado de monto + toggle Gasto/Ingreso + dictado de
 * concepto por voz. Al registrar, manda el mensaje al teléfono (propose-then-confirm
 * para gasto; PLANNED directo para ingreso) y llama [onSent]. La usan tanto el hub
 * (ruta interna) como la [CapturaActivity] lanzada desde el Tile B.
 */
@Composable
fun CapturaScreen(
    initialType: String,
    onSent: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sender = remember { ExpenseSender(context) }

    var type by remember { mutableStateOf(if (initialType == TYPE_INCOME) TYPE_INCOME else TYPE_EXPENSE) }
    var amountText by remember { mutableStateOf("") }
    var concept by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    // Mensaje de error visible cuando el envío al teléfono falla (sin cola offline
    // en MessageClient): el usuario conserva lo tecleado y puede reintentar con ✓.
    var sendError by remember { mutableStateOf(false) }

    val amount = amountText.toDoubleOrNull() ?: 0.0

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()?.let { concept = it }
        }
    }

    fun register() {
        if (amount <= 0.0 || sending) return
        scope.launch {
            sending = true
            sendError = false
            val label = concept.trim().ifBlank { if (type == TYPE_INCOME) "Ingreso" else "Gasto" }
            val res = if (type == TYPE_INCOME) sender.sendIncome(amount, label)
            else sender.sendQuickExpense(amount, label)
            sending = false
            if (res.isSuccess) onSent() else sendError = true
        }
    }

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // top holgado: en pantalla redonda el TimeText del arco superior
                // se encimaba con el chip de concepto al hacer scroll.
                .padding(start = 8.dp, end = 8.dp, top = 36.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Toggle tipo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TypeButton("Gasto", type == TYPE_EXPENSE, Modifier.weight(1f)) { type = TYPE_EXPENSE }
                TypeButton("Ingreso", type == TYPE_INCOME, Modifier.weight(1f)) { type = TYPE_INCOME }
            }

            // Monto
            Text(
                text = WearCache.money(amount),
                style = MaterialTheme.typography.display2,
                textAlign = TextAlign.Center,
            )

            // Concepto (dictable)
            Button(
                onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                        )
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
                    }
                    voiceLauncher.launch(intent)
                },
                colors = ButtonDefaults.secondaryButtonColors(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = concept.ifBlank { "🎤 Concepto" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Error de envío (aparece con resorte M3; lo tecleado se conserva)
            AnimatedVisibility(
                visible = sendError,
                enter = fadeIn() + expandVertically(
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
                ),
                exit = fadeOut() + shrinkVertically(
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
                ),
            ) {
                Text(
                    text = "Sin conexión con el teléfono — reintenta con ✓",
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Teclado
            Keypad(
                onDigit = { d -> if (amountText.length < 7) amountText += d },
                onDelete = { amountText = amountText.dropLast(1) },
                onOk = ::register,
                okEnabled = amount > 0.0 && !sending,
            )
        }
    }
}

@Composable
private fun TypeButton(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        colors = if (selected) ButtonDefaults.primaryButtonColors()
        else ButtonDefaults.secondaryButtonColors(),
        modifier = modifier,
    ) {
        Text(label, maxLines = 1)
    }
}

@Composable
private fun Keypad(
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
    onOk: () -> Unit,
    okEnabled: Boolean,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
        ).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { d -> Key(d) { onDigit(d) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Key("⌫", secondary = true) { onDelete() }
            Key("0") { onDigit("0") }
            Key("✓", primary = true, enabled = okEnabled) { onOk() }
        }
    }
}

@Composable
private fun Key(
    label: String,
    primary: Boolean = false,
    secondary: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = when {
            primary -> ButtonDefaults.primaryButtonColors()
            secondary -> ButtonDefaults.secondaryButtonColors()
            else -> ButtonDefaults.iconButtonColors()
        },
        modifier = Modifier.size(44.dp),
    ) {
        Text(label, style = MaterialTheme.typography.button, maxLines = 1)
    }
}

const val TYPE_EXPENSE = "EXPENSE"
const val TYPE_INCOME = "INCOME"
