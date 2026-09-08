package mx.budget.ai

import mx.budget.ai.domain.DispatchResult

/** En qué anda el asistente mientras el usuario espera. */
enum class ThinkingPhase {
    /** Consultando el ledger para armar el contexto. Dura poco. */
    READING_LEDGER,

    /** El modelo está generando. Es la parte lenta en CPU. */
    REASONING,
}

/**
 * Máquina de estados para la pantalla interactiva del asistente.
 *
 * [Thinking] y [Generating] cargan el instante de arranque para que la interfaz
 * pueda contar los segundos: con Gemma en CPU la espera se mide en decenas de
 * segundos y un spinner mudo no dice nada.
 */
sealed class AiAssistantUiState {
    object Idle : AiAssistantUiState()
    data class Unavailable(val reason: String) : AiAssistantUiState()
    data class Thinking(val phase: ThinkingPhase, val startedAtMs: Long) : AiAssistantUiState()
    data class Generating(val streamedText: String, val startedAtMs: Long) : AiAssistantUiState()
    data class Done(val result: DispatchResult, val latencyMs: Long) : AiAssistantUiState()
    data class Error(val message: String) : AiAssistantUiState()
}
