package mx.budget.wear.presentation.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Chip
import androidx.wear.protolayout.material.ChipColors
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import com.google.android.horologist.tiles.SuspendingTileService
import mx.budget.wear.data.ExpenseSender
import mx.budget.wear.data.WearCache

/**
 * Tile A — **Recomendados**. Muestra el cargo sugerido más relevante (concepto +
 * monto típico + razón) con un botón "Confirmar" que lo manda a la bandeja del
 * teléfono (propose-then-confirm) reusando el camino de gasto rápido.
 *
 * Reescrito con **ProtoLayout** (antes `glance-wear-tiles` alpha, que crasheaba con
 * "Glance Wear Tile Error" y hundía el FPS). Los datos vienen del cache local
 * ([WearCache]); el reloj no consulta Room ni red. El botón usa `LoadAction`: al
 * pulsarlo, [tileRequest] se re-ejecuta con `lastClickableId="accept"`, dispara el
 * envío y devuelve una tile de confirmación efímera.
 */
class SuggestionsTileService : SuspendingTileService() {

    override suspend fun tileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): TileBuilders.Tile {
        val deviceParams = requestParams.deviceConfiguration
        val clickedId = requestParams.currentState.lastClickableId

        // Confirmación: se pulsó "Confirmar" → enviar el primer recomendado.
        // El envío por MessageClient NO tiene cola offline: si falla, se dice
        // claramente (nada de "Enviado ✓" falso) y se ofrece reintentar.
        if (clickedId == CLICK_ACCEPT) {
            val top = WearCache.suggestions(this).firstOrNull()
                ?: return tile(messageLayout(deviceParams, "Sin sugerencias", "Nada por registrar ahora"))
            val sent = if (top.amount > 0.0) {
                runCatching { ExpenseSender(this).acceptSuggestion(top.amount, top.concept) }
                    .getOrElse { Result.failure(it) }
            } else {
                Result.failure(Exception("Sugerencia sin monto"))
            }
            return if (sent.isSuccess) {
                tile(messageLayout(deviceParams, "Enviado ✓", "Confírmalo en el teléfono"))
            } else {
                tile(errorLayout(deviceParams))
            }
        }

        val top = WearCache.suggestions(this).firstOrNull()
            ?: return tile(messageLayout(deviceParams, "Sin sugerencias", "Nada por registrar ahora"))

        return tile(suggestionLayout(deviceParams, top))
    }

    override suspend fun resourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ResourceBuilders.Resources =
        ResourceBuilders.Resources.Builder().setVersion(RES_VERSION).build()

    // ── Layouts ─────────────────────────────────────────────────────────────────

    private fun suggestionLayout(
        deviceParams: DeviceParameters,
        s: WearCache.Suggestion,
    ): LayoutElement {
        val header = Text.Builder(this, "RECOMENDADO")
            .setTypography(Typography.TYPOGRAPHY_CAPTION2)
            .setColor(argb(COLOR_MUTED))
            .build()

        val concept = Text.Builder(this, s.concept)
            .setTypography(Typography.TYPOGRAPHY_TITLE3)
            .setColor(argb(COLOR_ON_SURFACE))
            .setMaxLines(1)
            .build()

        val amount = Text.Builder(this, if (s.amount > 0) WearCache.money(s.amount) else "")
            .setTypography(Typography.TYPOGRAPHY_DISPLAY3)
            .setColor(argb(COLOR_PRIMARY))
            .build()

        val reason = Text.Builder(this, s.reason)
            .setTypography(Typography.TYPOGRAPHY_CAPTION2)
            .setColor(argb(COLOR_MUTED))
            .setMaxLines(2)
            .build()

        val content = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(header)
            .addContent(concept)
            .addContent(amount)
            .addContent(reason)
            .build()

        val confirmChip = Chip.Builder(this, clickable(CLICK_ACCEPT), deviceParams)
            .setChipColors(ChipColors.primaryChipColors(materialColors()))
            .setPrimaryLabelContent("Confirmar")
            .build()

        return PrimaryLayout.Builder(deviceParams)
            .setContent(content)
            .setPrimaryChipContent(confirmChip)
            .build()
    }

    /** Estado de error de envío: mensaje claro + chip "Reintentar" (mismo LoadAction). */
    private fun errorLayout(deviceParams: DeviceParameters): LayoutElement {
        val content = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(
                Text.Builder(this, "No se envió")
                    .setTypography(Typography.TYPOGRAPHY_TITLE3)
                    .setColor(argb(COLOR_ERROR))
                    .build()
            )
            .addContent(
                Text.Builder(this, "Sin conexión con el teléfono — reintenta")
                    .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                    .setColor(argb(COLOR_MUTED))
                    .setMaxLines(3)
                    .build()
            )
            .build()

        val retryChip = Chip.Builder(this, clickable(CLICK_ACCEPT), deviceParams)
            .setChipColors(ChipColors.primaryChipColors(materialColors()))
            .setPrimaryLabelContent("Reintentar")
            .build()

        return PrimaryLayout.Builder(deviceParams)
            .setContent(content)
            .setPrimaryChipContent(retryChip)
            .build()
    }

    private fun messageLayout(
        deviceParams: DeviceParameters,
        title: String,
        subtitle: String,
    ): LayoutElement {
        val content = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(
                Text.Builder(this, title)
                    .setTypography(Typography.TYPOGRAPHY_TITLE3)
                    .setColor(argb(COLOR_ON_SURFACE))
                    .build()
            )
            .addContent(
                Text.Builder(this, subtitle)
                    .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                    .setColor(argb(COLOR_MUTED))
                    .setMaxLines(2)
                    .build()
            )
            .build()
        return PrimaryLayout.Builder(deviceParams).setContent(content).build()
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private fun tile(layout: LayoutElement): TileBuilders.Tile =
        TileBuilders.Tile.Builder()
            .setResourcesVersion(RES_VERSION)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            // Refresco periódico: sin esto la tile solo se re-renderiza con el push
            // del teléfono y se queda congelada si el snapshot deja de llegar.
            .setFreshnessIntervalMillis(FRESHNESS_MS)
            .build()

    private fun clickable(id: String): ModifiersBuilders.Clickable =
        ModifiersBuilders.Clickable.Builder()
            .setId(id)
            .setOnClick(ActionBuilders.LoadAction.Builder().build())
            .build()

    private fun materialColors(): androidx.wear.protolayout.material.Colors =
        androidx.wear.protolayout.material.Colors(
            COLOR_PRIMARY, COLOR_ON_PRIMARY, COLOR_SURFACE, COLOR_ON_SURFACE,
        )

    companion object {
        private const val RES_VERSION = "1"
        private const val CLICK_ACCEPT = "accept"
        private const val FRESHNESS_MS = 30L * 60 * 1000 // 30 min

        private const val COLOR_PRIMARY = 0xFF016E3E.toInt()     // verde sembrado
        private const val COLOR_ON_PRIMARY = 0xFFFFFFFF.toInt()
        private const val COLOR_SURFACE = 0xFF000000.toInt()
        private const val COLOR_ON_SURFACE = 0xFFFFFFFF.toInt()
        private const val COLOR_MUTED = 0xFFAAAAAA.toInt()
        private const val COLOR_ERROR = 0xFFCF6679.toInt()       // alerta (fallo de envío)
    }
}
