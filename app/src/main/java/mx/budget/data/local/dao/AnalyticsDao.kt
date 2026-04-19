package mx.budget.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.result.InterestByWallet
import mx.budget.data.local.result.SpendByCategory

/**
 * DAO para consultas analíticas complejas con múltiples JOINs.
 *
 * Alimenta los módulos analíticos A–F del dashboard, las alertas
 * proactivas y el contexto RAG del asistente IA.
 *
 * Todas las queries son de solo lectura y usan gastos POSTED.
 */
@Dao
interface AnalyticsDao {

    // ── Módulo A: Flujo de capital (Sankey) ─────────────────────

    /**
     * Gasto por categoría en una quincena: proyectado vs ejecutado.
     * Alimenta el dashboard quincenal y el Sankey de flujo de capital.
     */
    @Query("""
        SELECT 
            c.id AS categoryId,
            c.display_name AS categoryName,
            c.code AS categoryCode,
            c.color_hex AS colorHex,
            COALESCE(planned.total, 0.0) AS projected,
            COALESCE(posted.total, 0.0) AS actual,
            COALESCE(planned.total, 0.0) - COALESCE(posted.total, 0.0) AS remaining,
            CASE 
                WHEN COALESCE(planned.total, 0.0) > 0 
                THEN CAST(ROUND(100.0 * COALESCE(posted.total, 0.0) / COALESCE(planned.total, 0.0)) AS INTEGER)
                ELSE 0 
            END AS pctExec
        FROM category c
        LEFT JOIN (
            SELECT category_id, SUM(amount_mxn) AS total
            FROM expense
            WHERE quincena_id = :quincenaId AND status = 'PLANNED'
            GROUP BY category_id
        ) planned ON planned.category_id = c.id
        LEFT JOIN (
            SELECT category_id, SUM(amount_mxn) AS total
            FROM expense
            WHERE quincena_id = :quincenaId AND status = 'POSTED'
            GROUP BY category_id
        ) posted ON posted.category_id = c.id
        WHERE c.household_id = :householdId
          AND c.kind LIKE 'EXPENSE_%'
          AND (planned.total IS NOT NULL OR posted.total IS NOT NULL)
        ORDER BY actual DESC
    """)
    fun observeSpendByCategory(
        householdId: String,
        quincenaId: String
    ): Flow<List<SpendByCategory>>

    /**
     * Versión suspend para el contexto RAG del asistente IA.
     */
    @Query("""
        SELECT 
            c.id AS categoryId,
            c.display_name AS categoryName,
            c.code AS categoryCode,
            c.color_hex AS colorHex,
            COALESCE(planned.total, 0.0) AS projected,
            COALESCE(posted.total, 0.0) AS actual,
            COALESCE(planned.total, 0.0) - COALESCE(posted.total, 0.0) AS remaining,
            CASE 
                WHEN COALESCE(planned.total, 0.0) > 0 
                THEN CAST(ROUND(100.0 * COALESCE(posted.total, 0.0) / COALESCE(planned.total, 0.0)) AS INTEGER)
                ELSE 0 
            END AS pctExec
        FROM category c
        LEFT JOIN (
            SELECT category_id, SUM(amount_mxn) AS total
            FROM expense
            WHERE quincena_id = :quincenaId AND status = 'PLANNED'
            GROUP BY category_id
        ) planned ON planned.category_id = c.id
        LEFT JOIN (
            SELECT category_id, SUM(amount_mxn) AS total
            FROM expense
            WHERE quincena_id = :quincenaId AND status = 'POSTED'
            GROUP BY category_id
        ) posted ON posted.category_id = c.id
        WHERE c.household_id = :householdId
          AND c.kind LIKE 'EXPENSE_%'
          AND (planned.total IS NOT NULL OR posted.total IS NOT NULL)
        ORDER BY actual DESC
    """)
    suspend fun getSpendByCategory(
        householdId: String,
        quincenaId: String
    ): List<SpendByCategory>

    // ── Módulo C: Intereses pagados ─────────────────────────────

    /**
     * Intereses pagados agrupados por método de pago.
     * Usa los campos installment_interest_mxn de los gastos de cuotas.
     * Alimenta el módulo analítico C (detección de intereses pagados).
     */
    @Query("""
        SELECT 
            pm.id AS paymentMethodId,
            pm.display_name AS paymentMethodName,
            pm.kind,
            COALESCE(SUM(e.installment_interest_mxn), 0.0) AS interestPaidMxn,
            COALESCE(SUM(e.amount_mxn), 0.0) AS totalPaidMxn,
            CASE 
                WHEN SUM(e.amount_mxn) > 0 
                THEN ROUND(100.0 * SUM(e.installment_interest_mxn) / SUM(e.amount_mxn), 1)
                ELSE 0.0
            END AS interestPct
        FROM expense e
        JOIN payment_method pm ON pm.id = e.payment_method_id
        WHERE e.household_id = :householdId
          AND e.status = 'POSTED'
          AND e.installment_interest_mxn IS NOT NULL
          AND e.installment_interest_mxn > 0
          AND e.occurred_at BETWEEN :fromEpoch AND :toEpoch
        GROUP BY pm.id
        ORDER BY interestPaidMxn DESC
    """)
    suspend fun getInterestByWallet(
        householdId: String,
        fromEpoch: Long,
        toEpoch: Long
    ): List<InterestByWallet>

