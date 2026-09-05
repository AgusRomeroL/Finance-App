# Presupuesto Familiar

App Android de presupuesto familiar quincenal para un hogar mexicano (MXN, español, zona horaria `America/Mexico_City`), con módulo Wear OS y una web de escritorio y colaboradores. Reemplaza un Excel real de presupuesto por quincenas: cada gasto se reparte entre quién lo consume y quién lo paga, las obligaciones recurrentes se materializan en el calendario y los estados de cuenta bancarios se importan para reconciliar cuentas y planes a meses sin intereses.

Target primario: Pixel 9 Pro Fold (layout adaptativo plegable), Pixel Watch 4 y navegador de escritorio.

## Qué hay en el repositorio

| Ruta | Contenido |
|---|---|
| `app/` | App Android (Kotlin, Jetpack Compose, Room, Firebase, IA on-device) |
| `wear/` | App Wear OS (tiles ProtoLayout + hub Compose), companion del teléfono |
| `wearcore/` | Biblioteca Kotlin con los paths del Data Layer compartidos por teléfono y reloj |
| `web/` | Web de escritorio y colaboradores (Vite + React + TypeScript + Tailwind + Firebase) |
| `scripts/` | ETL del Excel a SQLite, verificación de la semilla, administración de Firestore, evaluación del LLM de estados de cuenta |
| `seed/` | Semilla golden inmutable de la base de datos y los reportes de clasificación |
| `firestore.rules` | Reglas multi-tenant de Firestore (roles OWNER / PAYER / MEMBER) |

## Especificación y guías

- `CLAUDE.md`: guía técnica del repositorio (arquitectura, comandos, gotchas y estado real). Es el primer archivo que hay que leer.
- `PLAN_MAESTRO_CIERRE.md`: plan de cierre por fases con la tabla de estado y las decisiones abiertas.
- `ANALISIS_MAESTRO.md`, `ESPECIFICACION_PRESUPUESTO_APP.md`, `ESPECIFICACION_UX_HARDWARE_APP.md`, `ADENDA_IA_ON_DEVICE.md`, `ADENDA_IA_PROACTIVA.md`, `ADENDA_CAPTURA_CALENDARIO.md`: especificación viva del producto.
- `TUTORIAL.md`: tutorial guiado dentro de la app y sus reglas de mantenimiento.
- `SECURITY_REMEDIATION.md`: purga histórica de secretos del repositorio.

## Cómo se construye

Requisitos: Android Studio con su JBR (Java 21), Android SDK con plataforma 36 y Gradle 8.13 vía wrapper. El `java` del PATH no sirve para Gradle: exporta `JAVA_HOME` al JBR de Android Studio y usa siempre el wrapper.

```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"

./gradlew.bat :app:compileDebugKotlin   # verificación rápida
./gradlew.bat :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew.bat :wear:assembleDebug       # wear/build/outputs/apk/debug/wear-debug.apk
bash scripts/check_seed_integrity.sh    # el asset de la base sembrada coincide con el golden
```

La app necesita `app/google-services.json` (no versionado) para Firebase. La web se construye desde una copia en disco local NTFS (`npm ci && npm run build` dentro de `web/`), porque `npm` no funciona sobre el volumen de Google Drive; el detalle está en `web/README.md`.

## Datos

La base sembrada `app/src/main/assets/budget_database.db` se genera con el ETL de `scripts/etl/` a partir del Excel del hogar (no versionado) y se congela como golden en `seed/`. Room aplica las migraciones en el dispositivo; el asset se queda en la versión 1 del esquema. Los estados de cuenta reales y los Excel de origen contienen datos financieros personales y están excluidos del control de versiones.

## Licencia y uso

Repositorio privado. Todos los derechos reservados; no se concede licencia de uso, copia ni redistribución del código ni de los datos. `main` y `norma` son ramas de un solo hogar y no existe una versión pública ni una base de datos de demostración.

## Autoría

Agustín Romero López.
