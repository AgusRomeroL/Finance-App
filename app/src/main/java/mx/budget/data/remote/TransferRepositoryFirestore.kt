package mx.budget.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import mx.budget.data.local.entity.WalletTransferEntity
import mx.budget.data.local.result.TransferWithNames
import mx.budget.data.repository.TransferRepository

/**
 * Lado nube (Firestore) del [TransferRepository]: solo lo usa el SyncManager para
 * empujar transferencias. Subcolección `households/{householdId}/wallet_transfer`.
 * Las lecturas son no-op (la app SIEMPRE lee de Room; el pull lo hace RemotePullSync).
 */
class TransferRepositoryFirestore(
    private val firestore: FirebaseFirestore
) : TransferRepository {

    private fun collection(householdId: String) =
        firestore.collection("households").document(householdId).collection("wallet_transfer")

    override fun observeTransfers(householdId: String): Flow<List<TransferWithNames>> =
        flowOf(emptyList())

    override suspend fun recordTransfer(transfer: WalletTransferEntity) {
        collection(transfer.householdId).document(transfer.id)
            .set(transfer, SetOptions.merge()).await()
    }

    override suspend fun deleteTransfer(transferId: String) {
        // El SyncManager solo conoce el id; localiza el doc por collectionGroup.
        // LÁPIDA (tombstone): en vez de borrar el doc (cuyo REMOVED puede
        // perderse para un dispositivo offline prolongado), se reemplaza por
        // una lápida mínima con `deletedAt`; los pulls la tratan como borrado
        // (set SIN merge limpia el resto de campos). Decisión consciente: para
        // transfers el borrado GANA sobre una edición offline concurrente
        // (las transferencias no se editan en la app; no hay flujo de
        // "resurrección" como el de expenses).
        val ref = firestore.collectionGroup("wallet_transfer")
            .whereEqualTo("id", transferId).get().await()
            .documents.firstOrNull()?.reference ?: return
        val now = System.currentTimeMillis()
        ref.set(mapOf("id" to transferId, "deletedAt" to now, "updatedAt" to now)).await()
    }
}
