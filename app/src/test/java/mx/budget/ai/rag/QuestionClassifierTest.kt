package mx.budget.ai.rag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Clasificador heuristico del RAG: que dimensiones de contexto recupera para
 * una pregunta y como distingue lo que es del presupuesto de lo que no.
 * Los casos canonicos de analisis abierto viven en la golden suite.
 */
class QuestionClassifierTest {

    @Test
    fun `sin senal clara classify sobrecarga tres dimensiones y detect queda vacio`() {
        val q = "hola que tal"
        assertTrue(QuestionClassifier.detect(q).isEmpty())
        assertEquals(
            setOf(ContextDimension.SpendByCategory, ContextDimension.TopExpenses, ContextDimension.Wallets),
            QuestionClassifier.classify(q),
        )
    }

    @Test
    fun `reconoce categorias, top, cuentas, miembros, cuotas y comparaciones por palabra clave`() {
        assertTrue(ContextDimension.SpendByCategory in QuestionClassifier.detect("¿Cuánto gastamos en despensa?"))
        assertTrue(ContextDimension.TopExpenses in QuestionClassifier.detect("¿Cuáles son los últimos movimientos?"))
        assertTrue(ContextDimension.Wallets in QuestionClassifier.detect("¿Cuánto debo de la tarjeta?"))
        assertTrue(ContextDimension.ByMember in QuestionClassifier.detect("¿Quién gastó más?"))
        assertTrue(ContextDimension.Installments in QuestionClassifier.detect("¿Qué cuotas vienen?"))
        assertTrue(ContextDimension.Installments in QuestionClassifier.detect("lo de meses sin intereses"))
        assertTrue(ContextDimension.HistoricalCompare in QuestionClassifier.detect("Compara con la quincena anterior"))
    }

    @Test
    fun `los acentos no cambian la clasificacion`() {
        assertEquals(
            QuestionClassifier.detect("¿Quién gastó más en categoría comida?"),
            QuestionClassifier.detect("quien gasto mas en categoria comida"),
        )
    }

    @Test
    fun `los nombres propios del hogar llegan como parametros`() {
        assertTrue(ContextDimension.ByMember in QuestionClassifier.detect("y santi?", memberAliases = listOf("Santi")))
        assertTrue(ContextDimension.ByMember !in QuestionClassifier.detect("y santi?"))
        assertTrue(ContextDimension.Wallets in QuestionClassifier.detect("dame lo de bbva", walletNames = listOf("BBVA")))
        assertTrue(ContextDimension.Installments in QuestionClassifier.detect("¿Cómo va el Buró de Crédito 3 pagos?", planNames = listOf("Buró de Crédito 3 pagos")))
        assertTrue(ContextDimension.Installments !in QuestionClassifier.detect("como va lo de mercado libre", planNames = listOf("Mercado Libre 12 MSI")))
    }

    @Test
    fun `un nombre de menos de tres letras no dispara nada`() {
        assertTrue(QuestionClassifier.detect("hola", memberAliases = listOf("ho")).isEmpty())
    }

    @Test
    fun `una pregunta que solo suena a dinero por substring no entra`() {
        assertFalse(QuestionClassifier.hasFinancialVocabulary("Cuéntame un chiste"))
        assertFalse(QuestionClassifier.hasFinancialVocabulary("¿Qué día es hoy?"))
    }

    @Test
    fun `el vocabulario de dinero se reconoce por palabra completa y por familia`() {
        assertTrue(QuestionClassifier.hasFinancialVocabulary("¿Cuánto le debo a la tarjeta?"))
        assertTrue(QuestionClassifier.hasFinancialVocabulary("¿Gastamos mucho?"))
        assertTrue(QuestionClassifier.hasFinancialVocabulary("ya pagué la renta"))
        assertTrue(QuestionClassifier.hasFinancialVocabulary("¿Me pasé de presupuesto?"))
        assertTrue(QuestionClassifier.hasFinancialVocabulary("cuanto llevamos ahorrado"))
    }

    @Test
    fun `isOpenAnalysis distingue analisis abierto de una pregunta puntual`() {
        assertTrue(QuestionClassifier.isOpenAnalysis("Analiza mis gastos"))
        assertTrue(QuestionClassifier.isOpenAnalysis("¿Hay algo inusual?"))
        assertFalse(QuestionClassifier.isOpenAnalysis("¿Cuánto queda en Gasolina?"))
    }
}
