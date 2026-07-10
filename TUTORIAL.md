# TUTORIAL.md — Tutorial guiado (coach-marks / spotlight)

Guía para humanos y **agentes de IA** que mantengan el tutorial de uso de la app. Si tocas la
UI de una sección que el tour resalta, **actualiza también el tutorial** (ver la regla al final).

## Qué es

Un recorrido visual que se **sobrepone a la UI real**: oscurece la pantalla, **ilumina el
elemento vivo** (spotlight recortado) y muestra un globo explicativo con **Atrás / Siguiente /
Saltar**. **Navega solo** por todas las secciones (Dashboard → Captura → Calendario → Cuentas →
Analíticas → Libro Mayor).

- **Primera vez:** arranca automáticamente al llegar al Dashboard (flag `has_seen_tutorial` en
  DataStore, independiente del onboarding de alta de datos `needsOnboarding`). En instalaciones
  sembradas (p. ej. la de Norma) también arranca, porque el flag empieza en `false`.
- **Relanzar:** Perfil → sección **AYUDA → "Ver tutorial"**. No resetea el flag.
- Al terminar o **Saltar** se marca `has_seen_tutorial = true`.

## Arquitectura (paquete `app/src/main/java/mx/budget/ui/tutorial/`)

| Archivo | Rol |
|---|---|
| `TutorialKey.kt` | `enum TutorialKey` — **fuente única** de identidad de cada sección resaltable. |
| `TutorialSpec.kt` | `TutorialStep` + `TutorialSpec.steps` (lista ordenada = **orden del tour**). |
| `TutorialController.kt` | Estado (`isRunning`, `index`), registro de bounds, `start/next/prev/skip`. |
| `TutorialTarget.kt` | `Modifier.tutorialTarget(key, controller?, scrollTo?)` — registra bounds; no-op si controller es null. |
| `TutorialOverlay.kt` | Canvas scrim + recorte del spotlight + globo + orquestación (navegar / abrir hoja). |

### Modelo de **dos overlays** (importante)
La pantalla de **Captura es un `ModalBottomSheet`** → vive en **otra ventana** (Popup). Un Canvas
pintado en la ventana principal no puede dibujar sobre la hoja y `boundsInWindow()` medido dentro
de la hoja es relativo a **su** ventana. Por eso hay dos instancias de `TutorialOverlay` que
comparten el **mismo** `TutorialController`:

1. **Principal** (`orchestrate = true`) — envuelve `MainShell { NavHost }` en `BudgetNavGraph`.
   Cubre todas las pantallas normales y es quien **navega** entre rutas y **abre/cierra** la hoja.
   Filtro: `{ !it.requiresCaptureSheet }`.
2. **Dentro de la hoja** (`orchestrate = false`) — hijo del contenido del `CaptureBottomSheet`,
   con `Modifier.matchParentSize()`. Dibuja el spotlight de los pasos de captura en el espacio de
   coordenadas de la propia hoja. Filtro: `{ it.requiresCaptureSheet }`.

Cada overlay convierte bounds-en-ventana → locales restando el `positionInWindow()` de su propia
raíz, así que cada uno queda alineado dentro de su ventana.

### Trigger y cableado
- `data/settings/SettingsRepository.kt` — `has_seen_tutorial` (key + Flow + setter).
- `BudgetApplication.kt` — `initialHasSeenTutorial` (lectura síncrona al arrancar).
- `MainActivity.kt` — colecta el flag y pasa `startTutorial = !hasSeenTutorial` + `onTutorialSeen`.
- `BudgetNavGraph.kt` — `remember { TutorialController(...) }`, overlay principal, `LaunchedEffect`
  de primera vez (latch cuando `currentRoute == DASHBOARD`, `start(firstRun = true)`), señal
  `tutorialCaptureOpen` hacia el Dashboard, `onShowTutorial = { controller.start(firstRun = false) }`
  a `ProfileScreen`, y los dos `AlertDialog` (aviso/invitación, ver abajo).
- `ui/dashboard/DashboardScreen.kt` — `LaunchedEffect(tutorialCaptureOpen)` **abre** la hoja
  (`onOpenCapture`); el **cierre** lo hace `BudgetNavGraph` en `onRequestCloseCapture`
  (`captureMode = null`, gated a `tutorialController.isRunning` para no descartar una hoja
  abierta a mano fuera del tour).

### Datos de demostración (modo demo — solo en pantalla, cero DB)
Mientras el tour corre (`controller.demoActive == true`), las pantallas sustituyen su estado real
por un dataset canned de `ui/tutorial/TutorialDemoData.kt`, **sin tocar Room ni el sync**. Al
terminar el tour el flag baja y todo vuelve a los flujos reales — cero residuos. El patrón de
inyección es uniforme: tras cada `collectAsState()`, `val x = if (demoActive) TutorialDemoData.x else rawX`.
- **Dashboard** (`DashboardScreen.kt`): `uiState`, `proactiveSuggestions`, `bankCaptures`,
  `members`, `singleMember=false`.
