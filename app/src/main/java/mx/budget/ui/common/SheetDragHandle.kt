package mx.budget.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import mx.budget.R

/**
 * Asa de las hojas modales con objetivo táctil de 48 por 48 dp.
 *
 * `ModalBottomSheet` envuelve lo que se le pase como asa en un nodo que expande
 * y contrae la hoja al tocarlo, y ese nodo mide lo que mida el asa: la de
 * fábrica es de 32 por 4 dp con 22 dp de margen vertical, y la propia de la
 * captura era de 40 por 4 con 12. Accessibility Scanner las marcó a las dos
 * (Pixel 7, 2026-09-17). Aquí la barra visible sigue midiendo 40 por 4, pero
 * vive dentro de una caja de 48 por 24 con 12 dp arriba y abajo: 48 en total.
 */
@Composable
fun SheetDragHandle(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.cd_sheet_drag_handle)
    Box(
        modifier = modifier
            .padding(vertical = 12.dp)
            .size(width = 48.dp, height = 24.dp)
            // El nodo que envuelve al asa no trae nombre propio: sin este, el
            // lector de pantalla anunciaba un control sin etiqueta.
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}
