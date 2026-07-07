package mx.budget.ai.dispatch

import kotlinx.serialization.json.Json
import mx.budget.core.jaroWinkler
import mx.budget.core.unaccent
import mx.budget.data.local.entity.CategoryEntity
import mx.budget.data.local.entity.InstallmentPlanEntity
import mx.budget.data.local.entity.MemberEntity
import mx.budget.data.local.entity.PaymentMethodEntity

/**
 * Traduce cadenas extraídas por el LLM (o por la heurística) a entidades
 * concretas de Room en tres niveles, del más estricto al más laxo:
 *
 * 1. **Igualdad** normalizada (lower + unaccent) contra nombre/código/aliases.
 * 2. **Contención** en cualquier dirección ("bancomer" ⊂ "BBVA Bancomer").
 * 3. **Jaro-Winkler ≥ [FUZZY_THRESHOLD]** (typos: "bancomner" → "Bancomer"),
 *    reutilizando el mismo `jaroWinkler` de `mx.budget.core` que ya usa el
 *    ConceptCanonicalizer — se elige el mejor score, no el primero que pase.
 *
 * Para miembros, los `shortAliases` (JSON) cuentan en los tres niveles — antes
 * solo se miraba `displayName` y "Pau" no resolvía a "Paulina".
 */
class AliasResolver(
    private val members: List<MemberEntity> = emptyList(),
    private val categories: List<CategoryEntity> = emptyList(),
    private val wallets: List<PaymentMethodEntity> = emptyList(),
    private val installments: List<InstallmentPlanEntity> = emptyList()
) {

    fun resolveMember(alias: String?): MemberEntity? =
        resolve(alias, members) { m ->
            listOf(m.displayName) + parseAliases(m.shortAliases)
        }

    fun resolveCategory(alias: String?): CategoryEntity? =
        resolve(alias, categories) { c -> listOf(c.code, c.displayName) }

    fun resolveWallet(alias: String?): PaymentMethodEntity? =
        resolve(alias, wallets) { w -> listOf(w.displayName) }

    fun resolveInstallmentPlan(alias: String?): InstallmentPlanEntity? =
        resolve(alias, installments) { i -> listOf(i.displayName) }

    /** Resolución en 3 niveles: igualdad → contención → mejor Jaro-Winkler. */
    private fun <T> resolve(alias: String?, entities: List<T>, forms: (T) -> List<String>): T? {
        if (alias.isNullOrBlank()) return null
        val needle = alias.norm()
        if (needle.isBlank()) return null
        val normalized = entities.map { e -> e to forms(e).map { it.norm() }.filter { it.isNotBlank() } }

        normalized.firstOrNull { (_, fs) -> fs.any { it == needle } }?.let { return it.first }
        normalized.firstOrNull { (_, fs) -> fs.any { it.contains(needle) || needle.contains(it) } }
            ?.let { return it.first }

        return normalized
            .map { (e, fs) -> e to (fs.maxOfOrNull { jaroWinkler(it, needle) } ?: 0.0) }
            .filter { it.second >= FUZZY_THRESHOLD }
            .maxByOrNull { it.second }
            ?.first
    }

    // ── Búsqueda inversa: la entidad mencionada DENTRO de un texto libre ─────
    // (las usa el fallback heurístico cuando el LLM no produjo un intent
    // parseable). Se prefiere el nombre MÁS LARGO contenido en la pregunta
    // para que "comida gatos" gane sobre "comida".

    fun findCategoryIn(text: String): CategoryEntity? =
        findByName(text, categories) { it.displayName }

    fun findMemberIn(text: String): MemberEntity? =
        findByNames(text, members) { m -> listOf(m.displayName) + parseAliases(m.shortAliases) }

    fun findWalletIn(text: String): PaymentMethodEntity? =
        findByName(text, wallets) { it.displayName }

    fun findInstallmentPlanIn(text: String): InstallmentPlanEntity? =
        findByName(text, installments) { it.displayName }

    private fun <T> findByName(text: String, entities: List<T>, name: (T) -> String): T? =
        findByNames(text, entities) { listOf(name(it)) }

    private fun <T> findByNames(text: String, entities: List<T>, names: (T) -> List<String>): T? {
        val haystack = text.norm()
        return entities
            .mapNotNull { e ->
                val hit = names(e)
                    .map { it.norm() }
                    .filter { it.length >= 3 && haystack.contains(it) }
                    .maxByOrNull { it.length }
                if (hit == null) null else e to hit.length
            }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun String.norm() = lowercase().unaccent().trim()

    private fun parseAliases(raw: String): List<String> =
        runCatching { Json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())

    private companion object {
        /** Umbral fuzzy: 0.88 tolera typos de 1-2 letras sin fundir nombres distintos. */
        const val FUZZY_THRESHOLD = 0.88
    }
}
