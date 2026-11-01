package mx.budget.ai.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Las variantes del modelo se descargan de Hugging Face con la URL, el tamaño y
 * el SHA-256 fijados en el código (decisión 4, Fase 8). Un error al pegar esos
 * valores no falla al compilar: falla en el teléfono, después de bajar gigabytes.
 * Esta prueba lo atrapa antes.
 */
class ModelCatalogTest {

    private val pinnedUrl = Regex(
        "^https://huggingface\\.co/litert-community/[A-Za-z0-9._-]+/resolve/[0-9a-f]{40}/[A-Za-z0-9._-]+\\.litertlm$"
    )

    @Test
    fun `cada variante apunta a un commit fijo de litert-community`() {
        ModelCatalog.all.forEach { v ->
            assertTrue("${v.id}: ${v.downloadUrl}", pinnedUrl.matches(v.downloadUrl))
        }
    }

    @Test
    fun `cada variante trae tamano y huella SHA-256 validos`() {
        ModelCatalog.all.forEach { v ->
            assertTrue("${v.id}: tamaño ${v.sizeBytes}", v.sizeBytes > 1_000_000_000L)
            assertTrue("${v.id}: huella ${v.sha256}", Regex("^[0-9a-f]{64}$").matches(v.sha256))
        }
    }

    @Test
    fun `ids y nombres de archivo no se repiten y la de fabrica es E2B`() {
        assertEquals(ModelCatalog.all.size, ModelCatalog.all.map { it.id }.toSet().size)
        assertEquals(ModelCatalog.all.size, ModelCatalog.all.map { it.fileName }.toSet().size)
        assertEquals(ModelCatalog.all.size, ModelCatalog.all.map { it.sha256 }.toSet().size)
        assertEquals("E2B", ModelCatalog.default.id)
    }
}
