package mx.budget.ui.quicktap

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import mx.budget.data.local.entity.CategoryEntity
import mx.budget.data.local.entity.PaymentMethodEntity
import mx.budget.ui.capture.CaptureOperationState
import mx.budget.ui.capture.CaptureViewModel
import mx.budget.ui.common.AppLocale
import mx.budget.ui.common.AutoSizeAmountText
import mx.budget.ui.theme.BudgetMotion
import java.text.NumberFormat

/**
 * Panel flotante de Quick Tap (especificación §3.3, componentes B y C).
 *
 * No es una pantalla: se pinta encima de lo que la persona estuviera haciendo y
 * tiene que poder cerrarse sin haber costado nada. De ahí las tres reglas que
 * mandan sobre el diseño:
 *
 * 1. El importe tiene el foco desde el primer frame y guardar es posible en un
 *    solo toque después de teclearlo. Si el diseño exige elegir categoría, no
 *    sirve: lo predicho viene resuelto y solo se toca si está mal.
 * 2. Las sugerencias guardan el gasto entero de un toque, sin teclear nada.
 * 3. A los seis segundos sin tocar nada se cierra solo, porque el doble golpe en
 *    la espalda del teléfono también ocurre por accidente.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickCapturePanel(
    viewModel: CaptureViewModel,
    suggestions: List<QuickSuggestion>,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    onRequestFocus: () -> Unit,
) {
    val money = remember { NumberFormat.getCurrencyInstance(AppLocale) }
    val displayAmount by viewModel.displayAmount.collectAsState()
    val concepto by viewModel.concept.collectAsState()
    val categoriaId by viewModel.selectedCategoryId.collectAsState()
    val walletId by viewModel.selectedWalletId.collectAsState()
    val recientes by viewModel.recentCategories.collectAsState()
    // La categoría predicha no tiene por qué estar entre las recientes: el chip
    // busca su nombre en el catálogo completo o se quedaría diciendo "Categoría".
    val catalogo by viewModel.categories.collectAsState()
    val cuentas by viewModel.wallets.collectAsState()
    val operacion by viewModel.operationState.collectAsState()
    val miembros by viewModel.members.collectAsState()
    val beneficiarios by viewModel.beneficiaryShares.collectAsState()
    val pagadores by viewModel.payerShares.collectAsState()
    val faltantes by viewModel.missingFields.collectAsState()
    val senalarFaltantes by viewModel.showMissingHighlights.collectAsState()

    var visible by remember { mutableStateOf(false) }
    var interactuado by remember { mutableStateOf(false) }
    var restante by remember { mutableStateOf(AUTOCIERRE_SEGUNDOS) }

    LaunchedEffect(Unit) { visible = true }

    // La atribución por defecto se aplica cuando la lista de miembros llega, no
    // antes: el panel se pinta en cuanto hay ventana y esa consulta puede tardar
    // un latido más. Sin esto el reparto quedaba vacío y guardar no hacía nada.
    LaunchedEffect(miembros) {
        if (miembros.isEmpty()) return@LaunchedEffect
        if (beneficiarios.isEmpty()) viewModel.onSelectAllMembers()
        // Y un pagador. La hoja completa lo deduce de la cuenta o de la sesión;
        // aquí puede no haber ninguno de los dos (cuenta sin dueño, sesión sin
        // miembro vinculado) y sin pagador el gasto no se puede guardar. Se elige
        // el primer adulto que paga, que es lo que haría cualquiera.
        if (pagadores.isEmpty()) {
            val adulto = miembros.firstOrNull { it.role == "PAYER_ADULT" } ?: miembros.first()
            viewModel.onPayerToggled(adulto.id)
        }
    }

    // Cuenta atrás del cierre automático. Cualquier toque la cancela para
    // siempre: quien empezó a capturar no quiere que el panel se le vaya.
    LaunchedEffect(interactuado) {
        if (interactuado) return@LaunchedEffect
        while (restante > 0) {
            delay(1_000)
            restante--
        }
        visible = false
        delay(220)
        onDismiss()
    }

    LaunchedEffect(operacion) {
        if (operacion is CaptureOperationState.Success) {
            visible = false
            delay(220)
            viewModel.onOperationStateConsumed()
            onSaved()
        }
    }

    fun tocado() {
        interactuado = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(BudgetMotion.standard()) { -it } + fadeIn(BudgetMotion.standard()),
        exit = slideOutVertically(BudgetMotion.standard()) { -it } + fadeOut(BudgetMotion.standard()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Encabezado(onClose = onDismiss)

            Importe(
                texto = displayAmount,
                onFocus = { tocado(); onRequestFocus() },
            )

            Predicciones(
                concepto = concepto,
                categoria = catalogo.firstOrNull { it.id == categoriaId },
                cuenta = cuentas.firstOrNull { it.id == walletId },
                recientes = recientes,
                cuentas = cuentas,
                onCategoria = { tocado(); viewModel.onCategorySelected(it) },
                onCuenta = { tocado(); viewModel.onWalletSelected(it) },
            )

            if (suggestions.isNotEmpty()) {
                Sugerencias(
                    sugerencias = suggestions,
                    money = money,
                    onElegir = { sugerencia ->
                        tocado()
                        sugerencia.apply(viewModel)
                        viewModel.onRegisterAttempt()
                    },
                )
            }

            PieDelPanel(
                restante = restante,
                contando = !interactuado,
                guardando = operacion is CaptureOperationState.Loading,
                onGuardar = { tocado(); viewModel.onRegisterAttempt() },
            )

            (operacion as? CaptureOperationState.Error)?.let {
                Text(
                    it.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // Si falta algo, el panel lo dice. Un botón que no responde y no
            // explica por qué es peor que un botón apagado.
            if (senalarFaltantes && faltantes.isNotEmpty()) {
                Text(
                    "Falta " + faltantes.joinToString(", ") { campo ->
                        when (campo.name) {
                            "AMOUNT" -> "el monto"
                            "CATEGORY" -> "la categoría"
                            "WALLET" -> "la cuenta"
                            "BENEFICIARY" -> "quién consume"
                            "PAYER" -> "quién paga"
                            "CONCEPT" -> "el concepto"
                            else -> "la fecha"
                        }
                    } + ". Ábrelo en la app para completarlo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun Encabezado(onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            "GASTO RÁPIDO",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            letterSpacing = 1.6.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Cerrar",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun Importe(texto: String, onFocus: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onFocus)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        AutoSizeAmountText(
            text = "$$texto",
            baseStyle = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light),
            maxFontSp = 44f,
            minFontSp = 26f,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            "MXN",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            letterSpacing = 1.6.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.primary)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Predicciones(
    concepto: String,
    categoria: CategoryEntity?,
    cuenta: PaymentMethodEntity?,
    recientes: List<CategoryEntity>,
    cuentas: List<PaymentMethodEntity>,
    onCategoria: (String) -> Unit,
    onCuenta: (String) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChipDePanel(
            icono = Icons.Filled.Sell,
            texto = categoria?.displayName ?: concepto.ifBlank { "Categoría" },
            onClick = {
                // Un toque rota a la siguiente reciente: en un panel de seis
                // segundos no cabe abrir un selector.
                val indice = recientes.indexOfFirst { it.id == categoria?.id }
                recientes.getOrNull((indice + 1).coerceAtMost(recientes.lastIndex))
                    ?.let { onCategoria(it.id) }
            },
        )
        ChipDePanel(
            icono = Icons.Filled.AccountBalanceWallet,
            texto = cuenta?.displayName ?: "Cuenta",
            onClick = {
                val indice = cuentas.indexOfFirst { it.id == cuenta?.id }
                cuentas.getOrNull((indice + 1).coerceAtMost(cuentas.lastIndex))
                    ?.let { onCuenta(it.id) }
            },
        )
        ChipDePanel(icono = Icons.Filled.Group, texto = "Todos", onClick = {})
    }
}

@Composable
private fun ChipDePanel(
    icono: androidx.compose.ui.graphics.vector.ImageVector,
    texto: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            icono,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            texto,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
        )
    }
}

@Composable
private fun Sugerencias(
    sugerencias: List<QuickSuggestion>,
    money: NumberFormat,
    onElegir: (QuickSuggestion) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "LAS DE SIEMPRE A ESTA HORA",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            letterSpacing = 1.6.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        sugerencias.take(3).forEach { sugerencia ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable(role = Role.Button) { onElegir(sugerencia) }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    sugerencia.concepto,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    money.format(sugerencia.montoMxn),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun PieDelPanel(
    restante: Int,
    contando: Boolean,
    guardando: Boolean,
    onGuardar: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            if (contando) {
                Text(
                    "Se cierra solo en ${restante}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { restante / AUTOCIERRE_SEGUNDOS.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Row(
            modifier = Modifier
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(enabled = !guardando, role = Role.Button, onClick = onGuardar)
                .padding(horizontal = 26.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (guardando) "Guardando…" else "Guardar",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

private const val AUTOCIERRE_SEGUNDOS = 6
