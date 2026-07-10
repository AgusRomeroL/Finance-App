package mx.budget.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mx.budget.data.local.dao.CategoryDao
import mx.budget.data.local.dao.ExpenseAttributionDao
import mx.budget.data.local.dao.ExpenseDao
import mx.budget.data.local.dao.MemberDao
import mx.budget.data.local.dao.IncomeSourceDao
import mx.budget.data.local.dao.InstallmentPlanDao
import mx.budget.data.local.dao.LoanDao
import mx.budget.data.local.dao.PaymentMethodDao
import mx.budget.data.local.dao.SavingsGoalDao
import mx.budget.data.local.dao.SyncQueueDao
import mx.budget.data.local.dao.WalletTransferDao
import mx.budget.data.remote.LoanRepositoryFirestore
import mx.budget.data.repository.CategoryRepository
import mx.budget.data.repository.ExpenseRepository
import mx.budget.data.repository.MemberRepository
import mx.budget.data.repository.IncomeRepository
import mx.budget.data.repository.InstallmentRepository
import mx.budget.data.repository.SavingsRepository
import mx.budget.data.repository.TransferRepository
import mx.budget.data.repository.WalletRepository

/**
 * Orquestador del push offline-first (Room → Firestore).
 *
 * Drena el outbox `sync_queue` cada vez que aparece una red disponible y
 * una vez al construirse (por si ya hay conexión). Room es la fuente de
 * verdad; este manager solo empuja cambios locales hacia la nube de forma
 * eventual e idempotente.
 *
 * Diseño bidireccional del sync (este manager es SOLO la mitad push):
 * - **Push (aquí):** cada escritura por repo Room encola una fila en
 *   `sync_queue`; `drain()` la empuja al repo Firestore correspondiente.
 * - **Pull ([RemotePullSync]):** snapshot listeners sobre las subcolecciones
 *   de Firestore reflejan los cambios remotos en Room. **Anti-eco:** el pull
 *   escribe vía DAO directo (`upsert`), NUNCA por los repos — pasar por los
 *   repos volvería a encolar en el outbox y crearía un bucle push↔pull.
 * - **Conflictos:** last-write-wins por `updated_at` — el pull solo aplica un
 *   doc remoto si su timestamp supera al local (seeds/legados con 0 nunca
 *   pisan ediciones locales).
 *
 * @param remoteExpenseRepository implementación Firestore del
 *  [ExpenseRepository] (el "lado nube"), NO la implementación Room.
 */
class SyncManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val syncQueueDao: SyncQueueDao,
    private val expenseDao: ExpenseDao,
    private val attributionDao: ExpenseAttributionDao,
    private val remoteExpenseRepository: ExpenseRepository,
    private val paymentMethodDao: PaymentMethodDao,
    private val remoteWalletRepository: WalletRepository,
    private val transferDao: WalletTransferDao,
    private val remoteTransferRepository: TransferRepository,
    private val incomeSourceDao: IncomeSourceDao,
    private val remoteIncomeRepository: IncomeRepository,
    // MVP Fase 3.5 — hoja de balance (opcionales para compatibilidad).
    private val savingsGoalDao: SavingsGoalDao? = null,
    private val remoteSavingsRepository: SavingsRepository? = null,
    private val loanDao: LoanDao? = null,
    /** Concreto (no interfaz): expone `deleteById` para drenar `LOAN|DELETE`. */
    private val remoteLoanRepository: LoanRepositoryFirestore? = null,
    private val installmentPlanDao: InstallmentPlanDao? = null,
    private val remoteInstallmentRepository: InstallmentRepository? = null,
    // v13 — categorías con escritura local (alta inline, color).
    private val categoryDao: CategoryDao? = null,
    private val remoteCategoryRepository: CategoryRepository? = null,
    // v14 — miembros con escritura local (wizard de onboarding, CRUD de maestros).
    private val memberDao: MemberDao? = null,
    private val remoteMemberRepository: MemberRepository? = null,
) {

    private val mutex = Mutex()

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            scope.launch { drain() }
        }
    }

    init {
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        // Intento inmediato por si ya hay conexión al arrancar.
        scope.launch { drain() }
        // Drenado PROACTIVO al encolar: observa el outbox y, cuando aparecen filas
        // y hay red, empuja sin esperar a un cambio de conectividad (antes el push
        // se retrasaba si el usuario ya estaba en línea). `debounce` agrupa las
        // varias altas de una misma transacción (gasto + atribuciones + wallet);
        // el `drain()` es idempotente y está serializado por el mutex.
        scope.launch { observeAndDrain() }
    }

    @OptIn(FlowPreview::class)
    private suspend fun observeAndDrain() {
        syncQueueDao.observeCount(MAX_ATTEMPTS)
            .debounce(500)
            .collect { pending -> if (pending > 0 && isOnline()) drain() }
    }

    /** ¿Hay una red con Internet validado ahora mismo? */
    private fun isOnline(): Boolean {
        val net = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Procesa el outbox en orden FIFO. Usa un mutex para no solaparse con
     * otra invocación (p.ej. arranque + onAvailable simultáneos).
     *
     * Manejo de errores en dos clases:
     * - **Error de conectividad** ([isConnectivityError]): corta el drenado
     *   (`break`) SIN incrementar `attempts` — sin red no tiene caso seguir y
     *   no es culpa de la fila; se reintenta completo al volver la conexión.
     * - **Error por-fila** (p.ej. doc rechazado por las reglas de Firestore):
     *   incrementa `attempts`, loguea en W con entityType/entityId y CONTINÚA
     *   con la siguiente fila — un mensaje venenoso ya no congela la cola.
     *
     * **Dead-letter:** tras [MAX_ATTEMPTS] fallos por-fila, la fila queda como
     * fallida definitiva: `getPending(MAX_ATTEMPTS)` deja de devolverla, así
     * que no vuelve a reintentarse ni a bloquear el push. El dato local NO se
     * borra (sigue en su tabla de origen); la fila permanece en `sync_queue`
     * como evidencia diagnosticable.
     *
     * Nota consciente: al continuar tras un fallo por-fila se pierde el orden
     * FIFO estricto entre filas de la misma entidad; es aceptable porque los
     * UPSERT releen el estado local vigente al momento del push.
     */
    suspend fun drain() {
        mutex.withLock {
            val pending = syncQueueDao.getPending(MAX_ATTEMPTS)
            for (row in pending) {
                try {
                    when {
                        row.entityType == "EXPENSE" && row.operation == "UPSERT" -> {
                            val expense = expenseDao.getById(row.entityId)
                            if (expense == null) {
                                // El gasto ya no existe localmente; nada que empujar.
                                syncQueueDao.delete(row.id)
                            } else {
                                val attribs = attributionDao.getByExpenseId(row.entityId)
                                remoteExpenseRepository.insertWithAttributions(expense, attribs)
                                syncQueueDao.delete(row.id)
                            }
                        }

                        row.entityType == "EXPENSE" && row.operation == "DELETE" -> {
                            // TODO(remote-delete): se requiere un borrado remoto fiable.
                            //  `ExpenseRepositoryFirestore.deleteAndRevertBalance` existe pero
                            //  depende de un collectionGroup query por id; hasta validarlo lo
                            //  intentamos best-effort y, si falla, se reintenta. Decisión:
                            //  intentamos el delete remoto y solo quitamos la fila en éxito.
                            remoteExpenseRepository.deleteAndRevertBalance(row.entityId)
                            syncQueueDao.delete(row.id)
                        }

                        row.entityType == "WALLET" && row.operation == "UPSERT" -> {
                            val wallet = paymentMethodDao.getById(row.entityId)
                            if (wallet == null) {
                                syncQueueDao.delete(row.id)
                            } else {
                                remoteWalletRepository.insert(wallet)
                                syncQueueDao.delete(row.id)
                            }
                        }

                        row.entityType == "TRANSFER" && row.operation == "UPSERT" -> {
                            val transfer = transferDao.getById(row.entityId)
                            if (transfer == null) {
                                syncQueueDao.delete(row.id)
                            } else {
                                remoteTransferRepository.recordTransfer(transfer)
                                syncQueueDao.delete(row.id)
                            }
                        }

                        row.entityType == "TRANSFER" && row.operation == "DELETE" -> {
                            remoteTransferRepository.deleteTransfer(row.entityId)
                            syncQueueDao.delete(row.id)
                        }

                        row.entityType == "INCOME" && row.operation == "UPSERT" -> {
                            val income = incomeSourceDao.getById(row.entityId)
                            if (income == null) {
                                syncQueueDao.delete(row.id)
                            } else {
                                remoteIncomeRepository.insert(income)
                                syncQueueDao.delete(row.id)
                            }
                        }

                        // ── Hoja de balance (MVP Fase 3.5) ─────────────────────

                        row.entityType == "SAVINGS" && row.operation == "UPSERT" -> {
                            val goal = savingsGoalDao?.getById(row.entityId)
                            if (goal == null || remoteSavingsRepository == null) {
                                syncQueueDao.delete(row.id)
                            } else {
                                remoteSavingsRepository.insert(goal)
                                syncQueueDao.delete(row.id)
                            }
                        }

                        row.entityType == "LOAN" && row.operation == "UPSERT" -> {
                            val loan = loanDao?.getById(row.entityId)
                            if (loan == null || remoteLoanRepository == null) {
                                syncQueueDao.delete(row.id)
                            } else {
                                remoteLoanRepository.insert(loan)
                                syncQueueDao.delete(row.id)
                            }
                        }

                        row.entityType == "LOAN" && row.operation == "DELETE" -> {
                            remoteLoanRepository?.deleteById(row.entityId)
                            syncQueueDao.delete(row.id)
                        }

                        row.entityType == "CATEGORY" && row.operation == "UPSERT" -> {
                            val category = categoryDao?.getById(row.entityId)
                            if (category == null || remoteCategoryRepository == null) {
                                syncQueueDao.delete(row.id)
                            } else {
                                remoteCategoryRepository.insert(category)
                                syncQueueDao.delete(row.id)
                            }
                        }

                        row.entityType == "MEMBER" && row.operation == "UPSERT" -> {
                            val member = memberDao?.getById(row.entityId)
                            if (member == null || remoteMemberRepository == null) {
                                syncQueueDao.delete(row.id)
                            } else {
                                remoteMemberRepository.insert(member)
                                syncQueueDao.delete(row.id)
                            }
                        }

                        row.entityType == "INSTALLMENT" && row.operation == "UPSERT" -> {
                            val plan = installmentPlanDao?.getById(row.entityId)
                            if (plan == null || remoteInstallmentRepository == null) {
                                syncQueueDao.delete(row.id)
                            } else {
                                remoteInstallmentRepository.insert(plan)
                                syncQueueDao.delete(row.id)
                            }
                        }

                        else -> {
                            // Tipo/operación desconocidos: descartar para no bloquear la cola.
                            Log.w(TAG, "Fila de sync desconocida: ${row.entityType}/${row.operation}")
                            syncQueueDao.delete(row.id)
                        }
                    }
                } catch (e: Exception) {
                    if (isConnectivityError(e)) {
                        // Sin red no tiene caso seguir: se corta el drenado sin
                        // castigar a la fila (attempts intacto) y se reintenta
                        // todo en el siguiente disparo (onAvailable/observe).
                        Log.w(TAG, "Error de conectividad al drenar el outbox; se corta y reintenta al volver la red", e)
                        break
                    }
                    val attemptsNow = row.attempts + 1
                    syncQueueDao.incrementAttempts(row.id)
                    if (attemptsNow >= MAX_ATTEMPTS) {
                        Log.w(
                            TAG,
                            "Fila ${row.id} (${row.entityType}/${row.entityId}) marcada como fallida " +
                                "definitiva tras $attemptsNow intentos; deja de bloquear la cola " +
                                "(el dato local NO se borra)",
                            e
                        )
                    } else {
                        Log.w(
                            TAG,
                            "Fallo por-fila al sincronizar ${row.entityType}/${row.entityId} " +
                                "(intento $attemptsNow/$MAX_ATTEMPTS); se continúa con la siguiente",
                            e
                        )
                    }
                    // Continúa con la siguiente fila: un error por-fila no congela la cola.
                }
            }
        }
    }

    /**
     * ¿El fallo huele a conectividad (transitorio, no culpa de la fila)?
     * Recorre la cadena de causas buscando [IOException] (DNS, socket, timeout
     * de red) o un [FirebaseFirestoreException] con código UNAVAILABLE /
     * DEADLINE_EXCEEDED (backend inalcanzable). Como red de seguridad, si la
     * red se cayó a mitad del drenado (`!isOnline()`), cualquier error se
     * trata como de conectividad para no dead-letterear filas sanas.
     */
    private fun isConnectivityError(e: Throwable): Boolean {
        var cause: Throwable? = e
        while (cause != null) {
            if (cause is IOException) return true
            if (cause is FirebaseFirestoreException &&
                (cause.code == FirebaseFirestoreException.Code.UNAVAILABLE ||
                    cause.code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED)
            ) {
                return true
            }
            cause = cause.cause
        }
        return !isOnline()
    }

    companion object {
        private const val TAG = "SyncManager"

        /**
         * Umbral dead-letter: nº de fallos por-fila tras el cual una fila del
         * outbox se considera fallida definitiva y `getPending` la excluye.
         */
        private const val MAX_ATTEMPTS = 8
    }
}
