package mx.budget.ui.theme

import androidx.compose.ui.graphics.Color

// ── Paleta estática "The Architectural Ledger" (fallback de marca) ────────────
// Fuente: ui_reference/claude_design/Presupuesto Hogar.dc.html (diseño aprobado).
// Tonos cálidos "papel fino" derivados de la semilla verde #016E3E.
//
// Esta es la paleta de RESPALDO: aplica cuando el color dinámico (Material You)
// está desactivado o no disponible. Con dinámico ON, los roles M3 vienen del
// wallpaper del usuario (ver Theme.kt). Los semánticos financieros NO viven aquí
// — están en FinanceColors.kt, fuera del ColorScheme.

// ── Primary (Verde contable) ─────────────────────────────────────────────────
val Primary = Color(0xFF006C44)
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFF92F7B4)
val OnPrimaryContainer = Color(0xFF002111)
val PrimaryDim = Color(0xFF005233)
val PrimaryFixed = Color(0xFF92F7B4)
val PrimaryFixedDim = Color(0xFF77DA9A)
val OnPrimaryFixed = Color(0xFF002111)
val OnPrimaryFixedVariant = Color(0xFF005233)
val InversePrimary = Color(0xFF77DA9A)

// ── Secondary (Verde apagado) ────────────────────────────────────────────────
val Secondary = Color(0xFF3F6A4D)
val OnSecondary = Color(0xFFFFFFFF)
val SecondaryContainer = Color(0xFFC2EBCF)
val OnSecondaryContainer = Color(0xFF00210F)
val SecondaryDim = Color(0xFF2C5239)
val SecondaryFixed = Color(0xFFC2EBCF)
val SecondaryFixedDim = Color(0xFFA6CFB3)
val OnSecondaryFixed = Color(0xFF00210F)
val OnSecondaryFixedVariant = Color(0xFF275139)

// ── Tertiary (Ámbar editorial) ───────────────────────────────────────────────
val Tertiary = Color(0xFF7A5900)
val OnTertiary = Color(0xFFFFFFFF)
val TertiaryContainer = Color(0xFFFFDEA6)
val OnTertiaryContainer = Color(0xFF271900)
val TertiaryDim = Color(0xFF614600)
val TertiaryFixed = Color(0xFFFFDEA6)
val TertiaryFixedDim = Color(0xFFEFC885)
val OnTertiaryFixed = Color(0xFF271900)
val OnTertiaryFixedVariant = Color(0xFF5C4200)

// ── Error (alineado con el semántico de gasto #BA1A1A) ────────────────────────
val Error = Color(0xFFBA1A1A)
val OnError = Color(0xFFFFFFFF)
val ErrorContainer = Color(0xFFFFDAD6)
val OnErrorContainer = Color(0xFF410002)
val ErrorDim = Color(0xFF93000A)

// ── Surface / Background (ramp cálido "papel fino") ───────────────────────────
val Background = Color(0xFFFAF8F3)
val OnBackground = Color(0xFF1B1C18)
val Surface = Color(0xFFFAF8F3)
val SurfaceBright = Color(0xFFFAF8F3)
val SurfaceDim = Color(0xFFDED8CB)
val OnSurface = Color(0xFF1B1C18)
val SurfaceVariant = Color(0xFFE6DFCC)
val OnSurfaceVariant = Color(0xFF44483D)
val InverseSurface = Color(0xFF303129)
val InverseOnSurface = Color(0xFFF2F0E9)
val SurfaceTint = Color(0xFF006C44)

// ── Surface containers (jerarquía tonal por capas, NO por líneas) ─────────────
val SurfaceContainerLowest = Color(0xFFFFFFFF)
val SurfaceContainerLow = Color(0xFFF4EFE4)
val SurfaceContainer = Color(0xFFF1ECE0)
val SurfaceContainerHigh = Color(0xFFEAE4D4)
val SurfaceContainerHighest = Color(0xFFE6DFCC)

// ── Outline ───────────────────────────────────────────────────────────────────
val Outline = Color(0xFF79786A)
val OutlineVariant = Color(0xFFCBC6B4)
