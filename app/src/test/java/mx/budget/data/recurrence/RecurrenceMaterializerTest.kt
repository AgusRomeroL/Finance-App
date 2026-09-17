package mx.budget.data.recurrence

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import mx.budget.data.local.dao.ExpenseDao
import mx.budget.data.local.dao.MemberDao
import mx.budget.data.local.dao.PaymentMethodDao
import mx.budget.data.local.dao.RecurrenceTemplateDao
import mx.budget.data.local.entity.ExpenseAttributionEntity
import mx.budget.data.local.entity.ExpenseEntity
import mx.budget.data.local.entity.MemberEntity
import mx.budget.data.local.entity.PaymentMethodEntity
import mx.budget.data.local.entity.QuincenaEntity
import mx.budget.data.local.entity.RecurrenceTemplateEntity
import mx.budget.data.repository.ExpenseRepository
import mx.budget.testing.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

/**
 * Materializacion de PLANNED desde plantillas: proyeccion de la cadencia a la
 * quincena, ids deterministas (la clave de la convergencia multi-dispositivo)
 * y atribuciones que suman 10,000 bps exactos.
 */
class RecurrenceMaterializerTest {

    private val members = listOf(
        Fixtures.member("m-1", "Norma"),
        Fixtures.member("m-2", "Benjamin"),
        Fixtures.member("m-3", "Santiago", role = "BENEFICIARY_DEPENDENT"),
        Fixtures.member("m-ext", "Omar", role = "EXTERNAL_CREDITOR"),
    )
    private val wallets = listOf(Fixtures.wallet("w-1", ownerMemberId = "m-1"), Fixtures.wallet("w-2", "Efectivo", "CASH", ownerMemberId = "m-2"))
    private val externalWallet = Fixtures.wallet("external-${Fixtures.HID}", "Pagado por terceros", "EXTERNAL", ownerMemberId = null)

    private val first = Fixtures.quincena(2026, 9, "FIRST")
    private val second = Fixtures.quincena(2026, 9, "SECOND")

    private class Harness(
        templates: List<RecurrenceTemplateEntity>,
        members: List<MemberEntity>,
        wallets: List<PaymentMethodEntity>,
        externalWallet: PaymentMethodEntity,
        alreadyCount: Int = 0,
        existing: ExpenseEntity? = null,
    ) {
        val inserted = mutableListOf<Pair<ExpenseEntity, List<ExpenseAttributionEntity>>>()
        val recurrenceDao = mockk<RecurrenceTemplateDao> { coEvery { getActive(any()) } returns templates }
        val expenseDao = mockk<ExpenseDao> {
            coEvery { countForTemplateInQuincena(any(), any()) } returns alreadyCount
            coEvery { getById(any()) } returns existing
        }
        val walletDao = mockk<PaymentMethodDao> { coEvery { getActive(any()) } returns wallets }
        val memberDao = mockk<MemberDao> { coEvery { getActiveMembers(any()) } returns members }
        val repo = mockk<ExpenseRepository> {
            val e = slot<ExpenseEntity>()
            val a = slot<List<ExpenseAttributionEntity>>()
            coEvery { insertWithAttributions(capture(e), capture(a)) } answers {
                inserted += e.captured to a.captured
            }
            coEvery { ensureExternalWallet(any()) } returns externalWallet
        }
        val materializer = RecurrenceMaterializer(
            recurrenceDao, repo, expenseDao, walletDao, memberDao, Fixtures.HID,
            nowProvider = { 1_700_000_000_000L },
        )
    }

    private fun harness(vararg templates: RecurrenceTemplateEntity, alreadyCount: Int = 0, existing: ExpenseEntity? = null) =
        Harness(templates.toList(), members, wallets, externalWallet, alreadyCount, existing)

    private fun det(s: String) = UUID.nameUUIDFromBytes(s.toByteArray()).toString()

