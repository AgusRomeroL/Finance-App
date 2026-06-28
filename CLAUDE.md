# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Qué es este proyecto

App Android de **presupuesto familiar quincenal** para un hogar mexicano concreto (un solo household por instalación, MXN, español, zona horaria `America/Mexico_City`). Nace de hacer ingeniería inversa a un Excel real de 33 hojas (ene-2025 → jun-2026) y reemplazarlo. Target primario: **Pixel 9 Pro Fold** (layout adaptativo plegable) + módulo **Wear OS**.

La especificación viva del producto está en cuatro documentos markdown en la raíz — **léelos antes de cambios de diseño de fondo**: `ANALISIS_MAESTRO.md` (consolidado), `ESPECIFICACION_PRESUPUESTO_APP.md` (modelo de datos + DDL), `ESPECIFICACION_UX_HARDWARE_APP.md` (UX/foldable/Wear), `ADENDA_IA_ON_DEVICE.md` (capa de IA).

## Comandos

**Gotcha crítico de toolchain:** el `java` del PATH suele ser JDK 1.8, con el que Gradle/AGP fallan. `gradle.properties` ya fija `org.gradle.java.home=C:/Program Files/Android/Android Studio/jbr` (JBR 21), así que **usa siempre el wrapper** `./gradlew.bat` (Windows) y NO invoques `gradle` global. Las cmdline-tools del SDK (`sdkmanager`, `avdmanager`) también requieren Java 17+: exporta `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"` antes de usarlas.

```bash
# Compilar solo Kotlin (verificación rápida, la usan los flujos de trabajo)
./gradlew.bat :app:compileDebugKotlin

# Construir el APK debug -> app/build/outputs/apk/debug/app-debug.apk
./gradlew.bat :app:assembleDebug

# SDK / adb / emulador (rutas; adb NO está en el PATH)
SDK="$LOCALAPPDATA/Android/Sdk"; ADB="$SDK/platform-tools/adb.exe"; EMU="$SDK/emulator/emulator.exe"

# Crear/arrancar un AVD foldable (perfil "7.6in Foldable", API 34, google_apis)
"$EMU" -avd FinanceFold -no-snapshot -gpu auto -no-boot-anim

# Instalar y lanzar
"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" shell am start -n mx.budget/.MainActivity

# Inspeccionar la DB en el dispositivo (app debuggable -> run-as)
"$ADB" shell 'run-as mx.budget sqlite3 /data/data/mx.budget/databases/budget.db "PRAGMA user_version; SELECT COUNT(*) FROM expense;"'
```

**No hay tests** en el repo (sin `src/test` ni `src/androidTest`); la spec menciona una "golden suite" de IA pero no está implementada. No hay configuración de lint personalizada ni CI.

### Pipeline de datos (Python)

La base sembrada `app/src/main/assets/budget_database.db` se genera por ETL desde el Excel, no a mano:

```bash
python scripts/etl/excel_to_room_etl.py --excel "<ruta.xlsx>" --output app/src/main/assets/budget_database.db
python scripts/verify_db.py   # renombra household.id (UUID -> "default_household") propagando FKs
python scripts/seed_firebase.py  # sube la DB a Firestore via firebase-admin (requiere service-account.json local)
```
Las reglas que reclasifican la "mugre" del Excel (typos, beneficiarios embebidos en el concepto, categorías mal etiquetadas) viven en `scripts/etl/attribution_rules.json`. El ETL imprime caracteres unicode (`═`) que **crashean en consolas Windows cp1252** — corre con `PYTHONUTF8=1` o `chcp 65001`.

## Arquitectura

