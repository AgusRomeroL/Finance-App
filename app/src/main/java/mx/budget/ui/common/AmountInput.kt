package mx.budget.ui.common

/**
 * Utilidades para los campos de monto en pesos.
 *
 * Los montos se guardan como `Double` en pesos (no en centavos enteros), asi que
 * un campo que trunque a entero pierde centavos de verdad: abrir una cuenta con
 * saldo 1,234.56 y volver a guardarla la dejaba en 1,234.00, en silencio y en
 * cada edicion.
 */

/**
 * Limpia la entrada de un monto: conserva digitos, un solo punto decimal y hasta
 * dos decimales. Evita entradas malformadas como "1.2.3", que rompian el parseo
 * y acababan en cero.
 *
 * Con [allowNegative] admite un signo menos inicial, para los campos donde un
 * valor negativo es un dato legitimo, como el saldo real de una cuenta de debito
 * en sobregiro.
 */
fun sanitizeAmountInput(raw: String, allowNegative: Boolean = false): String {
    val negative = allowNegative && raw.trimStart().startsWith("-")
    val filtered = raw.filter { it.isDigit() || it == '.' }
    val dot = filtered.indexOf('.')
    val body = if (dot < 0) filtered else {
        val intPart = filtered.substring(0, dot)
        val decPart = filtered.substring(dot + 1).filter { it.isDigit() }.take(2)
        "$intPart.$decPart"
    }
    return if (negative) "-$body" else body
}

/** Interpreta el texto de un campo de monto ya saneado. */
fun String.toAmountOrNull(): Double? = toDoubleOrNull()

/**
 * Texto inicial de un campo de monto: entero cuando no hay fraccion, con
 * decimales cuando si la hay. Es el criterio que ya usaban la captura y el
 * detalle de gasto; aqui se centraliza para que ninguna pantalla vuelva a
 * truncar con `toLong()`.
 */
fun Double.toAmountInput(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString()
