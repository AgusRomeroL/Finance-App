package mx.budget.data.sync

import com.google.firebase.firestore.DocumentSnapshot
import mx.budget.data.local.entity.CategoryEntity
import mx.budget.data.local.entity.ExpenseAttributionEntity
import mx.budget.data.local.entity.ExpenseEntity
import mx.budget.data.local.entity.IncomeSourceEntity
import mx.budget.data.local.entity.InstallmentPlanEntity
import mx.budget.data.local.entity.LoanEntity
import mx.budget.data.local.entity.MemberEntity
import mx.budget.data.local.entity.PaymentMethodEntity
import mx.budget.data.local.entity.QuincenaEntity
import mx.budget.data.local.entity.SavingsGoalEntity
import mx.budget.data.local.entity.WalletTransferEntity

/**
 * Mappers manuales Firestore → entidades Room con **fallback dual de claves**
 * (MVP Fase 2d): cada campo se lee primero por su nombre camelCase (como lo
 * serializa el push de la app al hacer `set(entity)`) y, si no existe, por su
 * nombre snake_case (como lo sembró `scripts/seed_firebase.py` y como pueden
 * venir docs legados). Así el pull deserializa CUALQUIER doc del household sin
 * coordinar un re-seed entre dispositivos.
 *
 * Un mapper devuelve `null` si faltan campos obligatorios (doc corrupto o de
 * un esquema incompatible): el caller lo ignora y lo loguea, nunca crashea.
 *
 * El id de la entidad se toma SIEMPRE de `doc.id` (fuente canónica) con
 * fallback al campo, porque algunos docs sembrados podrían no repetir el id
 * en el cuerpo.
 */

private fun DocumentSnapshot.str(camel: String, snake: String): String? =
    (get(camel) ?: get(snake)) as? String

private fun DocumentSnapshot.dbl(camel: String, snake: String): Double? =
    ((get(camel) ?: get(snake)) as? Number)?.toDouble()

private fun DocumentSnapshot.lng(camel: String, snake: String): Long? =
    ((get(camel) ?: get(snake)) as? Number)?.toLong()

private fun DocumentSnapshot.int(camel: String, snake: String): Int? =
    ((get(camel) ?: get(snake)) as? Number)?.toInt()

private fun DocumentSnapshot.bool(camel: String, snake: String): Boolean? =
    (get(camel) ?: get(snake)) as? Boolean

fun DocumentSnapshot.toExpenseEntity(): ExpenseEntity? {
    return ExpenseEntity(
        id = id.ifBlank { str("id", "id") ?: return null },
        householdId = str("householdId", "household_id") ?: return null,
        occurredAt = lng("occurredAt", "occurred_at") ?: return null,
        quincenaId = str("quincenaId", "quincena_id") ?: return null,
        categoryId = str("categoryId", "category_id") ?: return null,
        concept = str("concept", "concept") ?: return null,
        amountMxn = dbl("amountMxn", "amount_mxn") ?: return null,
        paymentMethodId = str("paymentMethodId", "payment_method_id") ?: return null,
        recurrenceTemplateId = str("recurrenceTemplateId", "recurrence_template_id"),
        installmentPlanId = str("installmentPlanId", "installment_plan_id"),
        installmentNumber = int("installmentNumber", "installment_number"),
        installmentPrincipalMxn = dbl("installmentPrincipalMxn", "installment_principal_mxn"),
        installmentInterestMxn = dbl("installmentInterestMxn", "installment_interest_mxn"),
        status = str("status", "status") ?: "POSTED",
        notes = str("notes", "notes"),
        conceptCanonical = str("conceptCanonical", "concept_canonical"),
        createdAt = lng("createdAt", "created_at") ?: 0L,
        createdByMemberId = str("createdByMemberId", "created_by_member_id"),
        latitude = dbl("latitude", "latitude"),
        longitude = dbl("longitude", "longitude"),
        placeLabel = str("placeLabel", "place_label"),
        locationSource = str("locationSource", "location_source"),
        settlementStatus = str("settlementStatus", "settlement_status") ?: "NONE",
        externalPayerMemberId = str("externalPayerMemberId", "external_payer_member_id"),
        updatedAt = lng("updatedAt", "updated_at") ?: 0L,
    )
}

