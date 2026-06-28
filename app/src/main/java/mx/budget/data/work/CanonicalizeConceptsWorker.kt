package mx.budget.data.work

import android.content.Context
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import mx.budget.BudgetApplication
import mx.budget.ai.proactive.ConceptCanonicalizer

/**
 * Primera etapa del pipeline de normalización retroactiva (Apéndice F.3.4):
 * recalcula `expense.concept_canonical` para todo el historial.
 *
 * 1. Carga miembros del hogar → construye el [ConceptCanonicalizer].
 * 2. Calcula la clave de primera pasada de cada gasto.
 * 3. Agrupa claves casi idénticas (typos) en clusters y elige representante.
 * 4. Persiste la clave representante de cada gasto en lotes transaccionales.
 *
 * Idempotente: re-ejecutarlo recalcula desde cero sin efectos colaterales.
 */
class CanonicalizeConceptsWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as BudgetApplication
        val db = app.database
        val expenseDao = db.expenseDao()
        val householdId = app.householdId

        return try {
            val members = db.memberDao().getActiveMembers(householdId)
            val canonicalizer = ConceptCanonicalizer(members)

            val expenses = expenseDao.getAll(householdId)
            if (expenses.isEmpty()) return Result.success()

            // Primera pasada: expenseId → clave; y frecuencia por clave.
            val firstPass = HashMap<String, String>()
            val frequencies = HashMap<String, Int>()
            for (e in expenses) {
                val key = canonicalizer.canonicalize(e.concept) ?: continue
                firstPass[e.id] = key
                frequencies[key] = (frequencies[key] ?: 0) + 1
            }

            // Clustering de claves casi idénticas → representante por cluster.
            val repMap = ConceptCanonicalizer.clusterKeys(frequencies)

            // Persistir en lotes para no mantener una transacción gigante.
            firstPass.entries.chunked(BATCH_SIZE).forEach { batch ->
                db.withTransaction {
                    for ((expenseId, key) in batch) {
                        val canonical = repMap[key] ?: key
                        expenseDao.updateConceptCanonical(expenseId, canonical)
                    }
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val BATCH_SIZE = 100
        const val UNIQUE_NAME = "retro_canonicalize"
    }
}
