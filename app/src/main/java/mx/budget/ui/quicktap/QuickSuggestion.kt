package mx.budget.ui.quicktap

import mx.budget.ai.proactive.ProactiveSuggestionEngine
import mx.budget.data.local.dao.ExpenseDao
import mx.budget.data.local.entity.ExpenseEntity
import mx.budget.ui.capture.CaptureViewModel

/**
 * Un gasto que el hogar repite, completo: concepto, categoría, cuenta e importe.
 *
 * El panel de Quick Tap la ofrece entera de un toque, que es lo que hace que el
 * caso común no cueste teclear nada. No es lo mismo que la sugerencia proactiva
 * del panel de inicio, que solo propone concepto y categoría: aquí hace falta el
 * importe y la cuenta o no se puede guardar sin abrir nada.
 */
data class QuickSuggestion(
    val concepto: String,
    val montoMxn: Double,
    val categoryId: String,
    val walletId: String,
) {
    fun apply(viewModel: CaptureViewModel) {
        viewModel.setAmount(montoMxn)
        viewModel.onConceptChange(concepto)
        viewModel.onCategorySelected(categoryId)
        viewModel.onWalletSelected(walletId)
        viewModel.onSelectAllMembers()
    }
}

/**
 * Deriva las tres sugerencias del panel del historial del hogar.
 *
 * Se apoya en el mismo motor determinista que el panel de inicio para elegir QUÉ
 * conceptos proponer (franja horaria, día de la semana, frecuencia) y después
 * completa cada uno con el último gasto real de ese concepto, que es de donde
 * salen el importe y la cuenta. Sin LLM: esto corre en el camino de un gesto que
 * tiene que sentirse instantáneo.
 */
object QuickSuggestions {

    private val engine = ProactiveSuggestionEngine()

    suspend fun forNow(
        expenseDao: ExpenseDao,
        householdId: String,
        nowEpochMs: Long = System.currentTimeMillis(),
        limit: Int = 3,
    ): List<QuickSuggestion> {
        val historial = runCatching { expenseDao.getAll(householdId) }.getOrDefault(emptyList())
        if (historial.isEmpty()) return emptyList()

        val candidatos = engine.suggestMany(historial, activeQuincena = null, nowEpochMs, limit = limit * 2)
        val ejecutados = historial.filter { it.status == "POSTED" }

        val desdeElMotor = candidatos.mapNotNull { candidato ->
            ejecutados.ultimoDe(candidato.concept)?.let { gasto ->
                QuickSuggestion(
                    concepto = gasto.concept,
                    montoMxn = gasto.amountMxn,
                    categoryId = gasto.categoryId,
                    walletId = gasto.paymentMethodId,
                )
            }
        }

        // Relleno con lo más reciente: un hogar nuevo no tiene patrón todavía,
        // y un panel vacío no ayuda a nadie.
        val relleno = ejecutados
            .sortedByDescending { it.occurredAt }
            .distinctBy { it.concept.lowercase() }
            .map {
                QuickSuggestion(
                    concepto = it.concept,
                    montoMxn = it.amountMxn,
                    categoryId = it.categoryId,
                    walletId = it.paymentMethodId,
                )
            }

        return (desdeElMotor + relleno)
            .distinctBy { it.concepto.lowercase() }
            .take(limit)
    }

    private fun List<ExpenseEntity>.ultimoDe(concepto: String): ExpenseEntity? =
        filter { it.concept.equals(concepto, ignoreCase = true) }
            .maxByOrNull { it.occurredAt }
}
