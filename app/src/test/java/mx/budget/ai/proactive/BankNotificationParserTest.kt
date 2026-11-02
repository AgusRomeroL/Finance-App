package mx.budget.ai.proactive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * Tests JVM del parser de notificaciones bancarias contra las plantillas reales
 * (`assets/ai/bank_templates.json`). Nacen en la Fase 8: el smoke test del release
 * en el Pixel 10 propuso como gasto una cancelación de Klar ("Devolvimos -$20.00").
 */
class BankNotificationParserTest {

    private val parser = BankNotificationParser(
        BankTemplates.fromJson(File("src/main/assets/ai/bank_templates.json").readText())
    )

    private val now = 1_700_000_000_000L

    @Test
    fun `una compra cancelada no se propone como gasto`() {
        val r = parser.parse("mx.klar.app", "Tu compra fue cancelada", "Devolvimos -\$20.00 a tu cuenta", now)
        assertNull(r)
    }

    @Test
    fun `un reembolso no se propone como gasto`() {
        val r = parser.parse("mx.klar.app", "Klar", "Te hicimos un reembolso por \$150.00", now)
        assertNull(r)
    }

    @Test
    fun `una compra aprobada sigue proponiendose`() {
        val r = parser.parse("mx.klar.app", "Compra aprobada", "Compraste por \$20.00 en OXXO", now)
        assertNotNull(r)
        assertEquals(20.0, r!!.amountMxn, 0.001)
        assertEquals("klar", r.bankId)
    }
}
