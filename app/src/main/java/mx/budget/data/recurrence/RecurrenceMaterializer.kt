package mx.budget.data.recurrence

import mx.budget.data.local.dao.ExpenseDao
import mx.budget.data.local.dao.MemberDao
import mx.budget.data.local.dao.PaymentMethodDao
import mx.budget.data.local.dao.RecurrenceTemplateDao
import mx.budget.data.local.entity.ExpenseAttributionEntity
import mx.budget.data.local.entity.ExpenseEntity
import mx.budget.data.local.entity.QuincenaEntity
import mx.budget.data.local.entity.RecurrenceTemplateEntity
import mx.budget.data.repository.ExpenseRepository
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

/**
 * Materializa gastos **PLANNED** desde las plantillas recurrentes activas para una
 * quincena dada (Apéndice G.2, Fase 1). Es el corazón del modelo de calendario:
 * cada plantilla cuya cadencia "cae" en la quincena produce un gasto `status=PLANNED`
 * (presupuestado, aún no ejecutado) con sus atribuciones desde los splits default.
 * Confirmar (Fase 2) lo pasa a POSTED.
 *
 * **Idempotente:** no crea un PLANNED si la plantilla ya tiene un gasto (PLANNED o
 * POSTED) en esa quincena (`ExpenseDao.countForTemplateInQuincena`). Así puede
 * correr en cada arranque / activación de quincena sin duplicar.
 *
 * La proyección cadencia→quincena (§G.2.3) es nativa: `RecurrenceCadence` ya se
 * expresa en términos de quincena. `CUSTOM_CRON` queda diferido (devuelve null).
 */
