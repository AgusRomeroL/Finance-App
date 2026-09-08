package mx.budget.ai.rag

/**
 * Limpia y recorta el input del usuario antes de meterlo en el prompt: evita la
 * inyección básica y mantiene bajo control el consumo de tokens.
 *
 * Dos defectos que tenía y que el chat en texto libre hacía explotables:
 * el regex de cabeceras no era MULTILINE, así que solo miraba la primera línea y
 * un `\n## CONTEXTO_LOCAL ...` pegado abajo pasaba intacto; y el `replace("\\n", " ")`
 * sustituía la secuencia literal barra-n, no el salto de línea real, de modo que el
 * texto multilínea llegaba tal cual al prompt.
 */
object PromptSanitizer {

    /** Cabeceras de rol y marcadores de sección, en CUALQUIER línea. */
    private val CONTROL_HEADERS =
        Regex("(?im)^\\s*(system:|assistant:|user:|<\\|.*\\|>|#{2,}.*)$")

    /** Aproximado de 400 tokens con el factor de 4.5 caracteres por token. */
    private const val MAX_CHARS = 1800

    fun sanitize(input: String): String {
        var cleaned = CONTROL_HEADERS.replace(input, " ")
        // Saltos de línea REALES (y los tabuladores) a espacio: el prompt se arma
        // por líneas y una pregunta multilínea puede fingir una sección nueva.
        cleaned = cleaned.replace(Regex("[\\r\\n\\t]+"), " ")
        cleaned = cleaned.replace(Regex(" {2,}"), " ").trim()
        return if (cleaned.length > MAX_CHARS) cleaned.substring(0, MAX_CHARS) else cleaned
    }
}
