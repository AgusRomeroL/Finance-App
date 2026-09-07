package mx.budget.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import mx.budget.data.local.entity.StatementImportEntity

/**
 * Lado nube de la auditoría de estados de cuenta
 * (`households/{hh}/statement_import`). Solo lo usa el SyncManager para empujar;
 * la app siempre lee de Room.
 *
 * `payload_json` NO viaja: el documento remoto lleva solo la cabecera del estado.
 * El checklist mensual, la tarjeta de deuda y el recordatorio de pago se
 * alimentan de esos campos, mientras que el texto crudo que devolvió el LLM solo
 * le sirve al dispositivo que importó el PDF, que es el único que puede reabrir
 * esa revisión. Con eso el documento se queda en unos cientos de bytes en vez de
 * arrastrar decenas de kilobytes de movimientos por cada estado.
 */
class StatementRepositoryFirestore(
    private val firestore: FirebaseFirestore,
    private val householdId: String,
) {

    private fun collection(hid: String) =
        firestore.collection("households").document(hid).collection("statement_import")

    suspend fun insert(row: StatementImportEntity) {
        collection(row.householdId).document(row.id)
            .set(row.toRemoteMap(), SetOptions.merge()).await()
    }

    /** Borrado remoto por id (drenado de `STATEMENT|DELETE` del outbox). */
    suspend fun deleteById(id: String) {
        collection(householdId).document(id).writeTombstone(id, householdId)
    }

    /**
     * Mapa explícito en camelCase, sin `payload_json`. Los nulos se envían tal
     * cual para que el otro dispositivo pueda BORRAR un dato que aquí se limpió;
     * con `merge` un campo ausente se conservaría con su valor viejo.
     */
    private fun StatementImportEntity.toRemoteMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "householdId" to householdId,
        "walletId" to walletId,
        "emisor" to emisor,
        "last4" to last4,
        "periodoInicio" to periodoInicio,
        "periodoFin" to periodoFin,
        "fechaCorte" to fechaCorte,
        "fechaLimitePago" to fechaLimitePago,
        "saldoTotal" to saldoTotal,
        "pagoMinimo" to pagoMinimo,
        "pagoNoIntereses" to pagoNoIntereses,
        "tasaAnual" to tasaAnual,
        "createdAt" to createdAt,
        "appliedAt" to appliedAt,
        "updatedAt" to updatedAt,
    )
}
