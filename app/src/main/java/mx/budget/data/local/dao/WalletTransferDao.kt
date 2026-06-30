package mx.budget.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.entity.WalletTransferEntity
import mx.budget.data.local.result.TransferWithNames

@Dao
interface WalletTransferDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transfer: WalletTransferEntity)

    @Delete
    suspend fun delete(transfer: WalletTransferEntity)

    @Query("SELECT * FROM wallet_transfer WHERE id = :id")
    suspend fun getById(id: String): WalletTransferEntity?

    /**
     * Transferencias del hogar con los nombres de ambas cuentas resueltos,
     * ordenadas por fecha descendente. Alimenta el historial de transferencias.
     */
    @Query(
        """
        SELECT
            t.id                AS id,
            t.amount_mxn        AS amountMxn,
            t.occurred_at       AS occurredAt,
            t.note              AS note,
            src.display_name    AS fromName,
            dst.display_name    AS toName,
            dst.kind            AS toKind
        FROM wallet_transfer t
        INNER JOIN payment_method src ON src.id = t.from_payment_method_id
        INNER JOIN payment_method dst ON dst.id = t.to_payment_method_id
        WHERE t.household_id = :householdId
        ORDER BY t.occurred_at DESC
        """
    )
    fun observeWithNames(householdId: String): Flow<List<TransferWithNames>>
}
