package mx.budget.ai.proactive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Tests JVM del parser determinista de captura en lenguaje natural (§G.3).
 * La clase es Kotlin puro (sin deps de Android), así que corre como unit test
 * normal: `./gradlew.bat :app:testDebugUnitTest --tests "*NaturalLanguage*"`.
 */
class NaturalLanguageCaptureParserTest {

    private val parser = NaturalLanguageCaptureParser()

    /** Instante fijo para que las aserciones de fecha sean deterministas. */
    private val now = 1_700_000_000_000L

    @Test
    fun `perifrasis acabo de gastar con gerundio extrae solo el objeto`() {
        val r = parser.parse("Acabo de gastar \$10 en comprando papas fritas", now)
        assertNotNull(r)
        assertEquals(10.0, r!!.amountMxn, 0.001)
        assertEquals("papas fritas", r.concept)
        assertEquals(now, r.occurredAt)
    }

    @Test
    fun `pague N de X extrae el objeto tras la preposicion`() {
        val r = parser.parse("Pagué 250 de gasolina", now)
        assertNotNull(r)
        assertEquals(250.0, r!!.amountMxn, 0.001)
        assertEquals("gasolina", r.concept)
    }

    @Test
    fun `monto con comas y decimales mas fecha relativa ayer`() {
        val r = parser.parse("gasté \$1,234.50 en el súper ayer", now)
        assertNotNull(r)
        assertEquals(1234.50, r!!.amountMxn, 0.001)
        assertTrue(
            "concepto inesperado: '${r.concept}'",
            r.concept == "súper" || r.concept == "el súper",
        )
        val expectedAyer = Instant.ofEpochMilli(now).minus(1, ChronoUnit.DAYS).toEpochMilli()
        assertEquals(expectedAyer, r.occurredAt)
    }

    @Test
    fun `forma minima monto y concepto`() {
        val r = parser.parse("300 tacos", now)
        assertNotNull(r)
        assertEquals(300.0, r!!.amountMxn, 0.001)
        assertEquals("tacos", r.concept)
    }

    @Test
    fun `objeto compuesto con conjuncion se preserva completo`() {
        val r = parser.parse("Compré 89.90 de leche y pan", now)
        assertNotNull(r)
        assertEquals(89.90, r!!.amountMxn, 0.001)
        assertEquals("leche y pan", r.concept)
    }

    @Test
    fun `perifrasis acabo de pagar con preposicion por`() {
        val r = parser.parse("acabo de pagar 1500 por la luz", now)
        assertNotNull(r)
        assertEquals(1500.0, r!!.amountMxn, 0.001)
        assertTrue(
            "concepto inesperado: '${r.concept}'",
            r.concept == "la luz" || r.concept == "luz",
        )
    }

    @Test
    fun `truncado a 40 chars corta en limite de palabra`() {
        val r = parser.parse(
            "gasté 200 en refacciones especiales importadas para la camioneta blanca",
            now,
        )
        assertNotNull(r)
        assertTrue("excede 40 chars: '${r!!.concept}'", r.concept.length <= 40)
        // Nunca a media palabra: el concepto debe terminar en palabra completa.
        val original = "refacciones especiales importadas para la camioneta blanca"
        assertTrue(
            "corte a media palabra: '${r.concept}'",
            original == r.concept || original.startsWith(r.concept + " "),
        )
    }

    @Test
    fun `sin monto devuelve null`() {
        assertEquals(null, parser.parse("no compré nada hoy", now))
    }
}
