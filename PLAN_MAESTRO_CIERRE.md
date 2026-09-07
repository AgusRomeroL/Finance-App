# Plan maestro de cierre: dejar la app terminada, funcional y en producción

Fecha del diagnóstico: 2026-09-05. Base analizada: rama `develop` en `a06d470` (`main` y `norma` en `0eb8b7f`, tres commits por detrás). El último commit del proyecto era del 2026-07-11; el repositorio llevaba ocho semanas sin cambios. Nota: en la Fase 0 se reescribió el historial de `develop` desde `b9aa34f` para quitar trailers de coautoría, así que los hashes citados en este diagnóstico (`b9aa34f`, `e2ca12d`, `a06d470`, `0e57c5f`, `0a37e2a`) hoy corresponden a `56f619f`, `6316e80`, `d6e403a`, `31a8710` y `28595ce`; el contenido es idéntico.

Este documento es la fuente única de verdad del cierre. Cada fase la ejecuta un chat independiente que lee `CLAUDE.md`, la sección de su fase y la tabla de estado (§2), y que al terminar deja la tabla actualizada.

## Índice

1. Cómo usar este documento
2. Diagnóstico: qué está terminado y qué falta
3. Mapa de fases y estado
4. Detalle de cada fase (con su prompt de arranque)
5. Qué se necesita de Norma para alimentar su base de datos
6. La función `/design` de Claude y su papel en la mejora de UX
7. Decisiones abiertas que corresponden a Agustín
8. Fuentes

---

## 1. Cómo usar este documento

**Una fase, un chat.** El chat de cada fase copia el prompt de arranque de §4, trabaja en `develop`, verifica en dispositivo y cierra la fase con un commit. Si una fase se alarga más allá de lo que un chat sostiene con calidad, se parte en sub-fases (6a, 6b) y se anota en §3.

**Reglas invariables en todas las fases:**

- Rama de trabajo `develop`. Al cerrar una fase verificada se hace *fast-forward* de `develop` a `main`, y `norma` se iguala a `main` (siguen idénticas; la Fase 9, que las habría separado, quedó cancelada).
- Autoría única de Agustín Romero López en todo commit; sin trailers ni menciones de IA en commits, código, docs ni producto.
- Cero U+2014 en cualquier texto.
- Toda UI se verifica con la configuración fiel del Fold de Norma (`wm size 2076x2152`, `wm density 408`, `font_scale 1.30`, `font_weight_adjustment 300`, modo oscuro) y, cuando la fase lo exija, en hardware real (Pixel 10 Pro XL y Pixel 9 de Agustín, Pixel Watch 4).
- Todo cambio de estado de UI se anima con los tokens de `BudgetMotion` y respeta `LocalReducedMotion` (principio transversal de `CLAUDE.md`).
- Cualquier cambio de esquema Room sube la versión, escribe una `Migration` idempotente y copia el `createSql` del `schemas/N.json`; nunca `fallbackToDestructiveMigration`.

**Puerta de salida común a todas las fases:** `./gradlew.bat :app:compileDebugKotlin` y `./gradlew.bat :wear:assembleDebug` en verde; `bash scripts/check_seed_integrity.sh` OK; el flujo de la fase verificado en dispositivo; `CLAUDE.md` (y `TUTORIAL.md` si cambió una sección resaltada) actualizados; la fila de la fase en §3 marcada con commit y fecha; una memoria nueva o actualizada en el directorio de memoria del proyecto.

---

## 2. Diagnóstico: qué está terminado y qué falta

### 2.1 Lo que ya funciona (no se rehace)

El núcleo del producto está construido y verificado en hardware: captura manual, por voz y desde el reloj; atribución dual en basis points; calendario con plantillas recurrentes, materialización determinista y recordatorios; cuentas con transferencias, metas, préstamos y planes MSI; analíticas reales; libro mayor con edición y borrado; importación de estados de cuenta (texto local, clasificación con NVIDIA NIM) y reescritura de la tarjeta; checklist mensual de estados; sync *offline-first* Room→Firestore con LWW, *outbox* con *dead-letter* y lápidas para gastos; multi-hogar con roles OWNER/PAYER/MEMBER, invitaciones opacas y reglas multi-tenant desplegadas; onboarding, tutorial de 24 pasos y *journeys* guiados sin datos; sistema de *motion* expresivo; PWA de escritorio y colaboradores viva en `finance-app-abdf9.web.app`; módulo Wear con seis *tiles* y hub de cuatro pantallas; capa de IA reactiva y proactiva con ruta LLM validada en Pixel 9 (Gemma) y Pixel 10 (AICore).

### 2.2 Inventario de pendientes y problemas (con evidencia)

Severidad: **B** bloquea la entrega; **F** funcionalidad incompleta o defecto conocido; **C** calidad y deuda; **D** documentación e higiene.

