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
import androidx.wear.protolayout.material.Colors
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import com.google.android.horologist.tiles.SuspendingTileService
import mx.budget.wear.data.WearCache

/**
 * Tile — **Pendientes**. Cuenta cuántas capturas quedan por confirmar en la bandeja
 * y ofrece un chip que abre el hub del reloj ([mx.budget.wear.MainActivity]) para
 * revisarlas. Lee del [WearCache]; sin Room ni red en el reloj.
 */
class PendingConfirmTileService : SuspendingTileService() {

    override suspend fun tileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): TileBuilders.Tile {
        val deviceParams = requestParams.deviceConfiguration
        val count = WearCache.pending(this).size

        val content = if (count == 0) {
            LayoutElementBuilders.Column.Builder()
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(
                    Text.Builder(this, "Todo al día")
                        .setTypography(Typography.TYPOGRAPHY_TITLE2)
                        .setColor(argb(COLOR_ON_SURFACE))
                        .build()
                )
                .addContent(
                    Text.Builder(this, "Sin pendientes por confirmar")
                        .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                        .setColor(argb(COLOR_MUTED))
                        .setMaxLines(2)
                        .build()
                )
                .build()
        } else {
            LayoutElementBuilders.Column.Builder()
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(
                    Text.Builder(this, count.toString())
                        .setTypography(Typography.TYPOGRAPHY_DISPLAY1)
                        .setColor(argb(COLOR_PRIMARY))
                        .build()
                )
                .addContent(
                    Text.Builder(this, "Pendientes por confirmar")
                        .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                        .setColor(argb(COLOR_MUTED))
                        .setMaxLines(2)
                        .build()
                )
                .build()
        }

        val builder = PrimaryLayout.Builder(deviceParams).setContent(content)
        if (count > 0) {
            builder.setPrimaryChipContent(reviewChip(deviceParams))
        }
        val layout: LayoutElement = builder.build()

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RES_VERSION)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            .build()
    }

    override suspend fun resourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ResourceBuilders.Resources =
        ResourceBuilders.Resources.Builder().setVersion(RES_VERSION).build()

    private fun reviewChip(deviceParams: DeviceParameters): LayoutElement =
        Chip.Builder(this, launchHubClickable(), deviceParams)
            .setChipColors(ChipColors.primaryChipColors(materialColors()))
            .setPrimaryLabelContent("Revisar")
            .build()

    private fun launchHubClickable(): ModifiersBuilders.Clickable =
        ModifiersBuilders.Clickable.Builder()
            .setId("open_hub")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(HUB_ACTIVITY)
                            .build()
                    )
                    .build()
            )
            .build()

    private fun materialColors(): Colors =
        Colors(COLOR_PRIMARY, COLOR_ON_PRIMARY, COLOR_SURFACE, COLOR_ON_SURFACE)

    companion object {
        private const val RES_VERSION = "1"
        private const val HUB_ACTIVITY = "mx.budget.wear.MainActivity"
        private const val COLOR_PRIMARY = 0xFF016E3E.toInt()
        private const val COLOR_ON_PRIMARY = 0xFFFFFFFF.toInt()
        private const val COLOR_SURFACE = 0xFF000000.toInt()
        private const val COLOR_ON_SURFACE = 0xFFFFFFFF.toInt()
        private const val COLOR_MUTED = 0xFFAAAAAA.toInt()
    }
}
