# Color dinámico (Material You) en una app de finanzas con Compose + Material 3 Expressive

> Investigación profunda. Fecha: 2026-06-27. Alcance: Android 12+ (color dinámico), Material 3 / Material 3 Expressive, Jetpack Compose. App objetivo: presupuesto familiar quincenal (MXN, español), marca verde `#016E3E` y colores semánticos financieros fijos.

---

## 1. Resumen ejecutivo

El color dinámico de Android (Material You, Android 12+) deriva un esquema completo de un único *source color* extraído del wallpaper (o de contenido vía *content-based*), lo proyecta al espacio perceptual **HCT** (hue, chroma, tone) y genera cinco paletas tonales clave —primary, secondary, tertiary, neutral y neutral-variant— de 13 tonos cada una, de las que Material asigna los **roles de color** (`primary`, `onPrimary`, `primaryContainer`, `surface`, etc.). En Compose esto se consume con `dynamicLightColorScheme(context)` / `dynamicDarkColorScheme(context)` tras un guard `Build.VERSION.SDK_INT >= S`, con *fallback* a un esquema estático sembrado desde la marca. Material 3 Expressive (Android 16 / Compose material3 1.5.0-alpha tras `@ExperimentalMaterial3ExpressiveApi`; estable es 1.4.0 desde sep-2025) **no rompe** este modelo: añade `MaterialExpressiveTheme`, un nuevo *spec* de color (`SPEC_2025`) y el estilo de paleta `Expressive`, pero el esquema sigue siendo un `ColorScheme`.

La tensión marca-vs-dinámico se resuelve con una decisión por roles, no global. La recomendación es: **ceder los roles de acento (`primary/secondary/tertiary` y sus `container`) y de superficie al sistema cuando hay color dinámico, pero anclar la semántica financiera (ingreso/gasto/alerta) como colores personalizados fijos** que se **armonizan** (hue-shift en HCT hacia el `primary` dinámico vía `MaterialColors.harmonizeWithPrimary` / `harmonize()`) lo suficiente para integrarse sin perder su categoría perceptual (rojo sigue rojo, verde sigue verde). El verde de marca `#016E3E` se conserva como *seed* del esquema estático (degradación en Android 11-) y, opcionalmente, como un rol *fixed* o un color custom anclado cuando el usuario activa dynamic color, aceptando que en ese modo la marca cede protagonismo cromático.

Accesibilidad: las paletas tonales garantizan contraste por diseño (separación tonal mínima ~60), pero la semántica **no debe depender solo del color** (forma, posición, iconografía, signo +/-), y debe respetarse el slider de contraste del sistema (Android 14+, `UiModeManager.getContrast()` / `addContrastChangeListener`, o `SPEC_2025` con `contrastLevel`).

---

## 2. Mecánica de dynamic color y roles en Material 3 y Material 3 Expressive

**Extracción del source color.** A nivel sistema (AOSP), `com.android.systemui.monet.ColorScheme#getSeedColors` cuantifica el wallpaper y propone colores candidatos; si ninguno es apto, el sistema cae a `0xFF1B6EF3` (azul) por defecto. Desde Android 13 existen estilos de tema que transforman ese *seed* en hasta 65 colores dinámicos finales: `TONAL_SPOT` (defecto, vibrancia media), `VIBRANT`, `EXPRESSIVE` (vibrante con emparejamientos de acento únicos), `SPRITZ` (baja vibrancia), y `RAINBOW`/`FRUIT_SALAD` (recomendados para temas **estáticos**, no extracción de wallpaper). Fuente: AOSP *Dynamic color* (source.android.com).

**Espacio HCT.** Todos los colores se definen por tres ejes: **Hue** (0-360, la familia perceptual), **Chroma** (saturación, 0 a ~120) y **Tone** (0 negro – 100 blanco). HCT combina la exactitud perceptual de CAM16 con el control de luminancia de L\*, lo que permite garantizar contraste fijando diferencias de *tone*. Fuente: m3.material.io *how the system works*.

