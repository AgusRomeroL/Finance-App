package mx.budget.data.statements

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Extracción de texto **100% local** de un estado de cuenta (Fase C, paquete C1).
 *
 * PRINCIPIO DE PRIVACIDAD: el PDF/imagen crudo NUNCA sale del dispositivo. Solo
 * el texto plano extraído aquí viaja después al LLM cloud. Por eso la extracción
 * es local:
 *  - **PDF** → Apache PDFBox (port `pdfbox-android`), `PDFTextStripper`.
 *  - **Imagen (JPG/PNG)** → ML Kit Text Recognition (OCR on-device, modelo latino
 *    empacado; mismo stack ML Kit que `genai-prompt`).
 *
 * Detección de tipo por MIME del `ContentResolver` (los estados de cuenta suelen
 * venir como PDF; la imagen es el fallback para capturas/fotos).
 */
class StatementTextExtractor(private val context: Context) {

    /** Resultado de la extracción: texto o un error legible (sin crash). */
    sealed interface Result {
        data class Success(val text: String) : Result
        data class Failure(val message: String) : Result
    }

    /**
     * Extrae texto del documento apuntado por [uri]. Corre en IO. Determina PDF vs
     * imagen por el MIME type; si no puede leerlo, devuelve [Result.Failure] con un
     * mensaje para el usuario (nunca lanza).
     */
    suspend fun extract(uri: Uri): Result = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri).orEmpty()
        val name = displayName(uri).lowercase()
        try {
            when {
                mime.contains("pdf", ignoreCase = true) || name.endsWith(".pdf") -> extractPdf(uri)
                mime.startsWith("image", ignoreCase = true) -> extractImage(uri)
                // Exports estructurados (Amazon ZIP, Mercado Libre/PayPal CSV, SAT XML,
                // Google JSON) → texto plano local, SIN OCR ni LLM de por medio.
                mime.contains("zip", ignoreCase = true) || name.endsWith(".zip") -> extractZip(uri)
                mime.contains("csv", ignoreCase = true) || name.endsWith(".csv") ||
                    mime.contains("xml", ignoreCase = true) || name.endsWith(".xml") ||
                    mime.contains("json", ignoreCase = true) || name.endsWith(".json") ||
                    mime.startsWith("text", ignoreCase = true) -> extractPlainText(uri)
                // Sin MIME fiable: intenta PDF, luego texto, luego imagen (OCR).
                else -> runCatching { extractPdf(uri) }.getOrNull()?.takeIf { it is Result.Success }
                    ?: runCatching { extractPlainText(uri) }.getOrNull()?.takeIf { it is Result.Success }
                    ?: extractImage(uri)
            }
        } catch (e: Exception) {
            Result.Failure("No se pudo leer el archivo: ${e.message ?: "formato no soportado"}")
        }
    }

    /** Nombre visible del archivo (para detectar por extensión si el MIME falla). */
    private fun displayName(uri: Uri): String = runCatching {
        context.contentResolver.query(
            uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null,
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull() ?: uri.lastPathSegment ?: ""

    /** Lee un archivo de texto (CSV/XML/JSON) tal cual, con tope de tamaño. */
    private fun extractPlainText(uri: Uri): Result {
        context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) return Result.Failure("No se pudo abrir el archivo.")
            return finishText(input.bufferedReader(Charsets.UTF_8).readText())
        }
    }

    /**
     * Descomprime un ZIP (export de Amazon "Solicitar mis datos") y devuelve el CSV/
     * JSON/XML más relevante como texto. Prioriza el archivo de pedidos
     * (`Retail.OrderHistory…`); si no lo halla, el más grande.
     */
    private fun extractZip(uri: Uri): Result {
        context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) return Result.Failure("No se pudo abrir el archivo.")
            java.util.zip.ZipInputStream(input.buffered()).use { zip ->
                var chosen: Pair<String, String>? = null
                var entry = zip.nextEntry
                while (entry != null) {
                    val ename = entry.name.lowercase()
                    val ok = !entry.isDirectory &&
                        (ename.endsWith(".csv") || ename.endsWith(".json") || ename.endsWith(".xml"))
                    if (ok) {
                        val content = zip.readBytes().toString(Charsets.UTF_8)
                        val priority = ename.contains("orderhistory") ||
                            ename.contains("order") || ename.contains("compra")
                        if (priority) { chosen = entry.name to content; break }
                        if (chosen == null || content.length > chosen!!.second.length) {
                            chosen = entry.name to content
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
                val (fname, body) = chosen
                    ?: return Result.Failure(
                        "El ZIP no contiene CSV/JSON/XML reconocible " +
                            "(Amazon: Retail.OrderHistory.1.csv).",
                    )
                return finishText("# Archivo: $fname\n$body")
            }
        }
    }

    /** Recorta el texto para no desbordar el contexto del LLM y valida no-vacío. */
    private fun finishText(raw: String): Result {
        val text = raw.trim()
        if (text.isBlank()) return Result.Failure("El archivo está vacío.")
        val capped = if (text.length > MAX_CHARS) {
            text.take(MAX_CHARS) + "\n… (truncado: reimporta por rango de fechas si falta detalle)"
        } else {
            text
        }
        return Result.Success(capped)
    }

    private fun extractPdf(uri: Uri): Result {
        // pdfbox-android necesita el ResourceLoader inicializado (fuentes/glyphs).
        // Idempotente y barato: se llama justo antes de usarlo.
        PDFBoxResourceLoader.init(context.applicationContext)
        context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) return Result.Failure("No se pudo abrir el archivo.")
            PDDocument.load(input).use { doc ->
                val text = PDFTextStripper().getText(doc).trim()
                return if (text.isBlank()) {
                    Result.Failure(
                        "El PDF no contiene texto seleccionable (parece escaneado). " +
                            "Exporta un estado de cuenta digital o sube una imagen para OCR."
                    )
                } else {
                    Result.Success(text)
                }
            }
        }
    }

    private suspend fun extractImage(uri: Uri): Result {
        val bitmap = context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) return Result.Failure("No se pudo abrir la imagen.")
            BitmapFactory.decodeStream(input)
        } ?: return Result.Failure("No se pudo decodificar la imagen.")
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val result = recognizer.process(image).await()
        val text = result.text.trim()
        return if (text.isBlank()) {
            Result.Failure("No se detectó texto en la imagen.")
        } else {
            Result.Success(text)
        }
    }

    companion object {
        /** Tope de caracteres del texto extraído, para no desbordar el LLM. */
        private const val MAX_CHARS = 40_000
    }
}
