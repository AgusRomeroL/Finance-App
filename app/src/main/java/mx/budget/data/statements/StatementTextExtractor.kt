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
        try {
            when {
                mime.contains("pdf", ignoreCase = true) -> extractPdf(uri)
                mime.startsWith("image", ignoreCase = true) -> extractImage(uri)
                // Sin MIME fiable: intenta PDF primero (extensión típica del estado
                // de cuenta) y si falla cae a imagen.
                else -> runCatching { extractPdf(uri) }.getOrNull()
                    ?.takeIf { it is Result.Success }
                    ?: extractImage(uri)
            }
        } catch (e: Exception) {
            Result.Failure("No se pudo leer el archivo: ${e.message ?: "formato no soportado"}")
        }
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
}
