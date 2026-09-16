# Decisiones de diseño (Fase 6a)

Contrato de la sub-fase 6b: aquí se anota, por cada brief de `BRIEFS.md`, la opción
elegida, el enlace del lienzo y el porqué. Lo que no esté escrito aquí no se
implementa.

Las fuentes de cada lienzo viven en `artboards/`, así que un lienzo se puede
rehacer o corregir sin depender de la nube.

| Brief | Opción elegida | Enlace del lienzo | Por qué |
|---|---|---|---|
| 1. Hoja de captura | **por elegir entre A, B y C** | https://claude.ai/artifact/SgXHfSgnMJj7L4nqWJTPrp | Tres ejes distintos: retirar el teclado cuando estorba (A), quitarlo del todo (B), saltarse el formulario cuando el gasto se repite (C). Cada artboard lleva su nota con lo que gana y lo que arriesga. |
| 2. Perfil con jerarquía | pendiente | | |
| 3. Bloque héroe del panel | pendiente | | |
| 4. Cierre de quincena | pendiente | | |
| 5. Panel de Quick Tap | **listo para implementar** | https://claude.ai/artifact/SJUwQ4FwwgtkxRhshNfnjf | Tres artboards, no opciones: el panel flotante, la pantalla que precede al permiso y la degradación a hoja completa cuando no se concede. No hay alternativas que elegir porque la especificación §3.3 ya fija la forma. |

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
| Cabeceras de sección con `heading()` | Entra con el rediseño de Perfil del brief 2, para no marcar dos veces | Auditoría §3 |
