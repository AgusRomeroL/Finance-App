package mx.budget.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.budget.data.export.ExportFiles
import mx.budget.data.local.entity.QuincenaEntity
import mx.budget.ui.common.AppLocale
import mx.budget.ui.theme.BudgetMotion
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Seccion "Exportar y respaldar" de Perfil (Fase 5, punto 5).
 *
 * Es autocontenida: los selectores de archivo viven aqui y no engordan la lista
 * de parametros de [ProfileScreen], que ya pasa de treinta.
 */
@Composable
fun ExportSection(
    viewModel: ExportViewModel,
    onRestore: (mx.budget.data.backup.BackupInspection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val stage by viewModel.stage.collectAsState()
    val restore by viewModel.restore.collectAsState()
    val marcas by viewModel.timestamps.collectAsState()
    val quincenas by viewModel.quincenas.collectAsState()

    var eligiendoQuincena by remember { mutableStateOf(false) }
    var eligiendoRango by remember { mutableStateOf(false) }

    // Un lanzador por tipo: CreateDocument fija el tipo al construirse.
    val guardarPdf = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> if (uri != null) viewModel.guardarEn(uri) else viewModel.cancelarGuardado() }
    val guardarXlsx = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    ) { uri -> if (uri != null) viewModel.guardarEn(uri) else viewModel.cancelarGuardado() }
    val guardarCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> if (uri != null) viewModel.guardarEn(uri) else viewModel.cancelarGuardado() }
    val guardarRespaldo = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> if (uri != null) viewModel.guardarEn(uri) else viewModel.cancelarGuardado() }
    // Los respaldos suelen llegar tipados como octet-stream; un filtro estrecho
    // los escondería en el selector.
    val abrirRespaldo = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.inspeccionarRespaldo(uri) }

    LaunchedEffect(stage) {
        val listo = stage as? ExportStage.Ready ?: return@LaunchedEffect
        when (listo.kind) {
            ExportKind.PDF -> guardarPdf.launch(listo.nombre)
            ExportKind.XLSX -> guardarXlsx.launch(listo.nombre)
            ExportKind.CSV -> guardarCsv.launch(listo.nombre)
            ExportKind.BACKUP -> guardarRespaldo.launch(listo.nombre)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(22.dp)
    ) {
        Text(
            "EXPORTAR Y RESPALDAR",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.6.sp,
        )
        Spacer(Modifier.height(14.dp))

        SettingRow(
            icon = Icons.Filled.PictureAsPdf,
            title = "Reporte de quincena en PDF",
            subtitle = ultimaVez(marcas, mx.budget.data.settings.SettingsRepository.EXPORT_PDF),
            trailingBadge = null,
            onClick = { eligiendoQuincena = true },
        )
        Spacer(Modifier.height(8.dp))
        SettingRow(
            icon = Icons.Filled.TableChart,
            title = "Libro de Excel",
            subtitle = ultimaVez(marcas, mx.budget.data.settings.SettingsRepository.EXPORT_XLSX),
            trailingBadge = null,
            onClick = { viewModel.prepararXlsx() },
        )
        Spacer(Modifier.height(8.dp))
        SettingRow(
            icon = Icons.Filled.TextSnippet,
            title = "Movimientos en CSV",
            subtitle = ultimaVez(marcas, mx.budget.data.settings.SettingsRepository.EXPORT_CSV),
            trailingBadge = null,
            onClick = { eligiendoRango = true },
        )
        Spacer(Modifier.height(8.dp))
        SettingRow(
            icon = Icons.Filled.Backup,
            title = "Respaldar la base de datos",
            subtitle = ultimaVez(marcas, mx.budget.data.settings.SettingsRepository.EXPORT_BACKUP),
            trailingBadge = null,
            onClick = { viewModel.prepararRespaldo() },
        )
        Spacer(Modifier.height(8.dp))
        SettingRow(
            icon = Icons.Filled.Restore,
            title = "Restaurar un respaldo",
            subtitle = "Reemplaza todos los datos de este teléfono",
            trailingBadge = null,
            onClick = { abrirRespaldo.launch(arrayOf("*/*")) },
        )
        Spacer(Modifier.height(8.dp))
        SettingRow(
            icon = Icons.Filled.CloudDone,
            title = "Copia automática de Android",
            subtitle = "Activa: la base y los ajustes viajan al cambiar de teléfono",
            trailingBadge = null,
            onClick = {},
            navigable = false,
        )

        AnimatedVisibility(
            visible = stage !is ExportStage.Idle,
            enter = fadeIn(BudgetMotion.standard()),
            exit = fadeOut(BudgetMotion.standard()),
        ) {
            Column {
                Spacer(Modifier.height(12.dp))
                when (val actual = stage) {
                    is ExportStage.Working -> FilaEstado("Generando el archivo…", cargando = true)
                    is ExportStage.Ready -> FilaEstado("Elige dónde guardarlo…", cargando = true)
                    is ExportStage.Saved -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Guardado.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            context.startActivity(
                                ExportFiles.shareIntent(
                                    context,
                                    actual.archivo,
                                    actual.kind.mime,
                                    "Presupuesto Familiar",
                                )
                            )
                        }) { Text("Compartir") }
                        TextButton(onClick = viewModel::limpiarEstado) { Text("Listo") }
                    }
                    is ExportStage.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            actual.mensaje,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = viewModel::limpiarEstado) { Text("Entendido") }
                    }
                    ExportStage.Idle -> Unit
                }
            }
        }
    }

    if (eligiendoQuincena) {
        SelectorDeQuincena(
            quincenas = quincenas,
            onDismiss = { eligiendoQuincena = false },
            onElegir = {
                eligiendoQuincena = false
                viewModel.prepararPdf(it)
            },
        )
    }

    if (eligiendoRango) {
        SelectorDeRango(
            onDismiss = { eligiendoRango = false },
            onElegir = { desde, hasta ->
                eligiendoRango = false
                viewModel.prepararCsv(desde, hasta)
            },
        )
    }

    when (val paso = restore) {
        is RestoreStage.Confirm -> ConfirmarRestauracion(
            inspeccion = paso.inspeccion,
            onCancelar = viewModel::cancelarRestauracion,
            onConfirmar = {
                viewModel.marcarRestaurando()
                onRestore(paso.inspeccion)
            },
        )
        is RestoreStage.Failed -> AlertDialog(
            onDismissRequest = viewModel::cancelarRestauracion,
            title = { Text("No se pudo restaurar") },
            text = { Text(paso.mensaje) },
            confirmButton = {
                TextButton(onClick = viewModel::cancelarRestauracion) { Text("Entendido") }
            },
        )
        RestoreStage.Reading, RestoreStage.Restoring -> AlertDialog(
            onDismissRequest = {},
            title = { Text(if (paso == RestoreStage.Reading) "Revisando el respaldo" else "Restaurando") },
            text = {
                Text(
                    if (paso == RestoreStage.Reading) "Un momento, se está verificando el archivo."
                    else "La app se va a reiniciar en cuanto termine."
                )
            },
            confirmButton = {},
        )
        RestoreStage.Idle -> Unit
    }
}

