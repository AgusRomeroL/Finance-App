package mx.budget.data.quincena

import androidx.room.withTransaction
import mx.budget.data.local.BudgetDatabase
import mx.budget.data.local.dao.QuincenaDao
import mx.budget.data.local.dao.SyncQueueDao
import mx.budget.data.local.entity.QuincenaEntity
import mx.budget.data.local.entity.SyncQueueEntity
import java.time.LocalDate

/**
 * Unica puerta de las transiciones de estado de una quincena (RF-32).
 *
 * Antes de la Fase 5 el estado lo escribian tres sitios sin ponerse de acuerdo:
 * el rollover (que cerraba en automatico al vencer), `QuincenaRepositoryImpl`
 * (que llamaba a `updateStatus`, el cual no sella `updated_at`, asi que el
 * cambio viajaba invisible para el LWW) y un repo Firestore muerto. Aqui se
 * concentran las cuatro transiciones reales y todas hacen lo mismo: sellar
 * `updated_at` y encolar la fila `QUINCENA` en el outbox.
 *
 * El significado de cada estado despues de esta fase:
 * - `PROVISIONED`: existe pero todavia no es el periodo en curso.
 * - `ACTIVE`: periodo en curso, editable. Solo una por hogar.
 * - `CLOSING_REVIEW`: **vencida y pendiente de cierre**, todavia editable. Es a
 *   donde la manda el rollover cuando pasa su ultimo dia, en vez de cerrarla
 *   sola: quien decide que hacer con lo planeado que nunca se ejecuto es una
 *   persona, no el arranque de la app.
 * - `CLOSED`: congelada. El guard del ledger rechaza altas, ediciones y
 *   borrados de sus movimientos hasta que alguien la reabra.
 *
 * El relleno de huecos del rollover si nace `CLOSED`: son periodos en los que
 * nadie uso la app, no hay nada que revisar.
 */
class QuincenaLifecycle(
    private val dao: QuincenaDao,
    private val syncQueueDao: SyncQueueDao? = null,
    private val db: BudgetDatabase? = null,
) {

    /**
     * Alta de una quincena nueva. [enqueue] se apaga cuando el alta la sigue de
     * inmediato una activacion: la fila del outbox seria duplicada.
     */
    suspend fun create(
        quincena: QuincenaEntity,
        now: Long = System.currentTimeMillis(),
        enqueue: Boolean = true,
    ) {
        dao.insert(quincena.copy(updatedAt = now))
        if (enqueue) enqueue(quincena.id)
    }

    /** Escritura directa de la entidad completa, sellando y encolando. */
    suspend fun save(quincena: QuincenaEntity, now: Long = System.currentTimeMillis()) {
        dao.update(quincena.copy(updatedAt = now))
        enqueue(quincena.id)
    }

    /** PROVISIONED o CLOSING_REVIEW a ACTIVE. */
    suspend fun activate(quincena: QuincenaEntity, now: Long = System.currentTimeMillis()) {
        save(quincena.copy(status = ACTIVE, closedAt = null), now)
    }

    /** ACTIVE a CLOSING_REVIEW: la quincena vencio y espera cierre manual. */
    suspend fun markPendingClose(quincena: QuincenaEntity, now: Long = System.currentTimeMillis()) {
        save(quincena.copy(status = CLOSING_REVIEW, closedAt = null), now)
    }

    /**
     * CLOSING_REVIEW a CLOSED. [applyDecisions] corre dentro de la misma
     * transaccion: es donde la pantalla de cierre descarta, mueve o marca como
     * pagado lo que quedaba planeado. Si algo falla ahi, la quincena no se
     * cierra y no queda a medias.
     */
    suspend fun close(
        quincenaId: String,
        now: Long = System.currentTimeMillis(),
        applyDecisions: suspend () -> Unit = {},
    ) = inTransaction {
        applyDecisions()
        recalcActuals(quincenaId, now)
        val quincena = dao.getById(quincenaId) ?: return@inTransaction
        dao.update(quincena.copy(status = CLOSED, closedAt = now, updatedAt = now))
        enqueue(quincenaId)
    }

    /**
     * CLOSED a CLOSING_REVIEW. Deja `closed_at` en nulo, que es el criterio que
     * la interfaz muestra; el freeze mira `status`, nunca esa marca.
     */
    suspend fun reopen(quincenaId: String, now: Long = System.currentTimeMillis()) {
        val quincena = dao.getById(quincenaId) ?: return
        save(quincena.copy(status = CLOSING_REVIEW, closedAt = null), now)
    }

    /** Congela las dos columnas de totales reales desde los movimientos. */
    suspend fun recalcActuals(quincenaId: String, now: Long = System.currentTimeMillis()) {
        dao.recalcActualExpenses(quincenaId, now)
        dao.recalcActualIncome(quincenaId, now)
    }

    suspend fun enqueue(quincenaId: String) {
        syncQueueDao?.enqueue(
            SyncQueueEntity(
                entityType = "QUINCENA",
                entityId = quincenaId,
                operation = "UPSERT",
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    private suspend fun inTransaction(body: suspend () -> Unit) {
        val database = db
        if (database == null) body() else database.withTransaction { body() }
    }

    companion object {
        const val PROVISIONED = "PROVISIONED"
        const val ACTIVE = "ACTIVE"
        const val CLOSING_REVIEW = "CLOSING_REVIEW"
        const val CLOSED = "CLOSED"

        /**
         * Una quincena solo se puede cerrar cuando su ultimo dia ya paso. Sin
         * esto se podria cerrar la quincena en curso y el rollover la volveria
         * a activar en el siguiente arranque.
         */
        fun canClose(quincena: QuincenaEntity, today: LocalDate): Boolean {
            val fin = runCatching { LocalDate.parse(quincena.endDate) }.getOrNull() ?: return false
            return today.isAfter(fin) && quincena.status != CLOSED
        }
    }
}
