package mx.budget.ai.proactive

/**
 * Mapa determinista código-de-grupo → emoji, usado como **fallback** cuando la
 * sugerencia con IA on-device (AICore) no está disponible o aún no se ha
 * calculado. Lo consume tanto la UI de filtros (render inmediato) como el
 * [EmojiSuggester] (semilla y red de seguridad).
 *
 * Los códigos son los grupos top-level (parentId==null) sembrados del Excel.
 */
object CategoryEmojiFallback {

    private val byCode: Map<String, String> = mapOf(
        "INGRESOS" to "💵",
        "HOUSING" to "🏠",
        "TRANSPORTATION" to "🚗",
        "SEGUROS_MEDICOS" to "🏥",
        "FOOD" to "🍔",
        "PETS" to "🐾",
        "ENTERTAINMENT" to "🎬",
        "LOANS" to "💳",
        "TRANSFERENCIAS_FAMILIARES" to "👪",
        "ESCUELA" to "🎓",
        "SAVINGS" to "🐖",
        "GIFTS" to "🎁",
        "LEGAL" to "⚖️",
        "PERSONAL_CARE" to "🧴",
        "SERVICIOS_EXTERNOS" to "🛠️",
        "PRESTAMOS_OTORGADOS" to "🤝",
        "OTHER" to "🏷️"
    )

    /** Emoji de respaldo para un código de categoría (prefijo del code para hojas). */
    fun forCode(code: String?): String {
        if (code == null) return DEFAULT
        byCode[code]?.let { return it }
        // Para hojas tipo "HOUSING.TELEFONO", usar el prefijo del grupo.
        val root = code.substringBefore('.')
        return byCode[root] ?: DEFAULT
    }

    const val DEFAULT = "🏷️"
}
