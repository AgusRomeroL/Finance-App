package mx.budget.ai

import mx.budget.ai.domain.DispatchResult

/**
 * Máquina de estados para la pantalla interactiva del asistente.
 */
sealed class AiAssistantUiState {
    object Idle : AiAssistantUiState()
    data class Unavailable(val reason: String) : AiAssistantUiState()
    object Thinking : AiAssistantUiState()
    data class Generating(val streamedText: String) : AiAssistantUiState()
    data class Done(val result: DispatchResult, val latencyMs: Long) : AiAssistantUiState()
    data class Error(val message: String) : AiAssistantUiState()
}
