package mx.budget.wear.presentation.tile

import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import com.google.android.horologist.tiles.SuspendingTileService
import mx.budget.wear.data.WearCache

/**
 * Tile — **Gasto por miembro** (rol BENEFICIARY). Mini-barras horizontales con el
 * ancho proporcional al total de cada miembro (normalizado al mayor). Lee del
 * [WearCache]; sin Room ni red en el reloj.
 */
class MemberSpendTileService : SuspendingTileService() {

    override suspend fun tileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): TileBuilders.Tile {
        val deviceParams = requestParams.deviceConfiguration
        val members = WearCache.memberSpend(this).take(MAX_ROWS)
        val maxTotal = members.maxOfOrNull { it.total }?.takeIf { it > 0.0 } ?: 1.0

        val header = Text.Builder(this, "GASTO POR MIEMBRO")
            .setTypography(Typography.TYPOGRAPHY_CAPTION2)
            .setColor(argb(COLOR_MUTED))
            .build()

        val column = LayoutElementBuilders.Column.Builder()
            .setWidth(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
            .addContent(header)
            .addContent(spacer(4f))

        if (members.isEmpty()) {
            column.addContent(
                Text.Builder(this, "Sin datos")
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(argb(COLOR_ON_SURFACE))
                    .build()
            )
        } else {
            members.forEachIndexed { i, m ->
                if (i > 0) column.addContent(spacer(4f))
                column.addContent(memberRow(m, maxTotal))
            }
        }

        val layout: LayoutElement = PrimaryLayout.Builder(deviceParams)
            .setContent(column.build())
            .build()

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RES_VERSION)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            // Refresco periódico: sin esto la tile solo se re-renderiza con el push
            // del teléfono y se queda congelada si el snapshot deja de llegar.
            .setFreshnessIntervalMillis(FRESHNESS_MS)
            .build()
    }

    override suspend fun resourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ResourceBuilders.Resources =
        ResourceBuilders.Resources.Builder().setVersion(RES_VERSION).build()

    private fun memberRow(m: WearCache.MemberSpend, maxTotal: Double): LayoutElement {
        val name = Text.Builder(this, m.name)
            .setTypography(Typography.TYPOGRAPHY_CAPTION1)
            .setColor(argb(COLOR_ON_SURFACE))
            .setMaxLines(1)
            .build()

        val amount = Text.Builder(this, WearCache.money(m.total))
            .setTypography(Typography.TYPOGRAPHY_CAPTION2)
            .setColor(argb(COLOR_MUTED))
            .setMaxLines(1)
            .build()

        val labelRow = LayoutElementBuilders.Row.Builder()
            .setWidth(expand())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(name)
            .addContent(
                LayoutElementBuilders.Spacer.Builder().setWidth(expand()).build()
            )
            .addContent(amount)
            .build()

        val fraction = (m.total / maxTotal).coerceIn(0.04, 1.0)
        val barWidth = (BAR_MAX_DP * fraction).toFloat()
        val bar = LayoutElementBuilders.Box.Builder()
            .setWidth(dp(barWidth))
            .setHeight(dp(6f))
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(COLOR_PRIMARY))
                            .setCorner(
                                ModifiersBuilders.Corner.Builder().setRadius(dp(3f)).build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        return LayoutElementBuilders.Column.Builder()
            .setWidth(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
            .addContent(labelRow)
            .addContent(spacer(2f))
            .addContent(bar)
            .build()
    }

    private fun spacer(h: Float): LayoutElement =
        LayoutElementBuilders.Spacer.Builder().setHeight(dp(h)).build()

    companion object {
        private const val RES_VERSION = "1"
        private const val FRESHNESS_MS = 30L * 60 * 1000 // 30 min
        private const val MAX_ROWS = 4
        private const val BAR_MAX_DP = 120.0
        private const val COLOR_PRIMARY = 0xFF016E3E.toInt()
        private const val COLOR_ON_SURFACE = 0xFFFFFFFF.toInt()
        private const val COLOR_MUTED = 0xFFAAAAAA.toInt()
    }
}
