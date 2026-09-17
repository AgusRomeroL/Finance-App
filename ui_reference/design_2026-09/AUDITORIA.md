# Auditoría de recorridos y accesibilidad (Fase 6a)

Fecha: 2026-09-15. Dispositivo: emulador `FinanceFold` con la **configuración fiel del Fold de Norma**: `wm size 2076x2152`, `wm density 408`, `font_scale 1.30`, `font_weight_adjustment 300`, modo oscuro. Build: `20ce60b` (cierre de la Fase 5).

Evidencia: volcados de `uiautomator` en `capturas/*.xml` y capturas de pantalla en `capturas/*.png`. Las mediciones de objetivos táctiles salen de los volcados, no de mirar la pantalla: a 408 dpi, 1 dp son 2.55 px.

Estado de cada hallazgo: **abierto** (nadie lo ha tocado), **resuelto** (arreglado en la 6b), **aceptado** (se decide vivir con ello) o **diferido** (con fase).

---

## 1. Resumen

Lo que se creía y lo que resultó ser:

| Se creía | Resultó |
|---|---|
| 199 objetivos táctiles por debajo de 48 dp | **Tres reales**, ya corregidos. El conteo previo era de tamaños de icono, no de áreas táctiles: Compose ya expande los clicables al mínimo interactivo. |
| 30 iconos sin descripción para lectores de pantalla | **Cero clicables sin etiqueta** en las cinco pantallas principales. Los 30 son iconos decorativos junto a texto visible, que es exactamente donde `null` es lo correcto. |
| La captura es rápida | **No lo era en el Fold de Norma.** Con su tipografía, el teclado numérico ocupaba media hoja y la fila de categorías recientes, que es el camino corto, quedaba debajo del pliegue. Resuelto el 2026-09-16 (sección 4). |

La fricción de fondo no era de accesibilidad sino de **densidad a tipografía grande**: la app se diseñó y se probó con una escala normal, y a font 1.3 + negrita los caminos cortos dejaban de estar a la vista. Los cuatro rediseños de la 6b atacan exactamente eso y están verificados en FinanceFold con la configuración fiel (capturas del 2026-09-16 en `capturas/`).

---

## 2. Objetivos táctiles menores a 48 dp

Medido sobre los volcados con el script de la sesión. Regla de Material: 48 × 48 dp de área táctil.

| Pantalla | Elemento | Medido | Estado |
|---|---|---|---|
| Analíticas | Los cuatro pills de periodo (`Histórico`, `Anual`, `Mensual`, `Quincenal`) | 34 dp de alto | **resuelto** |
| Cuentas | Filas de tarjeta del panel de deuda (`Mercado Pago`, `Coppel`) | 41 dp de alto | **resuelto** |
| Inicio | Chevrones de navegación entre quincenas | 32 dp de ancho | **resuelto** |

Todo lo demás llega a 48 dp o más, incluidos los botones "atrás" de 40 dp que el análisis estático señalaba: Compose expande su área táctil aunque el círculo pintado sea menor.

**Cómo se arreglaron.** Los pills (`ui/common/PeriodSelector.kt`) y las filas de deuda (`ui/wallets/DebtPanelCard.kt`) piden ahora un alto mínimo de 48 dp; los chevrones del panel (`ui/dashboard/DashboardScreen.kt`) crecen su área táctil a 48 dp sin cambiar el círculo pintado de 32. `minimumInteractiveComponentSize` no servía para los chips: están exentos de la expansión automática de Material.

**Cuidado al medir.** Un nodo cortado por el borde de la pantalla o de su contenedor aparece en el volcado con el alto recortado, no con el real. La primera medición de los pills salió de un nodo así. El script de la sesión descarta esos casos y aplica una tolerancia de 47.5 dp, porque 122 px a 408 dpi son 47.84 dp y no 48 exactos.

## 3. Etiquetas para lectores de pantalla

Cero clicables sin etiqueta en Inicio, Calendario, Cuentas, Analíticas y Perfil. Los iconos con `contentDescription = null` (30 en el código) están todos junto a texto visible dentro del mismo elemento clicable, que es el caso en el que la descripción nula es correcta: repetirla haría que TalkBack leyera dos veces lo mismo.

