package mx.budget.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.google.android.horologist.compose.layout.AppScaffold
import mx.budget.wear.presentation.WearHub

/**
 * Punto de entrada del hub del reloj. [AppScaffold] inyecta el `TimeText` global;
 * el contenido es el [WearHub] (Estado / Captura / Movimientos / Pendientes) sobre
 * un `SwipeDismissableNavHost`. Los Tiles (Recomendados / Captura) son superficies
 * aparte declaradas en el manifest, no se lanzan desde aquí.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppScaffold {
                WearHub()
            }
        }
    }
}
