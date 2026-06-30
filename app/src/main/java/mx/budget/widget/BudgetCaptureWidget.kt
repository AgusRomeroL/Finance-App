package mx.budget.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import mx.budget.ui.capture.QuickCaptureActivity
import mx.budget.ui.capture.VoiceCaptureActivity

/**
 * Widget de pantalla de inicio (Jetpack Glance) enfocado en captura (§G.3).
 *
 * Glance/RemoteViews no soporta entrada de texto libre, así que el widget es un
 * **lanzador**, no un formulario: un botón abre la captura por voz
 * ([VoiceCaptureActivity], `source=WIDGET`) y otro la captura manual rápida
 * ([QuickCaptureActivity]). Ambos caen en la bandeja unificada
 * (propose-then-confirm); el widget nunca toca el ledger directo.
 */
class BudgetCaptureWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Content(context) }
    }

    @Composable
    private fun Content(context: Context) {
        val voiceIntent = Intent(context, VoiceCaptureActivity::class.java)
            .putExtra(VoiceCaptureActivity.EXTRA_SOURCE, "WIDGET")
        val quickIntent = Intent(context, QuickCaptureActivity::class.java)

        GlanceTheme {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(GlanceTheme.colors.widgetBackground)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Captura rápida",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Spacer(GlanceModifier.height(10.dp))
                PillButton(
                    label = "🎤  Por voz",
                    background = GlanceTheme.colors.primary,
                    foreground = GlanceTheme.colors.onPrimary,
                    onClick = actionStartActivity(voiceIntent),
                )
                Spacer(GlanceModifier.height(6.dp))
                PillButton(
                    label = "＋  Gasto",
                    background = GlanceTheme.colors.secondaryContainer,
                    foreground = GlanceTheme.colors.onSecondaryContainer,
                    onClick = actionStartActivity(quickIntent),
                )
            }
        }
    }

    @Composable
    private fun PillButton(
        label: String,
        background: ColorProvider,
        foreground: ColorProvider,
        onClick: androidx.glance.action.Action,
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(background)
                .cornerRadius(20.dp)
                .padding(vertical = 10.dp)
                .clickable(onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = TextStyle(
                    color = foreground,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}
