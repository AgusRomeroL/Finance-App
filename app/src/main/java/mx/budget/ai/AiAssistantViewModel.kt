package mx.budget.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.budget.ai.dispatch.IntentDispatcher
import mx.budget.ai.domain.DispatchResult
import mx.budget.ai.rag.LedgerRagUseCase
import mx.budget.ai.service.AiCoreManager

/**
 * ViewModel que expone el estado de la inferencia, orquesta
 * el caso de uso y mantiene el historial para la UI.
 * DI Manual (Factories) asumido externalmente para consistencia con Fase 1/2.
 */
class AiAssistantViewModel(
    private val aiCoreManager: AiCoreManager,
    private val ledgerRagUseCase: LedgerRagUseCase,
    private val dispatcher: IntentDispatcher,
    private val defaultHouseholdId: String = "default_household"
) : ViewModel() {

    private val _uiState = MutableStateFlow<AiAssistantUiState>(AiAssistantUiState.Idle)
    val uiState: StateFlow<AiAssistantUiState> = _uiState.asStateFlow()

    private val _chatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatHistory: StateFlow<List<ChatMessage>> = _chatHistory.asStateFlow()

    private val _lastResult = MutableStateFlow<DispatchResult?>(null)
    val lastResult: StateFlow<DispatchResult?> = _lastResult.asStateFlow()

    private val _paneFraction = MutableStateFlow(0.40f)
    val paneFraction: StateFlow<Float> = _paneFraction.asStateFlow()

    init {
        checkAiCoreAvailability()
    }

    private fun checkAiCoreAvailability() {
        viewModelScope.launch {
            val readiness = aiCoreManager.ensureReady()
            if (readiness != AiCoreManager.Readiness.Available) {
                _uiState.value = AiAssistantUiState.Unavailable("El asistente no está disponible en este dispositivo o modelo.")
            }
        }
    }

    fun sendQuery(question: String) {
        if (question.isBlank()) return
        if (_uiState.value is AiAssistantUiState.Unavailable) return

        val userMessage = ChatMessage(role = ChatMessage.Role.USER, text = question)
        _chatHistory.update { it + userMessage }

        viewModelScope.launch {
            _uiState.value = AiAssistantUiState.Thinking
            val startMs = System.currentTimeMillis()

            val rawResult = ledgerRagUseCase.invoke(question, defaultHouseholdId)
            
            rawResult.fold(
                onSuccess = { rawJson ->
                    val dispatchResult = dispatcher.dispatch(rawJson)
                    val latencyMs = System.currentTimeMillis() - startMs
                    
                    val assistantMessage = ChatMessage(
                        role = ChatMessage.Role.ASSISTANT,
                        text = "Consulta estructurada procesada. $latencyMs ms",
                        result = dispatchResult
                    )
                    
                    _chatHistory.update { it + assistantMessage }
                    _lastResult.value = dispatchResult
                    _uiState.value = AiAssistantUiState.Done(dispatchResult, latencyMs)
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

    fun setPaneFraction(fraction: Float) {
        _paneFraction.value = fraction.coerceIn(0.2f, 0.8f)
    }

    fun clearHistory() {
        _chatHistory.value = emptyList()
        _lastResult.value = null
        _uiState.value = AiAssistantUiState.Idle
    }
    
    override fun onCleared() {
        super.onCleared()
        aiCoreManager.close()
    }
}
