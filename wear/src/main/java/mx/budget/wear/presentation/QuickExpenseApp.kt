package mx.budget.wear.presentation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Confirmation
import androidx.wear.compose.material.dialog.Dialog
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.ScalingLazyColumnDefaults
import kotlinx.coroutines.launch
import mx.budget.wear.data.ExpenseSender

/**
 * Pantalla principal en el Reloj (Wear OS).
 * Permite inserción veloz de presets sobre un flujo UI unimanual para smartwatch (Pixel Watch).
 */
@Composable
fun QuickExpenseApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sender = remember { ExpenseSender(context) }
    var showConfirmation by remember { mutableStateOf(false) }

    // Preajustes Hardcodeados (Idealmente editables desde móvil)
    val presets = listOf(
        "Café" to 60.0,
        "Gasolina" to 500.0,
        "Propinas" to 50.0,
        "Suscripción" to 149.0
    )

    MaterialTheme {
        ScalingLazyColumn(
            columnState = ScalingLazyColumnDefaults.responsive().create()
        ) {
            item {
                Text(
                    text = "Quick Capture",
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            
            items(presets.size) { index ->
                val (concept, amount) = presets[index]
                
                Button(
                    onClick = {
                        scope.launch {
                            val result = sender.sendQuickExpense(amount, concept)
                            if (result.isSuccess) {
                                showConfirmation = true
                            }
                        }
                    },
                    colors = ButtonDefaults.primaryButtonColors(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("$concept $${amount.toInt()}")
                }
            }
        }
    }

    // Modal de finalización nativo Wear
    Dialog(
        showDialog = showConfirmation,
        onDismissRequest = { showConfirmation = false }
    ) {
        Confirmation(
            onTimeout = { showConfirmation = false },
            icon = { /* Reemplazar con iconografía verde en el proyecto base */ },
        ) {
            Text(
                "Enviado al Móvil",
                textAlign = TextAlign.Center
            )
        }
    }
}
