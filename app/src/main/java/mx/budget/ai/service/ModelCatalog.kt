package mx.budget.ai.service

import android.content.Context
import java.io.File

/**
 * Una variante del modelo Gemma `.litertlm` que la app sabe descargar y cargar.
 *
 * Origen (decisión 4 del plan, resuelta en la Fase 8): el repo público de
 * `litert-community` en Hugging Face, sin token. La URL apunta a un **commit**, no
 * a `main`, así que el archivo es inmutable: si el repo cambia, esta variante sigue
 * bajando exactamente los bytes cuya huella está aquí. Cambiar de modelo es
 * publicar una versión de la app con valores nuevos, que
 * `scripts/admin/pin_model.py` imprime listos para pegar.
 */
data class LlmModelVariant(
    /** Id estable que se persiste en DataStore. */
    val id: String,
    /** Nombre visible en Perfil. */
    val displayName: String,
    /** Una línea que explica el compromiso entre velocidad y calidad. */
    val description: String,
    /**
     * Nombre del archivo en `getExternalFilesDir()`. No cambia con el origen, para
     * que un modelo provisionado antes por `adb` siga sirviendo.
     */
    val fileName: String,
    /** `https://huggingface.co/<repo>/resolve/<commit>/<archivo>`. */
    val downloadUrl: String,
    /** Tamaño exacto del archivo terminado. */
    val sizeBytes: Long,
    /** SHA-256 del archivo terminado (el oid LFS que publica Hugging Face). */
    val sha256: String,
)

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
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/" +
            "resolve/1caa527c1e88b55f046fcd8d2b4265606680663d/gemma-4-E2B-it.litertlm",
        sizeBytes = 2_588_147_712L,
        sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
    )

    val E4B = LlmModelVariant(
        id = "E4B",
        displayName = "Gemma E4B",
        description = "Redacta mejor, pesa más y tarda más en responder.",
        fileName = "gemma-4-e4b-it.litertlm",
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/" +
            "resolve/e6a3892a8c22cf87ff99583996149cf3cb32452b/gemma-4-E4B-it.litertlm",
        sizeBytes = 3_659_530_240L,
        sha256 = "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0",
    )

    /** Orden de presentación en Perfil. */
    val all: List<LlmModelVariant> = listOf(E2B, E4B)

    /** Variante de fábrica. */
    val default: LlmModelVariant = E2B

    fun byId(id: String?): LlmModelVariant = all.firstOrNull { it.id == id } ?: default

    fun byFileName(fileName: String): LlmModelVariant? = all.firstOrNull { it.fileName == fileName }
}

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
