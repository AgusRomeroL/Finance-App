package mx.budget.data.remote

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import mx.budget.data.local.entity.QuincenaEntity

/**
 * Lado nube de las quincenas (`households/{hid}/quincenas/{id}`).
 *
 * Sustituye a la version anterior, que implementaba el [mx.budget.data.repository.QuincenaRepository]
 * completo, no la instanciaba nadie y traia literales rotos y fechas de
 * demostracion. Ahora sigue el molde de [HouseholdRepositoryFirestore]: sin
 * interfaz, un solo `upsert` y el push lo drena el
 * [mx.budget.data.sync.SyncManager] con el kind `QUINCENA`.
 */
class QuincenaRepositoryFirestore(
    private val firestore: FirebaseFirestore,
    private val householdId: String,
) {

    private fun document(quincenaId: String) =
        firestore.collection("households")
            .document(householdId)
            .collection("quincenas")
            .document(quincenaId)

    /**
     * Sube la quincena con `merge` y limpia la lapida en el mismo lote.
     *
     * `closedAt` se manda con [FieldValue.delete] cuando es nulo (reapertura).
     * Sin eso el otro dispositivo leeria una quincena reabierta con la fecha de
     * cierre vieja: el mapper del pull cae a snake_case si falta la clave
     * camelCase, y los documentos de la semilla si traen `closed_at`.
     */
    suspend fun upsert(quincena: QuincenaEntity) {
        val ref = document(quincena.id)
        val batch = firestore.batch()
        batch.set(ref, quincena.toRemoteMap(), SetOptions.merge())
        val limpieza = mutableMapOf<String, Any>(
            "deletedAt" to FieldValue.delete(),
            "deleted_at" to FieldValue.delete(),
        )
        if (quincena.closedAt == null) {
            limpieza["closedAt"] = FieldValue.delete()
            limpieza["closed_at"] = FieldValue.delete()
        }
        batch.update(ref, limpieza)
        batch.commit().await()
    }

    /**
     * Mapa explicito en camelCase en vez de `set(entity)`: el serializador de
     * beans de Firestore ya mordio a las plantillas recurrentes (`isActive` se
     * convertia en `active`).
     */
    private fun QuincenaEntity.toRemoteMap(): Map<String, Any> {
        val mapa = mutableMapOf<String, Any>(
            "id" to id,
            "householdId" to householdId,
            "year" to year,
            "month" to month,
            "half" to half,
            "startDate" to startDate,
            "endDate" to endDate,
            "label" to label,
            "projectedIncomeMxn" to projectedIncomeMxn,
            "projectedExpensesMxn" to projectedExpensesMxn,
            "actualIncomeMxn" to actualIncomeMxn,
            "actualExpensesMxn" to actualExpensesMxn,
            "status" to status,
            "updatedAt" to updatedAt,
        )
        closedAt?.let { mapa["closedAt"] = it }
        return mapa
    }
}