| # | Sev. | Hallazgo | Evidencia | Fase |
|---|---|---|---|---|
| 1 | B | No existe build de *release*: sin `buildTypes`, sin `signingConfigs`, sin minify ni shrink, sin reglas ProGuard; `versionCode 1` en app y reloj. Solo se ha distribuido `app-debug.apk`. | `app/build.gradle.kts:14-30`, `wear/build.gradle.kts:17-18`, no existe `app/proguard-rules.pro` | 8 |
| 2 | B | SHA-1 del keystore de *release* no registrado en Firebase: Google Sign-In fallará en un build firmado. | memoria `finance-app-mvp-integral-fases` | 8 |
| 3 | B | El modelo Gemma no se puede provisionar sin `adb`: la descarga in-app está pendiente y Hugging Face exige token. En el Fold de Norma la ruta LLM queda muerta. | `ai/service/LiteRtLmManager.kt:24`, `CLAUDE.md` §Capa de IA | 4 |
| 4 | B | Norma debe vincular Google en Perfil antes de cualquier reinstalación; si no, el uid anónimo nuevo pierde OWNER y el push queda en `PERMISSION_DENIED`. No hay pantalla que lo explique ni recuperación asistida. | memoria `finance-app-consolidacion-2026-07-09` | 2, 11 |
| 5 | B | Norma siguió en el Excel: el 2026-09-05 entregó `Copy of presupuesto 2.5 (2).xlsx`, ya descifrado en la raíz del repo (ignorado por git): 38 hojas frente a 37, una nueva («Quincena 1 al 15 Septiembre») y tres de julio y agosto con cambios. La semilla vigente se fijó con `BUDGET_TODAY=2026-07-07`. Hay que resembrar desde el Excel nuevo, con las quincenas nuevas y el 100 % de las líneas clasificadas antes de crear la base inicial. Por decisión de Agustín, es el último pendiente. | raíz del repo; `seed/README.md`; memoria `finance-app-auditoria-metodo-beneficiario` | 10 |
| 6 | F | Tres correcciones del dashboard diseñadas y nunca aplicadas: filas PLANNED se ven como ingreso (falta tono `SCHEDULED`), "Disponible" negativo por reservar obligaciones mensuales completas (falta prorrateo), duplicado de PLANNED por carrera en la materialización (falta `Mutex`). Resuelto en la Fase 1 (`8609d0c`, `aaebb70`, `f78f23a`). El "Disponible" negativo tenía una segunda causa: el rollover creaba la quincena con presupuesto en cero, corregido en `66c4680`. | `ui/theme/AmountSemantics.kt` sin `SCHEDULED`; `ExpenseDao.kt` sin `observeProratedPlannedTotal`; `RecurrenceMaterializer.kt` sin `Mutex` | 1 |
| 7 | F | Deuda P2 acumulada: `DatePicker` en inglés, transferencias invisibles en el Ledger, `WalletFormSheet` trunca saldos a `Long`, MSI `PAID` (ETL) vs `PAID_OFF` (runtime), import con `walletId=null` solo audita, resumen determinista POSTED-only contradice el proyectado, *netting* sobre 18 meses produce montos absurdos, quincenas intermedias sin rellenar, saldo negativo grande en BBVA débito, cortes de palabra en Fold. Resuelto en la Fase 1 (`631bd36`, `7820690`, `b44d330`, `5f08503`, `8ee30ee`, `98be19a`, `66c4680`, `0d9cc44`, `5c428b0`). Dos matices: el import ya exigía cuenta en la interfaz y lo que quedaba vivo era código muerto; y la semilla se regeneró con el ETL corregido, alineando el golden con el ETL actual. | memorias `consolidacion-2026-07-09`, `estados-cuenta-desglose`, `mvp-integral-fases` | 1 |
| 8 | F | El push de HOUSEHOLD no está cableado en `SyncManager`: ediciones del hogar quedan locales. | `data/local/dao/HouseholdDao.kt:33-36`; kinds del `SyncManager` = CATEGORY, EXPENSE, INCOME, INSTALLMENT, LOAN, MEMBER, RECURRENCE, SAVINGS, TRANSFER, WALLET | 2 |
| 9 | F | Lápidas verificadas solo para gastos; el resto de entidades puede resucitar tras un offline prolongado. Deriva de saldo multi-dispositivo (snapshot absoluto con LWW). | memoria `consolidacion-2026-07-09` | 2 |
| 10 | F | El rol Colaborador (MEMBER) lee todo el ledger por API: la regla comodín concede `read` a cualquier miembro sobre `households/{hid}/**`. | `firestore.rules:129-137` | 2 |
| 11 | F | `statement_import` es local-only por diseño: el checklist "Estados del mes" no converge entre dispositivos. | `data/local/entity/StatementImportEntity.kt:16` | 2 |
| 12 | F | Un grupo recién creado nace sin cuentas; la siembra de categorías por defecto ya existe pero no está verificada en el flujo multi-hogar. | memoria `bugs-2026-07-10`; `data/local/DefaultCategoryCatalog.kt` | 2 |
| 13 | F | Sin exportación ni respaldo visible: RF-34 (reporte PDF/XLSX), RF-91 (XLSX con el layout del Excel), RF-92 (CSV) no existen; el comentario de Perfil lo deja como "se añadirá". Sin cierre manual de quincena (RF-32). | `ui/profile/ProfileScreen.kt:59`; `grep` de export/closeQuincena vacío | 5 |
| 14 | F | Quick Tap (spec §3.3, "estrella del sistema") no existe; el permiso `SYSTEM_ALERT_WINDOW` está declarado y solo lo consumirá su overlay. Decisión 2026-09-05: Quick Tap SÍ se implementa (Fase 6) y el permiso se conserva. Corrección de la evidencia: `ACCESS_BACKGROUND_LOCATION` sí tiene uso real (nivel "Persistente" de Perfil; `MainActivity` lo solicita y `BankCaptureManager` lo aprovecha al ingerir notificaciones en segundo plano); se conserva por decisión del mismo día. Ambos permisos exigirán justificación si algún día se publica en Play (Fase 8). | `AndroidManifest.xml`; `data/location/LocationProvider.kt`; `ui/capture/QuickCaptureActivity.kt` (ya existe, con `CaptureViewModel` real) | 6 |
| 15 | F | Wear: sin *complications* (spec §4.3) ni *Ongoing Activity*; la sincronización Data Layer solo se ha verificado parcialmente en hardware; la app del reloj no tiene versionado ni firma compartidos con el teléfono. | `wear/src` sin `Complication`; memoria `wear-redesign` | 3 |
| 16 | F | Chat IA: respuesta genérica en texto libre; latencia de ~54 s por análisis abierto en Pixel 9 (CPU) sin gestión de expectativas en UI. | memoria `mvp-integral-fases` (J5-LLM) | 4 |
| 17 | C | Pruebas: un solo test JVM (`NaturalLanguageCaptureParserTest`); ninguna prueba de la cadena de migraciones 1→19 sobre el asset real; sin pruebas de reglas Firestore; sin CI. La *golden suite* de IA de la spec no existe. | `app/src/test` (1 archivo); sin `androidTest`; sin `.github/` | 7 |
| 18 | C | Sin observabilidad de fallos en producción (no hay Crashlytics ni registro local exportable). | `app/build.gradle.kts` sin crashlytics | 7 |
| 19 | C | Dependencia muerta de Hilt (cero anotaciones) que alarga KSP y engorda el APK. Resuelto en la Fase 0 (`65fd0bc`). | `app/build.gradle.kts`; `grep @Inject` = 0 | 0 |
| 20 | C | Accesibilidad no auditada: 28 `contentDescription = null` frente a 16 con texto; sin pasada de TalkBack; textos 100 % en código (0 `stringResource`, `strings.xml` de 4 líneas). | métricas del `grep` 2026-09-05 | 6 |
| 21 | C | La web fija el `appId` por variable de entorno con *fallback* al de Android; el despliegue depende de una copia NTFS local sin script. | `web/src/lib/firebase.ts:17-20`, `web/README.md` | 8 |
| 22 | D | `CLAUDE.md` desactualizado: declaraba Room v15 y `MIGRATION_14_15` cuando la base está en v19 (`MIGRATION_18_19`); sin mención de multi-hogar, roles v2, web de escritorio ni estados del mes. Resuelto en la Fase 0 (`533c9ba`): reescrito completo y verificado contra el código. | `data/local/BudgetDatabase.kt`; `CLAUDE.md` | 0 |
| 23 | D | Sin `README.md` ni `LICENSE` en la raíz; `main` no es público de facto (nombres de la familia en 30 archivos Kotlin; sin base demo; `main == norma`). Resuelto en la Fase 0 (`b17d397`): `README.md` añadido; por decisión del 2026-09-05 el repositorio es privado, sin `LICENSE`, y la Fase 9 se cancela. | listado de raíz; memoria `branch-model` | 0 |
| 24 | D | Higiene local: cinco volcados `java_pid*.hprof` (unos 3.8 GB) y `sdk.zip` (75 MB) en la carpeta sincronizada de Drive; están ignorados por git pero se replican en la nube. `DESIGN.md` de referencia habla de Roboto Flex cuando la app usa Google Sans Flex. Resuelto en la Fase 0: movidos a `C:\dev\finance-app-offdrive\` (4.2 GB con `temp_jdk`, `temp_sdk` y `build_log.txt`); `DESIGN.md` marcado como histórico con nota de tokens reales (la reescritura con tokens sigue en 6a). | listado de raíz; `ui_reference/veridian_ledger/DESIGN.md` | 0, 6 |
| 26 | D | Cero U+2014 es regla invariable, pero el repositorio versiona 1395 ocurrencias en 206 archivos (docs, specs, comentarios Kotlin y textos visibles de la app, p. ej. el encabezado de quincena "1 [raya] 15 SEPT" del dashboard). Detectado en la Fase 0; no se barrió porque exige reescritura frase por frase. Propuesta: los textos visibles y los archivos Kotlin en la Fase 6b (con la extracción a `strings.xml`), la documentación en la Fase 7 junto con la CI que lo verifique. | conteo con `git ls-files` + `grep` del 2026-09-05 | 6, 7 |
| 25 | B | Preguntas de clasificación de la semilla sin respuesta de Norma (Pau Raúl, Berna/Bernardo, Marco y Omar, Cochecito $8,000, Changan $5,000, deudor "Por identificar" $1,900) y dudas surgidas de los estados de cuenta (CENEVAL, CirculoDerm, ropa universitarios, WIM, Google CR, cargos de Amazon). Regla nueva (2026-09-05): la base inicial no se genera con ninguna duda abierta. | `seed/REPORTE_SEMILLA_2026-07.md:41-47,232`; memoria `auditoria-metodo-beneficiario` | 10 |

---

## 3. Mapa de fases y estado

El orden respeta dependencias reales: primero se sanea el terreno, después se cierra la deuda funcional, luego la robustez del sync (de la que dependen reloj y datos), en seguida las capas que se apoyan en ella, y al final calidad, release y puesta en producción con Norma. La Fase 9 (`main` público) se conserva en la numeración pero quedó cancelada el 2026-09-05: el repositorio es privado. Por decisión de Agustín (2026-09-05), todo lo que involucra a Norma queda al final: no recibe preguntas ni tareas hasta que el software esté terminado y publicado. Sus preguntas (§5.3) se envían al arrancar la Fase 10.

| Fase | Nombre | Depende de | Chats estimados | Estado | Commit de cierre |
|---|---|---|---|---|---|
| 0 | Saneamiento del repositorio y de la documentación | ninguna | 1 | **hecha** (2026-09-05) | `b17d397` (último de contenido; el cierre documental es el commit siguiente, que actualiza esta tabla) |
| 1 | Cierre de la deuda funcional conocida | 0 | 1 a 2 | **hecha** (2026-09-07) | `5c428b0` (último de contenido; el cierre documental es el commit siguiente, que actualiza esta tabla) |
| 2 | Sync, identidad y reglas a prueba de todo | 1 | 1 a 2 | pendiente | |
| 3 | Wear OS terminado y verificado en hardware | 2 | 1 | pendiente | |
| 4 | IA on-device terminada (provisión del modelo, latencia, chat) | 2 | 1 | pendiente | |
| 5 | Ciclo de quincena, exportación y respaldo | 1 | 1 | pendiente | |
| 6 | Mejora de UX con `/design` y accesibilidad | 1, 5 | 2 (6a auditoría y diseño; 6b implementación) | pendiente | |
| 7 | Calidad: pruebas, CI y observabilidad | 2, 5 | 1 | pendiente | |
| 8 | Release firmado, publicación web y distribución | 3, 4, 6, 7 | 1 | pendiente | |
| 9 | `main` público (opcional) | 8 | 0 | **cancelada** (2026-09-05: repo privado, sin `LICENSE`) | |
| 10 | Semilla definitiva desde el Excel 2.5 (2), 100 % clasificada | 8 + respuestas de Norma | 1 a 2 | pendiente | |
| 11 | Puesta en producción con Norma (cutover) | 10 | 1 | pendiente | |

Las fases 3 y 4 pueden correr en paralelo (chats distintos) una vez cerrada la 2. La 5 puede correr en paralelo con la 2. Las fases 10 y 11 son las últimas por decisión explícita; el chat de la Fase 10 adelanta primero el trabajo técnico que no requiere a Norma (diff de hojas, extensión del ETL, cuestionario de conceptos nuevos) y solo después le escribe.

---

## 4. Detalle de cada fase

### Fase 0. Saneamiento del repositorio y de la documentación

**Objetivo.** Que el siguiente chat encuentre un repositorio veraz, ligero y con `main` al día.

**Alcance.**
1. Verificar en el emulador FinanceFold (config fiel) los tres commits de `develop` que `main` no tiene (motion expresivo, *journeys* sin datos, refinamiento Fold) y hacer *fast-forward* de `main` y `norma`.
2. Reescribir las secciones desactualizadas de `CLAUDE.md`: Room v19 con la cadena completa de migraciones y su propósito, multi-hogar y roles v2, web de escritorio, estados del mes, ingesta multi-documento, Pixel Watch 4, rutas de navegación reales, y un apartado "Estado real al 2026-09" que apunte a este plan. Corregir `ui_reference/veridian_ledger/DESIGN.md` (Google Sans Flex, tokens reales) o marcarlo como histórico.
3. Quitar la dependencia de Hilt y el permiso `SYSTEM_ALERT_WINDOW`. Sobre `ACCESS_BACKGROUND_LOCATION`, aplicar la decisión de §7 (propuesta: quitarlo y dejar la ubicación solo en primer plano). *Ejecutado (2026-09-05):* Hilt retirado (`65fd0bc`); ambos permisos se conservan por decisión de Agustín (Quick Tap se implementa en la Fase 6; el nivel "Persistente" de ubicación sí usa el permiso de segundo plano).
4. Añadir `README.md` (qué es, cómo se construye, dónde vive la spec) y la `LICENSE` que Agustín decida (§7). *Ejecutado:* `README.md` (`b17d397`); sin `LICENSE`, repositorio privado.
5. Mover fuera de la carpeta de Drive los `java_pid*.hprof`, `sdk.zip`, `temp_jdk/`, `temp_sdk/` y `build_log.txt` (no son de git, pero se sincronizan). Confirmar que el wrapper local está en Gradle 8.13.

**Fuera de alcance.** Cualquier cambio de comportamiento de la app.

**Criterio de salida.** `main == develop == norma`; `CLAUDE.md` sin afirmaciones falsas (se valida leyendo cada sección contra el código); build en verde; APK debug instalado y arrancando en FinanceFold tras los cambios de manifest y dependencias.

**Registro de cierre (2026-09-05).** Commits en `develop`: `533c9ba` (guía del repo + DESIGN.md histórico), `65fd0bc` (Hilt fuera), `b17d397` (README), más el commit de esta actualización del plan. Historial de `develop` reescrito desde `56f619f` para eliminar los trailers de coautoría (respaldo local en el tag `backup/develop-pre-rewrite-2026-09-05`). Limpieza de Drive hecha; wrapper en 8.13. APK sin Hilt verificado en FinanceFold (config fiel, oscuro): dashboard expandido, validación guiada de la captura, autofoco de búsqueda y back sin crashes.

**Prompt de arranque.**
```text
Lee CLAUDE.md y la Fase 0 de PLAN_MAESTRO_CIERRE.md. Ejecuta esa fase completa en la rama develop
y cierra con fast-forward a main y norma. Autoría única de Agustín, sin trailers de IA, cero U+2014.
Antes de tocar CLAUDE.md verifica cada afirmación contra el código (versión de Room, migraciones,
rutas de navegación, módulos). Al terminar actualiza la tabla de la sección 3 del plan con el commit
de cierre y escribe la memoria correspondiente.
```

---

### Fase 1. Cierre de la deuda funcional conocida

**Objetivo.** Que ningún defecto ya diagnosticado siga vivo cuando se toque UX o release.

**Alcance (cada punto con su verificación en FinanceFold).**
1. Tono `SCHEDULED` en `AmountSemantics` (color `onSurfaceVariant`, signo "−", icono reloj, etiqueta "Programado") aplicado a filas PLANNED en `TransactionRow`; quitar el caso especial NEUTRAL.
2. "Disponible" prorrateado: nueva consulta `observeProratedPlannedTotal` ponderada por la cadencia de la plantilla (mensual ×0.5, quincenal ×1.0, bimestral ×0.25, sin plantilla ×1.0) y uso en `HeroRingContent`.
3. `Mutex` de proceso en `RecurrenceMaterializer.materialize` (arranque, edición y rollover comparten la sección crítica).
4. Localización es-MX real de `DatePicker` y de todo componente que dependa de la configuración del sistema (`localeConfig` o `LocaleList` por app).
5. Transferencias visibles en el Ledger como filas propias con su semántica no cromática.
6. `WalletFormSheet` con montos decimales; unificar el estado de MSI (`PAID_OFF`) entre ETL, semilla y runtime con migración de datos idempotente si aplica.
7. Import de estado de cuenta: el preview exige elegir cuenta antes de reconciliar.
8. Resumen determinista y chat: mostrar recibido y proyectado con la misma convención del dashboard.
9. *Netting* "Cuentas entre miembros": alcance por defecto = quincena activa, con selector de rango; documentar la decisión.
10. Rollover: rellenar quincenas intermedias ausentes al arrancar tras semanas sin abrir la app.
11. Semántica del saldo negativo en cuentas de débito (mostrar como sobregiro con aviso o bloquear en reconciliación).
12. Cortes de palabra en Fold a fontScale 1.3 + bold (auditar con `uiautomator dump`).

**Criterio de salida.** Cada punto verificado con captura en FinanceFold (dark, fuente 1.3, bold) y, para 1 a 3, en el Pixel 10 de Agustín. Tutorial revisado si cambió una sección resaltada.

**Prompt de arranque.**
```text
Lee CLAUDE.md y la Fase 1 de PLAN_MAESTRO_CIERRE.md. Resuelve los 12 puntos en develop, uno por
commit, verificando cada uno en el emulador FinanceFold con la configuración fiel del Fold. Usa los
tokens BudgetMotion para todo cambio de estado. Si un punto exige cambio de esquema Room, sigue la
regla de migraciones de CLAUDE.md. Cierra con fast-forward a main y norma, actualiza la tabla de la
sección 3 y escribe memoria.
```

---

### Fase 2. Sync, identidad y reglas a prueba de todo

**Objetivo.** Que dos o más dispositivos (Fold de Norma, Pixel de Agustín, web) converjan siempre, que nadie pierda su rol y que cada rol vea solo lo que le corresponde.

**Alcance.**
1. Cablear HOUSEHOLD en el `SyncManager` (push con `updated_at`, pull con LWW).
2. Lápidas (`deleted_at`) para todas las entidades sincronizadas, con la misma verificación *two-device* que ya se hizo para gastos.
3. Deriva de saldo: sustituir el snapshot absoluto del saldo de wallet por deltas idempotentes (o, como mínimo, reconciliación asistida con detección de divergencia y aviso). Documentar la elección.
4. Decidir el destino de `statement_import` y del checklist mensual: sincronizarlos (entidad con `updated_at`, migración v20) o declarar explícitamente que son por dispositivo. Propuesta: sincronizar, porque Norma y Agustín comparten el seguimiento.
5. Reglas Firestore por colección en lugar del comodín: el Colaborador lee `proposals`, `members`, `categories` y `wallets` (solo lo necesario para proponer) y un documento agregado `summary/{quincenaId}` que escriben los dispositivos con rol completo; `expenses`, `income_source`, `quincenas`, `savings_goal`, `loan`, `installment_plan`, `wallet_transfer` y `recurrence_template` quedan para OWNER y PAYER. Adaptar la web al documento agregado. Desplegar por Rules API (patrón de `scripts/admin/firebase_setup.py`).
6. Identidad: pantalla de estado de cuenta en Perfil ("Sesión vinculada a Google: sí/no; rol: Dueño") con aviso persistente si el usuario con rol OWNER sigue en anónimo, y flujo de recuperación documentado (reclamo por `createdBy`, `grant_owner.py` como válvula).
7. Onboarding de grupo nuevo: al crear o unirse a un grupo vacío, ofrecer cuentas y categorías por defecto (reutilizar el wizard y `DefaultCategoryCatalog`).
8. Verificación *two-device*: Pixel 10 (Agustín) + FinanceFold con un grupo de prueba, cubriendo alta, edición, borrado y offline prolongado de cada entidad.

**Criterio de salida.** Matriz de convergencia por entidad (crear, editar, borrar, offline 24 h) completa y en verde; reglas desplegadas y probadas con una cuenta Colaborador (web) que no puede leer `expenses`.

**Prompt de arranque.**
```text
Lee CLAUDE.md, la Fase 2 de PLAN_MAESTRO_CIERRE.md y las memorias de sync (data-architecture,
consolidacion-2026-07-09, bugs-2026-07-10). Implementa los 8 puntos en develop. El pull escribe
siempre por DAO (anti-eco). Las reglas se despliegan por Rules API y se prueban con una cuenta
Colaborador real. Entrega la matriz de convergencia por entidad como tabla en la memoria. Cierra con
fast-forward a main y norma y actualiza la sección 3 del plan.
```

---

### Fase 3. Wear OS terminado y verificado en hardware

**Objetivo.** Que el reloj sea una superficie completa y confiable, no un cascarón que compila.

**Alcance.**
1. Ejecutar la receta de verificación E2E en el Pixel Watch 4 emparejado con el Pixel 10: hub con "Disponible" real, captura desde el reloj a la bandeja, confirmar y descartar pendientes, ingreso desde el reloj, refresco de los seis *tiles*, dictado por voz.
2. Dos *complications* según la spec §4.3: "Disponible" (`RANGED_VALUE` con `SHORT_TEXT`) y "Próximo pago" (`SHORT_TEXT`), alimentadas desde `WearCache`.
3. Latencia y frescura: medir con `dumpsys gfxinfo`; corregir todo *jank* del hub y de los *tiles*.
4. Versionado y firma compartidos con el teléfono (mismo `versionCode` y misma llave, requisito del emparejamiento companion); preparar el `build.gradle.kts` del reloj para la Fase 8.
5. Estados de error visibles en el reloj: teléfono desconectado, cola pendiente, snapshot antiguo.

**Fuera de alcance.** *Ongoing Activity* (sin valor claro para este hogar).

**Criterio de salida.** Video o capturas de cada flujo en el reloj real; *complications* instalables en una carátula; sin *jank* medible.

**Prompt de arranque.**
```text
Lee CLAUDE.md, la Fase 3 de PLAN_MAESTRO_CIERRE.md y la memoria finance-app-wear-redesign. El
usuario tiene un Pixel 10 Pro XL y un Pixel Watch 4 reales conectados por adb; pídele confirmación
de conectividad antes de empezar. Ejecuta la receta E2E, implementa las dos complications y el
versionado compartido, y corrige todo lo que falle. Documenta los resultados por flujo. Cierra con
fast-forward a main y norma y actualiza la sección 3 del plan.
```

---

### Fase 4. IA on-device terminada

**Objetivo.** Que la capa de IA funcione en el Fold de Norma sin `adb` y con expectativas claras de latencia.

**Alcance.**
1. Provisión del modelo: descarga in-app del `.litertlm` desde un origen controlado por Agustín (§7: Firebase Storage del proyecto con regla de autenticado, o bucket propio), con `WorkManager` (solo Wi-Fi, con carga, reanudable por `Range`, verificación SHA-256), progreso y control en Perfil → "Asistente IA", y borrado del modelo. Elegir la variante (E2B frente a E4B) con base en la RAM del Fold y la latencia medida.
2. Gestión de latencia: indicador de progreso honesto, cancelación, *streaming* de tokens si la API lo permite, y precálculo nocturno del *digest* determinista para que el análisis abierto arranque caliente.
3. Chat en texto libre: cerrar la ruta genérica (clasificación → intent → respuesta anclada) con mensajes de límite claros cuando la pregunta no cabe en el catálogo.
4. Verificar las tres rutas del `HybridLlm` (AICore en Pixel 10, LiteRT-LM en Pixel 9, fallback SQL en emulador) tras los cambios.
5. Dejar los casos de la *golden suite* (pregunta → intent esperado) como datos JSON en `assets/ai/golden/`; la Fase 7 los convierte en pruebas.

**Criterio de salida.** Instalación fresca en Pixel 9 sin `adb push`: el modelo se descarga desde la app y el análisis abierto responde con el badge "Análisis IA"; el Pixel 10 responde por AICore; el emulador degrada a chips sin error.

**Prompt de arranque.**
```text
Lee CLAUDE.md (capa de IA), ADENDA_IA_PROACTIVA.md §F.8.4, la Fase 4 de PLAN_MAESTRO_CIERRE.md y las
memorias de IA. Antes de implementar la descarga, confirma con el usuario el origen del modelo
(decisión 4 de la sección 7). Implementa los 5 puntos en develop y verifica en Pixel 9 y Pixel 10
reales. Cierra con fast-forward a main y norma y actualiza la sección 3 del plan.
```

---

### Fase 5. Ciclo de quincena, exportación y respaldo

**Objetivo.** Que la app cubra el ciclo completo que hacía el Excel: cerrar el periodo, sacar el reporte y conservar una copia legible fuera de la app.

**Alcance.**
1. Cierre manual de quincena (RF-32) con confirmación, resumen de lo que se congela y reapertura controlada (solo OWNER/PAYER), sincronizado.
2. Reporte de quincena cerrada (RF-34) en PDF con `android.graphics.pdf.PdfDocument` (sin dependencias pesadas): KPIs, gasto por categoría y por miembro, deudas y pagos programados.
3. Exportación XLSX con el layout del Excel original (RF-91), una hoja por quincena, para que Norma reconozca su propio modelo mental; elegir una biblioteca ligera (evaluar `fastexcel`; queda excluido Apache POI completo por tamaño). Exportación CSV estándar (RF-92) por rango de fechas.
4. Respaldo y restauración: exportar la base Room completa (cerrando conexiones, con `VACUUM INTO`) a un archivo elegido por SAF, y restaurar con validación de `user_version`; habilitar Android Auto Backup para la base y DataStore mediante `dataExtractionRules`.
5. Sección "Exportar y respaldar" en Perfil con las cuatro acciones y su última ejecución.

**Criterio de salida.** Un PDF, un XLSX y un CSV generados desde el Fold abren correctamente en el escritorio; una restauración en instalación fresca recupera los 919 gastos y el estado de las cuentas.

**Prompt de arranque.**
```text
Lee CLAUDE.md, ESPECIFICACION_UX_HARDWARE_APP.md §2.2 y §6.5, y la Fase 5 de PLAN_MAESTRO_CIERRE.md.
Implementa los 5 puntos en develop sin añadir dependencias pesadas (nada de Apache POI completo).
Verifica cada archivo exportado abriéndolo con Python (openpyxl, pypdf) desde el escritorio. Cierra
con fast-forward a main y norma y actualiza la sección 3 del plan.
```

---

### Fase 6. Mejora de UX con `/design` y accesibilidad

**Objetivo.** Elevar la usabilidad de los recorridos reales de Norma con evidencia, usando los artboards de `/design` como herramienta de exploración antes de tocar Compose, e implementar **Quick Tap** (spec `ESPECIFICACION_UX_HARDWARE_APP.md` §3.3, decisión de Agustín del 2026-09-05), la entrada de captura más rápida del sistema. El detalle de la herramienta está en §6 de este documento.

**Sub-fase 6a. Auditoría y diseño (un chat interactivo, desde la app de escritorio de Claude Code).**
1. Auditoría heurística de siete recorridos con capturas en FinanceFold (config fiel): capturar un gasto en menos de 10 s; confirmar el PLANNED del día desde la notificación; entender "cuánto me queda" (héroe, reservado, proyectado); importar el estado del mes; corregir un gasto; invitar a un miembro; proponer desde la web. Cada fricción se anota con severidad y evidencia.
2. Pasada de accesibilidad con TalkBack y *Accessibility Scanner* en el Pixel 10: `contentDescription` faltantes, objetivos táctiles menores de 48 dp, orden de foco, contraste en dinámico oscuro.
3. Actualizar `ui_reference/veridian_ledger/DESIGN.md` con los tokens reales (Google Sans Flex, `FinanceColors`, `BudgetMotion`, `BudgetShapes`) y publicar un proyecto de sistema de diseño "Presupuesto Familiar" con `/design-sync` (vistas previas HTML de los componentes clave: `KpiCard`, `TransactionRow`, `BudgetRing`, hoja de captura, `FloatingNavBar`, chips de atribución).
4. Ejecutar `/design` con un brief por recorrido de mayor fricción (máximo cuatro), anclado en la evidencia del punto 1 y en los tokens del punto 3. Guardar la URL del lienzo y la opción elegida en `ui_reference/design_2026-09/DECISIONES.md`.
5. Brief adicional de **Quick Tap**: panel flotante de captura (spec §3.3 componentes B y C: aspecto de notificación expandida, `surfaceContainerHighest`, radio 28 dp, foco inicial en el monto, categoría, cuenta y beneficiario predichos, cierre automático a los 6 s sin interacción) y la pantalla explicativa previa al permiso "mostrar sobre otras apps".

**Sub-fase 6b. Implementación (un chat).**
6. Implementar en Compose las opciones elegidas, respetando `BudgetMotion`, `pressScale`, `staggeredEntrance` y `LocalReducedMotion`; actualizar `TutorialSpec` y `TUTORIAL.md` por cada sección que cambie.
7. Cerrar los hallazgos de accesibilidad; extraer a `strings.xml` como mínimo los textos que TalkBack lee y los mensajes de error; en esa misma pasada eliminar los U+2014 de los textos visibles y de los archivos Kotlin (hallazgo 26).
8. **Quick Tap** (spec §3.3, sobre `QuickCaptureActivity`, que ya existe con `CaptureViewModel` real): (a) entrada: `activity-alias` con `LAUNCHER` y etiqueta propia ("Gasto rápido") para que aparezca en Ajustes, Sistema, Gestos, Quick Tap, "Abrir app"; más el shortcut estático `quick_capture` (`res/xml/shortcuts.xml`) y el deep-link `mx.budget://capture?amount=&category=&wallet=&beneficiary=` (§3.5); (b) overlay: `OverlayService` (foreground, tipo `specialUse`) que pinta el panel diseñado en 6a con `TYPE_APPLICATION_OVERLAY`; permisos `SYSTEM_ALERT_WINDOW` (ya declarado; onboarding explicativo y redirección a `ACTION_MANAGE_OVERLAY_PERMISSION`), `FOREGROUND_SERVICE` y `FOREGROUND_SERVICE_SPECIAL_USE`; sin permiso de overlay cae a la hoja completa de `QuickCaptureActivity`; (c) fallback para teléfonos sin Quick Tap: `TileService` de Quick Settings "Capturar gasto" (§3.3 componente D); (d) motion con `BudgetMotion` (entrada `slideInVertically` + fade con resorte, salida al confirmar) y `LocalReducedMotion`; (e) trazas `QuickCapture.coldStart` y `QuickCapture.persistDuration` (§3.6) con metas P95 < 600 ms y P99 < 120 ms medidas en el Pixel 10.
9. Verificar en FinanceFold y en el Fold real de Norma (captura remota) los recorridos auditados, con tiempos antes y después; Quick Tap se verifica en hardware (Pixel 10 Pro XL y Pixel 9: el gesto no existe en el emulador, donde solo se prueban el intent, el tile y el deep-link).

