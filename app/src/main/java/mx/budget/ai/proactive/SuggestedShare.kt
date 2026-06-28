package mx.budget.ai.proactive

import kotlinx.serialization.Serializable

/**
 * Forma serializable de una porción de atribución sugerida, persistida como JSON
 * en `attribution_review.suggested_json` (Apéndice F.3.3).
 */
@Serializable
data class SuggestedShare(
    val memberId: String,
    val shareBps: Int
)
