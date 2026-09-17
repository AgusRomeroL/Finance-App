# Decisiones de diseño (Fase 6a)

Contrato de la sub-fase 6b: aquí se anota, por cada brief de `BRIEFS.md`, la opción
elegida, el enlace del lienzo y el porqué. Lo que no esté escrito aquí no se
implementa.

Las fuentes de cada lienzo viven en `artboards/`, así que un lienzo se puede
rehacer o corregir sin depender de la nube.

| Brief | Opción elegida | Enlace del lienzo | Por qué |
|---|---|---|---|
| 1. Hoja de captura | **A + C, implementadas juntas** (2026-09-16) | https://claude.ai/artifact/SgXHfSgnMJj7L4nqWJTPrp | A quita del camino el teclado propio en cuanto deja de usarse (al tocar cualquier otra cosa o al desplazar) y C pone arriba los gastos que el hogar repite, enteros. Juntas cubren los dos casos: el que se repite se guarda de un toque y el que no, ya no pelea con el teclado. Se descartó B porque devolvía el teclado del sistema, que fue justo lo que llevó a construir el propio. |
| 2. Perfil con jerarquía | **C, el acordeón, sin el buscador** (2026-09-16) | https://claude.ai/artifact/2Y9T9MywcV3xngUoBb7xPX | Cuatro grupos plegables, uno abierto a la vez: con todo cerrado las doce secciones caben en una pantalla y cualquier ajuste queda a dos toques, sin rutas nuevas ni pasos de tutorial que reescribir. Se descartó A (hub con segunda pantalla) por las rutas que añadía y B (pestañas) porque reparte mal doce secciones desiguales. El buscador del lienzo C queda fuera por ahora: con los grupos cerrados no hay nada que buscar que no esté a la vista. La ayuda y el estado de la sesión se quedan fuera de los grupos porque el aviso de sesión anónima no puede depender de que alguien abra algo. |
| 3. Bloque héroe del panel | **B y C juntas: el anillo partido y la resta escrita** (2026-09-16) | https://claude.ai/artifact/7rPHEns4k5DAeQuySAiiAY | El anillo gana un segundo tramo con lo reservado (B) y debajo va la resta completa con sus operadores (C), que reemplaza a los tres tiles de colores. Se descartó A porque retiraba el anillo, que es el sello del panel. El "por día" que C añadía ya vive en la tarjeta de ritmo, justo debajo, así que no se duplica. |
| 4. Cierre de quincena | **A, resumen y confirmación ancladas** (2026-09-16) | https://claude.ai/artifact/TAFJNjJdRzjuqer5YVFHfg | La decisión masiva sube a una cabecera fija con el conteo de lo que falta por decidir, el resumen de lo que se congela y el botón de cerrar viven en una barra inferior siempre visible, y la lista es lo único que se recorre. Se descartó B porque añadía un paso a una pantalla que ya es larga, y C porque escondía el resumen tras una hoja que hay que abrir. |
| 5. Panel de Quick Tap | **implementado y verificado** | https://claude.ai/artifact/SJUwQ4FwwgtkxRhshNfnjf | Tres artboards, no opciones: el panel flotante, la pantalla que precede al permiso y la degradación a hoja completa cuando no se concede. No hay alternativas que elegir porque la especificación §3.3 ya fija la forma. |

Los cinco lienzos están en `artboards/`, así que se rehacen o se corrigen sin depender
de la nube. Las elecciones de los briefs 1 a 4 las tomó Agustín el 2026-09-16 y son las
que se implementaron ese mismo día, verificadas en FinanceFold con la configuración fiel
del Fold (capturas en `capturas/2026-09-16-*.png`).

## Lo que se completó después de implementar (2026-09-16)

Al revisar las opciones descartadas quedaron a la vista dos huecos que la opción
elegida no cubría. Ninguno obliga a cambiar de opción: los dos se cierran
añadiendo, no rehaciendo.

| Hueco | De dónde salió | Qué se hizo |
|---|---|---|
| El acordeón de Perfil esconde el estado hasta que lo abres, que era el contra escrito de la opción C | Nota de la opción C del lienzo de Perfil | La cabecera plegada lleva una insignia con lo que reclama atención dentro: quincenas por cerrar y atribuciones por revisar. La lee TalkBack junto al estado abierto o cerrado, sin decirla dos veces |
| Confirmar el cierre no decía qué iba a pasar con los pagos planeados, que es lo que la opción B enseñaba en su segundo paso | Nota de la opción B del lienzo de cierre | La barra inferior y el diálogo dicen la misma frase, con cuántos pagos y cuánto dinero se lleva cada decisión. Si hay descartes o pagos dados por hechos, el diálogo avisa de que reabrir no los deshace |

De paso salieron los plurales escritos con paréntesis de la interfaz, del tipo
"17 pago(s) planeado(s)", en el cierre de quincena y en el importador de estados
de cuenta. Ahora se escriben en singular o en plural según el número.

Verificado en FinanceFold con la configuración fiel del Fold, adelantando el
reloj al 1 de octubre para que la quincena en curso venciera con sus 17 pagos
planeados sin ejecutar. Capturas en `capturas/2026-09-16-perfil-aviso-grupo-cerrado.png`,
`capturas/2026-09-16-cierre-barra-destino.png` y `capturas/2026-09-16-cierre-dialogo-destino.png`.

## Decisiones ya tomadas sin lienzo

Estas salen de la auditoría y no necesitan exploración visual: son correcciones,
no rediseños. Se implementan en la 6b tal cual.

| Qué | Decisión | Origen |
|---|---|---|
| Pills de periodo de Analíticas | Alto mínimo de 48 dp. Hecho | Auditoría §2 |
| Filas del panel de deuda de Cuentas | Alto mínimo de 48 dp. Hecho | Auditoría §2 |
| Chevrones de quincena del panel | Área táctil de 48 dp sin tocar el círculo pintado. Hecho | Auditoría §2 |
| Clicables hechos a mano | `Role.Button` y, donde aporta, `onClickLabel`. Hecho | Auditoría §3 |
| Guardado de un gasto | Región viva educada que anuncia el estado. Hecho | Auditoría §3 |
| Textos que lee TalkBack y mensajes de error | Extraídos a `strings.xml`, que tenía una sola cadena. Hecho | Plan §4, Fase 6 punto 7 |
| Raya larga (U+2014) | Fuera de los 12 textos visibles y de los 126 comentarios en Kotlin. Hecho | Plan, hallazgo 26 |
| Cabeceras de sección con `heading()` | Marcadas con el rediseño de Perfil: la cabecera de cada grupo es un encabezado y dice si está abierta o cerrada, y los rótulos de tarjeta también. Hecho | Auditoría §3 |