### Stack
**Kotlin 2.0.21** + Jetpack Compose (compose-bom **2024.10.01** → Compose 1.7.5 / Material 3 **1.3.1**), Room 2.6.1 + KSP (2.0.21-1.0.28), Firebase (Firestore/Auth anónima/Analytics), AICore (Gemini Nano on-device), Wear Data Layer. Compose se compila con el plugin **`org.jetbrains.kotlin.plugin.compose`** (NO `composeOptions.kotlinCompilerExtensionVersion`, que se quitó en la migración a Kotlin 2.0). Libs de UI añadidas para el rediseño: **`material3-adaptive*` 1.0.0** (`NavigationSuiteScaffold`, `SupportingPaneScaffold`/`PaneExpansionState` — disponibles pero hoy se prefieren primitivas custom por estabilidad), `material3-window-size-class`, `material-icons-extended`, **MDC** (`com.google.android.material`, solo para `MaterialColors.harmonize`), **DataStore** (`datastore-preferences`, toggle de color dinámico). Nota: NO se usa `MaterialExpressiveTheme` (es M3 1.4/1.5); el tema usa `MaterialTheme` estable. **DI manual sin Hilt** (Hilt está en dependencias pero no se usa): `BudgetApplication` es el contenedor que construye DB, repos, `settingsRepository` (DataStore) y arranca el sync; resuelve `householdId` e `initialDynamicColor` de forma síncrona al arrancar; `MainActivity` tiene las `ViewModelFactory`. namespace/applicationId `mx.budget`, minSdk 31, target/compileSdk 34, un solo módulo Gradle `:app` (el dir `wear/` existe pero **no está en `settings.gradle.kts` ni tiene AndroidManifest** — no se compila).

### Capa de datos — offline-first (DECISIÓN ARQUITECTÓNICA CLAVE)
**Room es la única fuente de verdad. Firestore es una réplica en la nube que se sincroniza, no la fuente directa.** Toda escritura es local primero (offline); al haber red se sube a Firestore.

- **Repos:** interfaces en `data/repository/`, impls Room en `data/repository/impl/*Impl.kt`, impls Firestore en `data/remote/*Firestore.kt` (implementan las mismas interfaces). `BudgetApplication` cablea los repos PÚBLICOS a las impls **Room**; las impls Firestore se guardan aparte como `remote*Repository` y son el "lado nube" del sync. (Históricamente esto estaba al revés y cableaba todo a Firestore — no lo reviertas.)
- **Sync push:** outbox `sync_queue` (tabla + `SyncQueueDao`). Cada escritura por repo encola una fila; `data/sync/SyncManager.kt` observa conectividad (`ConnectivityManager`) y drena la cola hacia los repos Firestore.
- **Sync pull:** `data/sync/RemotePullSync.kt` registra `addSnapshotListener` sobre las subcolecciones de Firestore y refleja cambios en Room. **Anti-eco crítico:** el pull escribe vía DAO directo (`*.upsert`), NUNCA por los repos (que encolarían y crearían un bucle push↔pull).
- **Atribución de gastos (modelo central):** cada `Expense` tiene dos particiones independientes en `expense_attribution` — `role="BENEFICIARY"` (quién consume) y `role="PAYER"` (quién paga), cada una en **basis points que suman exactamente 10000** (=100%). `ExpenseRepositoryImpl.insertWithAttributions` escribe gasto + atribuciones + outbox en un `db.withTransaction {}`.
- **Estructura Firestore:** `households/{householdId}/{expenses|categories|members|wallets|quincenas}`, atribuciones como subcolección bajo cada gasto.

### Room: esquema, asset precargado y migraciones (zona de peligro)
- La DB se carga con `createFromAsset("budget_database.db")`. El asset contiene el **schema v1** (tablas originales, sin `sync_queue` ni `concept_canonical`). En código el `@Database` está en **v3** y corren en cadena `MIGRATION_1_2` (añade `sync_queue`) y `MIGRATION_2_3` (añade `expense.concept_canonical` + la tabla local `attribution_review`, para la capa de IA proactiva — ver `ADENDA_IA_PROACTIVA.md`). Los schemas exportados están en `app/schemas/`.
- **GOTCHA del `user_version` del asset (zona de peligro real):** el asset DEBE declarar `PRAGMA user_version = 1`. Si está en `0`, el `SQLiteOpenHelper` de Android llama `onCreate` (no `onUpgrade`) en instalación fresca, y `createAllTables` de Room intenta crear índices del schema actual (p. ej. sobre `expense.concept_canonical`) sobre la tabla `expense` preexistente del asset que NO tiene esa columna → **crash al primer arranque**. El asset venía en `0` (un bug latente: `onCreate` hacía silenciosamente el trabajo de `MIGRATION_1_2`); se corrigió a `1` para que Room corra las migraciones de verdad. Si regeneras el asset con el ETL, fija `PRAGMA user_version=1` antes de commitearlo.
- **NO uses `fallbackToDestructiveMigration()`** — borraría los 793 gastos sembrados. Cualquier cambio de esquema exige: subir `version`, escribir una `Migration`, y que su SQL coincida EXACTO con el `createSql` que KSP genera en el nuevo `schemas/N.json` (compila primero, copia de ahí). Añadir un `@Dao` o un método `@Insert(onConflict=REPLACE)` NO cambia el esquema.
- **`householdId`:** el valor canónico es `"default_household"`. El código ya NO lo hardcodea: `BudgetApplication` lo resuelve una vez vía `HouseholdDao.getSingleId()` y lo expone como `app.householdId` (con fallback al literal). Los datos sembrados por el ETL usan un UUID hasta que `verify_db.py` los renombra — el asset commiteado ya viene renombrado.