- **Captura** (`CaptureBottomSheet.kt`): `members` y `recentCategories` (para que atribución y
  categoría tengan contenido).
- **Calendario** (`CalendarScreen.kt`): `planned` (3 pagos con fechas futuras → marcan días).
- **Libro Mayor** (`LedgerScreen.kt`): `rows` (4 movimientos).
- **Analíticas** (`AnalyticsScreen.kt`): `quincena`, `spendByCategory`, `postedIncome`,
  `topConcepts`, KPIs; los secundarios (trend/deuda/interés) se dejan reales y degradan a hints.
- **Cuentas** — sin demo (los wallets sembrados ya existen; son estructurales, no scoped a quincena).
`TutorialDemoData` usa tipos exactos (`DashboardUiState.Success`, `ExpenseWithDetails` denormalizado,
`SpendByMember`, `SpendByCategory`, `TopConcept`, `MemberEntity`, `CategoryEntity`, `QuincenaEntity`,
`ProactiveSuggestion`, `PendingCaptureEntity`) con datos coherentes entre pantallas.

### Aviso (2ª vez) e invitación (1ª vez)
`TutorialController.start(firstRun)` distingue:
- **1ª vez** (auto, `firstRun=true`): arranca directo con demo, sin aviso; al terminar/saltar,
  `pendingInvitation=true` → `AlertDialog` "¡Listo para empezar!" invitando a la Wallet real.
- **Relanzado desde Perfil** (`firstRun=false`): `pendingWarning=true` → `AlertDialog` "Datos de
  demostración" (Entendido → `confirmWarning()` arranca; Cancelar → `dismissWarning()`); sin
  invitación final. Los diálogos se renderizan en `BudgetNavGraph` gated por `pendingWarning`/`pendingInvitation`.

### Auto-scroll del spotlight
`Modifier.tutorialTarget` adjunta un `BringIntoViewRequester` y registra por defecto
`scrollTo = { requester.bringIntoView() }`. El overlay lo invoca antes de resaltar, así los targets
bajo el pliegue (categoría/atribución en la hoja de captura, filas de calendario/ledger) se traen a
la vista automáticamente. No-op sobre elementos ya visibles; funciona con `verticalScroll` y `LazyColumn`.

### Paridad de layouts (Fold vs compacto)
`DASH_MEMBER_BARS` (Beneficiario/Pagador) existe en **ambos** layouts: `MainHealthPane` (expandido) y,
desde esta iteración, un `item` en `CompactDashboard` (`DashboardScreen.kt`). El resto de arreglos
(demo, auto-scroll) intercepta en el punto común (`collectAsState`), así que aplica a Fold y no-Fold
por igual.

### Contrato de robustez (degradación)
- Un `TutorialStep` cuya `key` **no está registrada** en pantalla (sección renombrada/borrada, o
  target reciclado fuera de vista) → tras ~0.8 s el overlay muestra un **globo centrado sin
  spotlight**. **Nunca crashea ni bloquea** el tour.
- Un tag que use una `TutorialKey` inexistente **no compila** (el enum es la fuente única).
- En debug, `TutorialController.finish()` loguea (`Log.w("Tutorial", ...)`) las claves de
  `TutorialSpec` que nunca resolvieron bounds — pista de que una sección cambió sin actualizar el tour.

## Tabla de mapeo autoritativa

Cada paso del tour ↔ su `TutorialKey` ↔ la pantalla (ruta) ↔ el composable objetivo ↔ archivo.
Los tags llevan el comentario `// TUTORIAL: <KEY> — ver TUTORIAL.md` en el código (grep-able).