    @Test
    fun `el id del gasto y de sus atribuciones es determinista`() = runBlocking {
        val t = Fixtures.template(id = "t-1", cadence = "QUINCENAL_FIRST", dayOfMonth = 5)
        val h = harness(t)
        assertEquals(1, h.materializer.materialize(first))
        val (expense, attrs) = h.inserted.single()

        assertEquals(det("recur:t-1:q-2026-09-FIRST:2026-09-05"), expense.id)
        assertEquals("PLANNED", expense.status)
        assertEquals("t-1", expense.recurrenceTemplateId)
        assertEquals(Fixtures.HID, expense.householdId)
        assertEquals(first.id, expense.quincenaId)
        assertEquals(1_700_000_000_000L, expense.createdAt)
        val nine = LocalDate.of(2026, 9, 5).atTime(9, 0).atZone(Fixtures.MX).toInstant().toEpochMilli()
        assertEquals(nine, expense.occurredAt)

        val payer = attrs.single { it.role == "PAYER" }
        assertEquals(det("recurattr:${expense.id}:PAYER:m-1"), payer.id)
        assertEquals(10_000, payer.shareBps)

        // Segunda materializacion en otro proceso: mismos ids.
        val again = harness(t)
        again.materializer.materialize(first)
        assertEquals(h.inserted.single().first.id, again.inserted.single().first.id)
        assertEquals(h.inserted.single().second.map { it.id }, again.inserted.single().second.map { it.id })
    }

    @Test
    fun `sin beneficiarios declarados reparte en partes iguales entre los miembros no externos`() = runBlocking {
        val t = Fixtures.template(cadence = "QUINCENAL_FIRST", dayOfMonth = 5, amount = 300.0)
        val h = harness(t)
        h.materializer.materialize(first)
        val ben = h.inserted.single().second.filter { it.role == "BENEFICIARY" }
        assertEquals(listOf("m-1", "m-2", "m-3"), ben.map { it.memberId })
        assertEquals(listOf(3333, 3333, 3334), ben.map { it.shareBps })
        assertEquals(10_000, ben.sumOf { it.shareBps })
        assertEquals(300.0, ben.sumOf { it.shareAmountMxn }, 1e-9)
        assertEquals(100.02, ben.last().shareAmountMxn, 1e-9)
    }

    @Test
    fun `los repartos de la plantilla se normalizan a 10000 bps`() = runBlocking {
        val t = Fixtures.template(
            cadence = "QUINCENAL_FIRST", dayOfMonth = 5,
            beneficiaries = """{"m-1": 1, "m-2": 1, "m-3": 1}""",
            payerSplit = """{"m-1": 70, "m-2": 30}""",
        )
        val h = harness(t)
        h.materializer.materialize(first)
        val attrs = h.inserted.single().second
        val ben = attrs.filter { it.role == "BENEFICIARY" }
        assertEquals(listOf(3333, 3333, 3334), ben.map { it.shareBps })
        val pay = attrs.filter { it.role == "PAYER" }
        assertEquals(mapOf("m-1" to 7000, "m-2" to 3000), pay.associate { it.memberId to it.shareBps })
    }

    @Test
    fun `el formato historico de beneficiarios es un arreglo de ids`() = runBlocking {
        val t = Fixtures.template(cadence = "QUINCENAL_FIRST", dayOfMonth = 5, beneficiaries = """["m-2", "m-3"]""")
        val h = harness(t)
        h.materializer.materialize(first)
        val ben = h.inserted.single().second.filter { it.role == "BENEFICIARY" }
        assertEquals(mapOf("m-2" to 5000, "m-3" to 5000), ben.associate { it.memberId to it.shareBps })
    }

    @Test
    fun `sin reparto de pagador paga el dueno del wallet`() = runBlocking {
        val t = Fixtures.template(cadence = "QUINCENAL_FIRST", dayOfMonth = 5, walletId = "w-2")
        val h = harness(t)
        h.materializer.materialize(first)
        val pay = h.inserted.single().second.single { it.role == "PAYER" }
        assertEquals("m-2", pay.memberId)
        assertEquals("w-2", h.inserted.single().first.paymentMethodId)
    }

    @Test
    fun `la idempotencia por conteo y por id evita duplicar`() = runBlocking {
        val t = Fixtures.template(cadence = "QUINCENAL_FIRST", dayOfMonth = 5)
        assertEquals(0, harness(t, alreadyCount = 1).materializer.materialize(first))
        val existing = Fixtures.expense("x", LocalDate.of(2026, 9, 5), 1.0)
        assertEquals(0, harness(t, existing = existing).materializer.materialize(first))
    }

