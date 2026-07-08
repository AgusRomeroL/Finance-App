package mx.budget.ui.dashboard

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Anillo de progreso circular (estilo el anillo de cardio de Fitbit/Health) — el
 * KPI héroe del dashboard. Es una primitiva **pura y reutilizable**: recibe una
 * [fraction] 0..1 y dibuja un arco animado sobre una pista; el [content] se centra
 * dentro (típicamente un porcentaje o un monto).
 *
 * Movimiento expresivo (obligatorio, CLAUDE.md): el barrido no salta — anima con un
 * resorte espacial ([spring]) al montar y en cada cambio de [fraction].
 *
 * El significado no depende solo del color: el arco es una señal de magnitud por sí
 * mismo, y quien lo consume añade texto (%, etiqueta) en [content].
 */
@Composable
fun BudgetRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    ringSize: Dp = 150.dp,
    strokeWidth: Dp = 14.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    progressColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "budgetRingSweep",
    )
    Box(modifier = modifier.size(ringSize), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            // Pista completa (fondo tonal).
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            // Progreso: arranca a las 12 en punto (-90°) y barre en sentido horario.
            if (animated > 0f) {
                drawArc(
                    color = progressColor,
                    startAngle = -90f,
                    sweepAngle = animated * 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}
