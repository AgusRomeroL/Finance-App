package mx.budget.data.budget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pronostico lineal por categoria y niveles de aviso (Fase 7b, alcance 2). */
class CategoryForecastTest {

    @Test
    fun `el pronostico divide lo gastado entre la fraccion transcurrida`() {
        // Dia 8 de 16: lleva 1,000, al cierre iria en 2,000.
        assertEquals(2000.0, CategoryForecast.projected(1000.0, 0.5f, dayIndex = 8)!!, 1e-6)
    }

    @Test
    fun `sin tres dias de datos no hay pronostico`() {
        assertNull(CategoryForecast.projected(1000.0, 1f / 16, dayIndex = 1))
        assertNull(CategoryForecast.projected(1000.0, 2f / 16, dayIndex = 2))
        assertEquals(1000.0 / (3f / 16), CategoryForecast.projected(1000.0, 3f / 16, dayIndex = 3)!!, 1e-3)
    }

    @Test
    fun `sin gasto no hay pronostico`() {
        assertNull(CategoryForecast.projected(0.0, 0.5f, dayIndex = 8))
    }

    @Test
    fun `al tope o encima es cien`() {
        assertEquals(BudgetAlertLevel.CIEN, CategoryForecast.level(1500.0, 1500.0, null))
        assertEquals(BudgetAlertLevel.CIEN, CategoryForecast.level(1680.0, 1500.0, 3000.0))
    }

    @Test
    fun `del ochenta al tope es ochenta`() {
        assertEquals(BudgetAlertLevel.OCHENTA, CategoryForecast.level(1200.0, 1500.0, null))
        assertEquals(BudgetAlertLevel.OCHENTA, CategoryForecast.level(1499.0, 1500.0, 1600.0))
    }

    @Test
    fun `por debajo del ochenta avisa solo si el pronostico pasa el tope`() {
        assertEquals(BudgetAlertLevel.ANTICIPADO, CategoryForecast.level(700.0, 1500.0, 1800.0))
        assertNull(CategoryForecast.level(700.0, 1500.0, 1400.0))
        assertNull(CategoryForecast.level(700.0, 1500.0, null))
    }

    @Test
    fun `sin tope no hay aviso`() {
        assertNull(CategoryForecast.level(5000.0, 0.0, 9000.0))
    }

    @Test
    fun `el porcentaje se redondea y es cero sin tope`() {
        assertEquals(80, CategoryForecast.pct(1200.0, 1500.0))
        assertEquals(112, CategoryForecast.pct(1680.0, 1500.0))
        assertEquals(0, CategoryForecast.pct(100.0, 0.0))
    }

    @Test
    fun `los niveles se ordenan de menor a mayor`() {
        assert(BudgetAlertLevel.ANTICIPADO < BudgetAlertLevel.OCHENTA)
        assert(BudgetAlertLevel.OCHENTA < BudgetAlertLevel.CIEN)
    }
}
