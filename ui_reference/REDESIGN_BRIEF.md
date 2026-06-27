# Brief de rediseño UI — Finance-App (para Claude Design)

> Versión final consolidada y basada en evidencia. Reemplaza el brief preliminar.
> Síntesis de cinco informes de investigación (dos internos + tres externos de ChatGPT/Gemini) sobre el rediseño de dos pantallas de una app Android de presupuesto familiar quincenal para Pixel 9 Pro Fold.
> Cada recomendación lleva su **nivel de confianza por convergencia de fuentes**. Las etiquetas usadas:
> - **(convergen 5/5)** = todas las fuentes coinciden.
> - **(4/5)**, **(mío + ChatGPT + Gemini)**, etc. = convergencia parcial, con las fuentes nombradas.
> - **(solo 1 fuente)** = recomendación con respaldo único; tratar como hipótesis.
> - `[Inferencia]` / `[No verificado]` = deducción razonada o afirmación cuya fuente no pudo confirmarse.
>
> **Mapa de fuentes:** F1 = interno M3/foldable · F2 = interno UX fintech · F3 = ChatGPT M3/foldable · F4 = ChatGPT UX fintech · F5 = Gemini M3/foldable.

---

## 0. Cómo usar este brief

Objetivo: rediseñar dos pantallas (Dashboard y Registro de gasto / captura rápida) para que sean **más funcionales y más bonitas**, honrando el sistema de diseño existente "The Architectural Ledger". El documento está estructurado para alimentar una herramienta de diseño IA: primero el contexto autocontenido, luego las convergencias de alta confianza (lo que casi todas las fuentes piden), después las divergencias resueltas con recomendación concreta, y finalmente las especificaciones por pantalla mapeadas a Compose Material 3. Cierra con los adjuntos y entregables.

---

## 1. Contexto del producto (autocontenido)

App Android de **presupuesto familiar quincenal** de un hogar mexicano (un solo household por instalación). Idioma **español**, moneda **MXN**, zona horaria `America/Mexico_City`. La unidad operativa central es la **quincena** (1ª mitad 1–15 / 2ª mitad 16–fin de mes); el diseño debe hacer explícito ese contenedor temporal y no asumir un mes calendario implícito (F4, mental budgeting / Heath-Soll). Miembros reales: adultos-pagadores (Benjamín, Norma) y dependientes (Pau, David, Agustín, Santiago). Cada gasto se atribuye en **dos dimensiones independientes**: *quién lo consume* (beneficiario) y *quién lo paga* (pagador), cada una en porcentajes.

**Dispositivo target:** Pixel 9 Pro Fold.
- Pantalla externa plegada ≈ 6.3" → ventana **compacta** (`< 600dp`, una columna + navegación inferior).
- Pantalla interna desplegada ≈ 8" → ventana **expandida** **casi cuadrada** (≈ **884 × 1104 dp** a densidad ~2.0 [Inferencia]; relación de aspecto ≈ 1.04:1). Hay una discrepancia factual entre las fuentes sobre la resolución física exacta (`1768 × 2208 px` en el contexto de proyecto vs. `2152 × 2076 px` en la ficha oficial de Google según F3/F5); para layout esto es irrelevante porque la conclusión —display casi cuadrado, no tablet apaisada— es robusta en ambos casos. Pide mockups para **ambos** estados.

**Implementación:** Jetpack Compose + Material 3 (Expressive). Los diseños deben ser **construibles con Compose** (componentes M3 reales, formas, tonal layering). Si propones un efecto difícil en Compose, anótalo.

**Requisito de plataforma (no negociable):** Android 16 ya cambió las APIs de orientación/redimensionamiento, y **Android 17 (API 37) ignora `screenOrientation`, `setRequestedOrientation()`, `resizeableActivity`, `minAspectRatio`/`maxAspectRatio` en pantallas grandes (>600 dp)**, obligatorio en Google Play desde agosto de 2027 (F1 + F3, convergencia fuerte). **El rediseño debe ser adaptativo por requisito, no por opción**; no se puede bloquear orientación ni forzar un layout rígido.

---

## 2. Sistema de diseño a respetar (resumen; el detalle vive en DESIGN.md)

