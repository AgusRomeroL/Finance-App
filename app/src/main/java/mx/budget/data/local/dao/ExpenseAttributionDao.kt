package mx.budget.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.entity.ExpenseAttributionEntity
import mx.budget.data.local.result.MemberSpendByCategory
import mx.budget.data.local.result.SpendByMember

/**
 * DAO para la tabla de intersección de atribución fraccionada.
 *
 * Implementa las queries analíticas del sistema de atribución dual
 * (beneficiario + pagador) usando basis points como unidad de reparto.
 *
 * Regla de integridad: para cada (expense_id, role),
 * SUM(share_bps) DEBE ser exactamente 10,000.
 */
@Dao
interface ExpenseAttributionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(attribution: ExpenseAttributionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(attributions: List<ExpenseAttributionEntity>)

    /**
     * Todas las atribuciones de un gasto.
     */
    @Query("SELECT * FROM expense_attribution WHERE expense_id = :expenseId")
    suspend fun getByExpenseId(expenseId: String): List<ExpenseAttributionEntity>

    /**
     * Atribuciones de un gasto filtradas por rol (BENEFICIARY o PAYER).
     */
    @Query("""
        SELECT * FROM expense_attribution 
        WHERE expense_id = :expenseId AND role = :role
    """)
    suspend fun getByExpenseIdAndRole(expenseId: String, role: String): List<ExpenseAttributionEntity>

    /**
     * Elimina todas las atribuciones de un gasto (previo a re-asignar).
     */
    @Query("DELETE FROM expense_attribution WHERE expense_id = :expenseId")
    suspend fun deleteByExpenseId(expenseId: String)

    // ── Queries analíticas por miembro ──────────────────────────

    /**
     * Gasto total por miembro (rol BENEFICIARY) en un rango de fechas.
     *
     * Resuelve: "¿Cuánto consume cada miembro del hogar?"
     * Alimenta: módulo B (gasto proporcional), intent GET_TOP_SPENDER.
     *
     * Usa share_amount_mxn (pre-calculado) para evitar divisiones en la query.
     */
    @Query("""
        SELECT 
            m.id AS memberId,
            m.display_name AS memberName,
            COALESCE(SUM(ea.share_amount_mxn), 0.0) AS totalMxn,
            COUNT(DISTINCT e.id) AS expenseCount
        FROM expense_attribution ea
        JOIN expense e ON e.id = ea.expense_id
        JOIN member m ON m.id = ea.member_id
        WHERE ea.role = 'BENEFICIARY'
          AND e.status = 'POSTED'
          AND e.occurred_at BETWEEN :fromEpoch AND :toEpoch
        GROUP BY m.id
        ORDER BY totalMxn DESC
    """)
    suspend fun getSpendByMember(fromEpoch: Long, toEpoch: Long): List<SpendByMember>

    /**
     * Versión reactiva: emite cada vez que cambia un gasto en el rango.
     */
    @Query("""
        SELECT 
            m.id AS memberId,
            m.display_name AS memberName,
            COALESCE(SUM(ea.share_amount_mxn), 0.0) AS totalMxn,
            COUNT(DISTINCT e.id) AS expenseCount
        FROM expense_attribution ea
        JOIN expense e ON e.id = ea.expense_id
        JOIN member m ON m.id = ea.member_id
        WHERE ea.role = 'BENEFICIARY'
          AND e.status = 'POSTED'
          AND e.quincena_id = :quincenaId
        GROUP BY m.id
        ORDER BY totalMxn DESC
    """)
    fun observeSpendByMemberInQuincena(quincenaId: String): Flow<List<SpendByMember>>

    /**
     * Desglose por miembro y categoría.
     *
     * Resuelve: "¿En qué gasta David?" (treemap del módulo B).
     */
    @Query("""
        SELECT 
            m.id AS memberId,
            m.display_name AS memberName,
            c.id AS categoryId,
            c.display_name AS categoryName,
            COALESCE(SUM(ea.share_amount_mxn), 0.0) AS totalMxn,
            COUNT(DISTINCT e.id) AS expenseCount
        FROM expense_attribution ea
        JOIN expense e ON e.id = ea.expense_id
        JOIN member m ON m.id = ea.member_id
        JOIN category c ON c.id = e.category_id
        WHERE ea.role = 'BENEFICIARY'
          AND e.status = 'POSTED'
          AND e.occurred_at BETWEEN :fromEpoch AND :toEpoch
        GROUP BY m.id, c.id
        ORDER BY m.display_name, totalMxn DESC
    """)
    suspend fun getSpendByMemberAndCategory(
        fromEpoch: Long,
        toEpoch: Long
    ): List<MemberSpendByCategory>

    /**
     * Gasto de un miembro específico en una quincena.
     * Alimenta el intent GET_SPEND_BY_MEMBER del asistente IA.
     */
    @Query("""
        SELECT 
            m.id AS memberId,
            m.display_name AS memberName,
            COALESCE(SUM(ea.share_amount_mxn), 0.0) AS totalMxn,
            COUNT(DISTINCT e.id) AS expenseCount
        FROM expense_attribution ea
        JOIN expense e ON e.id = ea.expense_id
        JOIN member m ON m.id = ea.member_id
        WHERE ea.role = 'BENEFICIARY'
          AND e.status = 'POSTED'
          AND e.quincena_id = :quincenaId
          AND m.id = :memberId
        GROUP BY m.id
    """)
    suspend fun getSpendForMember(quincenaId: String, memberId: String): SpendByMember?

    /**
     * Validación de integridad: verifica que los basis points
     * sumen 10,000 para cada (expense_id, role).
     * Retorna los expense_ids con atribuciones inválidas.
     */
    @Query("""
        SELECT expense_id 
        FROM expense_attribution 
        GROUP BY expense_id, role 
        HAVING SUM(share_bps) != 10000
    """)
    suspend fun findInvalidAttributions(): List<String>
}
