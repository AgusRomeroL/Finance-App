package mx.budget.ui.calendar

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mx.budget.data.local.dao.ExpenseDao
import mx.budget.data.local.result.ExpenseWithDetails
import mx.budget.data.reminder.ReminderActions
import mx.budget.data.reminder.ReminderNotifier
import mx.budget.data.repository.ExpenseRepository
import mx.budget.data.settings.SettingsRepository

/**
 * ViewModel del Calendario (Apéndice G.2, Fase 4).
 *
 * Observa los gastos `PLANNED` del hogar (timeline por fecha) y expone las tres
 * acciones del flujo de confirmación, reusando lo construido en Fases 2/3:
 *  - [confirm] / [confirmWithAmount] → `ExpenseRepository.confirmPlanned`
 *    (PLANNED→POSTED, re-escala atribuciones si el monto real difiere).
 *  - [postpone] → `SettingsRepository.snoozeReminder` (sigue PLANNED, re-agenda
 *    el recordatorio para más tarde), espejo de la acción "Posponer" de la
 *    notificación.
 *
 * Cualquier acción cancela además la notificación de recordatorio pendiente del
 * gasto (no-op si no había ninguna), para que UI y notificación no se desincronicen.
 */
class CalendarViewModel(
    private val appContext: Context,
    private val expenseDao: ExpenseDao,
    private val expenseRepository: ExpenseRepository,
    private val settingsRepository: SettingsRepository,
    private val householdId: String,
) : ViewModel() {

    /** Gastos planeados (no ejecutados) ordenados por fecha ascendente. */
    val planned: StateFlow<List<ExpenseWithDetails>> =
        expenseDao.observePlannedWithDetails(householdId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Confirma con el monto previsto. */
    fun confirm(expenseId: String) = confirmWithAmount(expenseId, null)

    /** Confirma ajustando el monto real (recurrentes que varían: luz/agua). */
    fun confirmWithAmount(expenseId: String, actualAmountMxn: Double?) {
        viewModelScope.launch {
            expenseRepository.confirmPlanned(expenseId, actualAmountMxn)
            ReminderNotifier.cancel(appContext, expenseId)
        }
    }

    /** Pospone el recordatorio (el gasto sigue PLANNED). */
    fun postpone(expenseId: String) {
        viewModelScope.launch {
            settingsRepository.snoozeReminder(
                expenseId,
                System.currentTimeMillis() + ReminderActions.SNOOZE_MILLIS,
            )
            ReminderNotifier.cancel(appContext, expenseId)
        }
    }
}
