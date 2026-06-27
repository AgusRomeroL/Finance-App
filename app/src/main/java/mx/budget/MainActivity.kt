package mx.budget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import mx.budget.data.repository.CategoryRepository
import mx.budget.data.repository.ExpenseRepository
import mx.budget.data.repository.MemberRepository
import mx.budget.data.repository.QuincenaRepository
import mx.budget.data.repository.WalletRepository
import mx.budget.ui.capture.CaptureViewModel
import mx.budget.ui.dashboard.DashboardViewModel
import mx.budget.ui.navigation.BudgetNavGraph
import mx.budget.ui.theme.BudgetAppTheme

class MainActivity : ComponentActivity() {

    private val dashboardViewModel: DashboardViewModel by lazy {
        val app = application as BudgetApplication
        ViewModelProvider(this, DashboardViewModelFactory(
            app.quincenaRepository,
            app.expenseRepository,
            app.memberRepository,
            app.householdId
        ))[DashboardViewModel::class.java]
    }

    private val captureViewModel: CaptureViewModel by lazy {
        val app = application as BudgetApplication
        ViewModelProvider(this, CaptureViewModelFactory(
            app.expenseRepository,
            app.quincenaRepository,
            app.walletRepository,
            app.memberRepository,
            app.categoryRepository,
            app.householdId
        ))[CaptureViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val windowWidthDp = resources.displayMetrics.let {
            (it.widthPixels / it.density).toInt()
        }
        setContent {
            // Material You por default (brief §2.1): los roles M3 se derivan del
            // wallpaper. El verde sembrado #016E3E queda como fallback (toggle off,
            // pendiente de exponer en Perfil + persistir en DataStore).
            BudgetAppTheme(dynamicColor = true) {
                BudgetNavGraph(
                    dashboardViewModel = dashboardViewModel,
                    captureViewModel = captureViewModel,
                    windowWidthDp = windowWidthDp
                )
            }
        }
    }
}

/** Factory para DashboardViewModel */
class DashboardViewModelFactory(
    private val quincenaRepository: QuincenaRepository,
    private val expenseRepository: ExpenseRepository,
    private val memberRepository: MemberRepository,
    private val householdId: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DashboardViewModel(
            quincenaRepository = quincenaRepository,
            expenseRepository = expenseRepository,
            memberRepository = memberRepository,
            householdId = householdId,
        ) as T
    }
}

/** Factory para CaptureViewModel */
class CaptureViewModelFactory(
    private val expenseRepository: ExpenseRepository,
    private val quincenaRepository: QuincenaRepository,
    private val walletRepository: WalletRepository,
    private val memberRepository: MemberRepository,
    private val categoryRepository: CategoryRepository,
    private val householdId: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CaptureViewModel(
            expenseRepository = expenseRepository,
            quincenaRepository = quincenaRepository,
            walletRepository = walletRepository,
            memberRepository = memberRepository,
            categoryRepository = categoryRepository,
            householdId = householdId,
        ) as T
    }
}