| TutorialKey | Pantalla (route) | Composable objetivo | Archivo |
|---|---|---|---|
| `DASH_HERO_KPI` | dashboard | `CollapsedHealthCard` (compacto) / `HeroKpi` (expandido) | `ui/dashboard/DashboardScreen.kt` |
| `DASH_MEMBER_BARS` | dashboard | `MemberDistributionSection` (solo expandido) | `ui/dashboard/DashboardScreen.kt` |
| `DASH_SUGGESTIONS` | dashboard | `SuggestionsSection` | `ui/dashboard/DashboardScreen.kt` |
| `DASH_ACTION_BAR` | dashboard | botón "+" (`CircleActionButton` del `FloatingNavBar` en compacto / FAB del `NavigationRailCustom` en expandido) | `ui/navigation/FloatingNavBar.kt` + `ui/dashboard/DashboardScreen.kt` |
| `DASH_NAV` | dashboard | `NavigationRailCustom` / `FloatingNavBar` | `ui/navigation/MainShell.kt` |
| `CAP_KIND_TOGGLE` | dashboard (hoja) | `CaptureHeader` (toggle Gasto/Ingreso) | `ui/capture/CaptureBottomSheet.kt` |
| `CAP_AMOUNT_KEYPAD` | dashboard (hoja) | `AmountCard` | `ui/capture/CaptureBottomSheet.kt` |
| `CAP_CATEGORY` | dashboard (hoja) | `CategoryCard` | `ui/capture/CaptureBottomSheet.kt` |
| `CAP_ATTRIBUTION` | dashboard (hoja) | `BeneficiaryCard` | `ui/capture/CaptureBottomSheet.kt` |
| `CAL_MONTH_GRID` | calendar | rejilla del mes | `ui/calendar/CalendarScreen.kt` |
| `CAL_PLANNED` | calendar | `PlannedCard` / sección de planeados | `ui/calendar/CalendarScreen.kt` |
| `CAL_FAB` | calendar | FAB "Nuevo pago planeado" | `ui/calendar/CalendarScreen.kt` |
| `WAL_HEADER` | wallets | botones de ingresos/transferencias | `ui/wallets/WalletsScreen.kt` |
| `WAL_LIST` | wallets | **primera** `WalletCard` de la lista (globo forzado `Below` para no tapar el título) | `ui/wallets/WalletsScreen.kt` |
| `WAL_FAB` | wallets | FAB "Nueva cuenta" | `ui/wallets/WalletsScreen.kt` |
| `ANA_SUMMARY` | analytics | `SmartSummaryCard` | `ui/analytics/AnalyticsScreen.kt` |
| `ANA_KPI_ROW` | analytics | fila de KPIs (Ahorro/Por cobrar/MSI) | `ui/analytics/AnalyticsScreen.kt` |
| `ANA_WIDGETS` | analytics | primer `WidgetCard` (gráficas) | `ui/analytics/AnalyticsScreen.kt` |
| `ANA_ASK_FAB` | analytics | FAB "Preguntar" — lo usan DOS pasos: asistente y "Atajos que aprenden" (pills dinámicos) | `ui/analytics/AnalyticsScreen.kt` |
| `PROFILE_STATEMENTS` | profile | `SettingRow` "Importar estado de cuenta" (con tag; el auto-scroll del target baja hasta la fila) | `ui/profile/ProfileScreen.kt` |
| `ANA_LEDGER_ENTRY` | analytics | `IconButton` "Libro Mayor" del header — paso de transición: va DESPUÉS de Perfil y ANTES del bloque LED_* (orden del guion) | `ui/analytics/AnalyticsScreen.kt` |
| `LED_FILTERS` | ledger | fila de FilterChips | `ui/ledger/LedgerScreen.kt` |
| `LED_ROWS` | ledger | **primera** `LedgerRow` de la lista (patrón `index == 0`) | `ui/ledger/LedgerScreen.kt` |

> Nota: `DASH_MEMBER_BARS` solo existe en el layout expandido (Fold interno). En compacto degrada
> a globo centrado (comportamiento intencional).

## 🔧 REGLA DE MANTENIMIENTO (léela antes de tocar la UI)

Cuando **añadas, renombres o borres** una sección de la app que el tutorial resalta:

1. **Enum** — actualiza `TutorialKey` (`TutorialKey.kt`). Es la fuente única; renombrar aquí
   fuerza a arreglar todos los usos (no compila si no).
2. **Tag** — actualiza el `Modifier.tutorialTarget(TutorialKey.X, tutorialController)` (busca el
   comentario `// TUTORIAL: X`). Si la sección se movió a otro composable, mueve el tag.
3. **Guion** — actualiza/reordena la entrada en `TutorialSpec.steps` (`TutorialSpec.kt`): título,
   cuerpo, `route`, `requiresCaptureSheet`. El orden de la lista es el orden del tour.
4. **Esta tabla** — mantén sincronizada la fila correspondiente.

Para **añadir** una sección nueva al tour: (a) agrega una `TutorialKey`, (b) tag el composable con
`Modifier.tutorialTarget(...)` (hilando `tutorialController` como parámetro de la pantalla, tal
como ya lo hacen las pantallas existentes; el modifier es no-op si el controller es null), (c)
agrega un `TutorialStep` en el lugar del guion donde deba aparecer, (d) añade la fila a esta tabla.

Si borras una sección y olvidas el paso, el tour **no se rompe** (globo centrado) — pero el log de
debug lo delatará. Aun así, mantener esta tabla al día es la fuente de verdad para el mantenimiento.