### Dominio: dos representaciones (cuidado con la duplicada muerta)
Hay clases espejo entre `core/model/*.kt` (data classes `@Serializable`) y `data/local/entity/*Entity.kt` (entidades Room). **La app opera SOLO con las `*Entity`**; las de `core/model` están huérfanas (solo se usan los enums de `Enums.kt`). No las uses para lógica nueva; fueron generadas por `scripts/convert_to_domain.py`.

### Capa de IA on-device (`ai/`)
Pipeline RAG **100% local** con Gemini Nano vía AICore (`ai/service/AiCoreManager.kt`). El LLM **no responde en lenguaje natural**: solo traduce la pregunta a un intent JSON estructurado (esquema en `assets/ai/intent_schema.json`, system prompt en `assets/ai/system_prompt.es.txt`). Flujo: `AiAssistantViewModel` → `LedgerRagUseCase` (clasifica con `QuestionClassifier` → recupera SQL selectivo → serializa tabular denso con `ContextSerializer` → ensambla con `PromptAssembler`/`PromptSanitizer`) → `AiCoreManager.generate` → `IntentDispatcher` (parsea con `JsonRepairer`, resuelve nombres con `AliasResolver`, ejecuta determinista). La IA es opcional; sin AICore el módulo se oculta.

### Capa de UI — rediseño "The Architectural Ledger"
El Dashboard y la Captura se rediseñaron desde un diseño generado en Claude Design (fuente: `ui_reference/claude_design/Presupuesto Hogar.dc.html`; brief de evidencia en `ui_reference/REDESIGN_BRIEF.md`). Decisiones de implementación:
- **Tema (`ui/theme/`):** **color dinámico (Material You) por default**, con el verde sembrado `#016E3E` como fallback. El toggle `dynamicColor` se persiste en DataStore (`data/settings/SettingsRepository.kt`) y se expone en la pantalla **Perfil**. Los semánticos financieros (ingreso/gasto/alerta) viven **FUERA del `ColorScheme`** en `FinanceColors.kt` (vía `CompositionLocal`, accesor `MaterialTheme.financeColors`), armonizados al primary con tope bajo (MDC `harmonize`) solo en modo dinámico. `AmountSemantics.kt` da redundancia no-cromática (signo/flecha/etiqueta) — el significado financiero nunca depende solo del color.
- **`DashboardScreen.kt`:** adaptativo por `windowWidthDp` (≥600dp = expandido). Expandido = rail de iconos custom + `BentoPanes` (dos paneles 62/38 con **divisor arrastrable** custom — `draggable` + `rememberSaveable`, NO `SupportingPaneScaffold`, por estabilidad). Compacto = bottom nav + columna única. KPI héroe "Disponible para gastar", barras horizontales ordenadas por miembro con toggle Beneficiario/Pagador, lista sin divisores (capas tonales).
- **`CaptureBottomSheet.kt`:** `ModalBottomSheet` acotado a 640dp (`sheetMaxWidth`). Monto + concepto (junto al monto) + keypad; categoría = recientes + búsqueda + acordeón de grupos (NO rejilla de pills); atribución en dos dimensiones con **% editable por miembro** (`beneficiaryShares`/`payerShares` como mapas memberId→% en el VM; stepper ±5; "Todos" reparte equitativo). "Beneficia a" es visible; "Pagó" + fecha viven en "Más" (el pagador se autodefine al dueño del wallet seleccionado; la fecha default es hoy). El VM convierte %→bps con el último absorbiendo el resto para sumar 10000 exacto por rol.
- **Toolchain de verificación:** el AVD `FinanceFold` se fijó a **`hw.lcd.density=320`** (→ 884×1104 dp, la densidad del Pixel 9 Pro Fold real y del diseño); a 420dpi (default del perfil) la pantalla interna sale apretada (673dp). Para tapear con `adb` de forma fiable usa `uiautomator dump` (lee bounds exactos) en vez de estimar coordenadas; los swipes hacia abajo sobre un `ModalBottomSheet` lo descartan (gesto de dismiss).

