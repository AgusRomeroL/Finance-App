package mx.budget.data.quincena

import mx.budget.data.local.entity.QuincenaEntity
import kotlin.math.min
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
 * - **Arrastre**: lo que sobro (o falto) en la quincena inmediata anterior, solo
 *   si ya esta cerrada ([CarryOver], Fase 7b, decision 14).
 * - **Gastado**: solo lo ejecutado (POSTED). Lo planeado no se ha pagado.
 * - **Reservado**: lo planeado prorrateado por la cadencia de su plantilla, que
 *   es lo que de esas obligaciones toca a esta quincena.
 * - **Topes por gastar**: lo que falta por gastar de cada tope por categoria
 *   ([CategoryCaps], Fase 7b, decision 13). Un gasto dentro de su tope no mueve
 *   el disponible, porque ya estaba apartado; solo el exceso lo baja.
 * - **Disponible**: ingreso mas arrastre, menos gastado, reservado y topes.
 *
 * [capsPending] y [carryOver] valen cero por defecto: el reporte, el libro de
 * Excel y la pantalla de cierre hablan de las cifras propias del periodo y no
 * los pasan.
 */
data class QuincenaFigures(
    val projectedIncome: Double,
    val receivedIncome: Double,
    val spent: Double,
    val reserved: Double,
    val projectedExpenses: Double,
    val capsPending: Double = 0.0,
    val carryOver: Double = 0.0,
) {
    /** Ingreso de referencia: el proyectado, o lo recibido si ya lo supera. */
    val income: Double get() = maxOf(projectedIncome, receivedIncome)

    /** Lo que hay para la quincena: el ingreso con el arrastre de la anterior. */
    val base: Double get() = income + carryOver

    /** Disponible bruto: sin apartar lo reservado ni los topes. */
    val gross: Double get() = base - spent

    /** Lo que queda por gastar tras descontar lo pagado, lo reservado y los topes. */
    val available: Double get() = gross - reserved - capsPending

    /** Lo que el disponible aparta para despues: planeados mas topes. */
    val setAside: Double get() = reserved + capsPending

    /** Porcentaje de ejecucion del gasto presupuestado, o cero si no hay presupuesto. */
    val executionPct: Int
        get() = if (projectedExpenses > 0) (spent / projectedExpenses * 100).roundToInt() else 0
}

/**
 * Lo que toca gastar por dia para que [disponible] alcance hasta el cierre.
 *
 * [disponible] tiene que ser la misma cifra que muestra el hero: la tarjeta
 * Ritmo partia de ingreso menos gastado y, con planeados pendientes, prometia
 * por dia mas dinero del que habia. Cuenta hoy, porque lo que se gaste hoy sale
 * del mismo dinero ([diasRestantesSinHoy] es lo que el hero escribe como "dias
 * restantes"). Con el disponible en cero o negativo no hay cifra por dia que
 * tenga sentido y devuelve null.
 */
fun disponiblePorDia(disponible: Double, diasRestantesSinHoy: Int): Double? {
    if (disponible <= 0.0 || diasRestantesSinHoy < 0) return null
    return disponible / (diasRestantesSinHoy + 1)
}

/**
 * Lo que toca gastar por semana: siete veces lo de un dia, sin pasar del
 * disponible cuando quedan menos de siete dias (contando hoy). Null en los
 * mismos casos que [disponiblePorDia].
 */
fun disponiblePorSemana(disponible: Double, diasRestantesSinHoy: Int): Double? {
    val porDia = disponiblePorDia(disponible, diasRestantesSinHoy) ?: return null
    val diasConHoy = diasRestantesSinHoy + 1
    return porDia * min(7, diasConHoy)
}

/** Las tres vistas que alterna la tarjeta Ritmo con un toque. */
enum class RitmoVista { DIA, SEMANA, QUINCENA }

/**
 * La cifra de la tarjeta Ritmo para [vista]. La vista de quincena es el
 * disponible entero; las de dia y semana devuelven null cuando no hay cifra que
 * repartir, y la tarjeta cae entonces al disponible.
 */
fun cifraRitmo(vista: RitmoVista, disponible: Double, diasRestantesSinHoy: Int): Double? = when (vista) {
    RitmoVista.DIA -> disponiblePorDia(disponible, diasRestantesSinHoy)
    RitmoVista.SEMANA -> disponiblePorSemana(disponible, diasRestantesSinHoy)
    RitmoVista.QUINCENA -> disponible
}

/**
 * Arma las cifras de [quincena] con la convencion del dashboard.
 *
 * [spent] y [reserved] deben venir de los totales en vivo. [projectedExpenses]
 * cae al presupuesto sumado por categoria cuando la columna de la quincena esta
 * en cero, que es lo normal en una quincena recien creada por el rollover.
 * [capsPending] y [carryOver] salen de [QuincenaFiguresSource].
 */
fun quincenaFigures(
    quincena: QuincenaEntity,
    receivedIncome: Double,
    spent: Double,
    reserved: Double,
    projectedExpensesFallback: Double = 0.0,
    capsPending: Double = 0.0,
    carryOver: Double = 0.0,
): QuincenaFigures = QuincenaFigures(
    projectedIncome = quincena.projectedIncomeMxn,
    receivedIncome = receivedIncome,
    spent = spent,
    reserved = reserved,
    projectedExpenses = quincena.projectedExpensesMxn.takeIf { it > 0.0 } ?: projectedExpensesFallback,
    capsPending = capsPending,
    carryOver = carryOver,
)