**Paletas tonales y roles.** El *source color* se traduce a cinco colores clave —primary, secondary, tertiary, **neutral** y **neutral-variant**—; cada uno genera una paleta tonal de 13 incrementos. A nivel sistema se exponen como recursos `system_accent1_{0..1000}`, `system_accent2_*`, `system_accent3_*`, `system_neutral1_*`, `system_neutral2_*` (índices 0,10,50,100…1000). Los **roles** se eligen de esas paletas: los roles de acento (`primary`, `primaryContainer`, `onPrimary`…) salen de las paletas accent; los roles de superficie (`surface`, `surfaceVariant`, `background`, `outline`) salen de las paletas neutral/neutral-variant. En Compose, `ColorScheme` contiene los cinco colores clave que mapean a paletas de 13 tonos usadas por los componentes M3. Fuentes: developer.android.com *Material 3 in Compose*; m3.material.io *roles*.

**Roles `fixed`.** `primaryFixed`, `secondaryFixed`, `tertiaryFixed` (y sus `*FixedDim` más intensos, y `onPrimaryFixed`/`onPrimaryFixedVariant`) son colores de relleno que **mantienen el mismo tono en claro y oscuro** —a diferencia de los `*Container`, que cambian de tono entre modos—. Útiles cuando se quiere un color estable entre modos. Caveat oficial: como no se adaptan al modo, **pueden causar problemas de contraste**; evitarlos donde el contraste sea crítico y siempre emparejarlos con su `on*Fixed`. Fuente: m3.material.io *roles*.

**Material 3 Expressive (M3E).** Es una expansión de M3 (theming, componentes, motion, tipografía) que **mantiene** Material You / dynamic color. Lanzó en 2025 (Pixel primero; Android 16 / Wear OS 6). En color, refina el motor: separación más clara entre primary/secondary/tertiary y nuevos tokens. En Compose: API `MaterialExpressiveTheme` (alfa, `@ExperimentalMaterial3ExpressiveApi`, material3 1.5.0-alpha; estable 1.4.0 de sep-2025) y un nuevo *spec* de color `ColorSpec.SpecVersion.SPEC_2025` con `PaletteStyle.Expressive`. Fuentes: blog.google *M3 Expressive launch*; composables.com; developer.android.com *compose-material3 releases*.

---

## 3. Conciliación de marca fija con color dinámico

La decisión correcta **no es binaria** (todo dinámico vs todo fijo) sino **por rol**. Las opciones realistas:

| Estrategia | Qué roles ceden al sistema | Qué se ancla | Costo en identidad de marca | Consistencia/UX | Veredicto para esta app |
|---|---|---|---|---|---|
| **A. Dynamic puro** | Todos los acentos y superficies (`primary`, `secondary`, `tertiary`, `surface`, `outline`) | Nada (la marca desaparece como color) | Alto: el verde `#016E3E` no se ve nunca cuando el usuario tiene wallpaper | Máxima coherencia con el SO; muy "Google-like" | No recomendado solo: pierde marca |
| **B. Marca fija total (sin dynamic)** | Ninguno | Esquema completo sembrado desde `#016E3E` | Nulo: marca siempre presente | Alta interna, nula con el SO; ignora Material You | Es el estado actual; conservar como *fallback* |
| **C. Primary anclado + resto dinámico** | `secondary`, `tertiary`, superficies | `primary`/`primaryContainer` = verde de marca | Bajo: el verde manda | Riesgo de choque: superficies dinámicas frías chocando con verde fijo | Viable pero frágil sin armonizar |
| **D. Roles `fixed` para la marca** | Acentos y superficies dinámicos | `primaryFixed`/`primaryFixedDim` = verde estable entre modos | Bajo-medio: marca presente en superficies específicas (badges, chips de marca) | Buena, salvo contraste de los `fixed` | Recomendada para *acentos de marca puntuales* |
| **E. Dynamic + colores custom de marca armonizados** ⭐ | Todos los roles M3 estándar | Colores **personalizados** (marca + semánticos) que se **armonizan** hacia el `primary` dinámico | Medio: marca como acento secundario coherente | Alta: integra SO y marca sin choque | **Recomendada como modo dynamic** |

