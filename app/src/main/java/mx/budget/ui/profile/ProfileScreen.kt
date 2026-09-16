package mx.budget.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NoAccounts
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.budget.ui.tutorial.tutorialTarget
import mx.budget.R
import mx.budget.ui.common.LocalReducedMotion
import mx.budget.ui.theme.BudgetMotion
import mx.budget.ui.theme.financeColors

/**
 * Estado de la sesión tal como Perfil lo cuenta.
 *
 * @param linked la sesión está vinculada a una cuenta de Google. En anónimo, una
 *  reinstalación acuña un uid nuevo y con él se pierde la propiedad del hogar:
 *  el push queda en PERMISSION_DENIED y hace falta recuperarlo a mano.
 * @param email correo de la cuenta vinculada, si lo hay.
 * @param roleLabel rol propio en el hogar activo, ya en español, o null si la
 *  nube todavía no conoce a este usuario.
 * @param householdName nombre del hogar activo según la nube.
 */
data class IdentityStatus(
    val linked: Boolean,
    val email: String? = null,
    val displayName: String? = null,
    val roleLabel: String? = null,
    val householdName: String? = null,
)

/**
 * Pantalla de Perfil / Ajustes.
 *
 * Por ahora aloja el toggle de **color dinámico (Material You)** persistido
 * (brief §2.1). Más ajustes (miembros, quincena, exportar) se añadirán aquí.
 */
