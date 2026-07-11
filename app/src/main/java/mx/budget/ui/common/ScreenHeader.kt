package mx.budget.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Encabezado de pantalla unificado (patrón "The Architectural Ledger"):
 * eyebrow pequeño en MAYÚSCULAS con tracking abierto + título grande ligero
 * debajo. Es el patrón que estrenó Cuentas ("CUENTAS" / "Saldos") y ahora
 * comparten Analíticas y Libro Mayor.
 *
 * @param eyebrow etiqueta de contexto (se muestra en mayúsculas); null la omite.
 * @param title   título principal de la pantalla.
 */
@Composable
fun ScreenHeader(
    eyebrow: String?,
    title: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (eyebrow != null) {
            Text(
                eyebrow.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.6.sp,
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.Light,
                fontSize = 28.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}
