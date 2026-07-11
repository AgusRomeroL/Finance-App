package mx.budget.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Escala de formas centralizada.
 *
 * Antes los radios se hardcodeaban inline (6/10/14/16/18/20/22/28dp) sin sistema.
 * [BudgetShapes] mapea esa escala real a los cinco roles de M3 y se cablea en
 * [BudgetAppTheme] vía `MaterialExpressiveTheme(shapes = …)`, de modo que los
 * componentes M3 (Card, Button, Chip…) heredan la jerarquía correcta por default.
 *
 * Para elementos custom cuyo radio no cae en un rol M3, usa [AppShapes] (formas
 * nombradas por intención) en vez de un literal suelto.
 */
val BudgetShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Formas nombradas por intención para componentes custom fuera de los roles M3. */
object AppShapes {
    /** Tarjeta estándar (KPI, widget, wallet, transacción). */
    val card = RoundedCornerShape(20.dp)

    /** Tecla del keypad de captura. */
    val keypadKey = RoundedCornerShape(18.dp)

    /** Campo de entrada (monto, concepto, búsqueda). */
    val field = RoundedCornerShape(22.dp)

    /** Contenedor héroe / diálogos grandes / píldoras grandes. */
    val hero = RoundedCornerShape(28.dp)

    /** Píldora totalmente redondeada (chips, segmentos, FAB extendido). */
    val pill = RoundedCornerShape(percent = 50)
}
