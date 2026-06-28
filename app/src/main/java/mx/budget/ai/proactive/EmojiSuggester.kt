package mx.budget.ai.proactive

import mx.budget.ai.service.AiCoreManager
import mx.budget.data.local.dao.CategoryDao
import mx.budget.data.local.entity.CategoryEntity

/**
 * Asigna un emoji monocromo a cada grupo de categoría y lo cachea en
 * `category.suggested_emoji`. Intenta sugerirlo con IA on-device (Gemini Nano vía
 * [AiCoreManager]) en un solo prompt batch; si el dispositivo no la soporta o el
 * parseo falla, usa el mapa determinista [CategoryEmojiFallback].
 *
 * Idempotente: solo calcula los grupos sin emoji. Se invoca lazy al abrir el
 * dashboard; cuando persiste, el Flow de `category` reemite y los pills se actualizan.
 */
class EmojiSuggester(
    private val aiCoreManager: AiCoreManager,
    private val categoryDao: CategoryDao
) {

    suspend fun ensureEmojis(groups: List<CategoryEntity>) {
        val missing = groups.filter { it.suggestedEmoji.isNullOrBlank() }
        if (missing.isEmpty()) return

        val aiByIndex = runCatching { generateBatch(missing) }.getOrDefault(emptyMap())
        missing.forEachIndexed { i, group ->
            val emoji = aiByIndex[i]?.takeIf { it.looksLikeEmoji() }
                ?: CategoryEmojiFallback.forCode(group.code)
            categoryDao.updateSuggestedEmoji(group.id, emoji)
        }
    }

    /** Pide un emoji por grupo (una línea, mismo orden). Vacío si AICore no está listo. */
    private suspend fun generateBatch(groups: List<CategoryEntity>): Map<Int, String> {
        if (aiCoreManager.ensureReady() !is AiCoreManager.Readiness.Available) return emptyMap()
        val prompt = buildString {
            append("Para cada categoría de gasto de un hogar mexicano, responde con UN SOLO ")
            append("emoji que la represente. Una línea por categoría, en el MISMO orden, ")
            append("sin números, sin texto, solo el emoji.\n\nCategorías:\n")
            groups.forEachIndexed { i, g -> append("${i + 1}. ${g.displayName}\n") }
        }
        val text = aiCoreManager.generate(prompt).getOrNull().orEmpty()
        if (text.isBlank()) return emptyMap()

        val lines = text.lineSequence()
            .map { it.trim().removePrefixNumbering() }
            .filter { it.isNotBlank() }
            .toList()
        return lines.mapIndexedNotNull { i, line ->
            line.firstEmojiOrNull()?.let { i to it }
        }.toMap()
    }

    private fun String.removePrefixNumbering(): String =
        replace(Regex("^\\s*\\d+[.)\\-:]\\s*"), "").trim()

    /** Extrae el primer cluster de emoji de la línea (heurística simple). */
    private fun String.firstEmojiOrNull(): String? {
        val trimmed = trim()
        if (trimmed.isEmpty()) return null
        // Toma hasta los primeros 4 chars si arrancan con un símbolo no-ASCII (emoji).
        return if (trimmed.looksLikeEmoji()) trimmed.take(4).trim() else null
    }

    private fun String.looksLikeEmoji(): Boolean =
        isNotBlank() && any { it.code >= 0x2190 || Character.isSurrogate(it) }
}
