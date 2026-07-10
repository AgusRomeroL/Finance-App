package mx.budget.ui.household

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.budget.data.remote.MembershipRepository
import mx.budget.ui.common.LocalSessionMemberId
import mx.budget.ui.common.youLabel

/**
 * Pantalla "Cuenta y grupos" (Fase B). Reúne, en una sola pantalla con scroll,
 * las cuatro operaciones del paquete: iniciar sesión con Google, ver/cambiar el
 * grupo activo, crear un grupo, unirse por código y generar/compartir un código
 * de invitación. Resiliente a fontScale alto + bold (sin anchos fijos, wrap).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HouseholdScreen(
    viewModel: HouseholdViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
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
                    "CUENTA Y GRUPOS",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.6.sp
                )
                Text(
                    "Compartir el hogar",
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light, fontSize = 28.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        AnimatedVisibility(
            visible = state.message != null,
            enter = fadeIn(spring()),
            exit = fadeOut(spring()),
        ) {
            Column {
                // Contraste correcto M3: on*Container sobre *Container (antes era
                // primary sobre primaryContainer y el texto resultaba ilegible);
                // los errores usan el par de error para distinguirse de los avisos.
                val bannerBg = if (state.messageIsError) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.primaryContainer
                val bannerFg = if (state.messageIsError) MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onPrimaryContainer
                Text(
                    state.message ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = bannerFg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(bannerBg)
                        .clickable { viewModel.clearMessage() }
                        .padding(14.dp),
                )
                Spacer(Modifier.height(16.dp))
            }
        }

        // ── Cuenta ──────────────────────────────────────────────────────────
        SectionCard(label = "CUENTA") {
            if (!state.isLinked) {
                Text(
                    "Hoy usas la app de forma anónima. Vincula tu cuenta de Google para compartir el hogar con tu familia entre varios dispositivos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { viewModel.signInWithGoogle() },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Login, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Iniciar sesión con Google")
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            state.displayName ?: "Cuenta vinculada",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (state.email != null) {
                            Text(
                                state.email!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Grupos ──────────────────────────────────────────────────────────
        SectionCard(label = "MIS GRUPOS") {
            if (state.households.isEmpty()) {
                Text(
                    if (state.isLinked) "Aún no perteneces a ningún grupo. Crea uno o únete con un código."
                    else "Vincula tu cuenta para ver y cambiar de grupo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.households.forEach { hh ->
                    val active = hh.householdId == state.activeHouseholdId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { viewModel.switchTo(hh.householdId) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape)
                                .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (active) Icons.Filled.Check else Icons.Filled.Group, null,
                                tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                hh.displayName,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                roleLabel(hh.role),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Crear grupo ─────────────────────────────────────────────────────
        if (state.isLinked) {
            SectionCard(label = "CREAR GRUPO") {
                var name by rememberSaveable { mutableStateOf("") }
                // Limpia el campo SOLO cuando el VM reporta éxito (el contador
                // avanza). Antes se borraba incondicionalmente al pulsar, y un
                // fallo dejaba al usuario sin el nombre que había escrito.
                var seenCreateSuccess by rememberSaveable { mutableStateOf(state.createSuccessCount) }
                LaunchedEffect(state.createSuccessCount) {
                    if (state.createSuccessCount != seenCreateSuccess) {
                        seenCreateSuccess = state.createSuccessCount
                        name = ""
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del grupo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.createHousehold(name) },
                    enabled = !state.busy && name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Crear grupo")
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Unirse por código ───────────────────────────────────────────
            SectionCard(label = "UNIRSE A UN GRUPO") {
                var code by rememberSaveable { mutableStateOf("") }
                // Mismo criterio que "Crear grupo": el código solo se limpia
                // cuando la unión tuvo éxito, no al pulsar el botón.
                var seenJoinSuccess by rememberSaveable { mutableStateOf(state.joinSuccessCount) }
                LaunchedEffect(state.joinSuccessCount) {
                    if (state.joinSuccessCount != seenJoinSuccess) {
                        seenJoinSuccess = state.joinSuccessCount
                        code = ""
                    }
                }
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Código de invitación") },
                    placeholder = { Text("ej. 7QX4K2AB") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { viewModel.joinByCode(code) },
                    enabled = !state.busy && code.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Unirme")
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Invitar (generar y compartir código) ─────────────────────────
            // Gate de producto: invitar exige un GRUPO creado por el usuario y
            // seleccionado — nunca el hogar sembrado (default_household), cuyos
            // datos reales no son para invitados.
            val canInvite = state.activeHouseholdId.isNotBlank() &&
                state.activeHouseholdId != "default_household"
            SectionCard(label = "INVITAR A OTRO DISPOSITIVO") {
                Text(
                    if (canInvite)
                        "Elige quién del grupo es la persona invitada y genera un código de 8 caracteres. Quien lo canjee (app o web) queda vinculada a ese integrante — su rol deriva de él."
                    else
                        "Para invitar, primero crea un grupo (arriba) y selecciónalo. Los invitados entran a ese grupo — nunca a tu presupuesto personal.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))

                // ── Selector de member (invitación nominada, roles v2) ────────
                // El código queda ligado a un member concreto del grupo; el rol
                // DERIVA de ese member (PAYER_* → Administrador, resto →
                // Colaborador). Sin selección, el botón Generar se deshabilita.
                var inviteMemberId by rememberSaveable { mutableStateOf<String?>(null) }
                // Cinturón de identidad: el VM ya excluye al member de la sesión,
                // pero pudo construirse ANTES de que linkedMemberId se resolviera
                // online — se filtra otra vez con el CompositionLocal fresco.
                val sessionId = LocalSessionMemberId.current
                val eligibleMembers = state.eligibleMembers.filter { it.id != sessionId }
                val selectedMember = eligibleMembers.firstOrNull { it.id == inviteMemberId }
                if (canInvite) {
                    AnimatedVisibility(
                        visible = eligibleMembers.isNotEmpty(),
                        enter = fadeIn(spring()),
                        exit = fadeOut(spring()),
                    ) {
                        Column {
                            Text(
                                "¿QUIÉN ES LA PERSONA INVITADA?",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 1.2.sp,
                            )
                            Spacer(Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                eligibleMembers.forEach { member ->
                                    FilterChip(
                                        selected = member.id == inviteMemberId,
                                        onClick = {
                                            inviteMemberId =
                                                if (inviteMemberId == member.id) null else member.id
                                        },
                                        // youLabel: aunque el propio member ya no aparece
                                        // aquí, el helper mantiene el criterio uniforme.
                                        label = { Text(youLabel(member.displayName, member.id, sessionId)) },
                                    )
                                }
                            }
                            // Rol derivado del member elegido, visible ANTES de generar.
                            AnimatedVisibility(
                                visible = selectedMember != null,
                                enter = fadeIn(spring()),
                                exit = fadeOut(spring()),
                            ) {
                                val derived = selectedMember?.let { viewModel.deriveInviteRole(it) }
                                Text(
                                    if (derived == MembershipRepository.ROLE_PAYER)
                                        "Se invitará como Administrador: registra gastos directo en el presupuesto."
                                    else
                                        "Se invitará como Colaborador: propone gastos que tú confirmas.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 10.dp),
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }

                if (state.inviteCode != null && canInvite) {
                    Text(
                        state.inviteCode!!,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(14.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { inviteMemberId?.let { viewModel.generateInvite(it) } },
                            enabled = !state.busy && inviteMemberId != null,
                            modifier = Modifier.weight(1f),
                        ) { Text("Otro código") }
                        Button(
                            onClick = {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "Únete a mi presupuesto familiar con este código: ${state.inviteCode}")
                                }
                                context.startActivity(Intent.createChooser(send, "Compartir código"))
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Share, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Compartir")
                        }
                    }
                } else {
                    Button(
                        onClick = { inviteMemberId?.let { viewModel.generateInvite(it) } },
                        enabled = !state.busy && canInvite && inviteMemberId != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.busy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                when {
                                    !canInvite -> "Crea un grupo primero"
                                    inviteMemberId == null -> "Elige a la persona invitada"
                                    else -> "Generar código"
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}

/**
 * Etiqueta en español del rol de pertenencia (vocabulario roles v2, con
 * normalización de legacy): OWNER → "Dueño", PAYER → "Administrador",
 * MEMBER/COLLABORATOR/desconocido → "Colaborador".
 */
private fun roleLabel(role: String): String =
    when (MembershipRepository.normalizeRole(role)) {
        MembershipRepository.ROLE_OWNER -> "Dueño"
        MembershipRepository.ROLE_PAYER -> "Administrador"
        else -> "Colaborador"
    }

/** Tarjeta con etiqueta de sección (mismo lenguaje visual que Perfil). */
@Composable
private fun SectionCard(label: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(22.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.6.sp
        )
        Spacer(Modifier.height(14.dp))
        content()
    }
}
