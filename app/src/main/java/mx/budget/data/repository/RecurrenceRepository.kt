package mx.budget.data.repository

import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.entity.RecurrenceTemplateEntity

/**
 * Contrato del repositorio de plantillas recurrentes.
 */
interface RecurrenceRepository {

    /** Plantillas activas del household. */
    fun observeActive(householdId: String): Flow<List<RecurrenceTemplateEntity>>

    suspend fun getById(id: String): RecurrenceTemplateEntity?

    /** Plantillas que deben materializarse para una cadencia específica. */
    suspend fun getTemplatesForCadence(householdId: String, cadence: String): List<RecurrenceTemplateEntity>

    suspend fun insert(template: RecurrenceTemplateEntity)

    suspend fun update(template: RecurrenceTemplateEntity)

    suspend fun delete(template: RecurrenceTemplateEntity)

    /** Pausa una plantilla sin eliminarla. */
    suspend fun pause(templateId: String)

    /** Reanuda una plantilla pausada. */
    suspend fun resume(templateId: String)
}