**Criterio de salida.** Informe de fricciones con estado (resuelta, aceptada, diferida); tiempos de recorrido medidos; cero errores de *Accessibility Scanner* en las pantallas principales; tutorial coherente; Quick Tap operativo de doble golpe a gasto guardado en menos de 2.5 s en el Pixel 10, con fallback por tile verificado.

**Prompt de arranque (6a).**
```text
Sesión interactiva desde Claude Code Desktop (necesaria para /design y /design-sync). Lee CLAUDE.md,
la Fase 6 y la sección 6 de PLAN_MAESTRO_CIERRE.md, la sección 3.3 de
ESPECIFICACION_UX_HARDWARE_APP.md, ui_reference/REDESIGN_BRIEF.md y las skills emil-design-eng y
apple-design del repo. Ejecuta la auditoría de los 7 recorridos con capturas en FinanceFold (config
fiel), la pasada de accesibilidad, actualiza DESIGN.md con los tokens reales, publica el sistema de
diseño con /design-sync y corre /design con un brief por recorrido de mayor fricción (máximo 4)
más el brief del panel de Quick Tap. Registra las opciones elegidas en
ui_reference/design_2026-09/DECISIONES.md. No implementes todavía. Actualiza la sección 3 del plan.
```

**Prompt de arranque (6b).**
```text
Lee CLAUDE.md, la Fase 6 de PLAN_MAESTRO_CIERRE.md, la sección 3.3 de
ESPECIFICACION_UX_HARDWARE_APP.md, ui_reference/design_2026-09/DECISIONES.md y TUTORIAL.md.
Implementa en Compose las opciones elegidas con los tokens de BudgetMotion y verifica en
FinanceFold (config fiel). Implementa Quick Tap completo (alias de launcher, shortcut, deep-link,
overlay con permiso, tile de Quick Settings, trazas) y verifícalo en el Pixel 10 por adb. Cierra los
hallazgos de accesibilidad, quita los U+2014 de textos visibles y Kotlin, y actualiza el tutorial.
Cierra con fast-forward a main y norma y actualiza la sección 3 del plan.
```

