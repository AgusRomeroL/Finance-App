# ADENDA — Apéndice G: Captura multi-superficie, Calendario y Señales de contexto

> Complementa el Apéndice F (`ADENDA_IA_PROACTIVA.md`) y el Apéndice E (`ADENDA_IA_ON_DEVICE.md`). Decisiones de producto tomadas con el usuario el **2026-06-28**; especificación a nivel de contrato (build-ready en su cimiento, contrato+arquitectura en el resto), lista para que otro chat la implemente.

## G.0 La idea unificadora

Tres features nuevas pedidas por el usuario —**calendario de movimientos**, **captura por voz/widget/reloj en lenguaje natural** y **ubicación+hora en cada registro**— son, en el fondo, **nuevas fuentes de captura que alimentan un mismo núcleo "movimiento propuesto → confirmar"**. Ese patrón ya existe en Feature D (§F.6): la tabla `pending_bank_capture` + chip + notificación con acciones + propose-then-confirm.

**Decisión arquitectónica central:** generalizar ese mecanismo en **una bandeja única** en vez de tres flujos paralelos. Calendario, voz/reloj y notificaciones bancarias caen todos en la misma bandeja, con una sola UI de confirmación, un solo motor de prefill (categoría modal + `RetroAttributionEngine`) y un solo receiver de acciones. **Se construye el cimiento una vez y las features se cuelgan de él.**

**Principios heredados (no negociables):** Room = fuente de verdad (offline-first), **propose-then-confirm** (la IA nunca muta directo, propone), todo **ignorable / opt-in**, no-selección = señal negativa implícita, sin push salvo evento externo. Ver [[finance-app-data-architecture]] y §F.1.

### Decisiones confirmadas con el usuario (2026-06-28)
1. **Calendario:** in-app como fuente de verdad (Room) **+ espejo opcional de una vía** hacia Google Calendar (off por default; solo si el usuario lo activa).
2. **Recurrentes:** se modelan como **gastos PLANNED** materializados desde `recurrence_template` (no `pending_capture`; ver decisión refinada en §G.2.0). Confirmar = flip PLANNED→POSTED (monto real ajustable) **+ recordatorios configurables** — el usuario decide *cuánto antes*, con presets (mismo día, 1/2/3/7 días antes, **"al inicio de la quincena"**). Budget-aware: los PLANNED se reflejan en "Disponible para gastar".
3. **Ubicación:** señal oportunista (nunca obligatoria) con **tres niveles opt-in y permisos distintos** — *Completa/persistente* (background location), *Solo al usar* (while-in-use, **default recomendado**), *Ninguna*. Foreground real solo en captura in-app + **widget vía Activity trampolín**; las fuentes background (reloj/banco) obtienen ubicación al confirmar (salvo nivel persistente). Campo `location_source` para que el significado nunca sea ambiguo. Detalle en §G.4.2.
4. **Orden de construcción:** **bandeja unificada primero**, luego colgar calendario / voz / señal de ubicación encima.

---

## G.1 CIMIENTO — Bandeja unificada de capturas (`pending_capture`)

**Construir esto primero.** Es la generalización de `pending_bank_capture` (Feature D).

### G.1.1 Modelo
- Renombrar/extender `pending_bank_capture` → **`pending_capture`** (local-only, sin FK, sobrevive app cerrada).
- Campo nuevo **`source`** enum: `BANK | CALENDAR | VOICE | WIDGET | WATCH`.
- Campos comunes (los que ya tiene la bancaria): `id`, `amount_mxn`, `concept`, `category_id?`, `wallet_id?`, `occurred_at`, `status` (`PENDING | CONFIRMED | DISMISSED`), `created_at`, más metadata de origen (`raw_text?` para voz/banco, `recurrence_id?` para calendario), y ubicación opcional `latitude?`/`longitude?`/`place_label?`/`location_source` (ver §G.4.2).
- Migración Room **v5→v6** (zona de peligro): SQL literal EXACTO de `schemas/6.json` (compilar primero, copiar de ahí); añadir a `addMigrations`; **nunca** `fallbackToDestructiveMigration`; el asset se queda en v1 (no se toca). Ver el gotcha del `user_version` del asset en CLAUDE.md. **Sugerencia:** unir en esta misma v6 las columnas de ubicación de G.4 (una sola migración).

