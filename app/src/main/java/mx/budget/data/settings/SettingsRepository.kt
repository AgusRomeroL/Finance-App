package mx.budget.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// DataStore a nivel de Context (singleton por proceso).
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "budget_settings")

/**
 * Preferencias de usuario persistidas (DataStore).
 *
 * Hoy aloja el toggle de **color dinámico (Material You)** del brief §2.1:
 * `true` (default) usa el wallpaper; `false` fuerza el verde de marca sembrado.
 */
class SettingsRepository(private val context: Context) {

    private val dynamicColorKey = booleanPreferencesKey("dynamic_color")
    private val retroLabelingDoneKey = booleanPreferencesKey("retro_labeling_done")
    private val bankCaptureEnabledKey = booleanPreferencesKey("bank_capture_enabled")
    private val reminderLeadDaysKey = intPreferencesKey("reminder_lead_days")
    private val reminderStateKey = stringPreferencesKey("reminder_state_json")
    private val dismissedTemplateSuggestionsKey = stringSetPreferencesKey("dismissed_template_suggestions")

    /** Flujo del toggle de color dinámico. Default `true` (Material You). */
    val dynamicColor: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[dynamicColorKey] ?: true }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[dynamicColorKey] = enabled }
    }

    /**
     * `true` cuando el pipeline de normalización/atribución retroactiva (Apéndice F.3)
     * ya se encoló al menos una vez. WorkManager persiste el trabajo encolado, así que
     * marcar el flag justo tras encolar es seguro aunque el proceso muera.
     */
    val retroLabelingDone: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[retroLabelingDoneKey] ?: false }

    suspend fun setRetroLabelingDone(done: Boolean) {
        context.dataStore.edit { prefs -> prefs[retroLabelingDoneKey] = done }
    }

    /**
     * Opt-in de la **captura desde notificaciones bancarias** (Feature D, §F.6).
     * Default `false`: aunque el SO conceda acceso a notificaciones, el listener no
     * procesa nada hasta que el usuario activa esto explícitamente en Perfil.
     */
    val bankCaptureEnabled: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[bankCaptureEnabledKey] ?: false }

    suspend fun setBankCaptureEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[bankCaptureEnabledKey] = enabled }
    }

    // ── Recordatorios de gastos PLANNED (Apéndice G.2, Fase 3) ──────────────────

    /**
     * Días de antelación con que el [mx.budget.data.reminder.ReminderWorker] avisa
     * de un gasto PLANNED por default. Override por plantilla en `cadence_detail`
     * (`reminder_lead_days` o `"QUINCENA_START"`). Default global = 2 días.
     */
    val reminderLeadDays: Flow<Int> = context.dataStore.data
        .map { prefs -> prefs[reminderLeadDaysKey] ?: DEFAULT_REMINDER_LEAD_DAYS }

    suspend fun setReminderLeadDays(days: Int) {
        context.dataStore.edit { prefs -> prefs[reminderLeadDaysKey] = days }
    }

    /**
     * Estado de recordatorios: `expenseId → epoch millis del próximo momento en que
     * vuelve a ser elegible para notificar`. Sirve para dos cosas:
     * - **Notificar una sola vez:** al avisar se fija [NEVER_AGAIN] (no re-nag).
     * - **Posponer:** se fija `ahora + snooze` y el recordatorio vuelve a disparar
     *   cuando ese instante pasa.
     * Un id ausente del mapa es elegible en cuanto su fecha lo amerite.
     */
    suspend fun getReminderState(): Map<String, Long> =
        decodeState(context.dataStore.data.first()[reminderStateKey])

    /** Reemplaza el mapa completo (el worker lo poda a los PLANNED vigentes). */
    suspend fun setReminderState(state: Map<String, Long>) {
        context.dataStore.edit { prefs -> prefs[reminderStateKey] = encodeState(state) }
    }

    /** Pospone un recordatorio: vuelve a ser elegible en [untilMillis]. */
    suspend fun snoozeReminder(expenseId: String, untilMillis: Long) {
        context.dataStore.edit { prefs ->
            val current = decodeState(prefs[reminderStateKey]).toMutableMap()
            current[expenseId] = untilMillis
            prefs[reminderStateKey] = encodeState(current)
        }
    }

    /**
     * Claves canónicas de plantillas **sugeridas que el usuario descartó** (Fase 5).
     * Persistente: una sugerencia rechazada no vuelve a aparecer. Distinto del
     * descarte en sesión de las sugerencias reactivas del dashboard (Feature C).
     */
    val dismissedTemplateSuggestions: Flow<Set<String>> = context.dataStore.data
        .map { prefs -> prefs[dismissedTemplateSuggestionsKey] ?: emptySet() }

    suspend fun dismissTemplateSuggestion(canonicalKey: String) {
        context.dataStore.edit { prefs ->
            prefs[dismissedTemplateSuggestionsKey] = (prefs[dismissedTemplateSuggestionsKey] ?: emptySet()) + canonicalKey
        }
    }

    private fun decodeState(raw: String?): Map<String, Long> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching { Json.decodeFromString<Map<String, Long>>(raw) }.getOrDefault(emptyMap())
    }

    private fun encodeState(state: Map<String, Long>): String =
        Json.encodeToString(state)

    companion object {
        const val DEFAULT_REMINDER_LEAD_DAYS = 2

        /** Sentinela "ya notificado, no volver a avisar" (hasta posponer o confirmar). */
        const val NEVER_AGAIN = Long.MAX_VALUE
    }
}