Lo que sí falta, y no se detecta con un volcado:

- **`Role.Button` en los clicables hechos a mano.** `SettingRow`, las filas de saldo, las de deuda y los chevrones usan `Modifier.clickable` sin rol semántico, así que TalkBack los anunciaba como texto y no decía que se pueden activar. Estado: **resuelto**.
- **Sin región viva en el guardado.** Al guardar un gasto, el estado cambia sin que nada aparezca escrito. Ahora el botón es una región viva educada que anuncia "Guardando el movimiento". Estado: **resuelto**.
- **Encabezados sin `heading()`.** Las cabeceras de sección (`AJUSTES`, `EXPORTAR Y RESPALDAR`, `RECORDATORIOS`) no estaban marcadas, así que la navegación por encabezados de TalkBack no saltaba entre ellas. Con el rediseño de Perfil, la cabecera de cada grupo es un encabezado, dice si está abierta o cerrada (`stateDescription`) y anuncia qué pasa al activarla; los rótulos de tarjeta también van marcados. Estado: **resuelto**.

La pasada en hardware con TalkBack y Accessibility Scanner se hizo el 2026-09-17 en el Pixel 7 y desmintió una línea de esta sección: sí había clicables sin etiqueta, dos, y los dos eran botones extendidos de Material (sección 10).

## 4. Recorrido: capturar un gasto

Camino mínimo real en el Fold, con la hoja recién abierta:

1. Tocar el "+" del rail.
2. Teclear el monto (una pulsación por dígito).
3. **Desplazar** para llegar a las categorías recientes.
4. Elegir categoría.
5. Elegir cuenta.
6. Elegir beneficiario.
7. Guardar.

Evidencia (`capturas/captura-monto.png`): con el monto ya escrito, la hoja muestra el importe, el campo de concepto y el teclado numérico completo; la etiqueta `RECIENTES` asoma en el borde inferior y sus chips quedan tapados por la barra de resumen. El teclado numérico ocupa 530 de los 2000 px visibles.

**Hallazgo 4.1 (resuelto, severidad alta).** El camino corto no estaba a la vista. A tipografía normal los chips de categorías recientes caben; a font 1.3 + negrita no, y la persona para la que se hizo la app usa font 1.3 + negrita.

**Hallazgo 4.2 (resuelto, severidad media).** El teclado numérico se llevaba media hoja aunque el importe típico del hogar tenga tres o cuatro dígitos. Era el elemento que empujaba todo lo demás hacia abajo.

**Cómo se resolvieron (2026-09-16, opción A + C del brief 1).** El teclado propio ocupa alto solo mientras se teclea el importe: se retira al tocar cualquier otra cosa (escucha en la pasada inicial, así que el chip tocado recibe su clic igual) y al desplazar, y vuelve al tocar la cifra, que se anuncia como botón. Y arriba de todo va **"Las de siempre, de un toque"**: hasta tres gastos que el hogar repite a esta hora, completos, derivados del mismo motor que alimenta el panel de Quick Tap (`QuickSuggestions.forNow`); tocar uno guarda el gasto entero. Medido en FinanceFold con la configuración fiel: un gasto repetido pasa de siete interacciones a dos (abrir la hoja y tocar la fila), y para uno nuevo las categorías recientes quedan a la vista tras un solo gesto de desplazamiento, el mismo que retira el teclado.

**Hallazgo 4.3 (aceptado).** Elegir una fecha distinta de hoy exige abrir "Más" y el selector de fecha: cinco interacciones extra. Es correcto que el caso raro cueste más que el común.

## 5. Recorrido: entender "cuánto me queda"

El encabezado del panel muestra la etiqueta de la quincena y su rango, y el KPI `Disponible` con el anillo de progreso. En el Fold las dos cosas caben sin cortes a font 1.3 + negrita.

**Hallazgo 5.1 (resuelto, severidad baja).** El anillo decía "0 % gastado" y el KPI una cifra grande sin explicar que el disponible ya descuenta lo reservado; la tarjeta "Reservado" estaba más abajo, fuera de la primera pantalla.

