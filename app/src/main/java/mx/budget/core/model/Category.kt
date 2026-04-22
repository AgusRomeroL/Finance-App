package mx.budget.core.model

import kotlinx.serialization.Serializable







/**
 * Categoría jerárquica de gasto.
 *
 * Implementa un árbol de profundidad arbitraria con [parentId] como
 * auto-referencia. Las categorías raíz tienen parentId = null.
 *
 * Semilla derivada del Excel:
 * HOUSING > {Hipoteca, Internet, Electricidad, Agua, Teléfono, ...}
 * FOOD > {Comida, Despensa, Limpieza}
 * TRANSFERENCIAS_FAMILIARES > {David, Santiago, Pau, ...}   (antes "TAXES")
 */
@Serializable
data class Category(
    
    val id: String = "",

    
    val householdId: String = "",

    /** Referencia al padre. Null = categoría raíz. */
    
    val parentId: String? = null,

    /**
     * Código canónico jerárquico: "HOUSING", "HOUSING.TELEFONO".
     * Usado internamente para matching y en reglas de atribución YAML.
     */
    val code: String = "",

    
    val displayName: String = "",

    /** Clave del icono Material. */
    val icon: String? = null,

    
    val colorHex: String? = null,

    /**
     * Naturaleza económica: EXPENSE_FIXED, EXPENSE_VARIABLE,
     * EXPENSE_INSTALLMENT, TRANSFER_INTRA_HOUSEHOLD, SAVINGS,
     * INCOME, LOAN_RECEIVABLE.
     */
    val kind: String = "",

    /** Monto presupuestado por defecto por quincena. */
    
    val budgetDefaultMxn: Double? = null,

    val sortOrder: Int = 0
)
