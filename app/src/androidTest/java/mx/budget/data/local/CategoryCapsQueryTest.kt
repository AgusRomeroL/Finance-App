package mx.budget.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import mx.budget.data.local.entity.CategoryEntity
import mx.budget.data.local.entity.HouseholdEntity
import mx.budget.data.local.entity.QuincenaEntity
import mx.budget.data.local.entity.RecurrenceTemplateEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Topes por gastar (Fase 7b): una categoría con plantilla recurrente activa no
 * aparta su tope, porque su cargo ya lo aparta "Reservado". Decisión del
 * 2026-10-31. Se prueba la consulta real sobre Room en memoria.
 */
@RunWith(AndroidJUnit4::class)
class CategoryCapsQueryTest {

    private lateinit var db: BudgetDatabase

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, BudgetDatabase::class.java).build()
        db.householdDao().insert(HouseholdEntity(id = HID, name = "Hogar de prueba", createdAt = 0L))
        db.quincenaDao().insert(
            QuincenaEntity(
                id = QID, householdId = HID, year = 2026, month = 10, half = "SECOND",
                startDate = "2026-10-16", endDate = "2026-10-31", label = "Q2 Octubre 2026", status = "ACTIVE",
            )
        )
        db.categoryDao().insert(cat("cat-comida", "Comida", 3_000.0))
        db.categoryDao().insert(cat("cat-netflix", "Netflix", 329.0))
        db.categoryDao().insert(cat("cat-gym", "Gimnasio", 500.0))
        db.categoryDao().insert(cat("cat-sin-tope", "Varios", null))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun cat(id: String, name: String, budget: Double?) = CategoryEntity(
        id = id, householdId = HID, code = id.uppercase(), displayName = name, kind = "EXPENSE",
        budgetDefaultMxn = budget,
    )

    private fun template(id: String, categoryId: String, active: Boolean) = RecurrenceTemplateEntity(
        id = id, householdId = HID, concept = id, categoryId = categoryId, defaultAmountMxn = 100.0,
        cadence = "MONTHLY_SPECIFIC_HALF", isActive = active,
    )

    private suspend fun capIds(): Set<String> =
        db.analyticsDao().getCategoryCaps(HID, QID).map { it.categoryId }.toSet()

    @Test
    fun sin_plantillas_entran_todas_las_categorias_con_tope() = runBlocking {
        assertEquals(setOf("cat-comida", "cat-netflix", "cat-gym"), capIds())
    }

    @Test
    fun una_plantilla_activa_saca_a_su_categoria() = runBlocking {
        db.recurrenceTemplateDao().insert(template("t-netflix", "cat-netflix", active = true))
        assertEquals(setOf("cat-comida", "cat-gym"), capIds())
    }

    @Test
    fun una_plantilla_inactiva_no_saca_a_nadie() = runBlocking {
        db.recurrenceTemplateDao().insert(template("t-gym", "cat-gym", active = false))
        assertEquals(setOf("cat-comida", "cat-netflix", "cat-gym"), capIds())
    }

    private companion object {
        const val HID = "hogar-prueba"
        const val QID = "q-1"
    }
}
