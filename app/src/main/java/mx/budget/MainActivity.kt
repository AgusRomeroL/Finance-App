package mx.budget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import mx.budget.ui.navigation.BudgetNavGraph
import mx.budget.ui.theme.FinanceAppTheme // Verifica el nombre exacto de tu tema en Theme.kt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FinanceAppTheme {
                BudgetNavGraph()
            }
        }
    }
}