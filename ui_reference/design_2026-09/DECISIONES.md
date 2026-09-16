# Decisiones de diseño (Fase 6a)

Contrato de la sub-fase 6b: aquí se anota, por cada brief de `BRIEFS.md`, la opción
elegida, el enlace del lienzo y el porqué. Lo que no esté escrito aquí no se
implementa.

Las fuentes de cada lienzo viven en `artboards/`, así que un lienzo se puede
rehacer o corregir sin depender de la nube.

| Brief | Opción elegida | Enlace del lienzo | Por qué |
|---|---|---|---|
| 1. Hoja de captura | **A + C, implementadas juntas** (2026-09-16) | https://claude.ai/artifact/SgXHfSgnMJj7L4nqWJTPrp | A quita del camino el teclado propio en cuanto deja de usarse (al tocar cualquier otra cosa o al desplazar) y C pone arriba los gastos que el hogar repite, enteros. Juntas cubren los dos casos: el que se repite se guarda de un toque y el que no, ya no pelea con el teclado. Se descartó B porque devolvía el teclado del sistema, que fue justo lo que llevó a construir el propio. |
| 2. Perfil con jerarquía | **Acordeón de cuatro grupos** (2026-09-16) | sin lienzo: decidido sobre la evidencia de la auditoría | Con todo cerrado las doce secciones caben en una pantalla y cualquier ajuste queda a dos toques, sin rutas nuevas ni pasos de tutorial que reescribir. La ayuda y el estado de la sesión se quedan fuera de los grupos porque el aviso de sesión anónima no puede depender de que alguien abra algo. |
| 3. Bloque héroe del panel | **Anillo por segmentos y la resta escrita** (2026-09-16) | sin lienzo: decidido sobre la evidencia de la auditoría | El anillo gana un segundo tramo con lo reservado y debajo va la resta completa, con el mismo orden y sus operadores. Se conserva el anillo, que es el sello del panel, y la cifra grande deja de aparecer sin explicación. |
| 4. Cierre de quincena | **Barra inferior fija con el resumen** (2026-09-16) | sin lienzo: decidido sobre la evidencia de la auditoría | La decisión masiva sube a una cabecera fija con el conteo de lo que falta por decidir, el resumen de lo que se congela y el botón de cerrar viven en una barra inferior siempre visible, y la lista es lo único que se recorre. El resumen sigue delante antes de confirmar, que es lo que da confianza en una acción difícil de deshacer. |
| 5. Panel de Quick Tap | **implementado y verificado** | https://claude.ai/artifact/SJUwQ4FwwgtkxRhshNfnjf | Tres artboards, no opciones: el panel flotante, la pantalla que precede al permiso y la degradación a hoja completa cuando no se concede. No hay alternativas que elegir porque la especificación §3.3 ya fija la forma. |

Los briefs 2, 3 y 4 no llegaron a tener lienzo: `/design` exige `/design-login`, que
solo se concede desde una sesión interactiva, y la fricción de los tres estaba medida
con volcados y capturas. Agustín eligió entre opciones escritas el 2026-09-16 y esa
elección es la que se implementó.

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