- **Color base:** `primary #016e3e` (verde contable), `primary_dim #006035`. Semánticos: ingreso `#0F5A2E`, gasto/error `#BA1A1A`, warning `#8B5A00`.
- **Superficies (jerarquía por capas, NO por líneas):** `surface #f9f9f9` → `surface_container_low #f3f4f4` (zonas estructurales/nav) → `surface_container #edeeee` → `surface_container_lowest #ffffff` (tarjetas de datos) → `surface_container_highest #e0e3e4` (overlays). Esta decisión de separar por capas tonales en lugar de líneas de 1 px está **alineada con la dirección oficial de M3** (surface container roles, tone-based surfaces) (F3, mío).
- **Regla "No-Line":** prohibido bordes/divisores sólidos de 1 px. Separar con cambios tonales de superficie y espacio en blanco (gutters ≥ 24 dp; ítems de lista separados con 8–16 dp). Con No-Line, **la jerarquía visual recae enteramente en el espaciado** (F5): respétalo religiosamente.
- **Tipografía Roboto Flex variable:** balances grandes en `display-lg` (~3.5rem) `wght 300`; títulos de sección `headline-sm` (1.5rem) `wght 600`; datos `body-lg` 1rem `wght 400`; etiquetas tipo ledger `label-md` 0.75rem `wght 700`, MAYÚSCULAS, `+0.05rem` tracking. Nota técnica: Roboto Flex **aún no forma parte del typescale M3 por defecto** (F3); úsalo selectivamente y con ejes variables solo en cifras hero y encabezados, no en body/listas.
- **Formas:** contenedores mayores (cards, paneles, bottom sheets) radio **28 dp**; chips y botones **pill (full)**. Descarta el catálogo de formas orgánicas/amoeba de M3 Expressive; mantén el vocabulario en rectángulos redondeados de 28 dp (F5).
- **Profundidad:** tonal layering, NO sombras Material 2. Si algo "flota", sombra ultradifusa tintada (`0 12px 40px rgba(0,0,0,0.04)`).
- **CTAs:** gradiente 135° `primary → primary_dim`. Elementos flotantes pueden usar glassmorphism (70% opacidad + blur 24px).
- **Layout del Fold:** "Side-Car" de navegación a la izquierda + "Bento-Box" de datos a la derecha, asimétrico, con mucho espacio negativo.

---

## 3. Convergencias de alta confianza

Lo que **todas o casi todas** las fuentes recomiendan. Estos puntos pueden tratarse como decisiones firmes.

