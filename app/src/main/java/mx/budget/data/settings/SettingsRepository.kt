package mx.budget.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
}
