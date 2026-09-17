package mx.budget.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

/** Formato canonico de montos de la interfaz: pesos enteros con agrupacion es-MX. */
class MoneyFormatTest {

    @Test
    fun `agrupa miles con coma y sin centavos`() {
        assertEquals("$1,234", 1234.0.toMxn())
        assertEquals("$1,234,567", 1234567.0.toMxn())
    }

    @Test
    fun `trunca los centavos en vez de redondear`() {
        assertEquals("$999", 999.99.toMxn())
        assertEquals("$1,234", 1234.56.toMxn())
    }

    @Test
    fun `cero y cifras chicas no llevan separador`() {
        assertEquals("$0", 0.0.toMxn())
        assertEquals("$999", 999.0.toMxn())
    }

    @Test
    fun `un monto negativo conserva el signo`() {
        val s = (-1234.0).toMxn()
        assertEquals('$', s.first())
        assertEquals("$-1,234", s)
    }
}
