package mx.budget.ui.capture

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import mx.budget.ui.theme.AppTheme

/**
 * Ventana transparente optimizada para ser llamada desde un App Shortcut o Quick Tap.
 * Hospeda en exclusiva el BottomSheet de captura; finaliza su ciclo de vida al ocultarse
 * garantizando cero contaminación en el back stack.
 */
class QuickCaptureActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Define Theme.Transparent in Manifest to map purely the overlay
        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.ui.graphics.Color.Transparent
                ) {
                    CaptureBottomSheet(
                        viewModel = null, // Debería instanciarse y proveerse usando factories aquí
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
