package mx.budget.data.repository

import mx.budget.data.local.result.InterestByWallet
import mx.budget.data.local.result.SpendByCategory
import mx.budget.data.local.result.WalletBalanceInfo
import kotlinx.coroutines.flow.Flow

/**
 * Contrato del repositorio de analíticas.
 *
 * Consolida las consultas de solo lectura que alimentan los módulos
 * analíticos A-F, las alertas proactivas y el contexto RAG del asistente IA.
 */
interface AnalyticsRepository {

    // ── Módulo A: Flujo de capital ──────────────────────────────

    /**
     * Gasto por categoría: presupuestado vs ejecutado (Sankey + barras).
     */
    fun observeSpendByCategory(
        householdId: String,
        quincenaId: String
    ): Flow<List<SpendByCategory>>

    /** Versión suspend para el contexto RAG del asistente IA. */
    suspend fun getSpendByCategory(
        householdId: String,
        quincenaId: String
    ): List<SpendByCategory>

    // ── Módulo C: Intereses pagados ─────────────────────────────

    /** Intereses pagados por wallet en un rango de fechas. */
    suspend fun getInterestByWallet(
        householdId: String,
        fromEpoch: Long,
        toEpoch: Long
    ): List<InterestByWallet>

    // ── Módulo D: Varianza ──────────────────────────────────────

    /**
     * Gasto promedio por categoría en las últimas N quincenas cerradas.
     * Baseline para z-scores con Mediana + MAD.
     */
    suspend fun getAvgSpendByCategoryHistorical(
        householdId: String,
        sinceDate: String,
        nQuincenas: Int
    ): List<SpendByCategory>

    /** Gasto promedio quincenal (media de los últimos N períodos cerrados). */
    suspend fun getAvgExpenseOverLastN(householdId: String, n: Int = 6): Double

    // ── Módulo E: Concentración de deuda ────────────────────────

    /** Deuda por wallet con utilización de crédito (donut). */
    suspend fun getDebtConcentration(householdId: String): List<WalletBalanceInfo>

    // ── Alertas proactivas ──────────────────────────────────────

    /**
     * Categorías donde el gasto ejecutado supera el umbral del presupuesto.
     * Alimenta la alerta "sobregasto categoría" (push notification).
     */
    suspend fun getCategoriesOverBudget(
        quincenaId: String,
        thresholdPct: Int = 80
    ): List<SpendByCategory>
}
