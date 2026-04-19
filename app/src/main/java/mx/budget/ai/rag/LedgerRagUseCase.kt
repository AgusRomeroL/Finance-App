package mx.budget.ai.rag

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mx.budget.ai.service.AiCoreManager
import mx.budget.data.repository.AnalyticsRepository
import mx.budget.data.repository.ExpenseRepository
import mx.budget.data.repository.InstallmentRepository
import mx.budget.data.repository.QuincenaRepository
import mx.budget.data.repository.WalletRepository
import java.io.InputStreamReader

/**
 * Orquestador principal del pipeline RAG Local. Conecta los repositorios de Room
 * con la IA manteniéndose 100% como lector de datos.
 */
class LedgerRagUseCase(
    private val context: Context,
    private val aiCoreManager: AiCoreManager,
    private val quincenaRepository: QuincenaRepository,
    private val expenseRepository: ExpenseRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val walletRepository: WalletRepository,
    private val installmentRepository: InstallmentRepository
) {

    private val systemPrompt: String by lazy { loadAsset("ai/system_prompt.es.txt") }
    private val intentSchema: String by lazy { loadAsset("ai/intent_schema.json") }
    private val assembler: PromptAssembler by lazy { PromptAssembler(systemPrompt, intentSchema) }

    /**
     * Devuelve el JSON crudo emitido por la IA para la pregunta, habiendo construido
     * previamente el contexto de la base de datos local.
     */
    suspend fun invoke(question: String, householdId: String): Result<String> = withContext(Dispatchers.IO) {
        val activeQuincena = quincenaRepository.getActive(householdId) 
            ?: return@withContext Result.failure(IllegalStateException("No active quincena found"))

        val sanitizedQ = PromptSanitizer.sanitize(question)
        val schemaDims = QuestionClassifier.classify(sanitizedQ)

        val ragCtx = RagContext(
            currentQuincena = activeQuincena,
            spendByCategory = if (ContextDimension.SpendByCategory in schemaDims)
                analyticsRepository.getSpendByCategory(householdId, activeQuincena.id) else emptyList(),
            topExpenses = if (ContextDimension.TopExpenses in schemaDims)
                expenseRepository.getTopExpenses(activeQuincena.id, 15) else emptyList(),
            walletsSnapshot = if (ContextDimension.Wallets in schemaDims)
                analyticsRepository.getDebtConcentration(householdId) else emptyList(),
            memberSpend = emptyList(), // Asumiendo que AnalyticsRepo o similar expone esto en la fase posterior
            activeInstallments = emptyList(), // Asumiendo que InstallmentRepo expone el estado
            history6q = if (ContextDimension.HistoricalCompare in schemaDims)
                quincenaRepository.getClosedSnapshots(householdId, 6) else emptyList()
        )

        val serializedContext = ContextSerializer.serialize(ragCtx)
        val fullPrompt = assembler.assemble(serializedContext, sanitizedQ)

        aiCoreManager.generate(fullPrompt)
    }

    private fun loadAsset(path: String): String {
        return try {
            InputStreamReader(context.assets.open(path)).readText()
        } catch (e: Exception) {
            ""
        }
    }
}
