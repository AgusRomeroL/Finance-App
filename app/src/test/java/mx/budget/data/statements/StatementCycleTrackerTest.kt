package mx.budget.data.statements

import mx.budget.testing.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * "Estados del mes": por tarjeta, el corte esperado es el mas reciente que ya
 * lleva dos dias vencido, y esta cubierto si el ultimo import llega a cinco
 * dias antes de ese corte.
 */
class StatementCycleTrackerTest {

    private val today = LocalDate.of(2026, 9, 17)
    private val card = Fixtures.wallet(id = "cc", name = "Banamex", kind = "CREDIT_CARD", cutoffDay = 10)

    private fun single(
        wallet: mx.budget.data.local.entity.PaymentMethodEntity = card,
        lastEnd: LocalDate?,
        day: LocalDate = today,
    ) = StatementCycleTracker.compute(listOf(wallet), mapOf(wallet.id to lastEnd), day).single()

    @Test
    fun `el corte esperado es el ultimo ya vencido con gracia`() {
        val s = single(lastEnd = null)
        assertEquals(LocalDate.of(2026, 9, 10), s.expectedCutoff)
        assertEquals(StatementCycleStatus.PENDING, s.status)
    }

    @Test
    fun `dentro de la gracia el corte esperado sigue siendo el anterior`() {
        // Hoy 11: el corte del 10 lleva un dia, y la gracia son dos.
        val s = single(lastEnd = null, day = LocalDate.of(2026, 9, 11))
        assertEquals(LocalDate.of(2026, 8, 10), s.expectedCutoff)
    }

    @Test
    fun `un import que llega al corte menos la tolerancia cuenta como importado`() {
        assertEquals(StatementCycleStatus.IMPORTED, single(lastEnd = LocalDate.of(2026, 9, 10)).status)
        assertEquals(StatementCycleStatus.IMPORTED, single(lastEnd = LocalDate.of(2026, 9, 5)).status)
        assertEquals(StatementCycleStatus.PENDING, single(lastEnd = LocalDate.of(2026, 9, 4)).status)
    }

    @Test
    fun `el import del ciclo anterior deja el actual pendiente`() {
        val s = single(lastEnd = LocalDate.of(2026, 8, 10))
        assertEquals(StatementCycleStatus.PENDING, s.status)
        assertEquals(LocalDate.of(2026, 8, 10), s.lastImportPeriodEnd)
    }

    @Test
    fun `tarjeta sin dia de corte queda en NO_CUTOFF sin corte esperado`() {
        val w = Fixtures.wallet(id = "cc2", kind = "DEPARTMENT_STORE_CARD", cutoffDay = null)
        val s = single(wallet = w, lastEnd = null)
        assertEquals(StatementCycleStatus.NO_CUTOFF, s.status)
        assertNull(s.expectedCutoff)
        val bad = Fixtures.wallet(id = "cc3", kind = "CREDIT_CARD", cutoffDay = 40)
        assertEquals(StatementCycleStatus.NO_CUTOFF, single(wallet = bad, lastEnd = null).status)
    }

    @Test
    fun `solo entran tarjetas activas que reciben estado de cuenta`() {
        val debit = Fixtures.wallet(id = "d", kind = "DEBIT_ACCOUNT", cutoffDay = 10)
        val cash = Fixtures.wallet(id = "c", kind = "CASH")
        val inactive = Fixtures.wallet(id = "i", kind = "CREDIT_CARD", cutoffDay = 10, active = false)
        val bnpl = Fixtures.wallet(id = "b", kind = "BNPL_INSTALLMENT", cutoffDay = 3)
        val digital = Fixtures.wallet(id = "g", kind = "DIGITAL_WALLET", cutoffDay = 3)
        val out = StatementCycleTracker.compute(listOf(debit, cash, inactive, bnpl, digital, card), emptyMap(), today)
        assertEquals(listOf("b", "g", "cc"), out.map { it.walletId })
    }

    @Test
    fun `pending filtra los que faltan`() {
        val other = Fixtures.wallet(id = "cc2", kind = "CREDIT_CARD", cutoffDay = 10)
        val all = StatementCycleTracker.compute(
            listOf(card, other),
            mapOf(card.id to LocalDate.of(2026, 9, 10), other.id to null),
            today,
        )
        assertEquals(listOf("cc2"), StatementCycleTracker.pending(all).map { it.walletId })
    }

    @Test
    fun `lastCutoffOnOrBefore recorta el dia 31 en febrero`() {
        assertEquals(LocalDate.of(2026, 2, 28), StatementCycleTracker.lastCutoffOnOrBefore(31, LocalDate.of(2026, 3, 2)))
        assertEquals(LocalDate.of(2026, 3, 31), StatementCycleTracker.lastCutoffOnOrBefore(31, LocalDate.of(2026, 3, 31)))
        assertEquals(LocalDate.of(2025, 12, 20), StatementCycleTracker.lastCutoffOnOrBefore(20, LocalDate.of(2026, 1, 19)))
    }

    @Test
    fun `isStatementCard reconoce los cuatro tipos`() {
        assertTrue(StatementCycleTracker.isStatementCard("CREDIT_CARD"))
        assertTrue(StatementCycleTracker.isStatementCard("DEPARTMENT_STORE_CARD"))
        assertTrue(StatementCycleTracker.isStatementCard("BNPL_INSTALLMENT"))
        assertTrue(StatementCycleTracker.isStatementCard("DIGITAL_WALLET"))
        assertTrue(!StatementCycleTracker.isStatementCard("DEBIT_ACCOUNT"))
        assertTrue(!StatementCycleTracker.isStatementCard("EXTERNAL"))
    }
}
