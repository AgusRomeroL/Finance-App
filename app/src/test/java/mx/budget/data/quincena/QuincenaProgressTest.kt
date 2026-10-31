package mx.budget.data.quincena

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** Avance de la quincena que comparten Ritmo, el hero, el pronostico y los avisos. */
class QuincenaProgressTest {

    @Test
    fun `el primer dia ya cuenta`() {
        val p = quincenaProgress("2026-10-16", "2026-10-31", today = LocalDate.of(2026, 10, 16))
        assertEquals(1, p.dayIndex)
        assertEquals(16, p.totalDays)
        assertEquals(1f / 16, p.fraction, 1e-6f)
        assertEquals(15, p.daysRemaining)
    }

    @Test
    fun `el ultimo dia no quedan dias restantes`() {
        val p = quincenaProgress("2026-10-16", "2026-10-31", today = LocalDate.of(2026, 10, 31))
        assertEquals(16, p.dayIndex)
        assertEquals(1f, p.fraction, 1e-6f)
        assertEquals(0, p.daysRemaining)
    }

    @Test
    fun `fuera de la quincena se acota a sus bordes`() {
        val antes = quincenaProgress("2026-11-01", "2026-11-15", today = LocalDate.of(2026, 10, 31))
        assertEquals(1, antes.dayIndex)
        assertEquals(15, antes.daysRemaining)
        val despues = quincenaProgress("2026-10-01", "2026-10-15", today = LocalDate.of(2026, 10, 31))
        assertEquals(15, despues.dayIndex)
        assertEquals(0, despues.daysRemaining)
    }

    @Test
    fun `sin fechas validas devuelve un avance neutro`() {
        val p = quincenaProgress(null, "x")
        assertEquals(QuincenaProgress(1, 15, 0f, 0), p)
    }
}