@Composable
private fun FilaEstado(texto: String, cargando: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (cargando) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
        }
        Text(
            texto,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SelectorDeQuincena(
    quincenas: List<QuincenaEntity>,
    onDismiss: () -> Unit,
    onElegir: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿De qué quincena?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (quincenas.isEmpty()) {
                    Text("Todavía no hay quincenas que reportar.")
                }
                quincenas.take(12).forEach { quincena ->
                    TextButton(
                        onClick = { onElegir(quincena.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(quincena.label, modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/**
 * Rango del CSV. Se ofrecen periodos con sentido para el hogar en vez de un
 * calendario doble: quien exporta quiere "este año" o "todo", no elegir dos
 * fechas exactas.
 */
@Composable
private fun SelectorDeRango(
    onDismiss: () -> Unit,
    onElegir: (LocalDate, LocalDate) -> Unit,
) {
    val hoy = LocalDate.now(ZoneId.of("America/Mexico_City"))
    val opciones = listOf(
        "Este mes" to (hoy.withDayOfMonth(1) to hoy),
        "Últimos 3 meses" to (hoy.minusMonths(3) to hoy),
        "Este año" to (hoy.withDayOfYear(1) to hoy),
        "Todo el historial" to (LocalDate.of(2020, 1, 1) to hoy),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Qué periodo?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                opciones.forEach { (etiqueta, rango) ->
                    TextButton(
                        onClick = { onElegir(rango.first, rango.second) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(etiqueta, modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun ConfirmarRestauracion(
    inspeccion: mx.budget.data.backup.BackupInspection,
    onCancelar: () -> Unit,
    onConfirmar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("¿Restaurar este respaldo?") },
        text = {
            Column {
                Text(
                    "Contiene ${inspeccion.gastos} movimientos y ${inspeccion.quincenas} quincenas" +
                        (inspeccion.nombreHogar?.let { " de \"$it\"" } ?: "") + "."
                )
                Spacer(Modifier.height(8.dp))
                if (!inspeccion.hogarCoincide) {
                    Text(
                        "Ojo: ese respaldo es de otro hogar. Al restaurarlo, este teléfono " +
                            "pasa a trabajar con los datos de ese hogar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    "Reemplaza todos los datos locales de este teléfono y la app se reinicia. " +
                        "Lo que esté en la nube y sea más reciente se vuelve a bajar solo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirmar) { Text("Restaurar") } },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
    )
}

private val formatoFecha = DateTimeFormatter.ofPattern("d MMM yyyy", AppLocale)

private fun ultimaVez(marcas: Map<String, Long>, clave: String): String {
    val marca = marcas[clave] ?: return "Nunca"
    val fecha = Instant.ofEpochMilli(marca)
        .atZone(ZoneId.of("America/Mexico_City"))
        .toLocalDate()
    return "Último: ${fecha.format(formatoFecha)}"
}
