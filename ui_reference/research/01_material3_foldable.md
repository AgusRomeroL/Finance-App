# Diseño adaptativo Material 3 / Material 3 Expressive para el dashboard Bento y la captura rápida en Pixel 9 Pro Fold

> Investigación deep research. Fecha de ejecución: 2026-06-27. Producto: app de presupuesto familiar quincenal (MXN, español), Jetpack Compose + Material 3 Expressive, sistema propio *The Architectural Ledger*. Dispositivo objetivo: Pixel 9 Pro Fold (externa ~6.3", interna ~8" casi cuadrada). Calibración temporal: prioridad 2024-2026.

---

## 1. Resumen ejecutivo

El stack adaptativo de Material 3 maduró: las APIs `androidx.compose.material3.adaptive:*` 1.0 son estables desde septiembre de 2024 y `NavigationSuiteScaffold`, `ListDetailPaneScaffold` y `SupportingPaneScaffold` permiten construir layouts que se reorganizan por *clase de tamaño de ventana* (compacta <600 dp, media 600-839 dp, expandida ≥840 dp) en lugar de estirarse. El display interno del Pixel 9 Pro Fold (~1768x2208 px, casi cuadrado, relación ~1:1) cae en **ventana expandida en anchura pero también en altura**, lo que habilita rail/drawer lateral más multipanel simultáneo, pero rompe el supuesto de la mayoría de los layouts canónicos, pensados para *tablets apaisadas* donde el ancho domina. Esa tensión es el eje del rediseño: un dashboard Bento de dos o tres paneles en un lienzo cuadrado conviene tratarlo como una rejilla 12-columnas con paneles asimétricos, no como un list-detail 70/30 horizontal puro.

Material 3 Expressive (anunciado en Google I/O 2025, respaldado por 46 estudios y >18,000 participantes) añade física de resortes (`MotionScheme`), 35 formas nuevas con *shape morphing*, roles de color más diferenciados, tipografía con énfasis y componentes nuevos (`FloatingToolbar`, `ButtonGroup`, `SplitButton`, FAB Menu, `WideNavigationRail`, indicadores ondulados). En junio de 2026 estos componentes están **graduando a estable dentro de `material3 1.5.0-alpha`** (el estable sigue siendo 1.4.0), por lo que su uso aún implica dependencia preview: una advertencia material para un único desarrollador. Para una interfaz financiera de alta claridad, conviene adoptar el énfasis tipográfico y los roles de color con disciplina, y reservar el movimiento expresivo para transiciones de panel y confirmaciones, no para los KPIs.

Para la captura rápida, `ModalBottomSheet` con manejo correcto de `imePadding`/`WindowInsets` y `skipPartiallyExpanded` es el patrón base; en ventana expandida conviene considerar un diálogo centrado o panel lateral en lugar de una hoja que cruza un lienzo cuadrado.

Cambio de plataforma crítico para 2026-2027: **Android 16 y, sobre todo, Android 17 (API 37) eliminan la posibilidad de restringir orientación y redimensionamiento en pantallas grandes** (>600 dp), obligatorio en Google Play desde agosto de 2027. El rediseño debe ser adaptativo por requisito, no por opción.

---

## 2. Navegación adaptativa por clase de tamaño de ventana

`NavigationSuiteScaffold` (artefacto `androidx.compose.material3:material3-adaptive-navigation-suite`) decide el componente de navegación a partir de `currentWindowAdaptiveInfo()` (clase de tamaño + postura) y conmuta en caliente al cambiar la ventana. La **lógica por defecto** (`NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo`) es:

- Barra inferior (`NavigationBar`) si la anchura **o** la altura es compacta, o si el dispositivo está en postura tabletop.
- Rail lateral (`NavigationRail`) en el resto de los casos.

Por defecto **no** promueve a drawer automáticamente; hay que forzarlo comprobando `isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)` y pasando `layoutType = NavigationSuiteType.NavigationDrawer`. Material 3 Expressive introduce además el *expanded navigation rail* y `WideNavigationRail` / `ModalWideNavigationRail`, que muestran etiqueta junto al icono y un indicador activo que "abraza" el texto; el modal se abre desde un icono de menú y superpone el contenido con scrim sin alterar la rejilla.

