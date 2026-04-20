package mx.budget

import android.app.Application
import androidx.room.Room
import mx.budget.data.local.BudgetDatabase
import mx.budget.data.local.DatabaseSeedCallback
import mx.budget.data.repository.ExpenseRepository
import mx.budget.data.repository.MemberRepository
import mx.budget.data.repository.QuincenaRepository
import mx.budget.data.repository.WalletRepository
import mx.budget.data.repository.impl.ExpenseRepositoryImpl
import mx.budget.data.repository.impl.MemberRepositoryImpl
import mx.budget.data.repository.impl.QuincenaRepositoryImpl
import mx.budget.data.repository.impl.WalletRepositoryImpl

/**
 * Aplicación base — contenedor manual de dependencias (sin Hilt).
 * Expone los repositorios concretos para que MainActivity pueda
 * construir sus ViewModelFactories.
 */
class BudgetApplication : Application() {

    lateinit var database: BudgetDatabase
        private set

    lateinit var quincenaRepository: QuincenaRepository
        private set

    lateinit var expenseRepository: ExpenseRepository
        private set

    lateinit var memberRepository: MemberRepository
        private set

    lateinit var walletRepository: WalletRepository
        private set

    override fun onCreate() {
        super.onCreate()

        database = Room.databaseBuilder(
            this,
            BudgetDatabase::class.java,
            "budget.db"
        )
        .createFromAsset("budget_database.db")
        .fallbackToDestructiveMigration()
        .build()

        quincenaRepository = QuincenaRepositoryImpl(database.quincenaDao())
        expenseRepository  = ExpenseRepositoryImpl(database.expenseDao())
        memberRepository   = MemberRepositoryImpl(database.memberDao())
        walletRepository   = WalletRepositoryImpl(database.paymentMethodDao())
    }
}
