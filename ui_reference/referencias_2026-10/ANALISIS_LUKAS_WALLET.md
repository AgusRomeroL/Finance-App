# Lukas y Wallet frente a Finance-App: qué nos falta

Análisis del 2026-10-30 sobre un Pixel 7 (Android 17). Todo lo que sigue está descrito con nuestras palabras: no entra al repositorio ningún texto, ícono, diseño, recurso ni código de estas apps, y las capturas de pantalla se quedan fuera de git (`C:\dev\finance-app-offdrive\referencias\2026-10\`).

## Cómo se hizo

- **Herramientas.** El MCP de REA no estaba configurado en esta máquina (el único servidor MCP de usuario es `artemis`, y no conectó). Se usaron los equivalentes del SDK: `adb` para sacar los APK, recorrer las pantallas con volcados de `uiautomator` y toques, y capturar; `aapt2` para el manifiesto y los recursos. Apktool no hizo falta.
- **Sin descompilar.** No se descompiló ninguna de las dos apps. El inventario sale solo de metadatos del paquete: manifiesto, recursos XML (widgets, accesos directos), archivos de versión de bibliotecas en `META-INF/` y la lista de complementos nativos de Lukas. Por eso no hizo falta revisar los términos de uso sobre ingeniería inversa: todo lo demás es uso normal de la app.
- **Datos de prueba.** Los mismos en las dos apps y en MXN (cuentas, sueldo quincenal, cinco recurrentes, cinco gastos de la quincena, una compra a 6 MSI, una transferencia y tres presupuestos). En Wallet, que tiene los datos reales de Agustín y sincroniza con su nube, todo se cargó en tres cuentas nuevas con el prefijo **PRUEBA REA** (Efectivo, Débito y Tarjeta: una sola cuenta no admite una transferencia ni una tarjeta con límite), y el panel se filtró a esas tres. Las pantallas con datos reales no se guardaron y sus cifras no se anotaron.
- **Experimentos.** La fórmula del disponible de Lukas se dedujo registrando datos uno por uno y leyendo la cifra después de cada paso; la explicación que la propia app muestra se leyó al final, solo para contrastar.

## Resumen

Lukas es una app pequeña y muy enfocada: un solo saldo (no tiene cuentas), captura por IA en la nube (texto, voz y foto) y un **"Seguro para Gastar"** que reserva por adelantado lo programado y lo que falta por gastar de cada límite. Wallet es una plataforma grande: cuentas de todo tipo, conexión bancaria, pagos planificados con un selector de recurrencia de calendario, presupuestos con pronóstico, estadísticas profundas y muchos módulos accesorios.

Finance-App ya cubre o supera a las dos en lo que importa para el hogar: atribución dual, quincena con cierre, MSI como entidad, notificaciones bancarias, estados de cuenta, voz on-device, Quick Tap, exportación completa, multi-hogar y reloj. Lo que de verdad nos falta, ordenado por valor para Norma:

1. **Dos defectos propios que destapó la comparación** (no son funciones nuevas): la cifra "Por día" de la tarjeta Ritmo no descuenta lo reservado y no cuadra con el "Disponible", y los planes MSI dados de alta en la app o importados de un estado de cuenta nunca generan sus cuotas.
2. **Avisos de presupuesto por categoría**, con pronóstico de "te vas a pasar": el tope por categoría ya existe, la consulta de categorías excedidas también, pero nadie la llama.
3. **Presupuesto de la quincena editable** desde la interfaz: hoy solo llega por la semilla o por Firestore.
4. **Deshacer** tras guardar o borrar, y recordar la última cuenta usada.
5. **Widget con cifras** (disponible y gastado hoy), **bloqueo con huella** y **modo privacidad** que también calle a TalkBack.
6. **Recurrencias con fin** (por fecha o número de veces), "cada N meses" y descartar o posponer una ocurrencia de verdad.
7. **Captura por foto de ticket** y **proyección de saldo a 30, 60 y 90 días**.

La conexión bancaria de Wallet no se recomienda: depende de un agregador de pago, en México la lista de bancos frecuentes tenía cinco y metería datos de terceros; las notificaciones bancarias y los estados de cuenta ya resuelven ese hueco.

## Inventario de Lukas

`com.jhotech.smartspend` 0.18.0 (versionCode 54), minSdk 24, targetSdk 36, instalada desde Play. Es una app web (React empaquetada con Capacitor dentro de un WebView) con complementos nativos.

| Aspecto | Qué declara o trae |
|---|---|
| Permisos | Internet y estado de red, cámara, micrófono, biometría (y huella heredada), notificaciones, arranque al encender, vibración, wake lock, servicio en primer plano, facturación de Play, mensajería de Firebase, identificador de publicidad y APIs de AdServices |
| Widgets | Uno, `AppWidget` clásico con `RemoteViews`, 4×2, se refresca cada 30 min: "Hoy" (gastado hoy), "Restante" (el Seguro para Gastar) y un botón de privacidad |
| Mosaico de Ajustes rápidos | Uno: abre la lista de registros comunes desde cualquier app |
| Accesos directos | Uno estático: registros comunes (`smartspend://common`) |
| Lector de notificaciones | No |
| App de reloj | No |
| Biometría | Complemento nativo de biometría de Capacitor sobre `androidx.biometric` |
| Captura | Cámara (complemento con editor de imagen), grabadora de voz (el audio va a la IA en la nube), sin ML Kit |
| Segundo plano | WorkManager y Room vienen en el paquete; notificaciones locales que se reprograman al arrancar |
| Cifrado | No se ve biblioteca de cifrado en el paquete |
| Negocio | AdMob, RevenueCat (Pro), reseña dentro de la app |

Lo que se ve al usarla:

- **Arranque sin cuenta.** Elige idioma, preselecciona 22 categorías y corre un tour de cinco pasos con resaltados. Toma la moneda del idioma del teléfono (MXN). Según su propia pantalla de inicio de sesión, aun como invitado los movimientos se sincronizan de forma anónima con su nube, y la cuenta (Google o Apple) solo sirve para no perderlos.
- **Periodo.** Mensual gratis. Semanal, quincenal (1 al 15 y 16 a fin de mes), trimestral, semestral, anual y personalizado son de pago.
- **Sin cuentas.** El dinero inicial se registra como un ingreso llamado "Saldo inicial". No hay transferencias, tarjetas ni MSI.
- **Captura manual.** Botón "+", "Gasto manual", importe con el teclado del sistema (con el foco puesto), categoría con buscador y Guardar: **5 toques** más teclear el importe. Campos opcionales: fecha con atajos Ayer, Hoy y Mañana, descripción, etiquetas, estado (pagado, pendiente o planeado), nota y "hacer recurrente". Tiene "Guardar y agregar otro".
- **Asistente con IA (nube).** Un texto con varios movimientos ("súper 1850 el 20, farmacia 420 el 22…") se convierte en un lote editable con total, se corrige y se guarda todo junto: **4 toques para N movimientos**. Acertó montos y fechas y falló una categoría (restaurante como mercado). Cada petición gasta el 10 % del cupo mensual gratis.
- **Foto del ticket.** Pide la cámara al usarla, muestra un recuadro para encuadrar, deja capturar o elegir de la galería y procesa: devuelve un solo movimiento con descripción, categoría, fecha y total (no partidas). Con un ticket sintético leyó bien total, fecha y categoría.
- **Voz.** Graba hasta un minuto, lo manda a la IA y cae en la misma revisión.
- **Registros comunes.** Gastos completos guardados; desde el acceso directo del ícono o el mosaico se registran con **un toque** y un aviso de guardado con botón Deshacer. Una pantalla explica cómo poner el acceso directo y el mosaico.
- **Límites (pestaña Plan).** Uno por categoría, por periodo, renovable. No hay presupuesto total. Al excederse, la pestaña muestra una sección de alertas con etiqueta de urgente y "excedido por $180"; **no llegó notificación**. En la misma pestaña viven las metas de ahorro.
- **Recurrentes.** Diario, semanal, quincenal (dos días configurables, por defecto 15 y fin de mes), mensual (cada N meses, día del mes, último día si el mes es corto) y anual, con fecha de fin. Lo programado aparece en la lista como movimiento "virtual" con un botón para registrarlo antes; la única notificación configurable es el aviso un día antes.
- **Privacidad y bloqueo.** Botón de ojo con tres niveles: todo visible, ocultar el resumen, ocultar todo; las cifras se cambian por guiones también para el lector de pantalla. La huella se activa con un interruptor que la pide.
- **Exportar y respaldar.** CSV de movimientos, categorías, límites o todo; reporte PDF por rango de fechas que sale por la hoja de compartir; respaldo local automático cada 24 horas (guarda los últimos cinco, máximo 2 MB, y uno antes de operaciones delicadas) con restauración. La búsqueda filtra por texto, tipo y orden y exporta el resultado a CSV o PDF.
- **Estadísticas.** Tarjetas personalizables (promedio de gasto diario, categoría principal, día de la semana que más se gasta, comparación con el periodo anterior), dona por categoría, ingresos contra gastos, tendencia y **saldo futuro a 30, 60 y 90 días**, con orden editable.

### La fórmula del "Seguro para Gastar" de Lukas

Deducida con experimentos y confirmada después con la explicación de la app:

```
seguro = (ingresos del periodo − gastos del periodo + arrastre del periodo anterior)
         − gastos programados que caen antes de que termine el periodo
         − lo que falta por gastar de cada límite activo
por día = seguro ÷ días que faltan, contando hoy
```

- Un **ingreso programado no suma**: el sueldo del día 31 no movió la cifra hasta registrarse.
- Un límite que termina después del periodo se reserva en proporción a los días.
- Mantener presionada la tarjeta cambia entre por día, por semana y por mes.

Secuencia medida (periodo mensual, 30 de octubre, 2 días restantes):

| Paso | Seguro | Por día |
|---|---:|---:|
| Saldo inicial 20,500 | 20,500 | 10,250 |
| + sueldo del 15 (20,000); el del 31 queda programado | 40,500 | 20,250 |
| + gasolina quincenal (900 pagado el 15 y 900 programado el 31) | 38,700 | 19,350 |
| + renta, internet, Netflix y luz (ya pagados en octubre) | 29,162 | 14,581 |
| + súper, farmacia, restaurante y Uber (lote de IA) | 25,947 | 12,973.50 |
| + regalo 1,200 | 24,747 | 12,373.50 |
| + límites Mercado 4,000 y Restaurantes 1,500 (sin gastar: 2,150 y 720) | 21,877 | 10,938.50 |
| + restaurante 500, dentro del límite | 21,877 | 10,938.50 |
| + restaurante 400, que excede el límite por 180 | 21,697 | 10,848.50 |

Los dos últimos pasos son la clave: un gasto dentro de un límite **no mueve** la cifra (ya estaba reservado) y solo el exceso la baja.

## Inventario de Wallet

`com.droid4you.application.wallet` 9.3.13 (versionCode 90184), minSdk 32, targetSdk 36. App nativa (Views, Compose y Material 3, inyección con Dagger, réplica con Couchbase Lite).

| Aspecto | Qué declara o trae |
|---|---|
| Permisos | Internet, red y Wi-Fi, ubicación fina y aproximada, cámara, contactos, cuentas y credenciales, almacenamiento heredado, biometría, notificaciones, servicio en primer plano de sincronización, arranque al encender, accesos directos del lanzador, facturación, reconocimiento de actividad, identificador de publicidad y AdServices |
| Widgets | Tres `AppWidget` clásicos con `RemoteViews`: saldo (configurable, muestra saldo, gastos e ingresos y un botón "+"), alta de un toque y alta rápida configurable con una plantilla (el de un toque declara también la pantalla de bloqueo) |
| Mosaico de Ajustes rápidos | No |
| Accesos directos | Uno estático: nuevo registro |
| Lector de notificaciones | Sí: "Google Pay Sync", que registra solo los pagos hechos con Google Pay |
| Servicios | Sincronización bancaria, réplica inicial, mensajería de Firebase, geocercas y reconocimiento de actividad |
| App de reloj | No |
| Biometría | `androidx.biometric`; PIN al abrir y huella encima del PIN |
| Captura | Escáner de códigos de barras (tarjetas de lealtad), recorte de imagen, selector de lugares de Google; sin ML Kit |
| Segundo plano | WorkManager |
| Cifrado | No se ve biblioteca de cifrado; trae su propio SQLite para Couchbase Lite |
| Negocio | Facturación de Play (Premium, licencia vitalicia en la cuenta de Agustín), MoEngage, Analytics y Remote Config |

Lo que se ve al usarla:

- **Cuentas.** Once tipos (general, efectivo, cheques, tarjeta, ahorro, bono, seguro, inversión, préstamo, hipoteca, con sobregiro). La tarjeta pide límite, cómo mostrar el saldo (deuda o crédito disponible) y día de pago; **no pide día de corte**. Cada cuenta admite aviso de saldo mínimo y máximo. Una cuenta nueva puede nacer conectada al banco, ligada a Google Pay, de inversión, de importación de archivos o manual.
- **Filtro del panel.** Tocar una cuenta la deja como única seleccionada; mantenerla presionada la suma a la selección. Panel, registros y estadísticas siguen esa selección.
- **Captura manual.** Botón "+", "Nuevo registro", importe con un teclado calculadora propio, categoría (frecuentes, árbol o buscador) y Guardar: **5 toques** más los dígitos; la cuenta es la última usada. El detalle suma nota, etiquetas, pagador, fecha y hora, tipo de pago, garantía, estado (liquidado, pendiente, conciliado), lugar y un adjunto de recibo (solo se adjunta, no se lee).
- **Plantillas.** Aparecen en el menú del "+" y abren el formulario relleno: **3 toques**. El widget de alta rápida con plantilla: **2 toques**.
- **Transferencia.** El mismo formulario con cuenta de origen y destino; por defecto propone dos cuentas cualesquiera, así que hay que cambiar ambas. En la lista aparece como dos filas.
- **Pagos planificados.** Ingreso, gasto o transferencia, únicos o recurrentes, con un selector de calendario: diario, semanal, mensual (por día o por "el último lunes"), anual, cada N, y fin nunca, en una fecha o tras N veces. **No hay quincenal**: el sueldo y la gasolina quincenales hubo que darlos de alta como dos mensuales (día 15 y día 31). Aviso el mismo día o 1, 3 o 7 días antes. Cada ocurrencia se confirma, se pospone o se descarta. Ojo: por defecto (opción "recomendada") los pagos planificados **se convierten solos en registros** al llegar su fecha; esperar la confirmación es la opción secundaria.
- **MSI.** No existe. La pantalla a 6 MSI se aproximó con un pago planificado de 1,000 por seis veces en la tarjeta; la compra de 6,000 no ocupa el límite.
- **Presupuestos.** Semanal, mensual, anual o único, con varias categorías del árbol, cuentas y etiquetas. Dos avisos: "va a pasarse" (por pronóstico) y "se pasó". La tarjeta muestra porcentaje, "en camino", gasto pronosticado al cierre y promedio diario. Al exceder Restaurantes marcó "se pasó" y "−180 de 1,500"; **no llegó notificación** en los 50 minutos siguientes.
- **Panel.** Seis indicadores: saldo, flujo de los últimos 30 días, panorama de los próximos 7 días, gasto, uso del crédito (19 % = deuda de la tarjeta entre su límite) y deuda. El panorama marcó **−29,900** cuando en esa ventana había +20,000 de sueldo y −9,900 de pagos: suma el ingreso planificado como si fuera salida. Es una incoherencia que conviene no copiar.
- **Estadísticas.** Saldo, panorama (pagos planificados por categoría, línea de tiempo y **previsión de saldo a 30 días** que contesta "¿me alcanzará?"), flujo, gasto, crédito y reportes (tabla de flujo con promedios por día y por registro, libro de ingresos y gastos por categoría contra el periodo anterior), en rangos de 7 días a un año.
- **Ocultar montos.** Interruptor en el menú lateral que difumina las cifras en pantalla, pero **el árbol de accesibilidad las sigue leyendo**: TalkBack (o cualquiera que lea la pantalla) las dice en voz alta.
- **Conexión e importación.** Conexión bancaria con México (entre los frecuentes: American Express, Banorte, Santander, Scotiabank y Wise), Google Pay Sync, importación de CSV, Excel y OFX por correo, y reglas automáticas que asignan categoría y etiquetas y reconocen transferencias.
- **Ajustes.** Filtros guardados, día de inicio del mes contable, módulo al abrir, y notificaciones: recordatorio diario a una hora, límites de cuenta, registros sin confirmar, presupuestos, planificados, deudas, importaciones, ingresos grandes y tipo de cambio.
- **Accesorios.** Deudas, metas, listas de compras, garantías, tarjetas de lealtad, grupos compartidos con permisos, divisas, inversiones y módulos de facturas y pedidos.
- **No encontrado:** captura por voz, lectura de tickets y exportación desde la app (el menú de registros solo muestra el saldo por fila y los planificados).
- **Widgets en pantalla.** El de saldo se configuró con todas las cuentas, así que no se documentó su contenido. El de alta rápida abrió el formulario relleno con la plantilla, pero con la categoría equivocada ("ingreso" en vez de la de la plantilla); el de un toque no abrió nada visible.

## Finance-App frente a las dos

Las rutas cuelgan de `app/src/main/java/mx/budget/`, salvo `res/` (que es `app/src/main/res/`) y `firestore.rules` (raíz del repositorio). Toda referencia se verificó sobre `develop` (`f170011`).

### Lo que ya tenemos completo

| Función | Evidencia |
|---|---|
| Desglose escrito del disponible (entró − gastado − reservado), redondeado para que cuadre | `ui/dashboard/DashboardScreen.kt:2647` |
| Transferencias que mueven los dos saldos, con reversa y en el libro mayor | `data/repository/impl/TransferRepositoryImpl.kt:39`, `ui/wallets/WalletTransferSheet.kt:150` |
| Voz con `SpeechRecognizer` en el dispositivo y parser determinista a la bandeja | `ui/search/SpeechRecognizerController.kt:127`, `ui/capture/VoiceCaptureActivity.kt:103` |
| Gastos repetidos de un toque, acceso directo, mosaico, panel flotante (Quick Tap) | `ui/capture/CaptureBottomSheet.kt:324`, `res/xml/shortcuts.xml:8`, `ui/quicktap/OverlayService.kt:136` |
| Exportación PDF, XLSX, CSV y respaldo con restauración validada | `ui/profile/ExportSection.kt:120`, `data/backup/DatabaseBackupManager.kt:46` |
| Estados del movimiento con tono visual propio para lo planeado | `data/local/entity/ExpenseEntity.kt:136`, `ui/theme/AmountSemantics.kt:79` |
| Ubicación del gasto, opt-in por nivel | `data/local/entity/ExpenseEntity.kt:162`, `data/location/LocationProvider.kt:36` |
| Multi-hogar con roles e invitaciones | `data/remote/MembershipRepository.kt:287`, `firestore.rules:16` |

Además tenemos lo que ninguna de las dos tiene: atribución dual en basis points, ciclo de quincena con cierre y congelación, MSI como entidad, lectura de notificaciones bancarias de cualquier banco permitido, importación de estados de cuenta con LLM, reloj con tiles y complications, y asistente on-device.

### Defectos propios que destapó la comparación

- **"Por día" no cuadra con "Disponible".** La tarjeta Ritmo calcula su disponible como ingreso menos lo pagado, sin restar lo reservado (`ui/dashboard/DashboardScreen.kt:1879`), y lo divide entre los días restantes (`:1888`). El "Disponible" del héroe sí resta lo reservado (`data/quincena/QuincenaFigures.kt:35`). Con planeados pendientes, el "por día" promete más dinero del que hay. Es justo lo que Lukas hace bien.
- **Los MSI nuevos nunca generan cuotas.** El materializador salta el plan si no tiene categoría (`data/installments/InstallmentMaterializer.kt:70`, `categoryId = plan.categoryId ?: continue`), y ni el alta desde Cuentas (`ui/wallets/WalletsScreen.kt:417`) ni la importación de un estado de cuenta (`data/statements/StatementImportManager.kt:911`) le ponen categoría (`data/local/entity/InstallmentPlanEntity.kt:114`, nulo por defecto). Solo proyectan los planes de la semilla.

### Tabla de brechas

Valor para Norma: alto, medio o bajo. Esfuerzo: S (horas), M (un par de días), L (una fase).

| Función | Lukas | Wallet | Finance-App | Valor | Esfuerzo | Cómo encajaría |
|---|---|---|---|---|---|---|
| Disponible por día coherente con el principal | Sí: seguro ÷ días restantes | No | **Parcial**: existe "Por día" pero sin restar lo reservado (`ui/dashboard/DashboardScreen.kt:1879`, `:1888`) | Alto | S | Mover el cálculo a `QuincenaFigures` (una propiedad `perDay` sobre `available`) para que tarjeta Ritmo, resumen y chat compartan la cifra. Sin Room. Cambio de cifra animado con `BudgetMotion`; probar con `AutoSizeAmountText` en la configuración fiel del Fold |
| Cambiar la vista entre por día, por semana y por quincena | Sí, con pulsación larga | No | **No** | Medio | S | Alternar con un toque (no con pulsación larga, que TalkBack no descubre); el estado del alternador se anuncia con un `stateDescription` en `strings.xml`; transición con `BudgetMotion` y `LocalReducedMotion` |
| Reservar lo que falta de cada tope de categoría dentro del disponible | Sí | No | **No**: el disponible solo reserva lo planeado (`data/quincena/QuincenaFigures.kt:35`) | Medio | M | Es una decisión de producto, no un hueco técnico: cambia la convención de cifras que hoy comparten tres pantallas. Si se adopta, se hace en `QuincenaFigures` y se escribe en la resta del héroe como un término más |
| Desglose con la lista de cada reserva | Sí ("¿cómo funciona?") | No | **Parcial**: la resta existe pero no lista qué compone lo reservado (`ui/dashboard/DashboardScreen.kt:2647`) | Medio | S | Tocar "Reservado" abre una hoja con los planeados de la quincena; reutilizar la consulta prorrateada de `ExpenseDao`; filas con tono `SCHEDULED` |
| Avisos de presupuesto por categoría (80 % y 100 %) | Solo dentro de la app | Sí, push (no llegó en la prueba) | **Parcial**: tope por categoría y barra ámbar o roja (`data/local/entity/CategoryEntity.kt:83`, `ui/analytics/AnalyticsScreen.kt:1053`); `getCategoriesOverBudget` existe y nadie la llama (`data/local/dao/AnalyticsDao.kt:187`) | Alto | M | Worker periódico que reutiliza esa consulta y notifica una vez por categoría y quincena (marca en DataStore, sin Room); canal propio; acción que abre Analíticas en la categoría. Preferencia en Perfil. Texto de la notificación en `strings.xml` |
| Pronóstico de "te vas a pasar" | No | Sí | **No** | Medio | S | Proyección lineal del gasto por categoría con la fracción de quincena transcurrida (la misma de la tarjeta Ritmo); entra en el aviso anterior y como leyenda en la barra |
| Presupuesto total de la quincena declarable | No (solo por categoría) | Sí | **Parcial**: columnas y arrastre en el rollover (`data/local/entity/QuincenaEntity.kt:65`, `data/quincena/QuincenaRollover.kt:140`), sin pantalla para editarlas | Alto | S | Editor en la lista de quincenas (`ui/quincena/QuincenasScreen`) para ingreso y gasto proyectados; la escritura pasa por `QuincenaLifecycle` para sellar `updated_at` y encolar el kind `QUINCENA`. Sin migración |
| Deshacer tras guardar o borrar | Sí, en registros comunes | No | **No**: los snackbars solo muestran texto (`ui/capture/CaptureBottomSheet.kt:246`) | Alto | S | Snackbar con "Deshacer" que revierte vía repositorio (gasto, atribuciones y outbox en la misma transacción). El deshacer de un borrado tiene que convivir con la lápida ya encolada para no resucitar el gasto en otro dispositivo. El texto de la acción en `strings.xml` |
| Recordar la última cuenta usada | No aplica | Sí | **No**: la cuenta arranca vacía en cada captura (`ui/capture/CaptureViewModel.kt:501`, `:1299`) | Medio | S | Guardar la última cuenta por miembro en DataStore y sembrarla al abrir `CaptureBottomSheet` en modo New; nunca en modo Review, que trae la suya |
| Widget con cifras | Sí: hoy y restante | Sí: saldo, gastos e ingresos | **Parcial**: un widget Glance solo con botones (`widget/BudgetCaptureWidget.kt:41`, `:70`) | Alto | M | Segunda variante Glance (o tamaños responsivos del mismo) con "Disponible" y "Gastado hoy"; refresco al escribir en el repo de gastos vía `updateAll`. Probar en el lanzador del Fold abierto y cerrado; respetar el modo privacidad |
| Bloqueo con huella o PIN | Sí | Sí (PIN más huella) | **No**: `MainActivity` es `ComponentActivity` y no está `androidx.biometric` (`MainActivity.kt:51`) | Medio | M | Pasar `MainActivity` a `FragmentActivity`, pedir `BiometricPrompt` al volver de segundo plano (`lifecycle-process` ya está), con respaldo de credencial del dispositivo. Opt-in en Perfil. Cubrir también Quick Tap y la captura por voz |
| Modo privacidad | Sí, tres niveles, también para TalkBack | Sí, pero TalkBack sigue leyendo | **No**: no hay preferencia ni interfaz (`data/settings/SettingsRepository.kt:31`) | Medio | M | Preferencia en DataStore leída por un `CompositionLocal`; `AutoSizeAmountText` y los montos del dashboard y de Cuentas pintan guiones y su `contentDescription` dice "monto oculto" (en `strings.xml`). Hacerlo bien donde Wallet falla |
| Recurrencias con fin y "cada N meses" | Sí (fecha de fin, cada N) | Sí (fecha, N veces, cada N) | **Parcial**: cinco cadencias fijas, sin fin ni intervalo (`ui/calendar/RecurrenceViewModel.kt:37`, `data/recurrence/RecurrenceMaterializer.kt:190`) | Medio | M | Columnas `interval_months`, `end_date` y `max_occurrences` en `recurrence_template`: **migración Room v21 → v22** idempotente con `addColumnIfMissing`, `createSql` copiado del `22.json`, mapper del pull con doble nombre, y CRUD de la web |
| Descartar o posponer una ocurrencia de verdad | Sí ("revisar") | Sí (confirmar, posponer, descartar) | **Parcial**: posponer solo recorre el aviso 24 h (`data/reminder/ReminderNotifier.kt:57`); descartar uno suelto es borrarlo | Medio | M | El id determinista de la recurrencia haría renacer lo borrado: hace falta una marca de "omitida" por ocurrencia (columna o tabla, misma migración v22) que el materializador respete. Acción nueva en la notificación |
| Crédito disponible de la tarjeta | No | Sí | **Parcial**: barra de "% del límite" (`ui/wallets/WalletsScreen.kt:888`), sin la cifra límite − saldo | Medio | S | Una línea más en la tarjeta de la cuenta; cifra con `AutoSizeAmountText`. Sin Room |
| Alertas de saldo mínimo o máximo por cuenta | No | Sí | **No** (solo la etiqueta de sobregiro, `ui/wallets/WalletsScreen.kt:847`) | Bajo | M | Columna opcional en `payment_method` (migración) o preferencia por cuenta en DataStore; se evalúa al escribir el saldo |
| Captura por foto de ticket | Sí (IA en la nube) | No (solo adjunta) | **No**: el OCR de ML Kit solo sirve a estados de cuenta (`data/statements/StatementTextExtractor.kt:150`) | Medio | M | Selector de foto o cámara del sistema, OCR on-device con el ML Kit que ya está, extracción determinista de total, fecha y comercio (y LLM solo si hay AICore); se crea un `pending_capture` con un `source` nuevo y se abre `CaptureBottomSheet` en modo Review. Nunca insertar directo |
| Texto libre con varios movimientos | Sí (lote con IA) | No | **Parcial**: una propuesta por frase (`data/capture/BankCaptureManager.kt:142`) | Bajo | M | Partir el texto por separadores en el parser determinista y crear varias propuestas en la bandeja; revisión una a una en modo Review |
| Proyección de saldo a 30, 60 y 90 días | Sí | Sí (30 días) | **Parcial**: lista de próximos pagos (`ui/calendar/CalendarViewModel.kt:40`), sin saldo proyectado | Medio | M | Widget nuevo en Analíticas: saldo líquido de hoy más ingresos y planeados futuros, sumando con su signo (no repetir el error de Wallet). Recurrencias proyectadas en memoria, sin materializarlas |
| Recordatorio diario para registrar | No | Sí | **No** (`BudgetApplication.kt:1172`) | Medio | S | Worker diario a una hora elegida en Perfil; la acción abre Quick Tap. Texto en `strings.xml` |
| Arrastre del sobrante al periodo siguiente | Sí | No | **No**: solo se arrastra el presupuesto declarado (`data/quincena/QuincenaRollover.kt:140`) | Medio | M | Decisión de producto: el cierre de quincena ya congela cifras, así que el sobrante puede entrar como término "arrastre" en `QuincenaFigures` de la siguiente. Escribirlo en la resta del héroe |
| Estadísticas: promedio diario, día que más se gasta, orden editable | Sí | Parcial (promedios) | **Parcial**: muchas gráficas, orden fijo (`ui/analytics/AnalyticsScreen.kt:130`) | Bajo | S | Dos tarjetas más con consultas de `AnalyticsDao`; el orden editable puede esperar |
| Búsqueda con filtros y exportar lo filtrado | Sí | Sí (filtros guardados) | **Parcial**: texto y grupo; el CSV es solo por rango (`ui/search/SearchViewModel.kt:35`, `data/export/CsvExporter.kt:43`) | Bajo | M | Filtros de monto y fecha; `CsvExporter` acepta una lista ya filtrada |
| Etiquetas | Sí | Sí | **No** (`data/local/entity/ExpenseEntity.kt:139`, solo notas) | Bajo | M | Tabla `expense_tag` (migración v22) con kind propio en el sync y su regla en `firestore.rules` (una colección nueva nace cerrada) |
| Adjuntar foto del recibo | No | Sí | **No** | Bajo | M | Columna con URI local (migración); el archivo no viaja a Firestore |
| Reglas de categorización definidas por el usuario | No | Sí | **Parcial**: la categoría se infiere por historial del comercio (`data/capture/BankCaptureManager.kt:452`) | Bajo | M | Tabla de reglas (migración y sync) aplicada antes del historial |
| Conexión bancaria | No | Sí | **No**; lo cubren notificaciones bancarias y estados de cuenta | Bajo | L | No recomendada: agregador de pago, pocos bancos mexicanos a la vista y datos de terceros |
| Periodo configurable | Sí (Pro) | Día de inicio del mes | **No**: quincena fija (`data/quincena/QuincenaRollover.kt:161`) | Bajo | L | No recomendada: el hogar vive por quincena |

### Toques para registrar un gasto

| Camino | Lukas | Wallet | Finance-App |
|---|---|---|---|
| Alta manual completa | 5 más teclear el importe | 5 más los dígitos | Hoja de captura con importe, categoría reciente, cuenta y pagador; la cuenta hay que elegirla cada vez |
| Gasto repetido | 1 desde el acceso directo o el mosaico | 3 con plantilla, 2 con el widget | 1 en "Las de siempre" o en el panel de Quick Tap |
| Varios movimientos de golpe | 4 para N (texto con IA) | No | Uno por frase |

## Orden propuesto

1. **Corregir los dos defectos** (S cada uno): "Por día" calculado desde `QuincenaFigures`, y MSI con categoría obligatoria en el alta y en la importación más una reparación única (por bandera de DataStore, como el ancla del saldo) para los planes que ya nacieron sin ella.
2. **Presupuesto de la quincena editable** (S), porque los avisos y el pronóstico se apoyan en esa cifra.
3. **Avisos de presupuesto por categoría con pronóstico** (M).
4. **Deshacer y última cuenta recordada** (S): poca obra, mucha fricción menos al día.
5. **Desglose con la lista de reservas y alternador día, semana y quincena** (S).
6. **Widget con cifras** (M), ya con el modo privacidad en mente.
7. **Bloqueo con huella y modo privacidad** (M), juntos, porque los dos tocan el arranque y los montos.
8. **Recurrencias con fin, cada N y ocurrencias omitidas** (M, una sola migración v21 → v22 para todo lo que toque esquema en esta tanda).
9. **Recordatorio diario y crédito disponible de la tarjeta** (S).
10. **Captura por foto de ticket y proyección de saldo** (M).
11. Lo de valor bajo (etiquetas, adjuntos, reglas, búsqueda avanzada, estadísticas extra) cuando sobre tiempo. Conexión bancaria y periodo configurable, no.

Decisiones que tiene que tomar Agustín antes de implementar: si el disponible debe reservar lo que falta de los topes por categoría (cambia la convención de cifras de tres pantallas) y si el sobrante de una quincena se arrastra a la siguiente.
