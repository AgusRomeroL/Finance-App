package mx.budget.data.quincena

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Zona del hogar: el dia de la quincena se cuenta en Mexico, no en la del telefono. */
val HOUSEHOLD_ZONE: ZoneId = ZoneId.of("America/Mexico_City")

/**
 * Avance de una quincena: dia actual, fraccion transcurrida y dias restantes.
 *
 * Vivia como funcion privada del dashboard con la zona del sistema; salio aqui
 * (Fase 7b) porque la tarjeta Ritmo, la barra del hero, el pronostico de los
 * topes y el worker de avisos tienen que contar el mismo dia.
 *
 * - [fraction]: el dia 1 ya cuenta como 1/[totalDays].
 * - [daysRemaining]: sin contar hoy (lo que el hero escribe como "dias restantes").
 */
data class QuincenaProgress(
    val dayIndex: Int,
    val totalDays: Int,
    val fraction: Float,
    val daysRemaining: Int,
)

/**
 * Calcula el avance de la quincena entre [startDate] y [endDate] (ISO
 * `yyyy-MM-dd`) en [today]. Sin fechas validas devuelve un avance neutro de
 * quince dias en el dia 1 sin fraccion.
 */
fun quincenaProgress(
    startDate: String?,
    endDate: String?,
    today: LocalDate = LocalDate.now(HOUSEHOLD_ZONE),
): QuincenaProgress {
    val start = startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val end = endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    if (start == null || end == null) return QuincenaProgress(1, 15, 0f, 0)
    val total = (ChronoUnit.DAYS.between(start, end) + 1).toInt().coerceAtLeast(1)
    val day = (ChronoUnit.DAYS.between(start, today) + 1).toInt().coerceIn(1, total)
    val remaining = ChronoUnit.DAYS.between(today, end).toInt().coerceIn(0, total)
    return QuincenaProgress(day, total, day.toFloat() / total, remaining)
}
