package mx.budget.ui.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estado explícito del dictado (paquete A2). La UI lo observa para dar feedback
 * REAL de que el micrófono escucha (el bug reportado era "parece activarse pero
 * no reconoce" sin ninguna señal visible).
 *
 * Transiciones: Idle → Preparing → Listening → Processing → Done, con Error como
 * salida lateral desde cualquier punto. Error y Done son estados de reposo: el
 * usuario puede volver a tocar el micrófono (re-entran por Preparing).
 */
sealed interface SpeechState {
    data object Idle : SpeechState
    /** `startListening` emitido, esperando `onReadyForSpeech`. */
    data object Preparing : SpeechState
    /** Micrófono abierto, capturando audio (con nivel en [SpeechRecognizerController.rmsLevel]). */
    data object Listening : SpeechState
    /** Audio cerrado, el motor está transcribiendo el final. */
    data object Processing : SpeechState
    /** Hubo `onResults` con texto. */
    data object Done : SpeechState
    /** Falla con mensaje presentable al usuario. Estado de reposo (permite reintentar). */
    data class Error(val message: String) : SpeechState
}

/**
 * Envoltura de [SpeechRecognizer] para el dictado de captura NL (es-MX), con
 * máquina de estados explícita y manejo completo de errores (paquete A2).
 *
 * Decisiones anti-bug:
 *  - **`ERROR_RECOGNIZER_BUSY`**: causa típica del síntoma "parece escuchar pero
 *    no" (un recognizer previo quedó vivo — p. ej. una fuga en otra pantalla).
 *    Se destruye la instancia, se recrea y se reintenta UNA vez.
 *  - **`NO_MATCH`/`SPEECH_TIMEOUT`**: mensaje amable y regreso a reposo.
 *  - **On-device primero** ([SpeechRecognizer.createOnDeviceSpeechRecognizer],
 *    API 31+): más rápido y sin red. Si el modelo local no soporta es-MX
 *    (LANGUAGE_NOT_SUPPORTED/UNAVAILABLE) se reintenta con el recognizer normal.
 *  - [destroy] SIEMPRE al salir de composición (evita el BUSY del siguiente uso).
 *
 * Uso: `remember { SpeechRecognizerController(context) }` + `DisposableEffect`
 * que llame [destroy] al salir de composición.
 */
class SpeechRecognizerController(private val context: Context) {

    val isAvailable: Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    private val _state = MutableStateFlow<SpeechState>(SpeechState.Idle)
    val state: StateFlow<SpeechState> = _state.asStateFlow()

    /** Nivel de voz normalizado 0..1 (de `onRmsChanged`) para el pulso visual. */
    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private var recognizer: SpeechRecognizer? = null
    private var onPartial: (String) -> Unit = {}
    private var onFinal: (String) -> Unit = {}

    /** Reintentos de la sesión en curso (BUSY y fallback on-device→normal, uno c/u). */
    private var retriedBusy = false
    private var retriedOnDevice = false
    private var preferOnDevice = true

    /** `true` mientras hay una sesión de dictado en curso (no re-entrar). */
    val isActive: Boolean
        get() = _state.value.let {
            it is SpeechState.Preparing || it is SpeechState.Listening || it is SpeechState.Processing
        }

    /**
     * Arranca una sesión de dictado. No-op si no hay reconocimiento en el
     * dispositivo o si ya hay una sesión activa.
     */
    fun start(onPartial: (String) -> Unit, onFinal: (String) -> Unit) {
        if (!isAvailable || isActive) return
        this.onPartial = onPartial
        this.onFinal = onFinal
        retriedBusy = false
        retriedOnDevice = false
        preferOnDevice = true
        _state.value = SpeechState.Preparing
        startInternal()
    }

    /** Cierra el micrófono; el motor aún puede emitir el resultado final. */
    fun stop() {
        runCatching { recognizer?.stopListening() }
        if (isActive) _state.value = SpeechState.Processing
        _rmsLevel.value = 0f
    }

    /** Libera el recognizer. OBLIGATORIO al salir (fugas ⇒ BUSY en el siguiente uso). */
    fun destroy() {
        destroyRecognizer()
        _state.value = SpeechState.Idle
        _rmsLevel.value = 0f
    }

    // ── Internals ───────────────────────────────────────────────────────────────

    private fun startInternal() {
        destroyRecognizer()
        val sr = runCatching { createRecognizer() }.getOrNull() ?: run {
            _state.value = SpeechState.Error("El reconocimiento de voz no está disponible")
            return
        }
        recognizer = sr
        sr.setRecognitionListener(listener)
        runCatching { sr.startListening(recognitionIntent()) }
            .onFailure {
                Log.w(TAG, "startListening falló", it)
                _state.value = SpeechState.Error("No se pudo iniciar el dictado")
            }
    }

    /** On-device si el dispositivo lo trae (API 31+, minSdk 31); si no, el normal. */
    private fun createRecognizer(): SpeechRecognizer =
        if (preferOnDevice && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }

    private fun recognitionIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    }

    private fun destroyRecognizer() {
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.value = SpeechState.Listening
        }

        override fun onBeginningOfSpeech() {
            _state.value = SpeechState.Listening
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Rango típico ~[-2, 10] dB → 0..1 para el indicador de nivel.
            _rmsLevel.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            _state.value = SpeechState.Processing
            _rmsLevel.value = 0f
        }

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults?.firstResult()?.let(onPartial)
        }

        override fun onResults(results: Bundle?) {
            _rmsLevel.value = 0f
            val text = results?.firstResult()
            if (text != null) {
                onFinal(text)
                _state.value = SpeechState.Done
            } else {
                _state.value = SpeechState.Error("No te escuché, intenta de nuevo")
            }
        }

        override fun onError(error: Int) {
            _rmsLevel.value = 0f
            Log.w(TAG, "SpeechRecognizer onError=$error")
            when (error) {
                // Un recognizer previo quedó vivo: destruir, recrear y reintentar una vez.
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    if (!retriedBusy) {
                        retriedBusy = true
                        _state.value = SpeechState.Preparing
                        startInternal()
                    } else {
                        _state.value =
                            SpeechState.Error("El micrófono está ocupado, intenta de nuevo")
                    }
                }
                // El modelo on-device no soporta es-MX: reintenta con el recognizer normal.
                ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE -> {
                    if (!retriedOnDevice) {
                        retriedOnDevice = true
                        preferOnDevice = false
                        _state.value = SpeechState.Preparing
                        startInternal()
                    } else {
                        _state.value = SpeechState.Error("Español (MX) no disponible para dictado")
                    }
                }
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    _state.value = SpeechState.Error("No te escuché, intenta de nuevo")
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    _state.value = SpeechState.Error("Sin permiso de micrófono")
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER,
                ERROR_SERVER_DISCONNECTED ->
                    _state.value = SpeechState.Error("Error de red del reconocimiento")
                SpeechRecognizer.ERROR_AUDIO ->
                    _state.value = SpeechState.Error("Error de audio del micrófono")
                else ->
                    _state.value = SpeechState.Error("Falló el dictado, intenta de nuevo")
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun Bundle.firstResult(): String? =
        getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()?.takeIf { it.isNotBlank() }

    private companion object {
        const val TAG = "SpeechCapture"

        // Constantes añadidas en API 31/33; valores estables del framework. Se usan
        // literales para no depender del nivel exacto de compileSdk.
        const val ERROR_SERVER_DISCONNECTED = 11
        const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        const val ERROR_LANGUAGE_UNAVAILABLE = 13
    }
}
