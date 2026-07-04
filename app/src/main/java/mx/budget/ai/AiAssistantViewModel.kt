package mx.budget.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.budget.ai.dispatch.IntentDispatcher
import mx.budget.ai.domain.AssistantResponse
import mx.budget.ai.domain.DispatchResult
import mx.budget.ai.rag.LedgerRagUseCase
import mx.budget.ai.service.LlmReadiness
import mx.budget.ai.service.OnDeviceLlm

/**
 * ViewModel del asistente reactivo (MVP Fase 4). Orquesta el pipeline RAG
 * (pregunta libre → LLM on-device → intent JSON → dispatcher determinista) y
 * mantiene el historial para la UI.
 *
 * **Fallback determinista sin LLM**: si el [OnDeviceLlm] no está disponible
 * (emulador, sin AICore/Gemma), [llmAvailable] queda en false y la UI ofrece
 * chips de preguntas predefinidas que van directo al [IntentDispatcher] vía
 * [sendPredefined] — el chat sigue siendo útil con datos reales.
 */
class AiAssistantViewModel(
    private val llm: OnDeviceLlm,
    private val ledgerRagUseCase: LedgerRagUseCase,
    private val dispatcher: IntentDispatcher,
    private val defaultHouseholdId: String
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

    init {
        viewModelScope.launch {
            // ensureReady devuelve Pending mientras el engine carga (Gemma en
            // CPU tarda). Reintenta hasta resolver Available/Unavailable para
            // que la pregunta libre se habilite sola cuando termine la carga.
            var readiness = runCatching { llm.ensureReady() }.getOrNull() ?: LlmReadiness.Unavailable
            var attempts = 0
            while (readiness is LlmReadiness.Pending && attempts < 60) {
                kotlinx.coroutines.delay(2_000)
                readiness = runCatching { llm.ensureReady() }.getOrNull() ?: LlmReadiness.Unavailable
                attempts++
            }
            _llmAvailable.value = readiness == LlmReadiness.Available
        }
    }

    /** Pregunta libre → pipeline RAG completo (requiere LLM disponible). */
    fun sendQuery(question: String) {
        if (question.isBlank()) return
        if (!_llmAvailable.value) return

        _chatHistory.update { it + ChatMessage(role = ChatMessage.Role.USER, text = question) }

        viewModelScope.launch {
            _uiState.value = AiAssistantUiState.Thinking
            val startMs = System.currentTimeMillis()

            ledgerRagUseCase.invoke(question, defaultHouseholdId).fold(
                onSuccess = { rawJson ->
                    // La pregunta original habilita el fallback heurístico del
                    // dispatcher cuando la salida del LLM no parsea como intent.
                    val dispatchResult = dispatcher.dispatch(rawJson, originalQuestion = question)
                    finish(dispatchResult, System.currentTimeMillis() - startMs)
                },
                onFailure = { error ->
                    _uiState.value = AiAssistantUiState.Error(error.message ?: "Malla de razonamiento fallida")
                    _chatHistory.update {
                        it + ChatMessage(role = ChatMessage.Role.ERROR, text = "Lo siento, ocurrió un error interno.")
                    }
                }
            )
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
        _chatHistory.value = emptyList()
        _lastResult.value = null
        _uiState.value = AiAssistantUiState.Idle
    }

    // NOTA: no se hace llm.close() en onCleared — el OnDeviceLlm es el HybridLlm
    // compartido de la app (lo usa también la capa proactiva); su ciclo de vida
    // es el del proceso, no el de este ViewModel.
}
