package mx.budget.ai.dispatch

import mx.budget.core.unaccent
import mx.budget.data.local.entity.CategoryEntity
import mx.budget.data.local.entity.InstallmentPlanEntity
import mx.budget.data.local.entity.MemberEntity
import mx.budget.data.local.entity.PaymentMethodEntity

/**
 * Traduce cadenas extraídas por Gemini a IDs concretos de Room
 * mediante match difuso y aliases predefinidos.
 */
class AliasResolver(
    private val members: List<MemberEntity> = emptyList(), // Provide via constructor or fetch
    private val categories: List<CategoryEntity> = emptyList(),
    private val wallets: List<PaymentMethodEntity> = emptyList(),
    private val installments: List<InstallmentPlanEntity> = emptyList()
) {
    fun resolveMember(alias: String?): MemberEntity? {
        if (alias.isNullOrBlank()) return null
        val needle = alias.lowercase().unaccent()
        return members.firstOrNull { m ->
            m.displayName.lowercase().unaccent() == needle ||
            // Fallback simplistic similarity
            m.displayName.lowercase().unaccent().contains(needle)
        }
    }

    fun resolveCategory(alias: String?): CategoryEntity? {
        if (alias.isNullOrBlank()) return null
        val needle = alias.lowercase().unaccent()
        return categories.firstOrNull { c ->
            c.code.lowercase().unaccent() == needle ||
            c.name.lowercase().unaccent() == needle ||
            c.name.lowercase().unaccent().contains(needle)
        }
    }

    fun resolveWallet(alias: String?): PaymentMethodEntity? {
        if (alias.isNullOrBlank()) return null
        val needle = alias.lowercase().unaccent()
        return wallets.firstOrNull { w ->
            w.displayName.lowercase().unaccent() == needle ||
            w.displayName.lowercase().unaccent().contains(needle)
        }
    }

    fun resolveInstallmentPlan(alias: String?): InstallmentPlanEntity? {
        if (alias.isNullOrBlank()) return null
        val needle = alias.lowercase().unaccent()
        return installments.firstOrNull { i ->
            i.displayName.lowercase().unaccent() == needle ||
            i.displayName.lowercase().unaccent().contains(needle)
        }
    }
}
