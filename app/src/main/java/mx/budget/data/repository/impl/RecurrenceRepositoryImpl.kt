package mx.budget.data.repository.impl

import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.dao.RecurrenceTemplateDao
import mx.budget.data.local.entity.RecurrenceTemplateEntity
import mx.budget.data.repository.RecurrenceRepository

/**
 * Implementación Room del repositorio de plantillas recurrentes (Apéndice G.2,
 * Fase 0). Es la fuente de verdad local (offline-first); el lado nube (Firestore)
 * llegará después (§G.2.5) — hoy se delega directo al DAO, igual que los demás
 * repos públicos cableados a Room en [mx.budget.BudgetApplication].
 */
class RecurrenceRepositoryImpl(
    private val dao: RecurrenceTemplateDao
) : RecurrenceRepository {

    override fun observeActive(householdId: String): Flow<List<RecurrenceTemplateEntity>> =
        dao.observeActive(householdId)

    override fun observeAll(householdId: String): Flow<List<RecurrenceTemplateEntity>> =
        dao.observeAll(householdId)

    override suspend fun getById(id: String): RecurrenceTemplateEntity? =
        dao.getById(id)

    override suspend fun getTemplatesForCadence(
        householdId: String,
        cadence: String,
    ): List<RecurrenceTemplateEntity> =
        dao.getActiveForCadence(householdId, cadence)

    override suspend fun insert(template: RecurrenceTemplateEntity) =
        dao.insert(template)

    override suspend fun update(template: RecurrenceTemplateEntity) =
        dao.update(template)

    override suspend fun delete(template: RecurrenceTemplateEntity) =
        dao.delete(template)

    override suspend fun pause(templateId: String) =
        dao.setActive(templateId, false)

    override suspend fun resume(templateId: String) =
        dao.setActive(templateId, true)
}