class RecurrenceMaterializer(
    private val recurrenceDao: RecurrenceTemplateDao,
    private val expenseRepository: ExpenseRepository,
    private val expenseDao: ExpenseDao,
    private val paymentMethodDao: PaymentMethodDao,
    private val memberDao: MemberDao,
    private val householdId: String,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val zone: ZoneId = ZoneId.of("America/Mexico_City"),
) {

    /**
     * Crea los PLANNED faltantes de [quincena] desde las plantillas activas.
     * Devuelve cuántos creó. Cada plantilla se aísla: una que falle (FK inválida,
     * splits corruptos) no rompe el lote.
     */
    suspend fun materialize(quincena: QuincenaEntity): Int {
        val templates = recurrenceDao.getActive(householdId)
        if (templates.isEmpty()) return 0

        val wallets = paymentMethodDao.getActive(householdId)
        val members = memberDao.getActiveMembers(householdId)
            .filter { !it.role.startsWith("EXTERNAL_") }
        var created = 0

        for (template in templates) {
            val date = occurrenceDate(template, quincena) ?: continue
            // Idempotencia: ya materializado o confirmado en esta quincena.
            if (expenseDao.countForTemplateInQuincena(template.id, quincena.id) > 0) continue

            val ok = runCatching { materializeOne(template, quincena, date, wallets, members) }
                .getOrDefault(false)
            if (ok) created++
        }
        return created
    }

    private suspend fun materializeOne(
        template: RecurrenceTemplateEntity,
        quincena: QuincenaEntity,
        date: LocalDate,
        wallets: List<mx.budget.data.local.entity.PaymentMethodEntity>,
        members: List<mx.budget.data.local.entity.MemberEntity>,
    ): Boolean {
        // El gasto exige payment_method (NOT NULL): usa el de la plantilla o el primero activo.
        val walletId = template.defaultPaymentMethodId ?: wallets.firstOrNull()?.id ?: return false
        val wallet = wallets.firstOrNull { it.id == walletId }

        val occurredAt = date.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val now = nowProvider()
        val expenseId = UUID.randomUUID().toString()

        val expense = ExpenseEntity(
            id = expenseId,
            householdId = householdId,
            occurredAt = occurredAt,
            quincenaId = quincena.id,
            categoryId = template.categoryId,
            concept = template.concept,
            amountMxn = template.defaultAmountMxn,
            paymentMethodId = walletId,
            recurrenceTemplateId = template.id,
            status = "PLANNED",
            createdAt = now,
        )

        // Atribución desde los defaults de la plantilla; fallback conservador.
        val ownerId = wallet?.ownerMemberId
            ?: members.firstOrNull { it.role == "PAYER_ADULT" }?.id
            ?: members.firstOrNull()?.id
        val beneficiaryBps = parseBeneficiaries(template.defaultBeneficiaryIds)
            .ifEmpty { equalSplit(members.map { it.id }) }
        val payerBps = parsePayerSplit(template.defaultPayerSplit)
            .ifEmpty { ownerId?.let { mapOf(it to 10_000) } ?: emptyMap() }
        if (beneficiaryBps.isEmpty() || payerBps.isEmpty()) return false

        val attributions =
            buildRows(expenseId, normalizeBps(beneficiaryBps), "BENEFICIARY", template.defaultAmountMxn) +
                buildRows(expenseId, normalizeBps(payerBps), "PAYER", template.defaultAmountMxn)

        expenseRepository.insertWithAttributions(expense, attributions)
        return true
    }

    // ── Proyección cadencia → fecha dentro de la quincena (§G.2.3) ──────────────

    /** Fecha de la ocurrencia en [quincena], o null si la cadencia no aplica. */
    private fun occurrenceDate(template: RecurrenceTemplateEntity, quincena: QuincenaEntity): LocalDate? {
        val detail = runCatching { JSONObject(template.cadenceDetail) }.getOrDefault(JSONObject())
        val day = detail.optInt("day_of_month", -1).takeIf { it in 1..31 }
        val year = quincena.year
        val month = quincena.month
        val lastDay = YearMonth.of(year, month).lengthOfMonth()
        val isFirst = quincena.half == "FIRST"

        fun firstHalfDate() = LocalDate.of(year, month, (day ?: 1).coerceIn(1, 15))
        fun secondHalfDate() = LocalDate.of(year, month, (day ?: 16).coerceIn(16, lastDay))

        return when (template.cadence) {
            "QUINCENAL_FIRST" -> if (isFirst) firstHalfDate() else null
            "QUINCENAL_SECOND" -> if (!isFirst) secondHalfDate() else null
            "QUINCENAL_EVERY" -> if (isFirst) firstHalfDate() else secondHalfDate()
            "MONTHLY_SPECIFIC_HALF" -> monthlyDate(day, isFirst, year, month, lastDay)
            "BIMONTHLY" -> {
                val anchor = detail.optInt("anchor_month", anchorMonth(template) ?: 1)
                if (month % 2 != anchor % 2) null
                else monthlyDate(day, isFirst, year, month, lastDay)
            }
            else -> null // CUSTOM_CRON diferido (§G.2.6)
        }
    }

    /** Para cadencias mensuales: cae en la mitad que corresponde al día del mes. */
    private fun monthlyDate(day: Int?, isFirst: Boolean, year: Int, month: Int, lastDay: Int): LocalDate? {
        val d = day ?: 1
        val targetIsFirst = d <= 15
        if (targetIsFirst != isFirst) return null
        return LocalDate.of(year, month, d.coerceIn(1, lastDay))
    }

    /** Paridad de mes ancla, inferida de `next_expected_date` si la plantilla la trae. */
    private fun anchorMonth(template: RecurrenceTemplateEntity): Int? =
        template.nextExpectedDate
            ?.let { runCatching { LocalDate.parse(it).monthValue }.getOrNull() }

    // ── Parseo de splits default (JSON) ─────────────────────────────────────────

    /** `["id1","id2"]` → reparto equitativo. */
    private fun parseBeneficiaries(json: String): Map<String, Int> {
        val ids = runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }.filter { it.isNotBlank() }
        }.getOrDefault(emptyList())
        return equalSplit(ids)
    }

    /** `{"id":bps}` → mapa memberId→bps tal cual (se normaliza luego). */
    private fun parsePayerSplit(json: String): Map<String, Int> = runCatching {
        val obj = JSONObject(json)
        obj.keys().asSequence().associateWith { obj.getInt(it) }.filterValues { it > 0 }
    }.getOrDefault(emptyMap())

    // ── Helpers de atribución (mismo invariante que BankCaptureManager) ─────────

    private fun equalSplit(ids: List<String>): Map<String, Int> {
        if (ids.isEmpty()) return emptyMap()
        val base = 10_000 / ids.size
        val remainder = 10_000 - base * ids.size
        return ids.mapIndexed { i, id -> id to if (i == ids.lastIndex) base + remainder else base }.toMap()
    }

    /** Reescala para sumar exactamente 10,000 bps (el último absorbe el resto). */
    private fun normalizeBps(raw: Map<String, Int>): Map<String, Int> {
        if (raw.isEmpty()) return emptyMap()
        val total = raw.values.sum().takeIf { it > 0 } ?: return equalSplit(raw.keys.toList())
        val entries = raw.entries.toList()
        var assigned = 0
        return entries.mapIndexed { i, (id, bps) ->
            val scaled = if (i == entries.lastIndex) 10_000 - assigned
            else ((bps.toLong() * 10_000) / total).toInt().also { assigned += it }
            id to scaled
        }.toMap()
    }

    private fun buildRows(
        expenseId: String,
        bps: Map<String, Int>,
        role: String,
        amount: Double,
    ): List<ExpenseAttributionEntity> = bps.map { (memberId, share) ->
        ExpenseAttributionEntity(
            id = UUID.randomUUID().toString(),
            expenseId = expenseId,
            memberId = memberId,
            role = role,
            shareBps = share,
            shareAmountMxn = amount * share / 10_000.0,
        )
    }
}
