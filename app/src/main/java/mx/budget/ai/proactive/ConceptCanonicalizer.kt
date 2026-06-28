package mx.budget.ai.proactive

import kotlinx.serialization.json.Json
import mx.budget.core.jaroWinkler
import mx.budget.core.unaccent
import mx.budget.data.local.entity.MemberEntity

/**
 * Convierte el texto libre de `expense.concept` en una **clave canónica** estable
 * que funde variantes de escritura del mismo gasto (Apéndice F.3.2):
 *
 * - "Colegiatura Santi"  → "colegiatura|m:<idSanti>"
 * - "Santiago Colegiatura" → "colegiatura|m:<idSanti>"   (misma clave)
 *
 * Es **determinista, sin ML**: normaliza, expande nombres de miembro a marcadores
 * canónicos vía aliases + Jaro-Winkler, ordena tokens (invariante al orden de
 * palabras) y aplica stemming ligero conservador. Con ~800 gastos y nombres
 * propios conocidos del hogar, esto supera a cualquier modelo entrenable.
 *
 * @param members miembros del hogar; sus `shortAliases` y `displayName` alimentan
 *  la expansión de tokens de persona.
 */
class ConceptCanonicalizer(members: List<MemberEntity>) {

    /** (marcador canónico "m:<id>", lista de formas normalizadas que lo disparan). */
    private data class MemberAlias(val marker: String, val forms: List<String>)

    private val memberAliases: List<MemberAlias> = members.map { m ->
        val parsed = runCatching {
            Json.decodeFromString<List<String>>(m.shortAliases)
        }.getOrDefault(emptyList())
        val forms = (parsed + m.displayName)
            .map { it.lowercase().unaccent().trim() }
            .filter { it.isNotBlank() }
            .distinct()
        MemberAlias(marker = "m:${m.id}", forms = forms)
    }

    /**
     * Clave canónica de un concepto, o `null` si está en blanco.
     */
    fun canonicalize(concept: String): String? {
        val normalized = normalize(concept)
        if (normalized.isBlank()) return null

        val tokens = normalized.split(' ').filter { it.isNotBlank() }
        val canonicalTokens = tokens.map { token ->
            resolveMemberMarker(token) ?: stem(token)
        }
        // Ordenar + deduplicar para invarianza de orden de palabras.
        return canonicalTokens.distinct().sorted().joinToString("|")
    }

    /** lower → unaccent → solo alfanumérico+espacio → colapsa espacios → trim. */
    private fun normalize(s: String): String =
        s.lowercase()
            .unaccent()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /**
     * Si el token corresponde a un miembro, devuelve su marcador "m:<id>".
     * Match exacto contra cualquier alias (cualquier longitud) o fuzzy
     * (Jaro-Winkler ≥ 0.92) restringido a tokens de ≥ 4 chars para no fundir
     * nombres cortos con palabras comunes.
     */
    private fun resolveMemberMarker(token: String): String? {
        for (alias in memberAliases) {
            if (alias.forms.any { it == token }) return alias.marker
        }
        if (token.length >= 4) {
            for (alias in memberAliases) {
                if (alias.forms.any { it.length >= 4 && jaroWinkler(it, token) >= 0.92 }) {
                    return alias.marker
                }
            }
        }
        return null
    }

    /** Stemming español muy conservador: solo plurales triviales sobre tokens largos. */
    private fun stem(token: String): String = when {
        token.length <= 4 -> token
        token.endsWith("es") -> token.dropLast(2)
        token.endsWith("s") -> token.dropLast(1)
        else -> token
    }

    companion object {
        /**
         * Agrupa claves de primera pasada que son casi idénticas (typos no cubiertos
         * por aliases) y devuelve un mapa `clave → claveRepresentante` para que todas
         * las variantes de un cluster compartan exactamente la misma `concept_canonical`
         * (requisito para que las queries de igualdad del motor funcionen).
         *
         * @param keyFrequencies clave canónica → nº de gastos con esa clave.
         */
        fun clusterKeys(keyFrequencies: Map<String, Int>): Map<String, String> {
            val keys = keyFrequencies.keys.toList()
            // Union-Find simple.
            val parent = HashMap<String, String>().apply { keys.forEach { put(it, it) } }
            fun find(x: String): String {
                var root = x
                while (parent[root] != root) root = parent.getValue(root)
                var cur = x
                while (parent[cur] != cur) { val next = parent.getValue(cur); parent[cur] = root; cur = next }
                return root
            }
            fun union(a: String, b: String) {
                val ra = find(a); val rb = find(b)
                if (ra != rb) parent[ra] = rb
            }

            for (i in keys.indices) {
                for (j in i + 1 until keys.size) {
                    if (areSimilar(keys[i], keys[j])) union(keys[i], keys[j])
                }
            }

            // Representante de cada cluster = la clave más frecuente.
            val clusters = keys.groupBy { find(it) }
            val result = HashMap<String, String>()
            for ((_, members) in clusters) {
                val rep = members.maxByOrNull { keyFrequencies[it] ?: 0 } ?: continue
                members.forEach { result[it] = rep }
            }
            return result
        }

        /** Similitud entre dos claves: Jaccard de tokens ≥ 0.6 y Jaro-Winkler ≥ 0.9. */
        private fun areSimilar(a: String, b: String): Boolean {
            if (a == b) return true
            val ta = a.split('|').toSet()
            val tb = b.split('|').toSet()
            val inter = ta.intersect(tb).size.toDouble()
            val union = ta.union(tb).size.toDouble()
            val jaccard = if (union == 0.0) 0.0 else inter / union
            return jaccard >= 0.6 && jaroWinkler(a, b) >= 0.9
        }
    }
}
