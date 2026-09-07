package mx.budget.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

// ─────────────────────────────────────────────────────────────────────────────
// Redundancia no-cromática (brief §2.1 + C9/C11) — OBLIGATORIA
// ─────────────────────────────────────────────────────────────────────────────
//
// ~8% de los hombres tiene deficiencia rojo-verde. El significado financiero
// NUNCA puede depender solo del color. Este helper empaqueta las señales
// redundantes para cada tono: color + signo (+/−) + ícono (flecha ↑/↓) +
// etiqueta textual de accesibilidad. Las pantallas deben usar TODAS, no solo el color.

enum class FinancialTone { INCOME, EXPENSE, WARNING, SCHEDULED, NEUTRAL }

/**
 * Bundle de señales redundantes para mostrar una cifra financiera.
 *
 * @property color       Color del texto/acento (semántico, posiblemente armonizado).
 * @property container   Fondo tonal del chip/badge.
 * @property onContainer Texto sobre [container].
 * @property sign        Prefijo de signo: "+", "−" o "" (señal independiente del color).
 * @property icon        Ícono direccional (flecha ↑/↓, alerta, reloj), o null para neutral.
 * @property description Etiqueta para `contentDescription` (lectores de pantalla).
 */
@Immutable
data class AmountSemantic(
    val color: Color,
    val container: Color,
    val onContainer: Color,
    val sign: String,
    val icon: ImageVector?,
    val description: String
)

/** Resuelve las señales redundantes para [tone] desde la paleta del tema actual. */
@Composable
fun amountSemantic(tone: FinancialTone): AmountSemantic {
    val fc = MaterialTheme.financeColors
    val cs = MaterialTheme.colorScheme
    return when (tone) {
        FinancialTone.INCOME -> AmountSemantic(
            color = fc.income,
            container = fc.incomeContainer,
            onContainer = fc.onIncomeContainer,
            sign = "+",
            icon = Icons.Filled.ArrowUpward,
            description = "Ingreso"
        )
        FinancialTone.EXPENSE -> AmountSemantic(
            color = fc.expense,
            container = fc.expenseContainer,
            onContainer = fc.onExpenseContainer,
            sign = "−", // signo menos tipográfico
            icon = Icons.Filled.ArrowDownward,
            description = "Gasto"
        )
        FinancialTone.WARNING -> AmountSemantic(
            color = fc.warning,
            container = fc.warningContainer,
            onContainer = fc.onWarningContainer,
            sign = "",
            icon = Icons.Filled.WarningAmber,
            description = "Alerta"
        )
        // Cargo planeado que aún no se ejecuta: se lee como gasto (signo y flecha),
        // pero en tono apagado para que nunca se confunda con un ingreso ni con
        // dinero ya gastado. La etiqueta "Programado" es la señal no cromática.
        FinancialTone.SCHEDULED -> AmountSemantic(
            color = cs.onSurfaceVariant,
            container = cs.surfaceContainerHighest,
            onContainer = cs.onSurfaceVariant,
            sign = "−", // mismo signo menos tipográfico que EXPENSE
            icon = Icons.Filled.Schedule,
            description = "Programado"
        )
        FinancialTone.NEUTRAL -> AmountSemantic(
            color = cs.onSurface,
            container = cs.surfaceContainerHighest,
            onContainer = cs.onSurface,
            sign = "",
            icon = null,
            description = ""
        )
    }
}
