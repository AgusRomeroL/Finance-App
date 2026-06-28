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
import mx.budget.data.local.dao.PendingCaptureDao
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
import mx.budget.data.local.entity.PendingCaptureEntity
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
        AttributionReviewEntity::class,
        PendingCaptureEntity::class
    ],
    version = 6,
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

    abstract fun pendingCaptureDao(): PendingCaptureDao

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

        /**
         * v3 → v4: captura desde notificaciones bancarias (Apéndice F.6, Feature D).
         *
         * Crea la tabla LOCAL-ONLY `pending_bank_capture` (cola de propuestas) + índice.
         * El SQL se copió LITERAL del `createSql` que KSP genera en `app/schemas/4.json`;
         * cualquier divergencia rompe el identityHash. Sin FK: la captura existe antes
         * que el gasto.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `pending_bank_capture` (`id` TEXT NOT NULL, `bank_id` TEXT NOT NULL, `bank_name` TEXT NOT NULL, `bank_package` TEXT NOT NULL, `amount_mxn` REAL NOT NULL, `merchant` TEXT NOT NULL, `last4` TEXT, `occurred_at` INTEGER NOT NULL, `suggested_wallet_id` TEXT, `suggested_category_id` TEXT, `status` TEXT NOT NULL, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_bank_capture_status` ON `pending_bank_capture` (`status`)")
            }
        }

        /**
         * v4 → v5: emoji monocromo sugerido por grupo de categoría (filtros del
         * dashboard). Añade `category.suggested_emoji` (TEXT nullable). Columna nueva
         * por `ALTER TABLE ADD COLUMN` (no aparece como CREATE en `app/schemas/5.json`;
         * Room valida columnas por nombre/tipo, no por orden), coincidente con
         * `CategoryEntity.suggestedEmoji`. Sin índice.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `category` ADD COLUMN `suggested_emoji` TEXT")
            }
        }

        /**
         * v5 → v6: **bandeja unificada de capturas** (Apéndice G.1). Generaliza
         * `pending_bank_capture` (Feature D) en `pending_capture`, añadiendo el
         * discriminador `source`, metadata de fuente nullable (voz/calendario) y
         * las columnas de ubicación (§G.4.2).
         *
         * Patrón recreate-table (no simples ALTER) porque hay que (a) volver
         * nullable los campos de banco y (b) renombrar `merchant`→`concept`, cosas
         * que SQLite no hace in-place. Se crea la tabla nueva con el `createSql`
         * LITERAL de `app/schemas/6.json`, se copian las filas existentes (todas
         * `source='BANK'`, `merchant`→`concept`) y se elimina la vieja. Cualquier
         * divergencia del CREATE TABLE rompe el identityHash.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // CREATE TABLE — reemplazar por el createSql EXACTO de schemas/6.json.
                db.execSQL("CREATE TABLE IF NOT EXISTS `pending_capture` (`id` TEXT NOT NULL, `source` TEXT NOT NULL, `amount_mxn` REAL NOT NULL, `concept` TEXT NOT NULL, `occurred_at` INTEGER NOT NULL, `suggested_wallet_id` TEXT, `suggested_category_id` TEXT, `status` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `bank_id` TEXT, `bank_name` TEXT, `bank_package` TEXT, `last4` TEXT, `raw_text` TEXT, `recurrence_id` TEXT, `latitude` REAL, `longitude` REAL, `place_label` TEXT, `location_source` TEXT, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_capture_status` ON `pending_capture` (`status`)")
                db.execSQL(
                    "INSERT INTO `pending_capture` " +
                        "(`id`, `source`, `amount_mxn`, `concept`, `occurred_at`, `suggested_wallet_id`, `suggested_category_id`, `status`, `created_at`, `bank_id`, `bank_name`, `bank_package`, `last4`) " +
                        "SELECT `id`, 'BANK', `amount_mxn`, `merchant`, `occurred_at`, `suggested_wallet_id`, `suggested_category_id`, `status`, `created_at`, `bank_id`, `bank_name`, `bank_package`, `last4` FROM `pending_bank_capture`"
                )
                db.execSQL("DROP TABLE `pending_bank_capture`")
            }
        }
    }
}