| # | Recomendación | Convergencia | Evidencia clave |
|---|---|---|---|
| C1 | **Tratar la pantalla interna como ventana expandida casi cuadrada, NO como tablet apaisada.** Aflojar la receta canónica 70/30; trabajar con proporciones más balanceadas y división vertical de paneles. | **5/5** | El 1.04:1 rompe el supuesto de los layouts canónicos pensados para 16:9/4:3; aplicar 70/30 "a rajatabla" deja columnas estrechas que truncan cifras MXN (F1, F3, F5) y desperdician altura (F2, F4). |
| C2 | **Shell de navegación adaptativo con `NavigationSuiteScaffold`**: `NavigationBar` inferior en compacto/tabletop, `NavigationRail` en expandido, conmutando por `currentWindowAdaptiveInfo()`. | **mío + ChatGPT + Gemini** (F1, F3, F5) | Patrón oficial unificador; la rail es más cómoda al sostener el dispositivo por los lados en expandido. |
| C3 | **La rail de iconos perjudica descubribilidad y DEBE mitigarse** (no abandonarse: es decisión tomada). | **5/5** | NN/g: iconos sin etiqueta son ambiguos y se ignoran (F1, F2). M3 Expressive empuja al *expanded rail* con etiqueta (F1, F3). Mitigación obligatoria, ver D1. |
| C4 | **Estructura macro = `SupportingPaneScaffold`, no list-detail.** Los dos paneles son contenido independiente con valor simultáneo, no maestro/detalle. | **mío + ChatGPT + Gemini** (F1, F3, F5) | List-detail presupone jerarquía padre-hijo que aquí no existe; supporting pane modela "principal + contexto". |
| C5 | **Dentro del pane, componer un Bento real con rejilla adaptable**, no un `Column`/`Row` estirado; dividir el panel de salud financiera en KPIs (arriba) y distribución por miembro (abajo). | **5/5** | `LazyVerticalGrid` con spans variables; celdas asimétricas; división vertical aprovecha el alto del lienzo cuadrado (F1, F3, F5; respaldado por densidad/legibilidad en F2, F4). |
| C6 | **M3 Expressive con disciplina quirúrgica.** Énfasis tipográfico y color solo en KPIs; motion de resorte solo en transiciones de panel, expansión de acordeón y confirmación de guardado. **NUNCA animar las cifras que el usuario debe leer.** | **5/5** | Google recomienda usar el énfasis con moderación; en fintech el overshoot transmite falta de rigor fiduciario (F1, F3, F5; alineado con confianza/ansiedad en F2, F4). |
| C7 | **Precaución por APIs Expressive en alpha.** En jun-2026 el estable es `material3 1.4.0`; los componentes expresivos graduaron en `1.5.0-alpha`. Aislar el uso tras wrappers, fijar versión, **preferir estable en flujos críticos**. | **mío + ChatGPT + Gemini** (F1, F3, F5) | Historial de remociones/graduaciones no lineales entre ramas beta/alpha. |
| C8 | **KPI héroe = "disponible para gastar" (safe-to-spend)** como cifra dominante, con **fórmula visible** ("Disponible = presupuesto − gasto − compromisos apartados") y **periodo activo** siempre visible ("Quincena: 16–30 jun"). | **mío + ChatGPT (F2, F4)** | Patrón canónico Simple Bank/YNAB; descarga la aritmética mental; cada cifra financiera debe exponer origen, temporalidad y última actualización. |
| C9 | **Distribución por miembro = barras horizontales ordenadas, NO dona/treemap.** | **mío + ChatGPT (F2, F4)** | Jerarquía perceptual Cleveland-McGill (1984), replicada Heer-Bostock (CHI 2010): posición/longitud > ángulo/área. Dona solo para una cifra protagonista única; treemap solo como vista secundaria. |
| C10 | **Categoría = recientes/frecuentes + búsqueda con autocompletado + acordeón jerárquico**, NO la rejilla plana de 20+ pills. | **mío + ChatGPT (F2, F4)** | El anti-patrón real es el escaneo serial sin orden/jerarquía, no la "sobrecarga de elección" (Scheibehenne; Chernev). Híbrido secuencial: franja fija de 6–8 recientes + campo de búsqueda + grupos colapsables (6–8 padres, ancho > profundo). |
| C11 | **Captura: un solo campo de monto** con foco automático y teclado decimal, **defaults por recencia/frecuencia visibles y sobreescribibles**, **divulgación progresiva** (monto + categoría arriba; resto tras "Más"). | **mío + ChatGPT (F2, F4)** | Baymard mobile forms; NN/g defaults inteligentes; progressive disclosure. Validar **on-blur**, conservar la entrada ante error. |
| C12 | **Beneficiario vs. pagador = dos dimensiones explícitas con verbos distintos** ("pagó" / "beneficia a"); **evitar vocabulario de deuda/settlement** ("liquidar", "saldar", "ajustar cuentas"). | **mío + ChatGPT (F2, F4)** | Monarch "mío/suyo/nuestro"; CSCW: faltan lenguajes compartidos; el neteo de deudas rompe el modelo "a quién le debo" y genera fricción social (Splitwise teardowns). |
| C13 | **Continuidad de plegado:** hoistear estado a `ViewModel` + `SavedStateHandle`; `rememberSaveable` para selección, scroll (`rememberLazyListState`), expansión de panel y **borrador de captura a medio escribir**. | **mío + ChatGPT + Gemini** (F1, F3, F5) | Al plegar/desplegar la actividad se recrea; debe sobrevivir contexto, scroll y datos parciales del formulario. |
| C14 | **Microcopia calmada, no culpabilizante; prevención de error con texto bajo el campo.** "Restan $2,450 para 6 días" / "Faltan 20% por asignar", nunca "Te estás quedando sin dinero". | **mío + ChatGPT (F2, F4)** | La credibilidad en finanzas se juzga por el diseño (Stanford WCP); el rastreo induce culpa con facilidad; M3 recomienda error text como supporting text bajo el campo, sustituyendo el supporting text para evitar layout shift. |

---

## 4. Divergencias y decisiones a tomar

Donde las fuentes difieren. Cada una se cierra con una recomendación concreta para Claude Design.

### D1 — Mitigación de la rail de iconos (cómo, no si)

