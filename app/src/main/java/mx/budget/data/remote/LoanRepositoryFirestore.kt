package mx.budget.data.remote

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import mx.budget.data.local.entity.LoanEntity
import mx.budget.data.repository.LoanRepository

/**
 * Lado nube (Firestore) del [LoanRepository] — MVP Fase 3.5: solo lo usa el
 * SyncManager para empujar préstamos. Subcolección `households/{hh}/loan`.
 * Lecturas no-op (la app SIEMPRE lee de Room; el pull lo hace RemotePullSync).
 *
 * [deleteById] existe porque el SyncManager solo conoce el id al drenar un
 * `LOAN|DELETE` (la fila local ya no existe); usa la ruta directa con el
 * [householdId] del contenedor.
 */
class LoanRepositoryFirestore(
    private val firestore: FirebaseFirestore,
    private val householdId: String,
) : LoanRepository {

    private fun collection(hh: String) =
        firestore.collection("households").document(hh).collection("loan")

    override fun observeAll(householdId: String): Flow<List<LoanEntity>> = flowOf(emptyList())

    override fun observeTotalReceivable(householdId: String): Flow<Double> = flowOf(0.0)

    override suspend fun getOutstanding(householdId: String): List<LoanEntity> = emptyList()

    override suspend fun getById(id: String): LoanEntity? = null

    override suspend fun insert(loan: LoanEntity) {
        collection(loan.householdId).document(loan.id).set(loan, SetOptions.merge()).await()
    }

    override suspend fun update(loan: LoanEntity) = insert(loan)

    override suspend fun delete(loan: LoanEntity) {
        writeTombstone(collection(loan.householdId).document(loan.id), loan.id)
    }

    /** Borrado remoto por id (drenado de `LOAN|DELETE` del outbox). */
    suspend fun deleteById(loanId: String) {
        writeTombstone(collection(householdId).document(loanId), loanId)
    }

    /**
     * LÁPIDA (tombstone): en vez de borrar el doc (cuyo REMOVED puede perderse
     * para un dispositivo offline prolongado, que "resucitaría" el préstamo),
     * se reemplaza por una lápida mínima con `deletedAt`; los pulls la tratan
     * como borrado (set SIN merge limpia el resto de campos). Decisión
     * consciente: para loans el borrado GANA sobre una edición offline
     * concurrente (un abono re-pusheado tras el borrado converge a borrado en
     * todos los dispositivos vía el gate LWW >= del pull).
     */
    private suspend fun writeTombstone(ref: DocumentReference, id: String) {
        val now = System.currentTimeMillis()
        ref.set(mapOf("id" to id, "deletedAt" to now, "updatedAt" to now)).await()
    }

    override suspend fun applyPayment(loanId: String, paymentMxn: Double) {
        // No-op: el push del UPSERT (documento completo con el nuevo remaining)
        // lo hace insert() al drenar el outbox.
    }
}
