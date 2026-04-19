package mx.budget.wear.presentation.tile

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.wear.tiles.GlanceTileService
import androidx.glance.unit.ColorProvider
import mx.budget.wear.presentation.QuickExpenseAppActivity // Asumiendo actividad entry

/**
 * Tile Glance para proveer un vistazo al saldo de forma estática en la Home del Reloj.
 * Lee desde SharedPreferences para evitar bloquear la UI con llamadas a Android Datastore/Room.
 */
class BudgetGlanceTileService : GlanceTileService() {

    @Composable
    override fun Content() {
        val prefs = applicationContext.getSharedPreferences("wear_budget_prefs", Context.MODE_PRIVATE)
        val balance = prefs.getFloat("latest_balance", 0.0f)
        val label = prefs.getString("latest_label", "Sin sincronizar") ?: "Sin sincronizar"

        // Material Design for Wear OS minimalist styling
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(16.dp)
                // Abre nuestra aplicación principal al tapear
                // .clickable(actionStartActivity<QuickExpenseAppActivity>())
                , 
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
                    color = ColorProvider(0xFF388E3C.toInt()) // Esmeralda / Primary
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
