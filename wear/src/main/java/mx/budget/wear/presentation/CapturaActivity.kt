package mx.budget.wear.presentation

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.android.horologist.compose.layout.AppScaffold
import kotlinx.coroutines.launch
import mx.budget.wear.data.ExpenseSender

/**
 * Actividad ligera de captura lanzada desde el **Tile B**. El modo llega por extra
 * ([EXTRA_MODE]): `EXPENSE`/`INCOME` muestran el [CapturaScreen] (teclado+tipo+voz);
 * `VOICE` dispara el reconocedor de voz directo y manda el texto crudo al teléfono
 * (§G.3, el reloj no corre LLM). Al terminar, cierra.
 */
class CapturaActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_EXPENSE
        setContent {
            AppScaffold {
                when (mode) {
                    MODE_VOICE -> VoiceCapture(onDone = { finish() })
                    MODE_INCOME -> CapturaScreen(initialType = TYPE_INCOME, onSent = { finish() })
                    else -> CapturaScreen(initialType = TYPE_EXPENSE, onSent = { finish() })
                }
            }
        }
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_EXPENSE = "EXPENSE"
        const val MODE_INCOME = "INCOME"
        const val MODE_VOICE = "VOICE"
    }
}

/**
 * Dictado directo: abre el reconocedor una vez y manda el transcript como NL.
 * Si el envío falla (teléfono desconectado, sin cola offline en MessageClient),
 * NO cierra como si hubiera funcionado: muestra un estado de error con el texto
 * dictado y un botón "Reintentar" que reenvía la misma frase.
 */
@Composable
private fun VoiceCapture(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sender = remember { ExpenseSender(context) }

    var sending by remember { mutableStateOf(false) }
    // Texto dictado cuyo envío falló, pendiente de reintento (null = sin error).
    var failedText by remember { mutableStateOf<String?>(null) }

    fun send(text: String) {
        scope.launch {
            sending = true
            failedText = null
            val res = sender.sendNaturalLanguage(text)
            sending = false
            if (res.isSuccess) onDone() else failedText = text
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val text = if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        } else null
        if (!text.isNullOrBlank()) send(text) else onDone()
    }

    LaunchedEffect(Unit) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
        }
        runCatching { launcher.launch(intent) }.onFailure { onDone() }
    }

    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val error = failedText
            when {
                error != null -> {
                    // Entrada con resorte M3 (escala), consistente con el motion expresivo.
                    val scale by animateFloatAsState(
                        targetValue = 1f,
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
                        label = "voiceErrorScale",
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "No se envió",
                            style = MaterialTheme.typography.title3,
                            color = MaterialTheme.colors.error,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "Sin conexión con el teléfono",
                            style = MaterialTheme.typography.caption1,
                            color = MaterialTheme.colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "“$error”",
                            style = MaterialTheme.typography.caption2,
                            color = MaterialTheme.colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Button(
                            onClick = { send(error) },
                            enabled = !sending,
                            colors = ButtonDefaults.primaryButtonColors(),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) { Text("Reintentar") }
                    }
                }
                sending -> Text("Enviando…", textAlign = TextAlign.Center)
                else -> Text("🎤 Escuchando…", textAlign = TextAlign.Center)
            }
        }
    }
}
