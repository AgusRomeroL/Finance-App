package mx.budget.ui.masters

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.budget.data.local.entity.CategoryEntity
import mx.budget.ui.common.ColorPickerDialog

private fun parseColor(hex: String?): Color? =
    hex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }

/** CRUD de categorías en árbol (paquete B2). Sincronizable (encola CATEGORY). */
@Composable
fun CategoriesScreen(viewModel: CategoriesMasterViewModel, onBack: () -> Unit) {
    val tree by viewModel.tree.collectAsState()
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    // Alta: si addParentId != "" abre el diálogo (null = raíz, id = bajo ese grupo).
    var addUnderParent by remember { mutableStateOf<String?>("__none__") }
    val addOpen = addUnderParent != "__none__"

    MasterScaffold(
        eyebrow = "ADMINISTRAR",
        title = "Categorías",
        onBack = onBack,
        onAdd = { addUnderParent = null },
    ) {
        if (tree.isEmpty()) EmptyHint("Aún no hay categorías. Agrega la primera con el botón +.")
        tree.forEach { node ->
            // Encabezado de grupo raíz (tap para editar; botón para agregar hoja).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val dot = parseColor(node.category.colorHex)
                if (dot != null) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(dot))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    node.category.displayName.uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).clickable { editing = node.category },
                )
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { addUnderParent = node.category.id },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Add, "Agregar subcategoría", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                }
            }
            node.children.forEach { child ->
                MasterRow(
                    title = child.displayName,
                    subtitle = child.budgetDefaultMxn?.let { "Presupuesto: $${it.toLong()}" } ?: "Sin presupuesto",
                    colorDot = parseColor(child.colorHex),
                    onClick = { editing = child },
                )
            }
        }
    }

    if (addOpen) {
        CategoryDialog(
            initial = null,
            parentId = addUnderParent,
            onSave = { name, color, budget ->
                viewModel.addCategory(name, addUnderParent, color, budget); addUnderParent = "__none__"
            },
            onDismiss = { addUnderParent = "__none__" },
        )
    }
    editing?.let { c ->
        CategoryDialog(
            initial = c,
            parentId = c.parentId,
            onSave = { name, color, budget -> viewModel.updateCategory(c, name, color, budget); editing = null },
            onArchive = { viewModel.archive(c); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun CategoryDialog(
    initial: CategoryEntity?,
    parentId: String?,
    onSave: (name: String, colorHex: String?, budget: Double?) -> Unit,
    onArchive: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial?.displayName ?: "") }
    var colorHex by rememberSaveable { mutableStateOf(initial?.colorHex) }
    var budget by rememberSaveable { mutableStateOf(initial?.budgetDefaultMxn?.toLong()?.toString() ?: "") }
    var showColor by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) (if (parentId == null) "Nueva categoría" else "Nueva subcategoría") else "Editar categoría") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = budget,
                    onValueChange = { budget = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Presupuesto por quincena (opcional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(parseColor(colorHex) ?: MaterialTheme.colorScheme.primary))
                        Spacer(Modifier.width(12.dp))
                        Text("Color", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    }
                    TextButton(onClick = { showColor = true }) { Text("Cambiar") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(name, colorHex, budget.filter { it.isDigit() || it == '.' }.toDoubleOrNull())
                },
                enabled = name.isNotBlank(),
            ) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                if (onArchive != null) TextButton(onClick = onArchive) { Text("Archivar") }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )

    if (showColor) {
        ColorPickerDialog(
            title = "Color de la categoría",
            selectedHex = colorHex,
            onSelect = { colorHex = it; showColor = false },
            onDismiss = { showColor = false },
        )
    }
}
