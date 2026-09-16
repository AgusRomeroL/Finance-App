# Decisiones de diseño (Fase 6a)

Contrato de la sub-fase 6b: aquí se anota, por cada brief de `BRIEFS.md`, la opción
elegida, el enlace del lienzo y el porqué. Lo que no esté escrito aquí no se
implementa.

Las fuentes de cada lienzo viven en `artboards/`, así que un lienzo se puede
rehacer o corregir sin depender de la nube.

| Brief | Opción elegida | Enlace del lienzo | Por qué |
|---|---|---|---|
| 1. Hoja de captura | **por elegir entre A, B y C** | https://claude.ai/artifact/SgXHfSgnMJj7L4nqWJTPrp | Tres ejes distintos: retirar el teclado cuando estorba (A), quitarlo del todo (B), saltarse el formulario cuando el gasto se repite (C). Cada artboard lleva su nota con lo que gana y lo que arriesga. |
| 2. Perfil con jerarquía | **por elegir entre A, B y C** | https://claude.ai/artifact/2Y9T9MywcV3xngUoBb7xPX | Cuatro artboards: el hub con su segunda pantalla de Ajustes (A), tres pestañas (B) y acordeón con buscador (C). Los tres llegan a cualquier ajuste en dos toques por caminos distintos: jerarquía nueva, agrupación plana o búsqueda. |
| 3. Bloque héroe del panel | **por elegir entre A, B y C** | https://claude.ai/artifact/7rPHEns4k5DAeQuySAiiAY | Cifras reales de Q1 Julio 2026. A cambia el anillo por una barra apilada con leyenda, B parte el anillo en los tres pedazos reales, C escribe la resta con sus signos y añade cuánto se puede gastar por día. |
| 4. Cierre de quincena | **por elegir entre A, B y C** | https://claude.ai/artifact/TAFJNjJdRzjuqer5YVFHfg | Cifras reales de Q2 Junio 2026 con 48 planeados sin ejecutar. A ancla resumen y confirmación y deja que solo la lista se desplace, B parte el flujo en decidir y confirmar (dos artboards), C deja la lista intacta y sube una hoja con todo lo que hace falta para cerrar. |
| 5. Panel de Quick Tap | **implementado y verificado** | https://claude.ai/artifact/SJUwQ4FwwgtkxRhshNfnjf | Tres artboards, no opciones: el panel flotante, la pantalla que precede al permiso y la degradación a hoja completa cuando no se concede. No hay alternativas que elegir porque la especificación §3.3 ya fija la forma. |

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
