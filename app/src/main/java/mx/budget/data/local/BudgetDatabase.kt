package mx.budget.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import mx.budget.data.local.converter.Converters
import mx.budget.data.local.dao.*
import mx.budget.data.local.entity.*

/**
 * Base de datos Room central de la aplicación de presupuesto familiar.
 *
 * Schema: 12 tablas normalizadas + tabla de intersección para atribución.
 * Persistencia: SQLite local con WAL mode habilitado (default Room).
 * Inicialización: Singleton thread-safe con double-checked locking.
 *
 * Las entidades implementan el modelo relacional completo descrito en
 * ESPECIFICACION_PRESUPUESTO_APP.md §2 y ESPECIFICACION_UX_HARDWARE_APP.md §2.
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
        IncomeSourceEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class BudgetDatabase : RoomDatabase() {

    // ── DAOs ────────────────────────────────────────────────────

    abstract fun householdDao(): HouseholdDao
    abstract fun memberDao(): MemberDao
    abstract fun categoryDao(): CategoryDao
    abstract fun paymentMethodDao(): PaymentMethodDao
    abstract fun quincenaDao(): QuincenaDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun expenseAttributionDao(): ExpenseAttributionDao
    abstract fun recurrenceTemplateDao(): RecurrenceTemplateDao
    abstract fun installmentPlanDao(): InstallmentPlanDao
    abstract fun loanDao(): LoanDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun incomeSourceDao(): IncomeSourceDao
    abstract fun analyticsDao(): AnalyticsDao

    companion object {
        private const val DATABASE_NAME = "budget.db"

        @Volatile
        private var INSTANCE: BudgetDatabase? = null

        /**
         * Obtiene la instancia singleton de la base de datos.
         *
         * Usa double-checked locking para thread safety sin
         * penalizar el hot path con sincronización innecesaria.
         *
         * @param context Application context (no Activity) para evitar leaks.
         */
        fun getInstance(context: Context): BudgetDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): BudgetDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                BudgetDatabase::class.java,
                DATABASE_NAME
            )
                // Habilita exportación de schema para versionamiento y migraciones.
                // Los schemas se guardan en app/schemas/ para referencia.
                .createFromAsset("budget_database.db")
                .build()
        }
    }
}