    // ── Módulo D: Varianza histórica ────────────────────────────

    /**
     * Gasto promedio por categoría en las últimas N quincenas cerradas.
     * Baseline para el análisis de varianza (z-scores con MAD).
     */
    @Query("""
        SELECT 
            c.id AS categoryId,
            c.display_name AS categoryName,
            c.code AS categoryCode,
            c.color_hex AS colorHex,
            0.0 AS projected,
            COALESCE(SUM(e.amount_mxn), 0.0) / MAX(:nQuincenas, 1) AS actual,
            0.0 AS remaining,
            0 AS pctExec
        FROM expense e
        JOIN category c ON c.id = e.category_id
        JOIN quincena q ON q.id = e.quincena_id
        WHERE e.household_id = :householdId
          AND q.status = 'CLOSED'
          AND e.status = 'POSTED'
          AND q.start_date >= :sinceDate
        GROUP BY c.id
        ORDER BY actual DESC
    """)
    suspend fun getAvgSpendByCategoryHistorical(
        householdId: String,
        sinceDate: String,
        nQuincenas: Int
    ): List<SpendByCategory>

    // ── Módulo E: Concentración de deuda ────────────────────────

    /**
     * Deuda total por wallet con porcentaje de concentración.
     * Alimenta el donut de concentración de deuda.
     */
    @Query("""
        SELECT 
            pm.id AS paymentMethodId,
            pm.display_name AS paymentMethodName,
            pm.kind,
            pm.current_balance_mxn AS balance,
            pm.credit_limit_mxn AS creditLimit,
            CASE 
                WHEN pm.credit_limit_mxn IS NOT NULL AND pm.credit_limit_mxn > 0 
                THEN ROUND(100.0 * pm.current_balance_mxn / pm.credit_limit_mxn, 1)
                ELSE NULL
            END AS utilizationPct
        FROM payment_method pm
        WHERE pm.household_id = :householdId
          AND pm.is_active = 1
          AND pm.kind IN ('CREDIT_CARD', 'DEPARTMENT_STORE_CARD', 'BNPL_INSTALLMENT')
          AND pm.current_balance_mxn > 0
        ORDER BY pm.current_balance_mxn DESC
    """)
    suspend fun getDebtConcentration(householdId: String): List<mx.budget.data.local.result.WalletBalanceInfo>

    // ── Dashboard KPIs ──────────────────────────────────────────

    /**
     * Gasto promedio quincenal sobre las últimas N quincenas cerradas.
     * KPI: "Gasto promedio quincenal".
     */
    @Query("""
        SELECT COALESCE(AVG(actual_expenses_mxn), 0.0) 
        FROM (
            SELECT actual_expenses_mxn 
            FROM quincena 
            WHERE household_id = :householdId AND status = 'CLOSED'
            ORDER BY start_date DESC 
            LIMIT :n
        )
    """)
    suspend fun getAvgExpenseOverLastN(householdId: String, n: Int = 6): Double

    /**
     * Alerta: categorías donde el gasto ejecutado supera el umbral del presupuesto.
     * Default: 80% (regla de alerta proactiva).
     */
    @Query("""
        SELECT 
            c.id AS categoryId,
            c.display_name AS categoryName,
            c.code AS categoryCode,
            c.color_hex AS colorHex,
            COALESCE(SUM(CASE WHEN e.status = 'PLANNED' THEN e.amount_mxn ELSE 0 END), 0.0) AS projected,
            COALESCE(SUM(CASE WHEN e.status = 'POSTED' THEN e.amount_mxn ELSE 0 END), 0.0) AS actual,
            0.0 AS remaining,
            CASE 
                WHEN SUM(CASE WHEN e.status = 'PLANNED' THEN e.amount_mxn ELSE 0 END) > 0 
                THEN CAST(ROUND(100.0 
                    * SUM(CASE WHEN e.status = 'POSTED' THEN e.amount_mxn ELSE 0 END) 
                    / (SUM(CASE WHEN e.status = 'PLANNED' THEN e.amount_mxn ELSE 0 END) 
                       + SUM(CASE WHEN e.status = 'POSTED' THEN e.amount_mxn ELSE 0 END))
                ) AS INTEGER)
                ELSE 0
            END AS pctExec
        FROM expense e
        JOIN category c ON c.id = e.category_id
        WHERE e.quincena_id = :quincenaId
        GROUP BY c.id
        HAVING pctExec >= :thresholdPct
        ORDER BY pctExec DESC
    """)
    suspend fun getCategoriesOverBudget(
        quincenaId: String,
        thresholdPct: Int = 80
    ): List<SpendByCategory>
}
