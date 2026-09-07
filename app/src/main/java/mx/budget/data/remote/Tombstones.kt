package mx.budget.data.remote

import com.google.firebase.firestore.DocumentReference
import kotlinx.coroutines.tasks.await

/**
 * Lápida (tombstone) compartida por los repos del lado nube.
 *
 * Borrar el documento de verdad deja un agujero: el evento `REMOVED` del
 * snapshot listener solo llega a los dispositivos conectados en ese momento, así
 * que uno que estuvo offline lo bastante nunca se entera y "resucita" la fila al
 * volver a pushear su copia local. En su lugar el documento se reemplaza por una
 * lápida mínima con `deletedAt`, que sí sobrevive y que el pull trata como
 * borrado.
 *
 * `set` SIN `merge` es deliberado: limpia el resto de campos, de modo que el
 * documento lápida no sea mapeable a una entidad y ningún pull pueda aplicarlo
 * como si fuera un alta. Por eso el pull comprueba `deleted_at` ANTES de mapear.
 *
 * Convención: el borrado GANA sobre una edición concurrente. Los `insert` de
 * estos repos usan `merge` y NO limpian `deletedAt`, así que un dispositivo que
 * vuelve de un offline largo y re-empuja su copia converge a borrado en todos.
 * La única excepción es el gasto, cuyo `insertWithAttributions` sí limpia la
 * lápida porque su borrado se deshace desde la interfaz.
 */
internal suspend fun DocumentReference.writeTombstone(id: String, householdId: String? = null) {
    val now = System.currentTimeMillis()
    val tombstone = buildMap<String, Any> {
        put("id", id)
        if (householdId != null) put("householdId", householdId)
        put("deletedAt", now)
        put("updatedAt", now)
    }
    set(tombstone).await()
}
