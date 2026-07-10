package mx.budget.wear.presentation.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.dp
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
import mx.budget.wear.presentation.CapturaActivity

/**
 * Tile B — **Captura**. Dos accesos (Gasto, Ingreso) que lanzan la
 * [CapturaActivity] ligera pasando el modo por extra. Un tile no admite texto
 * libre NI scroll, así que el teclado/voz (incluido el dictado) viven en la
 * actividad; el tile es solo el disparo glanceable. ProtoLayout (estable, sin
 * Compose) → sin el jank del tile Glance.
 */
class QuickEntryTileService : SuspendingTileService() {

    override suspend fun tileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): TileBuilders.Tile {
        val deviceParams = requestParams.deviceConfiguration

        val header = Text.Builder(this, "CAPTURAR")
            .setTypography(Typography.TYPOGRAPHY_CAPTION2)
            .setColor(argb(COLOR_MUTED))
            .build()

        val content = LayoutElementBuilders.Column.Builder()
            .setWidth(androidx.wear.protolayout.DimensionBuilders.expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(header)
            .addContent(spacer())
            // Solo 2 chips: un tile ProtoLayout NO scrollea, y 3 chips + header no
            // caben en un reloj redondo (el 3º quedaba recortado e inalcanzable).
            // "Dictar" (voz) vive ahora dentro de CapturaActivity/CapturaScreen (mic).
            .addContent(actionChip(deviceParams, "Gasto", CapturaActivity.MODE_EXPENSE))
            .addContent(spacer())
            .addContent(actionChip(deviceParams, "Ingreso", CapturaActivity.MODE_INCOME))
            .build()

        val layout = PrimaryLayout.Builder(deviceParams).setContent(content).build()

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RES_VERSION)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            // Refresco periódico (contenido estático, pero mantiene el contrato de
            // frescura consistente entre los 6 tiles).
            .setFreshnessIntervalMillis(FRESHNESS_MS)
            .build()
    }

    override suspend fun resourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ResourceBuilders.Resources =
        ResourceBuilders.Resources.Builder().setVersion(RES_VERSION).build()

    private fun actionChip(deviceParams: DeviceParameters, label: String, mode: String): LayoutElement =
        Chip.Builder(this, launchClickable(mode), deviceParams)
            .setChipColors(ChipColors.secondaryChipColors(materialColors()))
            .setPrimaryLabelContent(label)
            .build()

    private fun spacer(): LayoutElement =
        LayoutElementBuilders.Spacer.Builder().setHeight(dp(6f)).build()

    private fun launchClickable(mode: String): ModifiersBuilders.Clickable =
        ModifiersBuilders.Clickable.Builder()
            .setId("capture_$mode")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(CapturaActivity::class.java.name)
                            .addKeyToExtraMapping(
                                CapturaActivity.EXTRA_MODE,
                                ActionBuilders.AndroidStringExtra.Builder().setValue(mode).build(),
                            )
                            .build()
                    )
                    .build()
            )
            .build()

    private fun materialColors(): androidx.wear.protolayout.material.Colors =
        androidx.wear.protolayout.material.Colors(
            COLOR_PRIMARY, COLOR_ON_PRIMARY, COLOR_SURFACE, COLOR_ON_SURFACE,
        )

    companion object {
        private const val RES_VERSION = "1"
        private const val FRESHNESS_MS = 30L * 60 * 1000 // 30 min
        private const val COLOR_PRIMARY = 0xFF016E3E.toInt()
        private const val COLOR_ON_PRIMARY = 0xFFFFFFFF.toInt()
        private const val COLOR_SURFACE = 0xFF1C1C1E.toInt()
        private const val COLOR_ON_SURFACE = 0xFFFFFFFF.toInt()
        private const val COLOR_MUTED = 0xFFAAAAAA.toInt()
    }
}
