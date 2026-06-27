# Handoff — Implementar el rediseño de Claude Design en la app

Documento de traspaso para continuar en un chat nuevo de Claude Code. Objetivo: tomar el diseño generado en Claude Design (archivo `Presupuesto Hogar.dc.html`) e implementarlo como UI real en Jetpack Compose, sobre la arquitectura existente.

## 1. El MCP de Claude Design (claude_design / `DesignSync`)

- En Claude Code, el MCP se expone como la herramienta **`DesignSync`** (lee/escribe proyectos de `claude.ai/design`). Endpoint que lo respalda: `https://api.anthropic.com/v1/design/mcp`.
- **Requiere autorización primero:** ejecutar **`/design-login`** en el chat (o `/login` con una suscripción Claude). Sin esto, `DesignSync` responde literalmente *"needs design-system authorization. Run /design-login"*. Este paso solo lo hace el usuario (es un flujo de cuenta).
- Métodos de lectura útiles: `list_files` (lista paths del proyecto), `get_file` (lee el contenido de un archivo, máx 256 KiB).

## 2. Proyecto y archivo a importar

- Proyecto: **"Presupuesto familiar quincenal Android"**
- `projectId`: **`0432d22c-2ad2-4316-8a14-5f8494d744a1`**
- URL: https://claude.ai/design/p/0432d22c-2ad2-4316-8a14-5f8494d744a1?file=Presupuesto+Hogar.dc.html
- Archivo a implementar: **`Presupuesto Hogar.dc.html`** (HTML/CSS que representa el diseño; hay que traducirlo a Compose, no copiarlo literal).
- Cómo leerlo: `DesignSync` con `method=list_files` y luego `method=get_file` (`projectId` arriba, `path="Presupuesto Hogar.dc.html"`). Conviene guardarlo en `ui_reference/claude_design/` como respaldo.

## 3. Qué significa "implementar"

Traducir el diseño a Jetpack Compose Material 3 sobre el código existente. Mapa pantalla → archivo:

| Pantalla del diseño | Archivo a rediseñar |
|---|---|
| Dashboard expandido (Fold interno, ~884×1104 dp): navigation rail de iconos + Bento 62/38, KPI héroe "Disponible para gastar", barras por miembro, lista de transacciones | `app/src/main/java/mx/budget/ui/dashboard/DashboardScreen.kt` |
| Dashboard compacto (plegado): bottom nav + una columna | mismo archivo (rama compacta) |
| Captura: hoja ~640dp; categoría = recientes/frecuentes + búsqueda + acordeón; atribución beneficiario/pagador | `app/src/main/java/mx/budget/ui/capture/CaptureBottomSheet.kt` |
| Tema: color dinámico (Material You) + verde fallback + semánticos protegidos | `app/src/main/java/mx/budget/ui/theme/` (Color.kt, Theme.kt) |

## 4. Restricciones y decisiones — NO reinventar (leer primero)

En el repo, antes de codificar:
- `ui_reference/REDESIGN_BRIEF.md` — brief final consolidado y basado en evidencia (convergencias C1–C14, divergencias resueltas D1–D4, specs por pantalla mapeadas a componentes Compose, y la §2.1 de color dinámico).
- `ui_reference/research/01..06_*.md` — los 6 informes (Material 3/foldable ×3, fintech ×2, color dinámico ×1).
- `ui_reference/veridian_ledger/DESIGN.md` — sistema de diseño "The Architectural Ledger".
- `CLAUDE.md` — arquitectura, toolchain, estado de implementación.

Puntos no negociables: `NavigationSuiteScaffold` forzado a rail de iconos en expandido (con mitigación de descubribilidad: `contentDescription`/tooltips/etiqueta del item activo); `SupportingPaneScaffold` + Bento con `LazyVerticalGrid`; barras horizontales ordenadas (NO dona); color dinámico con verde `#016E3E` como fallback + semánticos (`FinanceColors` fuera del `ColorScheme`, armonizados con tope bajo) + redundancia no-cromática (signo/ícono/etiqueta); categoría = recientes + búsqueda + acordeón (NO rejilla de pills); separación sin líneas (capas tonales); radios 28 dp.

## 5. Arquitectura a respetar

- **Room = fuente de verdad** (offline-first); la UI lee de los ViewModels (`DashboardViewModel`, `CaptureViewModel`) vía sus factories en `MainActivity`. No conectes la UI a Firestore directamente.
- **`householdId` se resuelve dinámicamente** (`(application as BudgetApplication).householdId`); NO hardcodear `"default_household"`.
- **NO usar `fallbackToDestructiveMigration()`** — borraría los 793 gastos sembrados. Cualquier cambio de esquema exige migración (ver CLAUDE.md, esquema actual v2).
- Datos reales presentes: 793 gastos, quincena ACTIVE (jun-2026, 2ª mitad).
- **M3 Expressive en alpha** (`material3 1.5.0-alpha`, `@OptIn(ExperimentalMaterial3ExpressiveApi)`): estable es 1.4.0. Aislar lo Expressive tras wrappers o usar `MaterialTheme` estable en flujos críticos; subir la dependencia es decisión consciente.

## 6. Toolchain / verificación

- Compilar: `./gradlew.bat :app:compileDebugKotlin` (el `java` del PATH es JDK 1.8; el wrapper usa el JBR de Android Studio vía `gradle.properties`).
- Emulador ya creado: AVD **`FinanceFold`** (perfil "7.6in Foldable", API 34, google_apis). Rutas: `adb`/`emulator` en `%LOCALAPPDATA%/Android/Sdk`. Build+install: `:app:assembleDebug` → `adb install -r app/build/outputs/apk/debug/app-debug.apk` → `adb shell am start -n mx.budget/.MainActivity`.
- Verificar en runtime: que la app no crashee, que el dashboard cargue datos reales, y probar plegado/desplegado (cambio de window size class).

## 7. Estado actual del repo

App compila y corre; dashboard muestra datos reales; capa de sync offline-first montada (push + pull); historial de git purgado de secretos; `main` sincronizado con `origin`. Último commit relevante: brief final + research de color dinámico.