@Composable
fun ProfileScreen(
    dynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    pendingReviewCount: Int = 0,
    onOpenReview: () -> Unit = {},
    onRenormalize: () -> Unit = {},
    bankCaptureEnabled: Boolean = false,
    onBankCaptureToggle: (Boolean) -> Unit = {},
    onGrantNotificationAccess: () -> Unit = {},
    reminderLeadDays: Int = 2,
    onReminderLeadChange: (Int) -> Unit = {},
    calendarMirrorEnabled: Boolean = false,
    onCalendarMirrorToggle: (Boolean) -> Unit = {},
    locationLevel: String = "NONE",
    onLocationLevelChange: (String) -> Unit = {},
    onOpenQuincenas: (() -> Unit)? = null,
    onOpenHousehold: (() -> Unit)? = null,
    /**
     * Estado de identidad de la sesión. `null` cuando la pantalla se compone sin
     * el ViewModel de grupos (por ejemplo en una vista previa).
     */
    identity: IdentityStatus? = null,
    onLinkGoogle: (() -> Unit)? = null,
    onManageMembers: (() -> Unit)? = null,
    onManageCategories: (() -> Unit)? = null,
    onManageIncome: (() -> Unit)? = null,
    onManageWallets: (() -> Unit)? = null,
    /**
     * Gobierno del modelo local (Fase 4). `null` cuando la pantalla se compone sin
     * la capa de IA (vista previa), y entonces la tarjeta no aparece.
     */
    aiAssistant: AiAssistantSettings? = null,
    nvidiaApiKey: String = "",
    onNvidiaApiKeyChange: (String) -> Unit = {},
    onImportStatement: (() -> Unit)? = null,
    /** Exportar y respaldar (Fase 5). `null` oculta la sección. */
    exportViewModel: ExportViewModel? = null,
    onRestoreBackup: ((mx.budget.data.backup.BackupInspection) -> Unit)? = null,
    onShowTutorial: (() -> Unit)? = null,
    tutorialController: mx.budget.ui.tutorial.TutorialController? = null,
) {
    var showLeadDialog by remember { mutableStateOf(false) }
    var showLocationDialog by remember { mutableStateOf(false) }
    // Fase 6b (brief 2): las doce secciones se reparten en cuatro grupos
    // plegables y solo uno queda abierto a la vez. Con todo cerrado la lista
    // entera cabe en una pantalla y cualquier ajuste queda a dos toques; antes
    // llegar al último exigía siete gestos de desplazamiento. Fuera de los
    // grupos solo quedan la ayuda y el estado de la sesión, porque el aviso de
    // sesión anónima tiene que verse sin abrir nada.
    var grupoAbierto by rememberSaveable { mutableStateOf<String?>(null) }
    val alternar: (String) -> Unit = { id -> grupoAbierto = if (grupoAbierto == id) null else id }
    // El tutorial alumbra la fila de estados de cuenta, que ahora vive dentro de
    // un grupo: si el grupo está cerrado, el paso no tendría nada que alumbrar.
    // Ver TUTORIAL.md.
    val pasoDelTutorial = tutorialController?.currentStep?.key
    LaunchedEffect(pasoDelTutorial) {
        if (pasoDelTutorial == mx.budget.ui.tutorial.TutorialKey.PROFILE_STATEMENTS) {
            grupoAbierto = GRUPO_MES
        }
    }
    if (showLocationDialog) {
        LocationLevelDialog(
            current = locationLevel,
            onSelect = { level -> onLocationLevelChange(level); showLocationDialog = false },
            onDismiss = { showLocationDialog = false },
        )
    }
    if (showLeadDialog) {
        ReminderLeadDialog(
            current = reminderLeadDays,
            onSelect = { days -> onReminderLeadChange(days); showLeadDialog = false },
            onDismiss = { showLeadDialog = false },
        )
    }
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
                    .clickable(role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    stringResource(R.string.cd_back),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    "AJUSTES",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.6.sp
                )
                Text(
                    "Perfil",
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light, fontSize = 30.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        // Card de ayuda: relanzar el tutorial guiado (coach-marks). Ver TUTORIAL.md.
        if (onShowTutorial != null) {
            AyudaCard(onShowTutorial)
            Spacer(Modifier.height(20.dp))
        }

        // Estado de identidad (Fase 2). Antes esta pantalla no decía nada de la
        // sesión: quién eres, qué puedes hacer, ni que seguir en anónimo pone en
        // riesgo la propiedad del hogar si algún día reinstalas.
        if (identity != null) {
            IdentityCard(identity = identity, onLinkGoogle = onLinkGoogle, onOpenHousehold = onOpenHousehold)
            Spacer(Modifier.height(20.dp))
        }

        // Lo que se usa cada mes va primero y por separado de lo que se toca una
        // vez y se olvida.
        if (onOpenQuincenas != null || onImportStatement != null ||
            (exportViewModel != null && onRestoreBackup != null)
        ) {
            ProfileGroup(
                titulo = "Este mes",
                resumen = "Quincenas, estados de cuenta, exportar y respaldar",
                icono = Icons.Filled.EventAvailable,
                abierto = grupoAbierto == GRUPO_MES,
                onToggle = { alternar(GRUPO_MES) },
            ) {
                if (onOpenQuincenas != null) QuincenasCard(onOpenQuincenas)
                if (onImportStatement != null) {
                    EstadosDeCuentaCard(
                        nvidiaApiKey = nvidiaApiKey,
                        onNvidiaApiKeyChange = onNvidiaApiKeyChange,
                        onImportStatement = onImportStatement,
                        tutorialController = tutorialController,
                    )
                }
                if (exportViewModel != null && onRestoreBackup != null) {
                    ExportSection(viewModel = exportViewModel, onRestore = onRestoreBackup)
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (onOpenHousehold != null || onManageMembers != null || onManageCategories != null ||
            onManageIncome != null || onManageWallets != null
        ) {
            ProfileGroup(
                titulo = "El hogar",
                resumen = "Grupo, miembros, categorías, ingresos y cuentas",
                icono = Icons.Filled.Group,
                abierto = grupoAbierto == GRUPO_HOGAR,
                onToggle = { alternar(GRUPO_HOGAR) },
            ) {
                if (onOpenHousehold != null) CuentaYGruposCard(onOpenHousehold)
                if (onManageMembers != null || onManageCategories != null ||
                    onManageIncome != null || onManageWallets != null
                ) {
                    AdministrarCard(
                        onManageMembers = onManageMembers,
                        onManageCategories = onManageCategories,
                        onManageIncome = onManageIncome,
                        onManageWallets = onManageWallets,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        ProfileGroup(
            titulo = "Apariencia e inteligencia",
            resumen = "Color de la app, revisión de atribuciones y asistente",
            icono = Icons.Filled.Palette,
            abierto = grupoAbierto == GRUPO_APARIENCIA,
            onToggle = { alternar(GRUPO_APARIENCIA) },
        ) {
            AparienciaCard(dynamicColor = dynamicColor, onDynamicColorChange = onDynamicColorChange)
            InteligenciaCard(
                pendingReviewCount = pendingReviewCount,
                onOpenReview = onOpenReview,
                onRenormalize = onRenormalize,
            )
            if (aiAssistant != null) AiAssistantCard(aiAssistant)
        }

        Spacer(Modifier.height(16.dp))

        ProfileGroup(
            titulo = "Automatización y avisos",
            resumen = "Notificaciones del banco, recordatorios, calendario y ubicación",
            icono = Icons.Filled.Notifications,
            abierto = grupoAbierto == GRUPO_AUTOMATIZACION,
            onToggle = { alternar(GRUPO_AUTOMATIZACION) },
        ) {
            AutomatizacionCard(
                bankCaptureEnabled = bankCaptureEnabled,
                onBankCaptureToggle = onBankCaptureToggle,
                onGrantNotificationAccess = onGrantNotificationAccess,
            )
            RecordatoriosCard(reminderLeadDays = reminderLeadDays, onPickLead = { showLeadDialog = true })
            CalendarioCard(
                calendarMirrorEnabled = calendarMirrorEnabled,
                onCalendarMirrorToggle = onCalendarMirrorToggle,
            )
            UbicacionCard(locationLevel = locationLevel, onPickLevel = { showLocationDialog = true })
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "Los colores de ingreso, gasto y alerta permanecen estables en ambos modos.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Grupos plegables (Fase 6b, brief 2)
// ─────────────────────────────────────────────────────────────────────────────

private const val GRUPO_MES = "mes"
private const val GRUPO_HOGAR = "hogar"
private const val GRUPO_APARIENCIA = "apariencia"
private const val GRUPO_AUTOMATIZACION = "automatizacion"

/**
 * Cabecera plegable con las tarjetas del grupo dentro.
 *
 * La cabecera es un encabezado de verdad para TalkBack (`heading()`), dice si
 * está abierta o cerrada (`stateDescription`) y anuncia lo que pasa al
 * activarla, así que la navegación por encabezados salta entre los cuatro
 * grupos en vez de recorrer doce tarjetas.
 */
@Composable
private fun ProfileGroup(
    titulo: String,
    resumen: String,
    icono: androidx.compose.ui.graphics.vector.ImageVector,
    abierto: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reducedMotion = LocalReducedMotion.current
    val giro by animateFloatAsState(
        targetValue = if (abierto) 180f else 0f,
        animationSpec = if (reducedMotion) snap() else BudgetMotion.standard(),
        label = "giroDelGrupo",
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    if (abierto) MaterialTheme.colorScheme.surfaceContainerHigh
                    else MaterialTheme.colorScheme.surfaceContainer
                )
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (abierto) "Cerrar el grupo" else "Abrir el grupo",
                    onClick = onToggle,
                )
                .semantics {
                    heading()
                    stateDescription = if (abierto) "Abierto" else "Cerrado"
                }
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icono,
                    null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    titulo,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    resumen,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Icon(
                Icons.Filled.ExpandMore,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(22.dp)
                    .rotate(giro),
            )
        }
        AnimatedVisibility(
            visible = abierto,
            enter = if (reducedMotion) fadeIn(animationSpec = snap())
            else expandVertically(animationSpec = BudgetMotion.standard()) +
                fadeIn(animationSpec = BudgetMotion.standard()),
            exit = if (reducedMotion) fadeOut(animationSpec = snap())
            else shrinkVertically(animationSpec = BudgetMotion.standard()) +
                fadeOut(animationSpec = BudgetMotion.standard()),
        ) {
            Column(
                modifier = Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

/** Contenedor común de las tarjetas de ajustes. */
@Composable
private fun AjustesCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(22.dp),
        content = content,
    )
}

/** Rótulo de sección, marcado como encabezado para la navegación por voz. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.6.sp,
        modifier = Modifier.semantics { heading() },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Tarjetas del Perfil
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AyudaCard(onShowTutorial: () -> Unit) {
    AjustesCard {
        SectionLabel("AYUDA")
        Spacer(Modifier.height(14.dp))
        SettingRow(
            icon = Icons.Filled.School,
            title = "Ver tutorial",
            subtitle = "Recorre las secciones de la app paso a paso",
            trailingBadge = null,
            onClick = onShowTutorial
        )
    }
}

@Composable
private fun CuentaYGruposCard(onOpenHousehold: () -> Unit) {
    AjustesCard {
        SectionLabel("CUENTA Y GRUPOS")
        Spacer(Modifier.height(14.dp))
        SettingRow(
            icon = Icons.Filled.Group,
            title = "Compartir el hogar",
            subtitle = "Inicia sesión con Google, crea o únete a un grupo y comparte tu presupuesto",
            trailingBadge = null,
            onClick = onOpenHousehold
        )
    }
}

@Composable
private fun QuincenasCard(onOpenQuincenas: () -> Unit) {
    // Sin rótulo de sección: la tarjeta tiene una sola fila y el grupo que la
    // contiene ya dice de qué va.
    AjustesCard {
        SettingRow(
            icon = Icons.Filled.EventAvailable,
            title = "Quincenas",
            subtitle = "Cerrar el periodo, reabrirlo y ver su resumen",
            trailingBadge = null,
            onClick = onOpenQuincenas
        )
    }
}

@Composable
private fun AdministrarCard(
    onManageMembers: (() -> Unit)?,
    onManageCategories: (() -> Unit)?,
    onManageIncome: (() -> Unit)?,
    onManageWallets: (() -> Unit)?,
) {
    AjustesCard {
        SectionLabel("ADMINISTRAR")
        Spacer(Modifier.height(14.dp))
        if (onManageMembers != null) {
            SettingRow(
                icon = Icons.Filled.Group,
                title = "Miembros",
                subtitle = "Personas del hogar y sus roles",
                trailingBadge = null,
                onClick = onManageMembers
            )
        }
        if (onManageCategories != null) {
            Spacer(Modifier.height(8.dp))
            SettingRow(
                icon = Icons.Filled.Category,
                title = "Categorías",
                subtitle = "Grupos, colores y presupuestos",
                trailingBadge = null,
                onClick = onManageCategories
            )
        }
        if (onManageIncome != null) {
            Spacer(Modifier.height(8.dp))
            SettingRow(
                icon = Icons.Filled.AttachMoney,
                title = "Ingresos",
                subtitle = "Fuentes de ingreso de la quincena",
                trailingBadge = null,
                onClick = onManageIncome
            )
        }
        if (onManageWallets != null) {
            Spacer(Modifier.height(8.dp))
            SettingRow(
                icon = Icons.Filled.AccountBalanceWallet,
                title = "Cuentas",
                subtitle = "Saldos, tarjetas y efectivo",
                trailingBadge = null,
                onClick = onManageWallets
            )
        }
    }
}

@Composable
private fun AparienciaCard(dynamicColor: Boolean, onDynamicColorChange: (Boolean) -> Unit) {
    AjustesCard {
        SectionLabel("APARIENCIA")
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable(role = Role.Switch) { onDynamicColorChange(!dynamicColor) }
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Palette, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Color dinámico",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    if (dynamicColor) "Material You: toma la paleta de tu fondo de pantalla"
                    else "Verde de marca (#016E3E)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = dynamicColor,
                onCheckedChange = onDynamicColorChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
private fun InteligenciaCard(
    pendingReviewCount: Int,
    onOpenReview: () -> Unit,
    onRenormalize: () -> Unit,
) {
    var renormalized by remember { mutableStateOf(false) }
    AjustesCard {
        SectionLabel("INTELIGENCIA")
        Spacer(Modifier.height(14.dp))
        SettingRow(
            icon = Icons.AutoMirrored.Filled.Rule,
            title = "Revisión de atribuciones",
            subtitle = if (pendingReviewCount > 0) "$pendingReviewCount gastos por revisar"
            else "Sin pendientes por ahora",
            trailingBadge = pendingReviewCount.takeIf { it > 0 }?.toString(),
            onClick = onOpenReview
        )
        Spacer(Modifier.height(8.dp))
        SettingRow(
            icon = Icons.Filled.Refresh,
            title = "Re-normalizar historial",
            subtitle = if (renormalized) "En proceso… revisa la cola en unos segundos"
            else "Recalcula conceptos y re-infiere atribuciones",
            trailingBadge = null,
            onClick = {
                onRenormalize()
                renormalized = true
            }
        )
    }
}

@Composable
private fun AutomatizacionCard(
    bankCaptureEnabled: Boolean,
    onBankCaptureToggle: (Boolean) -> Unit,
    onGrantNotificationAccess: () -> Unit,
) {
    AjustesCard {
        SectionLabel("AUTOMATIZACIÓN")
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable(role = Role.Switch) { onBankCaptureToggle(!bankCaptureEnabled) }
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Notifications, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Captura desde notificaciones",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Detecta cargos en notificaciones de tus bancos y propone el gasto (lo confirmas tú). Todo on-device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = bankCaptureEnabled,
                onCheckedChange = onBankCaptureToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }
        if (bankCaptureEnabled) {
            Spacer(Modifier.height(8.dp))
            SettingRow(
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                title = "Conceder acceso a notificaciones",
                subtitle = "Ábrelo en Ajustes del sistema y activa \"Presupuesto Familiar\"",
                trailingBadge = null,
                onClick = onGrantNotificationAccess
            )
        }
    }
}

@Composable
private fun EstadosDeCuentaCard(
    nvidiaApiKey: String,
    onNvidiaApiKeyChange: (String) -> Unit,
    onImportStatement: () -> Unit,
    tutorialController: mx.budget.ui.tutorial.TutorialController?,
) {
    var apiKeyDraft by remember(nvidiaApiKey) { mutableStateOf(nvidiaApiKey) }
    AjustesCard {
        SectionLabel("ESTADOS DE CUENTA")
        Spacer(Modifier.height(6.dp))
        Text(
            "Pega tu API key de NVIDIA para analizar estados de cuenta con IA. " +
                "Se guarda solo en este teléfono. El archivo nunca sale del " +
                "dispositivo: solo el texto extraído se envía a la nube.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        androidx.compose.material3.OutlinedTextField(
            value = apiKeyDraft,
            onValueChange = { apiKeyDraft = it },
            label = { Text("API key de NVIDIA") },
            placeholder = { Text("nvapi-…") },
            singleLine = true,
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
            ),
            trailingIcon = {
                if (apiKeyDraft != nvidiaApiKey) {
                    androidx.compose.material3.TextButton(onClick = { onNvidiaApiKeyChange(apiKeyDraft) }) {
                        Text("Guardar")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            if (nvidiaApiKey.isBlank()) "Sin key configurada"
            else "Key guardada (${nvidiaApiKey.take(6)}…)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )
        Spacer(Modifier.height(12.dp))
        // TUTORIAL: PROFILE_STATEMENTS, ver TUTORIAL.md (el tag trae la fila a la
        // vista con auto-scroll; no-op si controller es null)
        SettingRow(
            icon = Icons.Filled.UploadFile,
            title = "Importar estado de cuenta",
            subtitle = "Sube un PDF o imagen: reconcilia corte, límite y MSI, y reescribe los movimientos de la tarjeta",
            trailingBadge = null,
            onClick = onImportStatement,
            modifier = Modifier.tutorialTarget(
                mx.budget.ui.tutorial.TutorialKey.PROFILE_STATEMENTS,
                tutorialController,
            ),
        )
    }
}

@Composable
private fun RecordatoriosCard(reminderLeadDays: Int, onPickLead: () -> Unit) {
    AjustesCard {
        SectionLabel("RECORDATORIOS")
        Spacer(Modifier.height(14.dp))
        SettingRow(
            icon = Icons.Filled.Notifications,
            title = "Antelación de recordatorios",
            subtitle = "Avisar ${reminderLeadLabel(reminderLeadDays)} de cada pago planeado",
            trailingBadge = null,
            onClick = onPickLead
        )
    }
}

@Composable
private fun CalendarioCard(
    calendarMirrorEnabled: Boolean,
    onCalendarMirrorToggle: (Boolean) -> Unit,
) {
    AjustesCard {
        SectionLabel("CALENDARIO")
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable(role = Role.Switch) { onCalendarMirrorToggle(!calendarMirrorEnabled) }
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Event, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Espejo en Google Calendar",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Copia tus gastos planeados a un calendario propio \"Presupuesto Familiar\". Una sola vía: nunca lee ni toca tus otros calendarios.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = calendarMirrorEnabled,
                onCheckedChange = onCalendarMirrorToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
private fun UbicacionCard(locationLevel: String, onPickLevel: () -> Unit) {
    AjustesCard {
        SectionLabel("UBICACIÓN")
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable(role = Role.Button) { onPickLevel() }
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.LocationOn, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Ubicación del gasto",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    locationLevelLabel(locationLevel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Subtítulo legible del nivel de ubicación elegido. */
private fun locationLevelLabel(level: String): String = when (level) {
    "WHILE_IN_USE" -> "Solo al usar: fija el lugar al capturar o confirmar en la app"
    "PERSISTENT" -> "Persistente: también en segundo plano (banco o reloj)"
    else -> "Desactivada: los gastos no guardan ubicación"
}

/** Diálogo de selección del nivel de captura de ubicación (§G.4.2). */
@Composable
private fun LocationLevelDialog(current: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(
        "NONE" to "Desactivada",
        "WHILE_IN_USE" to "Solo al usar (recomendado)",
        "PERSISTENT" to "Persistente (segundo plano)",
    )
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ubicación del gasto") },
        text = {
            Column {
                Text(
                    "La ubicación ayuda a recordar y categorizar tus gastos. Es opcional y privada (se queda en tu hogar).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp, start = 8.dp, end = 8.dp)
                )
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(value) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.RadioButton(selected = value == current, onClick = { onSelect(value) })
                        Spacer(Modifier.width(8.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

/** Etiqueta legible del lead global (días). */
private fun reminderLeadLabel(days: Int): String = when (days) {
    0 -> "el mismo día"
    1 -> "1 día antes"
    else -> "$days días antes"
}

/** Diálogo de selección del lead global de recordatorios (Fase 4 inc. 2d). */
@Composable
private fun ReminderLeadDialog(current: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Antelación de recordatorios") },
        text = {
            Column {
                listOf(0, 1, 2, 3, 7).forEach { days ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(days) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.RadioButton(selected = days == current, onClick = { onSelect(days) })
                        Spacer(Modifier.width(8.dp))
                        Text(
                            reminderLeadLabel(days).replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

/**
 * Tarjeta de estado de la sesión. Dice tres cosas y ofrece la única acción que
 * importa: cómo estás identificado, qué rol tienes en el hogar activo y, si
 * sigues en anónimo, que reinstalar la app te haría perder la propiedad del hogar.
 *
 * El aviso es persistente a propósito: no se puede descartar, porque el daño solo
 * se manifiesta cuando ya es tarde (uid anónimo nuevo, push en PERMISSION_DENIED,
 * recuperación a mano con `scripts/admin/grant_owner.py`).
 */
@Composable
private fun IdentityCard(
    identity: IdentityStatus,
    onLinkGoogle: (() -> Unit)?,
    onOpenHousehold: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(22.dp)
    ) {
        Text(
            "CUENTA",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.6.sp
        )
        Spacer(Modifier.height(14.dp))
        SettingRow(
            icon = if (identity.linked) Icons.Filled.VerifiedUser else Icons.Filled.NoAccounts,
            title = if (identity.linked) "Sesión vinculada a Google" else "Sesión anónima",
            subtitle = if (identity.linked) {
                identity.email ?: identity.displayName ?: "Cuenta de Google vinculada"
            } else {
                "Vincula tu cuenta de Google para conservar tu hogar"
            },
            trailingBadge = null,
            onClick = if (identity.linked) (onOpenHousehold ?: {}) else (onLinkGoogle ?: onOpenHousehold ?: {}),
        )
        Spacer(Modifier.height(10.dp))
        SettingRow(
            icon = Icons.Filled.Badge,
            title = "Tu rol" + (identity.householdName?.let { " en $it" } ?: ""),
            subtitle = identity.roleLabel
                ?: "Todavía sin rol en la nube: este hogar solo existe en este dispositivo",
            trailingBadge = null,
            onClick = onOpenHousehold ?: {},
        )
        if (!identity.linked) {
            Spacer(Modifier.height(14.dp))
            val warn = MaterialTheme.financeColors.warning
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(warn.copy(alpha = 0.12f))
                    .padding(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = warn,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "Si reinstalas la app, pierdes el hogar",
                        style = MaterialTheme.typography.titleSmall,
                        color = warn,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Una sesión anónima se identifica solo con este dispositivo. " +
                            "Al reinstalar se genera otra identidad y el hogar deja de reconocerte: " +
                            "los cambios dejan de subir a la nube y hay que devolverte el permiso a mano. " +
                            "Vincular Google conserva la misma identidad.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (onLinkGoogle != null || onOpenHousehold != null) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onLinkGoogle ?: onOpenHousehold ?: {}) {
                            Text("Vincular mi cuenta de Google")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Fila de ajuste con icono, título, subtítulo y chevron (o badge numérico).
 *
 * `Role.Button` y alto mínimo de 48 dp por la auditoría de accesibilidad de la
 * Fase 6a: un `clickable` sin rol se anuncia como texto suelto, así que el lector
 * de pantalla no dice que la fila se puede activar.
 */
@Composable
internal fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    trailingBadge: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * `false` para filas que solo informan: sin chevron y sin clic, porque una
     * flecha que no lleva a ningún lado es una promesa falsa.
     */
    navigable: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(18.dp))
            .then(
                if (navigable) {
                    Modifier.clickable(
                        role = androidx.compose.ui.semantics.Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            )
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        if (trailingBadge != null) {
            Text(
                trailingBadge,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            )
        } else if (navigable) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp)
            )
        }
    }
}
