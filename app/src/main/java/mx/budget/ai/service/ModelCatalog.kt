package mx.budget.ai.service

import android.content.Context
import java.io.File

/**
 * Una variante del modelo Gemma `.litertlm` que la app sabe descargar y cargar.
 *
 * El tamaño y el SHA-256 NO viven aquí: llegan del manifiesto remoto
 * (`models/manifest.json` en Firebase Storage) que sube `scripts/admin/upload_model.py`.
 * Así, cambiar de archivo de modelo no obliga a publicar una versión de la app.
 */
data class LlmModelVariant(
    /** Id estable que se persiste en DataStore. */
    val id: String,
    /** Nombre visible en Perfil. */
    val displayName: String,
    /** Una línea que explica el compromiso entre velocidad y calidad. */
    val description: String,
    /** Nombre del archivo en `getExternalFilesDir()` y en el bucket. */
    val fileName: String,
) {
    /** Ruta del objeto dentro del bucket de Firebase Storage. */
    val storagePath: String get() = "$MODELS_PREFIX$fileName"

    companion object {
        const val MODELS_PREFIX = "models/"
    }
}

/**
 * Catálogo de variantes ofrecidas en Perfil, "Asistente IA".
 *
 * Decisión de la Fase 4 (2026-09-07): se ofrecen las dos y la chica es la de
 * fábrica. El aparato objetivo es el Fold de Norma (Tensor G4) y no queda ningún
 * equivalente para medir, así que la latencia se acotó por arriba con el Pixel 7
 * (Tensor G2, sin AICore); con esa cota, arrancar con E2B es lo honesto y quien
 * quiera más calidad de redacción puede cambiar a E4B sabiendo lo que cuesta.
 */
object ModelCatalog {

    val E2B = LlmModelVariant(
        id = "E2B",
        displayName = "Gemma E2B",
        description = "Más ligera y rápida. Recomendada.",
        fileName = "gemma-4-e2b-it.litertlm",
    )

    val E4B = LlmModelVariant(
        id = "E4B",
        displayName = "Gemma E4B",
        description = "Redacta mejor, pesa más y tarda más en responder.",
        fileName = "gemma-4-e4b-it.litertlm",
    )

    /** Orden de presentación en Perfil. */
    val all: List<LlmModelVariant> = listOf(E2B, E4B)

    /** Variante de fábrica. */
    val default: LlmModelVariant = E2B

    fun byId(id: String?): LlmModelVariant = all.firstOrNull { it.id == id } ?: default

    fun byFileName(fileName: String): LlmModelVariant? = all.firstOrNull { it.fileName == fileName }
}

/**
 * Lo que el manifiesto remoto dice de una variante: cuánto pesa y qué huella debe
 * tener el archivo terminado. Sin estos dos datos no se descarga nada, porque no
 * habría forma de saber si lo que quedó en disco es el modelo o basura.
 */
data class RemoteModelInfo(
    val id: String,
    val sizeBytes: Long,
    val sha256: String,
)

/**
 * Dónde vive el modelo en el dispositivo. Carpeta propia de la app
 * (`getExternalFilesDir`) porque el `open()` nativo de LiteRT-LM devuelve
 * `PERMISSION_DENIED` si el archivo lo dejó otro uid, que era justo lo que pasaba
 * al empujarlo por `adb` (hallazgo del Apéndice F.8.4).
 */
object ModelStorage {

    fun file(context: Context, variant: LlmModelVariant): File =
        File(context.getExternalFilesDir(null), variant.fileName)

    /** La primera variante cuyo archivo ya está en disco, si hay alguna. */
    fun installed(context: Context): LlmModelVariant? =
        ModelCatalog.all.firstOrNull { file(context, it).exists() }

    /** Borra el modelo y cualquier descarga a medias de esa variante. */
    fun delete(context: Context, variant: LlmModelVariant) {
        file(context, variant).delete()
        File(file(context, variant).path + PART_SUFFIX).delete()
    }

    /** Bytes ya descargados de una variante que aún no termina. */
    fun partialBytes(context: Context, variant: LlmModelVariant): Long {
        val part = File(file(context, variant).path + PART_SUFFIX)
        return if (part.exists()) part.length() else 0L
    }

    const val PART_SUFFIX = ".part"
}
