package mx.budget.core

import java.text.Normalizer

/**
 * Función de extensión para eliminar acentos (diacríticos) de una cadena.
 * Útil para comparaciones de texto difusas (matchers de categorías, miembros, etc.).
 */
fun String.unaccent(): String {
    val temp = Normalizer.normalize(this, Normalizer.Form.NFD)
    return Regex("\\p{InCombiningDiacriticalMarks}+").replace(temp, "")
}
