package mx.budget.data.work

import android.content.Context
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mx.budget.BudgetApplication
import mx.budget.ai.proactive.ConceptCanonicalizer
import mx.budget.ai.proactive.RetroAttributionEngine
import mx.budget.ai.proactive.SuggestedShare
import mx.budget.data.local.entity.AttributionReviewEntity
import mx.budget.data.local.entity.ExpenseAttributionEntity
import mx.budget.data.local.entity.SyncQueueEntity
import java.util.UUID

/**
 * Segunda etapa del pipeline de normalización retroactiva (Apéndice F.3.4):
 * infiere la atribución BENEFICIARY/PAYER faltante o inconsistente del historial.
 *
 * Para cada gasto cuya atribución de un rol NO es válida (no suma 10,000 bps):
 * - Infiere la distribución con el [RetroAttributionEngine] por su clave canónica.
 * - Confianza ≥ τ → auto-aplica (delete por rol + insert + un sync por gasto) Y
 *   registra una fila `AUTO_APPLIED` en `attribution_review` (provenance: visible
 *   y revertible desde la pantalla de revisión).
 * - Confianza < τ → encola una fila `PENDING` en `attribution_review`.
 *
 * **Decisiones del usuario se respetan siempre:** los (gasto, rol) con review
 * humana resuelta (REJECTED/CONFIRMED/EDITED) se saltan — lo rechazado no resurge.
 *
 * **Re-normalización:** en modo normal preserva los AUTO_APPLIED previos (los
 * gastos ya válidos se saltan). En modo `force` (botón "Re-normalizar historial")
 * revierte primero los AUTO_APPLIED máquina y los re-infiere desde cero, sin tocar
 * las decisiones humanas.
 *
 * Respeta la atribución existente del rol opuesto (borrado scoped por rol). Debe
 * correr DESPUÉS de [CanonicalizeConceptsWorker] (encadenado por WorkContinuation).
 */
class RetroAttributionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as BudgetApplication
        val db = app.database
        val expenseDao = db.expenseDao()
        val attributionDao = db.expenseAttributionDao()
        val reviewDao = db.attributionReviewDao()
        val syncQueueDao = db.syncQueueDao()
        val householdId = app.householdId
        val force = inputData.getBoolean(KEY_FORCE, false)

        return try {
            val members = db.memberDao().getActiveMembers(householdId)
            val canonicalizer = ConceptCanonicalizer(members)
            val engine = RetroAttributionEngine(attributionDao, canonicalizer)

            val now = System.currentTimeMillis()
            val enqueuedSync = HashSet<String>()

            // Solo se reemplazan las sugerencias PENDING; las decisiones humanas
            // (REJECTED/CONFIRMED/EDITED) y los AUTO_APPLIED se conservan.
            reviewDao.deletePending()

            // (gasto, rol) que el usuario ya resolvió → nunca re-inferir.
            val humanResolved = reviewDao.getHumanResolved()
                .map { "${it.expenseId}|${it.role}" }
                .toHashSet()

            // Re-normalización forzada: revierte los auto-aplicados máquina para
            // recomputarlos desde cero (las decisiones humanas siguen intactas).
            if (force) {
                val previousAuto = reviewDao.getByStatus(STATUS_AUTO_APPLIED)
                for (row in previousAuto) {
                    if ("${row.expenseId}|${row.role}" in humanResolved) continue
                    db.withTransaction {
                        attributionDao.deleteByExpenseIdAndRole(row.expenseId, row.role)
                        if (enqueuedSync.add(row.expenseId)) enqueueExpenseSync(syncQueueDao, row.expenseId, now)
                    }
                }
                reviewDao.deleteByStatus(STATUS_AUTO_APPLIED)
            }

            val expenses = expenseDao.getAll(householdId)
            val reviewsToInsert = ArrayList<AttributionReviewEntity>()

            for (role in ROLES) {
                // Recalcular DESPUÉS de la posible reversión, para que los gastos
                // revertidos cuenten como inválidos y se re-infieran.
                val validIds = attributionDao.getValidExpenseIds(role).toSet()

                for (e in expenses) {
                    if (e.id in validIds) continue                          // ya válido: no tocar
                    if ("${e.id}|$role" in humanResolved) continue          // decisión humana: respetar
                    val key = e.conceptCanonical ?: continue                // sin clave: cold start
                    val suggestion = engine.suggestForKey(key, role) ?: continue

                    if (suggestion.confidence >= RetroAttributionEngine.AUTO_APPLY_THRESHOLD) {
                        val rows = suggestion.distribution.map { (memberId, bps) ->
                            ExpenseAttributionEntity(
                                id = UUID.randomUUID().toString(),
                                expenseId = e.id,
                                memberId = memberId,
                                role = role,
                                shareBps = bps,
                                shareAmountMxn = e.amountMxn * bps / 10_000.0
                            )
                        }
                        db.withTransaction {
                            attributionDao.deleteByExpenseIdAndRole(e.id, role)
                            attributionDao.insertAll(rows)
                            if (enqueuedSync.add(e.id)) enqueueExpenseSync(syncQueueDao, e.id, now)
                        }
                        // Provenance: registra el auto-aplicado (visible y revertible).
                        reviewsToInsert += review(e.id, role, suggestion, key, STATUS_AUTO_APPLIED, now)
                    } else {
                        reviewsToInsert += review(e.id, role, suggestion, key, STATUS_PENDING, now)
                    }
                }
            }

            if (reviewsToInsert.isNotEmpty()) {
                reviewsToInsert.chunked(BATCH_SIZE).forEach { reviewDao.insertAll(it) }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun review(
        expenseId: String,
        role: String,
        suggestion: mx.budget.ai.proactive.AttributionSuggestion,
        key: String,
        status: String,
        now: Long
    ): AttributionReviewEntity {
        val json = Json.encodeToString(
            suggestion.distribution.map { SuggestedShare(it.key, it.value) }
        )
        return AttributionReviewEntity(
            id = UUID.randomUUID().toString(),
            expenseId = expenseId,
            role = role,
            suggestedJson = json,
            confidence = suggestion.confidence,
            sampleSize = suggestion.sampleSize,
            conceptCanonical = key,
            status = status,
            createdAt = now
        )
    }

    private suspend fun enqueueExpenseSync(
        syncQueueDao: mx.budget.data.local.dao.SyncQueueDao,
        expenseId: String,
        now: Long
    ) {
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = "EXPENSE",
                entityId = expenseId,
                operation = "UPSERT",
                createdAt = now
            )
        )
    }

    companion object {
        private const val BATCH_SIZE = 100
        private val ROLES = listOf("BENEFICIARY", "PAYER")
        const val UNIQUE_NAME = "retro_attribution"

        /** Flag de inputData: si true, revierte y recomputa los AUTO_APPLIED previos. */
        const val KEY_FORCE = "force"

        private const val STATUS_PENDING = "PENDING"
        private const val STATUS_AUTO_APPLIED = "AUTO_APPLIED"
    }
}
