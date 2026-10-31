package mx.budget.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import mx.budget.data.local.BudgetDatabase
import mx.budget.data.local.entity.CategoryEntity
import mx.budget.data.local.entity.ExpenseAttributionEntity
import mx.budget.data.local.entity.ExpenseEntity
import mx.budget.data.local.entity.HouseholdEntity
import mx.budget.data.local.entity.MemberEntity
import mx.budget.data.local.entity.PaymentMethodEntity
import mx.budget.data.local.entity.QuincenaEntity
import mx.budget.data.repository.impl.ExpenseRepositoryImpl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Deshacer el borrado de un gasto (Fase 7b, alcance 3) sobre una base Room real
 * en memoria: saldo, atribuciones y cola de sync tienen que quedar como antes,
 * tanto si el DELETE seguía en la cola como si ya había salido a la nube.
 */
@RunWith(AndroidJUnit4::class)
class ExpenseUndoTest {

    private lateinit var db: BudgetDatabase
    private lateinit var repo: ExpenseRepositoryImpl

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, BudgetDatabase::class.java).build()
        repo = ExpenseRepositoryImpl(db.expenseDao(), db.expenseAttributionDao(), db.syncQueueDao(), db)
        db.householdDao().insert(HouseholdEntity(id = HID, name = "Hogar de prueba", createdAt = 0L))
        db.memberDao().insert(
            MemberEntity(id = "m-1", householdId = HID, displayName = "Adulto", shortAliases = "[]", role = "PAYER_ADULT")
        )
        db.memberDao().insert(
            MemberEntity(id = "m-2", householdId = HID, displayName = "Menor", shortAliases = "[]", role = "DEPENDENT")
        )
        db.categoryDao().insert(
            CategoryEntity(id = "cat-1", householdId = HID, code = "SUPER", displayName = "Súper", kind = "EXPENSE")
        )
        db.paymentMethodDao().insert(
            PaymentMethodEntity(
                id = "w-1", householdId = HID, displayName = "Débito", kind = "DEBIT_ACCOUNT",
                currentBalanceMxn = 10_000.0,
            )
        )
        db.quincenaDao().insert(
            QuincenaEntity(
                id = "q-1", householdId = HID, year = 2026, month = 10, half = "SECOND",
                startDate = "2026-10-16", endDate = "2026-10-31", label = "Q2 Octubre 2026", status = "ACTIVE",
            )
        )
        repo.insertWithAttributions(
            ExpenseEntity(
                id = EXPENSE, householdId = HID, occurredAt = 1_793_000_000_000L, quincenaId = "q-1",
                categoryId = "cat-1", concept = "Súper", amountMxn = 500.0, paymentMethodId = "w-1",
                status = "POSTED", createdAt = 0L,
            ),
            listOf(
                ExpenseAttributionEntity("a-1", EXPENSE, "m-1", "BENEFICIARY", 6_000, 300.0),
                ExpenseAttributionEntity("a-2", EXPENSE, "m-2", "BENEFICIARY", 4_000, 200.0),
                ExpenseAttributionEntity("a-3", EXPENSE, "m-1", "PAYER", 10_000, 500.0),
            ),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun balance(): Double = db.paymentMethodDao().getById("w-1")!!.currentBalanceMxn

    private suspend fun expenseRows(operation: String) =
        db.syncQueueDao().getPending(Int.MAX_VALUE).filter { it.entityType == "EXPENSE" && it.operation == operation }

    @Test
    fun deshacer_con_el_delete_en_la_cola_lo_cancela() = runBlocking {
        assertEquals(9_500.0, balance(), 0.0)
        val snapshot = repo.deleteWithSnapshot(EXPENSE)!!
        assertNull(db.expenseDao().getById(EXPENSE))
        assertEquals(10_000.0, balance(), 0.0)
        assertEquals(1, expenseRows("DELETE").size)

        repo.restoreDeleted(snapshot)

        val restored = db.expenseDao().getById(EXPENSE)
        assertNotNull(restored)
        assertTrue(restored!!.updatedAt > snapshot.deletedAt)
        assertEquals(3, db.expenseAttributionDao().getByExpenseId(EXPENSE).size)
        assertEquals(9_500.0, balance(), 0.0)
        // La nube nunca ve el borrado: el DELETE sale de la cola y queda el UPSERT.
        assertEquals(0, expenseRows("DELETE").size)
        assertTrue(expenseRows("UPSERT").isNotEmpty())
    }

    @Test
    fun deshacer_con_el_delete_ya_empujado_revive_el_gasto() = runBlocking {
        val snapshot = repo.deleteWithSnapshot(EXPENSE)!!
        // El drenado ya empujó todo, la lápida incluida.
        db.syncQueueDao().getPending(Int.MAX_VALUE).forEach { db.syncQueueDao().delete(it.id) }

        repo.restoreDeleted(snapshot)

        val restored = db.expenseDao().getById(EXPENSE)!!
        // Marca posterior a la lápida: el eco del pull no puede borrarlo y el UPSERT
        // lo revive en la nube.
        assertTrue(restored.updatedAt > snapshot.deletedAt)
        assertEquals(1, expenseRows("UPSERT").size)
        assertEquals(9_500.0, balance(), 0.0)
    }

    @Test
    fun la_lapida_y_la_fila_del_delete_comparten_la_hora_del_borrado() = runBlocking {
        val snapshot = repo.deleteWithSnapshot(EXPENSE)!!
        assertEquals(snapshot.deletedAt, expenseRows("DELETE").single().createdAt)
    }

    @Test
    fun restaurar_dos_veces_no_aplica_el_saldo_dos_veces() = runBlocking {
        val snapshot = repo.deleteWithSnapshot(EXPENSE)!!
        repo.restoreDeleted(snapshot)
        repo.restoreDeleted(snapshot)
        assertEquals(9_500.0, balance(), 0.0)
        assertEquals(3, db.expenseAttributionDao().getByExpenseId(EXPENSE).size)
    }

    @Test
    fun un_planeado_restaurado_no_mueve_el_saldo() = runBlocking {
        repo.insertWithAttributions(
            ExpenseEntity(
                id = "e-planned", householdId = HID, occurredAt = 1_793_000_000_000L, quincenaId = "q-1",
                categoryId = "cat-1", concept = "Renta", amountMxn = 4_000.0, paymentMethodId = "w-1",
                status = "PLANNED", createdAt = 0L,
            ),
            listOf(ExpenseAttributionEntity("p-1", "e-planned", "m-1", "PAYER", 10_000, 4_000.0)),
        )
        val snapshot = repo.deleteWithSnapshot("e-planned")!!
        repo.restoreDeleted(snapshot)
        assertEquals(9_500.0, balance(), 0.0)
        assertNotNull(db.expenseDao().getById("e-planned"))
    }

    private companion object {
        const val HID = "hogar-prueba"
        const val EXPENSE = "e-1"
    }
}