---

### Fase 7. Calidad: pruebas, CI y observabilidad

**Objetivo.** Que una regresión se detecte antes de llegar al Fold de Norma.

**Alcance.**
1. Pruebas JVM de los motores puros: `InstallmentSchedule`, `StatementCycleTracker`, `RecurrenceDetector`, `RecurrenceMaterializer` (ids deterministas), `JsonRepairer`, `QuestionClassifier`, `AmountSemantics`, `MoneyFormat`, conversión %→bps del `CaptureViewModel`, y la *golden suite* de intents desde `assets/ai/golden/`.
2. Prueba instrumentada de migraciones con `MigrationTestHelper`: asset v1 real → v19 (o la versión vigente) sin pérdida de filas, más la ruta de *upgrade* desde v16, v17 y v18 (builds que existen en dispositivos reales).
3. Pruebas de `firestore.rules` con el emulador de Firebase y `@firebase/rules-unit-testing` en `web/` (matriz rol × colección × operación).
4. Pruebas mínimas de la web con Vitest (repositorio, normalización dual, rutas por rol).
5. GitHub Actions: `compileDebugKotlin`, `testDebugUnitTest`, `check_seed_integrity.sh`, `npm run build` de la web y pruebas de reglas; las instrumentadas quedan como *workflow* manual por costo.
6. Observabilidad: aplicar la decisión 7 de §7 (Crashlytics o, si se prefiere no enviar datos, registro local de fallos con exportación desde Perfil).

