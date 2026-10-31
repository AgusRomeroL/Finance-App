package mx.budget.data.quincena

import mx.budget.testing.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `por dia parte del disponible neto, no de ingreso menos gastado`() {
        // Entraron 20,000, se gastaron 8,000 y quedan 4,000 reservados: por dia
        // tiene que repartir 8,000, no 12,000.
        val f = QuincenaFigures(projectedIncome = 20000.0, receivedIncome = 0.0, spent = 8000.0, reserved = 4000.0, projectedExpenses = 0.0)
        assertEquals(2000.0, disponiblePorDia(f.available, diasRestantesSinHoy = 3)!!, 0.0)
    }

    @Test
    fun `por dia cuenta hoy`() {
        // El 30 de una quincena que cierra el 31 quedan hoy y manana.
        assertEquals(500.0, disponiblePorDia(1000.0, diasRestantesSinHoy = 1)!!, 0.0)
        assertEquals(1000.0, disponiblePorDia(1000.0, diasRestantesSinHoy = 0)!!, 0.0)
    }

    @Test
    fun `sin disponible no hay cifra por dia`() {
        assertNull(disponiblePorDia(0.0, diasRestantesSinHoy = 5))
        assertNull(disponiblePorDia(-1500.0, diasRestantesSinHoy = 5))
    }

    @Test
    fun `los topes por gastar y el arrastre entran a la resta`() {
        // Entraron 20,000, sobraron 1,500 de la anterior, se gastaron 8,000,
        // hay 2,000 reservados y faltan 3,000 por gastar de los topes.
        val f = QuincenaFigures(
            projectedIncome = 20000.0, receivedIncome = 0.0, spent = 8000.0, reserved = 2000.0,
            projectedExpenses = 0.0, capsPending = 3000.0, carryOver = 1500.0,
        )
        assertEquals(21500.0, f.base, 0.0)
        assertEquals(13500.0, f.gross, 0.0)
        assertEquals(5000.0, f.setAside, 0.0)
        assertEquals(8500.0, f.available, 0.0)
    }

    @Test
    fun `un arrastre negativo resta lo que falto`() {
        val f = QuincenaFigures(10000.0, 0.0, spent = 2000.0, reserved = 0.0, projectedExpenses = 0.0, carryOver = -1578.2)
        assertEquals(6421.8, f.available, 1e-9)
        assertEquals(6421.8, f.gross, 1e-9)
    }

    @Test
    fun `sin topes ni arrastre la convencion no cambia`() {
        val f = QuincenaFigures(10000.0, 0.0, spent = 7000.0, reserved = 2500.0, projectedExpenses = 0.0)
        assertEquals(500.0, f.available, 0.0)
        assertEquals(3000.0, f.gross, 0.0)
    }

    @Test
    fun `quincenaFigures pasa los topes y el arrastre`() {
        val q = Fixtures.quincena(2026, 10, "SECOND", projectedIncome = 18000.0)
        val f = quincenaFigures(q, receivedIncome = 0.0, spent = 1000.0, reserved = 500.0, capsPending = 700.0, carryOver = 300.0)
        assertEquals(16100.0, f.available, 0.0)
    }

    @Test
    fun `por semana son siete dias y nunca mas que el disponible`() {
        // 14 dias restantes mas hoy: 15 dias, 1,000 por dia, 7,000 por semana.
        assertEquals(7000.0, disponiblePorSemana(15000.0, diasRestantesSinHoy = 14)!!, 1e-9)
        // Quedan hoy y 6 mas: la semana es justo el disponible.
        assertEquals(7000.0, disponiblePorSemana(7000.0, diasRestantesSinHoy = 6)!!, 1e-9)
        // Quedan hoy y manana: la semana no puede prometer mas de lo que hay.
        assertEquals(1000.0, disponiblePorSemana(1000.0, diasRestantesSinHoy = 1)!!, 1e-9)
        assertEquals(1000.0, disponiblePorSemana(1000.0, diasRestantesSinHoy = 0)!!, 1e-9)
        assertNull(disponiblePorSemana(0.0, diasRestantesSinHoy = 5))
        assertNull(disponiblePorSemana(-10.0, diasRestantesSinHoy = 5))
    }

    @Test
    fun `la cifra de Ritmo sigue a la vista elegida`() {
        assertEquals(1000.0, cifraRitmo(RitmoVista.DIA, 15000.0, 14)!!, 1e-9)
        assertEquals(7000.0, cifraRitmo(RitmoVista.SEMANA, 15000.0, 14)!!, 1e-9)
        assertEquals(15000.0, cifraRitmo(RitmoVista.QUINCENA, 15000.0, 14)!!, 1e-9)
        // La quincena muestra el disponible aunque sea negativo; dia y semana no.
        assertEquals(-200.0, cifraRitmo(RitmoVista.QUINCENA, -200.0, 3)!!, 0.0)
        assertNull(cifraRitmo(RitmoVista.DIA, -200.0, 3))
        assertNull(cifraRitmo(RitmoVista.SEMANA, -200.0, 3))
    }

    @Test
    fun `quincenaFigures respeta la columna cuando trae presupuesto`() {
        val q = Fixtures.quincena(2026, 9, "FIRST", projectedIncome = 18000.0, projectedExpenses = 9000.0)
        val f = quincenaFigures(q, receivedIncome = 0.0, spent = 0.0, reserved = 0.0, projectedExpensesFallback = 12000.0)
        assertEquals(9000.0, f.projectedExpenses, 0.0)
    }
}
