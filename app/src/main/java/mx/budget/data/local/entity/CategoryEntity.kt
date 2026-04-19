package mx.budget.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
@Entity(
    tableName = "category",
    foreignKeys = [
        ForeignKey(
            entity = HouseholdEntity::class,
            parentColumns = ["id"],
            childColumns = ["household_id"]
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["parent_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["household_id", "code"], unique = true),
        Index(value = ["parent_id"])
    ]
)
data class CategoryEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "household_id")
    val householdId: String,

    /** Referencia al padre. Null = categoría raíz. */
    @ColumnInfo(name = "parent_id")
    val parentId: String? = null,

    /**
     * Código canónico jerárquico: "HOUSING", "HOUSING.TELEFONO".
     * Usado internamente para matching y en reglas de atribución YAML.
     */
    val code: String,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    /** Clave del icono Material. */
    val icon: String? = null,

    @ColumnInfo(name = "color_hex")
    val colorHex: String? = null,

    /**
     * Naturaleza económica: EXPENSE_FIXED, EXPENSE_VARIABLE,
     * EXPENSE_INSTALLMENT, TRANSFER_INTRA_HOUSEHOLD, SAVINGS,
     * INCOME, LOAN_RECEIVABLE.
     */
    val kind: String,

    /** Monto presupuestado por defecto por quincena. */
    @ColumnInfo(name = "budget_default_mxn")
    val budgetDefaultMxn: Double? = null,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0
)
