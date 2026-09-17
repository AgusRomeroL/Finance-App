package mx.budget.ui.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Conversion entre los porcentajes que edita la captura y los basis points del
 * ledger: la particion suma 10,000 exactos y el ultimo miembro absorbe el resto.
 */
class AttributionSharesTest {

    @Test
    fun `tres tercios en porcentaje producen 10000 bps exactos`() {
        val bps = AttributionShares.percentToBps(linkedMapOf("a" to 33, "b" to 33, "c" to 34))
        assertEquals(listOf("a", "b", "c"), bps.keys.toList())
        assertEquals(listOf(3300, 3300, 3400), bps.values.toList())
        assertEquals(10_000, bps.values.sum())
    }

    @Test
    fun `el ultimo absorbe el resto aunque los porcentajes no sumen 100`() {
        // 33 + 33 + 33 = 99: la interfaz valida la suma antes, pero la conversion
        // garantiza la invariante del ledger por construccion.
        val bps = AttributionShares.percentToBps(linkedMapOf("a" to 33, "b" to 33, "c" to 33))
        assertEquals(listOf(3300, 3300, 3400), bps.values.toList())
        assertEquals(10_000, bps.values.sum())
    }

    @Test
    fun `un solo miembro se lleva todo`() {
        assertEquals(mapOf("solo" to 10_000), AttributionShares.percentToBps(mapOf("solo" to 100)))
        assertEquals(mapOf("solo" to 10_000), AttributionShares.percentToBps(mapOf("solo" to 40)))
    }

    @Test
    fun `los mapas vacios no producen nada`() {
        assertTrue(AttributionShares.percentToBps(emptyMap()).isEmpty())
        assertTrue(AttributionShares.bpsToPercent(emptyMap()).isEmpty())
        assertTrue(AttributionShares.equalSplit(emptyList()).isEmpty())
    }

    @Test
    fun `bps a porcentaje trunca y el ultimo cierra a 100`() {
        val pct = AttributionShares.bpsToPercent(linkedMapOf("a" to 3333, "b" to 3333, "c" to 3334))
        assertEquals(listOf(33, 33, 34), pct.values.toList())
        assertEquals(100, pct.values.sum())
    }

    @Test
    fun `ida y vuelta conserva una particion redonda`() {
        val original = linkedMapOf("a" to 70, "b" to 30)
        assertEquals(original, AttributionShares.bpsToPercent(AttributionShares.percentToBps(original)))
    }

    @Test
    fun `el reparto equitativo deja el resto en el ultimo`() {
        assertEquals(listOf(33, 33, 34), AttributionShares.equalSplit(listOf("a", "b", "c")).values.toList())
        assertEquals(listOf(50, 50), AttributionShares.equalSplit(listOf("a", "b")).values.toList())
        assertEquals(listOf(14, 14, 14, 14, 14, 14, 16), AttributionShares.equalSplit((1..7).map { "m$it" }).values.toList())
        assertEquals(100, AttributionShares.equalSplit((1..7).map { "m$it" }).values.sum())
    }

    @Test
    fun `cualquier particion equitativa convertida a bps suma 10000`() {
        for (n in 1..12) {
            val pct = AttributionShares.equalSplit((1..n).map { "m$it" })
            assertEquals("n=$n", 100, pct.values.sum())
            assertEquals("n=$n", 10_000, AttributionShares.percentToBps(pct).values.sum())
        }
    }
}
