package mx.budget.data.quincena

import mx.budget.data.local.result.CategoryCapRow

/**
 * Lo que falta por gastar de cada tope por categoria (Fase 7b, decision 13).
 *
 * Lukas resta de su disponible lo que falta de cada limite activo; aqui se hace
 * igual con `category.budget_default_mxn`, que es un tope por quincena. El
 * planeado de la categoria se descuenta del tope porque ese dinero ya lo aparta
 * "Reservado": sin eso se contaria dos veces. Un tope excedido aporta cero, asi
 * que el exceso solo baja el disponible por el lado de lo gastado.
 *
 * El tope cubre solo los gastos de su misma categoria, sin sumar las hijas, como
 * las barras de Analiticas.
 *
 * Solo llegan aqui los topes de gasto libre: `AnalyticsDao.getCategoryCaps`
 * descarta las categorias con una plantilla recurrente activa (decision del
 * 2026-10-31), porque su cargo ya lo aparta "Reservado". En la semilla quedan
 * Comida, Despensa, Comida gatas y Gasolina; las suscripciones y servicios ya no
 * apartan su tope dos veces ni avisan al pagarse.
 */
object CategoryCaps {

    /** Lo que falta por gastar de [row], nunca negativo. */
    fun pending(row: CategoryCapRow): Double =
        (row.cap - row.spent - row.plannedProrated).coerceAtLeast(0.0)

    /** Suma de lo pendiente de todos los topes. */
    fun totalPending(rows: List<CategoryCapRow>): Double = rows.sumOf { pending(it) }
}
