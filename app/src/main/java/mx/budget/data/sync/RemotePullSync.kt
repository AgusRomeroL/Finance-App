package mx.budget.data.sync

import android.util.Log
import androidx.room.withTransaction
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import mx.budget.data.local.BudgetDatabase
import mx.budget.data.local.entity.ExpenseAttributionEntity

/**
 * Orquestador del pull offline-first (Firestore → Room).
 *
 * Es el espejo del [SyncManager] (que hace push). Escucha cada subcolección
 * del household vía `addSnapshotListener` y refleja los `documentChanges`
 * entrantes en Room para que los cambios hechos en OTRO dispositivo aparezcan
 * localmente (sincronización multi-dispositivo). El primer snapshot tras
 * registrar un listener trae TODOS los docs como `ADDED`, así que el arranque
 * sigue haciendo el volcado completo.
 *
 * ── ANTI-ECO (crítico) ──────────────────────────────────────────────────
 * Los upserts/deletes se hacen SIEMPRE vía los DAO directos, NUNCA vía los
 * repos públicos (`ExpenseRepositoryImpl`, etc.). Esos repos encolan en
 * `sync_queue`, así que escribir a través de ellos dispararía un push que
 * re-subiría a Firestore y crearía un bucle push↔pull infinito. El pull no
 * encola: solo escribe en las tablas locales.
 *
 * ── RESOLUCIÓN DE CONFLICTOS (LWW por `updated_at`, MVP Fase 2c) ────────
 * Para las entidades que la app escribe localmente (expense, payment_method,
 * wallet_transfer, income_source) el doc remoto solo se aplica si su
 * `updatedAt` es ESTRICTAMENTE mayor que el local. Docs sin campo (seed,
 * legados) deserializan `updatedAt = 0` y nunca pisan una edición local.
 * category/member/quincena no tienen `updated_at` (solo pull): REPLACE.
 *
 * ── DELETES REMOTOS (Fase 2e) ───────────────────────────────────────────
 * Un `DocumentChange.Type.REMOVED` borra la fila local por id vía DAO
 * directo. NO ajusta saldos: el saldo del wallet viaja como estado en su
 * propio documento, que llega por su propio listener. Limitación conocida
 * (documentada, sin tombstones): un dispositivo offline muy prolongado
 * podría "resucitar" un doc si su cache ya no contiene el removal.
 *
 * ── NOMBRES DE CAMPO (Fase 2d) ──────────────────────────────────────────
 * La deserialización usa los mappers manuales de [FirestoreMappers] con
 * fallback dual camelCase/snake_case, así que tanto los docs subidos por la
 * app (`set(entity)` → camelCase) como los sembrados por
 * `scripts/seed_firebase.py` (snake_case) se aplican correctamente.
 */