fun DocumentSnapshot.toExpenseAttributionEntity(expenseId: String): ExpenseAttributionEntity? {
    return ExpenseAttributionEntity(
        id = id.ifBlank { str("id", "id") ?: return null },
        expenseId = str("expenseId", "expense_id") ?: expenseId,
        memberId = str("memberId", "member_id") ?: return null,
        role = str("role", "role") ?: return null,
        shareBps = int("shareBps", "share_bps") ?: return null,
        shareAmountMxn = dbl("shareAmountMxn", "share_amount_mxn") ?: return null,
    )
}

fun DocumentSnapshot.toCategoryEntity(): CategoryEntity? {
    return CategoryEntity(
        id = id.ifBlank { str("id", "id") ?: return null },
        householdId = str("householdId", "household_id") ?: return null,
        parentId = str("parentId", "parent_id"),
        code = str("code", "code") ?: return null,
        displayName = str("displayName", "display_name") ?: return null,
        icon = str("icon", "icon"),
        suggestedEmoji = str("suggestedEmoji", "suggested_emoji"),
        colorHex = str("colorHex", "color_hex"),
        kind = str("kind", "kind") ?: return null,
        budgetDefaultMxn = dbl("budgetDefaultMxn", "budget_default_mxn"),
        sortOrder = int("sortOrder", "sort_order") ?: 0,
        updatedAt = lng("updatedAt", "updated_at") ?: 0L,
    )
}

fun DocumentSnapshot.toMemberEntity(): MemberEntity? {
    return MemberEntity(
        id = id.ifBlank { str("id", "id") ?: return null },
        householdId = str("householdId", "household_id") ?: return null,
        displayName = str("displayName", "display_name") ?: return null,
        shortAliases = str("shortAliases", "short_aliases") ?: "[]",
        role = str("role", "role") ?: return null,
        isActive = bool("isActive", "is_active") ?: true,
        defaultIncomeMxn = dbl("defaultIncomeMxn", "default_income_mxn"),
        meta = str("meta", "meta") ?: "{}",
        updatedAt = lng("updatedAt", "updated_at") ?: 0L,
    )
}

fun DocumentSnapshot.toPaymentMethodEntity(): PaymentMethodEntity? {
    return PaymentMethodEntity(
        id = id.ifBlank { str("id", "id") ?: return null },
        householdId = str("householdId", "household_id") ?: return null,
        displayName = str("displayName", "display_name") ?: return null,
        kind = str("kind", "kind") ?: return null,
        issuer = str("issuer", "issuer"),
        last4 = str("last4", "last4"),
        cutoffDay = int("cutoffDay", "cutoff_day"),
        dueDay = int("dueDay", "due_day"),
        creditLimitMxn = dbl("creditLimitMxn", "credit_limit_mxn"),
        currentBalanceMxn = dbl("currentBalanceMxn", "current_balance_mxn") ?: 0.0,
        openingBalanceMxn = dbl("openingBalanceMxn", "opening_balance_mxn") ?: 0.0,
        interestApr = dbl("interestApr", "interest_apr"),
        ownerMemberId = str("ownerMemberId", "owner_member_id"),
        isActive = bool("isActive", "is_active") ?: true,
        updatedAt = lng("updatedAt", "updated_at") ?: 0L,
    )
}

fun DocumentSnapshot.toQuincenaEntity(): QuincenaEntity? {
    return QuincenaEntity(
        id = id.ifBlank { str("id", "id") ?: return null },
        householdId = str("householdId", "household_id") ?: return null,
        year = int("year", "year") ?: return null,
        month = int("month", "month") ?: return null,
        half = str("half", "half") ?: return null,
        startDate = str("startDate", "start_date") ?: return null,
        endDate = str("endDate", "end_date") ?: return null,
        label = str("label", "label") ?: return null,
        projectedIncomeMxn = dbl("projectedIncomeMxn", "projected_income_mxn") ?: 0.0,
        projectedExpensesMxn = dbl("projectedExpensesMxn", "projected_expenses_mxn") ?: 0.0,
        actualIncomeMxn = dbl("actualIncomeMxn", "actual_income_mxn") ?: 0.0,
        actualExpensesMxn = dbl("actualExpensesMxn", "actual_expenses_mxn") ?: 0.0,
        status = str("status", "status") ?: "PROVISIONED",
        closedAt = lng("closedAt", "closed_at"),
        updatedAt = lng("updatedAt", "updated_at") ?: 0L,
    )
}