**Recomendación combinada (E sobre B como fallback).** Cuando hay dynamic color: usar `dynamic*ColorScheme()` para todos los roles M3 y exponer el verde de marca y los semánticos como **colores personalizados extendidos** (fuera de `ColorScheme`, en un `Extended`/`CustomColors` propio) **armonizados** con el `primary` del wallpaper. Cuando no hay dynamic (Android 11- o usuario lo desactiva): el esquema estático completo se siembra desde `#016E3E` (estrategia B), garantizando que la marca siempre exista. El equipo de Chrome documentó exactamente este patrón "fásico": ceder roles al sistema pero **proteger colores con significado** (en su caso colores de publisher e incognito), reconciliando temas vía atributos en lugar de colores hardcoded. Fuente: Android Developers Blog *Implementing Dynamic Color: Lessons from Chrome*.

---

## 4. Preservación de la semántica financiera y armonización

La regla de oro fintech es categórica: **verde = ingreso/positivo, rojo = gasto/negativo/alerta** son convenciones universales (compra/venta, ganancia/pérdida); "cualquier revolución en esa codificación resultará desastrosa" (síntesis de fuentes de diseño fintech). Por tanto estos colores **no deben** derivarse del wallpaper.

**Armonización (color harmonization).** Material ofrece `MaterialColors.harmonizeWithPrimary(context, color)` y `MaterialColors.harmonize(colorToHarmonize, colorToHarmonizeTowards)`. El algoritmo opera en HCT: examina el *hue* de ambos colores y **rota el hue del color custom hacia el del primary** una cantidad acotada, buscando "un hue armonioso que no altere las cualidades subyacentes del color". El objetivo es que rojo/verde/ámbar semánticos no se vean "fuera de lugar" frente a un esquema dinámico, **conservando su familia perceptual**. En vistas también existe `HarmonizedColors`/`HarmonizedColorsOptions.createMaterialDefaults()` para armonizar recursos a nivel `Context`. En KMP/Compose, `MaterialKolor` ofrece una extensión `harmonize()` equivalente. Fuentes: codelab *Basic Color Harmonization*; material-components-android *Color.md*; jordond/MaterialKolor.

**Caveat crítico de la armonización.** El propio codelab advierte que la rotación de *hue* debe preservar la percepción: **si la rotación excede límites seguros, el color pierde reconocibilidad** (p. ej. rojo virando a naranja, o saliendo de su familia). El algoritmo equilibra armonía y preservación calculando desplazamientos "cálido/frío" dentro de la misma familia, pero **no es infalible**: con un wallpaper de *hue* muy distante (p. ej. primary cian/azul), el rojo de "gasto" armonizado puede acercarse a magenta y el verde de "ingreso" a un verde-azulado que reduce el contraste rojo-verde justo cuando más importa distinguirlos. Mitigación: armonizar con un **tope de rotación bajo** (o no armonizar el par ingreso/gasto entre sí), y/o mantener los semánticos **literalmente fijos** (sin armonizar) si la app prioriza claridad sobre cohesión. La literatura de harmonization general confirma el principio: "no se puede recolorear arbitrariamente objetos con significado sin perder la comunicación" (analogía del *stop sign* rojo).

**Patrón recomendado:** definir `incomeColor`/`expenseColor`/`warningColor` (claro y oscuro) como colores custom; en modo dynamic, armonizarlos con rotación leve hacia `primary`; en modo estático, usarlos crudos (`0F5A2E`, `BA1A1A`, `8B5A00`). Para el rol `error` de Material, considerar **dejar el `error` dinámico estándar** (es rojo por convención del sistema) o sobreescribirlo con el rojo de marca para que "gasto" y "error" no diverjan.

