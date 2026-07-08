package mx.budget.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    onOpenHousehold: (() -> Unit)? = null,
    onManageMembers: (() -> Unit)? = null,
    onManageCategories: (() -> Unit)? = null,
    onManageIncome: (() -> Unit)? = null,
    onManageWallets: (() -> Unit)? = null,
    nvidiaApiKey: String = "",
    onNvidiaApiKeyChange: (String) -> Unit = {},
    onImportStatement: (() -> Unit)? = null,
    onShowTutorial: (() -> Unit)? = null,
) {
    var showLeadDialog by remember { mutableStateOf(false) }
    var showLocationDialog by remember { mutableStateOf(false) }
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
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
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
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        // Card de ayuda: relanzar el tutorial guiado (coach-marks). Ver TUTORIAL.md.
        if (onShowTutorial != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(22.dp)
            ) {
                Text(
                    "AYUDA",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.6.sp
                )
                Spacer(Modifier.height(14.dp))
                SettingRow(
                    icon = Icons.Filled.School,
                    title = "Ver tutorial",
                    subtitle = "Recorre las secciones de la app paso a paso",
                    trailingBadge = null,
                    onClick = onShowTutorial
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        // Card de cuenta y grupos (Fase B — multi-tenant).
        if (onOpenHousehold != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(22.dp)
            ) {
                Text(
                    "CUENTA Y GRUPOS",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.6.sp
                )
                Spacer(Modifier.height(14.dp))
                SettingRow(
                    icon = Icons.Filled.Group,
                    title = "Compartir el hogar",
                    subtitle = "Inicia sesión con Google, crea o únete a un grupo y comparte tu presupuesto",
                    trailingBadge = null,
                    onClick = onOpenHousehold
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        // Card de administración de maestros (paquete B2): miembros, categorías,
        // ingresos y cuentas. Cada fila abre su pantalla CRUD.
        if (onManageMembers != null || onManageCategories != null || onManageIncome != null || onManageWallets != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(22.dp)
            ) {
                Text(
                    "ADMINISTRAR",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.6.sp
                )
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
            Spacer(Modifier.height(20.dp))
        }

        // Card de apariencia
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(22.dp)
        ) {
            Text(
                "APARIENCIA",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.6.sp
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onDynamicColorChange(!dynamicColor) }
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
                        if (dynamicColor) "Material You — toma la paleta de tu fondo de pantalla"
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

        Spacer(Modifier.height(20.dp))

        // Card de inteligencia / normalización (Feature B)
        var renormalized by remember { mutableStateOf(false) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(22.dp)
        ) {
            Text(
                "INTELIGENCIA",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.6.sp
            )
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

        Spacer(Modifier.height(20.dp))

        // Card de automatización — captura desde notificaciones bancarias (Feature D)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(22.dp)
        ) {
            Text(
                "AUTOMATIZACIÓN",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.6.sp
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onBankCaptureToggle(!bankCaptureEnabled) }
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

        Spacer(Modifier.height(20.dp))

        // Card de importación de estados de cuenta (Fase C, paquete C1). API key de
        // NVIDIA (guardada en DataStore privado) + entrada a la pantalla de import.
        if (onImportStatement != null) {
            var apiKeyDraft by remember(nvidiaApiKey) { mutableStateOf(nvidiaApiKey) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(22.dp)
            ) {
                Text(
                    "ESTADOS DE CUENTA",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.6.sp
                )
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
                SettingRow(
                    icon = Icons.Filled.UploadFile,
                    title = "Importar estado de cuenta",
                    subtitle = "Sube un PDF o imagen: reconcilia corte, límite y MSI, y reescribe los movimientos de la tarjeta",
                    trailingBadge = null,
                    onClick = onImportStatement
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        // Card de recordatorios (Fase 4 inc. 2d) — lead global de los avisos de PLANNED.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(22.dp)
        ) {
            Text(
                "RECORDATORIOS",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.6.sp
            )
            Spacer(Modifier.height(14.dp))
            SettingRow(
                icon = Icons.Filled.Notifications,
                title = "Antelación de recordatorios",
                subtitle = "Avisar ${reminderLeadLabel(reminderLeadDays)} de cada pago planeado",
                trailingBadge = null,
                onClick = { showLeadDialog = true }
            )
        }

        Spacer(Modifier.height(20.dp))

        // Card de espejo a Google Calendar (Fase 6) — opt-in, una vía.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(22.dp)
        ) {
            Text(
                "CALENDARIO",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.6.sp
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onCalendarMirrorToggle(!calendarMirrorEnabled) }
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

        Spacer(Modifier.height(20.dp))

        // Card de ubicación del gasto (Apéndice G.4) — opt-in por nivel.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(22.dp)
        ) {
            Text(
                "UBICACIÓN",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.6.sp
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { showLocationDialog = true }
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

        Spacer(Modifier.height(20.dp))
        Text(
            "Los colores de ingreso, gasto y alerta permanecen estables en ambos modos.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

/** Subtítulo legible del nivel de ubicación elegido. */
private fun locationLevelLabel(level: String): String = when (level) {
    "WHILE_IN_USE" -> "Solo al usar — fija el lugar al capturar o confirmar en la app"
    "PERSISTENT" -> "Persistente — también en segundo plano (banco/reloj)"
    else -> "Desactivada — los gastos no guardan ubicación"
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

/** Fila de ajuste con icono, título, subtítulo y chevron (o badge numérico). */
@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    trailingBadge: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
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
        } else {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp)
            )
        }
    }
}
