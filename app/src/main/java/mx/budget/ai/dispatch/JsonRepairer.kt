package mx.budget.ai.dispatch

/**
 * Intento determinista y ultraligero de reparar un JSON truncado por
 * Nano si la salida fue más larga que maxOutputTokens.
 */
object JsonRepairer {
    fun repair(raw: String): String {
        var str = raw.trim()
        if (!str.startsWith("{")) {
            val idx = str.indexOf("{")
            str = if (idx >= 0) str.substring(idx) else return raw
        }
        
        // Simple brace counting technique (very naïve, assumes no escaped braces inside strings)
        var openBraces = 0
        var insideString = false
        var escape = false

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
                if (c == '}') openBraces--
            }
        }

        if (insideString) str += '"'
        while (openBraces > 0) {
            str += '}'
            openBraces--
        }

        return str
    }
}
