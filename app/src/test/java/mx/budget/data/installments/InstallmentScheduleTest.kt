package mx.budget.data.installments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Calendario de cuotas MSI: cada cuota cae un mes despues de la anterior, en el
 * dia de cargo de la tarjeta si se conoce, con recorte al ultimo dia del mes.
 */
class InstallmentScheduleTest {

    private val jan31 = LocalDate.of(2026, 1, 31)

    @Test
    fun `sin dia de cargo, cada cuota cae un mes despues y febrero se recorta`() {
        val s = InstallmentSchedule.schedule(jan31, 3, 100.0, dueDay = null)
        assertEquals(listOf(1, 2, 3), s.map { it.number })
        assertEquals(
            listOf("2026-01-31", "2026-02-28", "2026-03-31"),
            s.map { it.date.toString() },
        )
        assertTrue(s.all { it.amountMxn == 100.0 })
    }

    @Test
    fun `con dia de cargo, la cuota usa ese dia y no el de la compra`() {
        val s = InstallmentSchedule.schedule(jan31, 3, 100.0, dueDay = 15)
        assertEquals(
            listOf("2026-01-15", "2026-02-15", "2026-03-15"),
            s.map { it.date.toString() },
        )
    }

    @Test
    fun `dia de cargo 31 se recorta en los meses cortos`() {
        val s = InstallmentSchedule.schedule(LocalDate.of(2026, 1, 10), 4, 50.0, dueDay = 31)
        assertEquals(
            listOf("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30"),
            s.map { it.date.toString() },
        )
    }

    @Test
    fun `dia de cargo fuera de rango se acota a 1 a 31`() {
        val s = InstallmentSchedule.schedule(jan31, 1, 50.0, dueDay = 99)
        assertEquals("2026-01-31", s.single().date.toString())
        val z = InstallmentSchedule.schedule(jan31, 1, 50.0, dueDay = 0)
        assertEquals("2026-01-01", z.single().date.toString())
    }

    @Test
    fun `un plan sin cuotas produce un calendario vacio`() {
        assertTrue(InstallmentSchedule.schedule(jan31, 0, 100.0, null).isEmpty())
        assertTrue(InstallmentSchedule.schedule(jan31, -3, 100.0, null).isEmpty())
        assertNull(InstallmentSchedule.estimatedEndDate(jan31, 0, null))
    }

    @Test
    fun `remaining devuelve solo las cuotas no pagadas`() {
        val r = InstallmentSchedule.remaining(jan31, 4, currentInstallment = 2, installmentAmountMxn = 1.0, dueDay = null)
        assertEquals(listOf(3, 4), r.map { it.number })
    }

    @Test
    fun `remaining tolera un contador negativo`() {
        val r = InstallmentSchedule.remaining(jan31, 2, currentInstallment = -5, installmentAmountMxn = 1.0, dueDay = null)
        assertEquals(listOf(1, 2), r.map { it.number })
    }

    @Test
    fun `remainingCount se acota entre cero y el total`() {
        assertEquals(2, InstallmentSchedule.remainingCount(5, 3))
        assertEquals(0, InstallmentSchedule.remainingCount(5, 9))
        assertEquals(5, InstallmentSchedule.remainingCount(5, -1))
    }

    @Test
    fun `nextChargeDate es la primera cuota no pagada o null al terminar`() {
        assertEquals(LocalDate.of(2026, 3, 15), InstallmentSchedule.nextChargeDate(jan31, 3, 2, dueDay = 15))
        assertNull(InstallmentSchedule.nextChargeDate(jan31, 3, 3, dueDay = 15))
    }

    @Test
    fun `estimatedEndDate es la ultima cuota`() {
        assertEquals(LocalDate.of(2026, 12, 31), InstallmentSchedule.estimatedEndDate(jan31, 12, null))
    }

    @Test
    fun `parseIso toma los primeros diez caracteres y rechaza lo que no es fecha`() {
        assertEquals(LocalDate.of(2026, 9, 17), InstallmentSchedule.parseIso("2026-09-17T10:00:00"))
        assertEquals(LocalDate.of(2026, 9, 17), InstallmentSchedule.parseIso("2026-09-17"))
        assertNull(InstallmentSchedule.parseIso("2026-9-17"))
        assertNull(InstallmentSchedule.parseIso("no es fecha"))
        assertNull(InstallmentSchedule.parseIso(null))
    }
}
