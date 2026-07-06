# Briefs de logo — Finance-App (app + Wear)

Análisis de identidad y 3 conceptos listos para generar con IA. Elige uno (o mezcla) y genera el asset; la integración técnica (adaptive icon, monocromo, Wear) la hace el paquete C2 cuando entregues el archivo.

## Identidad de la app (de dónde salen los conceptos)

- **Qué es:** presupuesto familiar **quincenal** — el ciclo de 15 días es el corazón del producto (quincenas Q1/Q2, "disponible para gastar").
- **Lenguaje visual existente:** "The Architectural Ledger" — capas tonales, tipografía Google Sans Flex, sin ruido decorativo; motion expresivo M3 (resortes).
- **Color:** verde contable `#016E3E` / `#006C44` como color semilla; la app usa **Material You** (el sistema retiñe la UI), así que el ícono debe funcionar armonizado a cualquier paleta y tener **variante monocroma** (themed icon Android 13+).
- **Quién la usa:** una familia (varios miembros, atribución de quién paga/quién consume) + reloj Wear OS.
- **Ícono actual (placeholder):** círculo con símbolo de moneda sobre fondo índigo — genérico, no comunica ni quincena ni familia.

## Requisitos técnicos (aplican a cualquier concepto)

1. **Adaptive icon:** foreground y background SEPARABLES (el foreground debe vivir solo, centrado en el 66% interior del lienzo — Android recorta el resto en círculo/squircle según el launcher).
2. **Legible a 48dp**: nada de detalles finos; máximo 2-3 formas.
3. **Variante monocroma** (una sola silueta sólida) para themed icons.
4. **Variante circular** para Wear OS (se ve en una pantalla redonda de ~48mm).
5. Vector o PNG ≥1024×1024 con fondo transparente para el foreground.
6. Evitar: símbolos de dólar genéricos, gráficas de barras cliché, monedas apiladas.

---

## Concepto 1 — "La Quincena" (recomendado)

**Idea:** un círculo/anillo partido exactamente en dos mitades (Q1/Q2 — las dos quincenas del mes), con la mitad izquierda llena y la derecha delineada. Leído de otra forma: un ledger abierto visto desde arriba. Es único (ningún logo de finanzas usa el ciclo quincenal), geométrico (encaja con "Architectural Ledger") y funciona perfecto en monocromo y en Wear (forma circular nativa).

**Prompt sugerido para IA:**
> Minimal flat vector app icon: a perfect circle split vertically into two halves representing a 15-day budget cycle; left half solid deep green (#016E3E), right half outlined stroke only; subtle rounded gap between halves; centered on transparent background; geometric, architectural, Material Design 3 style; no text, no gradients, no shadows; clean single-weight strokes.

## Concepto 2 — "Techo compartido"

**Idea:** un techo/gable minimalista (la casa = hogar/familia) cuya viga inferior es una barra de progreso parcialmente llena (el presupuesto de la quincena). Dos trazos + una barra: extremadamente legible a 48dp, y la barra da la lectura financiera sin recurrir a $.

**Prompt sugerido para IA:**
> Minimal flat vector app icon: an abstract house roof formed by two clean strokes in deep green (#016E3E), and beneath it a horizontal rounded progress bar filled about two thirds; geometric, architectural, generous negative space; Material Design 3 aesthetic; transparent background; no text, no gradients, no dollar signs.

## Concepto 3 — "Hoja de balance viva"

**Idea:** tres capas horizontales apiladas con esquinas redondeadas (las capas tonales del design system; también: ingresos/gastos/disponible), donde la capa superior se desplaza ligeramente a la derecha — sugiere movimiento (el motion expresivo de la app) y un libro contable de hojas. Muy "arquitectónico".

**Prompt sugerido para IA:**
> Minimal flat vector app icon: three stacked horizontal rounded rectangles in shades of green (#016E3E 100%, 70%, 40% opacity), the top one slightly offset to the right suggesting motion; architectural layered composition; Material Design 3 style; transparent background; no text, no gradients, no coins.

---

## Qué entregar cuando generes

- 1 imagen del **foreground** (transparente, el símbolo solo).
- Color/tono del **background** (puede ser un plano `#016E3E` o un tono neutro; sin detalle).
- Si la IA no da la variante monocroma limpia, la derivamos nosotros de la silueta.

Con eso, C2 integra: `mipmap-*` adaptive icon en `:app` y `:wear`, `ic_launcher_monochrome`, y verificación en el launcher del Fold y en el Pixel Watch.
