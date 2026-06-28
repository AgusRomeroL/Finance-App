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

/**
 * Similitud de Jaro-Winkler entre dos cadenas, en [0.0, 1.0].
 *
 * Usada por el canonicalizador de conceptos y el resolutor de aliases para
 * fundir variantes de escritura ("santi" ↔ "santiago", typos cortos) sin
 * dependencias externas. Implementación estándar: Jaro + boost por prefijo
 * común (hasta 4 chars, factor 0.1). Devuelve 1.0 si ambas son iguales,
 * 0.0 si alguna está vacía y la otra no.
 *
 * Se asume que las entradas ya vienen normalizadas (lower + [unaccent]).
 */
fun jaroWinkler(s1: String, s2: String): Double {
    if (s1 == s2) return 1.0
    if (s1.isEmpty() || s2.isEmpty()) return 0.0

    val matchDistance = (maxOf(s1.length, s2.length) / 2) - 1
    val s1Matches = BooleanArray(s1.length)
    val s2Matches = BooleanArray(s2.length)

    var matches = 0
    for (i in s1.indices) {
        val start = maxOf(0, i - matchDistance)
        val end = minOf(i + matchDistance + 1, s2.length)
        for (j in start until end) {
            if (s2Matches[j]) continue
            if (s1[i] != s2[j]) continue
            s1Matches[i] = true
            s2Matches[j] = true
            matches++
            break
        }
    }
    if (matches == 0) return 0.0

    // Transposiciones.
    var transpositions = 0
    var k = 0
    for (i in s1.indices) {
        if (!s1Matches[i]) continue
        while (!s2Matches[k]) k++
        if (s1[i] != s2[k]) transpositions++
        k++
    }
    val m = matches.toDouble()
    val jaro = ((m / s1.length) + (m / s2.length) + ((m - transpositions / 2.0) / m)) / 3.0

    // Boost de prefijo común (Winkler), tope de 4 caracteres.
    var prefix = 0
    val maxPrefix = minOf(4, minOf(s1.length, s2.length))
    while (prefix < maxPrefix && s1[prefix] == s2[prefix]) prefix++

    return jaro + prefix * 0.1 * (1.0 - jaro)
}
