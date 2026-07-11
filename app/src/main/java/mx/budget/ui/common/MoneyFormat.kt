package mx.budget.ui.common

import java.text.NumberFormat
import java.util.Locale

/**
 * Formato canónico de montos MXN para la UI: `"$1,234"` — agrupación es-MX,
 * sin centavos (el presupuesto opera en pesos enteros).
 *
 * Es EXACTAMENTE el formato que Wallets y Calendario ya usaban en helpers
 * privados duplicados; se centraliza aquí para que no diverja. NO cubre los
 * formatos deliberadamente distintos: `NumberFormat.getCurrencyInstance`
 * (con centavos, en Analíticas/Ledger/Saldos entre miembros) ni el signado
 * de WalletTransferSheet.
 */
private val mxnInt: NumberFormat = NumberFormat.getIntegerInstance(Locale("es", "MX"))

fun Double.toMxn(): String = "$" + mxnInt.format(this.toLong())
