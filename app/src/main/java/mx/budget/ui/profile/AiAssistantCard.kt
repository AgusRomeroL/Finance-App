package mx.budget.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.budget.ai.service.LlmEngine
import mx.budget.ai.service.LlmModelVariant
import mx.budget.ai.service.ModelCatalog
import mx.budget.ai.service.ModelState
import mx.budget.ui.common.LocalReducedMotion
import mx.budget.ui.theme.BudgetMotion
import mx.budget.ui.theme.financeColors

/**
 * Todo lo que Perfil necesita para gobernar el asistente local. Se agrupa en un
 * objeto para no seguir alargando la lista de parámetros de [ProfileScreen], que ya
 * pasa de veinte.
 */
data class AiAssistantSettings(
    val modelState: ModelState,
    val selectedVariant: LlmModelVariant,
    val engine: LlmEngine,
    val onDownload: (LlmModelVariant) -> Unit,
    val onCancel: () -> Unit,
    val onDelete: (LlmModelVariant) -> Unit,
)

/**
 * Tarjeta "ASISTENTE IA": qué motor responde hoy, qué variante del modelo se usa,
 * y el control de la descarga.
 *
 * El texto es deliberadamente literal sobre lo que cuesta: son gigabytes, pide Wi-Fi
 * y cargador, y sin modelo el asistente sigue respondiendo, solo que con cifras
 * calculadas en vez de redacción.
 */
@Composable
fun AiAssistantCard(settings: AiAssistantSettings) {
    var showVariantDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val state = settings.modelState

    if (showVariantDialog) {
        ModelVariantDialog(
            current = settings.selectedVariant,
            onSelect = { variant ->
                showVariantDialog = false
                settings.onDownload(variant)
            },
            onDismiss = { showVariantDialog = false },
        )
    }
    if (showDeleteDialog) {
        val installed = (state as? ModelState.Installed)?.variant ?: settings.selectedVariant
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Borrar el modelo") },
            text = {
                Text(
                    "Se borra ${installed.displayName} de este teléfono y se libera su " +
                        "espacio. El asistente seguirá respondiendo con tus cifras, pero sin " +
                        "redacción de IA hasta que lo vuelvas a descargar."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    settings.onDelete(installed)
                }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Conservar") }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(22.dp)
    ) {
        Text(
            "ASISTENTE IA",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.6.sp,
        )
        Spacer(Modifier.height(14.dp))

        SettingRow(
            icon = Icons.Filled.AutoAwesome,
            title = "Motor actual",
            subtitle = engineLabel(settings.engine, state),
            trailingBadge = null,
            onClick = {},
        )
        Spacer(Modifier.height(8.dp))

        SettingRow(
            icon = Icons.Filled.Memory,
            title = "Modelo",
            subtitle = "${settings.selectedVariant.displayName}. ${settings.selectedVariant.description}",
            trailingBadge = null,
            onClick = { showVariantDialog = true },
        )

        when (state) {
            is ModelState.Installed -> {
                Spacer(Modifier.height(8.dp))
                SettingRow(
                    icon = Icons.Filled.DeleteOutline,
                    title = "Borrar el modelo",
                    subtitle = "${state.variant.displayName} está en este dispositivo",
                    trailingBadge = null,
                    onClick = { showDeleteDialog = true },
                )
            }

            is ModelState.Downloading -> {
                Spacer(Modifier.height(14.dp))
                DownloadProgress(
                    label = "Descargando ${state.variant.displayName}",
                    detail = bytesLabel(state.written, state.total),
                    fraction = if (state.total > 0L) state.written.toFloat() / state.total else null,
                    onCancel = settings.onCancel,
                )
            }

            is ModelState.Verifying -> {
                Spacer(Modifier.height(14.dp))
                DownloadProgress(
                    label = "Verificando ${state.variant.displayName}",
                    detail = "Comprobando que el archivo llegó completo",
                    fraction = null,
                    onCancel = settings.onCancel,
                )
            }

            is ModelState.Failed -> {
                Spacer(Modifier.height(12.dp))
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.financeColors.warning,
                )
                Spacer(Modifier.height(8.dp))
                SettingRow(
                    icon = Icons.Filled.CloudDownload,
                    title = "Reintentar la descarga",
                    subtitle = "Con Wi-Fi y el teléfono cargando",
                    trailingBadge = null,
                    onClick = { settings.onDownload(settings.selectedVariant) },
                )
            }

            is ModelState.NotInstalled -> {
                Spacer(Modifier.height(8.dp))
                SettingRow(
                    icon = Icons.Filled.CloudDownload,
                    title = "Descargar el modelo",
                    subtitle = if (state.partialBytes > 0L) {
                        "Se reanuda desde ${megabytes(state.partialBytes)} MB. Pide Wi-Fi y cargador"
                    } else {
                        "Pide Wi-Fi y el teléfono cargando"
                    },
                    trailingBadge = null,
                    onClick = { settings.onDownload(settings.selectedVariant) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "El modelo se guarda en este teléfono y responde sin conexión. Si no está, " +
                "el asistente contesta igual con tus cifras, solo que sin redactar.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DownloadProgress(
    label: String,
    detail: String,
    fraction: Float?,
    onCancel: () -> Unit,
) {
    val reduced = LocalReducedMotion.current
    val target = fraction ?: 0f
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = if (reduced) androidx.compose.animation.core.snap() else BudgetMotion.standard(),
        label = "modelDownloadProgress",
    )
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        if (fraction == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(
                progress = { animated.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel) { Text("Cancelar") }
        }
        AnimatedVisibility(
            visible = fraction != null && fraction < 1f,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Text(
                "Puedes salir de la app: la descarga sigue.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ModelVariantDialog(
    current: LlmModelVariant,
    onSelect: (LlmModelVariant) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modelo del asistente") },
        text = {
            Column {
                Text(
                    "Al elegir uno se descarga. Ocupa espacio en el teléfono y tarda " +
                        "varios minutos con Wi-Fi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                ModelCatalog.all.forEach { variant ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = variant.id == current.id,
                                onClick = { onSelect(variant) },
                            )
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = variant.id == current.id,
                            onClick = { onSelect(variant) },
                        )
                        Spacer(Modifier.height(0.dp))
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(variant.displayName, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                variant.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

private fun engineLabel(engine: LlmEngine, state: ModelState): String = when (engine) {
    LlmEngine.AICORE -> "Gemini Nano, integrado en este teléfono"
    LlmEngine.LITERTLM -> "Gemma en este dispositivo"
    LlmEngine.NONE -> when (state) {
        is ModelState.Installed -> "Preparando ${state.variant.displayName}"
        else -> "Sin modelo: respuestas con tus cifras, sin redacción"
    }
}

private fun bytesLabel(written: Long, total: Long): String =
    if (total > 0L) "${megabytes(written)} de ${megabytes(total)} MB" else "Preparando la descarga"

private fun megabytes(bytes: Long): String = "%.0f".format(bytes / 1_048_576.0)
