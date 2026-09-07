package mx.budget.wear.presentation.tile

import android.content.Context
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import mx.budget.wear.data.SyncStatus

/**
 * Pie de estado compartido por los cuatro tiles que enseñan datos.
 *
 * La regla que impone la pantalla redonda: no se añaden elementos nuevos al
 * layout. Ya se aprendió por las malas que tres chips más encabezado no caben,
 * así que esto ocupa el `secondaryLabel`, que es un hueco de primera clase que
 * los layouts de ProtoLayout ya reservan.
 *
 * Devuelve `null` cuando todo está en orden, y entonces el tile no pinta nada.
 */
internal object TileStatus {

    private const val COLOR_ALERT = 0xFFCF6679.toInt()
    private const val COLOR_MUTED = 0xFFAAAAAA.toInt()

    /** El pie, o `null` si el dato es fresco y hay teléfono. */
    fun element(context: Context): LayoutElement? {
        val status = SyncStatus.current(context)
        if (status == SyncStatus.OK) return null
        val label = when (status) {
            SyncStatus.DESCONECTADO -> "Sin teléfono cerca"
            SyncStatus.NUNCA -> "Todavía sin datos"
            // Más corto que en el hub: aquí compite con el contenido del tile.
            SyncStatus.ANTIGUO -> SyncStatus.ageLabel(context).replaceFirstChar { it.uppercase() }
            SyncStatus.OK -> return null
        }
        val color = if (status == SyncStatus.DESCONECTADO) COLOR_ALERT else COLOR_MUTED
        return Text.Builder(context, label)
            .setTypography(Typography.TYPOGRAPHY_CAPTION3)
            .setColor(argb(color))
            .setMaxLines(1)
            .build()
    }

    /** Si hay pie, hay que dejarle sitio quitando una fila de contenido. */
    fun rowsFor(context: Context, maxRows: Int): Int =
        if (SyncStatus.current(context) == SyncStatus.OK) maxRows else (maxRows - 1).coerceAtLeast(1)
}
