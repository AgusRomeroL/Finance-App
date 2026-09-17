package mx.budget.ai.dispatch

import org.junit.Assert.assertEquals
import org.junit.Test

/** Reparacion de la salida cruda del LLM antes de parsearla como intent. */
class JsonRepairerTest {

    private val clean = """{"intent": "GET_TOP_CATEGORY", "args": {}}"""

    @Test
    fun `un objeto limpio sale igual`() {
        assertEquals(clean, JsonRepairer.repair(clean))
    }

    @Test
    fun `quita los fences de markdown`() {
        assertEquals(clean, JsonRepairer.repair("```json\n$clean\n```"))
        assertEquals(clean, JsonRepairer.repair("```\n$clean\n```"))
        assertEquals(clean, JsonRepairer.repair("```JSON\n$clean```"))
    }

    @Test
    fun `descarta la prosa antes y despues del objeto`() {
        val raw = "Claro, aqui tienes el intent:\n$clean\nEspero que te sirva."
        assertEquals(clean, JsonRepairer.repair(raw))
    }

    @Test
    fun `cierra un objeto truncado por el limite de tokens`() {
        val truncated = """{"intent": "GET_SPEND_BY_MEMBER", "args": {"member_alias": "Dav"""
        assertEquals("""{"intent": "GET_SPEND_BY_MEMBER", "args": {"member_alias": "Dav"}}""", JsonRepairer.repair(truncated))
    }

    @Test
    fun `cierra las llaves pendientes cuando la cadena ya estaba cerrada`() {
        val truncated = """{"intent": "SUMMARIZE_QUINCENA", "args": {"from_date": "2026-09-01""""
        assertEquals("""{"intent": "SUMMARIZE_QUINCENA", "args": {"from_date": "2026-09-01"}}""", JsonRepairer.repair(truncated))
    }

    @Test
    fun `las llaves dentro de una cadena no cuentan`() {
        val raw = """{"reason": "una llave } y otra {", "intent": "UNKNOWN"} basura"""
        assertEquals("""{"reason": "una llave } y otra {", "intent": "UNKNOWN"}""", JsonRepairer.repair(raw))
    }

    @Test
    fun `una comilla escapada no abre ni cierra cadena`() {
        val raw = """{"reason": "dijo \"hola\"", "intent": "UNKNOWN"} y mas"""
        assertEquals("""{"reason": "dijo \"hola\"", "intent": "UNKNOWN"}""", JsonRepairer.repair(raw))
    }

    @Test
    fun `sin llave de apertura devuelve la entrada tal cual`() {
        assertEquals("no hay json aqui", JsonRepairer.repair("no hay json aqui"))
        assertEquals("", JsonRepairer.repair(""))
    }

    @Test
    fun `solo devuelve el primer objeto balanceado`() {
        assertEquals("""{"a": 1}""", JsonRepairer.repair("""{"a": 1} {"b": 2}"""))
    }
}