Las fuentes convergen en que la rail-solo-iconos daña descubribilidad (C3), pero divergen en la mitigación: F1 propone `WideNavigationRail`/`ModalWideNavigationRail` con etiqueta + tooltips + `contentDescription`; F3 sugiere añadir un drawer textual invocable si la IA crece más allá de 5 destinos; F5 sugiere micro-badges de notificación para dar contexto diferencial a los glifos.

> **Recomendación:** mantener la **rail delgada de iconos (~72–80 dp)** como decisión tomada, y mitigar en **tres capas acumulativas**: (1) `contentDescription` robusto + tooltips en cada item (mínimo viable, siempre); (2) **etiqueta visible bajo el icono del item seleccionado** y/o título de pantalla muy claro en el header del Bento; (3) reservar para fase futura un `ModalWideNavigationRail` invocable desde un icono de menú si los destinos pasan de 5. Los badges de F5 son aceptables como refuerzo, no como sustituto de la etiqueta. Con solo 3–5 destinos muy familiares usados quincenalmente, la memoria muscular compensa, pero las capas 1–2 no son opcionales.

### D2 — Contenedor de captura en el display interno expandido

Divergencia conocida: **F1** (interno) sugiere **diálogo centrado** con `widthIn(max=…)` o panel `extra`; **F3** (ChatGPT) sugiere **hoja modal con ancho restringido** (~2/3 del body, máx ~640–720 dp); **F5** (Gemini) sugiere **hoja inferior con ancho acotado a máx ~640 dp**, comportándose como elemento flotante anclado al eje vertical central, con un argumento ergonómico fuerte (zonas de alcance del pulgar de Hoober). La decisión de producto ya fija "bottom sheet" para captura.

> **Recomendación (reconciliada):** conservar **`ModalBottomSheet`** en ambos estados para coherencia de patrón con el flujo de captura ya decidido, pero **en expandido acotarla a un ancho máximo de ~640 dp**, anclada al centro horizontal con márgenes laterales transparentes, comportándose visualmente como una hoja flotante (no un rectángulo de borde a borde). Esto integra el ancho de Gemini (640 dp, el más respaldado por la guía adaptativa de M3 y por ergonomía del pulgar) con la prudencia de ChatGPT, y evita el cambio de patrón a diálogo que propone F1 —el diálogo es defendible pero rompe la continuidad del gesto de captura y añade una segunda variante de layout a mantener. Implementar con `ModalBottomSheet` + `contentWindowInsets` consciente; CTA "Registrar" pegado al borde inferior seguro, nunca oculto por IME ni system bars. **Punto abierto para fase 2:** Gemini propone un **numpad numérico custom embebido en la hoja** (3×4, teclas 56 dp) en vez del IME del sistema, para evitar el redimensionamiento abrupto de ventana al abrir el teclado; es una sola fuente pero ergonómicamente sólida para captura repetitiva de montos —vale la pena pedirlo como variante, ya que la captura actual ya usa numpad propio.

### D3 — Proporción de paneles del dashboard en pantalla casi cuadrada

Divergencia conocida: **60/40** (brief preliminar, sugerido), **balanceado / división vertical** vs **rejilla Bento 12 columnas**. Las fuentes: F1 propone rejilla **12 columnas** con celdas asimétricas + supporting pane ~60–66/34–40; F3 propone **7/5 columnas** (≈ 58/42) con hero arriba-izq, distribución abajo-izq y transacciones como columna alta a la derecha, gutter 24 dp; F5 propone **65/35** anclado con `PaneExpansionAnchor.Proportion`, gutter ampliado a **32 dp** para que los radios de 28 dp no se asfixien.

> **Recomendación (reconciliada):** **rejilla de 12 columnas** como sistema base (es el marco, no compite con las proporciones), con el divisor macro del `SupportingPaneScaffold` anclado en **~62/38** vía `PaneExpansionAnchor.Proportion` (`Saveable`, arrastrable por el usuario con `Modifier.paneExpansionDraggable`). El **pane principal (~7–8 columnas)** aloja la **salud financiera** dividida verticalmente: KPI héroe + ritmo de quincena arriba, distribución por miembro (barras horizontales) abajo. El **pane de apoyo (~4–5 columnas)** aloja **transacciones recientes** como columna alta y estrecha que emula la proporción de un teléfono anidado —esto resuelve el problema de "dos columnas idénticas que compiten" (F3, F5) y da a las transacciones la verticalidad que una lista quiere. **Gutter 24 dp por defecto, 32 dp entre los dos panes macro** (compromiso entre F1/F3 a 24 y F5 a 32: la separación mayor solo donde el radio de 28 dp lo exige). Margen perimetral 24–32 dp; padding interno por tarjeta 16 dp. La proporción exacta debe quedar **arrastrable y persistida**, no congelada.
>
> Nota sobre "izquierda vs derecha" (pregunta abierta del brief preliminar): coloca **salud financiera en el pane principal** (mayor, izquierda junto a la rail) por ser el contexto crítico para decidir, y **transacciones recientes a la derecha** como columna de acción. F3 y F5 convergen en esta asignación; F2/F4 son agnósticos al lado pero exigen que salud financiera tenga la cifra dominante.