**Criterio de salida.** CI en verde en `develop` y `main`; cobertura de los motores puros documentada; la prueba de migraciones corre en el emulador y pasa.

**Prompt de arranque.**
```text
Lee CLAUDE.md y la Fase 7 de PLAN_MAESTRO_CIERRE.md. Escribe las pruebas y el pipeline en develop.
La prueba de migraciones usa el asset real app/src/main/assets/budget_database.db y app/schemas.
Confirma con el usuario la decisión 7 de la sección 7 antes de añadir Crashlytics. Cierra con
fast-forward a main y norma y actualiza la sección 3 del plan.
```

---

### Fase 8. Release firmado, publicación web y distribución

**Objetivo.** Un APK o AAB firmado, con reducción de código, instalable en el Fold y el reloj, más la web desplegada con su configuración definitiva.

**Alcance.**
1. Agustín crea el keystore de *release* fuera del repositorio con `keytool` (las contraseñas nunca pasan por el chat); las rutas y claves se leen de `secrets.local.properties` (ignorado) mediante Gradle.
2. `buildTypes.release` en app y reloj: `isMinifyEnabled`, `isShrinkResources`, `proguard-rules.pro` con reglas para Room, Firestore, OkHttp, kotlinx.serialization, pdfbox-android, ML Kit y LiteRT-LM; esquema de `versionCode` compartido (fecha + secuencia) y `versionName` semántico.
3. Registrar el SHA-1 de *release* en Firebase, regenerar `google-services.json`, y verificar Google Sign-In con el build firmado.
4. Verificar la compatibilidad de páginas de 16 KB de las bibliotecas nativas (`zipalign -c -P 16`) y, si hace falta, subir las dependencias que la incumplan.
5. *Smoke test* completo del build de *release* (todos los flujos de §2.1) en Pixel 9, Pixel 10 y Watch 4: la reducción de código suele romper reflexión y serialización.
6. Web: `VITE_FIREBASE_APP_ID` definitivo, script de despliegue reproducible desde una copia NTFS (`scripts/web/deploy.ps1`), y verificación de la PWA instalada.
7. Canal de distribución según la decisión 5 de §7 (APK firmado compartido por Drive, o pista interna de Play).

