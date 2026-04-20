package mx.budget

import android.app.Application
import androidx.room.Room
import mx.budget.data.local.BudgetDatabase
import mx.budget.data.repository.ExpenseRepository
import mx.budget.data.repository.MemberRepository
import mx.budget.data.repository.QuincenaRepository
import mx.budget.data.repository.impl.ExpenseRepositoryImpl
import mx.budget.data.repository.impl.MemberRepositoryImpl
import mx.budget.data.repository.impl.QuincenaRepositoryImpl

/**
 * Aplicación base — contenedor manual de dependencias (sin Hilt).
 * Expone los repositorios concretos para que MainActivity pueda
 * construir su DashboardViewModelFactory.
 */
class BudgetApplication : Application() {

    // Instancia única de la base de datos Room
    lateinit var database: BudgetDatabase
        private set

    // Repositorios concretos listos para inyección manual
    lateinit var quincenaRepository: QuincenaRepository
        private set

    lateinit var expenseRepository: ExpenseRepository
        private set

    lateinit var memberRepository: MemberRepository
        private set

    override fun onCreate() {
        super.onCreate()

        database = Room.databaseBuilder(
            this,
            BudgetDatabase::class.java,
            "budget.db"
        )
        .createFromAsset("budget_database.db")
        .build()

        // Instanciar repositorios concretos (ROM injection)
        quincenaRepository = QuincenaRepositoryImpl(database.quincenaDao())
        expenseRepository  = ExpenseRepositoryImpl(database.expenseDao())
        memberRepository   = MemberRepositoryImpl(database.memberDao())
    }
}
