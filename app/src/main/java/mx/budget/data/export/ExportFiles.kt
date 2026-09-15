package mx.budget.data.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import mx.budget.ui.common.AppLocale
import java.io.File
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Trabajo de disco comun a las exportaciones.
 *
 * Todo se genera primero en la cache de la app y solo despues se copia al destino
 * que la persona eligio en el selector del sistema. Asi la generacion no depende
 * de que el proveedor destino acepte escrituras parciales, y el archivo temporal
 * sirve ademas para compartir por [shareIntent].
 */
object ExportFiles {

    const val AUTHORITY = "mx.budget.fileprovider"

    /** Carpeta de trabajo; se limpia sola al quedarse sin espacio (es cache). */
    fun exportsDir(context: Context): File =
        File(context.cacheDir, "exports").apply { mkdirs() }

    fun newFile(context: Context, nombre: String): File =
        File(exportsDir(context), nombre).also { if (it.exists()) it.delete() }

    /**
     * Copia el archivo generado al destino elegido. Se abre con "wt" y no con "w":
     * algunos proveedores (Drive entre ellos) no truncan con "w" y dejan restos del
     * archivo anterior al final.
     */
    fun copyTo(context: Context, origen: File, destino: Uri) {
        context.contentResolver.openOutputStream(destino, "wt").use { salida ->
            requireNotNull(salida) { "No se pudo abrir el destino elegido." }
            origen.inputStream().use { it.copyTo(salida) }
        }
    }

    fun shareIntent(context: Context, archivo: File, mime: String, titulo: String): Intent {
        val uri = FileProvider.getUriForFile(context, AUTHORITY, archivo)
        val envio = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, titulo)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(envio, titulo).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** Nombre de archivo sin acentos ni caracteres que algun sistema rechace. */
    fun slug(texto: String): String =
        Normalizer.normalize(texto, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(AppLocale)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "presupuesto" }

    fun hoyIso(): String = LocalDate.now().format(DateTimeFormatter.ISO_DATE)

    fun selloDeTiempo(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm"))
}
