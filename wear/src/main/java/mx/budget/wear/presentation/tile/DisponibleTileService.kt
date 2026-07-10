package mx.budget.wear.presentation.tile

import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.CircularProgressIndicator
import androidx.wear.protolayout.material.ProgressIndicatorColors
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.EdgeContentLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import com.google.android.horologist.tiles.SuspendingTileService
import mx.budget.wear.data.WearCache

/**
 * Tile — **Disponible** (héroe). Un arco de borde ([EdgeContentLayout] +
 * [CircularProgressIndicator]) muestra qué fracción del presupuesto de la quincena
 * ya se consumió; al centro, la cifra "Disponible" y la etiqueta de la quincena.
 * Todo se lee del [WearCache] (SharedPreferences); el reloj no consulta Room ni red.
 */
class DisponibleTileService : SuspendingTileService() {

    override suspend fun tileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): TileBuilders.Tile {
        val deviceParams = requestParams.deviceConfiguration

        val balance = WearCache.balance(this)
        val budgetTotal = WearCache.budgetTotal(this)
        val label = WearCache.label(this)

        // Fracción del presupuesto consumida (gastado / total), acotada a [0,1].
        val spent = budgetTotal - balance
        val fraction = (if (budgetTotal > 0.0) spent / budgetTotal else 0.0)
            .coerceIn(0.0, 1.0)
            .toFloat()

        // Sobregiro (saldo negativo) → arco de alerta; en caja → verde.
        val arcColor = if (balance < 0.0) COLOR_ERROR else COLOR_PRIMARY

        val arc = CircularProgressIndicator.Builder()
            .setProgress(fraction)
            .setCircularProgressIndicatorColors(ProgressIndicatorColors(arcColor, COLOR_TRACK))
            .build()

        val amount = Text.Builder(this, WearCache.money(balance))
            .setTypography(Typography.TYPOGRAPHY_DISPLAY2)
            .setColor(argb(if (balance < 0.0) COLOR_ERROR else COLOR_ON_SURFACE))
            .setMaxLines(1)
            .build()

        val heading = Text.Builder(this, "Disponible")
            .setTypography(Typography.TYPOGRAPHY_CAPTION2)
            .setColor(argb(COLOR_MUTED))
            .build()

        val caption = Text.Builder(this, label)
            .setTypography(Typography.TYPOGRAPHY_CAPTION2)
            .setColor(argb(COLOR_MUTED))
            .setMaxLines(1)
            .build()

        val layout: LayoutElement = EdgeContentLayout.Builder(deviceParams)
            .setEdgeContent(arc)
            .setPrimaryLabelTextContent(heading)
            .setContent(amount)
            .setSecondaryLabelTextContent(caption)
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

    companion object {
        private const val RES_VERSION = "1"
        private const val FRESHNESS_MS = 30L * 60 * 1000 // 30 min
        private const val COLOR_PRIMARY = 0xFF016E3E.toInt()     // verde sembrado
        private const val COLOR_ERROR = 0xFFCF6679.toInt()       // alerta (sobregiro)
        private const val COLOR_TRACK = 0xFF2A2A2A.toInt()       // pista sin llenar
        private const val COLOR_ON_SURFACE = 0xFFFFFFFF.toInt()
        private const val COLOR_MUTED = 0xFFAAAAAA.toInt()
    }
}
