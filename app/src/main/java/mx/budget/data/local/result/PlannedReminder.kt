package mx.budget.data.local.result

/**
 * Proyección de un gasto `PLANNED` con el contexto que necesita el
 * [mx.budget.data.reminder.ReminderWorker] (Apéndice G.2, Fase 3) para decidir
 * si toca recordar y con qué texto.
 *
 * - [occurredAt]: fecha prevista del gasto (epoch millis). El recordatorio se
 *   dispara `occurredAt − lead`.
 * - [quincenaStartDate]: inicio de la quincena (ISO `YYYY-MM-DD`), usado cuando
 *   el lead de la plantilla es `"QUINCENA_START"` (recordar al abrir la quincena).
 * - [cadenceDetail]: JSON de la plantilla recurrente que originó el PLANNED, o
 *   `null` si es un PLANNED manual sin plantilla. De aquí se lee el override
 *   `reminder_lead_days` (entero) o `"QUINCENA_START"`.
 */
data class PlannedReminder(
    val expenseId: String,
    val concept: String,
    val amountMxn: Double,
    val occurredAt: Long,
    val quincenaStartDate: String,
    val cadenceDetail: String?,
)