---

## 5. Accesibilidad, niveles de contraste e independencia del color

**Contraste garantizado por paletas.** Las paletas tonales aseguran accesibilidad "por defecto": el principio de Chrome es una **separación de luminancia mínima ~60 de *tone*** entre un color y su `on*` para cumplir estándares. Como HCT controla *tone* directamente, los pares `primary`/`onPrimary`, `surface`/`onSurface`, etc., mantienen contraste sobre **cualquier** wallpaper.

**Niveles de contraste del usuario.** Android 14 (API 34) añadió un slider de contraste (Ajustes → Accesibilidad → Color y movimiento) con **Standard / Medium / High**. SystemUI lee 0 / 0.5 / 1 y regenera el esquema dinámico con tonos más contrastados. Para apps que hacen su propio render: `UiModeManager.getContrast()` devuelve un `float` en `[-1, 1]` (0 = defecto), y `addContrastChangeListener(executor, listener)` (con `UiModeManager.ContrastChangeListener`) notifica cambios. Generadores de tema deben respetar `contrastLevel` (típicamente -1.0 a 1.0); `SPEC_2025`/`MaterialKolor` aceptan un `contrastLevel`. En vistas existe la `ColorContrast` API con `ColorContrastOptions.Builder()` para overlays medium/high. Fuentes: developer.android.com *UiModeManager*, *ColorContrast*; AndroidPolice (Android 14 DP2 high-contrast).

**Independencia respecto al color (daltonismo).** El contraste numérico no basta: rojo y verde son la confusión más común (deuteranopia/protanopia). La semántica financiera debe ser **redundante**: signo `+`/`−` y formato del monto, iconografía (flecha arriba/abajo, ícono de categoría), posición/columna, etiqueta textual ("Ingreso"/"Gasto"), y forma del badge. El color es refuerzo, no portador único del significado. (Principio WCAG 1.4.1 "Use of Color"; síntesis de fuentes de diseño fintech sobre accesibilidad de dashboards.)

---

## 6. Modos claro y oscuro y reacción en runtime

- **Claro/oscuro:** `dynamicLightColorScheme(context)` y `dynamicDarkColorScheme(context)` producen variantes coherentes del mismo *seed*. El selector debe combinar `isSystemInDarkTheme()` con la disponibilidad de dynamic color (ver receta §8). Los roles `*Container` cambian de tono entre modos; los `*Fixed` **no** (por eso sirven para marca estable, con cuidado de contraste).
- **Cambio de wallpaper / tema en runtime:** el sistema regenera `system_accent*`/`system_neutral*`. En Compose, como `dynamic*ColorScheme(LocalContext.current)` se evalúa en (re)composición y el cambio de wallpaper implica nueva configuración/recreación, el esquema se recalcula; basta con leer el contexto vivo y no cachear el `ColorScheme` en un `val` de nivel superior. Para vistas, los overlays `ThemeOverlay.Material3.DynamicColors.*` o `DynamicColors.applyToActivitiesIfAvailable(app)` reaplican.
- **Cambio de nivel de contraste en runtime:** registrar `addContrastChangeListener` y, al dispararse, recomponer/regenerar el esquema (si se usa `MaterialKolor`/`SPEC_2025` con `contrastLevel`, recalcular pasando `getContrast()`); recordar `removeContrastChangeListener` en `onDispose`. [Inferencia]: con el *rendering* nativo de Material el sistema ya entrega tonos ajustados, así que la escucha manual solo es necesaria para color custom propio (semánticos/marca) que la app dibuja por su cuenta —así lo indica la doc de `UiModeManager` ("solo si tu app hace su propio render").

---

## 7. Degradación sin color dinámico (Android 11 o anterior)

