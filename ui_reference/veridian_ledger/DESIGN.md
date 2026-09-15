# Sistema de diseño: The Architectural Ledger

Reescrito el 2026-09-15 (Fase 6a) con los tokens **reales** del código. La versión anterior era la especificación original del concepto tal como salió de la herramienta de diseño y llevaba dos años divergiendo de la implementación: hablaba de Roboto Flex cuando la app embarca Google Sans Flex, y de un verde fijo cuando el tema aplica color dinámico.

Este archivo es la referencia que alimenta a las herramientas de diseño. La guía de mantenimiento del código sigue siendo `CLAUDE.md`, sección "Capa de UI".

Fuentes en el código, por si algo aquí vuelve a quedar viejo: `ui/theme/Color.kt`, `FinanceColors.kt`, `Type.kt`, `Shape.kt`, `Motion.kt`, `AmountSemantics.kt`.

---

## 1. Idea rectora

Tratar la pantalla interna del Fold como una hoja de un libro contable de buena factura: densidad alta de información, jerarquía por capas tonales en vez de líneas, y aire suficiente para que las cifras respiren. Cada elemento se siente un objeto físico apoyado sobre otro, no una caja dibujada.

**Restricción que manda sobre todo lo demás:** el aparato real es un Pixel 9 Pro Fold con pantalla interna de 2076 × 2152 px (casi cuadrada) y su dueña usa **escala de fuente 1.3 con negrita**. Un diseño que solo funcione a escala normal no sirve. Nada de anchos o altos fijos que recorten, `maxLines` generosos y filas que se reacomoden.

## 2. Color

El tema aplica **color dinámico de Material You** por defecto: los roles cromáticos salen del fondo de pantalla. El verde sembrado es la semilla de respaldo, no la verdad única. Para un mockup conviene pintar con el respaldo y anotar qué roles cambiarían.

### Roles, valor de respaldo (claro)

| Rol | Valor |
|---|---|
| `primary` | `#006C44` |
| `onPrimary` | `#FFFFFF` |
| `primaryContainer` | `#92F7B4` |
| `onPrimaryContainer` | `#002111` |
| `primaryDim` | `#005233` |
| `inversePrimary` | `#77DA9A` |

### Semánticos financieros (fuera del `ColorScheme`)

Viven en `MaterialTheme.financeColors` y se armonizan al primary con tope bajo solo en modo dinámico, para que un fondo de pantalla naranja no convierta un ingreso en algo que parezca una alerta.

| Tono | Color | Contenedor |
|---|---|---|
| Ingreso | `#0F5A2E` | `#B7F4C5` |
| Gasto | `#BA1A1A` | `#FFDAD6` |
| Aviso | `#8B5A00` | `#FFDDB3` |

### Regla no cromática (obligatoria)

El significado financiero **nunca** depende solo del color. Cada tono lleva signo, icono y etiqueta (`ui/theme/AmountSemantics.kt`):

| Tono | Signo | Icono | Etiqueta |
|---|---|---|---|
| INCOME | `+` | flecha arriba | Ingreso |
| EXPENSE | `−` | flecha abajo | Gasto |
| WARNING | ninguno | triángulo | Aviso |
| SCHEDULED | `−` | reloj | Programado |
| TRANSFER | ninguno | flecha doble | Transferencia |
| NEUTRAL | ninguno | ninguno | ninguna |

### Superficies

Jerarquía por capas tonales, nunca por líneas de un píxel. Una tarjeta de datos se apoya sobre un contenedor más oscuro (o más claro en modo claro) y esa diferencia es toda la separación que necesita.

`surface` → `surfaceContainerLow` (zonas estructurales) → `surfaceContainer` (tarjetas de ajustes) → `surfaceContainerLowest` (tarjetas de datos) → `surfaceContainerHighest` (elementos flotantes).

## 3. Tipografía

**Google Sans Flex** (licencia SIL OFL), embarcada como cortes estáticos en `res/font/`, en dos tamaños ópticos:

- `GoogleSansFlexText` para títulos, cuerpo y etiquetas.
- `GoogleSansFlexDisplay` para display y headline, es decir los montos héroe.

Cortes disponibles: Light, Regular, Medium, SemiBold, Bold. No se usa la tipografía del sistema en ningún punto.

Escala real (`Type.kt`), con lo que importa de cada estilo:

| Estilo | Familia | Tamaño | Peso | Uso |
|---|---|---|---|---|
| `displayLarge` | Display | 57 sp | Light | Cifra héroe |
| `headlineMedium` | Display | 28 sp | Light | Título de pantalla |
| `titleMedium` | Text | 16 sp | Medium | Título de tarjeta |
| `bodyLarge` | Text | 16 sp | Regular | Cuerpo |
| `labelSmall` | Text | 10 sp | Bold, `letterSpacing` 0.8 | Cabecera de sección en mayúsculas |

El contraste entre un monto muy ligero y una etiqueta muy pesada es deliberado: es lo que da el aire de publicación editorial.

**Resiliencia:** los importes usan `AutoSizeAmountText`, que encoge la cifra hasta que quepa en una línea en vez de recortarla. Un monto cortado es un dato falso.

## 4. Forma

Escala centralizada en `BudgetShapes` y `AppShapes`. Nada de radios sueltos en el código.

| Rol | Radio |
|---|---|
| `extraSmall` | 6 dp |
| `small` | 10 dp |
| `medium` | 14 dp |
| `large` | 20 dp |
| `extraLarge` | 28 dp |

Formas por intención: tarjeta 20 dp, tecla del teclado numérico 18 dp, campo de entrada 22 dp, contenedor héroe y diálogos 28 dp, píldora al 50 %.

## 5. Movimiento

Todo cambio de estado se anima con resortes, nunca con saltos ni interpolaciones lineales. Los tokens viven en `ui/theme/Motion.kt`:

| Token | Especificación | Uso |
|---|---|---|
| `BudgetMotion.standard()` | resorte, amortiguación 0.8, rigidez 380 | Todo, salvo razón concreta |
| `BudgetMotion.canvas()` | resorte, amortiguación 0.85, rigidez 120 | Barridos de anillo y gráficas |
| `BudgetMotion.press()` | resorte crítico, rigidez alta | Respuesta al toque |

Complementos: `Modifier.pressScale` (0.97 al presionar), `Modifier.staggeredEntrance` (40 ms por elemento, tope de 8) y `LocalReducedMotion`, que lee la escala de animación del sistema y degrada a fundidos cortos.

**Nota de implementación:** aunque Material 3 1.4.0 está en el classpath, `MaterialExpressiveTheme` y `MotionScheme.expressive()` son internos en esa versión. El tema usa `MaterialTheme` estable y el sello expresivo se consigue con estos primitivos.

## 6. Qué hacer y qué no

**Hacer**

- Usar el espacio horizontal del Fold para una disposición de dos paneles con divisor arrastrable.
- Apoyarse en el peso variable de la tipografía para la jerarquía: una cifra muy fina junto a una etiqueta muy densa.
- Dejar aire: canales de 24 dp como mínimo entre bloques.
- Diseñar el estado a font 1.3 con negrita antes que el estado a escala normal.

**No hacer**

- Divisores de un píxel para separar elementos de una lista.
- Sombras al estilo Material 2: la profundidad es tonal.
- Esquinas duras en elementos estructurales.
- Confiar el significado financiero solo al color.
- Emojis dentro de un PDF exportado: la fuente de emoji de Android es de mapa de bits y no se embebe.
