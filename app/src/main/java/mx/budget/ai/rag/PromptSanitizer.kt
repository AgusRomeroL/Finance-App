package mx.budget.ai.rag

/**
 * Limpia y recorta el input del usuario para evitar ataques de inyección básica
 * y mantener bajo control el consumo de tokens.
 */
object PromptSanitizer {
    fun sanitize(input: String): String {
        // Strip out control headers that an attacker might try to inject
        var cleaned = input.replace(Regex("(?i)^(system:|assistant:|user:|<\\|.*\\|>|##.*)"), "")
            .replace("\\n", " ")
            .trim()
        
        // Truncar preventivamente a un aproximado de 400 tokens (usando factor x4.5 chars por token)
        if (cleaned.length > 1800) {
            cleaned = cleaned.substring(0, 1800)
        }
        return cleaned
    }
}