Dynamic color **no existe** antes de Android 12. La degradación correcta: **sembrar un esquema estático completo desde el verde de marca `#016E3E`**. Opciones:

- **Material Theme Builder** (material.io/material-theme-builder): introduce `#016E3E` como *source*/primary, exporta `Color.kt` + `Theme.kt` con `lightColorScheme(...)`/`darkColorScheme(...)`. Es lo que ya hace la app hoy.
- **material-color-utilities** (repo oficial Google): genera el esquema desde un *seed* en código (mismo motor HCT que el sistema).
- **MaterialKolor** (jordond/MaterialKolor, KMP): `dynamicColorScheme(seedColor, isDark, style = PaletteStyle.*, contrastLevel = ...)` produce un `ColorScheme` Compose desde una semilla, con `SPEC_2021`/`SPEC_2025` y `DynamicMaterialTheme` (con `animate`). Es un *port* de material-color-utilities; útil para que el esquema sembrado tenga la **misma estética** que el dinámico, solo que anclado al verde. Recomendado para unificar el look entre Android 11- y 12+.

[Inferencia]: usar `PaletteStyle.TonalSpot` (o `Expressive` si se adopta M3E) sobre `#016E3E` da el esquema estático más cercano al que el sistema generaría, reduciendo el salto visual entre dispositivos con y sin dynamic color.

---

## 8. Receta de implementación en Jetpack Compose

```kotlin
// 1) Guard de disponibilidad + selección de esquema
@Composable
fun FinanceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,        // bandera de usuario (preferencia)
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val canDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        dynamicColor && canDynamic && darkTheme  -> dynamicDarkColorScheme(context)
        dynamicColor && canDynamic && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme  -> DarkColorScheme   // sembrado estático desde #016E3E (fallback)
        else       -> LightColorScheme
    }

    // 2) Colores custom (marca + semánticos), armonizados al primary cuando hay dynamic
    val primaryArgb = colorScheme.primary.toArgb()
    val income  = harmonizeIf(canDynamic && dynamicColor, IncomeSeed,  primaryArgb, context)
    val expense = harmonizeIf(canDynamic && dynamicColor, ExpenseSeed, primaryArgb, context)
    val warning = harmonizeIf(canDynamic && dynamicColor, WarningSeed, primaryArgb, context)
    val custom  = FinanceColors(income = income, expense = expense, warning = warning)

    CompositionLocalProvider(LocalFinanceColors provides custom) {
        MaterialTheme(colorScheme = colorScheme, content = content)
        // o, con M3 Expressive (alfa):
        // MaterialExpressiveTheme(colorScheme = colorScheme, content = content)
    }
}

// helper: rota hue del color custom hacia el primary dinámico, acotado
fun harmonizeIf(on: Boolean, color: Color, towardArgb: Int, ctx: Context): Color =
    if (!on) color
    else Color(MaterialColors.harmonize(color.toArgb(), towardArgb))
```

Notas de mapeo a APIs reales:
- `dynamicLightColorScheme` / `dynamicDarkColorScheme` viven en `androidx.compose.material3` (Android 12+; en versiones inferiores no existen, de ahí el guard `>= S`).
- **No cachear** `colorScheme` en `val` de archivo: leerlo en composición (vía `LocalContext.current`) permite reaccionar a cambios de wallpaper/modo.
- **`MaterialExpressiveTheme`** (firma: `colorScheme?`, `motionScheme?`, `shapes?`, `typography?`, `content`) requiere `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` y material3 1.5.0-alpha; con estable 1.4.0 usar `MaterialTheme`. Migrar cuando M3E sea estable.
- **Colores custom fuera de `ColorScheme`**: `ColorScheme` no tiene ranuras para "ingreso/gasto"; exponerlos con un `data class FinanceColors` + `CompositionLocal` (patrón estándar de *extended colors*).
- **Armonización**: `MaterialColors.harmonize(int, int)` / `harmonizeWithPrimary(context, int)` desde `com.google.android.material:material`; o `Color.harmonize(...)` de MaterialKolor en código puro Compose/KMP.
- **Contraste runtime** (solo si dibujas color propio): `(getSystemService<UiModeManager>())?.getContrast()` (API 34+) y `addContrastChangeListener(executor, listener)`; regenerar custom colors con ese `contrastLevel`.
- **Bandera de usuario**: persistir `dynamicColor: Boolean` (DataStore) y exponer toggle en Ajustes, como hacen las apps de Google.