### D4 — Distribución por miembro: barras simples vs apiladas con toggle

Matiz entre fuentes: F2 recomienda **barras horizontales agrupadas/ordenadas** (apiladas solo si el total es el mensaje y miembros ≤5); F4 propone **barras horizontales apiladas con toggle beneficiario/pagador**.

> **Recomendación:** **barras horizontales ordenadas de mayor a menor** con un **toggle explícito "beneficiario / pagador"** (las dos dimensiones nunca mezcladas en la misma vista). Usar apilada **solo** si en algún momento el mensaje es el total del hogar y los miembros son pocos; por defecto, barras simples ordenadas (más legibles, Cleveland-McGill). El toggle es la pieza clave: materializa las dos dimensiones de C12 sin colapsarlas.

---

## 5. Pantalla A — Dashboard (estado actual y rediseño)

**Captura:** `brief_dashboard.png`.

**Estructura actual (expandido):**
- Izquierda: `PermanentNavigationDrawer` de **256 dp** con ítems de texto (Dashboard, Libro Mayor, Cuentas, Analíticas, Perfil).
- Centro: panel "Transacciones Recientes" — lista de tarjetas (avatar con iniciales, concepto, método de pago tipo "EFECTIVO", chip de categoría, fecha, monto).
- Derecha: panel "Salud Financiera" — tarjeta con 3 KPIs (Presupuesto / Gastado / Disponible) + "Distribución por Miembro" (barras).
- Header: "Presupuesto Familiar" + etiqueta de quincena. FAB verde abajo-derecha.

**Problemas (usuario):** (1) el drawer de 256 dp roba demasiado ancho, apretando ambos paneles; (2) no se ve bonito — tarjetas con bordes/divisores visibles, aire insuficiente, viola No-Line.

**Rediseño — especificación:**

- **Navegación:** sustituir el drawer por **`NavigationSuiteScaffold` forzado a `NavigationSuiteType.NavigationRail`** (~72–80 dp) en interno, `NavigationBar` en externo (C2). Mitigación de descubribilidad según D1.
- **Macro-layout:** `SupportingPaneScaffold` con divisor ~62/38 arrastrable, rejilla 12-col, gutters 24/32 dp (D3). Salud financiera = pane principal (izq); transacciones = pane de apoyo (der, columna alta).
- **KPI héroe:** "**Disponible para gastar**" como cifra dominante en `display-lg wght 300`, etiqueta MXN en bold pequeño al lado (contraste de pesos). Subtítulo "Restan $X para Y días de esta quincena" + enlace "Cómo se calcula" (C8). Periodo activo textual siempre visible ("Quincena: 16–30 jun").
- **KPIs secundarios:** Presupuesto y Gastado como soporte, NO rivales visuales del disponible (jerarquía visual = pregunta más accionable primero). Máx. 4–6 KPIs sin scroll (F3).
- **Ritmo de quincena:** barra de progreso / bullet con marcador de "ritmo esperado" (proporcional a días transcurridos, **etiquetado como referencia, no verdad normativa**: los hogares tienen picos por despensa/colegiaturas). Codificar estado sobre/bajo presupuesto **por forma + posición, no por color solo** (~8% de hombres con deficiencia de visión cromática; WCAG) (C9, F2/F4). Cuidado con el framing licenciante: "disponible" como orientación prudente, no como permiso (F4, contraevidencia dynamic budget monitoring).
- **Distribución por miembro:** barras horizontales ordenadas + toggle beneficiario/pagador (D4).
- **Transacciones recientes:** `LazyColumn` limpio, tarjetas con tonal layering (sin divisores 1 px), ancho máximo por fila, separación 8–16 dp. Cada fila: monto, concepto, categoría, wallet, fecha, miembro, resumen de reparto, acción rápida editar/duplicar.
- **Opcional (drill-down):** detalle de transacción vía `ListDetailPaneScaffold` anidado o `extra` pane, con `AnimatedPane` + predictive back (`ThreePaneScaffoldPredictiveBackHandler`, `enableOnBackInvokedCallback=true`).
- **Densidad:** resistir la tentación de "llenar" la pantalla. Pocas métricas hero, labels cortos, longitud de línea controlada (45–75 caracteres). Un dashboard saturado desplaza el esfuerzo del análisis a la decodificación visual (F2, F4, eye-tracking).
- **Compacto (externo):** una columna, `NavigationBar` inferior, salud financiera colapsada arriba + lista de transacciones abajo. El pane de apoyo cae debajo del principal (estrategia *reflow* de M3 Adaptive).

