package mx.budget.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

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

    background = InverseSurface,
    onBackground = InverseOnSurface,
    surface = InverseSurface,
    onSurface = InverseOnSurface,
    surfaceVariant = SurfaceContainerHighest,
    onSurfaceVariant = OnSurfaceVariant,
    inverseSurface = Surface,
    inverseOnSurface = OnSurface,

    outline = Outline,
    outlineVariant = OutlineVariant
)

/**
 * Tema raíz de la aplicación de presupuesto familiar.
 *
 * Aplica el sistema de diseño Material 3 Expressive con los tokens
 * de color y tipografía derivados del CSS de los prototipos visuales.
 *
 * Uso:
 * ```kotlin
 * BudgetAppTheme {
 *     DashboardScreen(...)
 * }
 * ```
 *
 * @param darkTheme Si `true`, aplica el esquema oscuro. Por defecto sigue al sistema.
 * @param content Contenido composable que hereda el tema.
 */
@Composable
fun BudgetAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BudgetTypography,
        content = content
    )
}
