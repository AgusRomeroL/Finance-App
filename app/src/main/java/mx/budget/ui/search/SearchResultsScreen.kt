package mx.budget.ui.search

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.unit.dp
import mx.budget.ui.common.SearchPill
import mx.budget.ui.dashboard.DashboardViewModel
import mx.budget.ui.dashboard.FilterBottomSheet
import mx.budget.ui.dashboard.FilterPillsRow
import mx.budget.ui.dashboard.TransactionRow

/**
 * Pantalla de resultados de búsqueda. La barra de búsqueda editable vive ARRIBA
 * (paridad con la barra superior del dashboard, estilo Recorder/Contactos): botón
 * atrás + [SearchPill] editable. Antes vivía abajo en una BottomActionBar que, con la
 * app ya edge-to-edge, quedaba OCULTA tras la barra de navegación transparente e
 * impedía escribir — bug corregido aquí. Reutiliza [TransactionRow] y comparte los
 * filtros por grupo con el dashboard ([DashboardViewModel]).
 */
@Composable
fun SearchResultsScreen(
    searchViewModel: SearchViewModel,
    dashboardViewModel: DashboardViewModel,
    onBack: () -> Unit
) {
    val query by searchViewModel.query.collectAsState()
    val rawResults by searchViewModel.results.collectAsState()
    val groups by dashboardViewModel.groups.collectAsState()
    val selectedGroups by dashboardViewModel.selectedGroupIds.collectAsState()
    var showFilterSheet by remember { mutableStateOf(false) }

    // Aplica el filtro de grupos (recompone cuando cambia la selección).
    val results = remember(rawResults, selectedGroups) { dashboardViewModel.applyGroupFilter(rawResults) }

    if (showFilterSheet) {
        FilterBottomSheet(
            groups = groups,
            selected = selectedGroups,
            onSave = { dashboardViewModel.setSelectedGroups(it); showFilterSheet = false },
            onDismiss = { showFilterSheet = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Barra superior: back + búsqueda editable.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 20.dp, top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, "Volver",
                    tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp)
                )
            }
            SearchPill(
                query = query,
                onQueryChange = searchViewModel::onQueryChange,
                readOnly = false,
                modifier = Modifier.weight(1f)
            )
        }

        FilterPillsRow(
            groups = groups,
            selectedGroupIds = selectedGroups,
            onToggle = { id ->
                dashboardViewModel.setSelectedGroups(
                    if (id in selectedGroups) selectedGroups - id else selectedGroups + id
                )
            },
            onOpenSheet = { showFilterSheet = true },
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(8.dp))

        when {
            query.isBlank() -> CenteredHint("Escribe para buscar entre tus movimientos.")
            results.isEmpty() -> CenteredHint("Sin resultados para \"$query\".")
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(results, key = { it.expenseId }) { tx -> TransactionRow(tx) }
            }
        }
    }
}

@Composable
private fun CenteredHint(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
