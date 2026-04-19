package mx.budget.core.model

/**
 * Rol de un miembro dentro del hogar.
 *
 * Define la relación económica del individuo con el presupuesto familiar:
 * pagadores adultos aportan ingresos, dependientes reciben beneficios,
 * y los externos participan en transacciones puntuales.
 */
enum class MemberRole {
    /** Adulto que aporta ingresos al hogar (ej. Benjamín, Norma). */
    PAYER_ADULT,
    /** Dependiente económico (ej. Pau, David, Agustín, Santi). */
    BENEFICIARY_DEPENDENT,
    /** Acreedor externo al que el hogar debe dinero (ej. Omar). */
    EXTERNAL_CREDITOR,
    /** Deudor externo que debe dinero al hogar (ej. Jaudiel). */
    EXTERNAL_DEBTOR,
    /** Proveedor de servicios externo (ej. Araceli). */
    EXTERNAL_SERVICE
}

/**
 * Naturaleza económica de una categoría de gasto.
 *
 * Permite al motor analítico tratar cada categoría según su comportamiento
 * presupuestal: fijo vs variable, y distinguir gastos de transferencias
 * internas, ahorro e ingresos.
 */
enum class CategoryKind {
    /** Gasto fijo predecible (hipoteca, internet). */
    EXPENSE_FIXED,
    /** Gasto variable no predecible (despensa, diversión). */
    EXPENSE_VARIABLE,
    /** Gasto en cuotas con intereses (préstamos, BNPL). */
    EXPENSE_INSTALLMENT,
    /** Transferencia interna del hogar: mesadas, pensiones (antes "TAXES"). */
    TRANSFER_INTRA_HOUSEHOLD,
    /** Destino de ahorro (tarjeta de ahorro, retirement). */
    SAVINGS,
    /** Fuente de ingreso (sueldos, extras). */
    INCOME,
    /** Préstamo otorgado a terceros. */
    LOAN_RECEIVABLE
}

/**
 * Tipo de instrumento financiero o método de pago.
 *
 * Mapea directamente a los 11 medios de pago detectados en el Excel:
 * Banamex débito, BBVA, Banamex Clásica, Mercado Pago, Mercado Libre BNPL,
 * Coppel, Liverpool, Sears, Walmart, Klar, Efectivo, Ahorro empresa.
 */
enum class PaymentMethodKind {
    /** Cuenta de débito bancaria (Banamex, BBVA). */
    DEBIT_ACCOUNT,
    /** Tarjeta de crédito bancaria (Banamex Clásica). */
    CREDIT_CARD,
    /** Tarjeta departamental (Coppel, Liverpool, Sears, Walmart). */
    DEPARTMENT_STORE_CARD,
    /** Compra a plazos sin tarjeta (Mercado Libre BNPL). */
    BNPL_INSTALLMENT,
    /** Monedero digital (Mercado Pago, Klar). */
    DIGITAL_WALLET,
    /** Efectivo físico. */
    CASH,
    /** Fondo de ahorro del empleador. */
    EMPLOYER_SAVINGS_FUND
}

/**
 * Estado del ciclo de vida de una quincena.
 *
 * Implementa el DFA: PROVISIONED → ACTIVE → CLOSING_REVIEW → CLOSED.
 * Solo una quincena puede estar ACTIVE por household en cualquier momento.
 */
enum class QuincenaStatus {
    /** Creada T-3, plantillas materializadas, aún no operativa. */
    PROVISIONED,
    /** Única quincena activa. Acepta registro de gastos. */
    ACTIVE,
    /** Revisión previa al cierre: validar PLANNED sin ejecutar, varianza. */
    CLOSING_REVIEW,
    /** Cerrada e inmutable. Datos congelados para analíticas históricas. */
    CLOSED
}

/**
 * Mitad del mes a la que corresponde una quincena.
 *
 * Anclaje CALENDAR: FIRST = días 1-15, SECOND = días 16-fin.
 */
enum class QuincenaHalf {
    /** Quincena del 1 al 15 del mes. */
    FIRST,
    /** Quincena del 16 al último día del mes. */
    SECOND
}

/**
 * Estado de un registro de gasto individual.
 *
 * Flujo: PLANNED → POSTED → RECONCILED.
 * PLANNED = presupuestado pero no ejecutado.
 * POSTED = confirmado / ejecutado.
 * RECONCILED = casado con estado de cuenta bancario.
 */
enum class ExpenseStatus {
    /** Gasto presupuestado, aún no ejecutado. Se muestra como outline en UI. */
    PLANNED,
    /** Gasto confirmado y ejecutado. Afecta saldos de wallet. */
    POSTED,
    /** Gasto conciliado contra estado de cuenta bancario. */
    RECONCILED
}

/**
 * Rol de un miembro en la tabla de atribución de gastos.
 *
 * Cada gasto tiene dos particiones independientes:
 * - BENEFICIARY: quién consume o recibe el bien/servicio.
 * - PAYER: quién desembolsa el dinero.
 *
 * Ambas particiones deben sumar 10,000 basis points (= 100%).
 */
enum class AttributionRole {
    /** Recibe el bien o servicio (puede ser múltiple). */
    BENEFICIARY,
    /** Paga el gasto (puede ser múltiple con reparto proporcional). */
    PAYER
}

/**
 * Cadencia de un gasto recurrente.
 *
 * Cubre los patrones detectados en el Excel: quincenales, mensuales,
 * bimestrales y expresiones custom.
 */
enum class RecurrenceCadence {
    /** Solo quincenas 1-15. */
    QUINCENAL_FIRST,
    /** Solo quincenas 16-fin. */
    QUINCENAL_SECOND,
    /** Ambas quincenas del mes. */
    QUINCENAL_EVERY,
    /** Una vez al mes en una quincena específica. */
    MONTHLY_SPECIFIC_HALF,
    /** Cada dos meses (ej. agua, electricidad). */
    BIMONTHLY,
    /** Expresión cron personalizada. */
    CUSTOM_CRON
}

/**
 * Estado de un plan de cuotas.
 */
enum class InstallmentStatus {
    /** Plan en curso con cuotas pendientes. */
    ACTIVE,
    /** Todas las cuotas pagadas. */
    PAID_OFF,
    /** En incumplimiento. */
    DEFAULTED
}

/**
 * Cadencia de un ingreso recurrente.
 */
enum class IncomeCadence {
    /** Ingreso cada quincena. */
    QUINCENAL,
    /** Ingreso mensual. */
    MONTHLY,
    /** Ingreso sin frecuencia fija. */
    IRREGULAR
}

/**
 * Ancla del calendario quincenal del hogar.
 */
enum class QuincenaAnchor {
    /** 1-15 / 16-fin (default MX). */
    CALENDAR,
    /** Cada 14 días desde un epoch configurable. */
    BIWEEKLY
}
