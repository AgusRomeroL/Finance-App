# Briefs para `/design` (Fase 6a)

Cada brief está anclado a una fricción medida en `AUDITORIA.md`, no a una intuición. Se piden entre dos y cuatro opciones; nunca "hazlo bonito".

**Contexto que va en todos.** App Android de presupuesto familiar quincenal de un hogar mexicano. Español de México, pesos, Material 3 con color dinámico. El aparato es un Pixel 9 Pro Fold: pantalla interna de 2076 × 2152 px, **casi cuadrada**, y su dueña usa **escala de fuente 1.3 con negrita** en **modo oscuro**. Los tokens están en `ui_reference/veridian_ledger/DESIGN.md`: Google Sans Flex en dos tamaños ópticos, jerarquía por capas tonales sin líneas de un píxel, radios de 6 a 28 dp, resortes en todo cambio de estado. Todo tiene que ser construible con Compose y Material 3.

---

## Brief 1. Hoja de captura que quepa a tipografía grande

**Fricción (hallazgo 4.1 y 4.2).** El camino más corto para capturar un gasto es elegir una de las categorías recientes, y a font 1.3 con negrita esa fila queda debajo del pliegue: el teclado numérico se lleva 530 de los 2000 px visibles y empuja todo hacia abajo. Evidencia: `capturas/captura-monto.png`.

**Encargo.** Rediseña la hoja de captura para que registrar un gasto en efectivo de hoy, con una categoría reciente, se resuelva **sin desplazar** a escala de fuente 1.3 con negrita, conservando la atribución en dos dimensiones (quién consume y quién paga) y el teclado numérico propio, que existe porque el teclado del sistema tapaba la hoja.

Pistas de por dónde: el teclado numérico podría aparecer solo mientras el foco está en el importe; el importe y las categorías recientes podrían compartir fila; la atribución podría resumirse en una línea que se expande.

**Dos a cuatro opciones.** Pinta cada una en el estado real: modo oscuro, font 1.3, negrita.

---

## Brief 2. Perfil con jerarquía

**Fricción (hallazgo 7.1).** Perfil tiene doce secciones apiladas con el mismo peso visual: ayuda, cuenta, grupos, administrar, apariencia, inteligencia, asistente, automatización, estados de cuenta, exportar y respaldar, recordatorios, calendario y ubicación. Llegar a la última exige siete gestos de desplazamiento.

**Encargo.** Propón una organización que permita llegar a cualquier ajuste en dos toques. Distingue lo que se toca una vez y se olvida (calendario, ubicación, automatización) de lo que se usa cada mes (estados de cuenta, exportar, quincenas).

---

## Brief 3. El panel dice cuánto queda y por qué

**Fricción (hallazgo 5.1).** El KPI `Disponible` ya descuenta lo reservado en pagos planeados, pero eso solo se entiende bajando a la tarjeta "Reservado". La cifra grande sin su explicación invita a gastar de más.

**Encargo.** Rediseña el bloque héroe para que la relación entre ingreso, gastado, reservado y disponible se lea de un vistazo, sin convertirlo en una tabla. El anillo de progreso puede quedarse o irse.

---

## Brief 4. Cerrar la quincena sin recorrerla entera

**Fricción (hallazgo 6.1).** Con 48 pagos planeados sin ejecutar, el botón de cerrar queda a 25 gestos del inicio. Existe "Mover todos", pero confirmar sigue exigiendo llegar al fondo.

**Encargo.** Propón una disposición donde la decisión masiva y la confirmación estén siempre a la vista, y la lista sea lo que se recorre. El resumen de lo que se congela tiene que seguir siendo visible antes de confirmar: es lo que da confianza para una acción difícil de deshacer.

---

## Brief 5. Panel flotante de Quick Tap

**Origen.** Especificación `ESPECIFICACION_UX_HARDWARE_APP.md` §3.3, componentes B y C. Es la entrada de captura más rápida del sistema y todavía no existe.

**Encargo.** Diseña el panel que aparece al dar dos golpes en la espalda del teléfono, **encima de cualquier app**. Requisitos de la especificación: aspecto de notificación expandida, superficie `surfaceContainerHighest`, radio 28 dp, foco inicial en el importe con teclado decimal, categoría, cuenta y beneficiario ya predichos y editables en un toque, tres sugerencias recientes, y cierre automático a los seis segundos sin interacción (el gesto pudo ser accidental).

Diseña también la pantalla explicativa que precede al permiso de mostrarse sobre otras apps: qué se va a pedir, para qué, y con una imagen del panel.

**Restricción dura.** Guardar tiene que ser posible en un solo toque después de teclear el importe. Si el diseño exige elegir categoría, no sirve.

---

## Cómo se ejecutan

1. Desde una sesión interactiva de Claude Code, con `/design-login` ya autorizado.
2. Un `/design` por brief, pegando el contexto común más el encargo.
3. La opción elegida, su enlace y el porqué se anotan en `DECISIONES.md`, que es el contrato de la sub-fase 6b.
