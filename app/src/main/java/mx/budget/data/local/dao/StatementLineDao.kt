package mx.budget.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import mx.budget.data.local.entity.StatementLineEntity

/**
 * DAO de `statement_line` (Fase 5 — conciliación de estados de cuenta).
 *
 * La inserción usa IGNORE: el índice UNIQUE `(wallet_id, line_fingerprint)`
 * convierte un re-import del mismo PDF en un no-op fila a fila (idempotencia),
 * conservando la decisión previa del usuario (MATCHED/NEW/IGNORED).
 */
@Dao
interface StatementLineDao {

    /** @return rowId, o -1 si la línea ya existía (fingerprint duplicado). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(line: StatementLineEntity): Long

    @Query("SELECT * FROM statement_line WHERE import_id = :importId ORDER BY post_date")
    suspend fun getByImport(importId: String): List<StatementLineEntity>

    @Query("SELECT * FROM statement_line WHERE wallet_id = :walletId AND line_fingerprint IN (:fingerprints)")
    suspend fun getByFingerprints(walletId: String, fingerprints: List<String>): List<StatementLineEntity>

    @Query(
        """
        UPDATE statement_line
        SET match_status = :status, matched_expense_id = :expenseId,
            match_confidence = :confidence, match_source = :source, updated_at = :now
        WHERE id = :id
        """
    )
    suspend fun updateMatch(
        id: String,
        status: String,
        expenseId: String?,
        confidence: Double?,
        source: String?,
        now: Long,
    )

    /** Ids de gastos ya vinculados en este wallet (no ofrecer dos veces el mismo). */
    @Query("SELECT matched_expense_id FROM statement_line WHERE wallet_id = :walletId AND matched_expense_id IS NOT NULL")
    suspend fun getMatchedExpenseIds(walletId: String): List<String>
}
