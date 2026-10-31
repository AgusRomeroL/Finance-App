package mx.budget.data.quincena

import mx.budget.testing.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Arrastre de la quincena inmediata anterior (Fase 7b, decision 14): solo si
 * esta cerrada, sin encadenar y con el deficit incluido.
 */
class CarryOverTest {

    @Test
    fun `sin quincena anterior no hay arrastre`() {
        assertNull(carryOverFrom(null))
    }

    @Test
    fun `una anterior cerrada deja su sobrante`() {
        val prev = Fixtures.quincena(2026, 10, "FIRST", projectedIncome = 75000.0, status = "CLOSED",
            actualIncome = 75000.0, actualExpenses = 72575.2)
        val c = carryOverFrom(prev)!!
        assertFalse(c.pendingClose)
        assertEquals(2424.8, c.amount, 1e-9)
        assertEquals(2424.8, c.effective, 1e-9)
        assertEquals(prev.id, c.fromQuincenaId)
    }

    @Test
    fun `el deficit tambien se arrastra`() {
        val prev = Fixtures.quincena(2026, 9, "SECOND", projectedIncome = 60000.0, status = "CLOSED",
            actualIncome = 60000.0, actualExpenses = 61578.2)
        assertEquals(-1578.2, carryOverFrom(prev)!!.effective, 1e-9)
    }

    @Test
    fun `manda lo recibido si supero lo proyectado`() {
        val prev = Fixtures.quincena(2026, 9, "SECOND", projectedIncome = 20000.0, status = "CLOSED",
            actualIncome = 23000.0, actualExpenses = 21000.0)
        assertEquals(2000.0, carryOverFrom(prev)!!.effective, 1e-9)
    }

    @Test
    fun `una anterior pendiente de cierre no arrastra hasta cerrarse`() {
        val prev = Fixtures.quincena(2026, 10, "FIRST", projectedIncome = 75000.0, status = "CLOSING_REVIEW",
            actualIncome = 75000.0, actualExpenses = 10000.0)
        val c = carryOverFrom(prev)!!
        assertTrue(c.pendingClose)
        assertEquals(0.0, c.effective, 0.0)
    }

    @Test
    fun `la fecha anterior es el dia previo al inicio, tambien entre meses`() {
        assertEquals("2026-10-15", previousQuincenaDate(Fixtures.quincena(2026, 10, "SECOND")))
        assertEquals("2026-09-30", previousQuincenaDate(Fixtures.quincena(2026, 10, "FIRST")))
        assertEquals("2025-12-31", previousQuincenaDate(Fixtures.quincena(2026, 1, "FIRST")))
    }
}
