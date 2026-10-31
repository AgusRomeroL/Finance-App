package mx.budget.data.quincena

import mx.budget.data.local.result.CategoryCapRow
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Lo que falta por gastar de cada tope (Fase 7b, decision 13): un gasto dentro
 * del tope no mueve el disponible y solo el exceso lo baja.
 */
class CategoryCapsTest {

    private fun row(cap: Double, spent: Double = 0.0, planned: Double = 0.0, id: String = "cat") =
        CategoryCapRow(categoryId = id, categoryName = id, colorHex = null, cap = cap, spent = spent, plannedProrated = planned)

    @Test
    fun `sin gasto queda el tope entero`() {
        assertEquals(4000.0, CategoryCaps.pending(row(4000.0)), 0.0)
    }

    @Test
    fun `lo gastado dentro del tope se descuenta`() {
        assertEquals(2150.0, CategoryCaps.pending(row(4000.0, spent = 1850.0)), 1e-9)
    }

    @Test
    fun `en el borde queda cero`() {
        assertEquals(0.0, CategoryCaps.pending(row(1500.0, spent = 1500.0)), 0.0)
    }

    @Test
    fun `un tope excedido aporta cero, nunca negativo`() {
        assertEquals(0.0, CategoryCaps.pending(row(1500.0, spent = 1680.0)), 0.0)
    }

    @Test
    fun `lo planeado de la categoria no se cuenta dos veces`() {
        // 900 ya apartados como reservado: del tope solo faltan 600.
        assertEquals(600.0, CategoryCaps.pending(row(2000.0, spent = 500.0, planned = 900.0)), 1e-9)
    }

    @Test
    fun `el total suma lo pendiente de cada tope`() {
        val rows = listOf(row(4000.0, spent = 1850.0, id = "a"), row(1500.0, spent = 1680.0, id = "b"), row(1000.0, id = "c"))
        assertEquals(3150.0, CategoryCaps.totalPending(rows), 1e-9)
    }

    @Test
    fun `un gasto dentro del tope no mueve el disponible y el exceso si`() {
        // Secuencia de Lukas: limite de 1,500 con 780 gastados; un gasto de 500
        // queda dentro y uno de 400 lo excede por 180.
        fun disponible(spentTotal: Double, catSpent: Double): Double = QuincenaFigures(
            projectedIncome = 20000.0, receivedIncome = 0.0, spent = spentTotal, reserved = 0.0,
            projectedExpenses = 0.0, capsPending = CategoryCaps.totalPending(listOf(row(1500.0, spent = catSpent))),
        ).available
        val antes = disponible(780.0, 780.0)
        val dentro = disponible(1280.0, 1280.0)
        val excede = disponible(1680.0, 1680.0)
        assertEquals(antes, dentro, 1e-9)
        assertEquals(antes - 180.0, excede, 1e-9)
    }
}
