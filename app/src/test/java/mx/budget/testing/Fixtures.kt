package mx.budget.testing

import mx.budget.data.local.entity.CategoryEntity
import mx.budget.data.local.entity.ExpenseEntity
import mx.budget.data.local.entity.InstallmentPlanEntity
import mx.budget.data.local.entity.MemberEntity
import mx.budget.data.local.entity.PaymentMethodEntity
import mx.budget.data.local.entity.QuincenaEntity
import mx.budget.data.local.entity.RecurrenceTemplateEntity
import java.time.LocalDate
import java.time.ZoneId

/**
 * Constructores de entidades con valores por defecto sensatos para las pruebas
 * JVM. Solo se nombran los campos que cada prueba quiere controlar.
 */
object Fixtures {

    const val HID = "default_household"

    val MX: ZoneId = ZoneId.of("America/Mexico_City")

    /** Epoch millis de [date] a las 12:00 en la zona del hogar. */
    fun noonMx(date: LocalDate): Long =
        date.atTime(12, 0).atZone(MX).toInstant().toEpochMilli()

    fun expense(
        id: String,
        date: LocalDate,
        amount: Double,
        concept: String = "Netflix",
        canonical: String? = "netflix",
        status: String = "POSTED",
        categoryId: String = "cat-1",
        walletId: String = "w-1",
        quincenaId: String = "q-2026-09-FIRST",
    ) = ExpenseEntity(
        id = id,
        householdId = HID,
        occurredAt = noonMx(date),
        quincenaId = quincenaId,
        categoryId = categoryId,
        concept = concept,
        amountMxn = amount,
        paymentMethodId = walletId,
        status = status,
        conceptCanonical = canonical,
        createdAt = 0L,
    )

    fun quincena(
        year: Int,
        month: Int,
        half: String,
        projectedIncome: Double = 0.0,
        projectedExpenses: Double = 0.0,
    ): QuincenaEntity {
        val first = half == "FIRST"
        val start = LocalDate.of(year, month, if (first) 1 else 16)
        val end = if (first) LocalDate.of(year, month, 15)
        else LocalDate.of(year, month, 1).plusMonths(1).minusDays(1)
        return QuincenaEntity(
            id = "q-%04d-%02d-%s".format(year, month, half),
            householdId = HID,
            year = year,
            month = month,
            half = half,
            startDate = start.toString(),
            endDate = end.toString(),
            label = "Quincena $year-$month $half",
            projectedIncomeMxn = projectedIncome,
            projectedExpensesMxn = projectedExpenses,
            status = "ACTIVE",
        )
    }

    fun template(
        id: String = "t-1",
        cadence: String,
        dayOfMonth: Int? = null,
        amount: Double = 250.0,
        walletId: String? = "w-1",
        beneficiaries: String = "[]",
        payerSplit: String = "{}",
        externalPayerId: String? = null,
        settlement: String = "NONE",
        nextExpectedDate: String? = null,
        anchorMonth: Int? = null,
    ) = RecurrenceTemplateEntity(
        id = id,
        householdId = HID,
        concept = "Netflix",
        categoryId = "cat-1",
        defaultAmountMxn = amount,
        defaultPaymentMethodId = walletId,
        cadence = cadence,
        cadenceDetail = buildString {
            append("{")
            val parts = mutableListOf<String>()
            if (dayOfMonth != null) parts += "\"day_of_month\": $dayOfMonth"
            if (anchorMonth != null) parts += "\"anchor_month\": $anchorMonth"
            append(parts.joinToString(", "))
            append("}")
        },
        nextExpectedDate = nextExpectedDate,
        defaultBeneficiaryIds = beneficiaries,
        defaultPayerSplit = payerSplit,
        defaultExternalPayerMemberId = externalPayerId,
        defaultSettlementStatus = settlement,
    )

    fun wallet(
        id: String = "w-1",
        name: String = "BBVA",
        kind: String = "DEBIT_ACCOUNT",
        cutoffDay: Int? = null,
        dueDay: Int? = null,
        ownerMemberId: String? = "m-1",
        active: Boolean = true,
    ) = PaymentMethodEntity(
        id = id,
        householdId = HID,
        displayName = name,
        kind = kind,
        cutoffDay = cutoffDay,
        dueDay = dueDay,
        ownerMemberId = ownerMemberId,
        isActive = active,
    )

    fun member(
        id: String,
        name: String,
        role: String = "PAYER_ADULT",
        aliases: String = "[]",
    ) = MemberEntity(
        id = id,
        householdId = HID,
        displayName = name,
        shortAliases = aliases,
        role = role,
    )

    fun category(code: String, name: String, id: String = code) = CategoryEntity(
        id = id,
        householdId = HID,
        code = code,
        displayName = name,
        kind = "EXPENSE",
    )

    fun plan(id: String, name: String) = InstallmentPlanEntity(
        id = id,
        householdId = HID,
        displayName = name,
        principalMxn = 1200.0,
        totalInstallments = 12,
        installmentAmountMxn = 100.0,
        startDate = "2026-01-15",
    )
}
