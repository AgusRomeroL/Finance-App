package mx.budget.data.quincena

import mx.budget.data.local.dao.QuincenaDao

/**
 * Una quincena cerrada no admite movimientos nuevos ni cambios en los que ya
 * tiene. El mensaje es el que ve la persona, asi que dice que hacer.
 */
class QuincenaClosedException(val quincenaLabel: String) : IllegalStateException(
    "La quincena $quincenaLabel está cerrada. Reábrela desde Inicio para modificar sus movimientos."
)

/**
 * Guardia de la congelacion (RF-32).
 *
 * Vive en el repositorio y no en la interfaz: asi cubre a la vez la captura, el
 * detalle del gasto, el libro mayor, los recordatorios y cualquier pantalla
 * futura, y el rechazo ocurre dentro de la misma transaccion que la escritura.
 *
 * Dos exenciones deliberadas:
 * - El pull remoto escribe por DAO directo, nunca por los repos, asi que un
 *   dispositivo que edito antes de enterarse del cierre sigue convergiendo.
 * - Los inicializadores del sistema (sembrado de estados de cuenta, curacion de
 *   plantillas) y la aplicacion de un estado de cuenta usan una instancia del
 *   repositorio SIN guardia: siembran y reconcilian sobre periodos historicos
 *   que por definicion estan cerrados, y con guardia una instalacion limpia
 *   moriria en el primer arranque.
 */
class QuincenaFreezeGuard(private val dao: QuincenaDao) {

    /** Lanza [QuincenaClosedException] si la quincena esta cerrada. */
    suspend fun ensureEditable(quincenaId: String?) {
        if (quincenaId.isNullOrBlank()) return
        val quincena = dao.getById(quincenaId) ?: return
        if (quincena.status == QuincenaLifecycle.CLOSED) {
            throw QuincenaClosedException(quincena.label)
        }
    }

    /** Consulta sin excepcion, para que la interfaz oculte acciones imposibles. */
    suspend fun isClosed(quincenaId: String?): Boolean {
        if (quincenaId.isNullOrBlank()) return false
        return dao.getById(quincenaId)?.status == QuincenaLifecycle.CLOSED
    }
}