---

## 6. Pantalla B — Registro de gasto / Quick Capture (estado actual y rediseño)

**Captura:** `brief_capture.png`. Es un `ModalBottomSheet`.

**Estructura actual:** header "Quick Capture" + X; monto gigante `$0.00`; campo "Concepto"; **FUENTE** (fila de wallets); **CATEGORÍA** (rejilla de 20+ pills ← ruido visual); **ATRIBUCIÓN** (chips de miembros + "Todos"); numpad 3×4 + "Registrar".

**Problema (usuario):** la categoría con 20+ pills abruma y ralentiza.

**Rediseño — especificación:**

- **Contenedor:** `ModalBottomSheet` con `skipPartiallyExpanded` según convenga; en interno, acotado a ~640 dp centrado (D2). Gestionar `contentWindowInsets`/`imePadding` para que el campo enfocado nunca quede tras el teclado.
- **Monto:** un solo campo, foco automático al abrir, teclado decimal (`KeyboardOptions`, `inputmode="decimal"` equivalente). Cifra grande, símbolo MXN contextual no editable. Formateo **on-blur**, valor crudo almacenado (normalizar punto/coma internamente). Considerar el **numpad custom embebido** de D2 como variante.
- **Divulgación progresiva (C11):** monto + categoría arriba; concepto, fuente de pago, atribución detallada (%), fecha y notas detrás de "Más". Campos mínimos para guardar: monto, categoría y un reparto válido; concepto y fuente con defaults editables.
- **Categoría (DECIDIDO — acordeón inline + búsqueda en el mismo sheet, C10):**
  1. **Fila de recientes/frecuentes** (3–8 chips, **aditiva**, NO reordena el catálogo completo — preserva memoria espacial).
  2. **Campo de búsqueda con autocompletado** (4–8 sugerencias, resalta la **porción predictiva**, no el término tecleado; tolera sinónimos coloquiales mexicanos y faltas comunes). Usar `OutlinedTextField` embebido + lista filtrada; **evitar `SearchBar`** porque sigue marcada experimental y su patrón de búsqueda expandida abre vistas completas que rompen la permanencia en la hoja (F3, decisión técnica).
  3. **Grupos colapsables (acordeón)** por categoría padre (6–8 grupos máx, ancho > profundo), colapsados por defecto. Agrupación sugerida: *Suscripciones, Colegiaturas, Servicios del hogar, Despensa/Comida, Transporte, Otros*. La transición colapsado→expandido se orquesta con shape morphing / resorte suave (C6).
- **Atribución (beneficiario vs. pagador, C12):** estructura narrativa, NO dos bloques idénticos de controles. Primero "Quién pagó", luego "Quién se benefició", y una **oración resumen viva**: "Pagó: Ana 100%. Beneficia a: Ana 50%, Luis 50%". Ofrecer **plantillas rápidas** ("Yo pagué / yo consumí", "Yo pagué / compartido 50-50", "Cuenta común / todos", "Mamá pagó / hijo consume"), originadas por recencia/frecuencia por categoría. Editor porcentual completo solo bajo demanda. `FilterChip`/`ButtonGroup` para atajos.
- **Validación y recuperación (C14):** validar **on-blur**, inline, específica y no modal. Si el reparto no suma 100%: "Faltan 20% por asignar" / "Excede 15%" como error text bajo el campo (sustituye supporting text, sin layout shift). Conservar lo escrito ante error. Tras guardar, **snackbar con "Deshacer"** (y opcional "Editar reparto") — el error más común es de prisa, no de sistema. Confirmar solo montos atípicamente grandes; no abusar de diálogos ("cry wolf").
- **Estilo de input (DESIGN.md):** fondo `surface_container_highest`, stroke inferior 2 dp `primary` al enfocar, esquinas superiores 28 dp.
- **CTA:** "Registrar" con énfasis expresivo y confirmación con resorte breve (`MotionScheme.standard()` para el flujo; `MotionScheme.expressive()` reservado a la micro-celebración de guardado, C6/C7).
- **Persistencia:** borrador a `rememberSaveable`/`SavedStateHandle` para sobrevivir plegado (C13). Interceptar dismissal accidental si el formulario está "sucio".

