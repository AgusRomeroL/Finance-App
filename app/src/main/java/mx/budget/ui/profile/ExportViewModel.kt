package mx.budget.ui.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mx.budget.data.backup.BackupInspection
import mx.budget.data.export.CsvExporter
import mx.budget.data.export.ExportFiles
import mx.budget.data.export.PdfReportWriter
import mx.budget.data.export.QuincenaReportBuilder
import mx.budget.data.export.XlsxBudgetExporter
import mx.budget.data.backup.DatabaseBackupManager
import mx.budget.data.local.entity.QuincenaEntity
import mx.budget.data.repository.QuincenaRepository
import mx.budget.data.settings.SettingsRepository
import java.io.File
import java.time.LocalDate

/** Lo que la persona puede generar desde "Exportar y respaldar". */
enum class ExportKind(val mime: String, val clave: String) {
    PDF("application/pdf", SettingsRepository.EXPORT_PDF),
    XLSX(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        SettingsRepository.EXPORT_XLSX,
    ),
    CSV("text/csv", SettingsRepository.EXPORT_CSV),
    BACKUP("application/octet-stream", SettingsRepository.EXPORT_BACKUP),
}

/** Fase en la que esta una exportacion. */
sealed interface ExportStage {
    data object Idle : ExportStage
    data class Working(val kind: ExportKind) : ExportStage

    /** Archivo listo en la cache, esperando que se elija donde guardarlo. */
    data class Ready(val kind: ExportKind, val archivo: File, val nombre: String) : ExportStage
    data class Saved(val kind: ExportKind, val archivo: File) : ExportStage
    data class Failed(val mensaje: String) : ExportStage
}

/** Paso a paso de la restauracion, que exige confirmacion explicita. */
sealed interface RestoreStage {
    data object Idle : RestoreStage
    data object Reading : RestoreStage
    data class Confirm(val inspeccion: BackupInspection) : RestoreStage
    data object Restoring : RestoreStage
    data class Failed(val mensaje: String) : RestoreStage
}

/**
 * Exportaciones y respaldo (Fase 5, puntos 2 a 5).
 *
 * Todo se genera primero en la cache y solo despues se copia al destino del
 * selector del sistema: asi el trabajo pesado no depende de que el proveedor
 * destino siga vivo, y el mismo archivo temporal sirve para compartir.
 */
class ExportViewModel(
    private val context: Context,
    private val quincenaRepository: QuincenaRepository,
    private val reportBuilder: QuincenaReportBuilder,
    private val csvExporter: CsvExporter,
    private val backupManager: DatabaseBackupManager,
    private val settings: SettingsRepository,
    private val householdId: String,
) : ViewModel() {

    private val _stage = MutableStateFlow<ExportStage>(ExportStage.Idle)
    val stage: StateFlow<ExportStage> = _stage.asStateFlow()

    private val _restore = MutableStateFlow<RestoreStage>(RestoreStage.Idle)
    val restore: StateFlow<RestoreStage> = _restore.asStateFlow()

    val quincenas: StateFlow<List<QuincenaEntity>> = quincenaRepository.observeAll(householdId)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val timestamps: StateFlow<Map<String, Long>> = settings.exportTimestamps
        .catch { emit(emptyMap()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Reporte PDF de una quincena. */
    fun prepararPdf(quincenaId: String) = preparar(ExportKind.PDF) {
        val report = reportBuilder.build(quincenaId)
            ?: error("Esa quincena ya no existe.")
        val nombre = "presupuesto-${ExportFiles.slug(report.quincena.label)}.pdf"
        val archivo = ExportFiles.newFile(context, nombre)
        PdfReportWriter(context).write(report, archivo)
        archivo to nombre
    }

    /** Libro con una hoja por quincena, desde la mas reciente. */
    fun prepararXlsx(maximoQuincenas: Int = 24) = preparar(ExportKind.XLSX) {
        val periodos = quincenaRepository.observeAll(householdId).first()
            .take(maximoQuincenas)
        if (periodos.isEmpty()) error("Todavía no hay quincenas que exportar.")
        val reportes = periodos.mapNotNull { reportBuilder.build(it.id) }
        val nombre = "presupuesto-${ExportFiles.hoyIso()}.xlsx"
        val archivo = ExportFiles.newFile(context, nombre)
        XlsxBudgetExporter().write(reportes, archivo)
        archivo to nombre
    }

    /** Movimientos de un rango de fechas. */
    fun prepararCsv(desde: LocalDate, hasta: LocalDate) = preparar(ExportKind.CSV) {
        val nombre = "movimientos-$desde-a-$hasta.csv"
        val archivo = ExportFiles.newFile(context, nombre)
        csvExporter.write(desde, hasta, archivo)
        archivo to nombre
    }

    /** Copia completa de la base. */
    fun prepararRespaldo() = preparar(ExportKind.BACKUP) {
        val archivo = backupManager.export()
        archivo to archivo.name
    }

    private fun preparar(kind: ExportKind, bloque: suspend () -> Pair<File, String>) {
        if (_stage.value is ExportStage.Working) return
        viewModelScope.launch {
            _stage.value = ExportStage.Working(kind)
            try {
                val (archivo, nombre) = withContext(Dispatchers.IO) { bloque() }
                _stage.value = ExportStage.Ready(kind, archivo, nombre)
            } catch (e: Exception) {
                _stage.value = ExportStage.Failed(e.message ?: "No se pudo generar el archivo.")
            }
        }
    }

    /** Copia el archivo ya generado al destino que la persona eligio. */
    fun guardarEn(destino: Uri) {
        val listo = _stage.value as? ExportStage.Ready ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ExportFiles.copyTo(context, listo.archivo, destino)
                }
                settings.setExportTimestamp(listo.kind.clave, System.currentTimeMillis())
                _stage.value = ExportStage.Saved(listo.kind, listo.archivo)
            } catch (e: Exception) {
                _stage.value = ExportStage.Failed(e.message ?: "No se pudo guardar el archivo.")
            }
        }
    }

    /** La persona cerro el selector sin elegir destino. */
    fun cancelarGuardado() {
        if (_stage.value is ExportStage.Ready) _stage.value = ExportStage.Idle
    }

    fun limpiarEstado() {
        _stage.value = ExportStage.Idle
    }

    // ── Restauracion ────────────────────────────────────────────────────────

    fun inspeccionarRespaldo(origen: Uri) {
        viewModelScope.launch {
            _restore.value = RestoreStage.Reading
            try {
                _restore.value = RestoreStage.Confirm(backupManager.inspect(origen))
            } catch (e: Exception) {
                _restore.value = RestoreStage.Failed(
                    e.message ?: "No se pudo leer el respaldo."
                )
            }
        }
    }

    fun cancelarRestauracion() {
        _restore.value = RestoreStage.Idle
    }

    fun marcarRestaurando() {
        _restore.update { RestoreStage.Restoring }
    }

    fun fallarRestauracion(mensaje: String) {
        _restore.value = RestoreStage.Failed(mensaje)
    }
}
