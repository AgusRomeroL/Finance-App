package mx.budget.data.local.result

/**
 * Comparación entre el saldo guardado de una cuenta y el que se deduce de sus
 * movimientos desde el ancla. Alimenta el aviso de divergencia de Cuentas.
 *
 * @param storedBalance lo que dice `payment_method.current_balance_mxn`, que es
 *  lo que la app muestra y lo que viaja a Firestore.
 * @param derivedBalance `opening_balance_mxn` más los movimientos posteriores a
 *  `balance_anchor_at`. Es función pura del estado de Room, así que dos
 *  dispositivos convergidos calculan el mismo número.
 * @param anchorAt instante del ancla. En 0 no hay ancla declarada (cuenta previa
 *  a la migración v21 que nunca se volvió a tocar) y no se compara nada.
 */
data class WalletBalanceDrift(
    val paymentMethodId: String,
    val storedBalance: Double,
    val derivedBalance: Double,
    val anchorAt: Long,
) {
    /** Diferencia con signo: positiva si el guardado va por encima del calculado. */
    val delta: Double get() = storedBalance - derivedBalance

    /**
     * ¿Vale la pena avisar? Un centavo de diferencia es ruido de coma flotante;
     * el umbral de medio peso evita alarmar por redondeos acumulados.
     */
    val hasDrift: Boolean get() = anchorAt > 0L && kotlin.math.abs(delta) >= 0.5
}
