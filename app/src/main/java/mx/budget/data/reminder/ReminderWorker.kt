package mx.budget.data.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import mx.budget.BudgetApplication
import mx.budget.data.local.result.PlannedReminder
import mx.budget.data.settings.SettingsRepository
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Recordatorios de gastos `PLANNED` (Apéndice G.2, Fase 3).
 *
 * Corre periódicamente (piso 15 min de WorkManager; **NO** `SCHEDULE_EXACT_ALARM`,
 * penalizado §F.9). En cada corrida:
 *  1. Lee los PLANNED del hogar con su contexto ([ExpenseDao.getPlannedForReminder]).
 *  2. Para cada uno resuelve el *lead* (override por plantilla en `cadence_detail`:
 *     `reminder_lead_days` entero o `"QUINCENA_START"`; si no, el default global de
 *     [SettingsRepository.reminderLeadDays]) y calcula su instante de disparo.
 *  3. Notifica los vencidos que aún son elegibles (no notificados / no pospuestos),
 *     fijándolos como [SettingsRepository.NEVER_AGAIN] para no repetir el aviso.
 *
 * El estado se persiste en DataStore y se **poda** a los PLANNED vigentes en cada
 * corrida: confirmar (→POSTED) o borrar un gasto lo saca del mapa automáticamente.
 * Idempotente: re-ejecutarlo no re-notifica lo ya avisado.
 */
class ReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private sealed interface Lead {
        data class Days(val days: Int) : Lead
        data object QuincenaStart : Lead
    }

    override suspend fun doWork(): Result {
        val app = applicationContext as BudgetApplication
        val settings = app.settingsRepository

        return try {
            val planned = app.database.expenseDao().getPlannedForReminder(app.householdId)
            val globalLead = settings.reminderLeadDays.first()
            val previousState = settings.getReminderState()
            val now = System.currentTimeMillis()

            // Poda: conserva solo el estado de los PLANNED que siguen vigentes.
            val plannedIds = planned.mapTo(HashSet()) { it.expenseId }
            val newState = previousState.filterKeys { it in plannedIds }.toMutableMap()

            for (reminder in planned) {
                val nextEligible = newState[reminder.expenseId]
                if (nextEligible != null && now < nextEligible) continue   // notificado o pospuesto
                if (triggerAt(reminder, globalLead) > now) continue        // aún no toca

                ReminderNotifier.notify(applicationContext, reminder)
                newState[reminder.expenseId] = SettingsRepository.NEVER_AGAIN
            }

            settings.setReminderState(newState)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    /** Instante (epoch millis) en que debe dispararse el recordatorio de [reminder]. */
    private fun triggerAt(reminder: PlannedReminder, globalLead: Int): Long =
        when (val lead = parseLead(reminder.cadenceDetail, globalLead)) {
            is Lead.Days -> reminder.occurredAt - TimeUnit.DAYS.toMillis(lead.days.toLong())
            Lead.QuincenaStart -> startOfQuincenaMillis(reminder.quincenaStartDate)
                ?: (reminder.occurredAt - TimeUnit.DAYS.toMillis(globalLead.toLong()))
        }

    /**
     * Lee el override de lead del `cadence_detail` de la plantilla. Acepta
     * `"reminder_lead_days": 3` (entero) o `"reminder_lead_days": "QUINCENA_START"`.
     * Cualquier ausencia/forma inválida cae al [globalLead].
     */
    private fun parseLead(cadenceDetail: String?, globalLead: Int): Lead {
        if (cadenceDetail.isNullOrBlank()) return Lead.Days(globalLead)
        val field = runCatching {
            Json.parseToJsonElement(cadenceDetail).jsonObject["reminder_lead_days"]
        }.getOrNull() ?: return Lead.Days(globalLead)

        (field as? JsonPrimitive)?.let { prim ->
            prim.intOrNull?.let { return Lead.Days(it) }
            if (prim.contentOrNull == "QUINCENA_START") return Lead.QuincenaStart
        }
        return Lead.Days(globalLead)
    }

    /** ISO `YYYY-MM-DD` → epoch millis del inicio de ese día en zona local. */
    private fun startOfQuincenaMillis(isoDate: String): Long? = runCatching {
        LocalDate.parse(isoDate).atStartOfDay(ZONE).toInstant().toEpochMilli()
    }.getOrNull()

    companion object {
        const val UNIQUE_NAME = "planned_reminders"
        private val ZONE: ZoneId = ZoneId.of("America/Mexico_City")
    }
}
