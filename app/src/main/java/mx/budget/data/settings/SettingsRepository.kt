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

    /** Flujo del toggle de color dinámico. Default `true` (Material You). */
    val dynamicColor: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[dynamicColorKey] ?: true }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[dynamicColorKey] = enabled }
    }
}
