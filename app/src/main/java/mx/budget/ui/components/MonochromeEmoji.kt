package mx.budget.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Renderiza un emoji en **blanco y negro** (escala de grises). Los emojis del
 * sistema se pintan a color; aquí se dibujan dentro de un layer con un
 * `ColorFilter` de saturación 0 para forzar el monocromo del sistema "Architectural
 * Ledger". Si la fuente de emoji del dispositivo ignorara el filtro, el glifo se
 * vería a color (degradación aceptable).
 */
@Composable
fun MonochromeEmoji(
    emoji: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 16.sp
) {
    Box(modifier = modifier.grayscale()) {
        Text(text = emoji, fontSize = fontSize, color = LocalContentColor.current)
    }
}

/** Dibuja el contenido en un layer con saturación 0 (escala de grises). */
private fun Modifier.grayscale(): Modifier = this.drawWithCache {
    val paint = Paint().apply {
        colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    }
    onDrawWithContent {
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(Offset.Zero, size), paint)
            drawContent()
            canvas.restore()
        }
    }
}
