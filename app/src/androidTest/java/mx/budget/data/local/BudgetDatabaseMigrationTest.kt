package mx.budget.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * La cadena de migraciones sobre el asset REAL que embarca la app.
 *
 * `createFromAsset` copia `budget_database.db` (schema v1, `user_version = 1`)
 * y Room corre 1→2→…→21 en el primer arranque. Aqui se hace exactamente eso
 * con `MigrationTestHelper`, que ademas valida cada version contra su
 * `schemas/N.json` (las columnas, tipos, defaults e indices que KSP genero), y
 * se comprueba que ninguna tabla sembrada pierde filas.
 *
 * Tambien se cubren las rutas que existen en aparatos reales: bases que se
 * quedaron en v16, v17 y v18. La v17 no tiene `17.json` en el repositorio
 * (solo anadio columnas nullable), asi que esa parada se construye aplicando
 * la migracion a mano y fijando la version sin validar el esquema intermedio;
 * el destino si se valida.
 *
 * El archivo de prueba es propio (`migration-test.db`): nunca se toca
 * `budget.db`, y el runner arranca una Application vacia para que la app no
 * abra Room en paralelo.
 */
@RunWith(AndroidJUnit4::class)
class BudgetDatabaseMigrationTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BudgetDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private val file: File get() = context.getDatabasePath(DB)

    @Before
    fun copySeedAsset() {
        context.deleteDatabase(DB)
        file.parentFile?.mkdirs()
        context.assets.open("budget_database.db").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        assertEquals("el asset debe declarar user_version = 1", 1, versionOnDisk())
    }

    @After
    fun cleanup() {
        context.deleteDatabase(DB)
    }

    @Test
    fun assetV1MigratesToCurrentWithoutLosingRows() {
        val before = countsOnDisk()
        assertTrue("la semilla trae gastos", before.getValue("expense") > 0)
        assertTrue("la semilla trae atribuciones", before.getValue("expense_attribution") > 0)

        helper.runMigrationsAndValidate(DB, BudgetDatabase.SCHEMA_VERSION, true, *ALL).use { db ->
            assertEquals(before, counts(db))
            // Las columnas que nacieron en la cadena existen con su default.
            assertEquals(0L, scalar(db, "SELECT COUNT(*) FROM payment_method WHERE balance_anchor_at IS NULL"))
            assertEquals(0L, scalar(db, "SELECT COUNT(*) FROM expense WHERE settlement_status <> 'NONE'"))
            assertEquals(0L, scalar(db, "SELECT COUNT(*) FROM sync_queue"))
        }
        assertEquals(BudgetDatabase.SCHEMA_VERSION, versionOnDisk())
    }

    @Test
    fun upgradeFromV16KeepsRows() = upgradeFrom(16)

    @Test
    fun upgradeFromV18KeepsRows() = upgradeFrom(18)

    @Test
    fun upgradeFromV17KeepsRows() {
        val before = countsOnDisk()
        helper.runMigrationsAndValidate(DB, 16, true, *upTo(16)).close()
        // Parada v17 sin schema exportado: la migracion a mano y la version a pelo.
        openRaw(16).use { h ->
            h.writableDatabase.let { db ->
                BudgetDatabase.MIGRATION_16_17.migrate(db)
                db.version = 17
            }
        }
        assertEquals(17, versionOnDisk())
        helper.runMigrationsAndValidate(DB, BudgetDatabase.SCHEMA_VERSION, true, *from(17)).use { db ->
            assertEquals(before, counts(db))
        }
    }

    @Test
    fun migrationsAreIdempotentOverAnAlreadyMigratedFile() {
        // Escenario de las ramas divergentes: la base ya trae las columnas que
        // 16→17, 17→18, 18→19 y 20→21 anaden. Rebobinar la version y volver a
        // correr la cola de la cadena no puede tronar con "duplicate column".
        helper.runMigrationsAndValidate(DB, BudgetDatabase.SCHEMA_VERSION, true, *ALL).close()
        openRaw(BudgetDatabase.SCHEMA_VERSION).use { it.writableDatabase.version = 16 }
        val before = countsOnDisk()
        helper.runMigrationsAndValidate(DB, BudgetDatabase.SCHEMA_VERSION, true, *from(16)).use { db ->
            assertEquals(before, counts(db))
        }
    }

    @Test
    fun v19ToV20RewritesLegacyPaidStatus() {
        helper.runMigrationsAndValidate(DB, 19, true, *upTo(19)).use { db ->
            db.execSQL(
                "INSERT INTO installment_plan (id, household_id, display_name, principal_mxn, total_installments, " +
                    "installment_amount_mxn, start_date, current_installment, status) " +
                    "VALUES ('plan-legacy', (SELECT id FROM household LIMIT 1), 'Plan legacy', 1200, 12, 100, '2026-01-15', 12, 'PAID')",
            )
        }
        helper.runMigrationsAndValidate(DB, BudgetDatabase.SCHEMA_VERSION, true, *from(19)).use { db ->
            assertEquals(0L, scalar(db, "SELECT COUNT(*) FROM installment_plan WHERE status = 'PAID'"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM installment_plan WHERE id = 'plan-legacy' AND status = 'PAID_OFF'"))
            assertEquals(0L, scalar(db, "SELECT COUNT(*) FROM installment_plan WHERE status NOT IN ('ACTIVE', 'PAID_OFF')"))
        }
    }

    @Test
    fun emptyV1SchemaMigratesToCurrent() {
        // Un hogar sin semilla (grupo nuevo): el esquema v1 exportado, vacio.
        context.deleteDatabase(DB)
        helper.createDatabase(DB, 1).close()
        helper.runMigrationsAndValidate(DB, BudgetDatabase.SCHEMA_VERSION, true, *ALL).use { db ->
            assertTrue(counts(db).values.all { it == 0L })
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun upgradeFrom(version: Int) {
        val before = countsOnDisk()
        helper.runMigrationsAndValidate(DB, version, true, *upTo(version)).close()
        assertEquals(version, versionOnDisk())
        helper.runMigrationsAndValidate(DB, BudgetDatabase.SCHEMA_VERSION, true, *from(version)).use { db ->
            assertEquals(before, counts(db))
        }
    }

    /** Abre el archivo sin que Room ni ningun callback lo toquen. */
    private fun openRaw(version: Int): SupportSQLiteOpenHelper {
        val cfg = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(DB)
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(cfg)
    }

    private fun versionOnDisk(): Int =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }

    private fun countsOnDisk(): Map<String, Long> =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            SEED_TABLES.associateWith { t ->
                db.rawQuery("SELECT COUNT(*) FROM `$t`", null).use { c -> c.moveToFirst(); c.getLong(0) }
            }
        }

    private fun counts(db: SupportSQLiteDatabase): Map<String, Long> =
        SEED_TABLES.associateWith { t -> scalar(db, "SELECT COUNT(*) FROM `$t`") }

    private fun scalar(db: SupportSQLiteDatabase, sql: String): Long =
        db.query(sql).use { c -> c.moveToFirst(); c.getLong(0) }

    companion object {
        private const val DB = "migration-test.db"

        /** Las doce tablas que trae la semilla v1. */
        private val SEED_TABLES = listOf(
            "household", "member", "category", "payment_method", "quincena", "expense",
            "expense_attribution", "recurrence_template", "installment_plan", "loan",
            "savings_goal", "income_source",
        )

        /** La cadena completa, en el mismo orden que la registra BudgetApplication. */
        private val ALL: Array<Migration> = arrayOf(
            BudgetDatabase.MIGRATION_1_2, BudgetDatabase.MIGRATION_2_3, BudgetDatabase.MIGRATION_3_4,
            BudgetDatabase.MIGRATION_4_5, BudgetDatabase.MIGRATION_5_6, BudgetDatabase.MIGRATION_6_7,
            BudgetDatabase.MIGRATION_7_8, BudgetDatabase.MIGRATION_8_9, BudgetDatabase.MIGRATION_9_10,
            BudgetDatabase.MIGRATION_10_11, BudgetDatabase.MIGRATION_11_12, BudgetDatabase.MIGRATION_12_13,
            BudgetDatabase.MIGRATION_13_14, BudgetDatabase.MIGRATION_14_15, BudgetDatabase.MIGRATION_15_16,
            BudgetDatabase.MIGRATION_16_17, BudgetDatabase.MIGRATION_17_18, BudgetDatabase.MIGRATION_18_19,
            BudgetDatabase.MIGRATION_19_20, BudgetDatabase.MIGRATION_20_21,
        )

        private fun upTo(version: Int) = ALL.filter { it.endVersion <= version }.toTypedArray()
        private fun from(version: Int) = ALL.filter { it.startVersion >= version }.toTypedArray()
    }
}