---

## 7. Restricciones y entregables

**Restricciones:**
- UI en español, montos en MXN (`$1,234.56`, separador localizado, alineación a la derecha, abreviación k/M para magnitudes grandes, negativos con signo/paréntesis y símbolo explícito).
- Construible en Compose Material 3 (`MaterialExpressiveTheme`, `NavigationSuiteScaffold`, `SupportingPaneScaffold`/`PaneExpansionState`, `ModalBottomSheet`, `FilterChip`, `OutlinedTextField`, `rememberSaveable`, `currentWindowAdaptiveInfo`, `FoldingFeature`). Glass/gradient OK.
- Respetar No-Line, radios 28 dp/pill, tonal layering, Roboto Flex selectivo.
- Mantener TODOS los campos de datos (no elimines funcionalidad; reorganiza).
- Adaptativo por requisito (API 37): no bloquear orientación ni asumir un solo layout.

**Adjuntar al prompt de diseño (lista exacta):**
1. **Este brief** (`ui_reference/REDESIGN_BRIEF.md`).
2. **`brief_dashboard.png`** y **`brief_capture.png`** (estado "antes").
3. **`ui_reference/veridian_ledger/DESIGN.md`** (sistema de diseño completo "The Architectural Ledger").

**Entregables a solicitar (3):**
1. **Dashboard expandido** (Fold interno, ~884 × 1104 dp): rail de iconos + Bento ~62/38 con salud financiera (KPI héroe + ritmo + barras por miembro con toggle) y transacciones recientes.
2. **Dashboard compacto** (plegado, externo): una columna, nav inferior, salud financiera colapsada + lista.
3. **Registro de gasto con la nueva UX de Categoría**: hoja acotada a ~640 dp en interno, monto + numpad, recientes/frecuentes + búsqueda + acordeón, atribución narrativa con resumen vivo.

Para cada uno: alta fidelidad, modo claro (oscuro si es posible), y **anotaciones de tokens/espaciados** (colores, radios, gutters) mapeables directo a Compose.

---

## 8. Vacíos e incertidumbre (transparencia)

- **Cifras dp/densidad exactas del Fold interno: [Inferencia].** ~884 × 1104 dp deriva de ~1768 × 2208 px / densidad ~2.0; las fuentes citan también 2152 × 2076 px. Confirmar en emulador real. La conclusión de layout (casi cuadrado) no depende del dato exacto.
- **Ancho de hoja acotado a 640 dp y proporción Bento: [Inferencia].** No hay plantilla oficial M3 para dashboards financieros en foldables casi cuadrados; las proporciones derivan de canonical layouts + codelabs + principios de contención. La proporción debe quedar arrastrable/persistida, no dogmática.
- **Modelo de doble atribución beneficiario/pagador en porcentajes: incertidumbre alta.** No hay estudio HCI que valide específicamente patrones UI para esta estructura. Conviene prueba de usabilidad propia con el hogar real. [Inferencia desde Monarch + CSCW.]
- **Numpad custom embebido y framing de "disponible": [solo 1 fuente / media-baja].** El numpad in-sheet (F5) y los umbrales de ritmo carecen de estudios controlados de consumo. El efecto de mostrar "disponible" sobre el gasto futuro no está cerrado (puede inducir licensing effects, F4): trátese como hipótesis de A/B, no dogma.
- **Estabilidad de `PaneExpansionState`/`PaneExpansionAnchor.Proportion`: [No verificado].** F5 reporta inconsistencias intermitentes en retención de estado entre versiones alpha; verificar en la versión de Compose que se despliegue (refuerza C7).
