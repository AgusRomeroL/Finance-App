package mx.budget.wear.data

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Cuándo vence un pago, dicho en días de calendario.
 *
 * El tile de próximos pagos y la complication tenían cada uno su copia, y las
 * dos restaban milisegundos y dividían entre un día. Eso cuenta días
 * transcurridos, no días de calendario, y **subestima siempre**: un pago que
 * vence mañana a medianoche, mirado a las tres de la tarde, son nueve horas de
 * diferencia, o sea cero días, así que el reloj lo anunciaba como "hoy". Un
 * aviso de pago que adelanta la fecha un día es peor que no tenerlo.
 *
 * Aquí se comparan fechas en la zona del dispositivo, con lo que "mañana"
 * significa mañana durante todo el día de hoy.
 */
internal object DueLabels {

    /** Etiqueta corta, para el título de una complication o el pie de un tile. */
    fun short(dueEpochMs: Long, nowEpochMs: Long = System.currentTimeMillis()): String {
        if (dueEpochMs <= 0L) return ""
        val dias = dias(dueEpochMs, nowEpochMs)
        return when {
            dias <= 0 -> "hoy"
            dias == 1 -> "mañana"
            dias < 7 -> "en ${dias}d"
            else -> "en ${dias / 7}sem"
        }
    }

    /** Versión hablada, para el `contentDescription` que lee un lector de pantalla. */
    fun spoken(dueEpochMs: Long, nowEpochMs: Long = System.currentTimeMillis()): String {
        if (dueEpochMs <= 0L) return ""
        val dias = dias(dueEpochMs, nowEpochMs)
        return when {
            dias <= 0 -> "hoy"
            dias == 1 -> "mañana"
            dias < 7 -> "en $dias días"
            dias < 14 -> "en una semana"
            else -> "en ${dias / 7} semanas"
        }
    }

    private fun dias(dueEpochMs: Long, nowEpochMs: Long): Int {
        val zona = ZoneId.systemDefault()
        val hoy = Instant.ofEpochMilli(nowEpochMs).atZone(zona).toLocalDate()
        val vence = Instant.ofEpochMilli(dueEpochMs).atZone(zona).toLocalDate()
        return ChronoUnit.DAYS.between(hoy, vence).toInt()
    }
}