Breakpoints estándar Material 3 (anchura): compacta <600 dp; media 600-839 dp; expandida ≥840 dp. El display interno del Fold (~8", ~1768x2208 px ≈ 884x1104 dp a densidad ~2.0 [Inferencia], ver §4) queda en **expandida**, donde la decisión por defecto sería rail.

### Matriz: rail vs drawer vs barra inferior

| Componente | Umbral de anchura / condición | Caso de uso típico | Compose | Notas para el Fold |
|---|---|---|---|---|
| `NavigationBar` (barra inferior) | Anchura **o** altura compacta (<600 dp); postura tabletop | Externa plegada (~6.3", ventana compacta); pantalla en multiventana estrecha | `NavigationSuiteType.NavigationBar` | Pulgar alcanza el borde inferior; 3-5 destinos |
| `NavigationRail` (rail de iconos) | Media/expandida no compacta | Default en ventana expandida; tablet/foldable desplegado | `NavigationSuiteType.NavigationRail` | **Decisión tomada:** rail delgada de iconos en el display interno |
| `WideNavigationRail` (rail ancha con etiqueta) | Expandida; cuando cabe etiqueta | Apps con ≥5 destinos donde la etiqueta mejora descubribilidad | `WideNavigationRail` / `WideNavigationRailItem(railExpanded=…)` | Mitiga el problema icono-sin-etiqueta (§9) |
| `ModalWideNavigationRail` (rail modal) | Expandida, abierta desde icono de menú | Acceso ocasional a muchos destinos sin robar lienzo permanente | `ModalWideNavigationRail` | Overlay con scrim; no afecta el grid del Bento |
| `PermanentNavigationDrawer` | Expandida con lienzo de sobra | Apps de productividad con jerarquía profunda y secciones nombradas | `NavigationSuiteType.NavigationDrawer` | En lienzo cuadrado roba ancho valioso a los paneles |
| `ModalNavigationDrawer` | Cualquier clase, invocado por usuario | Navegación secundaria/temporal | `ModalNavigationDrawer` | Útil como "más" sin permanencia |

Fuentes: developer.android.com *build adaptive navigation*; blog Android Developers 2024-09; m3.material.io *navigation rail guidelines*.

---

## 3. Layouts canónicos adaptativos y mapeo al dashboard Bento casi cuadrado

Material 3 define tres layouts canónicos, cada uno con configuración para compacta/media/expandida:

- **Feed**: rejilla configurable de tarjetas (`LazyVerticalGrid` con `GridCells.Adaptive(minSize)`), una columna en compacta, varias en media/expandida; `maxLineSpan()` para encabezados a ancho completo.
- **List-detail** (`ListDetailPaneScaffold`): lista + detalle lado a lado en expandida; un solo panel en compacta/media. Anchuras de panel recomendadas para resize *snap-to* en expandida/large/xl: **360 dp · 412 dp** [No verificado en cifra exacta del lienzo cuadrado].
- **Supporting pane** (`SupportingPaneScaffold`): principal + apoyo. En **media** se reparten 50/50; en **expandida** principal **~70% / apoyo ~30%**; en compacta el apoyo cae a bottom sheet o se oculta. Admite hasta tres paneles (main, supporting, extra).

**Mapeo recomendado al dashboard Bento (transacciones recientes + salud financiera):** la relación más natural no es list-detail (ambos paneles son contenido independiente, no maestro/detalle) sino **supporting pane**: panel de transacciones recientes como *main* (~60-66%) y panel de salud financiera (KPIs + distribución por miembro) como *supporting* (~34-40%). En un lienzo casi cuadrado, sin embargo, el 70/30 horizontal canónico desperdicia altura; conviene tratar el Bento como **rejilla de 12 columnas** y componer celdas asimétricas (p. ej. transacciones 7 col x altura completa; salud financiera dividida verticalmente en KPIs arriba y distribución por miembro abajo en las 5 col restantes). Para un tercer panel (captura rápida fija, o detalle de transacción) usa el `extra`/`supporting` pane o un `ListDetailPaneScaffold` anidado.

Implementación: `rememberSupportingPaneScaffoldNavigator()`, `AnimatedPane`, `ThreePaneScaffoldPredictiveBackHandler`, y `enableOnBackInvokedCallback=true` en el manifiesto. Para forzar dos paneles ya en anchura media existe `calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth`.

---

## 4. Proporción de paneles, densidad y gutters para el display interno del Fold

Valores Material 3 (rejilla responsiva) por breakpoint:

| Breakpoint | Anchura | Columnas | Márgenes | Gutters |
|---|---|---|---|---|
| Compacta | <600 dp | 4 | 16 dp | 16 dp |
| Media | 600-839 dp | 8 (algunas fuentes citan 12) | 24-32 dp | 24 dp |
| Expandida | ≥840 dp | 12 | 24 dp+ (escala con ancho) | 24 dp |

El sistema de espaciado opera en incrementos de **4 dp**; 16 dp es el padding base y **24 dp** el espaciado recomendado entre componentes/paneles. (Fuente: m3.material.io *grids & spacing* y codelab *adaptive material guidance*; los valores exactos no fueron extraíbles de m3.material.io por render JS, confirmados vía búsqueda y documentación de Android — [parcialmente verificado].)

**Para el lienzo casi cuadrado del Fold (~884x1104 dp [Inferencia] a densidad ~2.0):**
- Usa **12 columnas, margen 24 dp, gutter 24 dp** como rejilla del Bento.
- Aprovecha la **altura**: al ser casi cuadrado, divide paneles también en vertical (Bento asimétrico de 2x2 o L invertida), evitando paneles muy anchos y cortos con líneas de texto excesivamente largas (la longitud de línea legible ronda 45-75 caracteres [principio fundacional vigente]).
- Densidad: el espacio negativo amplio del *Architectural Ledger* (radios 28 dp, separación tonal sin líneas de 1 px) encaja con la guía M3 de "layout espacioso = calma"; mantén ≥24 dp entre celdas Bento y respeta el padding interno de 16-24 dp por tarjeta.
- Aplica **ancho máximo** a componentes individuales dentro de cada panel (campos, botones) para evitar estiramiento; este es uno de los errores más citados al portar a pantallas grandes.
- `PaneExpansionState` (ahora `Saveable`, con `Modifier.paneExpansionDraggable` estable y semántica de accesibilidad) permite que el usuario arrastre el divisor entre paneles y persista la proporción.

---

## 5. Continuidad de plegado, posturas y estado

**Detección de postura** vía `WindowInfoTracker.windowLayoutInfo()` (Flow de `WindowLayoutInfo`) filtrando `FoldingFeature`; o `currentWindowAdaptiveInfo()` en Compose. Propiedades clave de `FoldingFeature`: `state` (FLAT, HALF_OPENED), `orientation` (HORIZONTAL, VERTICAL), `isSeparating`, `occlusionType`, `bounds`.

- **Tabletop** (HALF_OPENED + HORIZONTAL): contenido primario arriba del pliegue, controles abajo. Para captura rápida manos libres podría situar el formulario en la mitad inferior.
- **Book** (HALF_OPENED + VERTICAL): dos páginas izquierda/derecha; encaja con el Bento de dos paneles, uno por mitad.
- **`isSeparating`**: evita colocar controles o texto cruzando la bisagra; usa `bounds` para posicionar la separación entre celdas Bento exactamente sobre el pliegue (separación tonal del Ledger como costura natural).
- Comprobar posturas soportadas (Android 15+): `WindowInfoTracker.supportedPostures` si `WindowSdkExtensions.extensionVersion >= 6`.

**Continuidad de estado al plegar/desplegar:** al doblar/desdoblar, la actividad por defecto se destruye y recrea. La guía oficial recomienda **dejar que Android maneje el cambio de configuración** con layouts adaptativos y preservar estado con `rememberSaveable` (selección de transacción, posición de scroll, valor parcial del formulario de captura, proporción de paneles vía `PaneExpansionState.Saveable`). Alternativa: declarar `android:configChanges` para recomponer sin recrear, pero solo para casos especiales. Requisito explícito: mantener contexto, datos de entrada y posición de scroll a través de la transición. Para el formulario de captura quincenal, hoistea el estado a un `ViewModel` y respalda los campos in-progress con `rememberSaveable` o `SavedStateHandle`.

**Android 16/17 (crítico 2026):** Android 16 ya cambia las APIs de orientación/redimensionamiento; **Android 17 (API 37) ignora `screenOrientation`, `setRequestedOrientation()`, `resizeableActivity`, `minAspectRatio`/`maxAspectRatio` en pantallas grandes (>600 dp)**. Obligatorio en Google Play desde agosto de 2027 al targetear API 37. Implica que el rediseño debe soportar todas las orientaciones y tamaños sin restricciones; probar con emuladores de foldable/tablet en Android 17 Beta.

---

## 6. Material 3 Expressive en interfaz financiera

Cambios respecto a M3 base (Google I/O 2025; 46 estudios, >18,000 participantes):
- **Movimiento**: sistema de física de resortes; `MotionScheme` configurable dentro de `MaterialExpressiveTheme`/`MaterialTheme` (graduado a estable en 1.5.0-alpha15).
- **Formas**: 35 formas nuevas + *shape morphing* en la librería y en Compose; `ButtonGroup` morfea al tocar.
- **Color**: roles más diferenciados (primario/secundario/terciario), separación clara — alineado con la jerarquía de capas tonales del Ledger.
- **Tipografía y énfasis**: estilos con mayor peso/tamaño para titulares y acciones; Roboto Flex variable encaja directamente.
- **Componentes nuevos**: `FloatingToolbar` (h/v, con `FloatingToolbarScrollBehavior`), `ButtonGroup` (conectado), `SplitButton`, FAB Menu, `LoadingIndicator`/`WavyProgressIndicator`, `WideNavigationRail`.

**Estado de madurez (junio 2026):** estable = `material3 1.4.0`; los componentes expresivos están **graduando dentro de `1.5.0-alpha`** (FloatingToolbar alpha22, ButtonGroup/SplitButton/FAB/ToggleButtons alpha19-20, `materialExpressiveTheme` alpha18, `MotionScheme` alpha15). Se añadió `Material3ExpressiveApi` sin `OptIn` obligatorio. **Implicación:** usar Expressive hoy ata el proyecto a una dependencia alpha (advertencia real para un solo dev).

**Aplicabilidad a finanzas de alta claridad:** Google recomienda usar el énfasis expresivo **con moderación** para no diluir el impacto. Para datos financieros:
- Usa énfasis tipográfico y color en KPIs clave (saldo quincenal, restante por gastar), no en todo.
- Reserva el movimiento de resorte para transiciones de panel, expansión de acordeón de categorías y confirmación de guardado; evita animar cifras que el usuario debe leer con precisión.
- Aprovecha roles de color diferenciados para codificar miembros del hogar y estados (sobre/bajo presupuesto) con contraste suficiente sobre superficies tonales (§7).

---

## 7. Bottom sheets y captura rápida

**Tipos M3:** *standard/persistente* (`BottomSheetScaffold`, coexiste con la UI, útil para contenido secundario siempre visible) y *modal* (`ModalBottomSheet`, bloquea con scrim, alternativa a menús/diálogos con más espacio). Estados (`SheetValue`): Hidden, PartiallyExpanded, Expanded; `skipPartiallyExpanded` para abrir directo a expandido; `rememberModalBottomSheetState()`; drag handle por defecto.

**Teclado / IME (corrección 2024-2025):** se arregló que `ModalBottomSheet` aplicara `imePadding` incondicionalmente — ahora se controla vía `contentWindowInsets`/`WindowInsets`; la prioridad del callback de back se ajustó para que el IME se cierre primero. Para la captura, gestiona el inset del teclado para que el campo activo (monto) quede visible y el sheet no salte.

**Captura rápida — recomendaciones:**
- `ModalBottomSheet` con `skipPartiallyExpanded = true` para ir directo al formulario completo; drag handle visible.
- Minimiza pasos: monto con teclado numérico in-sheet enfocado al abrir; concepto, fuente de pago, categoría (acordeón inline + búsqueda en el mismo sheet, **decisión tomada**) y atribución por miembro como chips/`ButtonGroup`.
- Maneja `imePadding`/`contentWindowInsets` para que el campo enfocado nunca quede tras el teclado.
- **Ventana expandida (Fold desplegado):** una hoja inferior que cruza un lienzo de ~884 dp se ve desproporcionada. Considera, en expandida, **diálogo centrado con ancho máximo** o **panel lateral (`extra` pane)** en lugar de bottom sheet; reserva el sheet para la externa compacta. (M3 sugiere sustituir bottom sheets por diálogos/side sheets en pantallas grandes [parcialmente verificado: la guía m3 no fue extraíble por render JS, inferido de la práctica adaptativa documentada].)

---

## 8. Recomendaciones accionables mapeables a Compose Material 3

**Estructura global**
- Envolver la app en `NavigationSuiteScaffold`; forzar `WideNavigationRail` de iconos en el display interno y `NavigationBar` en la externa. Como rail es "de iconos delgada" (decisión tomada), **añadir `contentDescription` robusto y tooltips** y considerar `railExpanded` opcional para mitigar descubribilidad (§9).
- Theming con `MaterialExpressiveTheme` + `expressiveLightColorScheme` + `MotionScheme`; mapear primario verde `#016e3e`, radios 28 dp en `Shapes`, Roboto Flex en `Typography`.

**Dashboard Bento**
- Base: `SupportingPaneScaffold` (main = transacciones recientes ~60-66%; supporting = salud financiera) con rejilla 12-col, margen/gutter 24 dp.
- Para el lienzo cuadrado, componer celdas con `Row`/`Column` + pesos o `LazyVerticalGrid` con spans; dividir el panel de salud en KPIs (arriba) y distribución por miembro (abajo).
- Transacciones recientes: `LazyColumn` con ancho máximo por fila; tarjetas con superficie tonal (sin líneas 1 px).
- Permitir resize de paneles con `PaneExpansionState` + `Modifier.paneExpansionDraggable` (Saveable).
- Detalle de transacción: `ListDetailPaneScaffold` anidado o `extra` pane; `AnimatedPane` + predictive back.

**Hoja de captura rápida**
- Externa/compacta: `ModalBottomSheet(skipPartiallyExpanded=true)`; gestionar `imePadding`.
- Interna/expandida: `Dialog`/`BasicAlertDialog` centrado con `widthIn(max=…)` o panel `extra`.
- Campos: monto (teclado numérico, autofoco), concepto (`OutlinedTextField`), fuente de pago y categoría (acordeón inline expandible + `SearchBar` embebida), atribución por miembro (`ButtonGroup`/`FilterChip`).
- Acción primaria: botón "Guardar" con énfasis expresivo y confirmación con `MotionScheme` (resorte breve).
- Persistir borrador con `rememberSaveable`/`SavedStateHandle` para sobrevivir plegado.

**Estado y continuidad**
- Hoistear a `ViewModel`; `rememberSaveable` para selección, scroll, expansión de panel y borrador de captura.
- `enableOnBackInvokedCallback=true`; `ThreePaneScaffoldPredictiveBackHandler`.
- Detectar postura con `currentWindowAdaptiveInfo()`; alinear costuras Bento con `FoldingFeature.bounds` cuando `isSeparating`.

---

## 9. Contraevidencia y tensiones (mandato adversarial)

- **Rail de iconos sin etiqueta perjudica descubribilidad.** Nielsen Norman Group documenta de forma consistente que los iconos sin etiqueta son ambiguos, reducen el tamaño de objetivo y llevan a que los usuarios ignoren la navegación; recomienda etiquetas con palabra clave al frente. La decisión tomada (rail delgada de iconos) es defendible para 3-5 destinos muy familiares, pero **conviene mitigarla** con `WideNavigationRail`/`ModalWideNavigationRail` (etiqueta junto al icono), tooltips y `contentDescription`. M3 Expressive de hecho empuja hacia el *expanded rail* con etiqueta, lo que sugiere que Google ve la rail-solo-iconos como subóptima para descubribilidad.

- **El lienzo casi cuadrado rompe layouts canónicos pensados para tablets apaisadas.** El supporting pane 70/30 y el list-detail 360/412 dp asumen que el ancho domina. En ~1:1, esos splits dejan paneles anchos y cortos o desperdician altura. Tratar el Bento como rejilla 12-col con celdas asimétricas (incluida división vertical) es más sólido que aplicar el canónico tal cual. [Inferencia basada en principios de longitud de línea y en los avisos oficiales sobre estiramiento.]

- **Material 3 Expressive es reciente y aún no estable.** En junio de 2026 el estable es `material3 1.4.0`; los componentes expresivos graduaron en `1.5.0-alpha`. Usarlos implica dependencia alpha, posibles cambios de API y herramientas Figma con fricciones reportadas (export de variables/temas). Para un único dev que alimentará una IA de diseño, esto añade riesgo de churn. Mitigación: aislar el uso expresivo tras wrappers propios y fijar versión.

- **Bottom sheet en pantalla grande.** Un sheet inferior que cruza un lienzo cuadrado de ~884 dp es ergonómicamente débil (recorrido del pulgar, proporción). La práctica adaptativa favorece diálogo/side sheet en expandida; mantener el sheet solo en compacta.

- **Coste de adaptatividad.** Multipanel + posturas + estado preservado multiplica la superficie de prueba (compacta, media, expandida, tabletop, book, multiventana, Android 17 sin restricciones). Para un solo desarrollador es esfuerzo real; priorizar dos breakpoints (compacta externa, expandida interna) y postura book/tabletop como mejoras incrementales.

---

## 10. Vacíos e incertidumbre

- **Cifras exactas de margen/columna por breakpoint en m3.material.io: [parcialmente verificado].** Las páginas de m3.material.io son JS-rendered y no fueron extraíbles por WebFetch; los valores (4/8/12 columnas, 16/24 dp) provienen de búsqueda agregada y de documentación Android, consistentes entre sí pero no leídos del documento canónico original en esta sesión.
- **Densidad y dp efectivos del Pixel 9 Pro Fold interno: [Inferencia].** ~1768x2208 px / densidad ~2.0 ≈ 884x1104 dp; la densidad exacta (xhdpi vs ~390 dpi reales) no se verificó contra specs de Google. Las fuentes citan también 2076x2152 px y relaciones ~1:1; conviene confirmar en el emulador real.
- **Comportamiento canónico de bottom sheet en pantalla grande: [parcialmente verificado]** por render JS de m3; la recomendación de diálogo/side sheet es inferencia de la práctica adaptativa documentada.
- **Anchuras snap-to 360/412 dp**: confirmadas como recomendación de resize de dos paneles, pero no específicamente validadas para lienzo cuadrado [No verificado en ese contexto].
- **`PaneExpansionState`/drag-to-resize**: confirmado como estable vía release notes, no leído end-to-end en guía dedicada.
- No se consultaron estudios de caso primarios de apps financieras concretas con buen soporte foldable (vacío deliberado por convergencia temprana; circuito no agotado).

Fuentes consultadas: ~27. Convergencia alcanzada antes del umbral de 60; no se activó el disyuntor.

---

## 11. Bibliografía (URLs canónicas)

Documentación oficial de Google / Android:
- Build adaptive navigation (Compose): https://developer.android.com/develop/ui/compose/layouts/adaptive/build-adaptive-navigation
- Build adaptive navigation (Adaptive apps): https://developer.android.com/develop/adaptive-apps/guides/build-adaptive-navigation
- Canonical layouts (Adaptive apps): https://developer.android.com/develop/adaptive-apps/guides/canonical-layouts
- Canonical layouts (Views): https://developer.android.com/develop/ui/views/layout/canonical-layouts
- Build a supporting pane layout: https://developer.android.com/develop/ui/compose/layouts/adaptive/build-a-supporting-pane-layout
- Make your app fold aware: https://developer.android.com/develop/ui/compose/layouts/adaptive/foldables/make-your-app-fold-aware
- Support trifolds and landscape foldables: https://developer.android.com/develop/ui/compose/layouts/adaptive/foldables/trifolds-and-landscape-foldables
- Configuration and continuity (large screens): https://developer.android.com/guide/topics/large-screens/configuration-and-continuity
- App orientation, aspect ratio, and resizability: https://developer.android.com/develop/ui/compose/layouts/adaptive/app-orientation-aspect-ratio-resizability
- Bottom sheets (Compose): https://developer.android.com/develop/ui/compose/components/bottom-sheets
- Navigation rail (Compose): https://developer.android.com/develop/ui/compose/components/navigation-rail
- Material Design 3 in Compose: https://developer.android.com/develop/ui/compose/designsystems/material3
- Compose Material3 releases: https://developer.android.com/jetpack/androidx/releases/compose-material3
- Compose Material3 Adaptive releases: https://developer.android.com/jetpack/androidx/releases/compose-material3-adaptive
- Codelab adaptive material guidance: https://developer.android.com/codelabs/adaptive-material-guidance

Blog Android Developers:
- Adaptive layouts APIs now stable (2024-09): https://android-developers.googleblog.com/2024/09/jetpack-compose-apis-for-building-adaptive-layouts-material-guidance-now-stable.html
- Orientation & resizability changes in Android 16 (2025-01): https://android-developers.googleblog.com/2025/01/orientation-and-resizability-changes-in-android-16.html
- Prepare for resizability/orientation changes in Android 17 (2026-02): https://android-developers.googleblog.com/2026/02/prepare-your-app-for-resizability-and.html
- Scaling Across Screens with Compose @ I/O '24: https://android-developers.googleblog.com/2024/05/scaling-across-screens-with-compose-google-io-24.html

Material Design 3 (m3.material.io — render JS, citado por referencia):
- Canonical layouts: https://m3.material.io/foundations/adaptive-design/canonical-layouts
- Bottom sheets guidelines: https://m3.material.io/components/bottom-sheets/guidelines
- Navigation rail guidelines: https://m3.material.io/components/navigation-rail/guidelines
- Grids & spacing: https://m3.material.io/foundations/layout/understanding-layout/spacing
- Applying layout / breakpoints: https://m3.material.io/foundations/layout/applying-layout
- Accessibility / structure: https://m3.material.io/foundations/designing/structure

Material 3 Expressive (oficial):
- Launch blog: https://blog.google/products-and-platforms/platforms/android/material-3-expressive-android-wearos-launch/
- M3 Expressive for Wear OS (dev blog): https://android-developers.googleblog.com/2025/08/introducing-material-3-expressive-for-wear-os.html

Accesibilidad / contraevidencia (secundarias reputadas):
- Touch target size (Android Accessibility Help): https://support.google.com/accessibility/android/answer/7101858
- Compose accessibility API defaults: https://developer.android.com/develop/ui/compose/accessibility/api-defaults
- NN/G Icon Usability: https://www.nngroup.com/articles/icon-usability/
- NN/G Left-Side Vertical Navigation: https://www.nngroup.com/articles/vertical-nav/

Dispositivo:
- Pixel 9 Pro Fold (Wikipedia): https://en.wikipedia.org/wiki/Pixel_9_Pro_Fold
- Pixel 9 Pro Fold specs (GSMArena): https://www.gsmarena.com/google_pixel_9_pro_fold-13220.php
- Pixel 9 Pro Fold specs (Google Store): https://store.google.com/product/pixel_9_pro_fold_specs

Análisis de industria (secundarias):
- Dylan Roussel — M3 Compose Adaptive: https://evowizz.dev/blog/first-look-m3-compose-adaptive
- Ian G. Clifton — New APIs for adaptive layouts: https://medium.com/androiddevelopers/new-apis-for-adaptive-layouts-in-jetpack-compose-f27cace48bcd
- ProAndroidDev — Material 3 Expressive: https://proandroiddev.com/material-3-expressive-design-a-new-era-9ea77959a262
- Composables — ModalWideNavigationRail / WideNavigationRail: https://composables.com/material3/modalwidenavigationrail
