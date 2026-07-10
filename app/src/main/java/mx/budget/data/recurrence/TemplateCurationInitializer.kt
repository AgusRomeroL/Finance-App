package mx.budget.data.recurrence

import mx.budget.data.local.dao.ExpenseDao
import mx.budget.data.local.dao.RecurrenceTemplateDao
import mx.budget.data.local.entity.ExpenseEntity
import mx.budget.data.repository.ExpenseRepository
import mx.budget.data.settings.SettingsRepository
import java.time.Instant
import java.time.ZoneId

/**
 * Curación one-shot de plantillas recurrentes (jul-2026). Corre UNA vez por
 * instalación (flag DataStore `template_curation_2026_07_done`), ANTES de la
 * materialización del arranque. Dos trabajos:
 *
 * 1. **Pausar las plantillas de consumo variable** sembradas por el ETL — conceptos
 *    "Walmart", "Comida Gatas", "Benji" y "Normita, David y Agus". Son gasto
 *    variable (super, comida, mesada), no obligaciones de fecha fija: no deben
 *    materializar PLANNED. Se marca `is_active = 0` (el usuario puede reactivarlas
 *    desde la pantalla de Plantillas) y se borran sus gastos aún PLANNED vía el
 *    repo PÚBLICO (encola el DELETE y tombstonea remoto). La golden DB no se toca:
 *    la corrección es en runtime; el ETL ya no las siembra en futuros reseeds.
 *
 * 2. **Deduplicar los PLANNED de plantilla**: la materialización histórica usaba
 *    `UUID.randomUUID()`, así que cada dispositivo del hogar creó SU copia del
 *    mismo PLANNED y el sync las multiplicó (×N). Se agrupan los PLANNED con
 *    `recurrence_template_id` por (plantilla, día de `occurred_at`); de cada grupo
 *    sobrevive la fila con `created_at` más antiguo y el resto se elimina vía el
 *    repo público — también cubre copias locales que nunca se pushearon (outbox
 *    atorado sin red): su DELETE encolado se vuelve un no-op remoto inofensivo.
 *    NO se tocan los PLANNED de MSI (`installment_plan_id`, ya deduplicados por
 *    plan+cuota) ni los manuales sin plantilla.
 *
 * A futuro, [RecurrenceMaterializer] ya genera ids deterministas, por lo que este
 * escenario no se reproduce.
 *
 * El PASO 2 corre en CADA arranque (no solo la primera vez): es idempotente y
 * barato, y cubre a los rezagados que llegan tarde por el pull — p.ej. el
 * dedupe local corrió antes de que el pull entregara la copia que la limpieza
 * remota conservó, dejando un par vivo hasta el siguiente arranque.
 */
class TemplateCurationInitializer(
    private val settings: SettingsRepository,
    private val householdId: String,
    private val recurrenceDao: RecurrenceTemplateDao,
    private val expenseDao: ExpenseDao,
    private val expenseRepository: ExpenseRepository,
    private val zone: ZoneId = ZoneId.of("America/Mexico_City"),
) {

    suspend fun curateOnce() {
        val firstRun = !settings.isTemplateCuration202607Done()

        // (1) Pausar plantillas variables (match por concept exacto, case-sensitive).
        // Solo la primera vez: si el usuario reactiva una plantilla desde la
        // pantalla de Plantillas, no se la volvemos a pausar en cada arranque.
        val variableTemplates =
            recurrenceDao.getByConcepts(householdId, VARIABLE_CONCEPTS.toList())
        val variableIds = variableTemplates.map { it.id }.toHashSet()
        if (firstRun) {
            for (template in variableTemplates) {
                runCatching { recurrenceDao.setActive(template.id, false) }
            }
        }

        val planned = expenseDao.getPlannedFromTemplates(householdId)

        // Borrar los PLANNED de esas plantillas (repo público → tombstone remoto).
        // deleteAndRevertBalance no toca saldo en PLANNED (solo revierte POSTED).
        if (firstRun) {
            for (expense in planned) {
                if (expense.recurrenceTemplateId in variableIds) {
                    runCatching { expenseRepository.deleteAndRevertBalance(expense.id) }
                }
            }
        }

        // (2) Dedupe por (plantilla, día) — CADA arranque (idempotente; ver KDoc).
        // MSI y curados arriba quedan fuera.
        val candidates = planned.filter {
            it.recurrenceTemplateId !in variableIds && it.installmentPlanId == null
        }
        val groups = candidates.groupBy { expense ->
            val day = Instant.ofEpochMilli(expense.occurredAt).atZone(zone).toLocalDate()
            "${expense.recurrenceTemplateId}:$day"
        }
        for (group in groups.values) {
            if (group.size <= 1) continue
            // Sobrevive la más antigua; empate por id para que la elección sea
            // determinista también entre dispositivos.
            val keeper = group.minWithOrNull(
                compareBy(ExpenseEntity::createdAt, ExpenseEntity::id)
            ) ?: continue
            for (duplicate in group) {
                if (duplicate.id == keeper.id) continue
                runCatching { expenseRepository.deleteAndRevertBalance(duplicate.id) }
            }
        }

        if (firstRun) settings.setTemplateCuration202607Done(true)
    }

    companion object {
        /**
         * Conceptos EXACTOS (case-sensitive, tal cual siembra el ETL) de las
         * plantillas de consumo variable a pausar. Criterio: solo las obligaciones
         * de fecha fija se materializan como PLANNED.
         */
        val VARIABLE_CONCEPTS: Set<String> = setOf(
            "Walmart",
            "Comida Gatas",
            "Benji",
            "Normita, David y Agus",
        )
    }
}
