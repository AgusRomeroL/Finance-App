package mx.budget.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.google.android.horologist.compose.layout.AppScaffold
import com.google.android.horologist.compose.layout.ScreenScaffold
import com.google.android.horologist.compose.layout.ScalingLazyColumnState
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import mx.budget.wear.presentation.QuickExpenseApp

/**
 * Punto de entrada principal para el módulo de Reloj Inteligente.
 * Utiliza Horologist Scaffold para inyectar un soporte nativo de biseles
 * y la hora en el borde del círculo.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            // El estado de la lista, creado de manera responsiva, para conectarlo
            // con nuestra app y soportar el sensor de corona del bisel.
            val columnState = rememberResponsiveColumnState()

            // AppScaffold provee un andamiaje base para Wear OS, inyectando TimeText globalmente.
            AppScaffold {
                // ScreenScaffold conecta nuestra columna de lista interna al andamiaje
                // permitiendo que el TimeText se desplace/oculte si hay scroll
                ScreenScaffold(scrollState = columnState) {
                    QuickExpenseApp(columnState = columnState)
                }
            }
        }
    }
}
