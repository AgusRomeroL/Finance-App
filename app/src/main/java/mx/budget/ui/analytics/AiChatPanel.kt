package mx.budget.ui.analytics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import mx.budget.ai.AiAssistantUiState
import mx.budget.ai.AiAssistantViewModel
import mx.budget.ai.ChatMessage
import mx.budget.ai.domain.AssistantResponse
import mx.budget.ai.domain.DispatchResult
import java.text.NumberFormat
import java.util.Locale

/**
 * Chat del asistente reactivo (MVP Fase 4 + paquete A1), hospedado como bottom
 * sheet desde Analíticas. Con LLM disponible la pregunta libre corre el
 * pipeline RAG completo; sin LLM (emulador) la misma caja usa la heurística
 * determinista + la ruta OPEN_ANALYSIS local — el chat siempre responde algo
 * con datos reales.
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
                else "Sin LLM en este dispositivo — respuestas deterministas con tus datos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))

            // Historial — cada mensaje nuevo aparece con resorte M3.
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(history, key = { it.id }) { msg ->
                    ChatBubble(
                        msg = msg,
                        money = money,
                        modifier = Modifier.animateItem(
                            fadeInSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
                            placementSpec = spring(
                                dampingRatio = 0.8f,
                                stiffness = 380f,
                                visibilityThreshold = IntOffset.VisibilityThreshold,
                            ),
                            fadeOutSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
                        ),
                    )
                }
            }

            // Indicador "Pensando…" con entrada/salida animada por resorte.
            AnimatedVisibility(
                visible = uiState is AiAssistantUiState.Thinking,
                enter = fadeIn(spring(dampingRatio = 0.8f, stiffness = 380f)) +
                    expandVertically(spring(dampingRatio = 0.8f, stiffness = 380f)),
                exit = fadeOut(spring(dampingRatio = 0.8f, stiffness = 380f)) +
                    shrinkVertically(spring(dampingRatio = 0.8f, stiffness = 380f)),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Pensando…", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Atajos — pills completos (mismo lenguaje visual que los filtros
            // del dashboard), en una sola fila horizontal desplazable.
            val thinking = uiState is AiAssistantUiState.Thinking
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ShortcutPill(
                    label = "¿Algún patrón inusual?",
                    enabled = !thinking,
                    onClick = {
                        viewModel.sendOpenAnalysis("¿Qué patrón no sobresale a simple vista en mis gastos?")
                    },
                )
                predefinedChips.forEach { (label, intent) ->
                    ShortcutPill(
                        label = label,
                        enabled = !thinking,
                        onClick = {
                            viewModel.sendPredefined(
                                label,
                                AssistantResponse(intent = intent, args = AssistantResponse.Args(), reason = ""),
                            )
                        },
                    )
                }
            }

            // Pregunta libre — siempre disponible: con LLM corre el RAG; sin
            // LLM cae a la heurística determinista + análisis local.
            Spacer(Modifier.height(10.dp))
            var input by remember { mutableStateOf("") }
            val send: () -> Unit = {
                if (input.isNotBlank() && !thinking) {
                    viewModel.sendQuery(input)
                    input = ""
                }
            }
            ChatInputPill(
                value = input,
                onValueChange = { input = it },
                onSend = send,
                sendEnabled = input.isNotBlank() && !thinking,
            )
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

/** Pill de atajo con shape circular completo, como los filtros del dashboard. */
@Composable
private fun ShortcutPill(label: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Input de pregunta libre con el look del SearchPill del dashboard: Surface
 * pill tonal con el botón de enviar integrado a la derecha.
 */
@Composable
private fun ChatInputPill(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    sendEnabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 18.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    "Pregunta sobre tus finanzas",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(
                    if (sendEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHighest
                )
                .clickable(enabled = sendEnabled, onClick = onSend),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = "Enviar",
                tint = if (sendEnabled) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ChatBubble(msg: ChatMessage, money: NumberFormat, modifier: Modifier = Modifier) {
    val isUser = msg.role == ChatMessage.Role.USER
    val openAnalysis = msg.result as? DispatchResult.OpenAnalysis
    Box(
        modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Surface(
            shape = if (isUser) RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
            else RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
            color = when (msg.role) {
                ChatMessage.Role.USER -> MaterialTheme.colorScheme.primaryContainer
                ChatMessage.Role.ERROR -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                // Badge de la ruta OPEN_ANALYSIS: distingue la respuesta
                // redactada por el LLM del insight determinista local.
                if (openAnalysis != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (openAnalysis.deterministic) "Análisis local" else "Análisis IA",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    text = msg.result?.let { formatResult(it, money) } ?: msg.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (msg.role) {
                        ChatMessage.Role.USER -> MaterialTheme.colorScheme.onPrimaryContainer
                        ChatMessage.Role.ERROR -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
            }
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

    // Ruta OPEN_ANALYSIS: el texto ya viene redactado (LLM o plantillas).
    is DispatchResult.OpenAnalysis -> result.text

    // Estos tres casos los intercepta el ViewModel (needsOpenAnalysis) antes de
    // llegar aquí; se dejan textos honestos por exhaustividad del `when`.
    DispatchResult.OutOfScope ->
        "Eso queda fuera de lo que puedo ver en el presupuesto, pero pregúntame sobre tus gastos y te ayudo."

    is DispatchResult.Unknown ->
        result.reason.ifBlank { "No pude resolverlo con los datos disponibles. Intenta reformular la pregunta." }

    is DispatchResult.ParseError ->
        "No estoy seguro de haber entendido eso. Intenta reformular la pregunta."

    is DispatchResult.MissingArg -> "Me faltó un dato (${result.argumentName}). Especifica a qué te refieres."
}
