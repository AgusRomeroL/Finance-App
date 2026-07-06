package mx.budget.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.entity.PendingCaptureEntity

/**
 * DAO de la bandeja unificada de capturas pendientes (Apéndice G.1).
 */
@Dao
interface PendingCaptureDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: PendingCaptureEntity)

    /** Capturas por confirmar, más recientes primero. Alimenta el chip del dashboard. */
    @Query("SELECT * FROM pending_capture WHERE status = 'PENDING' ORDER BY created_at DESC")
    fun observePending(): Flow<List<PendingCaptureEntity>>

    /** Conteo de pendientes (para badge/automatizaciones). */
    @Query("SELECT COUNT(*) FROM pending_capture WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM pending_capture WHERE id = :id")
    suspend fun getById(id: String): PendingCaptureEntity?

    @Query("UPDATE pending_capture SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    /**
     * Escribe los campos enriquecidos por el [mx.budget.data.capture.EnrichCaptureWorker]
     * y marca la captura `READY` en una sola sentencia (paquete A2). Cada campo
     * usa `COALESCE`: un `null` del worker **conserva** lo que la fila ya tenía
     * (p. ej. una categoría con evidencia resuelta al insertar) en vez de borrarlo.
     */
    @Query(
        """UPDATE pending_capture SET
            suggested_wallet_id = COALESCE(:walletId, suggested_wallet_id),
            suggested_category_id = COALESCE(:categoryId, suggested_category_id),
            suggested_beneficiary_json = COALESCE(:beneficiaryJson, suggested_beneficiary_json),
            suggested_payer_json = COALESCE(:payerJson, suggested_payer_json),
            notes = COALESCE(:notes, notes),
            enrich_status = 'READY'
        WHERE id = :id"""
    )
    suspend fun updateEnrichment(
        id: String,
        walletId: String?,
        categoryId: String?,
        beneficiaryJson: String?,
        payerJson: String?,
        notes: String?,
    )

    /**
     * Red de seguridad del watchdog (A2): pase lo que pase con el enriquecimiento
     * (timeout, excepción, proceso muerto a medias), la captura termina `READY`
     * para que la tarjeta del dashboard no quede bloqueada en "creando…".
     */
    @Query("UPDATE pending_capture SET enrich_status = 'READY' WHERE id = :id")
    suspend fun markEnrichReady(id: String)

    /** Limpieza opcional: descarta capturas viejas no resueltas. */
    @Query("DELETE FROM pending_capture WHERE status = 'PENDING' AND created_at < :beforeEpoch")
    suspend fun deleteStalePending(beforeEpoch: Long)
}
