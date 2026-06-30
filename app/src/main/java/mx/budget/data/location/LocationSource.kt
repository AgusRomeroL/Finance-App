package mx.budget.data.location

/**
 * Procedencia de la ubicación adjunta a un gasto/captura (Apéndice G.4.2).
 *
 * El significado nunca es ambiguo: distingue una ubicación tomada *en el momento*
 * del gasto de una añadida horas después. La atribución "ubicación del gasto =
 * donde estabas al gastar" solo es verdad para [CAPTURE]/[CONFIRM-dentro-de-ventana].
 */
object LocationSource {
    /** Fix fresco tomado en foreground al capturar (sheet in-app, o ingest persistente). */
    const val CAPTURE = "CAPTURE"

    /** Fix tomado al confirmar una captura de background dentro de la ventana corta. */
    const val CONFIRM = "CONFIRM"

    /** Ubicación añadida a mano por el usuario desde el detalle del gasto. */
    const val MANUAL = "MANUAL"

    /** Sin ubicación (permiso denegado, nivel NONE, fuera de ventana, o gasto sembrado). */
    const val NONE = "NONE"
}
