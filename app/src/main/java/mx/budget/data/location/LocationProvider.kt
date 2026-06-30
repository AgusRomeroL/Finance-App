package mx.budget.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import mx.budget.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Un fix de ubicación resuelto y listo para adjuntar a un gasto (Apéndice G.4).
 * [placeLabel] es best-effort: null si el reverse-geocode falló (solo coords).
 */
data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val placeLabel: String?,
)

/**
 * Niveles de captura de ubicación (§G.4.2), derivados del flag de intención de
 * Perfil cruzado con el permiso del SO.
 */
enum class LocationLevel { NONE, WHILE_IN_USE, PERSISTENT }

/**
 * Proveedor de ubicación on-device para la señal de contexto del gasto (G.4).
 *
 * Cruza **intención** (nivel elegido en Perfil, persistido en [SettingsRepository])
 * con **permiso** concedido por el SO antes de pedir nada. Devuelve `null` —y el
 * llamador marca `location_source=NONE`— en cuanto falte cualquiera de los dos, o si
 * el fix/geocode falla. Nunca lanza: la ubicación es accesoria, jamás bloquea la
 * captura.
 *
 * Bajo *solo-al-usar*, `getCurrentLocation` solo entrega fix en foreground; en
 * background (ingest del listener bancario) devuelve `null` y la ubicación llega al
 * confirmar (foreground). Bajo *persistente* puede obtener fix también en background.
 */
class LocationProvider(
    private val context: Context,
    private val settings: SettingsRepository,
) {

    private val fused by lazy { LocationServices.getFusedLocationProviderClient(context) }

    /** `true` si el SO concedió FINE o COARSE (while-in-use mínimo). */
    fun hasForegroundPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** `true` si además se concedió ACCESS_BACKGROUND_LOCATION (Android 10+). */
    fun hasBackgroundPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Nivel efectivo: intención (Perfil) ∧ permiso concedido. */
    suspend fun effectiveLevel(): LocationLevel {
        val intent = settings.locationCaptureLevel.first()
        return when (intent) {
            SettingsRepository.LOCATION_LEVEL_PERSISTENT ->
                if (hasForegroundPermission() && hasBackgroundPermission()) LocationLevel.PERSISTENT
                else if (hasForegroundPermission()) LocationLevel.WHILE_IN_USE
                else LocationLevel.NONE
            SettingsRepository.LOCATION_LEVEL_WHILE_IN_USE ->
                if (hasForegroundPermission()) LocationLevel.WHILE_IN_USE else LocationLevel.NONE
            else -> LocationLevel.NONE
        }
    }

    /**
     * Obtiene un fix fresco si el nivel lo permite, reverse-geocodeado on-device.
     *
     * @param requireForeground si `true` (default), el llamador está en foreground
     *   real (sheet/confirm in-app); cualquier nivel ≥ WHILE_IN_USE sirve. Si `false`
     *   (ingest de background), exige nivel PERSISTENT.
     * @return el fix, o `null` si no hay permiso/nivel, o si el fix/geocode falló.
     */
    suspend fun currentFix(requireForeground: Boolean = true): LocationFix? {
        val level = effectiveLevel()
        val allowed = when {
            level == LocationLevel.PERSISTENT -> true
            level == LocationLevel.WHILE_IN_USE -> requireForeground
            else -> false
        }
        if (!allowed) return null

        val location = fetchLocation() ?: return null
        val label = reverseGeocode(location.latitude, location.longitude)
        return LocationFix(location.latitude, location.longitude, label)
    }

    /** Fix actual con timeout corto; cae a la última ubicación conocida. Best-effort. */
    @Suppress("MissingPermission") // El permiso ya se verificó en effectiveLevel().
    private suspend fun fetchLocation(): Location? = runCatching {
        withTimeoutOrNull(GPS_TIMEOUT_MS) {
            fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
        } ?: fused.lastLocation.await()
    }.getOrNull()

    /**
     * Reverse-geocode on-device. API 33+ usa la variante async con listener
     * (la síncrona quedó deprecada); <33 usa la síncrona en un dispatcher IO.
     * Devuelve null si el dispositivo no trae geocoder o el lookup falla.
     */
    private suspend fun reverseGeocode(lat: Double, lon: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale("es", "MX"))
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                withTimeoutOrNull(GEOCODE_TIMEOUT_MS) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(lat, lon, 1) { addresses ->
                            cont.resume(addresses.firstOrNull()?.let(::formatAddress))
                        }
                    }
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()?.let(::formatAddress)
                }
            }
        }.getOrNull()
    }

    /** Etiqueta compacta: línea de dirección, o "colonia, ciudad" como fallback. */
    private fun formatAddress(a: android.location.Address): String? =
        a.getAddressLine(0)
            ?: listOfNotNull(a.subLocality ?: a.thoroughfare, a.locality)
                .filter { it.isNotBlank() }
                .joinToString(", ")
                .ifBlank { null }

    companion object {
        private const val GPS_TIMEOUT_MS = 8_000L
        private const val GEOCODE_TIMEOUT_MS = 4_000L
    }
}
