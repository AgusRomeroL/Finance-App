package mx.budget.data.quincena

import mx.budget.data.local.entity.QuincenaEntity
import java.time.LocalDate

/**
 * Arrastre de la quincena inmediata anterior (Fase 7b, decision 14).
 *
 * Reglas que decidio Agustin el 2026-10-31:
 * - Solo cuenta la quincena **inmediata** anterior y solo si ya esta cerrada:
 *   sus cifras estan congeladas y viajan por sync, asi que los dos telefonos
 *   calculan lo mismo. Si sigue pendiente de cierre, el arrastre es cero hasta
 *   que se cierre ([pendingClose]).
 * - No se encadena: la anterior aporta su propio sobrante, sin el arrastre que
 *   ella recibio. Encadenarlo sumaria todo el historico sembrado.
 * - El deficit tambien se arrastra: el disponible tiene que ser dinero que de
 *   verdad hay.
 *
 * [amount] es el sobrante con la convencion de [QuincenaFigures]: el mayor
 * entre lo proyectado y lo recibido, menos lo gastado al cerrar.
 */
data class CarryOver(
    val amount: Double,
    val fromQuincenaId: String,
    val fromLabel: String,
    val pendingClose: Boolean,
) {
    /** Lo que de verdad suma a la quincena: cero mientras la anterior no cierre. */
    val effective: Double get() = if (pendingClose) 0.0 else amount
}

/** Fecha ISO del dia anterior al inicio de [quincena], o null si la fecha no es valida. */
fun previousQuincenaDate(quincena: QuincenaEntity): String? =
    runCatching { LocalDate.parse(quincena.startDate).minusDays(1).toString() }.getOrNull()

/** El arrastre que deja [previous], o null si no hay quincena anterior. */
fun carryOverFrom(previous: QuincenaEntity?): CarryOver? {
    if (previous == null) return null
    val closed = previous.status == "CLOSED"
    val income = maxOf(previous.projectedIncomeMxn, previous.actualIncomeMxn)
    return CarryOver(
        amount = if (closed) income - previous.actualExpensesMxn else 0.0,
        fromQuincenaId = previous.id,
        fromLabel = previous.label,
        pendingClose = !closed,
    )
}
