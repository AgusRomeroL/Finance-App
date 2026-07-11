package mx.budget.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import mx.budget.BudgetApplication
import mx.budget.ui.common.LocalReducedMotion
import mx.budget.ui.search.SpeechRecognizerController
import mx.budget.ui.search.SpeechState
import mx.budget.ui.theme.BudgetAppTheme
import mx.budget.ui.theme.BudgetMotion

/**
 * Overlay transparente de **captura en lenguaje natural** (Apéndice G.3).
 *
 * Destino compartido por el botón de micrófono del dashboard y por el widget de
 * pantalla de inicio (DRY). Dicta una frase ("gasté 200 en gasolina"), la
 * envía a [BudgetApplication.bankCaptureManager.ingestText] y termina; el gasto
 * NO se registra aquí: cae como propuesta `PENDING` en la bandeja y el usuario lo
 * confirma con el chip del inicio (propose-then-confirm).
 *
 * Degrada con gracia: si el dispositivo no soporta reconocimiento de voz (p. ej.
 * el emulador), oculta el micrófono y el usuario escribe la frase a mano — el
 * mismo parser determinista la procesa.
 */
class VoiceCaptureActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val source = intent.getStringExtra(EXTRA_SOURCE) ?: "VOICE"

        // Ingesta headless: si llega texto por extra (automatización, pruebas adb,
        // futuros productores tipo Tasker), lo procesa directo sin abrir el overlay.
        val directText = intent.getStringExtra(EXTRA_TEXT)
        if (!directText.isNullOrBlank()) {
            submit(directText, source)
            return
        }

        setContent {
            BudgetAppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
                    VoiceCaptureOverlay(
                        onSubmit = { text -> submit(text, source) },
                        onDismiss = { finish() },
                    )
                }
            }
        }
    }

    private fun submit(text: String, source: String) {
        val app = applicationContext as BudgetApplication
        // Corre en el scope de la aplicación, no en el del Activity (que se destruye
        // con el finish() de abajo y cancelaría el insert suspend a medias).
        app.captureNaturalLanguage(text, source)
        Toast.makeText(
            this,
            "Propuesta creada — confírmala en el inicio",
            Toast.LENGTH_SHORT,
        ).show()
        finish()
    }

    companion object {
        const val EXTRA_SOURCE = "extra_source"

        /** Texto a ingerir sin UI (ingesta headless: automatización/pruebas). */
        const val EXTRA_TEXT = "extra_text"
    }
}

@Composable
private fun VoiceCaptureOverlay(
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val controller = remember { SpeechRecognizerController(context) }
    val speechState by controller.state.collectAsState()
    val rmsLevel by controller.rmsLevel.collectAsState()
    val listening = speechState is SpeechState.Listening || speechState is SpeechState.Preparing

    var transcript by remember { mutableStateOf("") }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    fun startListening() {
        if (!controller.isAvailable || controller.isActive) return
        controller.start(
            onPartial = { transcript = it },
            onFinal = { transcript = it },
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) startListening()
    }

    // Arranca el dictado automáticamente si ya hay permiso y hay reconocimiento.
    LaunchedEffect(Unit) {
        if (controller.isAvailable) {
            if (hasAudioPermission) startListening()
            else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    DisposableEffect(Unit) { onDispose { controller.destroy() } }

    // "Materialize" (Material Expressive): el overlay no aparece de golpe — el scrim
    // se atenúa y la tarjeta crece 0.9→1 con fade. (El animateFloatAsState anterior
    // arrancaba YA en 1f, por lo que la animación nunca corría y el pop-up entraba
    // brusco.) Respeta reduced-motion.
    val reducedMotion = LocalReducedMotion.current
    val appear = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reducedMotion) appear.animateTo(1f, animationSpec = BudgetMotion.standard())
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f * appear.value))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .graphicsLayer {
                    val p = appear.value
                    scaleX = 0.9f + 0.1f * p
                    scaleY = 0.9f + 0.1f * p
                    alpha = p
                }
                // Consume el clic para que tocar la tarjeta NO se propague al scrim
                // (un clickable disabled no intercepta; este no-op sin ripple sí).
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {},
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Captura por voz", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                // Feedback en vivo del dictado (el bug reportado era "parece
                // activarse pero no reconoce" sin ninguna señal visible).
                val (statusText, statusIsError) = when (val s = speechState) {
                    is SpeechState.Preparing -> "Preparando micrófono…" to false
                    is SpeechState.Listening -> "Escuchando… habla ahora" to false
                    is SpeechState.Processing -> "Procesando…" to false
                    is SpeechState.Error -> s.message to true
                    else -> (
                        if (controller.isAvailable) "Di o escribe tu gasto, ej. “gasté 200 en gasolina”"
                        else "Escribe tu gasto, ej. “gasté 200 en gasolina”"
                        ) to false
                }
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (statusIsError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = transcript,
                        onValueChange = { transcript = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Tu gasto…") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = { if (transcript.isNotBlank()) onSubmit(transcript) }
                        ),
                    )
                    if (controller.isAvailable) {
                        Spacer(Modifier.width(8.dp))
                        FilledTonalIconButton(
                            onClick = {
                                if (listening) {
                                    controller.stop()
                                } else if (hasAudioPermission) {
                                    startListening()
                                } else {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier.scale(if (listening) 1f + 0.12f * rmsLevel else 1f),
                            colors = if (listening)
                                IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                )
                            else IconButtonDefaults.filledTonalIconButtonColors(),
                        ) {
                            Icon(
                                if (listening) Icons.Filled.Mic else Icons.Filled.MicOff,
                                contentDescription = if (listening) "Escuchando" else "Dictar",
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { if (transcript.isNotBlank()) onSubmit(transcript) },
                        enabled = transcript.isNotBlank(),
                    ) { Text("Registrar") }
                }
            }
        }
    }
}
