package mx.budget.ui.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Envoltura mínima de [SpeechRecognizer] para el dictado de la barra de búsqueda
 * (es-MX). Degrada con gracia: si el dispositivo no soporta reconocimiento,
 * [isAvailable] es false y la UI oculta el micrófono.
 *
 * Uso: `remember { SpeechRecognizerController(context) }` + `DisposableEffect`
 * que llame [destroy] al salir de composición.
 */
class SpeechRecognizerController(private val context: Context) {

    val isAvailable: Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    fun start(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onEnd: () -> Unit
    ) {
        if (!isAvailable || listening) return
        val sr = SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it }
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) { listening = false; onEnd() }
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.firstResult()?.let(onPartial)
            }

            override fun onResults(results: Bundle?) {
                results?.firstResult()?.let(onFinal)
                listening = false
                onEnd()
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        listening = true
        sr.startListening(intent)
    }

    fun stop() {
        recognizer?.stopListening()
        listening = false
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
        listening = false
    }

    private fun Bundle.firstResult(): String? =
        getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }
}