---

## 9. Contraevidencia y tensiones (mandato adversarial)

1. **Pérdida de identidad de marca.** En modo dynamic puro (estrategia A), el verde `#016E3E` desaparece: la app se ve "como cualquier app de Google", indistinguible. Para un producto financiero donde la confianza se ancla parcialmente en consistencia visual de marca, esto es un costo real. Muchos productos financieros **prefieren marca fija** precisamente por reconocimiento y confianza (los bancos tradicionales fijan azul/navy deliberadamente).
2. **Wallpapers que comprometen legibilidad de cifras.** Aunque las paletas garantizan contraste `primary`/`onPrimary`, un wallpaper de *hue* extremo puede producir un `primary` chillón o un `tertiary` que, aplicado a montos o gráficos, **reduce la legibilidad de números financieros** o el contraste rojo-verde clave. El sistema protege los pares estándar, no necesariamente tus visualizaciones custom.
3. **Casos donde la marca fija gana.** La propia guía de Chrome **excluyó** del dynamic color zonas con semántica fuerte (incognito) y colores de terceros (publisher). Análogamente, pantallas críticas de finanzas (saldo, alerta de sobregiro) pueden justificar color fijo aunque el resto sea dinámico.
4. **Límites de la armonización para semántica.** La armonización es *hue-shift* acotado, pero no garantiza preservar **distinción entre dos semánticos** simultáneamente: rotar ingreso(verde) y gasto(rojo) ambos hacia un primary azulado puede **acercarlos**, debilitando el contraste rojo↔verde que es justo el portador del significado. La literatura confirma que objetos con color significativo no admiten recoloreo arbitrario. Mitigación: topes de rotación, o no armonizar el par crítico, o redundancia no-cromática (§5).
5. **Costo de mantenimiento.** Soportar dynamic + estático + claro/oscuro + 3 niveles de contraste + colores custom armonizados multiplica los caminos a probar. Para un desarrollador único, el riesgo de regresiones de contraste es alto; conviene *snapshot tests* o al menos verificación manual sobre wallpapers extremos.

**Conclusión adversarial:** la estrategia E (dynamic + custom armonizados con fallback B) es la mejor relación valor/riesgo, pero **debe** acompañarse de (a) semánticos con redundancia no-cromática, (b) topes de armonización conservadores, y (c) un toggle de usuario que permita volver a marca fija. No adoptar dynamic puro.

---

## 10. Vacíos e incertidumbre

- **[No verificado]** Detalle exacto de la firma/parámetros de `dynamicColorScheme` con `contrastLevel` y `ColorSpec.SpecVersion.SPEC_2025` en `androidx.compose.material3` estable: a jun-2026 vive en alfa (1.5.0-alpha) y la doc canónica no se pudo extraer (m3.material.io es JS-only; varias fetch devolvieron solo el título). Verificar contra `developer.android.com/jetpack/androidx/releases/compose-material3` al implementar.
- **[Inferencia]** El comportamiento de recomposición ante cambio de wallpaper se dedujo del modelo de `LocalContext`/recreación de configuración, no de doc explícita. Confirmar en dispositivo.
- **[Inferencia]** El tope óptimo de rotación de hue para no degradar el par rojo-verde no está cuantificado en la doc oficial; el algoritmo de `harmonize` no expone el ángulo. Requiere prueba empírica con wallpapers de hue lejano.
- **[Especulación]** Que `PaletteStyle.Expressive`/`SPEC_2025` mejore o empeore el contraste de los semánticos custom frente a `TonalSpot` es desconocido sin medición.
- **Cobertura:** no se pudo leer el cuerpo completo de m3.material.io (roles, contrast, advanced); se reconstruyó vía developer.android.com, material-components-android, AOSP, codelabs y blogs oficiales, que son primarios y concordantes. ~22 fuentes consultadas; convergencia alcanzada antes del disyuntor de 50.

