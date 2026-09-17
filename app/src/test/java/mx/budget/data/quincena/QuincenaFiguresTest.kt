package mx.budget.data.quincena

import mx.budget.testing.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Convencion de cifras compartida por dashboard, resumen y asistente: ingreso =
 * maximo entre proyectado y recibido, gastado = ejecutado, reservado =
 * prorrateado, disponible = la resta.
 */
class QuincenaFiguresTest {

    @Test
    fun `el ingreso es el proyectado mientras lo recibido no lo supere`() {
        val f = QuincenaFigures(projectedIncome = 20000.0, receivedIncome = 15000.0, spent = 0.0, reserved = 0.0, projectedExpenses = 0.0)
        assertEquals(20000.0, f.income, 0.0)
    }

    @Test
    fun `en cuanto entra mas dinero del planeado manda lo recibido`() {
        val f = QuincenaFigures(projectedIncome = 20000.0, receivedIncome = 23000.0, spent = 0.0, reserved = 0.0, projectedExpenses = 0.0)
        assertEquals(23000.0, f.income, 0.0)
    }

    @Test
    fun `disponible es ingreso menos gastado menos reservado y puede ser negativo`() {
        val f = QuincenaFigures(projectedIncome = 10000.0, receivedIncome = 0.0, spent = 7000.0, reserved = 2500.0, projectedExpenses = 0.0)
        assertEquals(500.0, f.available, 0.0)
        val over = f.copy(spent = 9000.0)
        assertEquals(-1500.0, over.available, 0.0)
    }

    @Test
    fun `la ejecucion se redondea al entero y es cero sin presupuesto`() {
        assertEquals(50, QuincenaFigures(0.0, 0.0, spent = 500.0, reserved = 0.0, projectedExpenses = 1000.0).executionPct)
        assertEquals(67, QuincenaFigures(0.0, 0.0, spent = 2.0, reserved = 0.0, projectedExpenses = 3.0).executionPct)
        assertEquals(0, QuincenaFigures(0.0, 0.0, spent = 500.0, reserved = 0.0, projectedExpenses = 0.0).executionPct)
        assertEquals(150, QuincenaFigures(0.0, 0.0, spent = 1500.0, reserved = 0.0, projectedExpenses = 1000.0).executionPct)
    }

    @Test
    fun `quincenaFigures cae al presupuesto por categoria cuando la columna esta en cero`() {
        val q = Fixtures.quincena(2026, 9, "FIRST", projectedIncome = 18000.0, projectedExpenses = 0.0)
        val f = quincenaFigures(q, receivedIncome = 0.0, spent = 100.0, reserved = 50.0, projectedExpensesFallback = 12000.0)
        assertEquals(12000.0, f.projectedExpenses, 0.0)
        assertEquals(18000.0, f.projectedIncome, 0.0)
        assertEquals(17850.0, f.available, 0.0)
    }

    @Test
    fun `quincenaFigures respeta la columna cuando trae presupuesto`() {
        val q = Fixtures.quincena(2026, 9, "FIRST", projectedIncome = 18000.0, projectedExpenses = 9000.0)
        val f = quincenaFigures(q, receivedIncome = 0.0, spent = 0.0, reserved = 0.0, projectedExpensesFallback = 12000.0)
        assertEquals(9000.0, f.projectedExpenses, 0.0)
    }
}