    @Test
    fun `cada cadencia cae solo en la mitad que le toca`() = runBlocking {
        val qf = Fixtures.template("qf", "QUINCENAL_FIRST", dayOfMonth = 5)
        val qs = Fixtures.template("qs", "QUINCENAL_SECOND", dayOfMonth = 20)
        val qe = Fixtures.template("qe", "QUINCENAL_EVERY", dayOfMonth = 3)
        val m1 = Fixtures.template("m1", "MONTHLY_SPECIFIC_HALF", dayOfMonth = 10)
        val m2 = Fixtures.template("m2", "MONTHLY_SPECIFIC_HALF", dayOfMonth = 25)
        val cron = Fixtures.template("cron", "CUSTOM_CRON")

        val a = harness(qf, qs, qe, m1, m2, cron)
        a.materializer.materialize(first)
        assertEquals(
            mapOf("qf" to "2026-09-05", "qe" to "2026-09-03", "m1" to "2026-09-10"),
            a.inserted.associate { it.first.recurrenceTemplateId!! to dayOf(it.first) },
        )

        val b = harness(qf, qs, qe, m1, m2, cron)
        b.materializer.materialize(second)
        assertEquals(
            mapOf("qs" to "2026-09-20", "qe" to "2026-09-16", "m2" to "2026-09-25"),
            b.inserted.associate { it.first.recurrenceTemplateId!! to dayOf(it.first) },
        )
    }

    @Test
    fun `el dia se acota a la mitad y al ultimo dia del mes`() = runBlocking {
        val late = Fixtures.template("late", "QUINCENAL_FIRST", dayOfMonth = 28)
        val eom = Fixtures.template("eom", "QUINCENAL_SECOND", dayOfMonth = 31)
        val early = Fixtures.template("early", "QUINCENAL_SECOND", dayOfMonth = 2)
        val a = harness(late)
        a.materializer.materialize(first)
        assertEquals("2026-09-15", dayOf(a.inserted.single().first))
        val b = harness(eom, early)
        b.materializer.materialize(second)
        assertEquals(
            mapOf("eom" to "2026-09-30", "early" to "2026-09-16"),
            b.inserted.associate { it.first.recurrenceTemplateId!! to dayOf(it.first) },
        )
    }

    @Test
    fun `la bimestral respeta la paridad del mes ancla`() = runBlocking {
        val odd = Fixtures.template("odd", "BIMONTHLY", dayOfMonth = 12, anchorMonth = 1)
        val even = Fixtures.template("even", "BIMONTHLY", dayOfMonth = 12, anchorMonth = 2)
        val fromNext = Fixtures.template("next", "BIMONTHLY", dayOfMonth = 12, nextExpectedDate = "2026-11-12")
        val h = harness(odd, even, fromNext)
        h.materializer.materialize(first)
        assertEquals(setOf("odd", "next"), h.inserted.map { it.first.recurrenceTemplateId }.toSet())
    }

    @Test
    fun `un tercero que adelanta el pago va al wallet externo con reembolso pendiente`() = runBlocking {
        val t = Fixtures.template(
            cadence = "QUINCENAL_FIRST", dayOfMonth = 5,
            externalPayerId = "m-ext", settlement = "PENDING_REIMBURSEMENT",
            payerSplit = """{"m-1": 100}""",
        )
        val h = harness(t)
        h.materializer.materialize(first)
        val (expense, attrs) = h.inserted.single()
        assertEquals(externalWallet.id, expense.paymentMethodId)
        assertEquals("m-ext", expense.externalPayerMemberId)
        assertEquals("PENDING_REIMBURSEMENT", expense.settlementStatus)
        val pay = attrs.single { it.role == "PAYER" }
        assertEquals("m-ext", pay.memberId)
        assertEquals(10_000, pay.shareBps)
        coVerify(exactly = 1) { h.repo.ensureExternalWallet(Fixtures.HID) }
    }

    @Test
    fun `sin plantillas activas no consulta nada mas`() = runBlocking {
        val h = harness()
        assertEquals(0, h.materializer.materialize(first))
        coVerify(exactly = 0) { h.walletDao.getActive(any()) }
    }

    @Test
    fun `una plantilla que falla no rompe el lote`() = runBlocking {
        val ok = Fixtures.template("ok", "QUINCENAL_FIRST", dayOfMonth = 5)
        val noWallet = Fixtures.template("nw", "QUINCENAL_FIRST", dayOfMonth = 6, walletId = null)
        val h = Harness(listOf(noWallet, ok), members, emptyList(), externalWallet)
        assertEquals(1, h.materializer.materialize(first))
        assertEquals("ok", h.inserted.single().first.recurrenceTemplateId)
        assertNull(h.inserted.single().first.externalPayerMemberId)
        assertTrue(h.inserted.single().second.all { it.shareBps > 0 })
    }

    private fun dayOf(e: ExpenseEntity): String =
        java.time.Instant.ofEpochMilli(e.occurredAt).atZone(Fixtures.MX).toLocalDate().toString()
}