### G.1.2 Comportamiento
- `CaptureManager` genérico (refactor de `BankCaptureManager`): `ingest(source, parsed)` resuelve wallet/categoría/atribución por defecto e inserta `PENDING`; `confirm(id)` arma el `Expense` POSTED con atribución del `RetroAttributionEngine` (fallbacks: beneficiary=equalSplit, payer=dueño del wallet; bps normalizados a 10000) y marca `CONFIRMED`; `dismiss(id)` = `DISMISSED` (señal negativa).
- Una sola UI de confirmación: chip en el dashboard (ya existe el `SmartSuggestionsCarousel`) + notificación con acciones [Registrar]/[Editar]/[Descartar]. El [Editar] que hoy falta en D (abre captura prefilada) se construye aquí una vez para todos los `source`.
- El editor de % de atribución ya está extraído y compartido (`ui/capture/AttributionShareEditor.kt`).

### G.1.3 Refactor de Feature D
Feature D pasa a ser "el productor con `source=BANK`". `BankNotificationParser` se mantiene; solo cambia que `BankCaptureManager.ingest` delega al `CaptureManager` genérico. Verificar que el flujo bancario sigue idéntico tras el refactor.

---

## G.2 Calendario + recurrencia — PLAN DE IMPLEMENTACIÓN (modelo PLANNED, decidido 2026-06-28)

### G.2.0 Modelo elegido y hallazgos de la arquitectura existente

**Decisión (Agustín, 2026-06-28): modelo de gastos PLANNED**, no `pending_capture source=CALENDAR`. La arquitectura original YA preveía esto y estaba a medio diseñar:
- **`recurrence_template`** (entidad del esquema **v1**, ya en el asset, con FK a household/categoría/wallet) — **dormida**: registrada en `@Database` pero **sin DAO y sin impl** del `RecurrenceRepository` (la interfaz existe). Modelo rico: `cadence`, `cadence_detail` (JSON), `default_amount_mxn`, `default_beneficiary_ids` (JSON), `default_payer_split` (JSON), `next_expected_date`, `is_active`, `confidence_score`, `learned_from_expense_ids`.
- **`ExpenseStatus = PLANNED → POSTED → RECONCILED`** (`core/model/Enums.kt`). PLANNED = "presupuestado, aún no ejecutado; se muestra como outline". El `ExpenseDao` **ya tiene** la query `status='PLANNED'`. El doc de la entidad dice literal: *"Al activar cada quincena, el sistema materializa instancias PLANNED desde plantillas activas."*
- **`RecurrenceCadence`**: `QUINCENAL_FIRST | QUINCENAL_SECOND | QUINCENAL_EVERY | MONTHLY_SPECIFIC_HALF | BIMONTHLY | CUSTOM_CRON`. → **la tensión "mensual ↔ quincena" queda resuelta de origen**: la cadencia se expresa nativamente en quincenas.

**Por qué PLANNED gana:** una app de presupuesto **quincenal** debe reflejar los pagos fijos conocidos en "Disponible para gastar" ANTES de pagarlos (es el sentido de planear el periodo). El gasto PLANNED es ledger-native y budget-aware; confirmar = flip PLANNED→POSTED (no un insert nuevo). Reusa toda la pieza ya diseñada.

**Relación con la bandeja unificada (G.1):** el calendario NO produce filas `pending_capture` — su "propuesta" ES el gasto PLANNED. La bandeja/chip del dashboard unifica a nivel de **presentación**: muestra *PLANNED que vencen ahora* + *pending_captures (banco/voz)* con la misma UI de confirmar. Bajo este modelo, `source=CALENDAR` queda **reservado y sin uso** (se deja en el enum por si una variante futura lo necesita).

### G.2.1 Cambios de esquema
- **`recurrence_template`: NO necesita migración** (ya existe en v1). Solo despertar DAO + impl.
- **Migración Room v6→v7 — `expense.recurrence_template_id TEXT` (nullable):** provenance PLANNED→plantilla, para (a) **idempotencia** de la materialización, (b) editar/saltar una ocurrencia, (c) recomputar al cambiar la plantilla. `ALTER TABLE ... ADD COLUMN` simple (patrón de `MIGRATION_4_5`); no aparece como CREATE en `schemas/7.json`; el asset se queda en v1. Índice opcional sobre `(recurrence_template_id)` si las queries lo piden.
- **Eventos manuales sin entidad nueva:** one-off = gasto PLANNED con fecha y sin `recurrence_template_id`. Recurrente manual = un `recurrence_template`. No se crea `calendar_event`.