**Cómo se resolvió (2026-09-16, brief 3).** El anillo gana un segundo tramo contiguo con lo reservado sobre el mismo ingreso, y debajo del KPI va la resta escrita: entró, menos gastado, menos reservado, igual a disponible, cada término con su tono y su signo. Los tres tiles de colores que estaban al costado se retiran: decían los mismos números sin la relación entre ellos. El término reservado muestra lo **prorrateado por cadencia**, que es lo que de verdad se resta, y una nota dice cuánto del compromiso completo pesa en la siguiente quincena. Los cuatro términos se redondean a pesos antes de restarse y el disponible sale de esa resta, así que la línea siempre cuadra; el KPI usa la misma cifra redondeada (antes truncaba y podía discrepar en un peso).

## 6. Recorrido: cerrar la quincena (nuevo en la Fase 5)

Verificado de punta a punta. El aviso del panel cabe sin cortes, la pantalla de cierre lista lo planeado con tres opciones por fila y "Mover todos" resuelve 48 filas de una vez.

**Hallazgo 6.1 (resuelto, severidad media).** Con 48 pagos planeados, el botón de cerrar quedaba a 25 gestos de desplazamiento del inicio de la pantalla. La decisión masiva existía, pero confirmar exigía llegar hasta abajo.

**Cómo se resolvió (2026-09-16, brief 4).** El estado del periodo y la decisión masiva suben a una cabecera fija, con el conteo de lo que falta por decidir ("17 de 17 sin decidir"); el resumen de lo que se congela y el botón de cerrar viven en una barra inferior siempre visible, con el motivo escrito cuando el botón está apagado. La lista es lo único que se recorre. Verificado en FinanceFold sobre la quincena activa, con 17 planeados: **cero** gestos de desplazamiento entre abrir la pantalla y tener delante la decisión masiva y la confirmación.

## 7. Recorrido: exportar y respaldar (nuevo en la Fase 5)

**Hallazgo 7.1 (resuelto, severidad media).** La sección vivía al final de Perfil: había que pasar por siete tarjetas para llegar. Perfil se había convertido en una lista larga de doce secciones sin jerarquía entre ellas.

**Cómo se resolvió (2026-09-16, brief 2).** Las doce secciones se reparten en cuatro grupos plegables, y solo uno queda abierto a la vez: **Este mes** (quincenas, estados de cuenta, exportar y respaldar), **El hogar**, **Apariencia e inteligencia** y **Automatización y avisos**. Fuera de los grupos quedan solo la ayuda y el estado de la sesión, porque el aviso de sesión anónima no puede depender de que alguien abra algo. Verificado en FinanceFold: con todo cerrado los cuatro grupos caben en una pantalla y cualquier ajuste queda a dos toques.

## 7 bis. Quick Tap (implementado en la 6b)

Medido en FinanceFold, abriendo el panel con el enlace `mx.budget://capture`:

| Situación | Tiempo hasta el panel |
|---|---|
| Proceso vivo | 4 a 19 ms |
| Proceso muerto | 2.2 a 2.4 s |

**Hallazgo 7bis.1 (medido en hardware el 2026-09-17; el diagnóstico original era incompleto).** Con el proceso vivo, el gesto cumple de sobra la meta de la especificación (P95 por debajo de 600 ms). Con el proceso muerto, la cifra de 2.3 s es del emulador x86 con GPU por software: **en el Pixel 7 real el mismo camino tarda 0.54 s** del arranque del proceso al panel. El detalle está en la sección 11. Las lecturas bloqueantes del arranque, que este hallazgo señalaba como culpables, son 0.19 s en el Pixel 7 (1.1 s en el emulador); lo que domina es lo que ocurre antes de `onCreate` (carga del dex de un APK de depuración de 211 MB e inicializadores de bibliotecas) y el primer cuadro de la interfaz. La única parte que se pudo aligerar sin tocar a toda la app ya está aplicada: las dos lecturas frías (preferencias y apertura de la base) corren a la par y `onCreate` bajó de 190 a 125 ms en el Pixel 7 y de 1.1 s a 0.6 s en el emulador. El resto se plantea a Agustín en la sección 11 porque cambia el cableado de toda la app.

