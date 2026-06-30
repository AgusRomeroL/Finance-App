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
        firestore.collectionGroup("wallet_transfer")
            .whereEqualTo("id", transferId).get().await()
            .documents.firstOrNull()?.reference?.delete()?.await()
    }
}
