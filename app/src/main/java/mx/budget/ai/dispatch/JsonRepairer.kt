package mx.budget.ai.dispatch

/**
 * Reparación determinista y ultraligera de la salida cruda del LLM antes de
 * parsearla como JSON. Los modelos chicos on-device (Gemma/Nano) rompen el
 * contrato "solo JSON" de tres maneras típicas, todas recuperables:
 *
 *  1. Fences de markdown (```json ... ```) o prosa antes del objeto.
 *  2. Prosa DESPUÉS del objeto ("Espero que te sirva…") — el objeto en sí es válido.
 *  3. Truncamiento por maxOutputTokens — llaves/comillas sin cerrar.
 */
object JsonRepairer {
    fun repair(raw: String): String {
        // Quitar fences de markdown si vienen.
        var str = raw.trim()
            .removePrefix("```json").removePrefix("```JSON").removePrefix("```")
            .removeSuffix("```")
            .trim()

        val start = str.indexOf('{')
        if (start < 0) return raw
        str = str.substring(start)

        // Recorrido con conteo de llaves (ignora las que van dentro de strings).
        var openBraces = 0
        var insideString = false
        var escape = false
        var balancedEnd = -1 // índice (exclusivo) donde el primer objeto queda balanceado

        for (i in str.indices) {
            val c = str[i]
            if (escape) {
                escape = false
                continue
            }
            if (c == '\\') {
                escape = true
                continue
            }
            if (c == '"') insideString = !insideString
            if (!insideString) {
                if (c == '{') openBraces++
                if (c == '}') {
                    openBraces--
                    if (openBraces == 0) {
                        balancedEnd = i + 1
                        break
                    }
                }
            }
        }

        // Caso 2: objeto completo con basura al final → recortar.
        if (balancedEnd > 0) return str.substring(0, balancedEnd)

        // Caso 3: truncado → cerrar string abierta y llaves pendientes.
        if (insideString) str += '"'
        while (openBraces > 0) {
            str += '}'
            openBraces--
        }
        return str
    }
}