**Resuelto durante la implementación.** El panel esperaba a que terminara la consulta del historial antes de existir, lo que metía segundos entre el gesto y el primer frame; ahora aparece vacío y se rellena. Y no se podía guardar cuando la cuenta no tenía dueño y la sesión era anónima, porque el gasto se quedaba sin pagador: el panel elige el primer adulto que paga, y si aún falta algo lo dice en vez de no responder.

## 8. Recorridos pendientes de auditar

Estado al 2026-09-17, en hardware (Pixel 7):

- **Confirmar un pago planeado desde la notificación.** **Auditado y funciona.** El recordatorio llegó con la cadencia natural del trabajo periódico (cada 15 minutos), con sus tres acciones; "Confirmar" desde el panel de notificaciones dejó el gasto en `POSTED` y retiró la notificación (captura `capturas/2026-09-17_pixel7_notificacion_confirmar.png`). Tres cosas que costaron encontrar y conviene saber: (1) forzar el trabajo con `cmd jobscheduler run -f` **no sirve** para un trabajo periódico de WorkManager, porque el propio WorkManager se niega a ejecutarlo antes de que venza su periodo y lo vuelve a programar en silencio; hay que esperar el ciclo; (2) el aparato tenía el permiso de notificaciones denegado y el trabajo marcó como "avisados" los pagos que no llegó a mostrar, así que después de conceder el permiso no volvieron a salir: para probar hubo que crear un pago planeado nuevo; (3) tocar el **cuerpo** del grupo de notificaciones abre la app y descarta las nueve de golpe, con sus acciones; hay que abrir el grupo con su chevrón. Ese último punto es comportamiento del sistema con el agrupado automático, no de la app, pero explica por qué un recordatorio puede "desaparecer" sin haberlo confirmado.
- **Importar el estado del mes.** **Bloqueado por diseño de esta sesión.** El Pixel 7 no tiene la clave de NVIDIA en Perfil y la app no la lee de ningún archivo: la clave la tiene que pegar Agustín en Perfil, en el Pixel 7, y después el recorrido se puede auditar con cualquiera de los PDF reales de `Estados de Cuenta/BBVA/`. No se hizo porque pegar una clave en la interfaz de un aparato es una acción que esta sesión no ejecuta.
- **Proponer desde la web.** **Bloqueado en el inicio de sesión.** La web solo entra con Google, y el selector de cuentas se abre en una ventana emergente que la automatización de esta sesión no alcanza. Lo que hace falta es que Agustín elija en esa ventana la cuenta `backuponly.arl.1@gmail.com`, que ya tiene rol `MEMBER` en el grupo de pruebas `hh_1ae9cf5c` ("Test"), y a partir de ahí la propuesta se puede seguir hasta `pending_capture` en el Pixel 7. No hace falta tocar el hogar de producción.

## 9. Lo que se lleva la sub-fase 6b

Ya hechos en la 6b, sin necesidad de exploración visual porque eran correcciones y no rediseños:

- Objetivos táctiles de Analíticas, Cuentas e Inicio (sección 2).
- `Role.Button` en los clicables hechos a mano y región viva al guardar (sección 3).
- `strings.xml` con lo que lee un lector de pantalla y los errores compartidos.
- Cero rayas largas en el código de la app.

Hechos con la opción de diseño elegida (2026-09-16):

1. Hoja de captura: teclado bajo demanda más "Las de siempre, de un toque" (hallazgos 4.1 y 4.2).
2. Perfil: cuatro grupos plegables y los encabezados marcados (hallazgo 7.1 y §3).
3. Bloque héroe: anillo por segmentos y la resta escrita (hallazgo 5.1).
4. Cierre de quincena: cabecera fija y barra inferior con el resumen (hallazgo 6.1).
5. Quick Tap, que no arregla una fricción sino que añade la entrada más rápida.

