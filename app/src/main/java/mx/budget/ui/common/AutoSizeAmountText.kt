package mx.budget.ui.common

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

/**
 * Cifra de monto con auto-escala.
 *
 * Si el texto no cabe a lo ancho (fontScale 1.3 mas bold en el panel angosto del
 * Fold), baja el tamano en pasos de 4sp hasta [minFontSp]. El contenido no se
 * dibuja hasta converger, para que no parpadee el reintento de medida.
 *
 * **Nunca elipsis ni marquee: una cifra financiera cortada es un dato falso.** Un
 * saldo que se lee "$1,23..." no informa, desinforma. Por eso los montos usan
 * esto en vez de `maxLines = 1` con recorte, que es lo que hacian el importe del
 * libro mayor, los tiles del dashboard y las tarjetas KPI.
 *
 * Estaba escrito y probado, pero privado del dashboard y con un solo uso.
 */
@Composable
fun AutoSizeAmountText(
    text: String,
    baseStyle: TextStyle,
    maxFontSp: Float,
    minFontSp: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    var fontSize by remember(text.length) { mutableFloatStateOf(maxFontSp) }
    var ready by remember(text.length) { mutableStateOf(false) }
    Text(
        text,
        style = baseStyle.copy(fontSize = fontSize.sp),
        color = color,
        maxLines = 1,
        softWrap = false,
        modifier = modifier.drawWithContent { if (ready) drawContent() },
        onTextLayout = { result ->
            if (result.didOverflowWidth && fontSize > minFontSp) {
                fontSize = (fontSize - 4f).coerceAtLeast(minFontSp)
            } else {
                ready = true
            }
        },
    )
}
