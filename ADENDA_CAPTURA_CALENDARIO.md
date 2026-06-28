# ADENDA — Apéndice G: Captura multi-superficie, Calendario y Señales de contexto

> Complementa el Apéndice F (`ADENDA_IA_PROACTIVA.md`) y el Apéndice E (`ADENDA_IA_ON_DEVICE.md`). Decisiones de producto tomadas con el usuario el **2026-06-28**; especificación a nivel de contrato (build-ready en su cimiento, contrato+arquitectura en el resto), lista para que otro chat la implemente.

## G.0 La idea unificadora

Tres features nuevas pedidas por el usuario —**calendario de movimientos**, **captura por voz/widget/reloj en lenguaje natural** y **ubicación+hora en cada registro**— son, en el fondo, **nuevas fuentes de captura que alimentan un mismo núcleo "movimiento propuesto → confirmar"**. Ese patrón ya existe en Feature D (§F.6): la tabla `pending_bank_capture` + chip + notificación con acciones + propose-then-confirm.

**Decisión arquitectónica central:** generalizar ese mecanismo en **una bandeja única** en vez de tres flujos paralelos. Calendario, voz/reloj y notificaciones bancarias caen todos en la misma bandeja, con una sola UI de confirmación, un solo motor de prefill (categoría modal + `RetroAttributionEngine`) y un solo receiver de acciones. **Se construye el cimiento una vez y las features se cuelgan de él.**

**Principios heredados (no negociables):** Room = fuente de verdad (offline-first), **propose-then-confirm** (la IA nunca muta directo, propone), todo **ignorable / opt-in**, no-selección = señal negativa implícita, sin push salvo evento externo. Ver [[finance-app-data-architecture]] y §F.1.

### Decisiones confirmadas con el usuario (2026-06-28)
1. **Calendario:** in-app como fuente de verdad (Room) **+ espejo opcional de una vía** hacia Google Calendar (off por default; solo si el usuario lo activa).
2. **Recurrentes al vencer:** **propose-then-confirm** (movimiento prefilado en la bandeja, monto esperado del historial) **+ recordatorios configurables por el usuario** — el usuario decide *cuánto antes* quiere el aviso, con presets (p. ej. mismo día, 1/2/3/7 días antes, **"al inicio de la quincena"**).
3. **Ubicación:** opt-in, **foreground-only** (solo al capturar, NUNCA en background), coords + nombre de lugar reverse-geocoded **on-device**.
4. **Orden de construcción:** **bandeja unificada primero**, luego colgar calendario / voz / señal de ubicación encima.

---

## G.1 CIMIENTO — Bandeja unificada de capturas (`pending_capture`)

**Construir esto primero.** Es la generalización de `pending_bank_capture` (Feature D).

### G.1.1 Modelo
- Renombrar/extender `pending_bank_capture` → **`pending_capture`** (local-only, sin FK, sobrevive app cerrada).
- Campo nuevo **`source`** enum: `BANK | CALENDAR | VOICE | WIDGET | WATCH`.
- Campos comunes (los que ya tiene la bancaria): `id`, `amount_mxn`, `concept`, `category_id?`, `wallet_id?`, `occurred_at`, `status` (`PENDING | CONFIRMED | DISMISSED`), `created_at`, más metadata de origen (`raw_text?` para voz/banco, `recurrence_id?` para calendario).
- Migración Room **v5→v6** (zona de peligro): SQL literal EXACTO de `schemas/6.json` (compilar primero, copiar de ahí); añadir a `addMigrations`; **nunca** `fallbackToDestructiveMigration`; el asset se queda en v1 (no se toca). Ver el gotcha del `user_version` del asset en CLAUDE.md. **Sugerencia:** unir en esta misma v6 las columnas de ubicación de G.4 (una sola migración).

### G.1.2 Comportamiento
- `CaptureManager` genérico (refactor de `BankCaptureManager`): `ingest(source, parsed)` resuelve wallet/categoría/atribución por defecto e inserta `PENDING`; `confirm(id)` arma el `Expense` POSTED con atribución del `RetroAttributionEngine` (fallbacks: beneficiary=equalSplit, payer=dueño del wallet; bps normalizados a 10000) y marca `CONFIRMED`; `dismiss(id)` = `DISMISSED` (señal negativa).
- Una sola UI de confirmación: chip en el dashboard (ya existe el `SmartSuggestionsCarousel`) + notificación con acciones [Registrar]/[Editar]/[Descartar]. El [Editar] que hoy falta en D (abre captura prefilada) se construye aquí una vez para todos los `source`.
- El editor de % de atribución ya está extraído y compartido (`ui/capture/AttributionShareEditor.kt`).

