package mx.budget.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import mx.budget.R

// ── Familias tipográficas — Google Sans Flex ──────────────────────────────────
// Google liberó **Google Sans Flex** bajo licencia SIL Open Font (OFL) el
// 2025-11-18; por eso se puede empacar legalmente en la app. Google Fonts NO
// provee un único archivo variable para esta familia, así que se empacan
// instancias estáticas en DOS tamaños ópticos (los .ttf viven en res/font/):
//   · Text  (opsz 9pt)  → roles de UI chicos: title / body / label.
//   · Display (opsz 24pt) → roles grandes: display / headline (montos héroe).
// El tamaño óptico hace que el trazo se ajuste al tamaño de render (la gracia de
// la familia). Cada FontFamily mapea FontWeight → el corte correspondiente.

val GoogleSansFlexText = FontFamily(
    Font(R.font.google_sans_flex_text_light, FontWeight.Light),
    Font(R.font.google_sans_flex_text_regular, FontWeight.Normal),
    Font(R.font.google_sans_flex_text_medium, FontWeight.Medium),
    Font(R.font.google_sans_flex_text_semibold, FontWeight.SemiBold),
    Font(R.font.google_sans_flex_text_bold, FontWeight.Bold),
)

val GoogleSansFlexDisplay = FontFamily(
    Font(R.font.google_sans_flex_display_light, FontWeight.Light),
    Font(R.font.google_sans_flex_display_regular, FontWeight.Normal),
    Font(R.font.google_sans_flex_display_medium, FontWeight.Medium),
    Font(R.font.google_sans_flex_display_semibold, FontWeight.SemiBold),
    Font(R.font.google_sans_flex_display_bold, FontWeight.Bold),
)

// Display para montos/encabezados grandes; Text para el resto de la UI.
private val DisplayFamily = GoogleSansFlexDisplay
private val TextFamily = GoogleSansFlexText

// ── Escala tipográfica Material 3 Expressive ──────────────────────────────────
val BudgetTypography = Typography(

    // Display: monto total en CaptureBottomSheet ($0.00 gigante)
    displayLarge = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Light,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Light,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Light,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),

    // Headline: montos de las SummaryCards ($105,000) — óptico display.
    headlineLarge = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Light,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Light,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),

    // Title: encabezados de sección
    titleLarge = TextStyle(
        fontFamily = TextFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = TextFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = TextFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),

    // Body: texto de transacciones, conceptos
    bodyLarge = TextStyle(
        fontFamily = TextFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = TextFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = TextFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),

    // Label: badges de categoría, etiquetas uppercase
    labelLarge = TextStyle(
        fontFamily = TextFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = TextFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = TextFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.8.sp
    )
)
