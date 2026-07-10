package mx.budget.data.remote

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import mx.budget.data.local.entity.RecurrenceTemplateEntity
import mx.budget.data.repository.RecurrenceRepository

/**
 * Lado nube (Firestore) del [RecurrenceRepository] — paquete ANDROID-TEMPLATES:
 * solo lo usa el SyncManager para empujar plantillas recurrentes. Subcolección
 * `households/{hh}/recurrence_template` (doc id = id de la plantilla; campos
 * camelCase serializados de [RecurrenceTemplateEntity], incluido `updatedAt`).
 * Lecturas no-op (la app SIEMPRE lee de Room; el pull lo hace RemotePullSync).
 *
 * [deleteById] existe porque el SyncManager solo conoce el id al drenar un
 * `RECURRENCE|DELETE` (la fila local ya no existe); usa la ruta directa con el
 * [householdId] del contenedor.
 */
class RecurrenceRepositoryFirestore(
    private val firestore: FirebaseFirestore,
    private val householdId: String,
) : RecurrenceRepository {

    private fun collection(hh: String) =
        firestore.collection("households").document(hh).collection("recurrence_template")

    override fun observeActive(householdId: String): Flow<List<RecurrenceTemplateEntity>> =
        flowOf(emptyList())

    override fun observeAll(householdId: String): Flow<List<RecurrenceTemplateEntity>> =
        flowOf(emptyList())

    override suspend fun getById(id: String): RecurrenceTemplateEntity? = null

    override suspend fun getTemplatesForCadence(
        householdId: String,
        cadence: String,
    ): List<RecurrenceTemplateEntity> = emptyList()

    /**
     * Push del doc completo como **mapa explícito camelCase** (NO `set(entity)`):
     * el serializador bean de Firestore convertiría `isActive` en el campo
     * `"active"` (regla JavaBean de prefijo `is`), el pull leería
     * `isActive/is_active` → null → default `true` y una PAUSA jamás se
     * propagaría. El mapa fija los nombres del contrato. `set` SIN merge:
     * reemplaza el doc entero, lo que además LIMPIA una lápida previa
     * (`deletedAt`) cuando una edición local posterior al borrado remoto gana
     * el LWW y re-pushea (mismo criterio que ExpenseRepositoryFirestore).
     */
    override suspend fun insert(template: RecurrenceTemplateEntity) {
        val doc = mapOf(
            "id" to template.id,
            "householdId" to template.householdId,
            "concept" to template.concept,
            "categoryId" to template.categoryId,
            "defaultAmountMxn" to template.defaultAmountMxn,
            "defaultPaymentMethodId" to template.defaultPaymentMethodId,
            "cadence" to template.cadence,
            "cadenceDetail" to template.cadenceDetail,
            "nextExpectedDate" to template.nextExpectedDate,
            "defaultBeneficiaryIds" to template.defaultBeneficiaryIds,
            "defaultPayerSplit" to template.defaultPayerSplit,
            "isActive" to template.isActive,
            "confidenceScore" to template.confidenceScore,
            "learnedFromExpenseIds" to template.learnedFromExpenseIds,
            "defaultExternalPayerMemberId" to template.defaultExternalPayerMemberId,
            "defaultSettlementStatus" to template.defaultSettlementStatus,
            "updatedAt" to template.updatedAt,
        )
        collection(template.householdId).document(template.id).set(doc).await()
    }

    override suspend fun update(template: RecurrenceTemplateEntity) = insert(template)

    override suspend fun delete(template: RecurrenceTemplateEntity) {
        writeTombstone(collection(template.householdId).document(template.id), template.id)
    }

    /** Borrado remoto por id (drenado de `RECURRENCE|DELETE` del outbox). */
    suspend fun deleteById(templateId: String) {
        writeTombstone(collection(householdId).document(templateId), templateId)
    }

    /** Pausa/reanuda: no-op remoto — el push del UPSERT (doc completo con el
     *  nuevo `isActive`) lo hace [insert] al drenar el outbox. */
    override suspend fun pause(templateId: String) = Unit

    override suspend fun resume(templateId: String) = Unit

    /**
     * LÁPIDA (tombstone): en vez de borrar el doc (cuyo REMOVED puede perderse
     * para un dispositivo offline prolongado, que "resucitaría" la plantilla),
     * se reemplaza por una lápida mínima con `deletedAt`; los pulls la tratan
     * como borrado (set SIN merge limpia el resto de campos). Mismo patrón que
     * [LoanRepositoryFirestore]; se incluye `householdId` para que la web pueda
     * filtrar lápidas sin depender de la ruta.
     */
    private suspend fun writeTombstone(ref: DocumentReference, id: String) {
        val now = System.currentTimeMillis()
        ref.set(
            mapOf(
                "id" to id,
                "householdId" to householdId,
                "deletedAt" to now,
                "updatedAt" to now,
            )
        ).await()
    }
}
