package mx.budget.data.local

import java.util.UUID
import mx.budget.data.local.entity.CategoryEntity

/**
 * Catálogo de categorías predeterminadas para un hogar mexicano.
 *
 * Se usa como semilla de arranque SOLO cuando un household está vacío de
 * categorías (ver [mx.budget.data.repository.CategoryRepository.seedDefaultsIfEmpty]),
 * para eliminar el callejón sin salida de la captura: crear una categoría nueva
 * pide "elige el grupo", y sin grupos (categorías con parentId == null) el
 * usuario no puede avanzar.
 *
 * Los households sembrados (asset del Excel) YA traen grupos, así que este
 * catálogo NUNCA se aplica sobre ellos (la guarda de "vacío" es no-op).
 *
 * Las filas resultantes son categorías Room normales: el usuario puede
 * editarlas, borrarlas y agregar más desde la captura y desde Ajustes.
 *
 * **ids deterministas:** se derivan de `householdId + code` vía
 * [UUID.nameUUIDFromBytes], no aleatorios. Así, si por cualquier motivo el
 * seeding corriera dos veces sobre el mismo hogar (no debería, está guardado),
 * los ids coinciden y el índice único (household_id, code) evita duplicados.
 */
object DefaultCategoryCatalog {

    /** kind por defecto para grupos/hojas de gasto sin naturaleza fija. */
    private const val EXPENSE_VARIABLE = "EXPENSE_VARIABLE"
    private const val EXPENSE_FIXED = "EXPENSE_FIXED"
    private const val INCOME = "INCOME"

    /**
     * Estructura declarativa del catálogo: grupo raíz + sus hojas.
     * El `code` del grupo es el prefijo jerárquico; el de la hoja es
     * `GRUPO.HOJA`. El `kind` de la hoja hereda el del grupo.
     */
    private data class Group(
        val code: String,
        val displayName: String,
        val kind: String,
        val leaves: List<Leaf>,
    )

    private data class Leaf(
        val codeSegment: String,
        val displayName: String,
    )

    private val CATALOG: List<Group> = listOf(
        Group(
            code = "VIVIENDA", displayName = "Vivienda", kind = EXPENSE_FIXED,
            leaves = listOf(
                Leaf("RENTA_HIPOTECA", "Renta / Hipoteca"),
                Leaf("LUZ", "Luz"),
                Leaf("AGUA", "Agua"),
                Leaf("GAS", "Gas"),
                Leaf("INTERNET", "Internet"),
            ),
        ),
        Group(
            code = "ALIMENTACION", displayName = "Alimentación", kind = EXPENSE_VARIABLE,
            leaves = listOf(
                Leaf("SUPER", "Súper"),
                Leaf("RESTAURANTES", "Restaurantes"),
                Leaf("CAFE", "Café"),
            ),
        ),
        Group(
            code = "TRANSPORTE", displayName = "Transporte", kind = EXPENSE_VARIABLE,
            leaves = listOf(
                Leaf("GASOLINA", "Gasolina"),
                Leaf("TRANSPORTE_PUBLICO", "Transporte público"),
                Leaf("UBER_DIDI", "Uber / DiDi"),
                Leaf("MANTENIMIENTO_AUTO", "Mantenimiento auto"),
            ),
        ),
        Group(
            code = "SALUD", displayName = "Salud", kind = EXPENSE_VARIABLE,
            leaves = listOf(
                Leaf("CONSULTAS", "Consultas"),
                Leaf("MEDICINAS", "Medicinas"),
                Leaf("SEGURO_MEDICO", "Seguro médico"),
            ),
        ),
        Group(
            code = "EDUCACION", displayName = "Educación", kind = EXPENSE_VARIABLE,
            leaves = listOf(
                Leaf("COLEGIATURAS", "Colegiaturas"),
                Leaf("UTILES", "Útiles"),
            ),
        ),
        Group(
            code = "ENTRETENIMIENTO", displayName = "Entretenimiento", kind = EXPENSE_VARIABLE,
            leaves = listOf(
                Leaf("STREAMING", "Streaming"),
                Leaf("SALIDAS", "Salidas"),
                Leaf("SUSCRIPCIONES", "Suscripciones"),
            ),
        ),
        Group(
            code = "PERSONAL", displayName = "Servicios / Personal", kind = EXPENSE_VARIABLE,
            leaves = listOf(
                Leaf("ROPA", "Ropa"),
                Leaf("CUIDADO_PERSONAL", "Cuidado personal"),
                Leaf("TELEFONO", "Teléfono"),
            ),
        ),
        Group(
            code = "OTROS", displayName = "Otros", kind = EXPENSE_VARIABLE,
            leaves = emptyList(),
        ),
        Group(
            code = "INGRESOS", displayName = "Ingresos", kind = INCOME,
            leaves = listOf(
                Leaf("SUELDO", "Sueldo"),
                Leaf("HONORARIOS", "Honorarios"),
                Leaf("OTROS_INGRESOS", "Otros ingresos"),
            ),
        ),
    )

    /**
     * Genera la lista COMPLETA de categorías (grupos + hojas) para [householdId].
     *
     * - Grupos: `parentId = null`, `sortOrder` incremental (10, 20, 30, …).
     * - Hojas: `parentId = <id del grupo>`, `code` jerárquico `GRUPO.HOJA`,
     *   `kind` heredado del grupo, `sortOrder` incremental dentro del grupo.
     *
     * Los ids son deterministas ([deterministicId]) para idempotencia por id
     * además del guardado por "vacío".
     */
    fun build(householdId: String): List<CategoryEntity> {
        val result = mutableListOf<CategoryEntity>()
        CATALOG.forEachIndexed { groupIndex, group ->
            val groupId = deterministicId(householdId, group.code)
            result += CategoryEntity(
                id = groupId,
                householdId = householdId,
                parentId = null,
                code = group.code,
                displayName = group.displayName,
                kind = group.kind,
                sortOrder = (groupIndex + 1) * 10,
            )
            group.leaves.forEachIndexed { leafIndex, leaf ->
                val leafCode = "${group.code}.${leaf.codeSegment}"
                result += CategoryEntity(
                    id = deterministicId(householdId, leafCode),
                    householdId = householdId,
                    parentId = groupId,
                    code = leafCode,
                    displayName = leaf.displayName,
                    kind = group.kind,
                    sortOrder = (leafIndex + 1) * 10,
                )
            }
        }
        return result
    }

    private fun deterministicId(householdId: String, code: String): String =
        UUID.nameUUIDFromBytes("$householdId::$code".toByteArray(Charsets.UTF_8)).toString()
}
