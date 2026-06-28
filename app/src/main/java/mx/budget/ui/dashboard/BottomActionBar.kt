package mx.budget.ui.dashboard

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import mx.budget.ui.search.SpeechRecognizerController

/**
 * Barra de acción inferior estilo Pixel Screenshots: pill de búsqueda (con
 * micrófono) + botón "+" de captura. FIJA abajo.
 *
 * - [readOnly] = true (dashboard): el pill es un botón que navega a la pantalla de
 *   búsqueda ([onActivate]); el micrófono también navega y arranca el dictado allá.
 * - [readOnly] = false (pantalla de búsqueda): input real enlazado a [query]/[onQueryChange]
 *   con dictado por voz in situ.
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
    val context = LocalContext.current
    val voice = remember { SpeechRecognizerController(context) }
    DisposableEffect(Unit) { onDispose { voice.destroy() } }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) voice.start(onPartial = onQueryChange, onFinal = onQueryChange, onEnd = {})
    }

    val onMic: () -> Unit = {
        if (readOnly) onActivate()
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
        if (voice.isAvailable) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onMic),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Mic, "Dictar búsqueda",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
