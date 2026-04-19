package mx.budget.ai

import mx.budget.ai.domain.DispatchResult
import java.util.UUID

/**
 * Entidad que representa un mensaje intercambiado entre el Actor y el LLM en la UI
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val result: DispatchResult? = null
) {
    enum class Role {
        USER, ASSISTANT, ERROR
    }
}
