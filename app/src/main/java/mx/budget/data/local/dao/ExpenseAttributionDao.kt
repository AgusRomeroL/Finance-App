package mx.budget.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.entity.ExpenseAttributionEntity
import mx.budget.data.local.result.SpendByMember

/**
 * DAO de la tabla puente de atribuciones de gasto.
 */
@Dao
interface ExpenseAttributionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<ExpenseAttributionEntity>)

    @Query("DELETE FROM expense_attribution WHERE expense_id = :expenseId")
    suspend fun deleteByExpenseId(expenseId: String)

    @Query("SELECT * FROM expense_attribution WHERE expense_id = :expenseId")
    suspend fun getByExpenseId(expenseId: String): List<ExpenseAttributionEntity>

    /**
     * Gasto agregado por miembro para un [role] dado ("BENEFICIARY" o "PAYER")
     * en la quincena, considerando solo gastos POSTED. Ordenado de mayor a menor.
     *
     * Usa el `share_amount_mxn` pre-calculado de cada atribución, así que el
     * total refleja la participación fraccionada real (no el monto completo
     * del gasto). Alimenta las barras horizontales del dashboard y su toggle.
     */
    @Query(
        """
        SELECT
            a.member_id                          AS memberId,
            m.display_name                       AS memberName,
            COALESCE(SUM(a.share_amount_mxn), 0.0) AS totalMxn,
            COUNT(DISTINCT a.expense_id)         AS expenseCount
        FROM expense_attribution a
        INNER JOIN expense e ON e.id = a.expense_id
        INNER JOIN member  m ON m.id = a.member_id
        WHERE e.quincena_id = :quincenaId
          AND a.role = :role
          AND e.status = 'POSTED'
        GROUP BY a.member_id, m.display_name
        ORDER BY totalMxn DESC
        """
    )
    fun observeSpendByMember(quincenaId: String, role: String): Flow<List<SpendByMember>>
}
