# Especificación de UX, Hardware e Integración Multiplataforma

**Documento complementario a `ESPECIFICACION_PRESUPUESTO_APP.md`**
**Superficie objetivo**: Pixel 9 Pro Fold (Android 15+) · Pixel Watch 3 (Wear OS 5+) · Web (React/Next.js)
**Lenguaje visual**: Material 3 Expressive (MDC-M3E v1.3+)
**Restricción dura**: **cero dependencia de LLMs en runtime**. Toda "inteligencia" se implementa con motores de reglas deterministas, tablas de frecuencias, *nearest-neighbor* sobre histórico y matemática estadística básica.
**Audiencia**: agentes de generación de código (vibe coding) y desarrolladores humanos.

---

## Índice

1. [Principios Transversales de Diseño](#1-principios-transversales-de-diseño)
2. [Paridad Funcional con Excel](#2-paridad-funcional-con-excel)
3. [Mecanismos de Captura Acelerada](#3-mecanismos-de-captura-acelerada)
4. [Ecosistema Wear OS (Pixel Watch 3)](#4-ecosistema-wear-os-pixel-watch-3)
5. [Motor Determinista de Atribución](#5-motor-determinista-de-atribución)
6. [Módulos Analíticos de Alto Nivel (Pixel 9 Pro Fold)](#6-módulos-analíticos-de-alto-nivel-pixel-9-pro-fold)
7. [Apéndice C: Tabla de Permisos y APIs](#apéndice-c-tabla-de-permisos-y-apis)
8. [Apéndice D: Tokens de Diseño M3 Expressive](#apéndice-d-tokens-de-diseño-m3-expressive)

---

## 1. Principios Transversales de Diseño

### 1.1 Stack tecnológico canónico

| Capa | Android (Fold) | Wear OS (Watch 3) | Web |
|---|---|---|---|
| Lenguaje | Kotlin 2.0 + Compose | Kotlin + Compose for Wear | TypeScript 5.4 |
| UI framework | Jetpack Compose 1.7 + Material 3 Expressive | Compose for Wear OS 1.4 + Protolayout 1.2 | React 19 + Tailwind 4 + shadcn/ui (tokens M3) |
| Persistencia | Room 2.7 + SQLite (WAL) | DataStore + Tiles state | IndexedDB (Dexie) + sync al backend |
| Sync | WorkManager + protobuf sobre gRPC | Wear Data Layer API + MessageClient | HTTP/2 + WebSocket |
| Estado | ViewModel + StateFlow + SavedStateHandle | TileService + ComplicationDataSourceService | Zustand + TanStack Query |
| Gráficas | Vico 2.0 (Compose-native) | Canvas + primitives Protolayout | Recharts + D3 |

### 1.2 Principios M3 Expressive aplicados

- **Emphasized motion**: animaciones *spring-based* con `stiffness = Spring.StiffnessMediumLow`, `dampingRatio = 0.75`. Todas las transiciones de panel deben correr a ≥ 120 Hz en el Fold (la pantalla interna soporta 120 Hz LTPO).
- **Shape expressivity**: corners de 28 dp para FAB, 16 dp para cards, morphing de shape entre estados (ver `MaterialShapes.Cookie12`, `MaterialShapes.Clover4Leaf` de M3E).
- **Dynamic color v2**: extracción de paleta desde *home screen wallpaper* del usuario y aplicación de tonal palette con seed `#006D3D` (verde-contable) como fallback.
- **Typography scale expresiva**: `displayLargeEmphasized` (57 sp, peso 700) para KPIs principales, `bodyMediumEmphasized` (14 sp, peso 500) para etiquetas de categoría.
- **Density tiers**: `Density.Comfortable` en móvil plegado, `Density.Compact` en pantalla interna (aprovecha el real estate), `Density.Spacious` en Wear OS.

### 1.3 Arquitectura lógica compartida

```
┌──────────────────────────────────────────────────┐
│              Core Domain (KMP/TS)                 │
│  - Entities (§2 del doc base)                     │
│  - Rule Engine (this doc §5.3)                    │
│  - Recurrence Matcher (this doc §5.4)             │
│  - Quincena State Machine (this doc §2.2)         │
└────────────┬─────────────┬───────────────────────┘
             │             │
     ┌───────▼──────┐  ┌───▼──────────┐
     │  Android App │  │  Wear OS App │
     └──────┬───────┘  └──────┬───────┘
            │                 │
            └───── Data Layer API (protobuf) ─────┐
                                                  │
                                           ┌──────▼───────┐
                                           │ Web Frontend │
                                           └──────────────┘
```

El `Core Domain` se compila como módulo Kotlin Multiplatform (targets: `androidTarget`, `wasmJs`) y se consume desde los tres frontends. La **lógica determinista de atribución y recurrencia vive en este módulo compartido** — esto es crítico: garantiza que la misma entrada produce el mismo output en todas las superficies sin requerir sincronización de modelos entrenados.

### 1.4 Contrato de latencia percibida

| Operación | Target P50 | Target P95 |
|---|---|---|
| Abrir app (cold start, Fold) | 420 ms | 900 ms |
| Abrir app (warm) | 120 ms | 280 ms |
| Registrar gasto (desde tap del FAB hasta confirmación) | 2.1 s | 4.0 s |
| Captura desde overlay Quick Tap | **1.4 s** | 2.5 s |
| Refresh de Tile en Watch | 180 ms | 400 ms |
| Sync bidireccional Watch↔Fold | 800 ms | 2.5 s |
| Query analítica (últimos 6 meses) | 60 ms | 150 ms |

---

## 2. Paridad Funcional con Excel

### 2.1 Mapeo directo Excel → UI

| Artefacto Excel | Equivalente en app | Superficie primaria |
|---|---|---|
| Hoja "Quincena X al Y de MES" | Pantalla `QuincenaScreen` (ruta `/quincena/{id}`) | Fold outer + inner |
| Título `PRESUPUESTO QUINCENAL DEL…` | `TopAppBar.MediumFlexible` con `label` y `supporting_text` con rango fechas | Todas |
| Celdas `E4/E5` (sueldos) | Card `IncomeCard` con `IncomeSource[]` editables inline | Fold, Web |
| Celda `E10` (presupuesto total) | Chip `BudgetChip` en header, color `tertiary` | Todas |
| Celda `K62` (gasto real) | Chip `ActualChip`, color dinámico según varianza | Todas |
| Celda `K66` (diferencia) | `VarianceIndicator` — badge con ▲/▼ y valor absoluto | Todas |
| Filas 12-60 (tablas categóricas) | `CategorySectionList` — cada categoría es un `ExpandableCard` | Fold (accordion), Fold interno (grid 2×5) |
| Columnas N-U (conciliación) | Pantalla `WalletScreen` dedicada | Fold, Web |
| Filas 90-127 (histórico) | Pantalla `HistoryScreen` con `LineChart` | Todas |

### 2.2 Máquina de estados de la Quincena

Cada `Quincena` transita por un DFA explícito. La UI nunca permite transiciones inválidas:

```
                   ┌─────────────────────────────────────────────┐
                   │                                              │
                   ▼                                              │
       ┌──────────────────┐     materialize     ┌──────────────────────┐
       │    PROVISIONED   │────templates────────▶│        ACTIVE        │
       └────────┬─────────┘                      └──────────┬───────────┘
                │                                            │
                │                                            │ user taps
                │                                            │ "cerrar"
                │                                            ▼
                │                                 ┌──────────────────────┐
                │                                 │    CLOSING_REVIEW    │
                │                                 │  (UI de validación)  │
                │                                 └──────────┬───────────┘
                │                                            │
                │                                            │ confirm
                │                                            ▼
                │                                 ┌──────────────────────┐
                └────────── from next ────────────│        CLOSED        │
                                                  │  (immutable + audit) │
                                                  └──────────────────────┘
```

Reglas:
- `PROVISIONED`: la quincena existe pero aún no se generaron gastos planeados. Se crea automáticamente al cerrar la anterior (T‑3 días antes del inicio real).
- `ACTIVE`: estado editable. Sólo puede haber **una quincena activa por household** a la vez.
- `CLOSING_REVIEW`: pantalla de validación que muestra: (a) gastos `PLANNED` no ejecutados, (b) varianza global, (c) snapshot de saldos por wallet. Usuario decide si los planned se descartan, se reprograman o se postean manualmente.
- `CLOSED`: entidad inmutable. Todo `Expense` con `quincena_id = X` queda bloqueado contra updates salvo por `reconciled_at`.

### 2.3 Ciclo quincenal — timeline de eventos del sistema

```
Día −3 (anterior a start_date)
 └─ WorkManager periodic worker "QuincenaProvisioner"
    - Crea Quincena(status=PROVISIONED, year, month, half)
    - Materializa Expenses PLANNED desde RecurrenceTemplate activas
    - Copia InstallmentPlan.next_installment → Expense PLANNED

Día 0 (start_date)
 └─ AlarmManager (exact alarm) dispara transición
    - PROVISIONED → ACTIVE
    - Push notification: "Quincena del {start}–{end} activa. Presupuesto ${E10}"
    - Tile de Wear refresca automaticamente

Día 0..N (operación normal)
 └─ Usuario captura Expenses (POSTED)
    - Motor de atribución sugiere (§5)
    - Trigger SQL updates saldos de PaymentMethod
    - Broadcast a Tiles y Complications

Día N (end_date, 22:00 local)
 └─ Notification: "¿Listo para cerrar la quincena?"
    - Tap → abre CLOSING_REVIEW

Día N+1..N+3
 └─ Daily reminder si aún ACTIVE
```

### 2.4 Consolidación de ingresos multi-usuario

Réplica de las filas `E4` y `E5` del Excel, pero modelada correctamente:

```kotlin
// Domain entity
data class IncomeSource(
    val id: UUID,
    val memberId: UUID,           // Benjamin, Norma, o futuro
    val label: String,            // "Sueldo quincenal", "Honorarios"
    val amountMxn: BigDecimal,
    val cadence: IncomeCadence,   // QUINCENAL | MONTHLY | IRREGULAR
    val expectedDate: LocalDate,
    val paymentMethodId: UUID     // cuenta donde se deposita
)
```

UI: `IncomeCard` en el header de `QuincenaScreen` con una fila por miembro pagador y un `Subtotal chip` con `=SUM(E4:E5)`. Cada fila es editable con `long-press` y confirma con haptic `HapticFeedbackType.Confirm`. El `Subtotal` se recalcula en `collectAsStateWithLifecycle` sobre el flow observable.

**Regla importante**: los ingresos registrados alimentan la columna `actual_income_mxn` de `Quincena` y son el divisor para el indicador `Balance` (§1.3 del doc base). Un ingreso con `status=PLANNED` cuenta hacia `projected_income_mxn` pero no hacia el dashboard real hasta que se marca como `POSTED` (ej. "¿recibiste el sueldo? confirma").

### 2.5 Seguimiento de saldos por wallet (reemplazo de columnas N–U)

Pantalla `WalletsScreen`:

```
┌──────────────────────────────────────────────────────┐
│  Cuentas                             + Nueva cuenta  │
├──────────────────────────────────────────────────────┤
│  [💳] Banamex Clásica          (CRÉDITO)             │
│       Límite $50,000 · Usado $32,450 (64.9%)         │
│       Corte día 18 · Paga día 5 · Próximo: 22 abr    │
│       ▶  Últimos cargos  ▶  Conciliar                │
├──────────────────────────────────────────────────────┤
│  [🏦] Banamex Débito           (DÉBITO)              │
│       Saldo: $18,520  ↑ $45,000 esta quincena        │
├──────────────────────────────────────────────────────┤
│  [📱] Mercado Pago Monedero    (WALLET)              │
│       Saldo: $1,230  · 3 cargos pendientes           │
├──────────────────────────────────────────────────────┤
│  [💵] Efectivo                 (CASH)                 │
│       Saldo estimado: $2,100                         │
└──────────────────────────────────────────────────────┘
```

**Mecánica del saldo en vivo**:

1. Al crear un `Expense(payment_method_id = X, status = POSTED)` un `Trigger` SQL decrementa `PaymentMethod.current_balance_mxn`:

```sql
CREATE TRIGGER trg_expense_posted_decrement
AFTER INSERT ON expense
WHEN NEW.status = 'POSTED'
BEGIN
  UPDATE payment_method
    SET current_balance_mxn = current_balance_mxn -
      CASE
        WHEN kind IN ('CREDIT_CARD','DEPARTMENT_STORE_CARD','BNPL_INSTALLMENT')
          THEN -NEW.amount_mxn    -- en crédito, el saldo (deuda) sube
        ELSE NEW.amount_mxn       -- en débito/efectivo, el saldo baja
      END
  WHERE id = NEW.payment_method_id;
END;
```

2. Los **tres casos típicos del Excel** (Mercado Pago, Banamex, Efectivo) se vuelven entidades `PaymentMethod` con su propio `current_balance_mxn` observable.
3. Un gasto puede **dividirse entre dos wallets** con un `ExpenseSplit` (extensión del modelo base):

```sql
CREATE TABLE expense_split (
  id TEXT PRIMARY KEY,
  expense_id TEXT NOT NULL REFERENCES expense(id) ON DELETE CASCADE,
  payment_method_id TEXT NOT NULL REFERENCES payment_method(id),
  amount_mxn REAL NOT NULL,
  CHECK (amount_mxn > 0)
);
-- Invariante: SUM(expense_split.amount_mxn) WHERE expense_id = X  =  expense.amount_mxn
```

Esto reemplaza el patrón `=N21+T13` observado en `O29` del Excel.

### 2.6 Cálculo de diferencia presupuestal en tiempo real

Widget `BudgetVarianceCard` suscrito a un `Flow<QuincenaSnapshot>` que se recalcula en el **main dispatcher** con `conflate()` (drop intermedios) cada vez que:
- Se inserta/edita/elimina un `Expense`
- Se cambia el `status` de un expense
- Se edita el `amount_mxn` de una `RecurrenceTemplate` activa

```kotlin
data class QuincenaSnapshot(
    val projectedIncomeMxn: BigDecimal,
    val projectedExpensesMxn: BigDecimal,
    val postedIncomeMxn: BigDecimal,
    val postedExpensesMxn: BigDecimal,
    val plannedRemainingMxn: BigDecimal,  // = Σ expense where status=PLANNED
    val savingsLockedMxn: BigDecimal,     // Ahorro empresa + transferencias a savings
) {
    val balanceMxn: BigDecimal
        get() = postedIncomeMxn - postedExpensesMxn - savingsLockedMxn
    val varianceMxn: BigDecimal
        get() = projectedExpensesMxn - postedExpensesMxn  // positivo = vamos bien
    val executionPct: Float
        get() = (postedExpensesMxn / projectedExpensesMxn * 100f)
            .coerceIn(0f, 999f).toFloat()
}
```

UI del widget:

```
┌───────────────────────────────────────────────┐
│  Quincena del 16 al 30 de abril 2026          │
│                                                │
│  Balance              Ejecución   Falta gastar │
│  $12,340              64%         $8,150       │
│  ▲ $2,100 vs plan     ▓▓▓▓▓▓░░░                │
└───────────────────────────────────────────────┘
```

El color del `balanceMxn` usa `colorScheme.primary` si ≥ 0, `colorScheme.error` si < 0, con transición de color animada vía `animateColorAsState(spring())`.

### 2.7 Tablas categóricas: patrón `ExpandableExpenseTable`

Cada categoría (HOUSING, ENTERTAINMENT, LOANS, etc.) se renderiza como un bloque plegable:

```
┌─ HOUSING ──────────────────── $6,612 / $7,400 ──▼─┐
│ Hipoteca              $4,000    Norma  Benjamin   │
│ Internet                $899    Norma       $0    │
│ Teléfono Norma          $498    Norma       $0    │
│ Teléfono Santi          $199    Norma       $0    │
│ Electricidad          $1,016       $0   Benjamin  │
│ ┌───────────────────────────────────────────────┐ │
│ │ + Agregar gasto en HOUSING                    │ │
│ └───────────────────────────────────────────────┘ │
└───────────────────────────────────────────────────┘
```

Columnas visibles son responsivas: en Fold plegado muestra `concepto | monto`, en Fold desplegado añade `pagador | beneficiarios | estado`. El usuario puede cambiar el layout con un toggle (`Density.toggle`).

---

## 3. Mecanismos de Captura Acelerada

### 3.1 Objetivo de fricción cero

Meta medible: **desde que el gasto ocurre hasta que queda registrado ≤ 5 segundos** en el 80% de los casos. El flujo ideal requiere ≤ 3 acciones físicas.

### 3.2 Superficies de captura — jerarquía por latencia

| Ranking | Superficie | Trigger | Latencia típica |
|---|---|---|---|
| 1 | **Quick Tap overlay** (Android) | Doble golpe dorso del teléfono | 1.4 s |
| 2 | Tile "Registrar gasto" (Wear) | Swipe lateral desde watch face | 2.0 s |
| 3 | Complicación de watch face | Tap directo en la esfera | 2.3 s |
| 4 | Widget homescreen expandible | Tap en botón de la quick row | 2.8 s |
| 5 | Atajo de assistant / intent | "Hey Google, registra gasto" | 3.1 s |
| 6 | Notification quick reply | Tap respuesta en notificación | 3.5 s |
| 7 | App completa (FAB) | Abrir app → FAB | 4.8 s |

Cada superficie escribe en la misma `ExpenseDraftRepository` y la confirmación final materializa un `Expense` transaccional.

### 3.3 Quick Tap — especificación técnica

**Quick Tap** es una feature nativa de Pixel (Android 12+) que detecta dos golpes consecutivos en el sensor trasero. Expone acciones limitadas a nivel de OS (screenshot, linterna, play/pause, Assistant, app específica). **No ofrece API pública para invocar un overlay in-app directamente**, pero se puede construir la experiencia combinando tres componentes:

#### Componente A — Shortcut disparable por Quick Tap

```xml
<!-- AndroidManifest.xml -->
<activity
    android:name=".capture.QuickCaptureActivity"
    android:theme="@style/Theme.Transparent.NoActionBar"
    android:excludeFromRecents="true"
    android:launchMode="singleInstance"
    android:showOnLockScreen="false"
    android:taskAffinity="">
    <intent-filter>
        <action android:name="mx.budget.action.QUICK_CAPTURE" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity>

<!-- Shortcut estático para que aparezca en Settings → Gestures → Quick Tap → "Open app" -->
<meta-data android:name="android.app.shortcuts"
           android:resource="@xml/shortcuts" />
```

`res/xml/shortcuts.xml`:

```xml
<shortcuts xmlns:android="http://schemas.android.com/apk/res/android">
    <shortcut
        android:shortcutId="quick_capture"
        android:enabled="true"
        android:icon="@drawable/ic_quick_capture"
        android:shortcutShortLabel="@string/shortcut_quick_capture_short"
        android:shortcutLongLabel="@string/shortcut_quick_capture_long">
        <intent
            android:action="mx.budget.action.QUICK_CAPTURE"
            android:targetPackage="mx.budget"
            android:targetClass="mx.budget.capture.QuickCaptureActivity" />
    </shortcut>
</shortcuts>
```

El usuario configura una sola vez: **Settings → System → Gestures → Quick Tap → "Open app" → Budget**. A partir de ahí, el doble golpe lanza `QuickCaptureActivity`.

#### Componente B — Overlay transparente tipo "floating panel"

`QuickCaptureActivity` no es una pantalla normal. Es una activity con `Theme.Transparent` que, al arrancar, solicita al `OverlayService` (foreground service) pintar una **burbuja semitransparente** encima del contenido actual usando `WindowManager.addView()`:

```kotlin
class OverlayService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val windowManager = getSystemService<WindowManager>()!!
        val overlayView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { QuickCapturePanel(onDismiss = { stopSelf() }) }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,  // Requiere SYSTEM_ALERT_WINDOW
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dpToPx(24)
        }
        windowManager.addView(overlayView, params)
        startForeground(NOTIF_ID, buildOngoingNotification())
        return START_NOT_STICKY
    }
}
```

El `QuickCapturePanel` es un composable que se muestra con animación `slideInVertically(spring())` + `fadeIn()`, imita visualmente una **notificación activa expandida** siguiendo los patrones de Notifications M3 Expressive (fondo `surfaceContainerHighest`, elevation 2, corner 28 dp).

#### Componente C — Formulario mínimo viable de 4 campos

```
┌─────────────────────────────────────────────────┐
│ ⚡ Gasto rápido                          × cerrar │
├─────────────────────────────────────────────────┤
│                                                  │
│  $ [___________]  ← focus inicial, keypad num   │
│                                                  │
│  [Comida ▾]  [Banamex ▾]  [Norma 👤]            │
│                                                  │
│  Sugerencias (últimos 3):                        │
│  • Despensa  $1,950   Banamex                   │
│  • Gasolina  $1,400   Efectivo                  │
│  • Netflix   $329     Banamex Clásica           │
│                                                  │
│                         [  Guardar  (Enter)  ]   │
└─────────────────────────────────────────────────┘
```

Detalles:
- **Foco inicial**: campo monto con `ImeAction.Done` y teclado `KeyboardType.Decimal`.
- **Categoría, wallet, beneficiario**: se predicen con el motor determinista (§5) en base a las últimas 3 capturas realizadas en la misma franja horaria (ventana ±90 min) y desde esa misma ubicación si hay permiso de *coarse location*.
- **Enter** confirma y el overlay se cierra con `slideOutVertically(spring())`. El gasto se escribe en Room **en el mismo dispatcher** con `withContext(Dispatchers.IO)` usando una transacción. Tiempo total medido: 1.2–1.6 s.
- Si el usuario no toca nada en 6 segundos, el overlay se desvanece automáticamente (el gesto pudo ser accidental).

#### Componente D — Fallback para teléfonos sin Quick Tap

En dispositivos que no son Pixel (o con Quick Tap desactivado), la misma `QuickCaptureActivity` se invoca desde:
- Tile de Quick Settings personalizado (`TileService` — requiere Android 7+, es ubicuo).
- Widget de pantalla de inicio de 2×1 "Capturar gasto".
- Ongoing notification permanente (si el usuario lo elige) con RemoteInput.

### 3.4 Permisos requeridos (tabla completa en Apéndice C)

- `SYSTEM_ALERT_WINDOW` — para el overlay. Flujo onboarding: al primer lanzamiento se abre un `Activity` explicativo (por qué lo pedimos, imagen del overlay) y luego redirige a `Settings.ACTION_MANAGE_OVERLAY_PERMISSION`.
- `POST_NOTIFICATIONS` — para la notificación del foreground service.
- `FOREGROUND_SERVICE` y `FOREGROUND_SERVICE_SPECIAL_USE` — porque la captura rápida no encaja en tipos de *special use* estándar (se declara `specialUse` con justificación "Persistent expense capture overlay").

### 3.5 Captura por intent/deep-link (compatibilidad con Tasker, Shortcuts, Assistant)

```
mx.budget://capture?amount=85.00&category=comida&wallet=efectivo&beneficiary=self
```

Este intent también lanza `QuickCaptureActivity` con los campos prellenados. Habilita integraciones de terceros sin depender de APIs privadas.

### 3.6 Instrumentación de rendimiento

Cada apertura del overlay emite trazas con `androidx.tracing.Trace` y `PerfettoHandle` para monitorear:

```
QuickCapture.coldStart         → desde intent hasta first frame del panel
QuickCapture.inputLatency      → entre keystroke y render
QuickCapture.persistDuration   → tiempo de commit de la transacción Room
```

Metas hard: `P95(coldStart) < 600 ms` en Fold interno, `P99(persistDuration) < 120 ms`.

---

## 4. Ecosistema Wear OS (Pixel Watch 3)

### 4.1 Superficies nativas distribuidas

El Pixel Watch 3 soporta cuatro superficies de integración; la app las usa todas:

1. **App completa** (Compose for Wear OS) — para registro detallado y revisión.
2. **Tiles** (Protolayout) — swipe desde watch face para ver un panel curado.
3. **Complications** — campos dentro de la esfera del reloj.
4. **Ongoing Activity** — notificación persistente cuando la quincena está activa.

### 4.2 Tiles nativos — catálogo

Cada Tile se implementa como un `TileService` separado. El usuario elige cuáles activar desde la watch companion app.

#### 4.2.1 Tile `BudgetRemainingTile`

**Propósito**: de un vistazo, cuánto presupuesto queda en la quincena actual.

```
┌────────────────────────┐
│   Quincena activa      │
│   16–30 abril          │
│                        │
│       $ 8,150          │  ← displayLargeEmphasized
│    falta gastar         │
│                        │
│   ▓▓▓▓▓▓▓░░░  64%      │
│                        │
│   Desde $127,000       │
│   ejecutado $81,850    │
└────────────────────────┘
```

Implementación:

```kotlin
class BudgetRemainingTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest) =
        serviceScope.future {
            val snapshot = repo.observeActiveQuincenaSnapshot().first()
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RES_VERSION)
                .setTileTimeline(buildTimeline(snapshot))
                .setFreshnessIntervalMillis(5 * 60 * 1000L)  // auto-refresh cada 5 min
                .build()
        }

    private fun buildTimeline(s: QuincenaSnapshot): TimelineBuilders.Timeline {
        val column = LayoutElementBuilders.Column.Builder()
            .addContent(Text.Builder(this, "Falta gastar")
                .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                .build())
            .addContent(Text.Builder(this, s.plannedRemaining.formatMxn())
                .setTypography(Typography.TYPOGRAPHY_DISPLAY1)
                .setColor(argb(colorProvider.primary))
                .build())
            .addContent(progressArc(s.executionPct))
            .build()

        val entry = TimelineBuilders.TimelineEntry.Builder()
            .setLayout(LayoutBuilders.Layout.Builder().setRoot(column).build())
            .build()
        return TimelineBuilders.Timeline.Builder().addTimelineEntry(entry).build()
    }
}
```

El refresh es proactivo por parte del service cuando:
- Se inserta un nuevo `Expense` en el teléfono → `TileService.getUpdater(ctx).requestUpdate(BudgetRemainingTileService::class.java)` se invoca desde el Wear data layer listener.
- Cambia el estado de la quincena (transición de ACTIVE → CLOSED).

#### 4.2.2 Tile `SavingsProgressTile`

Progreso acumulado hacia la meta de ahorro trimestral. Usa un **arc segmented** con 4 segmentos (uno por quincena) y un `tick mark` que marca la posición actual.

```
┌────────────────────────┐
│  Ahorro Q2 2026        │
│                        │
│      ╭─────╮           │
│     │ 67%  │           │
│      ╰─────╯           │
│                        │
│  $40,200 / $60,000     │
│  Faltan 2 quincenas     │
└────────────────────────┘
```

#### 4.2.3 Tile `UpcomingInstallmentsTile`

Lista priorizada (max 3) de próximas cuotas:

```
┌────────────────────────┐
│  Próximas cuotas        │
├────────────────────────┤
│  Omar 7/10   22 abr     │
│  $5,500                 │
├────────────────────────┤
│  MeLi 9/12   25 abr     │
│  $1,342                 │
├────────────────────────┤
│  Buró 3/3    28 abr     │
│  $2,800                 │
└────────────────────────┘
```

Cada fila es un `Clickable` que abre la app en la pantalla del plan correspondiente (deep link `mx.budget://installment/{id}`).

#### 4.2.4 Tile `QuickCaptureTile` (acción, no data)

Un solo botón grande que lanza la pantalla de captura rápida nativa del reloj (§4.5):

```
┌────────────────────────┐
│                        │
│         [   +   ]      │
│                        │
│     Registrar gasto     │
│                        │
│    Último: $85 Café     │
│    hace 2 horas         │
└────────────────────────┘
```

#### 4.2.5 Tile `WalletBalanceTile`

Saldo de la wallet de débito primaria (configurable). Incluye flecha con delta respecto al saldo inicio de quincena:

```
┌────────────────────────┐
│  Banamex Débito         │
│                        │
│     $ 18,520            │
│                        │
│   ▼ $26,480             │
│   esta quincena         │
└────────────────────────┘
```

### 4.3 Complications interactivas

Las *watch face complications* permiten exhibir datos dentro de esferas como la "Wavy Analog" o "Metro" del Pixel Watch 3. La app registra **cinco data sources** como `ComplicationDataSourceService`:

| ID | Tipo nativo | Contenido | Uso sugerido |
|---|---|---|---|
| `BudgetRemainingComplication` | `SHORT_TEXT` | `$8,150` + label `FALTA` | Slot superior/inferior |
| `ExecutionPctComplication` | `RANGED_VALUE` | 0–100% con progress ring | Slot lateral circular |
| `BalanceComplication` | `LONG_TEXT` | `Balance $12,340 ▲` | Slot de texto largo |
| `QuickCaptureComplication` | `SMALL_IMAGE` + tap | icono "+" → `QuickCapture` | Slot de acción |
| `NextInstallmentComplication` | `SHORT_TEXT` | `Omar 22abr` | Slot pequeño |

Implementación canónica:

```kotlin
class BudgetRemainingComplicationService : ComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData =
        ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder("$8.1k").build(),
            contentDescription = PlainComplicationText.Builder("Falta gastar").build()
        ).setTitle(PlainComplicationText.Builder("FALTA").build())
         .build()

    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener
    ) {
        serviceScope.launch {
            val snap = repo.observeActiveQuincenaSnapshot().first()
            val data = ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(
                    snap.plannedRemaining.formatMxnCompact()   // "$8.1k"
                ).build(),
                contentDescription = PlainComplicationText.Builder(
                    "Falta gastar ${snap.plannedRemaining.formatMxn()}"
                ).build()
            ).setTitle(PlainComplicationText.Builder("FALTA").build())
             .setTapAction(pendingIntentToApp())
             .build()
            listener.onComplicationData(data)
        }
    }

    override fun onComplicationActivated(
        complicationInstanceId: Int,
        type: ComplicationType
    ) {
        // Registra el instance id para refresh reactivo desde el repo
        activeComplicationRegistry.register(complicationInstanceId, this::class)
    }
}
```

Cuando cambia `QuincenaSnapshot`, el repo invoca:

```kotlin
ComplicationDataSourceUpdateRequester
    .create(context, ComponentName(context, BudgetRemainingComplicationService::class.java))
    .requestUpdateAll()
```

### 4.4 Ongoing Activity — presencia persistente

Mientras la quincena está `ACTIVE`, la app publica una `OngoingActivity` (Wear-specific) que aparece en watch face como *chip* y en CoF (Carousel of Faces). Ofrece:
- Label dinámico (% ejecución).
- `touchIntent` → `QuickCaptureWearActivity`.
- Cancelable sólo al cerrar la quincena.

```kotlin
val ongoing = OngoingActivity.Builder(context, NOTIF_ID, notificationBuilder)
    .setAnimatedIcon(R.drawable.animated_wallet)
    .setStaticIcon(R.drawable.wallet_outline)
    .setStatus(Status.Builder()
        .addTemplate("#BUDGET# falta · #PCT#% ejecutado")
        .addPart("BUDGET", Status.TextPart(snap.plannedRemaining.formatMxnCompact()))
        .addPart("PCT", Status.TextPart(snap.executionPct.toInt().toString()))
        .build())
    .setTouchIntent(quickCaptureIntent)
    .build()
ongoing.apply(context)
```

### 4.5 App completa en Wear — navegación

```
Navigation graph (Wear):
  watch_home (SwipeDismissable) →
     ├─ quick_capture        (pantalla de 4 campos rotables con bezel)
     ├─ recent_expenses      (lista de últimos 20 gastos con swipe-to-delete)
     ├─ quincena_summary     (KPIs del snapshot)
     └─ installments_list    (lista completa de planes)
```

Detalles UX:
- Rotación del **crown** (DPad virtual): scroll y selección de monto (modo fine-tune ±10 MXN).
- `HapticFeedback.Confirm` al guardar, `HapticFeedback.Reject` al error de validación.
- Gesto de reverse-lift: al voltear la muñeca hacia abajo durante captura, se cancela (similar a "end call by face-down").

### 4.6 Sincronización Watch ↔ Phone

Canal primario: **Data Layer API** con nodos autodescubiertos.

```kotlin
class ExpenseSyncListenerService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { event ->
            if (event.dataItem.uri.path == "/expense/create") {
                val draft = DataMapItem.fromDataItem(event.dataItem)
                    .dataMap.toExpenseDraft()
                lifecycleScope.launch { repo.commitDraft(draft) }
            }
        }
    }
}
```

Política de conflictos: **LWW con vector clock** a nivel de entidad (ya definido en RF-80 del doc base). La captura desde Watch siempre sucede offline-first y queda en cola hasta que haya pareo BT o Wi-Fi.

---

## 5. Motor Determinista de Atribución

### 5.1 Principios (sin LLMs)

1. Toda predicción de beneficiario/categoría/wallet se deriva **exclusivamente** de:
   - Tablas de frecuencia sobre el histórico.
   - Reglas explícitas (if/else o DSL declarativo).
   - Distancias de cadena (Jaro-Winkler) para *fuzzy matching* de conceptos.
   - Nearest-neighbor sobre vectores de características numéricas (monto, hora, día).
2. Todo modelo es **explicable**: para cada sugerencia, la UI puede mostrar "¿Por qué?" con las reglas aplicadas.
3. Las reglas son **determinísticas y reproducibles**: misma entrada → mismo output. Puede auditarse con tests unitarios.
4. El usuario **siempre** confirma una atribución antes de commitear. El motor solo **pre-llena**; no decide.

### 5.2 Superficie de datos del motor

```sql
-- Tabla materializada (refresh incremental on INSERT expense)
CREATE TABLE attribution_memory (
    key_hash         BLOB PRIMARY KEY,     -- sha256(normalized_concept || round_bucket(amount))
    concept_norm     TEXT NOT NULL,        -- "netflix"
    amount_bucket    INTEGER NOT NULL,     -- bucket de 50 MXN (329 → 6)
    category_id      TEXT NOT NULL,
    payment_method_id TEXT NOT NULL,
    beneficiary_ids_json TEXT NOT NULL,    -- ["member_uuid_1", "member_uuid_2"]
    payer_split_json TEXT NOT NULL,        -- {"norma_uuid": 10000}
    hit_count        INTEGER NOT NULL DEFAULT 1,
    last_seen_at     INTEGER NOT NULL,
    confidence       REAL NOT NULL         -- recalculado en cada hit
);
CREATE INDEX idx_attr_mem_concept ON attribution_memory(concept_norm);
CREATE INDEX idx_attr_mem_recency ON attribution_memory(last_seen_at DESC);
```

El `key_hash` se calcula como:

```kotlin
fun buildKeyHash(concept: String, amountMxn: BigDecimal): ByteArray {
    val normalized = concept.lowercase(MX_LOCALE)
        .unaccent()                                    // "teléfono" → "telefono"
        .replace(Regex("\\d+"), " ")                   // quitar dígitos ("Omar 8" → "Omar ")
        .replace(Regex("\\s+"), " ")
        .trim()
    val bucket = (amountMxn.toDouble() / 50).toInt()   // bucket de 50 MXN
    val payload = "$normalized||$bucket".toByteArray(Charsets.UTF_8)
    return MessageDigest.getInstance("SHA-256").digest(payload)
}
```

### 5.3 Algoritmo de sugerencia (flujo determinista)

Entrada: `(concept: String, amount: BigDecimal)` + contexto (`time_of_day`, `location_cell_id?`, `last_used_payment_method_id`)
Salida: `AttributionSuggestion` con campos: `category_id`, `payment_method_id`, `beneficiary_ids[]`, `payer_split`, `confidence: 0..1`, `reason: String`

Pseudo-código:

```kotlin
fun suggestAttribution(
    concept: String,
    amount: BigDecimal,
    context: CaptureContext
): AttributionSuggestion {

    // --- Paso 1: Exact match hash lookup ---
    val exactKey = buildKeyHash(concept, amount)
    val exactHit = db.queryAttributionByHash(exactKey)
    if (exactHit != null && exactHit.hitCount >= 3) {
        return exactHit.toSuggestion(
            confidence = min(0.95, 0.5 + 0.1 * exactHit.hitCount),
            reason = "Patrón exacto repetido ${exactHit.hitCount} veces"
        )
    }

    // --- Paso 2: Fuzzy match del concepto solo (sin bucket de monto) ---
    val candidates = db.queryByConceptPrefix(
        conceptNorm = normalize(concept).take(12),
        limit = 20
    )
    val scored = candidates.map { c ->
        val jw = jaroWinkler(normalize(concept), c.conceptNorm)       // 0..1
        val amountPenalty = 1.0 - min(
            abs(amount.toDouble() - decodeBucket(c.amountBucket)) / max(amount.toDouble(), 1.0),
            1.0
        )
        val recencyBoost = recencyScore(c.lastSeenAt, now)            // 0..1
        val frequencyBoost = sigmoid(c.hitCount - 3)                   // 0..1
        val total = 0.45 * jw + 0.25 * amountPenalty +
                    0.20 * frequencyBoost + 0.10 * recencyBoost
        c to total
    }.sortedByDescending { it.second }

    val best = scored.firstOrNull()
    if (best != null && best.second >= 0.78) {
        return best.first.toSuggestion(
            confidence = best.second,
            reason = "Similar a '${best.first.conceptNorm}' (${(best.second * 100).toInt()}%)"
        )
    }

    // --- Paso 3: Reglas declarativas por categoría ---
    val ruleHit = CategoryRuleEngine.match(concept, amount, context)
    if (ruleHit != null) return ruleHit

    // --- Paso 4: Default de household ---
    return AttributionSuggestion(
        categoryId = DEFAULT_CATEGORY_FALLBACK,
        paymentMethodId = context.lastUsedPaymentMethodId ?: DEFAULT_WALLET,
        beneficiaryIds = listOf(),
        payerSplit = mapOf(),
        confidence = 0.2f,
        reason = "Sin coincidencia; usando defaults"
    )
}
```

Funciones auxiliares clave:

```kotlin
// Similaridad de cadena Jaro-Winkler (implementación pura, sin dependencias ML)
fun jaroWinkler(s1: String, s2: String): Double { /* implementación estándar */ }

// Boost por recencia: últimos 7 días cuentan casi completo, decaimiento exponencial
fun recencyScore(lastSeenAt: Long, now: Long): Double {
    val daysAgo = (now - lastSeenAt).toDouble() / (1000 * 60 * 60 * 24)
    return exp(-daysAgo / 30.0)   // vida media de 30 días
}

fun sigmoid(x: Double) = 1.0 / (1.0 + exp(-x))
```

### 5.4 Reglas declarativas (DSL de atribución)

Para los casos que no tienen histórico, se evalúa un motor de reglas declarativas. Las reglas viven en un archivo versionado `attribution_rules.yaml` que se compila al cargar la app:

```yaml
# attribution_rules.yaml  v1.3
rules:

  - id: phone_santi
    when:
      concept_matches_regex: "(?i)\\btel(e|é)fono\\s+santi(ago)?\\b"
    then:
      category: HOUSING.TELEFONO
      beneficiaries: [ "member:santiago" ]
      payer_split: { "member:norma": 10000 }
    priority: 100

  - id: phone_pau_david
    when:
      concept_matches_regex: "(?i)\\btel(e|é)fono\\s+pau\\s+y\\s+david\\b"
    then:
      category: HOUSING.TELEFONO
      beneficiaries: [ "member:pau", "member:david" ]
      payer_split: { "member:norma": 10000 }
    priority: 100

  - id: mesada_david
    when:
      concept_matches_regex: "(?i)^(david|mesada\\s+david)(\\s+\\w+)?$"
      amount_between: [5000, 20000]
    then:
      category: TRANSFERENCIAS_FAMILIARES.DAVID
      beneficiaries: [ "member:david" ]
      payer_split: { "member:norma": 10000 }
    priority: 90

  - id: gasolina_by_keyword
    when:
      concept_contains_any: ["gasolina", "gasoliner", "combustible", "pemex", "shell"]
    then:
      category: TRANSPORTATION.GASOLINA
      beneficiaries_from_concept_parser: true    # invoca §4.2 del doc base
    priority: 80

  - id: streaming_netflix
    when:
      concept_contains: "netflix"
    then:
      category: ENTERTAINMENT.STREAMING
      beneficiaries: [ "household:all" ]
      default_amount: 329
    priority: 75

  - id: despensa_supermarket
    when:
      concept_contains_any: ["despensa", "walmart", "soriana", "chedraui", "superama", "costco"]
    then:
      category: FOOD.DESPENSA
      beneficiaries: [ "household:all" ]
    priority: 70

  - id: installment_numbered
    when:
      concept_matches_regex: "^(.+?)\\s+(\\d+)\\s*(de\\s+(\\d+))?$"
    then:
      extract_installment:
        from_group: 1
        current_group: 2
        total_group: 4
      category_from: "matched_base_concept"
    priority: 60
```

Motor de evaluación:

```kotlin
class CategoryRuleEngine(private val rules: List<Rule>) {

    fun match(concept: String, amount: BigDecimal, ctx: CaptureContext): AttributionSuggestion? {
        val eligible = rules
            .filter { it.predicate(concept, amount, ctx) }
            .sortedByDescending { it.priority }

        val winner = eligible.firstOrNull() ?: return null

        return AttributionSuggestion(
            categoryId = winner.resolvedCategoryId(concept),
            paymentMethodId = ctx.lastUsedPaymentMethodId ?: DEFAULT_WALLET,
            beneficiaryIds = winner.resolvedBeneficiaries(concept),
            payerSplit = winner.payerSplit,
            confidence = 0.7f,
            reason = "Regla: ${winner.id}"
        )
    }
}
```

Las reglas viven en un asset de la app y pueden actualizarse remotamente con un sistema simple tipo Remote Config sin necesidad de nuevo release (descarga de YAML firmado, parseo, swap in-memory).

### 5.5 Memorización con aprendizaje incremental determinista

Cada vez que el usuario confirma un `Expense` — incluso si aceptó la sugerencia tal cual o la editó — el sistema actualiza `attribution_memory`:

```kotlin
fun onExpenseConfirmed(expense: Expense, attributions: List<ExpenseAttribution>) {
    val keyHash = buildKeyHash(expense.concept, expense.amountMxn)
    val existing = db.queryAttributionByHash(keyHash)

    val payerSplit = attributions
        .filter { it.role == Role.PAYER }
        .associate { it.memberId to it.shareBps }

    val beneficiaryIds = attributions
        .filter { it.role == Role.BENEFICIARY }
        .map { it.memberId }

    if (existing == null) {
        db.insertAttributionMemory(AttributionMemoryRow(
            keyHash = keyHash,
            conceptNorm = normalize(expense.concept),
            amountBucket = bucketize(expense.amountMxn),
            categoryId = expense.categoryId,
            paymentMethodId = expense.paymentMethodId,
            beneficiaryIds = beneficiaryIds,
            payerSplit = payerSplit,
            hitCount = 1,
            lastSeenAt = expense.occurredAt,
            confidence = 0.35
        ))
    } else {
        val newHit = existing.hitCount + 1
        val newConf = min(0.99, existing.confidence + (1.0 - existing.confidence) * 0.15)

        // Si hubo cambio de atribución, registrar conflicto y recalcular
        if (existing.beneficiaryIds != beneficiaryIds ||
            existing.payerSplit != payerSplit) {
            logAttributionConflict(existing, attributions)
            // Adopta la nueva (LWW), pero baja confianza
            db.updateAttributionMemory(existing.copy(
                beneficiaryIds = beneficiaryIds,
                payerSplit = payerSplit,
                hitCount = 1,              // reset count on conflict
                confidence = 0.4,
                lastSeenAt = expense.occurredAt
            ))
        } else {
            db.updateAttributionMemory(existing.copy(
                hitCount = newHit,
                confidence = newConf,
                lastSeenAt = expense.occurredAt
            ))
        }
    }
}
```

Propiedades demostrables:
- **Convergencia**: la confianza crece monotónicamente hacia 0.99 si no hay conflictos.
- **Reactividad al cambio**: un cambio de preferencia del usuario (ej. ahora Benjamin paga Netflix) resetea el contador y converge de nuevo tras 3-5 ocurrencias.
- **Trazabilidad**: cada actualización queda en un `attribution_audit_log` para explicar al usuario por qué cambió la sugerencia.

### 5.6 UI del flujo de atribución

Cuando el usuario captura un gasto (en cualquier superficie), la UI presenta la sugerencia como **chips precargados** que se pueden editar con un tap:

```
┌─────────────────────────────────────────────────┐
│ Concepto  [Teléfono Santi_________]              │
│ Monto     [$ 199.00___________]                  │
│                                                  │
│ Categoría       [HOUSING · Teléfono ✏]          │
│ Método de pago  [Banamex Clásica ✏]              │
│ Beneficiarios   [🧑 Santi ✏]                     │
│ Pagador         [👤 Norma 100% ✏]                │
│                                                  │
│ ℹ️ Regla: phone_santi (confianza 95%)            │
│                                                  │
│              [ Cancelar ] [ Confirmar ]          │
└─────────────────────────────────────────────────┘
```

El tooltip "ℹ️ Regla: ..." es la **cláusula de explicabilidad**: el usuario ve exactamente qué regla o qué memoria disparó la sugerencia.

### 5.7 Dropdown de beneficiarios (UX canónica)

Los beneficiarios son seleccionables desde un **multi-select chip row** ordenado por:

1. Miembros actualmente sugeridos (preseleccionados).
2. Miembros más frecuentes para esa categoría (top 3).
3. Resto del household.
4. "Añadir nuevo miembro" al final.

```
Beneficiarios:
[✓ Santi]  [  Pau  ]  [  David  ]  [  Agustín  ]  [  + Nuevo  ]
          └ ranked by historical frequency for cat HOUSING.TELEFONO
```

El chip "Familiar" mencionado en el brief se implementa como una **categoría meta** que se expande a todos los dependientes del household (`role = BENEFICIARY_DEPENDENT`):

```kotlin
// al commitear, si el usuario eligió "Familiar", se expande a:
fun expandBeneficiaryMeta(meta: BeneficiaryMeta, household: Household): List<UUID> {
    return when (meta) {
        BeneficiaryMeta.FAMILIAR -> household.activeDependents().map { it.id }
        BeneficiaryMeta.HOUSEHOLD -> household.activeMembers().map { it.id }
        BeneficiaryMeta.SELF -> listOf(currentUser.memberId)
    }
}
```

---

## 6. Módulos Analíticos de Alto Nivel (Pixel 9 Pro Fold)

### 6.1 Aprovechamiento del formato plegable

El Pixel 9 Pro Fold ofrece dos canvas:
- **Outer display**: 6.3" (2424 × 1080, 120 Hz). Modo "control remoto": captura rápida y glance de KPIs.
- **Inner display**: 8.0" (2076 × 2152, 120 Hz LTPO). Modo "dashboard": visualizaciones densas en **dual-pane layout** (WindowSizeClass.EXPANDED).

Las analíticas profundas son ciudadanas de primera clase únicamente en el inner display. En plegado, sólo se muestran KPIs y un drill-down limitado.

### 6.2 Navegación adaptativa

```kotlin
@Composable
fun AnalyticsRoot() {
    val windowSize = currentWindowAdaptiveInfo().windowSizeClass
    val layout = when {
        windowSize.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND) -> TwoPane
        windowSize.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND) -> ListDetail
        else -> SinglePane
    }

    NavigableListDetailPaneScaffold(
        navigator = navigator,
        listPane = { AnalyticsCategoryList(onSelect = ::navigate) },
        detailPane = { AnalyticsDetail(selectedModule) },
        extraPane = null
    )
}
```

En el inner display, la lista de módulos ocupa 360 dp (~22%) a la izquierda y el detalle ocupa el resto con padding generoso.

### 6.3 Catálogo de módulos analíticos

#### 6.3.1 Módulo A — **Flujo de capital** (Sankey diagram)

Visualiza origen → destino del dinero en la quincena o en un rango.

```
        INGRESOS                    GASTOS
        ────────                    ──────
   Sueldo Norma  ━━━┓        ┏━━ HOUSING
                    ┣━━━━━━━━┫
   Sueldo Benji  ━━━┛        ┣━━ FOOD
                             ┣━━ TRANSFERENCIAS
                             ┣━━ LOANS (cuotas + intereses)
                             ┣━━ AHORRO
                             ┗━━ OTROS
```

Implementación: Sankey con `d3-sankey` en web, `Canvas` custom con primitivas de Compose en Android (no hay librería oficial M3; se implementa en ~400 líneas). Los nodos se posicionan por minimización de cruces (algoritmo estándar), los enlaces tienen grosor proporcional al monto y color por categoría (tonal palette del tema).

Interacciones:
- Tap en nodo → filtra el resto de la pantalla.
- Pinch → zoom al mes/trimestre.
- Toggle "Incluir cuentas bancarias como nodos intermedios" para ver qué wallet canaliza qué gastos.

Query base:

```sql
WITH income_agg AS (
  SELECT m.id AS src_id, m.display_name AS src_name,
         SUM(i.amount_mxn) AS flow
  FROM income_source i JOIN member m ON m.id = i.member_id
  WHERE i.occurred_at BETWEEN :from AND :to AND i.status='POSTED'
  GROUP BY m.id
),
expense_agg AS (
  SELECT c.id AS dst_id, c.display_name AS dst_name,
         SUM(e.amount_mxn) AS flow
  FROM expense e JOIN category c ON c.id = e.category_id
  WHERE e.occurred_at BETWEEN :from AND :to AND e.status='POSTED'
  GROUP BY c.id
)
SELECT * FROM income_agg UNION ALL SELECT * FROM expense_agg;
```

#### 6.3.2 Módulo B — **Gasto proporcional por miembro del hogar**

Responde "¿cuánto consume cada miembro?" con doble vista:

**Vista 1 — Stacked bar horizontal por quincena**:

```
Q1 Ene │ ████████████░░░░░░░░░ $24,300  (Pau 28%, David 34%, Agus 18%, Santi 12%, Adultos 8%)
Q2 Ene │ ██████████████░░░░░░░ $28,100
Q1 Feb │ ██████████░░░░░░░░░░░ $21,800
...
```

**Vista 2 — Treemap por miembro y categoría**:

```
┌─────────────────────────────────────┬─────────────┐
│  DAVID                              │  SANTI      │
│  ┌──────────┬─────────┐             │  ┌──────┐   │
│  │ Mesada   │Seguros  │             │  │Tele  │   │
│  │ $15,000  │ $1,166  │             │  │$199  │   │
│  └──────────┴─────────┘             │  ├──────┤   │
│                                     │  │Mesada│   │
├─────────────┬───────────────────────┼──┤ $2k  │   │
│  PAU        │  AGUSTÍN              │  └──────┘   │
│  Seguros    │  Seguros              │             │
│  $1,166     │  $1,166               │  NORMA+B.   │
│             │                       │  ...        │
└─────────────┴───────────────────────┴─────────────┘
```

Query (fundamental — ya incluida en §5.2.2 del doc base):

```sql
SELECT m.display_name AS miembro,
       c.display_name AS categoria,
       SUM(ea.share_amount_mxn) AS total
FROM expense_attribution ea
JOIN expense e  ON e.id = ea.expense_id
JOIN member m   ON m.id = ea.member_id
JOIN category c ON c.id = e.category_id
WHERE ea.role = 'BENEFICIARY' AND e.status = 'POSTED'
  AND e.occurred_at BETWEEN :from AND :to
GROUP BY m.id, c.id
ORDER BY total DESC;
```

KPI secundario: **Costo per cápita del hogar**:

```
Miembro       | Costo total q  | % del presupuesto
David         | $21,330        | 17.1%
Pau           | $3,452         |  2.8%
Agustín       | $1,166         |  0.9%
Santi         | $6,912         |  5.5%
Norma (adm.)  | $45,820        | 36.8%
Benjamin      | $9,230         |  7.4%
Compartido    | $36,600        | 29.4%
```

#### 6.3.3 Módulo C — **Detección de pagos por intereses**

Desglose mensual de intereses pagados. Componentes:

**Panel C.1 — Intereses acumulados por wallet** (columna izquierda del dual-pane):

```
Intereses pagados últimos 12 meses

Banamex Clásica   $4,830      ████████░░░░░░░░░  23%
Mercado Libre     $2,964      █████░░░░░░░░░░░░  14%
Liverpool         $1,912      ███░░░░░░░░░░░░░░   9%
Coppel            $1,204      ██░░░░░░░░░░░░░░░   6%
Préstamo Omar     $9,950      █████████████░░░░  48%
                  ─────────
Total             $20,860
                  ═════════
```

**Panel C.2 — Detalle del plan seleccionado** (columna derecha):

Línea de tiempo con cada cuota, distinguiendo capital (verde) de interés (rojo):

```
Préstamo Omar — 10 cuotas
Tasa implícita: 22.4% anual

Cuota 1  feb  ██ $5,500  → $4,475 capital · $1,025 interés
Cuota 2  mar  ██ $5,500  → $4,569 capital ·   $931 interés
Cuota 3  abr  ██ $5,500  → $4,664 capital ·   $836 interés
...
Cuota 10 nov  ██ $5,500  → $5,410 capital ·    $90 interés

Total intereses: $9,950   Capital: $45,050
```

Query base (ya definida en §5.2.3 del doc):

```sql
SELECT pm.display_name AS cuenta,
       SUM(COALESCE(e.installment_interest_mxn, 0)) AS intereses,
       SUM(e.amount_mxn) AS total_pagado,
       ROUND(100.0 * SUM(COALESCE(e.installment_interest_mxn,0))
             / NULLIF(SUM(e.amount_mxn), 0), 2) AS pct
FROM expense e
JOIN payment_method pm ON pm.id = e.payment_method_id
WHERE e.status = 'POSTED' AND e.occurred_at >= date('now', '-12 months')
GROUP BY pm.id
ORDER BY intereses DESC;
```

**Alerta inline**: si los intereses representan > 10% del gasto mensual, se muestra un banner persistente con recomendación de prioridad ("Liquidar Préstamo Omar ahorraría $X de aquí a noviembre").

#### 6.3.4 Módulo D — **Análisis de varianza histórica entre quincenas**

Comparador estadístico de la quincena actual vs baseline histórica.

**Panel superior — Overview numérico**:

```
                       Actual   Mediana 6Q   σ        Z-score    Status
Ingresos              $105,000   $105,000    $0       0.00       ═
Gastos totales         $92,340    $88,500   $4,200    +0.91      ▲
 · Housing              $6,612     $6,580     $45      +0.71      ═
 · Food                 $7,450     $7,100    $350      +1.00      ▲
 · Entertainment        $2,184     $1,980    $180      +1.13      ▲
 · Transportation       $3,100     $2,850    $260      +0.96      ═
 · Loans               $15,842    $14,500   $1,100     +1.22      ▲
 · Transferencias      $32,000    $30,500   $1,800     +0.83      ═
Ahorro                 $12,660    $16,500   $2,000     −1.92      ▼▼
```

Los umbrales visuales (`▲ ▼ ═`) se disparan con |z-score|:
- `|z| < 0.5` → `═` (normal)
- `0.5 ≤ |z| < 1.5` → `▲/▼` (atención)
- `|z| ≥ 1.5` → `▲▲/▼▼` (anomalía)

**Panel inferior — Visualizaciones**:

1. **Line chart con banda σ** (6 líneas, una por categoría top) — muestra trayectoria histórica con banda ±1σ sombreada. El punto actual resalta con glow si es outlier.
2. **Waterfall chart de la varianza total** — descompone la diferencia `actual_total − baseline_total` en contribuciones por categoría.
3. **Heatmap de varianza** (12 meses × 6 categorías top) — tonalidad rojo si sobregasto, verde si subgasto.

**Detector de outliers — método robusto (sin LLM)**:

```kotlin
fun detectOutliers(history: List<QuincenaSnapshot>, current: QuincenaSnapshot): List<Outlier> {
    val outliers = mutableListOf<Outlier>()
    val categories = history.flatMap { it.spendByCategory.keys }.distinct()

    for (cat in categories) {
        val series = history.mapNotNull { it.spendByCategory[cat] }
        if (series.size < 4) continue

        val median = series.median()
        val mad = series.map { abs(it - median) }.median()       // Median Absolute Deviation
        val robustZ = if (mad > 0) (current.spendByCategory[cat]!! - median) / (1.4826 * mad) else 0.0

        if (abs(robustZ) > 2.5) {
            outliers.add(Outlier(cat, robustZ, median, current.spendByCategory[cat]!!))
        }
    }
    return outliers.sortedByDescending { abs(it.zScore) }
}
```

Se usa **Mediana + MAD** en lugar de media + σ para robustez ante pocos datos y outliers extremos (típico del contexto: una quincena atípica arrastra la media y enmascara otras).

**Pronóstico simple (sin ML)**:

```kotlin
fun forecastNextQuincena(history: List<QuincenaSnapshot>): QuincenaForecast {
    val n = history.size.coerceAtMost(6)
    val recent = history.takeLast(n)

    // Media móvil ponderada (weights: 1, 2, 3, ..., n → más peso a reciente)
    val weights = (1..n).toList()
    val weightSum = weights.sum()

    val forecastedExpenses = recent.zip(weights)
        .sumOf { (snap, w) -> snap.actualExpenses * w } / weightSum

    // Intervalo de confianza: forecast ± 1.5 * MAD_histórico
    val mad = recent.map { abs(it.actualExpenses - forecastedExpenses) }.median()
    val lowerBound = forecastedExpenses - 1.5 * mad
    val upperBound = forecastedExpenses + 1.5 * mad

    return QuincenaForecast(forecastedExpenses, lowerBound, upperBound)
}
```

#### 6.3.5 Módulo E — **Concentración de deuda y utilización de crédito**

Panel dual:
- Izquierda: `DonutChart` de deuda total por wallet de crédito (absoluto).
- Derecha: barras horizontales con `% utilización vs límite`, color escalado de verde (0%) → amarillo (50%) → rojo (80%+).

Query base ya en §5.2.5 del doc.

Widget secundario: **"tiempo estimado para liquidar al ritmo actual"** calculado como:

```kotlin
val amortizationMonths = currentBalance / monthlyAveragePayment
// Si monthlyAveragePayment ≤ mensual_intereses_esperados → INFINITY (red alert)
```

#### 6.3.6 Módulo F — **Timeline quincenal del household**

Vista cronológica completa del household: cada quincena es un card con thumbnail de KPIs, permitiendo saltar a la vista detalle.

```
2026
├─ Q2 Abril  (activa)   ═ $12,340 balance   ▲ gastos +4.3%
├─ Q1 Abril  cerrada    ═ $15,200 balance   ═ normal
├─ Q2 Marzo  cerrada    ▼ -$3,400 balance   ▲▲ gastos +18%
├─ Q1 Marzo  cerrada    ═ $9,100  balance
│
2025
├─ Q2 Dic    cerrada    ▲ $22,000 balance   (aguinaldo)
├─ ...
```

### 6.4 Interacciones específicas del Fold

- **Modo dual-screen mientras se captura**: al abrir `QuickCapture` en el Fold desplegado, la pantalla izquierda mantiene el dashboard activo y la derecha muestra el formulario. Al confirmar, el dashboard se refresca en tiempo real.
- **Foldable posture**: en modo "tabletop" (half-folded a 90°), el dashboard se divide horizontalmente: gráfica en la mitad superior, controles/leyenda en la inferior. Se detecta con `WindowInfoTracker.getOrCreate(activity).windowLayoutInfo`.
- **Drag & drop entre paneles**: arrastrar un gasto de una lista y soltarlo sobre una categoría la recategoriza; soltarlo sobre un miembro cambia su atribución de beneficiario. Usa `DragAndDropTarget` de Compose 1.7.

### 6.5 Exportación e impresión

En el inner display, cada módulo analítico expone:
- **Exportar PDF** (usa `PrintAttributes.MediaSize.ISO_A4` con `PdfDocument`).
- **Exportar imagen** (captura del canvas vía `GraphicsLayer.toImageBitmap()`).
- **Compartir snapshot** a Gmail, WhatsApp, Drive vía `Intent.ACTION_SEND`.

### 6.6 Contrato de performance analítica

Métricas hard:
- Render inicial del Módulo D (análisis varianza, 12 meses de datos) < **150 ms** tras resolver la query.
- Cualquier módulo debe ejecutar sus queries principales off-main con `Dispatchers.IO` y renderizar los loaders con `placeholder(shimmer())` en < 50 ms.
- **Precomputación**: al cerrar una quincena, se calculan y materializan los snapshots agregados en la tabla `quincena_snapshot_cache` para que los módulos históricos no requieran full scans.

```sql
CREATE TABLE quincena_snapshot_cache (
  quincena_id TEXT PRIMARY KEY REFERENCES quincena(id),
  snapshot_json TEXT NOT NULL,              -- QuincenaSnapshot serializado
  generated_at INTEGER NOT NULL,
  schema_version INTEGER NOT NULL DEFAULT 1
);
```

---

## Apéndice C: Tabla de Permisos y APIs

| Permiso / API | Uso | Superficie | Obligatoriedad |
|---|---|---|---|
| `SYSTEM_ALERT_WINDOW` | Overlay de Quick Capture | Android | Opcional (onboarding lo solicita) |
| `POST_NOTIFICATIONS` | Ongoing activity + recordatorios | Android + Wear | Obligatorio |
| `FOREGROUND_SERVICE` | Servicio del overlay | Android | Obligatorio si overlay activo |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Justificación Android 14+ | Android | Obligatorio si overlay activo |
| `USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM` | Provisionar quincenas en T-3 | Android | Obligatorio |
| `RECEIVE_BOOT_COMPLETED` | Restablecer alarmas | Android | Obligatorio |
| `BIND_QUICK_SETTINGS_TILE` | Tile de quick-capture | Android | Opcional |
| `BIND_TILE_PROVIDER_SERVICE` | Wear Tiles | Wear OS | Obligatorio |
| `BIND_COMPLICATION_PROVIDER` | Complications | Wear OS | Obligatorio |
| `VIBRATE` | Haptic feedback | Todas Android | Obligatorio |
| `ACCESS_COARSE_LOCATION` | Contexto de ubicación para predicción | Android | Opcional |
| `READ_SMS` / `BIND_NOTIFICATION_LISTENER_SERVICE` | Parser de cargos bancarios | Android | Opcional con onboarding separado |
| `com.google.android.wearable.DATA_LAYER` | Sync Watch↔Phone | Wear | Obligatorio |

---

## Apéndice D: Tokens de Diseño M3 Expressive

### D.1 Colores semánticos (extienden `ColorScheme`)

```kotlin
val LightExtendedColors = ExtendedColors(
    success          = Color(0xFF1B6E3F),
    onSuccess        = Color(0xFFFFFFFF),
    successContainer = Color(0xFFB8F1C9),
    warning          = Color(0xFF8B5A00),
    warningContainer = Color(0xFFFFDE9F),
    expense          = Color(0xFFBA1A1A),
    income           = Color(0xFF0F5A2E),
    neutralBudget    = Color(0xFF5F6063),
    overBudget       = Color(0xFFB3261E),
    underBudget      = Color(0xFF2E7D32)
)
```

### D.2 Motion tokens

```kotlin
val SpringDefaults = SpringSpec<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,   // 0.75
    stiffness    = Spring.StiffnessMediumLow          // 400
)
val SpringExpressive = SpringSpec<Float>(
    dampingRatio = 0.65f,
    stiffness    = 300f
)
val MotionExpressiveFast  = 100.milliseconds
val MotionExpressiveSlow  = 400.milliseconds
```

### D.3 Shape tokens extendidos

```kotlin
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small      = RoundedCornerShape(12.dp),
    medium     = RoundedCornerShape(16.dp),
    large      = RoundedCornerShape(28.dp),
    extraLarge = MaterialShapes.Cookie12Sided     // M3E expressive shape
)
```

### D.4 Tipografía extendida

```kotlin
val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily  = RobotoFlex,
        fontSize    = 57.sp,
        lineHeight  = 64.sp,
        fontWeight  = FontWeight.W400,
        letterSpacing = (-0.25).sp,
    ),
    // Variant emphasized (peso 700) para KPIs
    displayLargeEmphasized = TextStyle(
        fontFamily  = RobotoFlex,
        fontSize    = 57.sp,
        fontWeight  = FontWeight.W700,
        fontVariationSettings = FontVariation.Settings(
            FontVariation.width(110f),
            FontVariation.weight(700)
        )
    ),
    // ...
)
```

### D.5 Density en Wear

Composables en Wear fuerzan `LocalDensity.current.copy(density = 2.0f)` en capturas donde el sistema no lo hace automáticamente (algunos devices de 1.4"), para asegurar que las áreas tappable sean ≥ 48 × 48 dp.

---

**Fin del documento complementario.**
