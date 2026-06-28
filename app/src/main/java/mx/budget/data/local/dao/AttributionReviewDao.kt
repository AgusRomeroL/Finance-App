package mx.budget.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.entity.AttributionReviewEntity

/**
 * DAO de la cola de revisión de atribuciones inferidas (Apéndice F.3).
 */
@Dao
interface AttributionReviewDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<AttributionReviewEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: AttributionReviewEntity)

    /** Sugerencias pendientes, más recientes primero. Alimenta la pantalla de revisión. */
    @Query(
        """
        SELECT * FROM attribution_review
        WHERE status = 'PENDING'
        ORDER BY confidence DESC, created_at DESC
        """
    )
    fun observePending(): Flow<List<AttributionReviewEntity>>

    /** Conteo de pendientes para el badge del dashboard. */
    @Query("SELECT COUNT(*) FROM attribution_review WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    /**
     * Atribuciones auto-aplicadas por la máquina (provenance). Alimentan el
     * bloque "auto-aplicado · revertir" de la pantalla de revisión, para que el
     * usuario vea y pueda deshacer lo que el worker aplicó solo (Apéndice F.3.6/F.3.7).
     */
    @Query(
        """
        SELECT * FROM attribution_review
        WHERE status = 'AUTO_APPLIED'
        ORDER BY confidence DESC, created_at DESC
        """
    )
    fun observeAutoApplied(): Flow<List<AttributionReviewEntity>>

    @Query("SELECT * FROM attribution_review WHERE id = :id")
    suspend fun getById(id: String): AttributionReviewEntity?

    @Query("UPDATE attribution_review SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    /**
     * Borra SOLO las sugerencias pendientes, preservando las que el usuario ya
     * resolvió (CONFIRMED/REJECTED/EDITED) y las máquina-aplicadas (AUTO_APPLIED).
     * El worker la llama al inicio para re-inferir sin destruir decisiones.
     */
    @Query("DELETE FROM attribution_review WHERE status = 'PENDING'")
    suspend fun deletePending()

    /**
     * Reviews resueltas POR EL USUARIO. El worker salta estos (expenseId, role)
     * para no resurgir lo rechazado ni pisar lo confirmado/editado.
     */
    @Query("SELECT * FROM attribution_review WHERE status IN ('REJECTED', 'CONFIRMED', 'EDITED')")
    suspend fun getHumanResolved(): List<AttributionReviewEntity>

    /** Reviews de un estado dado (p.ej. AUTO_APPLIED, para revertir en re-normalización forzada). */
    @Query("SELECT * FROM attribution_review WHERE status = :status")
    suspend fun getByStatus(status: String): List<AttributionReviewEntity>

    /** Borra reviews de un estado dado (p.ej. AUTO_APPLIED al forzar re-normalización). */
    @Query("DELETE FROM attribution_review WHERE status = :status")
    suspend fun deleteByStatus(status: String)
}
