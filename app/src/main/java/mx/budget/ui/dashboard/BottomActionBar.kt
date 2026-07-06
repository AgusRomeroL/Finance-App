package mx.budget.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.Intent
import mx.budget.ui.capture.VoiceCaptureActivity

/**
 * Barra de acción inferior estilo Pixel Screenshots: pill de búsqueda (solo
 * texto) + botón de micrófono de captura por voz + botón "+" de captura. FIJA abajo.
 *
 * - [readOnly] = true (dashboard): el pill es un botón que navega a la pantalla de
 *   búsqueda ([onActivate]).
 * - [readOnly] = false (pantalla de búsqueda): input real enlazado a [query]/[onQueryChange].
 *
 * La búsqueda es SOLO texto: el micrófono de captura por voz (a la derecha) es el
 * único punto de dictado, para no confundir "dictar búsqueda" con "capturar gasto".
 */
@Composable
fun BottomActionBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    onActivate: () -> Unit = {}
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SearchPill(
            query = query,
            onQueryChange = onQueryChange,
            readOnly = readOnly,
            onActivate = onActivate,
            modifier = Modifier.weight(1f)
        )
        // Captura por voz en lenguaje natural (§G.3): abre el overlay de dictado,
        // que arma la propuesta y la deja en la bandeja (propose-then-confirm).
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable {
                    context.startActivity(
                        Intent(context, VoiceCaptureActivity::class.java)
                            .putExtra(VoiceCaptureActivity.EXTRA_SOURCE, "VOICE")
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Mic, "Capturar por voz",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
        // Botón "+" de captura con gradiente primary (look del FAB previo).
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onPlus),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Add, "Capturar gasto", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun SearchPill(
    query: String,
    onQueryChange: (String) -> Unit,
    readOnly: Boolean,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(if (readOnly) Modifier.clickable(onClick = onActivate) else Modifier)
            .padding(start = 18.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Search, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    "Buscar movimientos",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis
                )
            }
            if (!readOnly) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (query.isNotEmpty()) {
                Text(
                    query,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }
    }
}
