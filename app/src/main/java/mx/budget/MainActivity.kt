package mx.budget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import mx.budget.data.repository.ExpenseRepository
import mx.budget.data.repository.MemberRepository
import mx.budget.data.repository.QuincenaRepository
import mx.budget.ui.dashboard.DashboardViewModel
import mx.budget.ui.navigation.BudgetNavGraph
import mx.budget.ui.theme.BudgetAppTheme

class MainActivity : ComponentActivity() {

    private val dashboardViewModel: DashboardViewModel by lazy {
        val app = application as BudgetApplication
        ViewModelProvider(this, DashboardViewModelFactory(
            app.quincenaRepository,
            app.expenseRepository,
            app.memberRepository
        ))[DashboardViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val windowWidthDp = resources.displayMetrics.let {
            (it.widthPixels / it.density).toInt()
        }
        setContent {
            BudgetAppTheme {
                BudgetNavGraph(
                    dashboardViewModel = dashboardViewModel,
                    windowWidthDp = windowWidthDp
                )
            }
        }
    }
}

/** Factory para inyectar repositorios en DashboardViewModel sin Hilt. */
class DashboardViewModelFactory(
    private val quincenaRepository: QuincenaRepository,
    private val expenseRepository: ExpenseRepository,
    private val memberRepository: MemberRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DashboardViewModel(
            quincenaRepository = quincenaRepository,
            expenseRepository = expenseRepository,
            memberRepository = memberRepository,
        ) as T
    }
}