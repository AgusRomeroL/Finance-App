package mx.budget.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.budget.ai.dispatch.IntentDispatcher
import mx.budget.ai.domain.AssistantResponse
import mx.budget.ai.domain.DispatchResult
import mx.budget.ai.rag.LedgerRagUseCase
import mx.budget.ai.rag.OpenAnalysisAnswerer
import mx.budget.ai.rag.QuestionClassifier
import mx.budget.ai.service.LlmReadiness
import mx.budget.ai.service.OnDeviceLlm
import mx.budget.ai.suggest.SuggestedQuestion
import mx.budget.ai.suggest.SuggestedQuestionEngine

/**
 * ViewModel del asistente reactivo (MVP Fase 4 + ruta OPEN_ANALYSIS). Orquesta
 * el pipeline RAG (pregunta libre → LLM on-device → intent JSON → dispatcher
 * determinista) y mantiene el historial para la UI.
 *
 * **Ruta OPEN_ANALYSIS (paquete A1)**: las preguntas abiertas/analíticas se
 * detectan ANTES de intentar el intent schema ([QuestionClassifier.isOpenAnalysis])
 * y van directo al [OpenAnalysisAnswerer]; además, cuando el dispatch se rinde
 * (OutOfScope/ParseError/Unknown sin razón) se corre una segunda pasada por la
 * misma ruta en vez de mostrar el texto enlatado de capacidades.
 *
 * **Fallback determinista sin LLM**: si el [OnDeviceLlm] no está disponible
 * (emulador, sin AICore/Gemma), [llmAvailable] queda en false pero la pregunta
 * libre SIGUE funcionando: se adivina el intent con la heurística del
 * [IntentDispatcher] y, si no aplica, el [OpenAnalysisAnswerer] responde con
 * un insight determinista por plantillas — nunca el texto de capacidades.
 */