### G.1.3 Refactor de Feature D
Feature D pasa a ser "el productor con `source=BANK`". `BankNotificationParser` se mantiene; solo cambia que `BankCaptureManager.ingest` delega al `CaptureManager` genérico. Verificar que el flujo bancario sigue idéntico tras el refactor.

---

## G.2 Calendario de movimientos

### G.2.1 Fuente de verdad + espejo
- **Room es la verdad.** Entidades nuevas: `recurrence_rule` (la regla) y `calendar_event` (ocurrencia materializada / evento manual).
- **Espejo opcional de una vía** a Google Calendar (`CalendarContract`), off por default, toggle en Perfil. Solo entonces se pide `WRITE_CALENDAR`. Nunca two-way (evita ensuciar el calendario personal de Norma y mantener el modelo offline-first limpio). El espejo es best-effort, como los deletes remotos del sync.

### G.2.2 Regla de recurrencia
- `recurrence_rule`: `frequency` (`SEMANAL | QUINCENAL | MENSUAL | ANUAL`), `interval` (cada N), `day_of_month?` (para mensual; QUINCENAL = días 1 y 16 por convención mexicana), `start_date`, `end_date?`, `expected_amount_mxn` (promedio histórico — los recurrentes **varían de monto**: luz, agua), `concept_canonical`, `category_id`, atribución por defecto.
- **Materialización:** la regla proyecta ocurrencias esperadas. **Tensión a resolver explícitamente:** el presupuesto se organiza por **quincena**, pero un recurrente mensual cruza quincenas — la proyección debe mapear cada ocurrencia a la quincena que le toca (p. ej. "renta mensual día 1" → cae en la 1ª quincena del mes).

### G.2.3 Origen de los eventos
- **Inferido (bajo autorización):** el `ProactiveSuggestionEngine` ya detecta recurrentes (camino "inicio de quincena"). Se ofrece como **sugerencia** "¿Crear recordatorio recurrente para «Colegiatura Santi»?"; si el usuario acepta, se crea la `recurrence_rule`. Nunca se crea solo sin autorización.
- **Manual:** el usuario crea un evento desde cero.
- **Editable** en ambos casos: fecha inicio, fecha fin, frecuencia, intervalo, monto esperado, categoría, atribución.

### G.2.4 Recordatorios + confirmación (decisión #2)
- **Recordatorio configurable:** el usuario elige *cuánto antes* (presets: mismo día / 1 / 2 / 3 / 7 días antes / **"al inicio de la quincena"**). Lead time por evento, con un default global en Perfil. WorkManager (`PeriodicWorkRequest`, piso 15 min; NO `SCHEDULE_EXACT_ALARM` — penalizado por Play, §F.9).
- **Al vencer:** se inserta un `pending_capture` con `source=CALENDAR`, `amount_mxn = expected_amount` y `recurrence_id` → notificación + chip → **propose-then-confirm** (el usuario confirma/corrige el monto real antes de que sea gasto POSTED). El recordatorio "avisa antes"; la confirmación "registra al ocurrir". Son dos momentos distintos del mismo evento.

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

### G.4.2 Ubicación (decisión #3)
- **Opt-in + foreground-only:** se captura **solo en el momento de registrar** (gesto del usuario), NUNCA en background. Esto evita la prohibición de Play Store sobre background location (§F.9 lo evitó a propósito; esta reversión es segura SOLO con esta restricción).
- **Datos:** `latitude`, `longitude`, `place_label` (reverse-geocoded **on-device** vía `Geocoder`; si falla, solo coords). Migración Room **v5→v6** sobre `expense` (idealmente unida con G.1.1).
- **Histórico:** los **793 gastos sembrados se quedan sin ubicación** (solo los nuevos la tienen). Aceptado.
- **Privacidad/seguridad:** dato financiero + ubicación de Norma = sensible. Vive en Room, sincroniza solo a *su* Firestore (ya privado por household). Divulgación clara al pedir el permiso. Ver `SECURITY_REMEDIATION.md`.

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
