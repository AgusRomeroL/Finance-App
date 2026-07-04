package mx.budget.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.result.InterestByWallet
import mx.budget.data.local.result.MemberSpendByCategory
import mx.budget.data.local.result.SpendByCategory
import mx.budget.data.local.result.SpendByMember
import mx.budget.data.local.result.TopConcept
import mx.budget.data.local.result.WalletBalanceInfo

/**
 * Queries de agregación de solo lectura para el módulo de Analíticas (MVP
 * Fase 3) y el contexto RAG del asistente IA.
 *
 * REGLA ANTI-DOBLE-CONTEO: las vistas "por categoría" agregan sobre `expense`
 * (un gasto con N beneficiarios cuenta UNA vez); solo las vistas "por miembro"
 * agregan sobre `expense_attribution` (reparto en `share_amount_mxn`).
 */
@Dao
interface AnalyticsDao {

    // ── Módulo A: gasto por categoría (presupuesto vs ejecutado) ─────────────

    @Query(
        """
        SELECT c.id AS categoryId,
               c.display_name AS categoryName,
               c.code AS categoryCode,
               c.color_hex AS colorHex,
               COALESCE(c.budget_default_mxn, 0) AS projected,
               COALESCE(SUM(CASE WHEN e.status = 'POSTED' THEN e.amount_mxn ELSE 0 END), 0) AS actual,
               COALESCE(c.budget_default_mxn, 0) -
                   COALESCE(SUM(CASE WHEN e.status = 'POSTED' THEN e.amount_mxn ELSE 0 END), 0) AS remaining,
               CASE WHEN COALESCE(c.budget_default_mxn, 0) > 0
                    THEN CAST(ROUND(100.0 * COALESCE(SUM(CASE WHEN e.status = 'POSTED' THEN e.amount_mxn ELSE 0 END), 0)
                         / c.budget_default_mxn) AS INTEGER)
                    ELSE 0 END AS pctExec
        FROM category c
        LEFT JOIN expense e ON e.category_id = c.id AND e.quincena_id = :quincenaId
        WHERE c.household_id = :householdId
        GROUP BY c.id
        HAVING actual > 0 OR projected > 0
        ORDER BY actual DESC
        """
    )
    fun observeSpendByCategory(householdId: String, quincenaId: String): Flow<List<SpendByCategory>>

    @Query(
        """
        SELECT c.id AS categoryId,
               c.display_name AS categoryName,
               c.code AS categoryCode,
               c.color_hex AS colorHex,
               COALESCE(c.budget_default_mxn, 0) AS projected,
               COALESCE(SUM(CASE WHEN e.status = 'POSTED' THEN e.amount_mxn ELSE 0 END), 0) AS actual,
               COALESCE(c.budget_default_mxn, 0) -
                   COALESCE(SUM(CASE WHEN e.status = 'POSTED' THEN e.amount_mxn ELSE 0 END), 0) AS remaining,
               CASE WHEN COALESCE(c.budget_default_mxn, 0) > 0
                    THEN CAST(ROUND(100.0 * COALESCE(SUM(CASE WHEN e.status = 'POSTED' THEN e.amount_mxn ELSE 0 END), 0)
                         / c.budget_default_mxn) AS INTEGER)
                    ELSE 0 END AS pctExec
        FROM category c
        LEFT JOIN expense e ON e.category_id = c.id AND e.quincena_id = :quincenaId
        WHERE c.household_id = :householdId
        GROUP BY c.id
        HAVING actual > 0 OR projected > 0
        ORDER BY actual DESC
        """
    )
    suspend fun getSpendByCategory(householdId: String, quincenaId: String): List<SpendByCategory>

    // ── Módulo C: intereses pagados por wallet ───────────────────────────────

    @Query(
        """
        SELECT pm.id AS paymentMethodId,
               pm.display_name AS paymentMethodName,
               pm.kind AS kind,
               COALESCE(SUM(COALESCE(e.installment_interest_mxn, 0)), 0) AS interestPaidMxn,
               COALESCE(SUM(e.amount_mxn), 0) AS totalPaidMxn,
               CASE WHEN SUM(e.amount_mxn) > 0
                    THEN 100.0 * SUM(COALESCE(e.installment_interest_mxn, 0)) / SUM(e.amount_mxn)
                    ELSE 0 END AS interestPct
        FROM expense e
        JOIN payment_method pm ON pm.id = e.payment_method_id
        WHERE e.household_id = :householdId
          AND e.status = 'POSTED'
          AND e.occurred_at BETWEEN :fromEpoch AND :toEpoch
        GROUP BY pm.id
        HAVING interestPaidMxn > 0
        ORDER BY interestPaidMxn DESC
        """
    )
    suspend fun getInterestByWallet(
        householdId: String,
        fromEpoch: Long,
        toEpoch: Long
    ): List<InterestByWallet>

    // ── Módulo D: baseline histórico (últimas N quincenas cerradas) ──────────