fun DocumentSnapshot.toWalletTransferEntity(): WalletTransferEntity? {
    return WalletTransferEntity(
        id = id.ifBlank { str("id", "id") ?: return null },
        householdId = str("householdId", "household_id") ?: return null,
        fromPaymentMethodId = str("fromPaymentMethodId", "from_payment_method_id") ?: return null,
        toPaymentMethodId = str("toPaymentMethodId", "to_payment_method_id") ?: return null,
        amountMxn = dbl("amountMxn", "amount_mxn") ?: return null,
        occurredAt = lng("occurredAt", "occurred_at") ?: return null,
        note = str("note", "note"),
        createdAt = lng("createdAt", "created_at") ?: 0L,
        updatedAt = lng("updatedAt", "updated_at") ?: 0L,
    )
}

fun DocumentSnapshot.toSavingsGoalEntity(): SavingsGoalEntity? {
    return SavingsGoalEntity(
        id = id.ifBlank { str("id", "id") ?: return null },
        householdId = str("householdId", "household_id") ?: return null,
        name = str("name", "name") ?: return null,
        targetMxn = dbl("targetMxn", "target_mxn") ?: return null,
        currentMxn = dbl("currentMxn", "current_mxn") ?: 0.0,
        targetDate = str("targetDate", "target_date"),
        linkedPaymentMethodId = str("linkedPaymentMethodId", "linked_payment_method_id"),
        updatedAt = lng("updatedAt", "updated_at") ?: 0L,
    )
}

fun DocumentSnapshot.toLoanEntity(): LoanEntity? {
    return LoanEntity(
        id = id.ifBlank { str("id", "id") ?: return null },
        householdId = str("householdId", "household_id") ?: return null,
        debtorMemberId = str("debtorMemberId", "debtor_member_id") ?: return null,
        principalMxn = dbl("principalMxn", "principal_mxn") ?: return null,
        remainingBalanceMxn = dbl("remainingBalanceMxn", "remaining_balance_mxn") ?: return null,
        agreedInterestMxn = dbl("agreedInterestMxn", "agreed_interest_mxn") ?: 0.0,
        issuedAt = str("issuedAt", "issued_at") ?: return null,
        dueAt = str("dueAt", "due_at"),
        paymentScheduleId = str("paymentScheduleId", "payment_schedule_id"),
        notes = str("notes", "notes"),
        paymentCount = int("paymentCount", "payment_count"),
        paymentFrequency = str("paymentFrequency", "payment_frequency"),
        paymentAmountMxn = dbl("paymentAmountMxn", "payment_amount_mxn"),
        scheduleStartDate = str("scheduleStartDate", "schedule_start_date"),
        updatedAt = lng("updatedAt", "updated_at") ?: 0L,
    )
}

fun DocumentSnapshot.toInstallmentPlanEntity(): InstallmentPlanEntity? {
    return InstallmentPlanEntity(
        id = id.ifBlank { str("id", "id") ?: return null },
        householdId = str("householdId", "household_id") ?: return null,
        displayName = str("displayName", "display_name") ?: return null,
        creditorMemberId = str("creditorMemberId", "creditor_member_id"),
        paymentMethodId = str("paymentMethodId", "payment_method_id"),
        principalMxn = dbl("principalMxn", "principal_mxn") ?: return null,
        totalInstallments = int("totalInstallments", "total_installments") ?: return null,
        installmentAmountMxn = dbl("installmentAmountMxn", "installment_amount_mxn") ?: return null,
        interestRateApr = dbl("interestRateApr", "interest_rate_apr"),
        startDate = str("startDate", "start_date") ?: return null,
        currentInstallment = int("currentInstallment", "current_installment") ?: 0,
        status = str("status", "status") ?: "ACTIVE",
        categoryId = str("categoryId", "category_id"),
        updatedAt = lng("updatedAt", "updated_at") ?: 0L,
    )
}

fun DocumentSnapshot.toIncomeSourceEntity(): IncomeSourceEntity? {
    return IncomeSourceEntity(
        id = id.ifBlank { str("id", "id") ?: return null },
        householdId = str("householdId", "household_id") ?: return null,
        quincenaId = str("quincenaId", "quincena_id") ?: return null,
        memberId = str("memberId", "member_id") ?: return null,
        label = str("label", "label") ?: return null,
        amountMxn = dbl("amountMxn", "amount_mxn") ?: return null,
        cadence = str("cadence", "cadence") ?: "QUINCENAL",
        expectedDate = str("expectedDate", "expected_date") ?: return null,
        paymentMethodId = str("paymentMethodId", "payment_method_id"),
        status = str("status", "status") ?: "PLANNED",
        createdAt = lng("createdAt", "created_at") ?: 0L,
        updatedAt = lng("updatedAt", "updated_at") ?: 0L,
    )
}
