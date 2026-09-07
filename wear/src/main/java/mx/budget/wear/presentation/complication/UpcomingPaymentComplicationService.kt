package mx.budget.wear.presentation.complication

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import mx.budget.wear.data.SyncStatus
import mx.budget.wear.data.WearCache

/**
 * Complication **Próximo pago** (spec §4.3), en `SHORT_TEXT`: el monto del
 * siguiente gasto PLANNED y cuándo vence.
 *
 * A diferencia del saldo, aquí un snapshot viejo NO cambia el título. La fecha
 * de vencimiento es absoluta y la etiqueta relativa se recalcula contra la hora
 * del reloj en cada dibujo, así que "en 3 días" sigue siendo cierto aunque el
 * dato tenga horas. Lo único que puede fallar es que aparezca un pago ya
 * liquidado, que es un error menor. La vejez se dice en la descripción para
 * quien la escuche con TalkBack, y no se roba el título con un aviso que aquí
 * no corresponde.
 */
class UpcomingPaymentComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (request.complicationType != ComplicationType.SHORT_TEXT) return null

        val hasData = WearCache.hasData(this)
        val next = WearCache.upcoming(this).minByOrNull { it.dueDate }
        val age = SyncStatus.ageLabel(this)
        val stale = SyncStatus.current(this) == SyncStatus.ANTIGUO
        val suffix = if (stale && age.isNotEmpty()) ", dato de $age" else ""

        return when {
            !hasData -> short("--", "Pagos", "Todavía sin datos del teléfono")
            next == null -> short("Sin", "Pagos", "Sin pagos próximos$suffix")
            else -> short(
                text = WearCache.moneyCompact(next.amount),
                title = relativeDue(next.dueDate, System.currentTimeMillis()),
                description = "Próximo pago: ${next.concept}, " +
                    "${WearCache.money(next.amount)}, ${relativeDueLong(next.dueDate, System.currentTimeMillis())}$suffix",
            )
        }
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        if (type == ComplicationType.SHORT_TEXT) {
            short("$1.5k", "hoy", "Próximo pago: Sears, $1,483, hoy", tap = false)
        } else {
            null
        }

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
        .apply { if (tap) setTapAction(hubTapAction(REQ_PROXIMO_PAGO)) }
        .build()

    /**
     * Etiqueta corta para el título, que da unos siete caracteres. Es un calco
     * deliberado de la del tile de próximos pagos: extraerla a la capa de cache
     * para compartirla metería formato de interfaz donde no toca, y son cinco
     * líneas. Si alguna cambia, cambian las dos.
     */
    private fun relativeDue(due: Long, now: Long): String {
        val days = ((due - now) / DAY_MS).toInt()
        return when {
            days <= 0 -> "hoy"
            days == 1 -> "mañana"
            days < 7 -> "en ${days}d"
            else -> "en ${days / 7}sem"
        }
    }

    /** Versión hablada, para el `contentDescription` que lee TalkBack. */
    private fun relativeDueLong(due: Long, now: Long): String {
        val days = ((due - now) / DAY_MS).toInt()
        return when {
            days <= 0 -> "hoy"
            days == 1 -> "mañana"
            days < 7 -> "en $days días"
            else -> "en ${days / 7} semanas"
        }
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
