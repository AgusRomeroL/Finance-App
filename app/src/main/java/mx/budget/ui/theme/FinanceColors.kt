package mx.budget.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.android.material.color.MaterialColors

// ─────────────────────────────────────────────────────────────────────────────
// FinanceColors — colores semánticos financieros FUERA del ColorScheme M3
// ─────────────────────────────────────────────────────────────────────────────
//
// Brief §2.1: ingreso/gasto/alerta NO son roles M3 (primary/secondary/tertiary).
// Si vivieran dentro del ColorScheme, el color dinámico (Material You) los
// reemplazaría con el wallpaper del usuario y se perdería el significado.
// Por eso van aquí, como tokens custom expuestos vía CompositionLocal, y solo
// se "armonizan" hacia el primary con un tope de rotación bajo (≤15° en HCT)
// para que rojo siga siendo rojo y verde siga siendo verde.
//
// IMPORTANTE: el color es solo UNA de las señales. El significado financiero
// debe reforzarse SIEMPRE con redundancia no-cromática (signo +/−, ícono de
// flecha, posición, etiqueta) — ver helpers en AmountSemantics.

/**
 * Paleta semántica financiera estable, independiente del ColorScheme M3.
 *
 * @property income            Color de ingreso/positivo (verde contable).
 * @property expense           Color de gasto/negativo (rojo).
 * @property warning           Color de alerta (ámbar) — sobregiro, falta asignar.
 * Cada rol trae su `on*` de alto contraste y su `*Container` tonal.
 */
@Immutable
data class FinanceColors(
    val income: Color,
    val onIncome: Color,
    val incomeContainer: Color,
    val onIncomeContainer: Color,
    val expense: Color,
    val onExpense: Color,
    val expenseContainer: Color,
    val onExpenseContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color
)

// ── Paleta clara (semilla del brief: income #0F5A2E, expense #BA1A1A, warning #8B5A00) ──
val LightFinanceColors = FinanceColors(
    income = Color(0xFF0F5A2E),
    onIncome = Color(0xFFFFFFFF),
    incomeContainer = Color(0xFFB7F4C5),
    onIncomeContainer = Color(0xFF00210F),
    expense = Color(0xFFBA1A1A),
    onExpense = Color(0xFFFFFFFF),
    expenseContainer = Color(0xFFFFDAD6),
    onExpenseContainer = Color(0xFF410002),
    warning = Color(0xFF8B5A00),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFFFDDB3),
    onWarningContainer = Color(0xFF2C1600)
)

// ── Paleta oscura (tonos invertidos: rol más claro, container más oscuro) ──
val DarkFinanceColors = FinanceColors(
    income = Color(0xFF6FD896),
    onIncome = Color(0xFF00391C),
    incomeContainer = Color(0xFF00522A),
    onIncomeContainer = Color(0xFFB7F4C5),
    expense = Color(0xFFFFB4AB),
    onExpense = Color(0xFF690005),
    expenseContainer = Color(0xFF93000A),
    onExpenseContainer = Color(0xFFFFDAD6),
    warning = Color(0xFFFFB95C),
    onWarning = Color(0xFF492A00),
    warningContainer = Color(0xFF6A4100),
    onWarningContainer = Color(0xFFFFDDB3)
)

/**
 * Armoniza los roles cromáticos hacia [primary] usando HCT/Blend (cap ≤15°).
 *
 * Solo se rotan los colores con croma (income/expense/warning + containers);
 * los `on*` (casi blanco/negro) se dejan intactos para preservar el contraste.
 * Con el tope de 15° de [MaterialColors.harmonize], el par crítico rojo↔verde
 * nunca se confunde aunque el wallpaper imponga un hue lejano (brief §2.1).
 */
fun FinanceColors.harmonizeWith(primary: Color): FinanceColors {
    val p = primary.toArgb()
    fun Color.h(): Color = Color(MaterialColors.harmonize(this.toArgb(), p))
    return copy(
        income = income.h(),
        incomeContainer = incomeContainer.h(),
        expense = expense.h(),
        expenseContainer = expenseContainer.h(),
        warning = warning.h(),
        warningContainer = warningContainer.h()
    )
}

/** CompositionLocal que transporta la paleta financiera al árbol Compose. */
val LocalFinanceColors = staticCompositionLocalOf { LightFinanceColors }

/**
 * Accesor estilo `MaterialTheme.colorScheme`:
 * ```
 * val finance = MaterialTheme.financeColors
 * Text(amount, color = finance.expense)
 * ```
 */
val MaterialTheme.financeColors: FinanceColors
    @Composable
    @ReadOnlyComposable
    get() = LocalFinanceColors.current
