# Decisiones de diseño (Fase 6a)

Contrato de la sub-fase 6b: aquí se anota, por cada brief de `BRIEFS.md`, la opción
elegida, el enlace del lienzo y el porqué. Lo que no esté escrito aquí no se
implementa.

| Brief | Opción elegida | Enlace del lienzo | Por qué |
|---|---|---|---|
| 1. Hoja de captura | pendiente | | |
| 2. Perfil con jerarquía | pendiente | | |
| 3. Bloque héroe del panel | pendiente | | |
| 4. Cierre de quincena | pendiente | | |
| 5. Panel de Quick Tap | pendiente | | |

## Decisiones ya tomadas sin lienzo

Estas salen de la auditoría y no necesitan exploración visual: son correcciones,
no rediseños. Se implementan en la 6b tal cual.

| Qué | Decisión | Origen |
|---|---|---|
| Pills de periodo de Analíticas | Subir el área táctil a 48 dp sin cambiar el aspecto, con relleno interno | Auditoría §2 |
| Filas de saldo de Cuentas | Subir el área táctil a 48 dp | Auditoría §2 |
| Clicables hechos a mano | Añadir `Role.Button` y, donde haga falta, `onClickLabel` | Auditoría §3 |
| Cabeceras de sección | Marcar con `heading()` para que TalkBack navegue entre ellas | Auditoría §3 |
| Guardado de un gasto | Región viva que anuncie el resultado | Auditoría §3 |
| Textos que lee TalkBack y mensajes de error | Extraer a `strings.xml`, que hoy tiene una sola cadena | Plan §4, Fase 6 punto 7 |
| Raya larga (U+2014) | Quitarla de los 12 textos visibles y de los 130 comentarios en Kotlin | Plan, hallazgo 26 |
