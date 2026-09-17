package mx.budget.ui.theme

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Redundancia no cromatica de los tonos financieros: el significado de una
 * cifra nunca depende solo del color, asi que cada tono lleva signo, icono y
 * etiqueta. `amountSemantic` solo existe en composicion (lee el tema), por eso
 * la prueba compone el tema real dentro de una Activity vacia y lee el
 * resultado.
 *
 * Se usa `ActivityScenario` y no `createComposeRule`: la regla de Compose
 * sincroniza con Espresso, y el Espresso que arrastra `ui-test-junit4` revienta
 * en Android 14+ con `NoSuchMethodException InputManager.getInstance`. Aqui no
 * hay interaccion que sincronizar: basta con esperar la primera composicion.
 */
@RunWith(AndroidJUnit4::class)
class AmountSemanticsTest {

    private fun resolve(dark: Boolean, dynamic: Boolean): Map<FinancialTone, AmountSemantic> {
        var out: Map<FinancialTone, AmountSemantic>? = null
        val composed = CountDownLatch(1)
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    BudgetAppTheme(darkTheme = dark, dynamicColor = dynamic) {
                        out = FinancialTone.entries.associateWith { amountSemantic(it) }
                        composed.countDown()
                    }
                }
            }
            assertTrue("el tema no llego a componerse", composed.await(10, TimeUnit.SECONDS))
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
        return requireNotNull(out)
    }

    @Test
    fun cadaTonoLlevaSignoIconoYEtiqueta() {
        val s = resolve(dark = false, dynamic = false)

        assertEquals("+", s.getValue(FinancialTone.INCOME).sign)
        assertEquals("−", s.getValue(FinancialTone.EXPENSE).sign)
        assertEquals("−", s.getValue(FinancialTone.SCHEDULED).sign)
        assertEquals("", s.getValue(FinancialTone.WARNING).sign)
        assertEquals("", s.getValue(FinancialTone.TRANSFER).sign)
        assertEquals("", s.getValue(FinancialTone.NEUTRAL).sign)

        assertEquals("Ingreso", s.getValue(FinancialTone.INCOME).description)
        assertEquals("Gasto", s.getValue(FinancialTone.EXPENSE).description)
        assertEquals("Alerta", s.getValue(FinancialTone.WARNING).description)
        assertEquals("Programado", s.getValue(FinancialTone.SCHEDULED).description)
        assertEquals("Transferencia", s.getValue(FinancialTone.TRANSFER).description)
        assertEquals("", s.getValue(FinancialTone.NEUTRAL).description)

        for (tone in FinancialTone.entries) {
            if (tone == FinancialTone.NEUTRAL) assertNull(s.getValue(tone).icon)
            else assertNotNull("icono de $tone", s.getValue(tone).icon)
        }
        // Un planeado no se puede confundir con un gasto ejecutado ni con un ingreso.
        assertNotEquals(s.getValue(FinancialTone.EXPENSE).icon, s.getValue(FinancialTone.SCHEDULED).icon)
        assertNotEquals(s.getValue(FinancialTone.INCOME).icon, s.getValue(FinancialTone.EXPENSE).icon)
    }

    @Test
    fun losColoresSemanticosSeDistinguenEnClaroYOscuro() {
        for (dark in listOf(false, true)) {
            val s = resolve(dark = dark, dynamic = false)
            assertNotEquals("ingreso vs gasto (dark=$dark)", s.getValue(FinancialTone.INCOME).color, s.getValue(FinancialTone.EXPENSE).color)
            assertNotEquals("gasto vs programado (dark=$dark)", s.getValue(FinancialTone.EXPENSE).color, s.getValue(FinancialTone.SCHEDULED).color)
            assertNotEquals("alerta vs gasto (dark=$dark)", s.getValue(FinancialTone.WARNING).color, s.getValue(FinancialTone.EXPENSE).color)
            assertTrue(s.values.all { it.color.alpha > 0f && it.container.alpha > 0f })
        }
    }

    @Test
    fun conColorDinamicoLasSenalesNoCromaticasNoCambian() {
        val fixed = resolve(dark = false, dynamic = false)
        val dynamic = resolve(dark = false, dynamic = true)
        for (tone in FinancialTone.entries) {
            assertEquals(fixed.getValue(tone).sign, dynamic.getValue(tone).sign)
            assertEquals(fixed.getValue(tone).description, dynamic.getValue(tone).description)
            assertEquals(fixed.getValue(tone).icon, dynamic.getValue(tone).icon)
        }
    }
}
