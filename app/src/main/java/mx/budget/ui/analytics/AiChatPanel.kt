package mx.budget.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.budget.ai.AiAssistantUiState
import mx.budget.ai.AiAssistantViewModel
import mx.budget.ai.ChatMessage
import mx.budget.ai.domain.AssistantResponse
import mx.budget.ai.domain.DispatchResult
import java.text.NumberFormat
import java.util.Locale

/**
 * Chat del asistente reactivo (MVP Fase 4), hospedado como bottom sheet desde
 * Analíticas. Con LLM disponible acepta pregunta libre; sin LLM (emulador)
 * ofrece chips predefinidos que van directo al dispatcher determinista.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatSheet(
    viewModel: AiAssistantViewModel,
    onDismiss: () -> Unit,
) {
    val history by viewModel.chatHistory.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val llmAvailable by viewModel.llmAvailable.collectAsState()
    val money = remember { NumberFormat.getCurrencyInstance(Locale("es", "MX")) }
    val listState = rememberLazyListState()

    // Cada apertura del sheet reintenta el sondeo si aún no está disponible —
    // cubre el caso de un `Unavailable` previo que ya podría haberse resuelto
    // (p. ej. el modelo se empujó por adb después de arrancar la app).
    LaunchedEffect(Unit) { viewModel.ensureLlmReadinessChecked() }

    // Auto-scroll al último mensaje.
    LaunchedEffect(history.size) {
        if (history.isNotEmpty()) listState.animateScrollToItem(history.lastIndex)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = 640.dp,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                "Asistente del presupuesto",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                if (llmAvailable) "Pregunta libre (LLM on-device) o usa un atajo."
                else "Sin LLM en este dispositivo — usa los atajos deterministas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))

            // Historial.
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(history, key = { it.id }) { msg -> ChatBubble(msg, money) }
            }

            if (uiState is AiAssistantUiState.Thinking) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Pensando…", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Atajos deterministas (sin args) — una sola fila horizontal
            // desplazable, como los chips de filtro (no crecen en vertical).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                predefinedChips.forEach { (label, intent) ->
                    AssistChip(
                        onClick = {
                            viewModel.sendPredefined(
                                label,
                                AssistantResponse(intent = intent, args = AssistantResponse.Args(), reason = ""),
                            )
                        },
                        label = { Text(label, maxLines = 1) },
                    )
                }
            }

            // Pregunta libre — solo con LLM disponible.
            if (llmAvailable) {
                Spacer(Modifier.height(10.dp))
                var input by remember { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("¿Cuánto queda en Comida?") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { viewModel.sendQuery(input); input = "" },
                        enabled = input.isNotBlank() && uiState !is AiAssistantUiState.Thinking,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar")
                    }
                }
            }
        }
    }
}

private val predefinedChips = listOf(
    "Resumen de la quincena" to AssistantResponse.Intent.SUMMARIZE_QUINCENA,
    "¿En qué gasto más?" to AssistantResponse.Intent.GET_TOP_CATEGORY,
    "¿Quién gasta más?" to AssistantResponse.Intent.GET_TOP_SPENDER,
    "Comparar quincenas" to AssistantResponse.Intent.COMPARE_QUINCENAS,
    "Cuotas pendientes" to AssistantResponse.Intent.LIST_UPCOMING_INSTALLMENTS,
)

@Composable
private fun ChatBubble(msg: ChatMessage, money: NumberFormat) {
    val isUser = msg.role == ChatMessage.Role.USER
    Box(Modifier.fillMaxWidth(), contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = when (msg.role) {
                ChatMessage.Role.USER -> MaterialTheme.colorScheme.primaryContainer
                ChatMessage.Role.ERROR -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ) {
            Text(
                text = msg.result?.let { formatResult(it, money) } ?: msg.text,
                style = MaterialTheme.typography.bodyMedium,
                color = when (msg.role) {
                    ChatMessage.Role.USER -> MaterialTheme.colorScheme.onPrimaryContainer
                    ChatMessage.Role.ERROR -> MaterialTheme.colorScheme.onErrorContainer
                    else -> MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

/** Render determinista y legible de cada [DispatchResult]. */
private fun formatResult(result: DispatchResult, money: NumberFormat): String = when (result) {
    is DispatchResult.CategoryRemaining ->
        "${result.category.displayName}: gastado ${money.format(result.actual)} de " +
            "${money.format(result.projected)} presupuestados — quedan ${money.format(result.remaining)}."

    is DispatchResult.TopCategories -> buildString {
        append("Donde más se gasta esta quincena:")
        result.categories.forEachIndexed { i, c ->
            val pct = if (result.totalMxn > 0) (c.actual / result.totalMxn * 100).toInt() else 0
            append("\n${i + 1}. ${c.categoryName}: ${money.format(c.actual)} ($pct % del total)")
        }
    }

    is DispatchResult.TopSpender ->
        "El mayor gasto de la quincena lo concentra ${result.member.displayName}: " +
            "${money.format(result.totalMxn)} (${"%.0f".format(result.share * 100)} % del total atribuido)."

    is DispatchResult.SpendByMemberResult -> buildString {
        append("${result.member.displayName} lleva ${money.format(result.totalMxn)} en la quincena.")
        result.byCategory.take(4).forEach {
            append("\n· ${it.categoryName}: ${money.format(it.totalMxn)}")
        }
    }

    is DispatchResult.WalletBalance ->
        "${result.wallet.displayName}: saldo actual ${money.format(result.balance)}."

    is DispatchResult.InstallmentStatus ->
        result.summary?.let {
            "${it.displayName}: cuota ${it.currentInstallment} de ${it.totalInstallments} " +
                "(${money.format(it.installmentAmountMxn)} c/u) — restan ${money.format(it.remainingMxn)}."
        } ?: "${result.plan.displayName}: sin resumen activo (¿ya liquidado?)."

    is DispatchResult.SavingsProjection ->
        "Si cortaras ${result.hypotheticalCutCategory.displayName} ahorrarías " +
            "${money.format(result.deltaMxn)} por quincena. " +
            result.assumptions.joinToString(" ", prefix = "(", postfix = ")")

    is DispatchResult.QuincenaComparison -> result.comparisonText

    is DispatchResult.VarianceExplanation -> result.explanation

    is DispatchResult.UpcomingInstallments ->
        if (result.installments.isEmpty()) "No hay planes a meses activos."
        else result.installments.joinToString("\n") {
            "· ${it.displayName}: ${it.currentInstallment}/${it.totalInstallments}, restan ${money.format(it.remainingMxn)}"
        }

    is DispatchResult.QuincenaSummary -> with(result.summary) {
        "$label — ingreso ${money.format(actualIncomeMxn)}, gasto ${money.format(actualExpensesMxn)}; " +
            "diferencia ${money.format(savingsMxn)}."
    }

    // Pregunta fuera del dominio financiero (saludo, charla): en vez de un
    // seco "no pude resolverlo", el asistente se ofrece proactivamente y guía.
    DispatchResult.OutOfScope -> ASSISTANT_CAPABILITIES

    is DispatchResult.Unknown ->
        // Errores internos con dato concreto (sin quincena activa, miembro no
        // hallado, etc.) se muestran tal cual; si viene vacío, guía proactiva.
        result.reason.ifBlank { ASSISTANT_CAPABILITIES }

    is DispatchResult.ParseError ->
        "No estoy seguro de haber entendido eso. $ASSISTANT_CAPABILITIES"

    is DispatchResult.MissingArg -> "Me faltó un dato (${result.argumentName}). Especifica a qué te refieres."
}

/**
 * Mensaje proactivo del asistente cuando la pregunta cae fuera de su dominio o
 * no se pudo mapear. No está cableado a detectar saludos: es la manera general
 * de decirle al usuario en qué SÍ puede ayudar, invitándolo a preguntar.
 */
private const val ASSISTANT_CAPABILITIES =
    "Estoy aquí para ayudarte con tus finanzas. Puedes preguntarme, por ejemplo:\n" +
        "· En qué gastas más\n" +
        "· Cuánto queda en una categoría\n" +
        "· Quién gasta más\n" +
        "· El saldo de una cuenta\n" +
        "· Cómo va la quincena\n" +
        "· Tus cuotas pendientes"