**Criterio de salida.** `app-release` y `wear-release` firmados, instalados y funcionando en hardware real; web desplegada desde el script; `CLAUDE.md` con la sección "Cómo se publica".

**Prompt de arranque.**
```text
Lee CLAUDE.md y la Fase 8 de PLAN_MAESTRO_CIERRE.md. El keystore lo crea el usuario; pídele la ruta y
los nombres de las propiedades, nunca las contraseñas. Configura release en app y wear, las reglas de
ProGuard y el versionado compartido, y ejecuta el smoke test completo en los dispositivos reales.
Despliega la web con el script nuevo. Cierra con fast-forward a main y norma y actualiza la sección 3
del plan.
```

---

### Fase 9. `main` público (opcional)

**CANCELADA el 2026-09-05 por decisión de Agustín:** el repositorio es privado, sin `LICENSE` (todos los derechos reservados) y `main` sigue idéntica a `norma`. Se conserva el texto por si la decisión cambia; ninguna fase depende de ella.

**Objetivo.** Que `main` sea instalable y comprensible por cualquier persona, sin datos ni nombres de la familia.

**Alcance.**
1. *Product flavors* `demo` y `norma`: el asset de la base depende del *flavor*; la golden real queda solo en `norma`.
2. Base demo anonimizada generada por el ETL con un Excel sintético (`create_fake_excel.py` como punto de partida) y nombres genéricos.
3. Genericizar los nombres de la familia en los 30 archivos Kotlin (comentarios, reglas de canonicalización, alias del `AliasResolver`, `Enums.kt`).
4. Extracción completa a `strings.xml` (español) y `README.md` público con capturas.
5. Divergencia oficial: `main` = demo, `norma` = real; documentar el flujo de *merge* de `main` a `norma`.

**Criterio de salida.** Instalación fresca del *flavor* demo sin rastro de datos reales; `norma` sigue construyendo la base real.

**Prompt de arranque.**
```text
Lee CLAUDE.md, la Fase 9 de PLAN_MAESTRO_CIERRE.md y la memoria finance-app-branch-model. Implementa
los flavors y la base demo en develop, verifica que la golden real no se filtra al flavor demo y
actualiza la política de ramas. Actualiza la sección 3 del plan.
```

---

### Fase 10. Semilla definitiva desde el Excel 2.5 (2), 100 % clasificada

**Contexto.** Norma siguió capturando en el Excel y el 2026-09-05 entregó `Copy of presupuesto 2.5 (2).xlsx`, ya descifrado por Agustín ese mismo día y guardado en la raíz del repo (ignorado por git, patrón `Copy of presupuesto*.xlsx`). Diff verificado contra la versión (1): 38 hojas frente a 37; hoja nueva «Quincena 1 al 15 Septiembre» (72 filas con datos); tres hojas existentes con cambios («16 al 30 de Julio», 23 filas distintas; «1al 15 Agosto», 28; «16 al 30 Agosto», 31); la hoja «1al 15 Julio 2026» no cambió desde el export del 7 de julio, por lo que hay que revisar si quedó incompleta. Norma decidió no reunir exports de comercios (Amazon, Mercado Libre, PayPal, SAT); la única fuente externa además del Excel son los estados de cuenta bancarios. Esta fase y la siguiente son las últimas del plan por decisión de Agustín: Norma no recibe preguntas hasta que arranque este chat.

**Objetivo.** Una base inicial generada por el ETL desde el Excel nuevo, con las quincenas nuevas y con el 100 % de las líneas clasificadas (categoría, beneficiarios, pagador, método de pago) antes de crearla. Ninguna decisión de baja confianza queda abierta; ninguna línea cae en OTHER por omisión.

