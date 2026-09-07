package mx.budget.data.local

import java.util.UUID
import mx.budget.data.local.entity.PaymentMethodEntity

/**
 * Cuentas por defecto que se ofrecen a un hogar que nace vacío.
 *
 * Un grupo creado o unido desde la app o la web solo trae miembros: sin cuentas
 * no se puede capturar nada, y la captura exige elegir una. El wizard de
 * onboarding resuelve eso para la primera instalación, pero no corre al entrar a
 * un grupo nuevo, así que ese camino terminaba en un callejón sin salida.
 *
 * Son las tres formas de pago que el propio wizard ofrece
 * (`OnboardingScreen.WALLET_KINDS`), con saldo en cero para que el usuario las
 * ajuste desde Cuentas. NO se siembran solas: se ofrecen con un toque desde el
 * vacío de Cuentas, porque inventarle cuentas a alguien sin preguntar es peor que
 * dejarle la pantalla vacía.
 *
 * Ids deterministas por hogar (mismo criterio que [DefaultCategoryCatalog]): si
 * dos dispositivos del mismo hogar aceptan la oferta, escriben las MISMAS filas
 * en vez de duplicarlas.
 */
object DefaultWalletCatalog {

    private data class Spec(val code: String, val name: String, val kind: String)

    private val CATALOG = listOf(
        Spec("cash", "Efectivo", "CASH"),
        Spec("debit", "Cuenta de débito", "DEBIT_ACCOUNT"),
        Spec("credit", "Tarjeta de crédito", "CREDIT_CARD"),
    )

    /** Etiqueta para la acción que las ofrece, en el orden del catálogo. */
    val displayNames: List<String> get() = CATALOG.map { it.name }

    fun build(householdId: String, now: Long = System.currentTimeMillis()): List<PaymentMethodEntity> =
        CATALOG.map { spec ->
            PaymentMethodEntity(
                id = deterministicId(householdId, spec.code),
                householdId = householdId,
                displayName = spec.name,
                kind = spec.kind,
                currentBalanceMxn = 0.0,
                openingBalanceMxn = 0.0,
                balanceAnchorAt = now,
                isActive = true,
                updatedAt = now,
            )
        }

    private fun deterministicId(householdId: String, code: String): String =
        UUID.nameUUIDFromBytes("$householdId::wallet::$code".toByteArray()).toString()
}
