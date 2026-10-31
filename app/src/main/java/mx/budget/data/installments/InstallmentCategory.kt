package mx.budget.data.installments

import mx.budget.data.local.entity.CategoryEntity
import java.text.Normalizer

/**
 * Categoría con la que se proyectan las cuotas de un plan MSI que no trae una.
 *
 * El materializador no puede crear un gasto sin categoría, y los planes dados de
 * alta en Cuentas o creados al importar un estado de cuenta nacían sin ella: se
 * saltaban y nunca proyectaban una sola cuota. Solo los planes de la semilla, que
 * sí traen categoría, llegaban al calendario.
 *
 * Orden de búsqueda:
 * 1. la hoja de cuotas (`kind = EXPENSE_INSTALLMENT`) que nombra el plan o, si no,
 *    la tarjeta: "Mercado Libre" para "Mercado Libre 12 MSI", "Liverpool" para una
 *    pantalla pagada con la tarjeta Liverpool; si varias coinciden, la de nombre
 *    más largo;
 * 2. el grupo de cuotas de la raíz ("Tarjetas y préstamos" en la semilla);
 * 3. "Otros" (`OTHER` en la semilla, `OTROS` en el catálogo de un grupo nuevo);
 * 4. el primer grupo de gasto.
 *
 * Lógica pura, sin Room: quien la llama reúne las categorías y el nombre de la
 * cuenta.
 */
object InstallmentCategory {

    const val KIND = "EXPENSE_INSTALLMENT"
    private val OTHER_CODES = setOf("OTHER", "OTROS")

    /** Nombres más cortos que esto ("Klar" tiene 4) coinciden por accidente. */
    private const val MIN_NAME = 4

    fun resolve(planName: String, walletName: String?, categories: List<CategoryEntity>): String? {
        val installment = categories.filter { it.kind == KIND }
        val leaves = installment
            .filter { it.parentId != null }
            .map { it to normalize(it.displayName) }
            .filter { (_, n) -> n.length >= MIN_NAME }
        // El nombre del plan es el comercio y manda sobre la tarjeta con que se paga.
        val byName = listOfNotNull(planName, walletName)
            .map(::normalize)
            .firstNotNullOfOrNull { text ->
                leaves.filter { (_, n) -> containsWords(text, n) }.maxByOrNull { (_, n) -> n.length }?.first
            }
        return (byName
            ?: installment.firstOrNull { it.parentId == null }
            ?: installment.firstOrNull()
            ?: categories.firstOrNull { it.parentId == null && it.code in OTHER_CODES }
            ?: categories.firstOrNull { it.parentId == null && it.kind.startsWith("EXPENSE") })
            ?.id
    }

    /** Minúsculas, sin acentos y con todo lo que no es letra o número como espacio. */
    private fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    /** [name] aparece en [text] como palabras completas, no como pedazo de otra. */
    private fun containsWords(text: String, name: String): Boolean = " $text ".contains(" $name ")
}
