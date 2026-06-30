package mx.budget.data.sync

import android.util.Log
import androidx.room.withTransaction
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import mx.budget.data.local.BudgetDatabase
import mx.budget.data.local.entity.CategoryEntity
import mx.budget.data.local.entity.ExpenseAttributionEntity
import mx.budget.data.local.entity.ExpenseEntity
import mx.budget.data.local.entity.MemberEntity
import mx.budget.data.local.entity.IncomeSourceEntity
import mx.budget.data.local.entity.PaymentMethodEntity
import mx.budget.data.local.entity.WalletTransferEntity
import mx.budget.data.local.entity.QuincenaEntity

/**
 * Orquestador del pull offline-first (Firestore → Room).
 *
 * Es el espejo del [SyncManager] (que hace push). Escucha cada subcolección
 * del household vía `addSnapshotListener` y refleja (upsert) los documentos
 * entrantes en Room para que los cambios hechos en OTRO dispositivo aparezcan
 * localmente (sincronización multi-dispositivo).
 *
 * ── ANTI-ECO (crítico) ──────────────────────────────────────────────────
 * Los upserts se hacen SIEMPRE vía los DAO directos, NUNCA vía los repos
 * públicos (`ExpenseRepositoryImpl`, etc.). Esos repos encolan en
 * `sync_queue`, así que escribir a través de ellos dispararía un push que
 * re-subiría a Firestore y crearía un bucle push↔pull infinito. El pull no
 * encola: solo escribe en las tablas locales.
 *
 * ── RESOLUCIÓN DE CONFLICTOS ────────────────────────────────────────────
 * Política Last-Write-Wins simple: el documento remoto SOBRESCRIBE la fila
 * local (`@Insert(onConflict = REPLACE)`).
 * TODO(conflict-resolution): las entidades no tienen `updatedAt`, así que no
 *  hay comparación temporal fina; el "ganador" es simplemente quien aplica
 *  más tarde. Una resolución robusta (updatedAt / vector clock, como pedían
 *  las specs de Wear) queda como trabajo futuro. No se añaden campos a las
 *  entidades para no alterar el esquema/identityHash de Room.
 *
 * ── NOMBRES DE CAMPO ────────────────────────────────────────────────────
 * Cuando la app hace push serializa las `XEntity` (propiedades camelCase) a
 * Firestore, así que el round-trip app→nube→app deserializa bien con
 * `toObject(XEntity::class.java)`.
 * TODO(seed-snake-case): los datos sembrados por el script Python pueden
 *  tener claves snake_case y NO deserializarán correctamente con `toObject`.
 *  No se resuelve esa discrepancia aquí; el foco es el round-trip de la app.
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

        // expenses (+ subcolección de atribuciones por gasto)
        listeners += household().collection("expenses")
            .addSnapshotListener { snapshot, error ->
                handleExpenses(snapshot, error)
            }

        // categories
        listeners += household().collection("categories")
            .addSnapshotListener { snapshot, error ->
                handleSimple(snapshot, error, "categories") { it.toObject(CategoryEntity::class.java) }
                    ?.let { entities -> scope.launch { entities.forEach { categoryDao.upsert(it) } } }
            }

        // members
        listeners += household().collection("members")
            .addSnapshotListener { snapshot, error ->
                handleSimple(snapshot, error, "members") { it.toObject(MemberEntity::class.java) }
                    ?.let { entities -> scope.launch { entities.forEach { memberDao.upsert(it) } } }
            }

        // wallets → tabla local payment_method
        listeners += household().collection("wallets")
            .addSnapshotListener { snapshot, error ->
                handleSimple(snapshot, error, "wallets") { it.toObject(PaymentMethodEntity::class.java) }
                    ?.let { entities -> scope.launch { entities.forEach { paymentMethodDao.upsert(it) } } }
            }

        // quincenas
        listeners += household().collection("quincenas")
            .addSnapshotListener { snapshot, error ->
                handleSimple(snapshot, error, "quincenas") { it.toObject(QuincenaEntity::class.java) }
                    ?.let { entities -> scope.launch { entities.forEach { quincenaDao.upsert(it) } } }
            }

        // wallet_transfer (RF-41). Escribe vía DAO directo (anti-eco): no re-encola.
        listeners += household().collection("wallet_transfer")
            .addSnapshotListener { snapshot, error ->
                handleSimple(snapshot, error, "wallet_transfer") { it.toObject(WalletTransferEntity::class.java) }
                    ?.let { entities -> scope.launch { entities.forEach { walletTransferDao.insert(it) } } }
            }

        // income_source (ingresos). DAO directo (anti-eco).
        listeners += household().collection("income_source")
            .addSnapshotListener { snapshot, error ->
                handleSimple(snapshot, error, "income_source") { it.toObject(IncomeSourceEntity::class.java) }
                    ?.let { entities -> scope.launch { entities.forEach { incomeSourceDao.insert(it) } } }
            }

        Log.i(TAG, "Pull arrancado para household=$householdId (${listeners.size} listeners)")
    }

    /** Remueve todos los listeners. Seguro de llamar varias veces. */
    fun stop() {
        listeners.forEach { it.remove() }
        listeners.clear()
    }

    /**
     * Deserialización defensiva de una subcolección "plana" (sin
     * subcolecciones anidadas). Devuelve la lista o `null` si hubo error /
     * snapshot nulo, para no crashear el listener.
     */
    private fun <T> handleSimple(
        snapshot: QuerySnapshot?,
        error: Exception?,
        label: String,
        mapper: (com.google.firebase.firestore.DocumentSnapshot) -> T?
    ): List<T>? {
        if (error != null) {
            Log.w(TAG, "Listener de '$label' falló; se reintentará al reconectar", error)
            return null
        }
        if (snapshot == null) return null
        return try {
            snapshot.documents.mapNotNull(mapper)
        } catch (e: Exception) {
            // Probablemente documento con claves incompatibles (p.ej. seed snake_case).
            Log.w(TAG, "Fallo al deserializar '$label'; se ignoran documentos no mapeables", e)
            null
        }
    }

    /**
     * Caso especial de expenses: cada gasto lleva su lista de atribuciones en
     * la subcolección `attributions`. Como un snapshot listener sobre la
     * colección padre NO incluye las subcolecciones, leemos las atribuciones
     * con un `get()` adicional por gasto, y aplicamos
     * upsert(expense) + deleteByExpenseId + insertAll(attribs) en una
     * transacción.
     *
     * TODO(remote-attributions): si en el futuro se decide EMBEBER las
     *  atribuciones dentro del documento del gasto (campo `attributions`),
     *  léelas de ahí en vez de hacer el `get()` por subcolección. Por ahora,
     *  si la subcolección no existe o falla, se deja la lista vacía.
     */
    private fun handleExpenses(snapshot: QuerySnapshot?, error: Exception?) {
        if (error != null) {
            Log.w(TAG, "Listener de 'expenses' falló; se reintentará al reconectar", error)
            return
        }
        if (snapshot == null) return

        val docs = try {
            snapshot.documents.mapNotNull { doc ->
                val expense = doc.toObject(ExpenseEntity::class.java) ?: return@mapNotNull null
                doc.reference to expense
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fallo al deserializar 'expenses'; se ignoran no mapeables", e)
            return
        }

        scope.launch {
            for ((ref, expense) in docs) {
                try {
                    val attribs: List<ExpenseAttributionEntity> = try {
                        ref.collection("attributions").get().await()
                            .documents.mapNotNull { it.toObject(ExpenseAttributionEntity::class.java) }
                    } catch (e: Exception) {
                        // Sin subcolección de atribuciones o fallo de red: lista vacía.
                        // TODO(remote-attributions): ver nota arriba.
                        emptyList()
                    }

                    db.withTransaction {
                        // PRESERVAR concept_canonical local (Apéndice F.3): es una
                        // columna calculada SOLO localmente por el pipeline retro. Los
                        // docs remotos (seed o subidos antes de la columna) no la traen
                        // y deserializan a null; un REPLACE ciego la borraría en cada
                        // pull. Si el remoto no la trae, conservamos la que ya hay.
                        val merged = if (expense.conceptCanonical == null) {
                            val local = expenseDao.getById(expense.id)
                            if (local?.conceptCanonical != null)
                                expense.copy(conceptCanonical = local.conceptCanonical) else expense
                        } else expense
                        expenseDao.upsert(merged)

                        // NO borrar atribuciones locales si el remoto no trae ninguna:
                        // una lectura vacía (fallo de red o seed sin subcolección) NO
                        // debe destruir la atribución local (incluida la inferida por el
                        // worker retro). Solo se reemplaza cuando el remoto sí aporta.
                        if (attribs.isNotEmpty()) {
                            attributionDao.deleteByExpenseId(expense.id)
                            attributionDao.insertAll(attribs)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Fallo al aplicar gasto remoto ${expense.id} a Room", e)
                }
            }
        }
    }

    companion object {
        private const val TAG = "RemotePullSync"
    }
}