---

## 11. Bibliografía (URLs canónicas)

**Oficiales Google / AOSP / Material**
- Cómo funciona el sistema de color (HCT, paletas, roles): https://m3.material.io/styles/color/system/how-the-system-works
- Roles de color (incl. fixed): https://m3.material.io/styles/color/roles
- Color dinámico (user-generated source): https://m3.material.io/styles/color/dynamic/user-generated-source
- Customizaciones avanzadas de color: https://m3.material.io/styles/color/advanced/define-new-colors
- Material 3 en Compose (ColorScheme, dynamic*, código): https://developer.android.com/develop/ui/compose/designsystems/material3
- Personalizar color (Views, DynamicColors): https://developer.android.com/develop/ui/views/theming/dynamic-colors
- Dynamic color (AOSP, extracción wallpaper, estilos): https://source.android.com/docs/core/display/dynamic-color
- Implementing Dynamic Color: Lessons from Chrome (blog): https://android-developers.googleblog.com/2022/05/implementing-dynamic-color-lessons-from.html
- Lanzamiento Material 3 Expressive: https://blog.google/products-and-platforms/platforms/android/material-3-expressive-android-wearos-launch/
- Compose Material 3 (releases / 1.4 estable, 1.5-alpha): https://developer.android.com/jetpack/androidx/releases/compose-material3
- Codelab armonización de color (harmonizeWithPrimary, HCT): https://codelabs.developers.google.com/harmonize-color-android-views
- material-components-android Color.md (harmonize, DynamicColors, content-based): https://github.com/material-components/material-components-android/blob/master/docs/theming/Color.md
- UiModeManager (getContrast, addContrastChangeListener): https://developer.android.com/reference/android/app/UiModeManager
- UiModeManager.ContrastChangeListener: https://developer.android.com/reference/android/app/UiModeManager.ContrastChangeListener
- ColorContrast (API, overlays medium/high): https://developer.android.com/reference/com/google/android/material/color/ColorContrast

**Comunidad / herramientas / casos**
- MaterialKolor (seed → ColorScheme, KMP, SPEC_2025, contrastLevel): https://github.com/jordond/MaterialKolor
- MaterialExpressiveTheme (firma/params): https://composables.com/material3/materialexpressivetheme
- M3 colorScheme explained (softAai): https://softaai.com/material-3-colorscheme-explained-how-dynamic-color-works/
- M3 Expressive deep dive (Android Authority): https://www.androidauthority.com/google-material-3-expressive-features-changes-availability-supported-devices-3556392/
- Android 14 DP2 high-contrast + dynamic theming (AndroidPolice): https://www.androidpolice.com/android-14-dp2-high-contrast-text-material-you/
- What's new Compose Material3 1.4.0 (sep-2025): https://medium.com/@basit.shaabaz/jetpack-compose-material-3-whats-new-in-1-4-0-sept-24-2025-d85f828ec8a5

**Diseño fintech / color (secundarias, contexto adversarial)**
- Psicología del color en apps financieras: https://windmill.digital/psychology-of-color-in-financial-app-design/
- Guía de colores fintech: https://www.patrickhuijs.com/blog/fintech-brand-colors-guide
- Cómo elegir colores para fintech: https://www.progress.com/blogs/how-choose-right-colors-fintech

**Fundamento de harmonization (límite semántico)**
- Color Harmonization, Deharmonization and Balancing in AR (MDPI): https://www.mdpi.com/2076-3417/11/9/3915