## Estado de implementación (importante — no asumas que todo funciona)

Es un proyecto **a medio construir** detrás de contratos bien diseñados. Antes de tocar algo, asume que puede ser un stub:
- ~~`ExpenseRepositoryImpl.observeSpendByMember` devuelve `emptyList()`~~ **YA IMPLEMENTADO** con una query real de atribuciones (`ExpenseAttributionDao.observeSpendByMember(quincenaId, role)`, agregando `share_amount_mxn` por miembro sobre gastos POSTED). Se añadió `observePaidByMember` (role=PAYER) para el toggle del dashboard. Las barras por miembro muestran datos reales. (El impl Firestore sí sigue como stub `emptyList()`, pero el dashboard lee de Room.)
- **`IntentDispatcher`** (capa IA) tiene handlers que devuelven datos hardcodeados/cero; aún no consultan repos. `AliasResolver` recibe listas vacías por defecto.
- **Firestore sync devuelve `PERMISSION_DENIED`** en runtime: faltan reglas de seguridad + que la auth anónima esté activa. El código de sync funciona; es configuración de la consola Firebase.
- **Repos Firestore** tienen mismatches de nombres de campo (camelCase de la entidad vs nombres inventados en algunos `whereEqualTo`) — el round-trip de datos que la propia app sube funciona, pero leer datos sembrados con claves snake_case puede no deserializar.
- **Módulo Wear** (`wear/`) no tiene AndroidManifest ni está en el build; `WearSyncManager` y `DynamicShortcutManager` nunca se instancian; `QuickCaptureActivity` crea el sheet con `viewModel=null`.
- **Navegación:** `dashboard` y **`profile`** están implementados (Perfil = pantalla real de ajustes con el toggle de color dinámico); `ledger/wallets/analytics` siguen como placeholders.
- **6 de 11 interfaces de repo** (Analytics, Income, Installment, Loan, Recurrence, Savings) no tienen ninguna implementación.
- **Resolución de conflictos del sync** es Last-Write-Wins simple (el remoto sobrescribe); las specs piden updatedAt/vector clock — es trabajo futuro. Deletes remotos son best-effort.

## Seguridad

`service-account.json` (clave privada de Firebase Admin) y `app/google-services.json` están en el disco local (el primero gitignored, untracked). El historial de git **ya fue purgado** de `service-account.json` (y de `*.hprof`, `etl_output.log`, `budget_database.db` raíz) con `git filter-repo`, y `origin/main` está limpio — el secreto nunca llegó al árbol remoto. Detalles en `SECURITY_REMEDIATION.md`. No re-añadas secretos ni los commitees; el ETL/seed los leen desde el disco local. Nota: `app/src/main/assets/budget_database.db` SÍ está trackeada a propósito (es la DB semilla) — no la confundas con la `budget_database.db` de la raíz que fue purgada.

**Semilla original "golden" (preservación de los datos del Excel).** El Excel original convertido a DB se guarda inmutable en `seed/budget_database.golden.db` (+ `.sha256` + `seed/README.md`). El **invariante**: el asset que se embarca (`app/src/main/assets/budget_database.db`) debe ser **idéntico** al golden, salvo regeneración deliberada del ETL. Esto protege los 793 gastos originales de cualquier cambio (migraciones en runtime no tocan el asset, que se queda en v1). Verifica con `bash scripts/check_seed_integrity.sh` (compara asset↔golden y, si hay sqlite3, valida `user_version=1`); restaura con `--restore`. Si regeneras la semilla a propósito, promueve el nuevo asset a golden y actualiza el `.sha256` (pasos en `seed/README.md`).
