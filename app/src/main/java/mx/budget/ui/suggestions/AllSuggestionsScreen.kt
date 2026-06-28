package mx.budget.ui.suggestions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.budget.ui.capture.CaptureBottomSheet
import mx.budget.ui.capture.CaptureViewModel
import mx.budget.ui.dashboard.DashboardViewModel
import mx.budget.ui.dashboard.SmartSuggestionCard
import mx.budget.ui.dashboard.buildSuggestionItems

/**
 * Pantalla "Todas las sugerencias": lista completa de capturas bancarias (D) y
 * sugerencias proactivas (C) con sus acciones. Reutiliza el `DashboardViewModel`
 * (flows + acciones) y la tarjeta [SmartSuggestionCard]; el "Registrar" prefila la
 * captura igual que en el dashboard.
 */
@Composable
fun AllSuggestionsScreen(
    dashboardViewModel: DashboardViewModel,
    captureViewModel: CaptureViewModel?,
    onBack: () -> Unit
) {
    val bankCaptures by dashboardViewModel.pendingBankCaptures.collectAsState()
    val proactiveSuggestions by dashboardViewModel.proactiveSuggestions.collectAsState()
    var showCapture by remember { mutableStateOf(false) }

    if (showCapture) {
        CaptureBottomSheet(viewModel = captureViewModel, onDismiss = { showCapture = false })
    }

    val items = buildSuggestionItems(bankCaptures, proactiveSuggestions)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    "INTELIGENCIA",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.6.sp
                )
                Text(
                    "Sugerencias",
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light, fontSize = 28.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No hay sugerencias por ahora.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items, key = { it.key }) { item ->
                    SmartSuggestionCard(
                        item = item,
                        onConfirmCapture = dashboardViewModel::confirmBankCapture,
                        onDismissCapture = dashboardViewModel::dismissBankCapture,
                        onRegisterSuggestion = { suggestion ->
                            captureViewModel?.onConceptChange(suggestion.concept)
                            captureViewModel?.onCategorySelected(suggestion.categoryId)
                            showCapture = true
                        },
                        onDismissSuggestion = dashboardViewModel::dismissProactiveSuggestion
                    )
                }
            }
        }
    }
}
