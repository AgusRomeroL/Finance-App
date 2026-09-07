package mx.budget.data.quincena

import mx.budget.data.local.entity.QuincenaEntity
import kotlin.math.roundToInt

/**
 * Cifras de una quincena segun la convencion del dashboard.
 *
 * Antes cada superficie las calculaba a su manera y se contradecian: el
 * dashboard usaba los totales en vivo, el resumen de Analiticas partia de la
 * columna desnormalizada `actual_expenses_mxn` y el analisis abierto del
 * asistente usaba una tercera combinacion. Como esas columnas agregadas de la
 * quincena no se mantienen y llegan en cero, cada pantalla contaba una historia
 * distinta del mismo dia.
 *
 * La convencion, que aqui queda en un solo sitio:
 * - **Ingreso**: el mayor entre el proyectado de la quincena y el realmente
 *   recibido. En cuanto entra mas dinero del planeado, manda lo recibido.
 * - **Gastado**: solo lo ejecutado (POSTED). Lo planeado no se ha pagado.
 * - **Reservado**: lo planeado prorrateado por la cadencia de su plantilla, que
 *   es lo que de esas obligaciones toca a esta quincena.
 * - **Disponible**: ingreso menos gastado menos reservado.
 */
data class QuincenaFigures(
    val projectedIncome: Double,
    val receivedIncome: Double,
    val spent: Double,
    val reserved: Double,
    val projectedExpenses: Double,
) {
    /** Ingreso de referencia: el proyectado, o lo recibido si ya lo supera. */
    val income: Double get() = maxOf(projectedIncome, receivedIncome)

    /** Lo que queda por gastar tras descontar lo pagado y lo reservado. */
    val available: Double get() = income - spent - reserved

    /** Porcentaje de ejecucion del gasto presupuestado, o cero si no hay presupuesto. */
    val executionPct: Int
        get() = if (projectedExpenses > 0) (spent / projectedExpenses * 100).roundToInt() else 0
}

/**
 * Arma las cifras de [quincena] con la convencion del dashboard.
 *
 * [spent] y [reserved] deben venir de los totales en vivo. [projectedExpenses]
 * cae al presupuesto sumado por categoria cuando la columna de la quincena esta
 * en cero, que es lo normal en una quincena recien creada por el rollover.
 */
fun quincenaFigures(
    quincena: QuincenaEntity,
    receivedIncome: Double,
    spent: Double,
    reserved: Double,
    projectedExpensesFallback: Double = 0.0,
): QuincenaFigures = QuincenaFigures(
    projectedIncome = quincena.projectedIncomeMxn,
    receivedIncome = receivedIncome,
    spent = spent,
    reserved = reserved,
    projectedExpenses = quincena.projectedExpensesMxn.takeIf { it > 0.0 } ?: projectedExpensesFallback,
)
