package mx.budget.wear.presentation.complication

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import mx.budget.wear.data.SyncStatus
import mx.budget.wear.data.WearCache

/**
 * Complication **Disponible** (spec §4.3), en `RANGED_VALUE` con `SHORT_TEXT` de
 * respaldo para los huecos que no admiten arco.
 *
 * El anillo se llena con lo que YA no está disponible (lo pagado más lo
 * reservado), igual que el arco del tile y que el anillo del dashboard del
 * teléfono, que muestra el porcentaje gastado. En toda la app el círculo se
 * llena al consumir; hacerlo al revés aquí sería más bonito en abstracto y
 * peor en conjunto.
 *
 * Lee del [WearCache], que puebla el push del teléfono: el reloj no consulta
 * Room ni red, tampoco desde una carátula.
 */
class DisponibleComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val balance = WearCache.balance(this)
        val total = WearCache.budgetTotal(this)
        val hasData = WearCache.hasData(this)
        val stale = SyncStatus.current(this) == SyncStatus.ANTIGUO

        // Solo "sin datos" y "antiguo" cambian lo que se pinta. Estar sin
        // teléfono con un dato fresco no se anuncia en la carátula: el número
        // sigue siendo bueno, y diagnosticar la conexión desde una esfera es
        // ruido. Ese aviso vive en el hub, donde la persona ya está mirando.
        val text = if (hasData) WearCache.moneyCompact(balance) else "$--"
        val title = if (stale) "Viejo" else "Saldo"
        val description = when {
            !hasData -> "Todavía sin datos del teléfono"
            stale -> "Disponible ${WearCache.money(balance)}, dato de ${SyncStatus.ageLabel(this)}"
            else -> "Disponible de la quincena: ${WearCache.money(balance)}"
        }

        return when (request.complicationType) {
            ComplicationType.RANGED_VALUE -> ranged(
                // Consumido: lo que ya se fue del ingreso. Con sobregiro el
                // coerce lo deja lleno, que es exactamente lo que hay que ver.
                consumed = if (total > 0.0) (total - balance).coerceIn(0.0, total) else 0.0,
                // Sin quincena activa no se divide entre cero: anillo vacío y la
                // cifra real al lado.
                max = if (total > 0.0) total else 1.0,
                text = text,
                title = title,
                description = description,
            )
            ComplicationType.SHORT_TEXT -> short(text, title, description)
            else -> null
        }
    }

    /**
     * Datos fijos, sin tocar el cache: el editor de carátulas la pide en frío, a
     * veces antes de que exista ningún snapshot, y una vista previa vacía hace
     * que la complication parezca rota en el selector.
     */
    override fun getPreviewData(type: ComplicationType): ComplicationData? = when (type) {
        ComplicationType.RANGED_VALUE -> ranged(
            consumed = PREVIEW_TOTAL - PREVIEW_BALANCE,
            max = PREVIEW_TOTAL,
            text = "$8.2k",
            title = "Saldo",
            description = "Disponible de la quincena: $8,150",
            tap = false,
        )
        ComplicationType.SHORT_TEXT -> short(
            "$8.2k", "Saldo", "Disponible de la quincena: $8,150", tap = false,
        )
        else -> null
    }

    private fun ranged(
        consumed: Double,
        max: Double,
        text: String,
        title: String,
        description: String,
        tap: Boolean = true,
    ): RangedValueComplicationData = RangedValueComplicationData.Builder(
        value = consumed.toFloat(),
        min = 0f,
        max = max.toFloat(),
        contentDescription = plain(description),
    )
        .setText(plain(text))
        .setTitle(plain(title))
        .apply { if (tap) setTapAction(hubTapAction(REQ_DISPONIBLE)) }
        .build()

    private fun short(
        text: String,
        title: String,
        description: String,
        tap: Boolean = true,
    ): ShortTextComplicationData = ShortTextComplicationData.Builder(
        text = plain(text),
        contentDescription = plain(description),
    )
        .setTitle(plain(title))
        .apply { if (tap) setTapAction(hubTapAction(REQ_DISPONIBLE)) }
        .build()

    private companion object {
        const val PREVIEW_BALANCE = 8_150.0
        const val PREVIEW_TOTAL = 24_000.0
    }
}
