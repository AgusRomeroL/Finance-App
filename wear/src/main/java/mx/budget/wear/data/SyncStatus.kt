package mx.budget.wear.data

import android.content.Context

/**
 * Salud del dato que el reloj esta a punto de enseñar.
 *
 * Vive en un solo sitio para que el hub, los seis tiles y las dos complications
 * no acaben con tres criterios distintos ni con tres redacciones distintas de la
 * misma frase en español.
 */
enum class SyncStatus {
    /** Hay telefono y el snapshot es reciente: no se dice nada. */
    OK,

    /** No hay telefono alcanzable. Lo mas accionable: acercarse o abrir la app. */
    DESCONECTADO,

    /** Nunca llego un snapshot. Distinto de "el saldo es cero". */
    NUNCA,

    /** Llego, pero hace demasiado. El numero puede haber cambiado sin avisar. */
    ANTIGUO;

    companion object {

        /**
         * Gana el peor. DESCONECTADO va primero porque es el unico sobre el que
         * la persona puede hacer algo, y porque con el telefono ausente la edad
         * del dato es una consecuencia, no una causa aparte.
         */
        fun current(context: Context): SyncStatus = when {
            !PhoneLink.isReachable(context) -> DESCONECTADO
            !WearCache.hasData(context) -> NUNCA
            WearCache.isStale(context) -> ANTIGUO
            else -> OK
        }

        /** "hace 40 min", "hace 7 h", "hace 2 d". Vacio si nunca llego nada. */
        fun ageLabel(context: Context): String {
            val ms = WearCache.snapshotAgeMs(context)
            if (ms == Long.MAX_VALUE) return ""
            val minutes = ms / 60_000
            return when {
                minutes < 60 -> "hace $minutes min"
                minutes < 60 * 48 -> "hace ${minutes / 60} h"
                else -> "hace ${minutes / (60 * 24)} d"
            }
        }

        /**
         * Linea corta para el hub y para el pie de los tiles. Vacia cuando todo
         * esta bien: en el caso normal, que es casi siempre, no ocupa ni un pixel.
         */
        fun shortLabel(context: Context): String = when (current(context)) {
            OK -> ""
            DESCONECTADO -> "Sin teléfono cerca"
            NUNCA -> "Todavía sin datos"
            ANTIGUO -> "Datos de ${ageLabel(context)}"
        }
    }
}
