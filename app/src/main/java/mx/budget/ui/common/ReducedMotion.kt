package mx.budget.ui.common

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * `true` cuando el usuario ha desactivado las animaciones del sistema
 * (Ajustes → Accesibilidad → Quitar animaciones, o Opciones de desarrollador →
 * escala de animación = 0). Ambas filosofías de motion exigen respetarlo:
 * reduced-motion NO significa cero feedback, significa saltar el MOVIMIENTO
 * (scale/slide/spring) conservando color y semántica.
 *
 * Los helpers de motion custom ([androidx…], `Modifier.pressScale`, el materialize
 * del sheet, el stagger de listas) consultan este flag y, si está activo, saltan
 * al estado final sin animar.
 *
 * Default `false` (animar) — es el estado por defecto del sistema.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/**
 * Lee `Settings.Global.ANIMATOR_DURATION_SCALE` y reacciona en caliente a cambios
 * (el usuario puede togglear animaciones sin reiniciar la app). Provee este valor
 * en [LocalReducedMotion] alrededor del árbol raíz.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    val resolver = context.contentResolver

    var reduced by remember {
        mutableStateOf(animatorDurationScale(resolver) == 0f)
    }

    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = animatorDurationScale(resolver) == 0f
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }

    return reduced
}

private fun animatorDurationScale(resolver: android.content.ContentResolver): Float =
    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