### G.2.2 Fases de construcción

**Fase 0 — Despertar la capa de recurrencia (sin UI).**
- `RecurrenceTemplateDao` (nuevo): `observeActive(householdId)`, `getById`, `getActiveForCadence`, `insert/update/delete`, `pause/resume`.
- `RecurrenceRepositoryImpl` (Room, en `data/repository/impl/`) — cableado como repo PÚBLICO en `BudgetApplication` (igual que los demás: público = Room).
- Registrar el DAO en `BudgetDatabase`; **migración v6→v7** (col. provenance).
- *Verificable:* insertar una plantilla, `observeActive` la emite; fresh install v1→v7 sin crash, 793 intactos, asset==golden.

**Fase 1 — Materialización.**
- `RecurrenceMaterializer` (use-case puro-ish): dado `templates activos` + una `quincena`, produce gastos `status=PLANNED` (+ atribuciones desde `default_beneficiary_ids`/`default_payer_split`, montos desde `default_amount_mxn`) para las plantillas cuya cadencia cae en esa quincena. **Idempotente** por `(recurrence_template_id, quincena_id)`.
- **Trigger:** al activarse una quincena (hook en el rollover de `QuincenaRepository`/arranque). Más un "materializar ahora" manual para pruebas.
- Tabla de proyección cadencia→quincena (ver G.2.3).

**Fase 2 — Confirmación PLANNED→POSTED.**
- `ExpenseRepository.confirmPlanned(expenseId, actualAmountMxn?)`: flip `status=POSTED`, ajusta el monto real (los recurrentes **varían**: luz/agua) y **re-escala los `share_amount_mxn`** de las atribuciones al nuevo monto (los bps no cambian). Encola sync.
- Surfaced en el chip del dashboard (PLANNED que vencen) y en la pantalla de calendario. "Posponer" = mantener PLANNED, re-agendar recordatorio.