class AiAssistantViewModel(
    private val llm: OnDeviceLlm,
    private val ledgerRagUseCase: LedgerRagUseCase,
    private val dispatcher: IntentDispatcher,
    private val openAnalysisAnswerer: OpenAnalysisAnswerer,
    private val defaultHouseholdId: String,
    private val suggestedQuestionEngine: SuggestedQuestionEngine? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AiAssistantUiState>(AiAssistantUiState.Idle)
    val uiState: StateFlow<AiAssistantUiState> = _uiState.asStateFlow()

    private val _chatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatHistory: StateFlow<List<ChatMessage>> = _chatHistory.asStateFlow()

    private val _lastResult = MutableStateFlow<DispatchResult?>(null)
    val lastResult: StateFlow<DispatchResult?> = _lastResult.asStateFlow()

    private val _paneFraction = MutableStateFlow(0.40f)
    val paneFraction: StateFlow<Float> = _paneFraction.asStateFlow()

    /** `true` si hay LLM on-device listo (pregunta libre habilitada). */
    private val _llmAvailable = MutableStateFlow(false)
    val llmAvailable: StateFlow<Boolean> = _llmAvailable.asStateFlow()

    /**
     * Pills sugeridos DINÁMICOS (Fase 3): generados desde el estado real del
     * presupuesto por [SuggestedQuestionEngine]. Lista vacía → la UI cae a los
     * chips estáticos históricos.
     */
    private val _suggestedQuestions = MutableStateFlow<List<SuggestedQuestion>>(emptyList())
    val suggestedQuestions: StateFlow<List<SuggestedQuestion>> = _suggestedQuestions.asStateFlow()

    private var llmPollJob: Job? = null

    init {
        ensureLlmReadinessChecked()
        seedWelcome()
        refreshSuggestions()
    }

    /** Regenera los pills (al abrir el sheet y tras cada respuesta). */
    fun refreshSuggestions() {
        val engine = suggestedQuestionEngine ?: return
        viewModelScope.launch {
            runCatching { engine.suggest(defaultHouseholdId) }
                .onSuccess { _suggestedQuestions.value = it }
            // onFailure: se conserva la lista previa (o vacía → chips estáticos).
        }
    }

    /**
     * El historial NUNCA arranca vacío: el asistente saluda primero y las
     * pills de atajo quedan debajo — sin pantalla en blanco al abrir el chat.
     */
    private fun seedWelcome() {
        _chatHistory.value = listOf(
            ChatMessage(role = ChatMessage.Role.ASSISTANT, text = WELCOME_MESSAGE)
        )
    }

    /**
     * Dispara (o reanuda) el sondeo de disponibilidad del LLM. Segura de
     * llamar varias veces — si ya está disponible o hay un sondeo en curso,
     * no hace nada.
     *
     * La UI la invoca de nuevo cada vez que se abre el chat: así, si un
     * sondeo previo terminó en `Unavailable` (p. ej. porque el modelo aún no
     * se había empujado por adb, o un fallo transitorio de inicialización),
     * el usuario puede recuperar la pregunta libre sin reiniciar la app.
     *
     * Mientras el engine está `Pending` (Gemma en CPU puede tardar varios
     * minutos en frío, sobre todo la primera carga tras instalar), reintenta
     * con backoff exponencial SIN un límite de intentos que lo dé por
     * perdido — el límite anterior (60 intentos × 2 s ≈ 120 s) apagaba la
     * pregunta libre para siempre si la carga tardaba más, sin forma de
     * recuperarla salvo reiniciar el proceso.
     */
    fun ensureLlmReadinessChecked() {
        if (_llmAvailable.value) return
        if (llmPollJob?.isActive == true) return
        llmPollJob = viewModelScope.launch {
            var delayMs = LLM_POLL_INITIAL_DELAY_MS
            while (true) {
                val readiness = runCatching { llm.ensureReady() }.getOrElse { LlmReadiness.Unavailable }
                when (readiness) {
                    LlmReadiness.Available -> {
                        _llmAvailable.value = true
                        return@launch
                    }
                    LlmReadiness.Unavailable -> {
                        // Terminal por ahora (sin modelo/init fallido) — no se
                        // insiste en bucle, pero ensureLlmReadinessChecked()
                        // puede relanzar el sondeo más tarde.
                        _llmAvailable.value = false
                        return@launch
                    }
                    is LlmReadiness.Pending -> {
                        delay(delayMs)
                        delayMs = (delayMs * 3 / 2).coerceAtMost(LLM_POLL_MAX_DELAY_MS)
                    }
                }
            }
        }
    }

    /**
     * Pregunta libre. Con LLM → pipeline RAG completo; sin LLM → heurística
     * determinista del dispatcher. En ambos casos, si la pregunta es abierta o
     * el dispatch se rinde, la ruta OPEN_ANALYSIS responde con un análisis
     * real (LLM sobre digest, o plantillas locales).
     */
    fun sendQuery(question: String) {
        if (question.isBlank()) return

        _chatHistory.update { it + ChatMessage(role = ChatMessage.Role.USER, text = question) }

        viewModelScope.launch {
            _uiState.value = AiAssistantUiState.Thinking
            val startMs = System.currentTimeMillis()

            // Pregunta abierta/analítica → directo a OPEN_ANALYSIS, sin gastar
            // una pasada del LLM (lento) intentando el intent schema primero.
            if (QuestionClassifier.isOpenAnalysis(question)) {
                runOpenAnalysis(question, startMs)
                return@launch
            }

            if (_llmAvailable.value) {
                // Memoria conversacional corta: las últimas preguntas del usuario
                // (excluyendo la actual, ya añadida al historial) permiten
                // follow-ups tipo "¿y el mes pasado?".
                val previousQuestions = _chatHistory.value
                    .filter { it.role == ChatMessage.Role.USER }
                    .map { it.text }
                    .dropLast(1)
                    .takeLast(3)
                ledgerRagUseCase.invoke(question, defaultHouseholdId, previousQuestions).fold(
                    onSuccess = { rawJson ->
                        // La pregunta original habilita el fallback heurístico del
                        // dispatcher cuando la salida del LLM no parsea como intent.
                        val result = dispatcher.dispatch(rawJson, originalQuestion = question)
                        if (needsOpenAnalysis(result)) runOpenAnalysis(question, startMs)
                        else finish(result, System.currentTimeMillis() - startMs)
                    },
                    onFailure = {
                        // El RAG de intents falló (p. ej. generate() roto):
                        // la segunda pasada aún puede salvar la respuesta.
                        runOpenAnalysis(question, startMs)
                    }
                )
            } else {
                // Sin LLM: dispatch con salida vacía → cae directo al
                // HeuristicIntentGuesser sobre la pregunta original.
                val result = runCatching { dispatcher.dispatch("", originalQuestion = question) }
                    .getOrElse { DispatchResult.Unknown(it.message ?: "Error interno") }
                if (needsOpenAnalysis(result)) runOpenAnalysis(question, startMs)
                else finish(result, System.currentTimeMillis() - startMs)
            }
        }
    }

    /**
     * Atajo de análisis abierto (chip "¿Algún patrón inusual?"): va directo a
     * la ruta OPEN_ANALYSIS. Funciona con y sin LLM (sin LLM → insight
     * determinista por plantillas).
     */
    fun sendOpenAnalysis(question: String) {
        if (question.isBlank()) return
        _chatHistory.update { it + ChatMessage(role = ChatMessage.Role.USER, text = question) }
        viewModelScope.launch {
            _uiState.value = AiAssistantUiState.Thinking
            runOpenAnalysis(question, System.currentTimeMillis())
        }
    }

    /**
     * Chip predefinido → bypass del LLM: el intent llega YA estructurado y va
     * directo al dispatcher determinista. Funciona sin AICore/Gemma.
     */
    fun sendPredefined(label: String, response: AssistantResponse) {
        _chatHistory.update { it + ChatMessage(role = ChatMessage.Role.USER, text = label) }
        viewModelScope.launch {
            _uiState.value = AiAssistantUiState.Thinking
            val startMs = System.currentTimeMillis()
            val result = runCatching { dispatcher.dispatch(response) }
                .getOrElse { DispatchResult.Unknown(it.message ?: "Error interno") }
            finish(result, System.currentTimeMillis() - startMs)
        }
    }

    /** El dispatch se rindió sin nada útil que decir → segunda pasada. */
    private fun needsOpenAnalysis(result: DispatchResult): Boolean = when (result) {
        DispatchResult.OutOfScope -> true
        is DispatchResult.ParseError -> true
        is DispatchResult.Unknown -> result.reason.isBlank()
        else -> false
    }

    private suspend fun runOpenAnalysis(question: String, startMs: Long) {
        val answer = runCatching {
            openAnalysisAnswerer.answer(question, defaultHouseholdId, useLlm = _llmAvailable.value)
        }.getOrElse { OpenAnalysisAnswerer.Answer.Failed(it.message ?: "Error interno") }

        when (answer) {
            is OpenAnalysisAnswerer.Answer.Llm -> finish(
                DispatchResult.OpenAnalysis(answer.text, deterministic = false),
                System.currentTimeMillis() - startMs,
            )
            is OpenAnalysisAnswerer.Answer.Deterministic -> finish(
                DispatchResult.OpenAnalysis(answer.text, deterministic = true),
                System.currentTimeMillis() - startMs,
            )
            is OpenAnalysisAnswerer.Answer.Failed -> {
                _uiState.value = AiAssistantUiState.Error(answer.reason)
                _chatHistory.update {
                    it + ChatMessage(role = ChatMessage.Role.ERROR, text = answer.reason)
                }
            }
        }
    }

    private fun finish(result: DispatchResult, latencyMs: Long) {
        _chatHistory.update {
            it + ChatMessage(role = ChatMessage.Role.ASSISTANT, text = "", result = result)
        }
        _lastResult.value = result
        _uiState.value = AiAssistantUiState.Done(result, latencyMs)
    }

    fun setPaneFraction(fraction: Float) {
        _paneFraction.value = fraction.coerceIn(0.2f, 0.8f)
    }

    fun clearHistory() {
        seedWelcome()
        _lastResult.value = null
        _uiState.value = AiAssistantUiState.Idle
    }

    // NOTA: no se hace llm.close() en onCleared — el OnDeviceLlm es el HybridLlm
    // compartido de la app (lo usa también la capa proactiva); su ciclo de vida
    // es el del proceso, no el de este ViewModel.

    private companion object {
        const val LLM_POLL_INITIAL_DELAY_MS = 2_000L
        const val LLM_POLL_MAX_DELAY_MS = 15_000L

        /**
         * Único lugar donde vive la guía de capacidades: como bienvenida, nunca
         * como respuesta a una pregunta.
         */
        const val WELCOME_MESSAGE =
            "Hola, soy tu asistente del presupuesto. Pregúntame lo que quieras sobre " +
                "tus finanzas — por ejemplo:\n" +
                "· En qué gastan más\n" +
                "· Cuánto queda en una categoría\n" +
                "· Quién gasta más\n" +
                "· El saldo de una cuenta\n" +
                "· Cómo va la quincena\n" +
                "O prueba uno de los atajos de abajo."
    }
}
