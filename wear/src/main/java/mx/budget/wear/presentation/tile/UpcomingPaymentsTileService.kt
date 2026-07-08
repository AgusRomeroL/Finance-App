package mx.budget.wear.presentation.tile

import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
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
 * Tile — **Próximos pagos**. Lista los siguientes gastos PLANNED (concepto + monto
 * + vencimiento relativo). Lee del [WearCache]; sin Room ni red en el reloj.
 */
class UpcomingPaymentsTileService : SuspendingTileService() {

    override suspend fun tileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): TileBuilders.Tile {
        val deviceParams = requestParams.deviceConfiguration
        val upcoming = WearCache.upcoming(this).take(MAX_ROWS)

        val header = Text.Builder(this, "PRÓXIMOS PAGOS")
            .setTypography(Typography.TYPOGRAPHY_CAPTION2)
            .setColor(argb(COLOR_MUTED))
            .build()

        val column = LayoutElementBuilders.Column.Builder()
            .setWidth(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
            .addContent(header)
            .addContent(spacer(4f))

        if (upcoming.isEmpty()) {
            column.addContent(
                Text.Builder(this, "Sin pagos próximos")
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(argb(COLOR_ON_SURFACE))
                    .setMaxLines(2)
                    .build()
            )
        } else {
            val now = System.currentTimeMillis()
            upcoming.forEachIndexed { i, u ->
                if (i > 0) column.addContent(spacer(4f))
                column.addContent(row(u, now))
            }
        }

        val layout: LayoutElement = PrimaryLayout.Builder(deviceParams)
            .setContent(column.build())
            .build()

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RES_VERSION)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            .build()
    }

    override suspend fun resourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ResourceBuilders.Resources =
        ResourceBuilders.Resources.Builder().setVersion(RES_VERSION).build()

    private fun row(u: WearCache.Upcoming, now: Long): LayoutElement {
        val title = Text.Builder(this, u.concept)
            .setTypography(Typography.TYPOGRAPHY_BUTTON)
            .setColor(argb(COLOR_ON_SURFACE))
            .setMaxLines(1)
            .build()

        val sub = Text.Builder(this, "${WearCache.money(u.amount)} · ${relativeDue(u.dueDate, now)}")
            .setTypography(Typography.TYPOGRAPHY_CAPTION2)
            .setColor(argb(COLOR_MUTED))
            .setMaxLines(1)
            .build()

        return LayoutElementBuilders.Column.Builder()
            .setWidth(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
            .addContent(title)
            .addContent(sub)
            .build()
    }

    private fun spacer(h: Float): LayoutElement =
        LayoutElementBuilders.Spacer.Builder().setHeight(dp(h)).build()

    private fun relativeDue(due: Long, now: Long): String {
        if (due <= 0L) return ""
        val days = ((due - now) / DAY_MS).toInt()
        return when {
            days <= 0 -> "hoy"
            days == 1 -> "mañana"
            days < 7 -> "en ${days}d"
            else -> "en ${days / 7}sem"
        }
    }

    companion object {
        private const val RES_VERSION = "1"
        private const val MAX_ROWS = 3
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val COLOR_ON_SURFACE = 0xFFFFFFFF.toInt()
        private const val COLOR_MUTED = 0xFFAAAAAA.toInt()
    }
}
