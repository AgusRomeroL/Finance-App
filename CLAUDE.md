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
Kotlin + Jetpack Compose (Material 3 Expressive, semilla verde `#016E3E`), Room 2.6.1 + KSP, Firebase (Firestore/Auth anónima/Analytics), AICore (Gemini Nano on-device), Wear Data Layer. **DI manual sin Hilt** (Hilt está en dependencias pero no se usa): `BudgetApplication` es el contenedor que construye DB, repos y arranca el sync; `MainActivity` tiene las `ViewModelFactory`. namespace/applicationId `mx.budget`, minSdk 31, target/compileSdk 34, un solo módulo Gradle `:app` (el dir `wear/` existe pero **no está en `settings.gradle.kts` ni tiene AndroidManifest** — no se compila).

### Capa de datos — offline-first (DECISIÓN ARQUITECTÓNICA CLAVE)
**Room es la única fuente de verdad. Firestore es una réplica en la nube que se sincroniza, no la fuente directa.** Toda escritura es local primero (offline); al haber red se sube a Firestore.

- **Repos:** interfaces en `data/repository/`, impls Room en `data/repository/impl/*Impl.kt`, impls Firestore en `data/remote/*Firestore.kt` (implementan las mismas interfaces). `BudgetApplication` cablea los repos PÚBLICOS a las impls **Room**; las impls Firestore se guardan aparte como `remote*Repository` y son el "lado nube" del sync. (Históricamente esto estaba al revés y cableaba todo a Firestore — no lo reviertas.)
- **Sync push:** outbox `sync_queue` (tabla + `SyncQueueDao`). Cada escritura por repo encola una fila; `data/sync/SyncManager.kt` observa conectividad (`ConnectivityManager`) y drena la cola hacia los repos Firestore.
- **Sync pull:** `data/sync/RemotePullSync.kt` registra `addSnapshotListener` sobre las subcolecciones de Firestore y refleja cambios en Room. **Anti-eco crítico:** el pull escribe vía DAO directo (`*.upsert`), NUNCA por los repos (que encolarían y crearían un bucle push↔pull).
- **Atribución de gastos (modelo central):** cada `Expense` tiene dos particiones independientes en `expense_attribution` — `role="BENEFICIARY"` (quién consume) y `role="PAYER"` (quién paga), cada una en **basis points que suman exactamente 10000** (=100%). `ExpenseRepositoryImpl.insertWithAttributions` escribe gasto + atribuciones + outbox en un `db.withTransaction {}`.
- **Estructura Firestore:** `households/{householdId}/{expenses|categories|members|wallets|quincenas}`, atribuciones como subcolección bajo cada gasto.

### Room: esquema, asset precargado y migraciones (zona de peligro)
- La DB se carga con `createFromAsset("budget_database.db")`. El asset está congelado en **schema v1** (identityHash `9b889298...`); en código el `@Database` está en **v2** y `BudgetDatabase.MIGRATION_1_2` (añade `sync_queue`) corre al primer arranque. Los schemas exportados están en `app/schemas/`.
- **NO uses `fallbackToDestructiveMigration()`** — borraría los 793 gastos sembrados. Cualquier cambio de esquema exige: subir `version`, escribir una `Migration`, y que su SQL coincida EXACTO con el `createSql` que KSP genera en el nuevo `schemas/N.json` (compila primero, copia de ahí). Añadir un `@Dao` o un método `@Insert(onConflict=REPLACE)` NO cambia el esquema.
- **`householdId`:** el valor canónico es `"default_household"`. El código ya NO lo hardcodea: `BudgetApplication` lo resuelve una vez vía `HouseholdDao.getSingleId()` y lo expone como `app.householdId` (con fallback al literal). Los datos sembrados por el ETL usan un UUID hasta que `verify_db.py` los renombra — el asset commiteado ya viene renombrado.

### Dominio: dos representaciones (cuidado con la duplicada muerta)
Hay clases espejo entre `core/model/*.kt` (data classes `@Serializable`) y `data/local/entity/*Entity.kt` (entidades Room). **La app opera SOLO con las `*Entity`**; las de `core/model` están huérfanas (solo se usan los enums de `Enums.kt`). No las uses para lógica nueva; fueron generadas por `scripts/convert_to_domain.py`.

### Capa de IA on-device (`ai/`)
Pipeline RAG **100% local** con Gemini Nano vía AICore (`ai/service/AiCoreManager.kt`). El LLM **no responde en lenguaje natural**: solo traduce la pregunta a un intent JSON estructurado (esquema en `assets/ai/intent_schema.json`, system prompt en `assets/ai/system_prompt.es.txt`). Flujo: `AiAssistantViewModel` → `LedgerRagUseCase` (clasifica con `QuestionClassifier` → recupera SQL selectivo → serializa tabular denso con `ContextSerializer` → ensambla con `PromptAssembler`/`PromptSanitizer`) → `AiCoreManager.generate` → `IntentDispatcher` (parsea con `JsonRepairer`, resuelve nombres con `AliasResolver`, ejecuta determinista). La IA es opcional; sin AICore el módulo se oculta.

## Estado de implementación (importante — no asumas que todo funciona)

Es un proyecto **a medio construir** detrás de contratos bien diseñados. Antes de tocar algo, asume que puede ser un stub:
- **`ExpenseRepositoryImpl.observeSpendByMember`** devuelve `emptyList()` → el recuadro "Distribución por Miembro" del dashboard sale vacío.
- **`IntentDispatcher`** (capa IA) tiene handlers que devuelven datos hardcodeados/cero; aún no consultan repos. `AliasResolver` recibe listas vacías por defecto.
- **Firestore sync devuelve `PERMISSION_DENIED`** en runtime: faltan reglas de seguridad + que la auth anónima esté activa. El código de sync funciona; es configuración de la consola Firebase.
- **Repos Firestore** tienen mismatches de nombres de campo (camelCase de la entidad vs nombres inventados en algunos `whereEqualTo`) — el round-trip de datos que la propia app sube funciona, pero leer datos sembrados con claves snake_case puede no deserializar.
- **Módulo Wear** (`wear/`) no tiene AndroidManifest ni está en el build; `WearSyncManager` y `DynamicShortcutManager` nunca se instancian; `QuickCaptureActivity` crea el sheet con `viewModel=null`.
- **Navegación:** solo `dashboard` está implementado; `ledger/wallets/analytics/profile` son placeholders.
- **6 de 11 interfaces de repo** (Analytics, Income, Installment, Loan, Recurrence, Savings) no tienen ninguna implementación.
- **Resolución de conflictos del sync** es Last-Write-Wins simple (el remoto sobrescribe); las specs piden updatedAt/vector clock — es trabajo futuro. Deletes remotos son best-effort.

## Seguridad

`service-account.json` (clave privada de Firebase Admin) y `app/google-services.json` están en el disco local (el primero gitignored, untracked). El historial de git **ya fue purgado** de `service-account.json` (y de `*.hprof`, `etl_output.log`, `budget_database.db` raíz) con `git filter-repo`, y `origin/main` está limpio — el secreto nunca llegó al árbol remoto. Detalles en `SECURITY_REMEDIATION.md`. No re-añadas secretos ni los commitees; el ETL/seed los leen desde el disco local. Nota: `app/src/main/assets/budget_database.db` SÍ está trackeada a propósito (es la DB semilla) — no la confundas con la `budget_database.db` de la raíz que fue purgada.
