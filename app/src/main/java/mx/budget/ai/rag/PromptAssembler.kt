package mx.budget.ai.rag

/**
 * Responsable de ensamblar todo el prompt con la plantilla correcta.
 */
class PromptAssembler(
    private val systemPrompt: String,
    private val intentSchema: String
) {
    fun assemble(contextText: String, userQuestion: String): String = """
        $systemPrompt

        ## CONTEXTO_LOCAL
        $contextText

        ## ESQUEMA_DE_SALIDA
        Responde EXCLUSIVAMENTE con un objeto JSON que valide contra el siguiente schema.
        No añadas prosa, backticks ni comentarios. Si no encuentras la respuesta en el
        contexto, usa intent="UNKNOWN" y explica en "reason".

        $intentSchema

        ## PREGUNTA_DEL_USUARIO
        $userQuestion

        ## END
    """.trimIndent()
}