class RemotePullSync(
    private val firestore: FirebaseFirestore,
    private val db: BudgetDatabase,
    private val scope: CoroutineScope,
    private val householdId: String
) {

    private val expenseDao = db.expenseDao()
    private val attributionDao = db.expenseAttributionDao()
    private val categoryDao = db.categoryDao()
    private val memberDao = db.memberDao()
    private val paymentMethodDao = db.paymentMethodDao()
    private val quincenaDao = db.quincenaDao()
    private val walletTransferDao = db.walletTransferDao()
    private val incomeSourceDao = db.incomeSourceDao()

    private val listeners = mutableListOf<ListenerRegistration>()

    private fun household() =
        firestore.collection("households").document(householdId)

    /**
     * Registra los snapshot listeners de cada subcolección. Idempotente:
     * si ya estaba arrancado, primero remueve los listeners previos.
     */
    fun start() {
        stop()

        // expenses (+ subcolección de atribuciones por gasto): handler propio.
        listeners += household().collection("expenses")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Listener de 'expenses' falló; se reintentará al reconectar", error)
                    return@addSnapshotListener
                }
                val changes = snapshot?.documentChanges ?: return@addSnapshotListener
                scope.launch { changes.forEach { applyExpenseChange(it) } }
            }

        // categories / members / quincenas: solo pull, sin LWW (REPLACE).
        listeners += register("categories", { it.toCategoryEntity() }, { categoryDao.upsert(it) })
        listeners += register("members", { it.toMemberEntity() }, { memberDao.upsert(it) })
        listeners += register("quincenas", { it.toQuincenaEntity() }, { quincenaDao.upsert(it) })

        // wallets → payment_method: LWW por updated_at.
        listeners += register(
            "wallets",
            { it.toPaymentMethodEntity() },
            apply = { paymentMethodDao.upsert(it) },
            shouldApply = { remote ->
                val local = paymentMethodDao.getById(remote.id)
                local == null || remote.updatedAt > local.updatedAt
            },
        )

        // wallet_transfer (RF-41): LWW + removal remoto.
        listeners += register(
            "wallet_transfer",
            { it.toWalletTransferEntity() },
            apply = { walletTransferDao.insert(it) },
            shouldApply = { remote ->
                val local = walletTransferDao.getById(remote.id)
                local == null || remote.updatedAt > local.updatedAt
            },
            onRemoved = { walletTransferDao.deleteById(it) },
        )

        // income_source: LWW + removal remoto.
        listeners += register(
            "income_source",
            { it.toIncomeSourceEntity() },
            apply = { incomeSourceDao.insert(it) },
            shouldApply = { remote ->
                val local = incomeSourceDao.getById(remote.id)
                local == null || remote.updatedAt > local.updatedAt
            },
            onRemoved = { incomeSourceDao.deleteById(it) },
        )

        Log.i(TAG, "Pull arrancado para household=$householdId (${listeners.size} listeners)")
    }

    /** Remueve todos los listeners. Seguro de llamar varias veces. */
    fun stop() {
        listeners.forEach { it.remove() }
        listeners.clear()
    }

    /**
     * Listener genérico de una subcolección "plana": aplica ADDED/MODIFIED con
     * el [mapper] (fallo de mapeo = doc ignorado + log, nunca crash), gateado
     * por [shouldApply] (LWW), y borra por id en REMOVED si hay [onRemoved].
     */
    private fun <T : Any> register(
        label: String,
        mapper: (DocumentSnapshot) -> T?,
        apply: suspend (T) -> Unit,
        shouldApply: (suspend (T) -> Boolean)? = null,
        onRemoved: (suspend (String) -> Unit)? = null,
    ): ListenerRegistration =
        household().collection(label).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Listener de '$label' falló; se reintentará al reconectar", error)
                return@addSnapshotListener
            }
            val changes = snapshot?.documentChanges ?: return@addSnapshotListener
            scope.launch {
                for (change in changes) {
                    try {
                        when (change.type) {
                            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                val entity = mapper(change.document)
                                if (entity == null) {
                                    Log.w(TAG, "Doc '$label/${change.document.id}' no mapeable; ignorado")
                                } else if (shouldApply == null || shouldApply(entity)) {
                                    apply(entity)
                                }
                            }
                            DocumentChange.Type.REMOVED -> onRemoved?.invoke(change.document.id)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Fallo al aplicar cambio de '$label/${change.document.id}'", e)
                    }
                }
            }
        }

    /**
     * Caso especial de expenses: cada gasto lleva su lista de atribuciones en
     * la subcolección `attributions`. Como un snapshot listener sobre la
     * colección padre NO incluye las subcolecciones, leemos las atribuciones
     * con un `get()` adicional por gasto, y aplicamos
     * upsert(expense) + deleteByExpenseId + insertAll(attribs) en una
     * transacción — solo si el doc remoto gana el LWW.
     *
     * REMOVED: borra el gasto por id (las atribuciones caen por FK CASCADE).
     *
     * TODO(remote-attributions): si en el futuro se decide EMBEBER las
     *  atribuciones dentro del documento del gasto (campo `attributions`),
     *  léelas de ahí en vez de hacer el `get()` por subcolección. Por ahora,
     *  si la subcolección no existe o falla, se deja la lista vacía.
     */
    private suspend fun applyExpenseChange(change: DocumentChange) {
        try {
            if (change.type == DocumentChange.Type.REMOVED) {
                expenseDao.deleteById(change.document.id)
                return
            }

            val doc = change.document
            val expense = doc.toExpenseEntity()
            if (expense == null) {
                Log.w(TAG, "Doc 'expenses/${doc.id}' no mapeable; ignorado")
                return
            }

            // LWW: aplicar solo si el remoto es estrictamente más nuevo. Un doc
            // seed/legado (updatedAt=0) nunca pisa una fila local editada, y en el
            // volcado inicial las filas del asset (también 0) se conservan tal cual.
            val local = expenseDao.getById(expense.id)
            if (local != null && expense.updatedAt <= local.updatedAt) return

            val attribs: List<ExpenseAttributionEntity> = try {
                doc.reference.collection("attributions").get().await()
                    .documents.mapNotNull { it.toExpenseAttributionEntity(expense.id) }
            } catch (e: Exception) {
                // Sin subcolección de atribuciones o fallo de red: lista vacía.
                emptyList()
            }

            db.withTransaction {
                // PRESERVAR concept_canonical local (Apéndice F.3): es una columna
                // calculada SOLO localmente por el pipeline retro. Los docs remotos
                // que no la traen deserializan a null; un REPLACE ciego la borraría
                // en cada pull. Si el remoto no la trae, conservamos la que ya hay.
                val merged = if (expense.conceptCanonical == null && local?.conceptCanonical != null) {
                    expense.copy(conceptCanonical = local.conceptCanonical)
                } else expense
                expenseDao.upsert(merged)

                // NO borrar atribuciones locales si el remoto no trae ninguna: una
                // lectura vacía (fallo de red o seed sin subcolección) NO debe
                // destruir la atribución local (incluida la inferida por el worker
                // retro). Solo se reemplaza cuando el remoto sí aporta.
                if (attribs.isNotEmpty()) {
                    attributionDao.deleteByExpenseId(expense.id)
                    attributionDao.insertAll(attribs)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fallo al aplicar gasto remoto ${change.document.id} a Room", e)
        }
    }

    companion object {
        private const val TAG = "RemotePullSync"
    }
}
