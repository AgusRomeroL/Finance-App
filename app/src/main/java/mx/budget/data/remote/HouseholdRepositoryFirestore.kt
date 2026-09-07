package mx.budget.data.remote

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import mx.budget.data.local.entity.HouseholdEntity

/**
 * Lado nube del documento raíz del hogar (`households/{id}`).
 *
 * No implementa ninguna interfaz de repositorio porque `household` es la única
 * entidad del ledger sin capa de repos: sus escrituras locales pasan por
 * [mx.budget.data.local.dao.HouseholdDao] y el push las drena el
 * [mx.budget.data.sync.SyncManager] con el kind `HOUSEHOLD`.
 *
 * Complementa a [MembershipRepository.createHousehold], que solo escribe
 * `name`, `updatedAt` y `createdBy` (los tres campos que las reglas exigen para
 * el alta). Aquí se sube el espejo completo de la entidad Room, así que un
 * segundo dispositivo recibe divisa, zona horaria y ancla de quincena reales en
 * vez de los valores por defecto.
 */
class HouseholdRepositoryFirestore(
    private val firestore: FirebaseFirestore
) {

    private fun document(householdId: String) =
        firestore.collection("households").document(householdId)

    /**
     * Sube el hogar con `merge` para no pisar `createdBy` (que solo escribe el
     * alta) ni el resto de campos que la nube pueda conocer y Room no. Limpia la
     * lápida en el mismo lote: un alta legítima resucita el documento, igual que
     * en [ExpenseRepositoryFirestore.insertWithAttributions].
     */
    suspend fun insert(household: HouseholdEntity) {
        val ref = document(household.id)
        val batch = firestore.batch()
        batch.set(ref, household.toRemoteMap(), SetOptions.merge())
        batch.update(
            ref,
            mapOf(
                "deletedAt" to FieldValue.delete(),
                "deleted_at" to FieldValue.delete(),
            )
        )
        batch.commit().await()
    }

    /**
     * Mapa explícito en camelCase en vez de `set(entity)`. El serializador de
     * beans de Firestore ya mordió a las plantillas recurrentes (`isActive` se
     * convertía en `active`); aquí además interesa NO enviar campos que la
     * entidad no tiene y la nube sí usa.
     */
    private fun HouseholdEntity.toRemoteMap(): Map<String, Any> = mapOf(
        "id" to id,
        "name" to name,
        "currency" to currency,
        "timezone" to timezone,
        "quincenaAnchor" to quincenaAnchor,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt,
    )
}
