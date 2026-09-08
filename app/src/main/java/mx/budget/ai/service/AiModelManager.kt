package mx.budget.ai.service

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mx.budget.ai.download.ModelDownloadWorker
import mx.budget.data.settings.SettingsRepository

/** Lo que Perfil necesita saber del modelo local, sin conocer WorkManager ni el engine. */
sealed interface ModelState {
    /** No hay modelo; [partialBytes] es lo que quedó de un intento anterior. */
    data class NotInstalled(val partialBytes: Long) : ModelState

    data class Downloading(val variant: LlmModelVariant, val written: Long, val total: Long) : ModelState

    /** Bajó completo y se está comprobando el SHA-256, que en gigabytes no es instantáneo. */
    data class Verifying(val variant: LlmModelVariant) : ModelState

    data class Installed(val variant: LlmModelVariant) : ModelState

    data class Failed(val variant: LlmModelVariant, val message: String) : ModelState
}

/**
 * Fachada única de la provisión del modelo de IA: qué variante se eligió, si está
 * en disco, cómo va la descarga y cómo borrarlo.
 *
 * Existe para que la interfaz no tenga que tocar ni [LiteRtLmManager] ni WorkManager,
 * y para que el engine se entere de que llegó (o se fue) un modelo sin necesidad de
 * reiniciar el proceso.
 */
class AiModelManager(
    private val context: Context,
    private val settings: SettingsRepository,
    private val liteRtLm: LiteRtLmManager,
    private val scope: CoroutineScope,
) {

    private val diskTick = MutableStateFlow(0)

    init {
        // El engine tiene que saber qué variante prefiere el usuario: si hay dos
        // archivos en disco, la elegida gana, y al cambiarla se recarga sola.
        scope.launch {
            settings.aiModelVariant.collect { liteRtLm.preferredVariant = ModelCatalog.byId(it) }
        }
    }

    /** Variante elegida en Perfil (no necesariamente la instalada). */
    val selectedVariant: Flow<LlmModelVariant> =
        settings.aiModelVariant.map { ModelCatalog.byId(it) }

    val state: StateFlow<ModelState> = combine(
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(ModelDownloadWorker.WORK_NAME),
        settings.aiModelVariant,
        diskTick,
    ) { infos, selectedId, _ ->
        val selected = ModelCatalog.byId(selectedId)
        val info = infos.firstOrNull()
        val installed = ModelStorage.installed(context)
        when {
            info != null && !info.state.isFinished -> downloadingState(info, selected)
            info?.state == WorkInfo.State.FAILED && installed == null -> ModelState.Failed(
                selected,
                info.outputData.getString(ModelDownloadWorker.KEY_ERROR)
                    ?: "La descarga no terminó.",
            )
            installed != null -> ModelState.Installed(installed)
            else -> ModelState.NotInstalled(ModelStorage.partialBytes(context, selected))
        }
    }.stateIn(
        scope,
        SharingStarted.WhileSubscribed(5_000),
        ModelState.NotInstalled(0L),
    )

    private fun downloadingState(info: WorkInfo, selected: LlmModelVariant): ModelState {
        val variant = ModelCatalog.byId(
            info.progress.getString(ModelDownloadWorker.KEY_VARIANT) ?: selected.id
        )
        val written = info.progress.getLong(ModelDownloadWorker.KEY_BYTES, 0L)
        val total = info.progress.getLong(ModelDownloadWorker.KEY_TOTAL, 0L)
        return if (total > 0L && written >= total) {
            ModelState.Verifying(variant)
        } else {
            ModelState.Downloading(variant, written, total)
        }
    }

    /** Guarda la variante elegida y encola la descarga. */
    fun start(variant: LlmModelVariant) {
        scope.launch { settings.setAiModelVariant(variant.id) }
        ModelDownloadWorker.enqueue(context, variant)
    }

    fun cancel() {
        ModelDownloadWorker.cancel(context)
        diskTick.value++
    }

    /**
     * Borra el modelo del dispositivo. Cierra antes el engine, porque mientras esté
     * abierto el archivo sigue en uso y la app se queda con un motor apuntando a algo
     * que ya no existe.
     */
    fun delete(variant: LlmModelVariant) {
        ModelDownloadWorker.cancel(context)
        liteRtLm.releaseAndForget()
        ModelStorage.delete(context, variant)
        diskTick.value++
    }

    /** Lo llama el worker al terminar: hay modelo nuevo, el engine puede reintentar. */
    fun onModelInstalled(variant: LlmModelVariant) {
        liteRtLm.onModelChanged()
        scope.launch { settings.setAiModelVariant(variant.id) }
        diskTick.value++
    }

    /** Refresca el estado leído del disco (tras volver a Perfil, por ejemplo). */
    fun refresh() {
        diskTick.value++
    }
}
