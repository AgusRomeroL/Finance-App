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
 * wallet_transfer, income_source y, desde v13, category) el doc remoto solo
 * se aplica si su `updatedAt` es ESTRICTAMENTE mayor que el local. Docs sin
 * campo (seed, legados) deserializan `updatedAt = 0` y nunca pisan una
 * edición local. member/quincena no tienen `updated_at` (solo pull): REPLACE.
 *
 * ── DELETES REMOTOS (Fase 2e + LÁPIDAS) ─────────────────────────────────
 * Dos vías, ambas borran la fila local por id vía DAO directo:
 *
 * 1. `DocumentChange.Type.REMOVED` (deletes duros históricos, compatibilidad).
 *    Limitación conocida: un dispositivo offline muy prolongado cuyo cache ya
 *    no contiene el removal recibiría el doc como ADDED y lo re-insertaría.
 * 2. **Lápida (tombstone)**: un doc con `deleted_at > 0` (soft-delete que el
 *    push escribe en vez de borrar el doc). Como el doc SIGUE existiendo,
 *    incluso un dispositivo offline prolongado lo recibe (como ADDED) y borra
 *    localmente — cierra la "resurrección" de la vía 1. Gate LWW: la lápida
 *    solo se aplica si max(deleted_at, updated_at) remoto >= updated_at local
 *    (un doc editado localmente DESPUÉS del borrado remoto sobrevive y su
 *    re-push limpia la lápida — ver ExpenseRepositoryFirestore).
 *
 * Ninguna vía ajusta saldos: el saldo del wallet viaja como estado en su
 * propio documento, que llega por su propio listener.
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
    private val savingsGoalDao = db.savingsGoalDao()
    private val loanDao = db.loanDao()
    private val installmentPlanDao = db.installmentPlanDao()
    private val pendingCaptureDao = db.pendingCaptureDao()

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

        // members / quincenas: desde v14 la app los escribe localmente (wizard de
        // onboarding, CRUD de maestros) → LWW por updated_at. Seed/legados (0)
        // nunca pisan una edición local.
        listeners += register(
            "members",
            { it.toMemberEntity() },
            apply = { memberDao.upsert(it) },
            shouldApply = { remote ->
                val local = memberDao.getById(remote.id)
                local == null || remote.updatedAt > local.updatedAt
            },
        )
        listeners += register(
            "quincenas",
            { it.toQuincenaEntity() },
            apply = { quincenaDao.upsert(it) },
            shouldApply = { remote ->
                val local = quincenaDao.getById(remote.id)
                local == null || remote.updatedAt > local.updatedAt
            },
        )

        // categories: desde v13 la app las escribe localmente (alta inline en
        // captura, edición de color) → LWW por updated_at, como los wallets.
        // Seed/legados (updatedAt=0) nunca pisan una edición local.
        listeners += register(
            "categories",
            { it.toCategoryEntity() },
            apply = { categoryDao.upsert(it) },
            shouldApply = { remote ->
                val local = categoryDao.getById(remote.id)
                local == null || remote.updatedAt > local.updatedAt
            },
        )

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

        // wallet_transfer (RF-41): LWW + removal remoto (duro o por lápida).
        listeners += register(
            "wallet_transfer",
            { it.toWalletTransferEntity() },
            apply = { walletTransferDao.insert(it) },
            shouldApply = { remote ->
                val local = walletTransferDao.getById(remote.id)
                local == null || remote.updatedAt > local.updatedAt
            },
            onRemoved = { walletTransferDao.deleteById(it) },
            localUpdatedAt = { walletTransferDao.getById(it)?.updatedAt },
        )

        // income_source: LWW + removal remoto (duro o por lápida).
        listeners += register(
            "income_source",
            { it.toIncomeSourceEntity() },
            apply = { incomeSourceDao.insert(it) },
            shouldApply = { remote ->
                val local = incomeSourceDao.getById(remote.id)
                local == null || remote.updatedAt > local.updatedAt
            },
            onRemoved = { incomeSourceDao.deleteById(it) },
            localUpdatedAt = { incomeSourceDao.getById(it)?.updatedAt },
        )

        // Hoja de balance (MVP Fase 3.5): savings_goal / loan / installment_plan,
        // todas con LWW + removal remoto (duro o por lápida).
        listeners += register(
            "savings_goal",
            { it.toSavingsGoalEntity() },
            apply = { savingsGoalDao.insert(it) },
            shouldApply = { remote ->
                val local = savingsGoalDao.getById(remote.id)
                local == null || remote.updatedAt > local.updatedAt
            },
            onRemoved = { savingsGoalDao.deleteById(it) },
            localUpdatedAt = { savingsGoalDao.getById(it)?.updatedAt },
        )

        listeners += register(
            "loan",
            { it.toLoanEntity() },
            apply = { loanDao.insert(it) },
            shouldApply = { remote ->
                val local = loanDao.getById(remote.id)
                local == null || remote.updatedAt > local.updatedAt
            },
            onRemoved = { loanDao.deleteById(it) },
            localUpdatedAt = { loanDao.getById(it)?.updatedAt },
        )

        listeners += register(
            "installment_plan",
            { it.toInstallmentPlanEntity() },
            apply = { installmentPlanDao.insert(it) },
            shouldApply = { remote ->
                val local = installmentPlanDao.getById(remote.id)
                local == null || remote.updatedAt > local.updatedAt
            },
            onRemoved = { installmentPlanDao.deleteById(it) },
            localUpdatedAt = { installmentPlanDao.getById(it)?.updatedAt },
        )

        // proposals (Fase B): gastos que un COLABORADOR propone desde la web o su
        // app. No pueden escribir el ledger; el dueño los ve como sugerencias en
        // su bandeja `pending_capture` (source=REMOTE) y entran por el patrón
        // propose-then-confirm ya existente. Anti-eco: se insertan vía DAO directo
        // con id determinista ("proposal:{id}") para evitar duplicados en re-emisiones.
        listeners += household().collection("proposals")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Listener de 'proposals' falló; se reintentará al reconectar", error)
                    return@addSnapshotListener
                }
                val changes = snapshot?.documentChanges ?: return@addSnapshotListener
                scope.launch { changes.forEach { applyProposalChange(it) } }
            }

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
     *
     * LÁPIDAS: un doc entrante con `deleted_at > 0` NUNCA se upserta. Si la
     * colección tiene [onRemoved], se borra la fila local (gate LWW contra
     * [localUpdatedAt] si se proporcionó — borrar una fila inexistente es
     * no-op, así que sin timestamp local se borra directo). Si la colección NO
     * tiene flujo de borrado (members, quincenas, categories, wallets: hoy
     * nadie escribe lápidas ahí), el doc lápida simplemente se ignora — jamás
     * debe pisar la fila local con el doc mínimo de la lápida.
     */
    private fun <T : Any> register(
        label: String,
        mapper: (DocumentSnapshot) -> T?,
        apply: suspend (T) -> Unit,
        shouldApply: (suspend (T) -> Boolean)? = null,
        onRemoved: (suspend (String) -> Unit)? = null,
        localUpdatedAt: (suspend (String) -> Long?)? = null,
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
                                // Lápida ANTES de mapear: el doc mínimo de una
                                // lápida no es mapeable (campos limpiados).
                                val deletedAt = change.document.tombstoneDeletedAt()
                                if (deletedAt > 0L) {
                                    if (onRemoved != null) {
                                        val tombstoneTs =
                                            maxOf(deletedAt, change.document.remoteUpdatedAt())
                                        val localTs = localUpdatedAt?.invoke(change.document.id)
                                        if (localTs == null || tombstoneTs >= localTs) {
                                            onRemoved(change.document.id)
                                        }
                                    }
                                    continue
                                }
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
     * LÁPIDA (tombstone): un doc con `deleted_at > 0` también se trata como
     * borrado — se chequea ANTES de mapear porque el doc lápida viene con los
     * campos limpiados (no mapeable). Gate LWW: solo se aplica si
     * max(deleted_at, updated_at) remoto >= updated_at local; así una edición
     * local POSTERIOR al borrado remoto sobrevive y su re-push (que limpia la
     * lápida en Firestore) resucita el gasto legítimamente en los demás
     * dispositivos.
     *
     * CONTRATO con los escritores remotos (push Android y web del titular): el
     * gasto y su subcolección `attributions` se escriben en UN batch atómico,
     * así el `get()` posterior las ve completas. Aun así, para un gasto NUEVO
     * cuya lectura de atribuciones llegue vacía (carrera de propagación o fallo
     * transitorio de red) se reintenta una vez con backoff corto antes de
     * aplicarlo sin atribuciones.
     */
    private suspend fun applyExpenseChange(change: DocumentChange) {
        try {
            if (change.type == DocumentChange.Type.REMOVED) {
                expenseDao.deleteById(change.document.id)
                return
            }

            val doc = change.document

            // Lápida: borrado soft multi-dispositivo (anti-resurrección). Va
            // ANTES del mapeo (el doc lápida no es mapeable a ExpenseEntity).
            val deletedAt = doc.tombstoneDeletedAt()
            if (deletedAt > 0L) {
                val localForTombstone = expenseDao.getById(doc.id)
                val tombstoneTs = maxOf(deletedAt, doc.remoteUpdatedAt())
                if (localForTombstone == null || tombstoneTs >= localForTombstone.updatedAt) {
                    // Las atribuciones locales caen por FK CASCADE. NO ajusta
                    // saldos (el wallet llega por su propio listener).
                    expenseDao.deleteById(doc.id)
                }
                return
            }

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

            suspend fun readAttribs(): List<ExpenseAttributionEntity> = try {
                doc.reference.collection("attributions").get().await()
                    .documents.mapNotNull { it.toExpenseAttributionEntity(expense.id) }
            } catch (e: Exception) {
                // Sin subcolección de atribuciones o fallo de red: lista vacía.
                emptyList()
            }

            var attribs = readAttribs()
            if (attribs.isEmpty() && local == null) {
                // Gasto nuevo sin atribuciones visibles: probable carrera de
                // propagación del batch remoto. Un reintento corto evita dejarlo
                // sin atribución hasta el próximo updated_at.
                kotlinx.coroutines.delay(1_500)
                attribs = readAttribs()
            }

            suspend fun applyToRoom() = db.withTransaction {
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

            try {
                applyToRoom()
            } catch (e: android.database.sqlite.SQLiteConstraintException) {
                // Carrera de ORDEN entre listeners: una cuota MSI remota puede
                // llegar antes que su installment_plan (o un gasto antes que su
                // quincena aprovisionada por el peer). El doc no se re-emite solo,
                // así que sin reintento quedaría saltado para siempre.
                kotlinx.coroutines.delay(2_500)
                applyToRoom()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fallo al aplicar gasto remoto ${change.document.id} a Room", e)
        }
    }

    /**
     * Refleja una propuesta remota (colaborador) en la bandeja local
     * `pending_capture` con `source="REMOTE"`. Solo procesa las PENDING nuevas
     * (ADDED/MODIFIED con status PENDING). Id determinista `proposal:{docId}`
     * para que re-emisiones del snapshot NO dupliquen la fila (el insert es
     * REPLACE). No encola en sync_queue (pending_capture es local-only).
     */
    private suspend fun applyProposalChange(change: DocumentChange) {
        try {
            if (change.type == DocumentChange.Type.REMOVED) return
            val doc = change.document
            val status = (doc.getString("status") ?: "PENDING")
            if (status != "PENDING") return

            val amount = (doc.get("amountMxn") ?: doc.get("amount_mxn")) as? Number ?: return
            val concept = (doc.getString("concept") ?: doc.getString("note"))?.takeIf { it.isNotBlank() }
                ?: "Propuesta"
            val occurredAt = ((doc.get("occurredAt") ?: doc.get("occurred_at")) as? Number)?.toLong()
                ?: System.currentTimeMillis()
            val note = doc.getString("note")

            // Fase 6 (colaboradores): el proposer pagó con SU dinero. Se resuelve su
            // miembro vinculado (roles/{uid}.linkedMemberId) y se sugiere como PAYER
            // al 100% — el Review lo preactiva como tercero + reembolsable, y al
            // aceptar el gasto cae en "Por reembolsar" a favor del colaborador.
            val proposerUid = doc.getString("proposedByUid")
            val payerJson = proposerUid?.let { uid ->
                runCatching {
                    household().collection("roles").document(uid).get().await()
                        .getString("linkedMemberId")
                }.getOrNull()
            }?.let { memberId -> """{"$memberId":10000}""" }

            val localId = "proposal:${doc.id}"
            // Idempotencia: si ya existe (y no está PENDING, o ya está), no re-crea
            // trabajo del usuario. Un REPLACE sobre una fila ya confirmada la
            // resucitaría, así que solo insertamos si no existe o sigue PENDING.
            val existing = pendingCaptureDao.getById(localId)
            if (existing != null && existing.status != "PENDING") return

            pendingCaptureDao.insert(
                mx.budget.data.local.entity.PendingCaptureEntity(
                    id = localId,
                    source = "REMOTE",
                    amountMxn = amount.toDouble(),
                    concept = concept,
                    occurredAt = occurredAt,
                    status = "PENDING",
                    enrichStatus = "READY",
                    createdAt = System.currentTimeMillis(),
                    notes = note,
                    suggestedPayerJson = payerJson,
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Fallo al aplicar propuesta remota ${change.document.id}", e)
        }
    }

    companion object {
        private const val TAG = "RemotePullSync"
    }
}
