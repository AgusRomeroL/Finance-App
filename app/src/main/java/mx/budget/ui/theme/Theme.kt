package mx.budget.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── Esquema de color claro ────────────────────────────────────────────────────
// Modo default: light — los prototipos de referencia usan class="light"
private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    inversePrimary = InversePrimary,

    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,

    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,

    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,

    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceTint = SurfaceTint,
    surfaceBright = SurfaceBright,
    surfaceDim = SurfaceDim,
    surfaceContainerLowest = SurfaceContainerLowest,
    surfaceContainerLow = SurfaceContainerLow,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = SurfaceContainerHighest,
    inverseSurface = InverseSurface,
    inverseOnSurface = InverseOnSurface,

    outline = Outline,
    outlineVariant = OutlineVariant
)

// ── Esquema de color oscuro ───────────────────────────────────────────────────
// Invertido tonal: los contenedores se oscurecen, los "on-" se aclaran.
private val DarkColorScheme = darkColorScheme(
    primary = PrimaryFixedDim,
    onPrimary = OnPrimaryFixed,
    primaryContainer = PrimaryDim,
    onPrimaryContainer = PrimaryContainer,
    inversePrimary = Primary,

    secondary = SecondaryFixedDim,
    onSecondary = OnSecondaryFixed,
    secondaryContainer = SecondaryDim,
    onSecondaryContainer = SecondaryContainer,

    tertiary = TertiaryFixedDim,
    onTertiary = OnTertiaryFixed,
    tertiaryContainer = TertiaryDim,
    onTertiaryContainer = TertiaryContainer,

    error = ErrorContainer,
    onError = OnErrorContainer,
    errorContainer = ErrorDim,
    onErrorContainer = OnError,

    background = Color(0xFF14150F),
    onBackground = Color(0xFFE5E2D9),
    surface = Color(0xFF14150F),
    onSurface = Color(0xFFE5E2D9),
    surfaceVariant = Color(0xFF45483B),
    onSurfaceVariant = Color(0xFFC5C8B6),
    surfaceBright = Color(0xFF3A3B32),
    surfaceDim = Color(0xFF14150F),
    surfaceContainerLowest = Color(0xFF0E0F0A),
    surfaceContainerLow = Color(0xFF1C1D17),
    surfaceContainer = Color(0xFF202118),
    surfaceContainerHigh = Color(0xFF2A2B22),
    surfaceContainerHighest = Color(0xFF35362C),
    inverseSurface = Surface,
    inverseOnSurface = OnSurface,

    outline = Color(0xFF8F9184),
    outlineVariant = Color(0xFF45483B)
)

/**
 * Tema raíz de la aplicación de presupuesto familiar.
 *
 * **Color dinámico (Material You), brief §2.1:**
 * - Si [dynamicColor] está activo (default) y el dispositivo lo soporta (API 31+,
 *   garantizado por `minSdk 31`), los roles M3 (`primary/secondary/tertiary`,
 *   superficies, `outline`) se derivan del wallpaper del usuario via
 *   [dynamicLightColorScheme]/[dynamicDarkColorScheme].
 * - Si [dynamicColor] está desactivado (toggle del usuario), cae al esquema
 *   estático sembrado del verde `#016e3e` ([LightColorScheme]/[DarkColorScheme]).
 *
 * **Semánticos financieros:** [FinanceColors] viven FUERA del ColorScheme y se
 * inyectan vía [LocalFinanceColors]. En modo dinámico se armonizan hacia el
 * primary con tope bajo; en modo estático quedan tal cual (verde/rojo de marca).
 *
 * Uso:
 * ```kotlin
 * BudgetAppTheme(dynamicColor = userPrefs.dynamicColor) {
 *     DashboardScreen(...)
 * }
 * ```
 *
 * @param darkTheme    Si `true`, aplica el esquema oscuro. Por defecto sigue al sistema.
 * @param dynamicColor Si `true` (default) usa Material You; `false` fuerza el verde sembrado.
 * @param content      Contenido composable que hereda el tema.
 */
@Composable
fun BudgetAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val supportsDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        supportsDynamic && darkTheme -> dynamicDarkColorScheme(context)
        supportsDynamic -> dynamicLightColorScheme(context)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val baseFinance = if (darkTheme) DarkFinanceColors else LightFinanceColors
    val financeColors = if (supportsDynamic) {
        remember(colorScheme.primary, darkTheme) { baseFinance.harmonizeWith(colorScheme.primary) }
    } else {
        baseFinance
    }

    // Blend edge-to-edge: la ventana ya es transparente (MainActivity llama
    // enableEdgeToEdge); aquí solo sincronizamos la APARIENCIA de los íconos de
    // status/nav bar con el tema Compose. Sin esto los íconos quedan oscuros
    // sobre superficie oscura en dark mode (framework Theme.Material.Light).
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        SideEffect {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalFinanceColors provides financeColors) {
        // Nota de motion (M3 1.4): los componentes M3 ya animan con física de
        // resortes por default. El MotionScheme público (expressive()) sigue
        // experimental en material3 1.5-alpha; cuando gradúe, se pasa aquí.
        // Mientras, el sello expresivo se aplica con springs en los componentes
        // custom (specs dampingRatio≈0.8, stiffness≈380).
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BudgetTypography,
            content = content
        )
    }
}