**Alcance.**
1. Trabajo técnico previo, sin Norma: diff celda a celda del Excel (2) contra el (1) sobre las cuatro hojas afectadas y la de julio sospechosa; cambios de ingreso (sueldo de $85,000 desde agosto de 2026, situación de Benjamín); lista de conceptos nuevos sin regla (`generate_questionnaire.py`). Registrar todo en `seed/REPORTE_SEMILLA_2026-09.md`. Solo entonces se le envía a Norma `seed/SOLICITUD_A_NORMA_2026-09.md` con la lista anexa y se anota la fecha de envío en ese archivo.
2. Extender el ETL a las quincenas nuevas (julio a septiembre de 2026 y las que traiga el archivo), respetando huecos deliberados y el modelo de futuro vigente (solo la quincena activa, futuro por plantillas).
3. Clasificación completa antes de generar: incorporar las respuestas de Norma (§5.3) a `scripts/etl/attribution_rules.json` y a `CANONICAL_CONCEPT_RULES`; resolver cada concepto nuevo con ella; eliminar todas las reglas marcadas ⚠️ sustituyéndolas por la decisión confirmada; renombrar al deudor "Por identificar". Unificar el estado de MSI del ETL con el runtime (`PAID_OFF`).
4. Estados de cuenta de julio a septiembre de 2026: extraer el gold de los PDF que Norma entregue (mismo método que `Estados de Cuenta/_gold`), clasificarlos (categoría y beneficiarios) y extender `app/src/main/assets/seed_statements.json` a una versión 3 con anti doble conteo contra el Excel, para que también nazcan en la base inicial.
5. Regenerar el asset con `BUDGET_TODAY` fijado al día del corte acordado con Norma; correr `scripts/verify_db.py`; promover el asset a `seed/budget_database.golden.db` con su `.sha256`; `bash scripts/check_seed_integrity.sh` en verde.
6. Instalación fresca en FinanceFold: conteos de gastos, quincenas e ingresos iguales a los del reporte; quincena ACTIVE correcta; calendario sin duplicados; checklist de estados coherente con la versión 3; tutorial arranca.
7. Actualizar `seed/README.md`, `seed/TABLA_CLASIFICACION_2026-09.md`, `CLAUDE.md` (semilla vigente) y la memoria `finance-app-semilla-norma`.

**Fuera de alcance.** Cualquier trabajo sobre la ingesta multi-documento: la feature se conserva funcional tal como está, sin solicitud de archivos ni desarrollo nuevo.

**Criterio de salida.** Cero reglas ⚠️ y cero conceptos sin regla en el reporte; golden promovida e íntegra; instalación fresca verificada; la base no se ha subido todavía a Firestore (eso ocurre en la Fase 11).

**Prompt de arranque.**
```text
Lee CLAUDE.md (pipeline de datos y semilla golden), la Fase 10 y la sección 5 de
PLAN_MAESTRO_CIERRE.md, seed/README.md, seed/REPORTE_SEMILLA_2026-07.md y las memorias de semilla y
auditoría. El Excel fuente es "Copy of presupuesto 2.5 (2).xlsx" en la raíz, ya descifrado. Haz
primero el trabajo técnico que no requiere a Norma (diff de hojas, ETL de las quincenas nuevas,
cuestionario de conceptos nuevos); después pide al usuario que le envíe la solicitud de
seed/SOLICITUD_A_NORMA_2026-09.md con la lista anexa. No generes la base con ninguna duda abierta.
Corre el ETL con PYTHONUTF8=1 y BUDGET_TODAY fijado al día del corte. No toques Firestore en esta
fase. Al terminar promueve la golden, verifica la instalación fresca en FinanceFold y actualiza la
sección 3 del plan.
```

---

### Fase 11. Puesta en producción con Norma (cutover)

**Objetivo.** Que la base definitiva quede en la nube y en el Fold de Norma, y que ella opere la app sola desde ese día.

**Alcance.**
1. Reconfirmar con Norma el día del corte y que no capturó nada en la app; purgar Firestore y resembrar desde la golden nueva con `scripts/admin/purge_and_reseed.py` (única situación en la que se acepta un purge).
2. Cutover de identidad: Norma vincula Google en Perfil (`normly@gmail.com`, uid reservado), se confirma el rol OWNER, se instala el build de *release* en Fold y reloj, y se otorgan accesos a los demás miembros (web como Colaborador o app como Administrador).
3. Reconciliar el saldo de apertura de cada cuenta al día del corte con "Reconciliar saldo" y curar plantillas recurrentes (activar, pausar, montos vigentes).
4. Sesión de acompañamiento: tutorial, primera captura, confirmación de un PLANNED, importación del estado de cuenta del mes, exportación de un reporte. Registrar dudas para mantenimiento.
5. Documentar el corte en `seed/` y alinear la rama `norma`.

**Criterio de salida.** Norma opera una quincena completa sin intervención; sync verificado con el Pixel de Agustín; el Excel deja de usarse. Con esto el plan queda cerrado.

**Prompt de arranque.**
```text
Lee CLAUDE.md, la Fase 11 y la sección 5 de PLAN_MAESTRO_CIERRE.md, y las memorias de consolidación
y bugs de julio. Confirma con el usuario el día del corte antes de ejecutar purge_and_reseed; nunca lo
corras sin esa confirmación explícita. Ejecuta los 5 puntos, verifica el sync con el Pixel del
usuario y documenta el corte en seed/. Actualiza la sección 3 del plan.
```

---

## 5. Qué se necesita de Norma para alimentar su base de datos

Existe una versión en lenguaje llano para enviarle directamente: `seed/SOLICITUD_A_NORMA_2026-09.md`. Aquí queda la versión técnica, agrupada por quién puede resolver cada cosa.

Decisiones ya tomadas el 2026-09-05: Norma siguió en el Excel y entregó la versión (2), ya descifrada en la raíz; no quiere reunir exports de comercios (Amazon, Mercado Libre, PayPal, SAT ni Google), así que la única fuente externa son los estados de cuenta bancarios; la ingesta multi-documento se conserva funcional en la app, sin trabajo nuevo ni solicitud de archivos. Además, nada de esta sección se le pide antes de arrancar la Fase 10: primero se termina y publica el software.

### 5.1 Acciones que solo ella puede hacer en su teléfono
1. Vincular su cuenta de Google en Perfil (`normly@gmail.com`, ya reservada en Firebase Auth) antes de cualquier reinstalación.
2. Conceder los permisos que quiera usar: notificaciones, acceso a notificaciones bancarias (captura automática), calendario (espejo opcional), micrófono (voz).
3. Cuando exista la descarga in-app (Fase 4), bajar el modelo de IA por Wi-Fi si desea el asistente.

### 5.2 Datos para la base inicial
1. El Excel (2) ya está descifrado en la raíz; como Norma seguirá capturando en Excel hasta el día del corte, entrega la versión final ese día para que la semilla no nazca vieja.
2. Estados de cuenta (PDF) de julio a septiembre de 2026, y de los meses que pasen hasta el corte, de los diez emisores ya conocidos (BBVA, BanCoppel, Banamex Clásica, Banamex MiCuenta, DiDi Card, Klar, Liverpool, Mercado Pago, Sears, Walmart Inbursa), más los de Coppel y Mercado Libre, que nunca se han tenido.
3. Saldo real de cada cuenta y tarjeta al día del corte que se acuerde (para reconciliar aperturas).

