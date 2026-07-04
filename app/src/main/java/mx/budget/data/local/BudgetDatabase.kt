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
import mx.budget.data.local.dao.IncomeSourceDao
import mx.budget.data.local.dao.MemberDao
import mx.budget.data.local.dao.PaymentMethodDao
import mx.budget.data.local.dao.PendingCaptureDao
import mx.budget.data.local.dao.QuincenaDao
import mx.budget.data.local.dao.RecurrenceTemplateDao
import mx.budget.data.local.dao.SyncQueueDao
import mx.budget.data.local.dao.WalletTransferDao
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
import mx.budget.data.local.entity.WalletTransferEntity

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
        PendingCaptureEntity::class,
        WalletTransferEntity::class
    ],
    version = 11,
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

    abstract fun recurrenceTemplateDao(): RecurrenceTemplateDao

    abstract fun walletTransferDao(): WalletTransferDao

    abstract fun incomeSourceDao(): IncomeSourceDao

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

        /**
         * v6 → v7: **ubicación del gasto** (Apéndice G.4). Añade las 4 columnas de
         * ubicación a `expense`, espejo de las que `pending_capture` ya tiene desde
         * v6: `latitude`, `longitude`, `place_label`, `location_source`
         * (`CAPTURE | CONFIRM | MANUAL | NONE`).
         *
         * Columnas nuevas por `ALTER TABLE ADD COLUMN` (no aparecen como CREATE en
         * `app/schemas/7.json`; Room valida columnas por nombre/tipo, no por orden),
         * todas nullable, coincidentes con `ExpenseEntity`. Sin índice. Los 793
         * gastos sembrados quedan con ubicación nula (`location_source` interpretado
         * como `NONE`), según §G.4.2.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `expense` ADD COLUMN `latitude` REAL")
                db.execSQL("ALTER TABLE `expense` ADD COLUMN `longitude` REAL")
                db.execSQL("ALTER TABLE `expense` ADD COLUMN `place_label` TEXT")
                db.execSQL("ALTER TABLE `expense` ADD COLUMN `location_source` TEXT")
            }
        }

        /**
         * v7 → v8: **saldo inicial (ancla) del wallet**. Añade
         * `payment_method.opening_balance_mxn` — el punto de partida que el usuario
         * declara al alta/edición, base de `saldo = inicial + Σ ingresos − Σ gastos`.
         *
         * Columna nueva por `ALTER TABLE ADD COLUMN` con `NOT NULL DEFAULT 0` (los 13
         * wallets sembrados arrancan en 0). El `DEFAULT 0` debe coincidir con el
         * `@ColumnInfo(defaultValue = "0")` de `PaymentMethodEntity` para que el
         * identityHash de `app/schemas/8.json` valide. Sin índice.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `payment_method` ADD COLUMN `opening_balance_mxn` REAL NOT NULL DEFAULT 0")
            }
        }

        /**
         * v8 → v9: **captura rica de lenguaje natural** (Apéndice G.3). El LLM con
         * contexto del hogar puede asignar beneficiarios, pagadores y notas desde la
         * frase; se persisten en `pending_capture` para prellenar la hoja al confirmar.
         *
         * Tres columnas nuevas nullable por `ALTER TABLE ADD COLUMN` (Room valida
         * columnas por nombre/tipo, no por orden; mismo patrón que v2→v3, v6→v7),
         * coincidentes con `PendingCaptureEntity`: `suggested_beneficiary_json`,
         * `suggested_payer_json` (atribución `{memberId: bps}` serializada) y `notes`.
         * Sin índice. Las filas existentes quedan en null (capturas simples).
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `pending_capture` ADD COLUMN `suggested_beneficiary_json` TEXT")
                db.execSQL("ALTER TABLE `pending_capture` ADD COLUMN `suggested_payer_json` TEXT")
                db.execSQL("ALTER TABLE `pending_capture` ADD COLUMN `notes` TEXT")
            }
        }

        /**
         * v9 → v10: **transferencias entre wallets** (RF-41). Crea la tabla
         * `wallet_transfer` (origen, destino, monto, fecha, nota). El CREATE TABLE y
         * los índices se copiaron LITERAL del `createSql` que KSP genera en
         * `app/schemas/10.json`; cualquier divergencia rompe el identityHash. El asset
         * se queda en v1.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `wallet_transfer` (`id` TEXT NOT NULL, `household_id` TEXT NOT NULL, `from_payment_method_id` TEXT NOT NULL, `to_payment_method_id` TEXT NOT NULL, `amount_mxn` REAL NOT NULL, `occurred_at` INTEGER NOT NULL, `note` TEXT, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`household_id`) REFERENCES `household`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`from_payment_method_id`) REFERENCES `payment_method`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`to_payment_method_id`) REFERENCES `payment_method`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_wallet_transfer_household_id` ON `wallet_transfer` (`household_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_wallet_transfer_from_payment_method_id` ON `wallet_transfer` (`from_payment_method_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_wallet_transfer_to_payment_method_id` ON `wallet_transfer` (`to_payment_method_id`)")
            }
        }

        /**
         * v10 → v11: **`updated_at` para LWW del sync multi-dispositivo** (MVP
         * Fase 2). Añade la marca de última modificación local a las 4 tablas que
         * se **pushean** a Firestore: `expense`, `payment_method`,
         * `wallet_transfer`, `income_source`. El pull remoto solo aplica un doc si
         * su `updatedAt` es mayor que el local; `0` (default de migración y de
         * docs legados/seed sin el campo) nunca pisa una edición local.
         *
         * NO se añade a `expense_attribution` (viaja con su gasto: la gatea
         * `expense.updated_at`) ni a category/member/quincena (solo pull, sin
         * escritura local que proteger). Columnas por `ALTER TABLE ADD COLUMN`
         * con `NOT NULL DEFAULT 0`, coincidente con el
         * `@ColumnInfo(defaultValue = "0")` de las entidades para que el
         * identityHash de `app/schemas/11.json` valide (mismo patrón que v7→v8).
         */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `expense` ADD COLUMN `updated_at` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `payment_method` ADD COLUMN `updated_at` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `wallet_transfer` ADD COLUMN `updated_at` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `income_source` ADD COLUMN `updated_at` INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
