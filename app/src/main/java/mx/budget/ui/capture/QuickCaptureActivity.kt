package mx.budget.ui.capture

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import mx.budget.BudgetApplication
import mx.budget.CaptureViewModelFactory
import mx.budget.ui.theme.BudgetAppTheme

/**
 * Ventana transparente optimizada para ser llamada desde un App Shortcut, Quick Tap
 * o el widget de captura (§G.3). Hospeda en exclusiva el BottomSheet de captura;
 * finaliza su ciclo de vida al ocultarse garantizando cero contaminación en el back stack.
 */
class QuickCaptureActivity : ComponentActivity() {

    private val captureViewModel: CaptureViewModel by lazy {
        val app = application as BudgetApplication
        ViewModelProvider(
            this,
            CaptureViewModelFactory(
                app.expenseRepository,
                app.quincenaRepository,
                app.walletRepository,
                app.memberRepository,
                app.categoryRepository,
                app.retroAttributionEngine,
                app.locationProvider,
                app.householdId,
                // A3: paridad con MainActivity (ingresos, recientes reales,
                // autocompletado de categoría y quincena por fecha).
                incomeRepository = app.incomeRepository,
                expenseDao = app.database.expenseDao(),
                categoryDao = app.database.categoryDao(),
                quincenaDao = app.database.quincenaDao(),
                pendingCaptureDao = app.database.pendingCaptureDao(),
                // Pagador default de sesión (roles v2), paridad con MainActivity.
                sessionMemberId = app.linkedMemberId,
            ),
        )[CaptureViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Define Theme.Transparent in Manifest to map purely the overlay
        setContent {
            BudgetAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.ui.graphics.Color.Transparent
                ) {
                    CaptureBottomSheet(
                        viewModel = captureViewModel,
                        onDismiss = {
                            // Al cerrar o confirmar el gasto, destruye la actividad
                            finish()
                        }
                    )
                }
            }
        }
    }
}