### 5.3 Preguntas de clasificación (respuesta breve por cada una; sin ellas no se genera la base)
1. "Pau Raúl" $500 (2025): ¿quién es Raúl y a qué corresponde?
2. "Berna" / "Bernardo" $8,100: ¿qué servicio presta y a quién beneficia?
3. "Marco y Omar" $2,120: ¿abono al préstamo de Omar u otro concepto?
4. "Cochecito" $8,000 (junio de 2026): ¿reparación, gasolina u otra cosa, y de quién es el coche?
5. "Changan" $5,000 (julio de 2026): ¿enganche, servicio o regalo?
6. Deuda por cobrar "Deben" $3,600 con saldo $1,900 (medicina y rotafolio, octubre de 2025): ¿quién debe?
7. Dudas nacidas de los estados de cuenta: CENEVAL ¿para Santiago?; CirculoDerm ¿qué paciente?; ropa de universitarios ¿David?; cargo WIM $249 y Google CR $59 ¿de quién?; cargos de Amazon en tarjeta ¿de quién fueron?
8. Confirmar la regla vigente "Norma paga todo desde octubre de 2025 salvo Spotify" y si sigue sin ingreso Benjamín.
9. Confirmar el sueldo de $85,000 desde agosto de 2026 y su periodicidad.
10. Cada concepto nuevo que aparezca en las hojas de julio a septiembre sin regla previa (la Fase 10 produce la lista con `generate_questionnaire.py` y se le manda en un solo mensaje).

### 5.4 Exports de comercios: descartados
Por decisión de Norma (2026-09-05) no se piden los exports de Amazon, Mercado Libre, Mercado Pago, PayPal, SAT ni Google descritos en `seed/CATALOGO_ARCHIVOS_SOLICITABLES.md`. El catálogo se conserva como referencia histórica y la feature de ingesta multi-documento sigue disponible en la app por si algún día cambia de opinión.

### 5.5 Datos de referencia del hogar
1. Qué miembros tendrán acceso y por qué vía (Benjamín con la app como Administrador; hijos vía web como Colaboradores) y sus correos de Google.
2. Límite de crédito, día de corte y día de pago de cada tarjeta si cambiaron desde los últimos estados.
3. Costo por línea del plan telefónico (Norma, David, Normita, Santiago).

### 5.6 Rutina mensual a partir del corte
Subir cada estado de cuenta bancario en el checklist "Estados del mes" durante la semana posterior al corte; confirmar los pagos programados desde el Calendario; capturar el efectivo el mismo día (teléfono o reloj). Con eso la base se alimenta sola, sin exports de comercios, y el Excel deja de existir.

---

## 6. La función `/design` de Claude y su papel en la mejora de UX

### 6.1 Qué es
`/design` es una *skill* de Claude Code en *research preview* (desde la versión 2.1.234, agosto de 2026, disponible en los planes Pro, Max, Team y Enterprise) que trae a la sesión de código el flujo de *artboards* de Claude Design. Se invoca con un brief en lenguaje natural, por ejemplo `/design redesign the composer based on what people actually use it for`; Claude publica un lienzo de *artboards* editables como artefacto, imprime el enlace, y el usuario abre el lienzo, elige una opción, la ajusta en el propio lienzo y le indica a Claude cuál implementar. Toda la iteración, del boceto al código, ocurre en la misma sesión (Anthropic, 2026; explainx.ai, 2026).

`/design-sync` (junio de 2026) es la pieza complementaria: sincroniza en dos sentidos un *sistema de diseño* entre el repositorio y un proyecto de Claude Design de tipo *design system*. En sentido *pull* trae tokens (color, tipografía, espaciado, radios) y *stubs* de componentes; en sentido *push* sube implementaciones y variantes del código al proyecto para que el lienzo herede la identidad real del producto. En esta máquina la herramienta `DesignSync` está disponible; opera con el login de claude.ai o con `/design-login` cuando no lo hay, y exige el orden `list_projects` → `finalize_plan` → `write_files`.

### 6.2 Limitaciones que condicionan el plan
Consume mucho contexto porque genera varios *artboards* a la vez; la herencia automática del sistema de diseño no está confirmada en la vista previa, por lo que hay que alimentarla con tokens explícitos (un `DESIGN.md` actualizado y un proyecto sincronizado con `/design-sync`); y produce HTML, no Compose, así que la implementación en Kotlin sigue siendo trabajo del chat de la sub-fase 6b. La *skill* `design` que aparece en el listado de esta máquina es un plugin de terceros para logotipos y banners y no guarda relación con `/design`.

### 6.3 Cómo se usa en este plan (Fase 6)
1. Se corre desde una sesión interactiva de Claude Code Desktop (en una sesión no interactiva la *skill* no está disponible).
2. Antes de pedir *artboards* se actualiza `DESIGN.md` con los tokens reales y se publica el sistema de diseño con `/design-sync`, para que las propuestas nazcan con Google Sans Flex, la paleta armonizada y las formas de `BudgetShapes`.
3. Cada brief de `/design` se ancla en una fricción medida en la auditoría (por ejemplo: "rediseña la hoja de captura para que un gasto en efectivo del día se registre en tres toques a fontScale 1.3 con bold, conservando la atribución dual") y pide entre dos y cuatro opciones; nunca "hazlo bonito".
4. Las opciones elegidas se registran con su URL y justificación en `ui_reference/design_2026-09/DECISIONES.md`, que es el contrato de la sub-fase 6b.

---

## 7. Decisiones abiertas que corresponden a Agustín

1. **Quick Tap.** Resuelta el 2026-09-05: se implementa (Fase 6, puntos 5 y 8) y `SYSTEM_ALERT_WINDOW` se conserva para su overlay.
2. **Ubicación en segundo plano.** Resuelta el 2026-09-05: se conservan el permiso y el nivel "Persistente" (la evidencia del hallazgo 14 estaba incompleta: el nivel sí lo usa).
3. **Licencia y `main` público.** Resuelta el 2026-09-05: repositorio privado, sin `LICENSE`; la Fase 9 queda cancelada y `main` sigue idéntica a `norma`.
4. **Origen de descarga del modelo Gemma.** Firebase Storage del proyecto (simple, con costo de egreso) o bucket propio.
5. **Canal de distribución.** APK firmado por Drive (inmediato) o pista interna de Play (requiere cuenta de desarrollador y política de permisos).
6. **Alcance del *netting*.** Resuelta el 2026-09-07: quincena activa por defecto con selector de rango. La sección "Balance entre adultos" es informativa y de solo lectura, solo incluye miembros `PAYER_ADULT`, reparte con la cuota justa ponderada por ingreso declarado, filtra `settlement_status = 'NONE'` para no contar dos veces lo que ya tiene mecanismo propio, oculta netos menores a 50 pesos y advierte que fuera de la quincena la diferencia se acumula. Se retiró el andamiaje del netting anterior (`markNetted`, `observeNettingRows`, el valor `NETTED` y el stub de Firestore). Nota para la Fase 10: con la semilla actual, cualquier ventana mayor que una quincena da cifras grandes porque Norma figura como pagadora de casi todo el histórico; por quincena las cifras son modestas y accionables.
7. **Observabilidad.** Crashlytics (envía trazas a Google) o registro local exportable (sin salida de datos).
8. **Reseed frente a corrección in-app.** Resuelta el 2026-09-05: Norma siguió en el Excel, así que la Fase 10 resiembra desde `Copy of presupuesto 2.5 (2).xlsx` y la base inicial nace con toda la clasificación resuelta. Por decisión del mismo día, esa fase y el cutover (11) son las últimas del plan. Queda por fijar con ella el día del corte cuando llegue el momento.

---

## 8. Fuentes

- Anthropic. (2026). *Week 34 · August 17–21, 2026: /design research preview*. Claude Code Docs. https://code.claude.com/docs/en/whats-new/2026-w34
- explainx.ai. (2026). *Claude Code /design Command: UI Artboards (Aug 2026)*. https://explainx.ai/blog/claude-code-design-command-artboards-research-preview-2026
- explainx.ai. (2026). *Claude Design June 2026: Design Systems & /design-sync*. https://www.explainx.ai/blog/claude-design-june-2026-update-design-sync-2026
- AI for Anything. (2026). *Claude Design /design-sync: The Two-Way Bridge Between Design and Code*. https://www.aiforanything.io/blog/claude-design-sync-claude-code-guide-2026
- Piebald-AI. (2026). *tool-description-designsync.md*. https://github.com/Piebald-AI/claude-code-system-prompts/blob/main/system-prompts/tool-description-designsync.md