**Fase 3 — Recordatorios (WorkManager).**
- `ReminderWorker` (`PeriodicWorkRequest`, piso 15 min; **NO** `SCHEDULE_EXACT_ALARM` — penalizado, §F.9). Busca PLANNED cuya `(fecha − lead) ≤ ahora` y no notificados → notificación con acciones [Confirmar]/[Editar]/[Posponer] (reusa la infra de notificación + `BankCaptureActionReceiver` generalizado).
- **Lead time configurable (decisión #2):** default global en `SettingsRepository` (DataStore) + override por plantilla en `cadence_detail` JSON (`reminder_lead_days` o `"QUINCENA_START"`). Presets UI: mismo día / 1 / 2 / 3 / 7 días / **"al inicio de la quincena"**. Recordar (avisa antes) y confirmar (registra al ocurrir) son dos momentos del mismo PLANNED.

**Fase 4 — UI Calendario.**
- Nuevo destino de navegación **`CALENDAR`** (reusar el placeholder `LEDGER` o añadir ruta en `BudgetNavGraph` + entrada en rail/bottom-nav). Vista timeline/mes de gastos PLANNED (+ POSTED recientes para contexto), por fecha.
- Cada PLANNED: concepto, monto esperado, fecha, [Confirmar]/[Editar]/[Posponer].
- **Gestión de plantillas:** lista de activas; crear/editar (selector de cadencia, monto, fecha inicio/fin, lead de recordatorio, **splits con el `AttributionShareEditor` compartido**); pausar/reanudar/eliminar.
- Manual one-off: crear un PLANNED con fecha (sin plantilla).
- **Animaciones Material Expressive obligatorias** (resortes espaciales) en aparición/confirmación/expansión — mandato transversal de CLAUDE.md.

**Fase 5 — Inferencia bajo autorización.**
- Detector sobre recurrencia (reusa/extiende `ProactiveSuggestionEngine`, que ya halla recurrentes en el camino "inicio de quincena") → sugerencia **"¿Crear plantilla recurrente para «Colegiatura Santi»?"**. Al aceptar, construye el `recurrence_template` con `confidence_score`, `learned_from_expense_ids` y splits default vía `RetroAttributionEngine`. **Nunca** crea plantilla sin autorización explícita (propose-then-confirm).

**Fase 6 — Espejo Google Calendar (opt-in, una vía).**
- Toggle en Perfil + permiso `WRITE_CALENDAR` (solo si se activa). `CalendarContract` writer a un calendario dedicado; guardar el `external_event_id` (en `cadence_detail` JSON o columna futura) para update/delete. **Best-effort**, una vía (nunca two-way — no ensuciar ni leer el calendario personal de Norma). Última fase, no bloquea las anteriores.

### G.2.3 Proyección cadencia → quincena
| Cadencia | Materializa en |
|---|---|
| `QUINCENAL_FIRST` | 1ª quincena de cada mes (días 1-15) |
| `QUINCENAL_SECOND` | 2ª quincena (16-fin) |
| `QUINCENAL_EVERY` | Ambas quincenas |
| `MONTHLY_SPECIFIC_HALF` | Una quincena específica/mes (`cadence_detail.day_of_month` decide cuál) |
| `BIMONTHLY` | Cada 2 meses (luz/agua) — track del último periodo materializado |
| `CUSTOM_CRON` | **Diferido** — empezar sin esto; parsear cron es trabajo posterior |

`cadence_detail` JSON: `{ "day_of_month": 15, "drift_tolerance_days": 3, "reminder_lead_days": 2 }`.

### G.2.4 Budget-awareness (pendiente de UI)
Los PLANNED del periodo deben reflejarse en "Disponible para gastar" (reservar lo previsto). Decisión de UI futura: mostrarlos como **reservado** separado vs **restado** directo. El dato ya está; la pieza de UI se define al llegar a Fase 4.

### G.2.5 Sincronización
`recurrence_template` es entidad **sincronizada** (FK, datos del hogar) → `RecurrenceRepositoryImpl` encola outbox; faltará un impl Firestore + manejo en `RemotePullSync` (best-effort; hoy `PERMISSION_DENIED`). Los PLANNED sincronizan como gastos normales. No bloquea las fases (offline-first).

### G.2.6 Pendientes abiertos
- UI de budget-awareness (reservado vs restado) — Fase 4.
- "Saltar/editar esta ocurrencia" sin tocar la plantilla (usa `recurrence_template_id` + un flag de override en el PLANNED).
- `CUSTOM_CRON` (diferido).
- `RECONCILED` (conciliación bancaria) queda **fuera** de este bloque.

### G.2.7 ESTADO DE IMPLEMENTACIÓN (2026-06-28)

> Para quien continúe: **el cimiento G.1 + las Fases 0/1/2 están CONSTRUIDAS, verificadas en emulador y commiteadas.** La siguiente es **Fase 3**.

- **G.1 — Bandeja unificada `pending_capture` HECHA (commit `5ed97de`).** Generaliza `pending_bank_capture` (Feature D) en `pending_capture` con `source` (BANK|CALENDAR|VOICE|WIDGET|WATCH) + columnas de ubicación. **Migración Room v5→v6** (recreate-table: campos de banco nullable, `merchant`→`concept`). `BankCaptureManager` ganó `enqueue()` genérico. **Nota:** bajo el modelo PLANNED, el calendario NO usa `pending_capture` (su propuesta es el PLANNED); `source=CALENDAR` queda reservado.
- **Fase 0 — Capa de recurrencia HECHA (commit `67286fc`). SIN migración** (la tabla `recurrence_template` y `expense.recurrence_template_id` YA estaban en el esquema v1). `RecurrenceTemplateDao` + `RecurrenceRepositoryImpl` (Room) cableado como `app.recurrenceRepository`.
- **Fase 1 — Materialización HECHA (commit `a72c3ab`).** `data/recurrence/RecurrenceMaterializer.kt`: crea gastos `status=PLANNED` desde plantillas activas según cadencia, con atribuciones de los splits default (fallback: beneficiary=equalSplit, payer=dueño wallet). **Idempotente** por `(recurrence_template_id, quincena_id)` vía `ExpenseDao.countForTemplateInQuincena`. Trigger de arranque en `BudgetApplication.materializeRecurringForActiveQuincena()`.
- **Fase 2 — Confirmación PLANNED→POSTED HECHA (commit `3ded134`).** `ExpenseRepository.confirmPlanned(expenseId, actualAmountMxn?)`: flip a POSTED, ajusta monto real y **re-escala** los `share_amount_mxn` (bps no cambian). `postPlannedExpense` delega en él. **Hallazgo:** `observePlannedTotal(quincenaId)` ya existía (budget-awareness con base). **Nota honesta:** el "ajuste de saldo de wallet" que mencionan los docs de interfaz NO está implementado en ningún lado del repo.

**SIGUIENTE = Fase 3 (§G.2.2):** `ReminderWorker` (WorkManager `PeriodicWorkRequest`, piso 15 min, **NO** `SCHEDULE_EXACT_ALARM`): busca PLANNED cuya `(fecha − lead) ≤ ahora` y no notificados → notificación con acciones [Confirmar]/[Editar]/[Posponer] (reusa la infra de notificación + `BankCaptureActionReceiver` + `confirmPlanned`). Lead configurable: default global en `SettingsRepository` (DataStore) + override por plantilla en `cadence_detail` (`reminder_lead_days` o `"QUINCENA_START"`). Luego Fases 4 (UI calendario) → 5 (inferencia bajo autorización) → 6 (espejo Google Calendar).

---

## G.3 Captura en lenguaje natural (voz / widget / reloj)

### G.3.1 Qué es
Es el **pipeline de IA reactiva (Apéndice E) en dirección de ESCRITURA**. Hoy `LedgerRagUseCase → intent JSON → IntentDispatcher` solo *lee* y está stubbeado. Se agrega un **intent de escritura**: `{action: "ADD_EXPENSE" | "ADD_INCOME" | "ADD_CALENDAR_EVENT", amount, concept, category?, date?, ...}` (extender `assets/ai/intent_schema.json`).

### G.3.2 Flujo seguro
Voz/texto NL → Gemini Nano on-device (`AiCoreManager`, ya cableado por Capa 3) traduce a intent JSON → se construye un **movimiento propuesto** que cae en `pending_capture` (`source=VOICE|WIDGET|WATCH`, `raw_text` = lo dictado) → notificación de confirmación → el usuario corrige huecos (categoría/atribución ya prefilados por los engines) y confirma. **El LLM nunca muta directo.**
- **Offline:** Gemini Nano es on-device → funciona sin red (coherente con offline-first).
- **Fallback determinista:** parser regex ("$X en Y", "gasté N en …") cuando Nano no esté, espejo del `BankNotificationParser`.
- **Reúso:** `SpeechRecognizerController` (es-MX) ya existe en la barra de búsqueda.

### G.3.3 Superficies
- **Widget (Glance):** campo/botón de captura por voz en la pantalla de inicio → manda el NL a la app.
- **Reloj (Pixel Watch):** **DEPENDENCIA DURA — exige habilitar el módulo `wear/`** (hoy no compila: no está en `settings.gradle.kts` ni tiene `AndroidManifest`). Es el trabajo cross-cutting §F.7. El reloj capta voz → manda el transcript al teléfono vía **Wear Data Layer** → el teléfono hace el parsing pesado con Gemini Nano (el reloj NO corre Nano) → confirmación. **Habilitar Wear viene incluido con esta feature.**
- **Notificación de confirmación:** dado que la captura nace desde widget/reloj (lejos de la pantalla principal), el usuario eventualmente confirma vía la notificación con acciones (mismo receiver de G.1).

---

## G.4 Señales de contexto: ubicación + hora en cada registro

### G.4.1 Hora
Ya existe (`Expense.occurredAt`, epoch millis). Lo nuevo es **exponerla/editarla** mejor en el detalle y **usarla como señal** (el motor proactivo y la atribución ya usan hora/día).

### G.4.2 Ubicación — el problema foreground vs las fuentes background (decisión #3, refinada 2026-06-28)

**El choque (descubierto al madurar):** "foreground-only" NO funciona desde widget ni reloj, porque esas superficies son *background* por definición. En Android, la ubicación *while-in-use* solo se concede con una **Activity visible** o un **foreground service tipo `location`**. Un tap de widget dispara un `BroadcastReceiver` (sin Activity); el reloj delega al teléfono que recibe en un `WearableListenerService` (background); y la captura bancaria (D) corre en un `NotificationListenerService` (background). **De las 5 fuentes de la bandeja, solo la captura in-app está en foreground real.**

**La ubicación es señal oportunista, NUNCA obligatoria** (columna nullable → degrada bien). Se resuelve con **tres niveles opt-in, con permisos distintos** (el usuario elige en Perfil; default = intermedio):

| Nivel | Permiso | Qué obtiene |
|---|---|---|
| **Completa / persistente** | `ACCESS_FINE/COARSE` + `ACCESS_BACKGROUND_LOCATION` | Incluso capturas que nacen en background (reloj, banco) obtienen fix en su momento. El más pesado y vigilado por Play Store. |
| **Solo al usar** *(default recomendado)* | `ACCESS_FINE/COARSE` *while-in-use* (sin background) | Foreground + widget-trampolín → fix fresco. Capturas background → ubicación al **confirmar** (que es foreground). |
| **Ninguna** | — | Cero ubicación. |

**Estrategia por fuente:**

| Fuente | ¿Foreground? | Ubicación |
|---|---|---|
| **In-app** (sheet abierto) | Sí | Fix al momento. Caso común. |
| **Widget** | No, pero → | **Activity trampolín** (translúcida/invisible): el tap abre una Activity que toma el fix y sigue. Cuenta como interacción de usuario → foreground. **Confirmado: sí vale la pena el trampolín.** |
| **Reloj / Banco / Widget-silencioso** | No (imposible) | Nivel persistente → fix en su momento. Nivel "solo al usar" → ubicación al confirmar. |

**Matiz honesto:** `getLastLocation()` desde background puro **sin** permiso persistente devuelve `null` en Android 10+. Por eso, en el nivel "solo al usar", las capturas de reloj/banco reciben su ubicación **al confirmar** (foreground), no al crearse. El nivel persistente es justo lo que habilita ubicación en el instante background.

**`location_source`** (campo en `pending_capture`/`expense`): `CAPTURE | CONFIRM | MANUAL | NONE` — para que el significado nunca sea ambiguo. Al confirmar solo se **auto-adjunta** la ubicación si la confirmación cae dentro de una **ventana corta** (~15 min del evento); si no, queda `NONE` y el usuario puede tocar "añadir ubicación" (→ `MANUAL`). Evita la mentira de "ubicación del gasto = donde confirmaste horas después".

- **Datos:** `latitude`, `longitude`, `place_label` (reverse-geocoded **on-device** vía `Geocoder`; si falla, solo coords), `location_source`. Migración Room **v5→v6** sobre `expense` (unida con G.1.1).
- **Histórico:** los **793 gastos sembrados se quedan sin ubicación** (`location_source=NONE`). Aceptado.
- **Privacidad/seguridad:** dato financiero + ubicación de Norma = sensible. Vive en Room, sincroniza solo a *su* Firestore (ya privado por household). Divulgación clara por nivel al pedir el permiso (el nivel persistente exige el flujo de Play para background location). Ver `SECURITY_REMEDIATION.md` y la reversión justificada de §F.9 (que evitó ubicación por la prohibición de *background* — los niveles in-app/while-in-use NO caen en esa prohibición).

### G.4.3 Doble valor (ambos pedidos por el usuario)
1. **Señal de inferencia:** la ubicación junto a concepto/hora/día ayuda al LLM y a los engines a adivinar categoría/concepto ("OXXO cerca de casa" → Despensa). Potencia Feature D, la captura por voz (G.3) y el motor proactivo (C).
2. **Ayuda-memoria:** ver lugar+hora en el detalle del gasto deja al usuario reconstruir gastos capturados sin detalle (clusterizar por lugar es un futuro natural).

---

## G.5 Orden de construcción

1. **Bandeja unificada `pending_capture` (G.1)** — cimiento; incluye el refactor de Feature D y el [Editar] genérico. Migración v5→v6 (con columnas de ubicación de G.4 unidas).
2. **Calendario + recurrencia (G.2)** — no depende de Wear; la detección ya está medio hecha en el motor proactivo.
3. **Señal de ubicación (G.4)** — pequeña una vez hecha la migración; potencia a todos los demás.
4. **Captura NL voz/widget/reloj (G.3)** — la más grande; **trae consigo la activación del módulo Wear (§F.7)**.

## G.6 Pendientes abiertos (para validar con uso real)
- Mapeo exacto recurrencia-mensual ↔ quincena (G.2.2).
- Modelo RRULE pragmático vs iCalendar completo (hoy: enum + intervalo + día del mes).
- Reverse-geocoding on-device vs sin nombre (latencia/cobertura del `Geocoder` en MX).
- Si el intent de escritura del LLM necesita su propio system prompt + golden suite (Apéndice E.8 extendida).
