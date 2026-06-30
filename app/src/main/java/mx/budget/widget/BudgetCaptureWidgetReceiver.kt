package mx.budget.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Receiver del widget de captura (§G.3). Punto de registro en el manifest;
 * delega todo el render a [BudgetCaptureWidget].
 */
class BudgetCaptureWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BudgetCaptureWidget()
}
