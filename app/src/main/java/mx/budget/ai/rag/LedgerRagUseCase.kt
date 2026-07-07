package mx.budget.ai.rag

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import mx.budget.ai.service.OnDeviceLlm
import mx.budget.data.repository.AnalyticsRepository
import mx.budget.data.repository.ExpenseRepository
import mx.budget.data.repository.InstallmentRepository
import mx.budget.data.repository.MemberRepository
import mx.budget.data.repository.QuincenaRepository
import mx.budget.data.repository.WalletRepository
import java.io.InputStreamReader

/**
 * Orquestador principal del pipeline RAG Local. Conecta los repositorios de Room
 * con la IA manteniéndose 100% como lector de datos.
 *
 * MVP Fase 4: el motor es la interfaz [OnDeviceLlm] (el HybridLlm de la app:
 * AICore → LiteRT-LM), no el AiCoreManager concreto.
 */
class LedgerRagUseCase(
    private val context: Context,
    private val llm: OnDeviceLlm,
    private val quincenaRepository: QuincenaRepository,
    private val expenseRepository: ExpenseRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val walletRepository: WalletRepository,
    private val installmentRepository: InstallmentRepository,
    private val memberRepository: MemberRepository? = null,
) {

    private val systemPrompt: String by lazy { loadAsset("ai/system_prompt.es.txt") }
    private val intentSchema: String by lazy { loadAsset("ai/intent_schema.json") }
    private val assembler: PromptAssembler by lazy { PromptAssembler(systemPrompt, intentSchema) }

    /**
     * Devuelve el JSON crudo emitido por la IA para la pregunta, habiendo construido
     * previamente el contexto de la base de datos local.
     *
     * @param previousQuestions memoria conversacional corta (últimas preguntas del
     *  usuario): permite follow-ups ("¿y el mes pasado?") sin repetir la entidad.
     */
    suspend fun invoke(
        question: String,
        householdId: String,
        previousQuestions: List<String> = emptyList(),
    ): Result<String> = withContext(Dispatchers.IO) {
        val activeQuincena = quincenaRepository.getActive(householdId)
            ?: return@withContext Result.failure(IllegalStateException("No active quincena found"))

        val sanitizedQ = PromptSanitizer.sanitize(question)
        // Los nombres del hogar se inyectan FRESCOS al clasificador (antes eran
        // regex hardcodeados con nombres propios que no generalizaban).
        val memberAliases = memberRepository
            ?.let { repo -> runCatching { repo.observeActiveMembers(householdId).first() }.getOrNull() }
            ?.flatMap { m ->
                listOf(m.displayName) + runCatching {
                    Json.decodeFromString<List<String>>(m.shortAliases)
                }.getOrDefault(emptyList())
            }
            ?: emptyList()
        val walletNames = runCatching { walletRepository.getActive(householdId).map { it.displayName } }
            .getOrDefault(emptyList())
        val planNames = runCatching { installmentRepository.getActive(householdId).map { it.displayName } }
            .getOrDefault(emptyList())
        val schemaDims = QuestionClassifier.classify(sanitizedQ, memberAliases, walletNames, planNames)

        val ragCtx = RagContext(
            currentQuincena = activeQuincena,
            spendByCategory = if (ContextDimension.SpendByCategory in schemaDims)
                analyticsRepository.getSpendByCategory(householdId, activeQuincena.id) else emptyList(),
            topExpenses = if (ContextDimension.TopExpenses in schemaDims)
                expenseRepository.getTopExpenses(activeQuincena.id, 15) else emptyList(),
            walletsSnapshot = if (ContextDimension.Wallets in schemaDims)
                analyticsRepository.getDebtConcentration(householdId) else emptyList(),
            memberSpend = if (ContextDimension.ByMember in schemaDims)
                expenseRepository.observeSpendByMember(activeQuincena.id).first() else emptyList(),
            activeInstallments = if (ContextDimension.Installments in schemaDims)
                installmentRepository.observeActiveSummaries(householdId).first() else emptyList(),
            history6q = if (ContextDimension.HistoricalCompare in schemaDims)
                quincenaRepository.getClosedSnapshots(householdId, 6) else emptyList()
        )

        val serializedContext = ContextSerializer.serialize(ragCtx)
        // Memoria conversacional corta: las preguntas previas van como contexto
        // adicional (no como parte de la pregunta) para que un follow-up herede
        // la entidad mencionada antes sin confundir el intent JSON.
        val contextWithHistory = if (previousQuestions.isEmpty()) serializedContext else buildString {
            append(serializedContext)
            append("\nPREGUNTAS_PREVIAS_DEL_USUARIO:")
            previousQuestions.takeLast(3).forEach { append("\n- ").append(PromptSanitizer.sanitize(it).take(160)) }
        }
        val fullPrompt = assembler.assemble(contextWithHistory, sanitizedQ)

        llm.generate(fullPrompt)
    }

    private fun loadAsset(path: String): String {
        return try {
            InputStreamReader(context.assets.open(path)).readText()
        } catch (e: Exception) {
            ""
        }
    }
}
