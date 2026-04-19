package mx.budget.ai.rag

import mx.budget.data.local.entity.QuincenaEntity
import mx.budget.data.local.result.InstallmentSummary
import mx.budget.data.local.result.QuincenaSnapshot
import mx.budget.data.local.result.SpendByCategory
import mx.budget.data.local.result.SpendByMember
import mx.budget.data.local.result.TopExpense
import mx.budget.data.local.result.WalletBalanceInfo

/**
 * Contenedor de datos tabulares extraídos de Room que forman
 * la ventana de contexto para el LLM.
 */
data class RagContext(
    val currentQuincena: QuincenaEntity,
    val spendByCategory: List<SpendByCategory> = emptyList(),
    val topExpenses: List<TopExpense> = emptyList(),
    val walletsSnapshot: List<WalletBalanceInfo> = emptyList(),
    val memberSpend: List<SpendByMember> = emptyList(),
    val activeInstallments: List<InstallmentSummary> = emptyList(),
    val history6q: List<QuincenaSnapshot> = emptyList()
)
