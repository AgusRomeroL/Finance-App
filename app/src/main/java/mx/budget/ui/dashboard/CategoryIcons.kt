package mx.budget.ui.dashboard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Spa
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Ícono Material (glyph monocromo) por grupo de categoría, para los pills de filtro.
 * Determinista por `code` (prefijo del grupo en categorías hoja "HOUSING.TELEFONO").
 * Material Icons (vectores) en vez de emoji: monocromos nativos, tintables, estables
 * en claro/oscuro.
 */
fun iconForCategory(code: String?): ImageVector = when (code?.substringBefore('.')) {
    "INGRESOS" -> Icons.Filled.Payments
    "HOUSING" -> Icons.Filled.Home
    "TRANSPORTATION" -> Icons.Filled.DirectionsCar
    "SEGUROS_MEDICOS" -> Icons.Filled.LocalHospital
    "FOOD" -> Icons.Filled.Restaurant
    "PETS" -> Icons.Filled.Pets
    "ENTERTAINMENT" -> Icons.Filled.Movie
    "LOANS" -> Icons.Filled.CreditCard
    "TRANSFERENCIAS_FAMILIARES" -> Icons.Filled.Groups
    "ESCUELA" -> Icons.Filled.School
    "SAVINGS" -> Icons.Filled.Savings
    "GIFTS" -> Icons.Filled.CardGiftcard
    "LEGAL" -> Icons.Filled.Gavel
    "PERSONAL_CARE" -> Icons.Filled.Spa
    "SERVICIOS_EXTERNOS" -> Icons.Filled.Build
    "PRESTAMOS_OTORGADOS" -> Icons.Filled.Handshake
    else -> Icons.Filled.Category
}