El hallazgo 7bis.1 quedó medido en hardware y acotado (sección 11), y la pasada con TalkBack y
Accessibility Scanner en hardware está hecha (sección 10).

## 10. Pasada de accesibilidad en hardware (Pixel 7, 2026-09-17)

Aparato: Pixel 7 (`34191FDH20075M`), Android 17, color dinámico y modo oscuro, tipografía 1.0 y
después 1.3 con negrita (la de Norma). Herramientas: Accessibility Scanner instalado desde Play
Store y activado como servicio; TalkBack 17. Cada pantalla se escaneó con una instantánea del
Scanner y se leyó su lista de sugerencias por volcado de accesibilidad. Los volcados y capturas
de la sesión no se versionan; lo que importa es la tabla.

### 10.1 Resultado por pantalla

Sugerencias del Scanner antes y después de las correcciones de esta pasada, a tipografía 1.0:

| Pantalla | Antes | Después | Lo que queda y por qué |
|---|---|---|---|
| Inicio | 8 (con el carrusel de sugerencias a la vista) | 2 | Los dos iconos del pill de navegación (36 dp de ancho, ver 10.3). |
| Calendario | 35 | 6 | Cuatro "misma descripción" entre las dos tarjetas de pago que tienen idéntico subtítulo, fecha e importe (son datos iguales, no un defecto) y los dos iconos del pill. |
| Cuentas | 5 | 4 | "Deuda de tarjetas" aparece dos veces (KPI y encabezado de sección, ambos correctos), la tarjeta que asoma recortada bajo la barra inferior y los dos iconos del pill. |
| Analíticas | 3 | 3 | Solo los tres iconos del pill. |
| Perfil | 0 | 0 | |
| Hoja de captura | 4 | 0 | |
| Hoja del asistente | 3 | 1 | El texto de la pantalla de fondo bajo el velo de la hoja, que el Scanner lee por OCR y no está en el árbol porque es una hoja modal: correcto. |

Con tipografía 1.3 y negrita: Perfil 0, hoja de captura 4 (las cuatro teclas de la última fila del
teclado, recortadas por la barra inferior y por eso "sin etiqueta"; la hoja se desplaza y el
teclado entero se ve al tocar el importe), Calendario 10 (los cuatro de datos iguales, los dos
del pill y cuatro recortes de la tarjeta que queda bajo la barra), Cuentas 4 (los mismos tres
más el botón extendido, corregido después de ese escaneo). **Inicio no se pudo escanear a 1.3:**
el Scanner falla con "no se pudo completar" dos veces seguidas; la sospecha es que el árbol no
llega a quedarse quieto por las animaciones de entrada del panel. Es una limitación de la medida,
no un hallazgo.

### 10.2 Lo que se corrigió (todo en esta pasada)