    @Query(
        """
        SELECT c.id AS categoryId,
               c.display_name AS categoryName,
               c.code AS categoryCode,
               c.color_hex AS colorHex,
               COALESCE(c.budget_default_mxn, 0) AS projected,
               COALESCE(SUM(e.amount_mxn), 0) * 1.0 / :nQuincenas AS actual,
               COALESCE(c.budget_default_mxn, 0) -
                   COALESCE(SUM(e.amount_mxn), 0) * 1.0 / :nQuincenas AS remaining,
               0 AS pctExec
        FROM category c
        LEFT JOIN expense e ON e.category_id = c.id AND e.status = 'POSTED'
             AND e.quincena_id IN (
                 SELECT q.id FROM quincena q
                 WHERE q.household_id = :householdId AND q.status = 'CLOSED'
                   AND q.start_date >= :sinceDate
                 ORDER BY q.start_date DESC LIMIT :nQuincenas
             )
        WHERE c.household_id = :householdId
        GROUP BY c.id
        HAVING actual > 0
        ORDER BY actual DESC
        """
    )
    suspend fun getAvgSpendByCategoryHistorical(
        householdId: String,
        sinceDate: String,
        nQuincenas: Int
    ): List<SpendByCategory>

    @Query(
        """
        SELECT COALESCE(AVG(actual_expenses_mxn), 0) FROM (
            SELECT actual_expenses_mxn FROM quincena
            WHERE household_id = :householdId AND status = 'CLOSED'
            ORDER BY start_date DESC LIMIT :n
        )
        """
    )
    suspend fun getAvgExpenseOverLastN(householdId: String, n: Int): Double

    // ── Módulo E: concentración de deuda (crédito revolvente) ────────────────

    @Query(
        """
        SELECT pm.id AS paymentMethodId,
               pm.display_name AS displayName,
               pm.kind AS kind,
               pm.current_balance_mxn AS balance,
               pm.credit_limit_mxn AS creditLimit,
               CASE WHEN pm.credit_limit_mxn > 0
                    THEN 100.0 * pm.current_balance_mxn / pm.credit_limit_mxn
                    ELSE NULL END AS utilizationPct
        FROM payment_method pm
        WHERE pm.household_id = :householdId AND pm.is_active = 1
          AND pm.kind IN ('CREDIT_CARD', 'DEPARTMENT_STORE_CARD', 'BNPL_INSTALLMENT')
        ORDER BY balance DESC
        """
    )
    suspend fun getDebtConcentration(householdId: String): List<WalletBalanceInfo>

    // ── Alertas: categorías sobre el umbral del presupuesto ──────────────────

    @Query(
        """
        SELECT c.id AS categoryId,
               c.display_name AS categoryName,
               c.code AS categoryCode,
               c.color_hex AS colorHex,
               COALESCE(c.budget_default_mxn, 0) AS projected,
               SUM(e.amount_mxn) AS actual,
               COALESCE(c.budget_default_mxn, 0) - SUM(e.amount_mxn) AS remaining,
               CAST(ROUND(100.0 * SUM(e.amount_mxn) / c.budget_default_mxn) AS INTEGER) AS pctExec
        FROM category c
        JOIN expense e ON e.category_id = c.id
             AND e.quincena_id = :quincenaId AND e.status = 'POSTED'
        GROUP BY c.id
        HAVING COALESCE(c.budget_default_mxn, 0) > 0
           AND 100.0 * SUM(e.amount_mxn) / c.budget_default_mxn >= :thresholdPct
        ORDER BY pctExec DESC
        """
    )
    suspend fun getCategoriesOverBudget(quincenaId: String, thresholdPct: Int): List<SpendByCategory>

    // ── Top conceptos de la quincena (pantalla Analíticas) ───────────────────

    @Query(
        """
        SELECT COALESCE(e.concept_canonical, e.concept) AS concept,
               SUM(e.amount_mxn) AS totalMxn,
               COUNT(*) AS timesCount
        FROM expense e
        WHERE e.quincena_id = :quincenaId AND e.status = 'POSTED'
        GROUP BY COALESCE(e.concept_canonical, e.concept)
        ORDER BY totalMxn DESC
        LIMIT :limit
        """
    )
    suspend fun getTopConcepts(quincenaId: String, limit: Int): List<TopConcept>

    // ── Vistas por miembro (agregan sobre expense_attribution) ───────────────

    /** Gasto (BENEFICIARY) de un miembro específico en una quincena. */
    @Query(
        """
        SELECT m.id AS memberId,
               m.display_name AS memberName,
               COALESCE(SUM(CASE WHEN e.id IS NOT NULL THEN a.share_amount_mxn ELSE 0 END), 0) AS totalMxn,
               COUNT(e.id) AS expenseCount
        FROM member m
        LEFT JOIN expense_attribution a ON a.member_id = m.id AND a.role = 'BENEFICIARY'
        LEFT JOIN expense e ON e.id = a.expense_id
             AND e.quincena_id = :quincenaId AND e.status = 'POSTED'
        WHERE m.id = :memberId
        GROUP BY m.id
        """
    )
    suspend fun getSpendForMember(quincenaId: String, memberId: String): SpendByMember?

    /** Desglose miembro × categoría (BENEFICIARY) en un rango de fechas. */
    @Query(
        """
        SELECT m.id AS memberId,
               m.display_name AS memberName,
               c.id AS categoryId,
               c.display_name AS categoryName,
               SUM(a.share_amount_mxn) AS totalMxn,
               COUNT(DISTINCT e.id) AS expenseCount
        FROM expense_attribution a
        JOIN expense e ON e.id = a.expense_id AND e.status = 'POSTED'
             AND e.occurred_at BETWEEN :fromEpoch AND :toEpoch
        JOIN member m ON m.id = a.member_id
        JOIN category c ON c.id = e.category_id
        WHERE a.role = 'BENEFICIARY'
        GROUP BY m.id, c.id
        ORDER BY m.display_name, totalMxn DESC
        """
    )
    suspend fun getSpendByMemberAndCategory(fromEpoch: Long, toEpoch: Long): List<MemberSpendByCategory>
}
