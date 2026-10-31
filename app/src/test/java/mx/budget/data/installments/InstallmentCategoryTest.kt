package mx.budget.data.installments

import mx.budget.data.local.entity.CategoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Un plan MSI sin categoría nunca proyectaba cuotas. Estas pruebas fijan de dónde
 * sale la que se le asigna, con categorías como las de la semilla.
 */
class InstallmentCategoryTest {

    private fun cat(id: String, name: String, kind: String, parent: String? = null, code: String = id) =
        CategoryEntity(id = id, householdId = "h", parentId = parent, code = code, displayName = name, kind = kind)

    private val semilla = listOf(
        cat("food", "Alimentación", "EXPENSE_VARIABLE"),
        cat("loans", "Tarjetas y préstamos", "EXPENSE_INSTALLMENT", code = "LOANS"),
        cat("liverpool", "Liverpool", "EXPENSE_INSTALLMENT", parent = "loans"),
        cat("ml", "Mercado Libre", "EXPENSE_INSTALLMENT", parent = "loans"),
        cat("mp", "Mercado Pago", "EXPENSE_INSTALLMENT", parent = "loans"),
        cat("buro", "Buró de crédito", "EXPENSE_INSTALLMENT", parent = "loans"),
        cat("other", "Otros", "EXPENSE_VARIABLE", code = "OTHER"),
    )

    @Test
    fun `la hoja que nombra el plan gana`() {
        assertEquals("ml", InstallmentCategory.resolve("Mercado Libre 12 MSI", walletName = "Mercado Pago", categories = semilla))
    }

    @Test
    fun `sin comercio en el nombre usa la tarjeta`() {
        assertEquals("liverpool", InstallmentCategory.resolve("Pantalla 6 MSI", walletName = "Tarjeta LIVERPOOL", categories = semilla))
    }

    @Test
    fun `ignora acentos y mayusculas`() {
        assertEquals("buro", InstallmentCategory.resolve("BURO DE CREDITO 3 PAGOS", walletName = null, categories = semilla))
    }

    @Test
    fun `no confunde un nombre con un pedazo de otra palabra`() {
        val conKlar = semilla + cat("klar", "Klar", "EXPENSE_INSTALLMENT", parent = "loans")
        assertEquals("loans", InstallmentCategory.resolve("Klarinete 3 MSI", walletName = null, categories = conKlar))
    }

    @Test
    fun `sin coincidencia cae al grupo de cuotas`() {
        assertEquals("loans", InstallmentCategory.resolve("Pantalla 6 MSI", walletName = "Débito", categories = semilla))
    }

    @Test
    fun `sin grupo de cuotas cae a Otros del catalogo nuevo`() {
        val grupoNuevo = listOf(
            cat("viv", "Vivienda", "EXPENSE_FIXED"),
            cat("otros", "Otros", "EXPENSE_VARIABLE", code = "OTROS"),
        )
        assertEquals("otros", InstallmentCategory.resolve("Pantalla 6 MSI", walletName = null, categories = grupoNuevo))
    }

    @Test
    fun `sin Otros usa el primer grupo de gasto y sin categorias no inventa`() {
        val soloIngreso = listOf(cat("ing", "Ingresos", "INCOME"), cat("viv", "Vivienda", "EXPENSE_FIXED"))
        assertEquals("viv", InstallmentCategory.resolve("Pantalla", walletName = null, categories = soloIngreso))
        assertNull(InstallmentCategory.resolve("Pantalla", walletName = null, categories = emptyList()))
    }
}