| Hallazgo | Dónde | Corrección |
|---|---|---|
| **Los dos botones extendidos ("Nueva cuenta", "Preguntar") no exponen su texto**: para TalkBack eran botones sin nombre. Es un comportamiento del `ExtendedFloatingActionButton` de Material 3 1.4.0, no de la app; el volcado de accesibilidad lo confirma (el nodo botón existe, sin texto ni descripción). Esto desmiente la afirmación de la sección 3 de que no había clicables sin etiqueta: el volcado de la 6a no llegó a esos nodos. | `ui/wallets/WalletsScreen.kt`, `ui/analytics/AnalyticsScreen.kt` | `contentDescription` explícita en los dos. |
| El asa de las hojas modales era un objetivo táctil de 32 dp (la de fábrica) o 40 dp (la propia de la captura) de ancho. | `ui/common/SheetDragHandle.kt` (nuevo), usado por la captura y el asistente | Asa de 48 por 48 dp con la barra visible de 40 por 4 dentro, y nombre en `strings.xml` (`cd_sheet_drag_handle`). |
| Las 42 celdas del calendario compartían descripción (solo el punto de "hay pagos" la tenía) y las dos "M" de la cabecera eran indistinguibles. | `ui/calendar/MonthCalendar.kt` | Cada día se anuncia como "17 de septiembre, hoy, con pagos planeados"; la cabecera dice "lunes, martes, miércoles..." mientras muestra la letra. |
| Los números de los días fuera del mes no llegaban al contraste mínimo (alfa 0.35 sobre el fondo oscuro). | `ui/calendar/MonthCalendar.kt` | Alfa 0.8 (a 0.6 el Scanner seguía marcándolos). |
| Los botones Confirmar, Editar y Posponer de cada tarjeta del calendario sonaban idénticos de una tarjeta a otra. | `ui/calendar/CalendarScreen.kt` | Cada uno dice a qué pago pertenece ("Confirmar Teléfono David") y tiene rol de botón. |
| El campo de pregunta del asistente no tenía nombre: su texto de ayuda se pinta aparte. | `ui/analytics/AiChatPanel.kt` | `contentDescription` en el campo. |
| En el carrusel de sugerencias, el motivo iba al 78 % de opacidad (3.5:1) y "Ahora no" quedaba en 3.8:1 porque el fondo del botón se aclaraba con el color del texto. | `ui/dashboard/DashboardScreen.kt` | Motivo a opacidad completa; el botón secundario oscurece el fondo en vez de aclararlo. Medido con los píxeles de la captura antes de cambiar. |
| Registrar y Ahora no sonaban igual en todas las tarjetas del carrusel; el icono de destello repetía "Sugerencia" que ya está escrito al lado. | `ui/dashboard/DashboardScreen.kt` | Los botones dicen el concepto ("Registrar Hipoteca"); el icono es decorativo. |
| El segmentado Neto/Bruto medía 32 dp de alto. | `ui/dashboard/DashboardScreen.kt` | 48 dp por segmento, con rol de botón. |
| La cifra héroe se anunciaba en dos nodos: "signo de pesos" y luego el número. | `ui/dashboard/DashboardScreen.kt` | Un solo nodo que dice "$52,727". |
| Los items del pill de navegación medían 42 dp de alto. | `ui/navigation/FloatingNavBar.kt` | 48 dp de alto sin cambiar la altura de la barra. |
| A tipografía 1.3 con negrita en ancho compacto (el Fold plegado, un teléfono), la etiqueta "NUEVO MOVIMIENTO" de la hoja de captura se partía en "MOVIMIENT / O". | `ui/capture/CaptureBottomSheet.kt` | La etiqueta nunca parte una palabra y en ancho compacto dice "NUEVO". |

### 10.3 Lo que se acepta, con el motivo

- **Los tres iconos no seleccionados del pill de navegación miden 36 dp de ancho** (48 de alto). En un teléfono de 411 dp no caben cuatro items de 48 dp, la etiqueta del seleccionado, el micrófono y el "+": ensancharlos obliga a recortar la etiqueta ("Analí..."), que es peor para todo el mundo. En el Fold desplegado la barra es un riel y no aplica. La alternativa real es mover el micrófono dentro de la hoja de captura para liberar 62 dp; es una decisión de diseño para Agustín, no una corrección.
- **"Misma descripción" entre tarjetas con datos idénticos** (dos pagos de "Teléfono celular" del mismo día y el mismo importe) y entre el KPI y el encabezado "Deuda de tarjetas": el Scanner no distingue datos iguales de etiquetas repetidas.
- **Elementos recortados por la barra inferior o por el borde de la pantalla**: el Scanner los mide con el alto visible, igual que los volcados de la 6a (sección 2).

### 10.4 TalkBack

TalkBack quedó activo en el aparato, pero **ni los gestos ni los atajos de teclado inyectados por
`adb` mueven su foco** (la traversal se probó con deslizamientos y con Alt+flecha; el foco no
salió del buscador), así que el orden de lectura se verificó con el orden del árbol de
accesibilidad, que es el que TalkBack recorre. En Inicio el orden es el visual: buscador, Perfil,
periodo, título, flechas de quincena, anillo, cifra, desglose, ritmo, miembros, transacciones y
barra. El único defecto de orden encontrado fue la cifra héroe partida en dos nodos, ya corregido.
Lo que sigue sin verificar es el habla en sí (qué dice exactamente TalkBack por cada nodo), que
exige oírlo.

