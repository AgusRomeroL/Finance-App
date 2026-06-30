package mx.budget.wear.presentation.tile

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.wear.tiles.GlanceTileService

/**
 * Tile Glance para proveer un vistazo al saldo de forma estática en la Home del Reloj.
 * Lee desde SharedPreferences (poblado por [mx.budget.wear.data.MobileSyncListenerService])
 * para evitar bloquear la UI con llamadas a Room/Datastore.
 */
class BudgetGlanceTileService : GlanceTileService() {

    @Composable
    override fun Content() {
        val prefs = applicationContext.getSharedPreferences("wear_budget_prefs", Context.MODE_PRIVATE)
        val balance = prefs.getFloat("latest_balance", 0.0f)
        val label = prefs.getString("latest_label", "Sin sincronizar") ?: "Sin sincronizar"

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DISPONIBLE",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = ColorProvider(0xFFAAAAAA.toInt()) // onSurfaceVariant aproximado
                )
            )

            Text(
                text = "$ ${String.format("%,.0f", balance)}",
                style = TextStyle(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorProvider(0xFF016E3E.toInt()) // verde sembrado (primary fallback)
                )
            )

            Text(
                text = label,
                style = TextStyle(
                    fontSize = 10.sp,
                    color = ColorProvider(0xFF777777.toInt())
                ),
                modifier = GlanceModifier.padding(top = 4.dp)
            )
        }
    }
}
