package mx.budget.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import mx.budget.data.local.converter.Converters
import mx.budget.data.local.dao.AttributionReviewDao
import mx.budget.data.local.dao.CategoryDao
import mx.budget.data.local.dao.ExpenseAttributionDao
import mx.budget.data.local.dao.ExpenseDao
import mx.budget.data.local.dao.HouseholdDao
import mx.budget.data.local.dao.MemberDao
import mx.budget.data.local.dao.PaymentMethodDao
import mx.budget.data.local.dao.QuincenaDao
import mx.budget.data.local.dao.SyncQueueDao
import mx.budget.data.local.entity.AttributionReviewEntity
import mx.budget.data.local.entity.CategoryEntity
import mx.budget.data.local.entity.ExpenseAttributionEntity
import mx.budget.data.local.entity.ExpenseEntity
import mx.budget.data.local.entity.HouseholdEntity
import mx.budget.data.local.entity.IncomeSourceEntity
import mx.budget.data.local.entity.InstallmentPlanEntity
import mx.budget.data.local.entity.LoanEntity
import mx.budget.data.local.entity.MemberEntity
import mx.budget.data.local.entity.PaymentMethodEntity
import mx.budget.data.local.entity.QuincenaEntity
import mx.budget.data.local.entity.RecurrenceTemplateEntity
import mx.budget.data.local.entity.SavingsGoalEntity
import mx.budget.data.local.entity.SyncQueueEntity

/**
 * Base de datos Room del módulo de presupuesto.
 *
 * El esquema (12 entidades, version 1) está congelado en
 * `app/schemas/mx.budget.data.local.BudgetDatabase/1.json`.
 * La base se precarga vía `createFromAsset("budget_database.db")`
 * en [mx.budget.BudgetApplication]; el identityHash del esquema debe
 * coincidir con el del asset, por eso el orden y la versión NO deben
 * alterarse sin regenerar el asset.
 */
@Database(
    entities = [
        HouseholdEntity::class,
        MemberEntity::class,
        CategoryEntity::class,
        PaymentMethodEntity::class,
        QuincenaEntity::class,
        ExpenseEntity::class,
        ExpenseAttributionEntity::class,
        RecurrenceTemplateEntity::class,
        InstallmentPlanEntity::class,
        LoanEntity::class,
        SavingsGoalEntity::class,
        IncomeSourceEntity::class,
        SyncQueueEntity::class,
        AttributionReviewEntity::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class BudgetDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao

    abstract fun categoryDao(): CategoryDao

    abstract fun memberDao(): MemberDao

    abstract fun quincenaDao(): QuincenaDao

    abstract fun paymentMethodDao(): PaymentMethodDao

    abstract fun syncQueueDao(): SyncQueueDao

    abstract fun expenseAttributionDao(): ExpenseAttributionDao

    abstract fun householdDao(): HouseholdDao

    abstract fun attributionReviewDao(): AttributionReviewDao

    companion object {
        /**
         * v1 → v2: añade la tabla `sync_queue` (outbox de sincronización).
         *
         * La tabla `expense_attribution` ya existe desde v1, por eso esta
         * migración solo crea `sync_queue`. El CREATE TABLE se tomó literal
         * del `createSql` generado por Room en
         * `app/schemas/mx.budget.data.local.BudgetDatabase/2.json` para que
         * el identityHash valide.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sync_queue` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `entity_type` TEXT NOT NULL, `entity_id` TEXT NOT NULL, `operation` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `attempts` INTEGER NOT NULL)"
                )
            }
        }

        /**
         * v2 → v3: capa de normalización/atribución retroactiva (Apéndice F.3).
         *
         * - Añade `expense.concept_canonical` (clave canónica del concepto) + índice.
         * - Crea la tabla LOCAL-ONLY `attribution_review` (cola de revisión) + índices.
         *
         * El SQL de la tabla y sus índices se copió LITERAL del `createSql` que KSP
         * genera en `app/schemas/3.json`; cualquier divergencia rompe el identityHash.
         * El `ALTER TABLE ... ADD COLUMN` no aparece en el json (Room valida columnas
         * por nombre/tipo, no por orden), por eso se escribe a mano con tipo `TEXT`
         * nullable, coincidente con `ExpenseEntity.conceptCanonical`.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `expense` ADD COLUMN `concept_canonical` TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_concept_canonical` ON `expense` (`concept_canonical`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `attribution_review` (`id` TEXT NOT NULL, `expense_id` TEXT NOT NULL, `role` TEXT NOT NULL, `suggested_json` TEXT NOT NULL, `confidence` REAL NOT NULL, `sample_size` INTEGER NOT NULL, `concept_canonical` TEXT, `status` TEXT NOT NULL, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`expense_id`) REFERENCES `expense`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attribution_review_status` ON `attribution_review` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attribution_review_expense_id` ON `attribution_review` (`expense_id`)")
            }
        }
    }
}