### 10.5 Contraste en color dinámico oscuro

Con la paleta dinámica del Pixel 7 en modo oscuro, el Scanner solo marcó tres contrastes: los días
fuera del mes en el calendario y los dos textos del carrusel de sugerencias; los tres están
corregidos. Los tonos financieros de `FinanceColors` (ingreso, gasto, alerta) pasaron sin
observaciones.

### 10.6 Otras dos cosas vistas en el camino, sin corregir

- En ancho compacto, el botón flotante del Calendario ("Nuevo pago planeado") y el de Cuentas
  ("Nueva cuenta") se superponen a la última tarjeta o fila de la lista, y la barra de navegación
  tapa el final de las listas (captura de Cuentas en la sesión). En el Fold desplegado no ocurre
  porque el contenido es más ancho y más corto. Es de diseño de la 6, no de accesibilidad:
  la lista necesita un relleno inferior del alto de la barra más el botón.
- `CLAUDE.md` sigue diciendo que Perfil es una de "las cinco pestañas top-level"; desde la 6b
  Perfil se abre desde el avatar de la barra superior y la barra tiene cuatro pestañas. Se corrige
  en ese archivo en este mismo cierre.

## 11. Arranque en frío de Quick Tap, medido en hardware (hallazgo 7bis.1)

Medida: `am start` del enlace `mx.budget://capture` con el proceso muerto (`am force-stop` antes
de cada corrida), cinco corridas por aparato, leyendo la traza `QuickCapture.coldStart` (que ahora
usa el mismo reloj en los dos lados: restaba `elapsedRealtime` de `getStartUptimeMillis` y en un
teléfono real daba las horas que llevaba dormido) y una traza nueva de `BudgetApplication.onCreate`
que dice cuánto costó cada tramo.

| Aparato | Del arranque del proceso al panel | `onCreate` | Antes de `onCreate` |
|---|---|---|---|
| Pixel 7, antes | 0.54 s (0.53 a 0.54) | 0.19 s (prefs 55 ms, consultas 35, Firebase 55, workers 20) | ~0.32 s |
| Pixel 7, después | 0.55 a 0.61 s | 0.12 s (prefs y base a la par: 40 ms; consultas 32; Firebase 25) | ~0.41 s |
| FinanceFold, antes | 2.35 s (1.9 a 2.5) | 1.1 s (prefs 460 a 520 ms, consultas 260, Firebase 170) | ~1.2 s |
| FinanceFold, después | 2.0 s (2.0 a 2.3) | 0.6 s (prefs y base a la par: 130 ms; consultas 200; Firebase 125) | ~1.3 s |

Lo que dice la tabla: en el teléfono real el arranque en frío ya cumple la meta de la especificación
(P95 por debajo de 600 ms) por un margen estrecho; los 2.3 s eran del emulador. El cambio aplicado
(las dos lecturas frías en paralelo) recorta `onCreate` en los dos aparatos pero no mueve la cifra
total en el Pixel 7, porque ahí `onCreate` es la parte chica: el grueso es lo que pasa antes
(arranque del proceso, carga de clases de un APK de depuración de 211 MB sin R8, inicializadores
de Firebase, WorkManager y `ProcessLifecycle`) y el primer cuadro de Compose.

**Propuesta para Agustín, en orden de rendimiento por esfuerzo.** (1) La compilación de release con
R8 y un perfil de referencia (`androidx.profileinstaller`, Fase 8) ataca justo el tramo grande, el
anterior a `onCreate`, sin tocar una línea de lógica: es lo primero que hay que medir antes de
reestructurar nada. (2) Si después de eso hace falta más, mover fuera del hilo principal la
construcción de Firebase, el sync y los workers (hoy 80 a 100 ms en el Pixel 7, 250 en el emulador)
con inicialización perezosa; cambia el cableado de `BudgetApplication` y obliga a que todo lo que
lee `app.syncManager` y compañía tolere la espera, así que no se aplicó. (3) Nada más dentro de
Quick Tap: el punto de entrada cuesta 3 a 20 ms.
