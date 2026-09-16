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
| La captura es rápida | **No en el Fold de Norma.** Con su tipografía, el teclado numérico ocupa media hoja y la fila de categorías recientes, que es el camino corto, queda debajo del pliegue. |

La fricción de fondo no es de accesibilidad sino de **densidad a tipografía grande**: la app se diseñó y se probó con una escala normal, y a font 1.3 + negrita los caminos cortos dejan de estar a la vista.

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
- **Encabezados sin `heading()`.** Las cabeceras de sección (`AJUSTES`, `EXPORTAR Y RESPALDAR`, `RECORDATORIOS`) no están marcadas, así que la navegación por encabezados de TalkBack no salta entre ellas. Estado: abierto, entra con el rediseño de Perfil del brief 2.

Pendiente de hardware: la pasada con TalkBack y Accessibility Scanner en el Pixel 7, que es lo único que mide contraste real y orden de foco.

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

**Hallazgo 4.1 (abierto, severidad alta).** El camino corto no está a la vista. A tipografía normal los chips de categorías recientes caben; a font 1.3 + negrita no, y la persona para la que se hizo la app usa font 1.3 + negrita.

**Hallazgo 4.2 (abierto, severidad media).** El teclado numérico se lleva media hoja aunque el importe típico del hogar tenga tres o cuatro dígitos. Es el elemento que empuja todo lo demás hacia abajo.

**Hallazgo 4.3 (aceptado).** Elegir una fecha distinta de hoy exige abrir "Más" y el selector de fecha: cinco interacciones extra. Es correcto que el caso raro cueste más que el común.

## 5. Recorrido: entender "cuánto me queda"

El encabezado del panel muestra la etiqueta de la quincena y su rango, y el KPI `Disponible` con el anillo de progreso. En el Fold las dos cosas caben sin cortes a font 1.3 + negrita.

**Hallazgo 5.1 (abierto, severidad baja).** El anillo dice "0 % gastado" y el KPI dice `$54,206` sin explicar que el disponible ya descuenta lo reservado. La tarjeta "Reservado" está más abajo, fuera de la primera pantalla.

## 6. Recorrido: cerrar la quincena (nuevo en la Fase 5)

Verificado de punta a punta. El aviso del panel cabe sin cortes, la pantalla de cierre lista lo planeado con tres opciones por fila y "Mover todos" resuelve 48 filas de una vez.

**Hallazgo 6.1 (abierto, severidad media).** Con 48 pagos planeados, el botón de cerrar queda a 25 gestos de desplazamiento del inicio de la pantalla. La decisión masiva existe, pero confirmar exige llegar hasta abajo.

## 7. Recorrido: exportar y respaldar (nuevo en la Fase 5)

**Hallazgo 7.1 (abierto, severidad media).** La sección vive al final de Perfil: hay que pasar por siete tarjetas para llegar. Perfil se ha convertido en una lista larga de doce secciones sin jerarquía entre ellas.

## 7 bis. Quick Tap (implementado en la 6b)

Medido en FinanceFold, abriendo el panel con el enlace `mx.budget://capture`:

| Situación | Tiempo hasta el panel |
|---|---|
| Proceso vivo | 4 a 19 ms |
| Proceso muerto | 2.2 a 2.4 s |

**Hallazgo 7bis.1 (abierto, severidad media).** Con el proceso vivo, el gesto cumple de sobra la meta de la especificación (P95 por debajo de 600 ms). Con el proceso muerto no se acerca: de esos 2.3 s, el punto de entrada solo usa 8 ms; el resto es el arranque de la aplicación, que resuelve el hogar, la identidad de sesión, el color dinámico y la bandera del tutorial con lecturas bloqueantes antes de dejar dibujar nada. Aligerar ese arranque es lo único que puede cerrar la brecha, y toca a toda la app, no a Quick Tap.

**Resuelto durante la implementación.** El panel esperaba a que terminara la consulta del historial antes de existir, lo que metía segundos entre el gesto y el primer frame; ahora aparece vacío y se rellena. Y no se podía guardar cuando la cuenta no tenía dueño y la sesión era anónima, porque el gasto se quedaba sin pagador: el panel elige el primer adulto que paga, y si aún falta algo lo dice en vez de no responder.

## 8. Recorridos pendientes de auditar

Estos tres necesitan datos o dispositivos que esta sesión no tiene:

- **Confirmar un pago planeado desde la notificación.** Exige que el trabajo periódico dispare, o forzarlo con WorkManager.
- **Importar el estado del mes.** Necesita la clave de NVIDIA y un PDF real de estado de cuenta.
- **Proponer desde la web.** Necesita una sesión de colaborador con rol en la nube; el emulador está en sesión anónima sin rol y todo su push falla con permiso denegado.

## 9. Lo que se lleva la sub-fase 6b

Ya hechos en la 6b, sin necesidad de exploración visual porque eran correcciones y no rediseños:

- Objetivos táctiles de Analíticas, Cuentas e Inicio (sección 2).
- `Role.Button` en los clicables hechos a mano y región viva al guardar (sección 3).
- `strings.xml` con lo que lee un lector de pantalla y los errores compartidos.
- Cero rayas largas en el código de la app.

Pendientes de la opción de diseño elegida:

1. Hoja de captura: que el camino corto quepa a font 1.3 con negrita (hallazgos 4.1 y 4.2).
2. Perfil: jerarquía entre doce secciones, y de paso los encabezados marcados (hallazgo 7.1).
3. Bloque héroe: que la relación entre ingreso, gastado, reservado y disponible se lea junta (hallazgo 5.1).
4. Cierre de quincena: confirmar sin recorrer toda la lista (hallazgo 6.1).
5. Quick Tap, que no arregla una fricción sino que añade la entrada más rápida.
