package mx.budget.wear.presentation

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
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

/** Dictado directo: abre el reconocedor una vez y manda el transcript como NL. */
@Composable
private fun VoiceCapture(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sender = remember { ExpenseSender(context) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val text = if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        } else null
        if (!text.isNullOrBlank()) {
            scope.launch {
                sender.sendNaturalLanguage(text)
                onDone()
            }
        } else {
            onDone()
        }
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
            Text("🎤 Escuchando…", textAlign = TextAlign.Center)
        }
    }
}
