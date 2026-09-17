package mx.budget.data.recurrence

import mx.budget.testing.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Detector de recurrencia: agrupa el historial POSTED por clave canonica, mide
 * la regularidad de fechas y montos, y solo propone lo que supera 0.55.
 */
class RecurrenceDetectorTest {

    private val detector = RecurrenceDetector()

    private fun monthly(day: Int, months: Int, amount: (Int) -> Double = { 189.0 }, canonical: String = "netflix") =
        (0 until months).map { i ->
            Fixtures.expense(
                id = "$canonical-$i",
                date = LocalDate.of(2026, 1, day).plusMonths(i.toLong()),
                amount = amount(i),
                canonical = canonical,
                concept = if (i == 0) "NETFLIX.COM" else "Netflix",
            )
        }

    @Test
    fun `seis cargos mensuales iguales dan una plantilla mensual con alta confianza`() {
        val out = detector.detect(monthly(day = 5, months = 6))
        val s = out.single()
        assertEquals("netflix", s.canonicalKey)
        assertEquals("MONTHLY_SPECIFIC_HALF", s.cadence)
        assertEquals(5, s.dayOfMonth)
        assertEquals(6, s.occurrences)
        assertEquals(189.0, s.amountMxn, 0.0)
        assertEquals("cat-1", s.categoryId)
        assertEquals("w-1", s.paymentMethodId)
        assertTrue("confianza ${s.confidence}", s.confidence > 0.9)
        assertEquals(6, s.learnedFromExpenseIds.size)
        assertTrue(s.reason.contains("cada mes"))
        assertTrue(s.reason.contains("6 registros"))
    }

    @Test
    fun `el concepto sugerido es el mas frecuente, no el primero`() {
        assertEquals("Netflix", detector.detect(monthly(5, 4)).single().concept)
    }

    @Test
    fun `menos de tres cargos no proponen nada`() {
        assertTrue(detector.detect(monthly(5, 2)).isEmpty())
    }

    @Test
    fun `los planeados y los que no tienen clave canonica no cuentan`() {
        val planned = monthly(5, 6).map { it.copy(status = "PLANNED") }
        assertTrue(detector.detect(planned).isEmpty())
        val sinClave = monthly(5, 6).map { it.copy(conceptCanonical = null) }
        assertTrue(detector.detect(sinClave).isEmpty())
    }

    @Test
    fun `dos cargos el mismo dia cuentan como una sola ocurrencia`() {
        val rows = monthly(5, 2) + monthly(5, 2).map { it.copy(id = it.id + "-dup") }
        assertTrue(detector.detect(rows).isEmpty())
    }

    @Test
    fun `montos erraticos con pocas ocurrencias caen bajo el umbral`() {
        val rows = monthly(5, 3, amount = { listOf(10.0, 1000.0, 20.0)[it] })
        assertTrue(detector.detect(rows).isEmpty())
    }

    @Test
    fun `el monto sugerido es la mediana`() {
        val rows = monthly(5, 5, amount = { listOf(200.0, 210.0, 205.0, 215.0, 190.0)[it] })
        assertEquals(205.0, detector.detect(rows).single().amountMxn, 0.0)
    }

    @Test
    fun `cargos los dias 1 y 16 dan cadencia quincenal doble`() {
        val rows = (0 until 3).flatMap { i ->
            listOf(
                Fixtures.expense("a$i", LocalDate.of(2026, 1, 1).plusMonths(i.toLong()), 500.0, canonical = "renta"),
                Fixtures.expense("b$i", LocalDate.of(2026, 1, 16).plusMonths(i.toLong()), 500.0, canonical = "renta"),
            )
        }
        val s = detector.detect(rows).single()
        assertEquals("QUINCENAL_EVERY", s.cadence)
        assertTrue(s.reason.contains("dos veces al mes"))
    }

    @Test
    fun `cargos cada dos meses dan cadencia bimestral`() {
        val rows = (0 until 4).map { i ->
            Fixtures.expense("g$i", LocalDate.of(2026, 1, 12).plusMonths(2L * i), 800.0, canonical = "gas")
        }
        val s = detector.detect(rows).single()
        assertEquals("BIMONTHLY", s.cadence)
        assertEquals(12, s.dayOfMonth)
    }

    @Test
    fun `un intervalo que no encaja en ninguna cadencia no propone`() {
        val rows = (0 until 4).map { i ->
            Fixtures.expense("w$i", LocalDate.of(2026, 1, 1).plusWeeks(i.toLong()), 100.0, canonical = "semanal")
        }
        assertTrue(detector.detect(rows).isEmpty())
    }

    @Test
    fun `las sugerencias salen ordenadas por confianza`() {
        val rows = monthly(5, 6, canonical = "netflix") +
            monthly(20, 3, amount = { listOf(100.0, 130.0, 90.0)[it] }, canonical = "spotify")
        val out = detector.detect(rows)
        assertEquals(listOf("netflix", "spotify"), out.map { it.canonicalKey })
        assertTrue(out[0].confidence >= out[1].confidence)
    }
}
