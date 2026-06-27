# Investigación de UX fintech para presupuesto familiar quincenal

Fecha de ejecución: 2026-06-27. Metodología: descomposición en siete sub-preguntas, búsqueda web dirigida con priorización de Nielsen Norman Group (NN/g), Baymard Institute e investigación revisada por pares (CHI, CSCW, IEEE VIS, JASA, JCP), lectura directa de fuentes primarias, mandato adversarial y etiquetado explícito de incertidumbre. Fuentes consultadas con evidencia retenida: ~50 (ver bibliografía, sección 11). Convergencia alcanzada antes del disyuntor de 70 fuentes.

Calibración de confianza usada en todo el documento: **alta** (NN/g empírico, Baymard con muestra grande, estudios seminales o meta-análisis revisados por pares); **media** (guía de practicante reputado, estudio único, transferencia desde dominio adyacente); **baja** (anecdótico, teardown sin método). Etiquetas: [Inferencia] = deducción razonada del autor; [Especulación] = hipótesis plausible sin respaldo directo; [No verificado] = afirmación cuya fuente no pudo confirmarse.

---

## 1. Resumen ejecutivo

La evidencia converge en una tesis central: para un hogar mexicano que registra gasto quincenal en MXN, el valor de la app no está en mostrar *más* datos ni en capturar *más rápido* a cualquier costo, sino en reducir la carga cognitiva mientras se preserva la exactitud y la confianza. La credibilidad en finanzas se decide desproporcionadamente por la calidad visual de superficie —el efecto del diseño sobre la credibilidad percibida es más fuerte en la categoría financiera que en cualquier otra (Stanford WCP)— y la confianza es jerárquica: hay que demostrar competencia antes de pedir datos o compromisos sensibles (NN/g). En tono, la confiabilidad explica la mayor parte de la deseabilidad de marca; la simpatía aporta poco y el humor erosiona credibilidad en dinero.

En visualización, la jerarquía perceptual de Cleveland y McGill (1984), replicada en CHI 2010, dicta preferir codificaciones de posición y longitud (barras) sobre ángulo (dona/pastel) y área (treemap). Para "gasto por miembro" la recomendación es barras (agrupadas para comparar, apiladas solo si el total es el mensaje y los miembros son pocos); la dona solo tolera el caso de una sola cifra protagonista. Para varianza vs. presupuesto, el bullet graph de Few y la cascada son superiores al medidor decorativo.

En captura, el patrón ganador es un solo campo de monto alineado a la derecha con teclado decimal nativo y teclas grandes, defaults por recencia y frecuencia visibles y sobreescribibles, y divulgación progresiva del resto. En selección de categoría, el anti-patrón no es "demasiadas categorías" sino la rejilla plana sin orden ni jerarquía; la solución combinada es búsqueda con autocompletado + fila de recientes/frecuentes + agrupación jerárquica para la cola larga.

El mandato adversarial arroja tensiones reales: captura ultrarrápida contra exactitud (sesgo de automatización, compromiso velocidad-exactitud), alertas proactivas que disparan el "efecto avestruz", y modelos de finanzas compartidas que generan fricción social. El diseño debe optimizar *registros correctos y revisados por unidad de esfuerzo*, no toques absolutos.

---

## 2. Confianza y claridad en fintech (principios accionables)

**La credibilidad se juzga primero por el diseño visual, y este efecto es máximo en finanzas.** En el Stanford Web Credibility Project, ~46% de los consumidores evaluaron la credibilidad de un sitio en parte por el atractivo del diseño general (layout, tipografía, color), y el efecto fue más fuerte en la categoría Finanzas (~55%) que en salud o noticias. **[Inferencia]** Para esta app significa que el pulido visual del dashboard y de la hoja de captura no es cosmético: es un componente directo de la confianza percibida en las cifras (Fogg et al., Stanford WCP).

**La confianza es jerárquica; gánala antes de pedir datos o compromisos sensibles.** NN/g describe una "jerarquía de confianza" de cinco niveles tipo Maslow: la información financiera sensible y los compromisos de relación continua están en la cima y requieren satisfacer primero los niveles de credibilidad básica. Saltarse niveles —muros de datos o de login antes de demostrar competencia— provoca abandono (NN/g, *Hierarchy of Trust*).

**La confiabilidad pesa mucho más que la simpatía, y el humor daña en dinero.** En el estudio de tono de voz de NN/g, la confiabilidad explicó ~52% de la variabilidad en deseabilidad de marca, frente a ~8% de la simpatía. Un tono conversacional cálido superó al formal en confianza, pero la copia "graciosa" en seguros ganó simpatía y *perdió* credibilidad por resultar "demasiado familiar". Principio: la tranquilidad debe venir de la calidez y la claridad, no de bromas (NN/g, *Tone of Voice*).

**Prevención y recuperación de errores en montos:**
- Dispara el teclado numérico/decimal correcto y desactiva autocorrección/autocapitalización; el teclado numérico agranda las teclas ~500% y reduce erratas (Baymard, *Touch Keyboard Types*). Usa `inputmode="decimal"` en lugar de `type="number"` para moneda (MDN).
- Usa máscaras de entrada que autoformatean y validación en línea, **pero solo al completar el campo (on blur), nunca mientras el usuario teclea**: la validación prematura ("eager") se percibe como que el formulario "regaña", interrumpe el tecleo y marca como error entradas válidas, aumentando la ansiedad en vez de reducirla (Baymard, *Inline Form Validation*).
- Conserva siempre el valor capturado tras un error de validación; borrar el campo obliga a reescribir y dispara abandono (Baymard).
- Reserva los diálogos de confirmación para acciones de consecuencia real (montos grandes, irreversibles), hazlos específicos (muestra monto y destino) y no preselecciones la opción peligrosa; si abusas de confirmaciones genéricas, los usuarios las ignoran por hábito ("cry wolf") (NN/g, *Confirmation Dialogs*).

**Comunicación que reduce ansiedad financiera.** El encuadre positivo de las cifras reduce el riesgo percibido y mejora la comprensión frente al encuadre negativo (Yuan et al., 2023, contexto de visualización de seguridad; transferencia [Inferencia]). El rastreo de gasto induce culpa con facilidad; conviene encuadrar el sobregasto como "una señal que requiere atención, no un fracaso", tal como el propio soporte de YNAB matiza su indicador rojo.

**Carga cognitiva y accesibilidad de cifras.** Tres palancas de NN/g: evitar el desorden visual, apoyarse en modelos mentales existentes y descargar tareas del usuario (p. ej., autocategorizar para que no calcule). El lenguaje llano beneficia incluso a expertos. La jerarquía visual (tamaño, color, posición, proximidad) debe llevar el ojo a la cifra clave primero; el escaneo se concentra arriba e izquierda (patrón F).

**Contraevidencia sobre transparencia (no es monótonamente buena).** Zerilli et al. (2022, *Patterns*) muestran una relación en U invertida: el exceso de transparencia causa sobrecarga de información, exceso de confianza y anclaje en el primer dato mostrado. Y la "paradoja de la transparencia" (OBHDP, 2025) documenta que divulgar de más puede *reducir* la confianza por suscitar dudas de legitimidad. Implicación: divulga cifras y razones a una profundidad *moderada e interpretable*, no en crudo total.

---

## 3. Visualización de datos financieros

Fundamento perceptual transversal: las personas decodifican magnitudes con mayor exactitud por **posición sobre una escala común**, luego **longitud**, luego **ángulo/pendiente**, luego **área**, y por último **color** (Cleveland y McGill, 1984, JASA; replicado por Heer y Bostock, CHI 2010). NN/g lo resume: longitud y posición 2D son los atributos preatentivos que mapean bien a cantidad; área y ángulo "comunican cantidad pobremente".

### Matriz: pregunta financiera → gráfico recomendado → justificación

| Pregunta financiera | Gráfico recomendado | Justificación (y qué evitar) |
|---|---|---|
| ¿Cuánto gastó cada miembro? (comparar miembros) | Barras horizontales agrupadas, ordenadas de mayor a menor | Comparación directa de longitud sobre línea base común (Cleveland-McGill). Evita treemap: el área se compara mal y sirve a exploración, no a dashboards accionables (NN/g). |
| ¿Cómo se reparte el total del hogar entre miembros? (parte-todo + total) | Barra apilada única **solo** si el total es el mensaje y miembros ≤ ~5 | El segmento base se lee bien; los superiores "flotan" y se comparan mal. NN/g advierte que las apiladas están entre los gráficos de mayor tasa de error y sugiere "3 barras separadas". |
| ¿Está concentrado el gasto en una categoría? | Barras horizontales ordenadas descendente | Para *comparar* concentración, barra > dona. La dona fuerza juicios de ángulo/área (lento e impreciso) y colapsa más allá de ~5-6 categorías. |
| "Una sola cifra protagonista" (p. ej., top categoría = X%) | Dona única con la cifra al centro | Tolerable solo como glanceable de una proporción gruesa con ≤2-3 porciones; no para comparar varias (Depict Data Studio, contraevidencia acotada). |
| ¿Gasté de más o de menos vs. presupuesto? (KPI titular) | Bullet graph (barra de medida + marcador de meta + rangos cualitativos) | Reemplazo de Few para medidores; denso y comparativo en poco espacio. Rangos como intensidades de un mismo tono (seguro para daltónicos). |
| ¿Qué partidas explican la desviación vs. presupuesto? | Cascada (waterfall) | Descompone contribuciones secuenciales +/- (FT Visual Vocabulary). Alternativa más simple para muchas partidas: barras divergentes (sobre/bajo presupuesto). |
| ¿Voy a buen ritmo dentro de la quincena? | Barra de progreso con etiqueta numérica + umbrales | Glance simple; etiqueta el valor y el % porque algunos usuarios vuelven a leer el número en vez de estimar la fracción de la barra. |
| Tres KPIs (presupuesto, gastado, disponible) | Tarjetas KPI apiladas en móvil | Cifra primaria grande, etiqueta y contexto (meta/brecha/tendencia) pequeños; KPI primario primero/arriba-izquierda; máx. 4-6 KPIs. |

**Legibilidad y formato de montos (MXN):**
- Formato sensible a la configuración regional con separador de miles (es-MX: punto de miles, coma decimal según convención local; usar un formateador estándar tipo `Intl.NumberFormat`, no codificar a mano) (Unicode CLDR).
- Alinear cifras a la derecha y abreviar valores grandes (p. ej., "1.2k", "3.4M") para que las magnitudes se escaneen alineadas (Workday Design).
- Negativos con paréntesis o signo menos claro, **no por color solo**: ~8% de los hombres tienen deficiencia de visión cromática; nunca dependas de rojo/verde aislado, acompaña con signo, flecha o etiqueta y cumple contraste WCAG (Tableau; NN/g).
- Mostrar símbolo/código de moneda explícito reduce errores.

**Contraevidencia / "más detalle estorba":** las barras apiladas son de las de mayor error (NN/g); la dona dificulta la comparación de porciones por su codificación angular (Cleveland-McGill); el treemap se vuelve ilegible con muchos segmentos pequeños (FT). Y aun una barra/bullet bien hecha no siempre es intuitiva: algunos usuarios revierten a leer el número, por lo que **etiquetar la cifra junto al gráfico** es obligatorio, no opcional [Inferencia desde FasterCapital, confianza baja-media].

---

## 4. Captura rápida de transacciones (patrones y defaults)

**Campo de monto.** Un solo campo (no dividir el monto en entero+decimales; dividir una entidad lógica empeora el móvil y rompe el modelo mental — Baymard), alineado a la derecha, con teclado decimal nativo vía `inputmode="decimal"` y `type="text"` (no `type="number"`, que muestra spinners y maltrata separadores locales — MDN). Teclas grandes: objetivo táctil mínimo ~1 cm × 1 cm (NN/g, *Touch Targets*). Formatea on blur, no en cada pulsación (evita saltos de cursor); guarda el valor crudo y muestra el formateado.

**Patrón "centavos primero" / calculadora.** Rellenar dígitos desde la derecha insertando el decimal automáticamente (teclear "950" → $9.50) conviene a captura frecuente de montos pequeños, pero confunde con cantidades grandes redondas; ofrécelo con cuidado o como opción [confianza baja-media, fuentes de practicante]. Para el layout del teclado, sigue la convención nativa de la plataforma (calculadora 7-8-9 arriba vs. teléfono 1-2-3 arriba es históricamente arbitrario; respeta la memoria motriz del usuario).

**Defaults inteligentes (recencia/frecuencia).** NN/g recomienda explícitamente buenos defaults a partir del historial y de "valores usados con frecuencia". Aplícalo a categoría, fuente de pago y atribución por miembro: prerrellena con lo más reciente/frecuente, **pero mantén cada default visible y sobreescribible en un toque**.

**Estructura de la hoja inferior.** Las bottom sheets sirven para interacciones rápidas y contextuales, no para flujos complejos de varios pasos (NN/g). Divulgación progresiva: monto + categoría arriba; concepto, fecha alternativa, notas y atribución detallada detrás de un "Más". Provee cierre explícito (botón X + gesto Atrás) y evita el descarte accidental que perdería una transacción a medio capturar. No coloques los controles primarios en el borde inferior extremo: NN/g sostiene que la zona más alcanzable es el **centro**, no el fondo (matiz frente a la literatura clásica de "thumb zone"). Marca campos requeridos vs. opcionales y minimiza los opcionales: cada pregunta extra aumenta el esfuerzo percibido.

**Tensión velocidad-exactitud (adversarial, ver también §9).** La autocategorización ML parte de ~70-80% de exactitud y solo llega a ~95% tras decenas de correcciones del usuario; el compromiso velocidad-exactitud es una propiedad inescapable de la decisión (Heitz, 2014). Meta: *menos toques hasta un registro correcto y revisado*, no menos toques absolutos.

---

## 5. Selección de categoría con catálogo grande

**Diagnóstico del anti-patrón actual.** Una rejilla plana de 20+ pills es subóptima sobre todo por el **costo de escaneo visual serial y la ausencia de orden/jerarquía**, no principalmente por "sobrecarga de elección". NN/g: una vez que las opciones superan ~15, conviene una lista estructurada (listbox), no controles autónomos dispersos (chips), apropiados solo para conjuntos ≤5. Una rejilla sin orden inherente fuerza escaneo serial con sesgo de fijación central/patrón F, de modo que las opciones en bordes y abajo se sub-atienden.

### Matriz comparativa de patrones

| Patrón | Cómo funciona | Cuándo conviene | Limitaciones / contraevidencia |
|---|---|---|---|
| Búsqueda con autocompletado | Campo de texto que filtra y muestra 4-8 sugerencias | Catálogo grande **y** objetivo incierto; usuario que conoce el nombre | Cuesta teclado; en móvil el teclado tapa media pantalla y las sugerencias quedan exprimidas (Baymard). Más lento si el set es pequeño o el objetivo ya es visible. |
| Acordeón / agrupación jerárquica | Grupos colapsables (grupo → categoría) | Cola larga de categorías poco usadas; reduce desorden y scroll en móvil | El contenido oculto se olvida/pasa por alto y agrega toques; baja descubribilidad de categorías que el usuario no sabe que existen (NN/g, contraevidencia). |
| Recientes / frecuentes (fila superior) | Atajos a lo más usado, **aditivos** sobre la lista principal | Captura repetitiva: la mayoría de gastos caen en pocas categorías habituales | Si se *reordena* la lista por frecuencia, rompe la memoria espacial/muscular y ralentiza a expertos (Findlater y McGrenere). Mantener como sección añadida, no reorganizar. |

**Recomendación combinada (alta confianza).** No "menos categorías" (débilmente respaldado), sino mejor estructura para encontrar una entre muchas: fila de recientes/frecuentes arriba + búsqueda con autocompletado que devuelva un shortlist corto + agrupación jerárquica/divulgación progresiva para la cola larga. Resalta la porción predictiva de cada sugerencia (no el término tecleado) para acelerar el escaneo (Baymard; NN/g).

**Controversia de "paradoja de la elección" (adversarial).** El estudio seminal de las mermeladas (Iyengar y Lepper, 2000) reportó ~30% de conversión con 6 opciones vs ~3% con 24. Pero el meta-análisis de Scheibehenne et al. (2010, ~50 estudios) halló un efecto medio ≈ 0 con alta heterogeneidad y señales de sesgo de publicación: la sobrecarga **no replica como efecto principal**. La reconciliación de Chernev, Böckenholt y Goodman (2015, 99 observaciones, N=7,202) muestra que la sobrecarga es real y fuerte (d ≈ 0.5-0.6) solo bajo cuatro moderadores: alta complejidad del conjunto, alta dificultad de la tarea, alta incertidumbre de preferencia y meta de minimizar esfuerzo. **[Inferencia]** Para un usuario habitual que ya sabe "Despensa", la incertidumbre de preferencia es baja, así que el costo real es de escaneo/interacción (no parálisis); para un usuario nuevo, los cuatro moderadores suben.

**Leyes seminales mal aplicadas (adversarial).** La Ley de Hick mide el tiempo de decisión entre alternativas *conocidas, equiprobables y equimapeadas* en condiciones de laboratorio; con familiaridad, jerarquía y probabilidades desiguales, no predice que 20 ítems sean 20× más lentos. Y el "7±2" de Miller es un mito mal aplicado a contar opciones en pantalla: trata de fragmentos en memoria de trabajo (revisada a ~4±1 por Cowan, 2001), no de cuántas opciones se pueden *mostrar*. El argumento contra la rejilla es el escaneo y la falta de jerarquía, no un límite de memoria.

---

## 6. Presupuesto por periodos y comunicación del disponible para gastar

**Prioriza una sola primitiva calmada: "disponible para gastar" / safe-to-spend.** El patrón canónico (Simple Bank) calcula el saldo disponible en tiempo real restando metas reservadas y facturas programadas, descargando la aritmética mental de "qué me queda". YNAB reencuadra el presupuesto como asignación prospectiva ("dale un trabajo a cada peso"), siendo "Disponible" por categoría la primitiva central de la UI. Una alternativa que esquiva el encuadre ansiógeno de saldo bajo es el "Age of Money" (gastar dinero ganado hace ≥30 días). Para tu quincena, la primitiva natural es el **disponible restante de la quincena** y, opcionalmente, un promedio diario implícito hasta el corte (1-15 / 16-fin).

**Indicadores de ritmo / burn-rate.** El encuadre de ritmo ("¿es sostenible este gasto hasta el corte?") es patrón establecido; implementaciones usan umbrales graduados (p. ej., ~70% atención, ~90% alerta) y barras de progreso por porcentaje [confianza media, contexto de presupuesto de proyecto, transferible]. Codifica el estado sobre/bajo presupuesto con forma + posición, no con rojo solo (NN/g).

**Alertas proactivas sin alarmar.** Taxonomía de NN/g: Reactivas (acción inmediata, canal prominente), Proactivas (visibles, no disruptivas), Optimización (mínimamente intrusivas). Un empujón de ritmo presupuestal es Proactivo/Optimización, **no** Reactivo. Si todas las alertas lucen igual, el usuario no prioriza y o las ignora o se angustia. Las notificaciones de optimización demasiado prominentes "se sienten manipuladoras" y la gente las desactiva ("cry wolf"). Recomendación: limita la frecuencia, filtra por tipo y **ata cada alerta a una acción protectora** (no a la mera mala noticia).

---

## 7. Finanzas compartidas y atribución por miembros

**Modelo mental "mío / suyo / nuestro" como patrón líder.** Monarch operacionaliza la distinción propiedad-vs-consumo con "Shared Views": cada miembro etiqueta cuentas y transacciones y filtra finanzas individuales vs compartidas, con login propio y notificaciones personalizadas pero un dashboard/presupuesto común —autonomía con vista conjunta, sin fusión forzada de cuentas. También permite asignar transacciones a un miembro para revisión, codificando el reparto del "trabajo de dinero" en la UI. **[Inferencia]** Tu modelo de doble dimensión (beneficiario = quién consume; pagador = quién desembolsa, en porcentajes) es una generalización más fina de este "mío/suyo/nuestro": es defendible, pero su novedad exige hacer ambas dimensiones explícitas y legibles, porque el modelo mental por defecto del usuario suele colapsar "quién paga" con "quién usa".

**Fundamento HCI/CSCW: el dinero es relacional, emocional y prospectivo.** Kaye et al. (CHI 2014, *Money Talks*) hallaron que el dinero es profundamente emocional, que la gente usa procesos idiosincráticos y que planifica contra "la naturaleza desconocida del futuro" (motiva colchones/márgenes). Snow, Roe, Vyas y Brereton (CSCW 2016) muestran que los sistemas financieros del hogar se desarrollan "con cuidadosa consideración de valores, relaciones y rutinas familiares": la gestión es trabajo coordinado del hogar, no tarea individual; el diseño debe encajar en rutinas existentes, no imponer un modelo normativo. Supriya Singh agrega que la cuenta conjunta expresa "confianza, unión, compromiso", pero el fondo común "enmascara cuestiones de poder y dependencia" —matiz adversarial: el encuadre de "unión" puede ocultar asimetrías de poder.

**Contraevidencia: la división de gastos genera fricción social.** Teardowns de Splitwise documentan que la "transparencia radical" por defecto (todos ven todo) crea incomodidad de privacidad; que "Simplify Debts" produce obligaciones socialmente confusas (te dice paga a alguien con quien nunca compartiste un gasto); que cualquier miembro puede editar/borrar gastos (riesgo de confianza); y que el supuesto de división igual falla en escenarios reales. El reparto no es solo aritmética: carga peso relacional. **[Inferencia]** Para tu hogar, prefiere transparencia configurable, evita el "neteo" de deudas que rompe el modelo "a quién le debo", y permite porcentajes flexibles (que tu modelo ya soporta) en vez de división rígida.

---

## 8. Recomendaciones accionables mapeadas a las dos pantallas

### Dashboard financiero
1. **Jerarquía de tres KPIs en tarjetas apiladas** (presupuesto, gastado, disponible): cifra primaria grande, etiqueta y contexto (brecha vs. meta, % de quincena transcurrida) pequeños; el "disponible para gastar" de la quincena es el KPI héroe, arriba/izquierda (NN/g KPI; safe-to-spend).
2. **Gasto por miembro con barras horizontales ordenadas**, no treemap; usa apilada única solo si quieres comunicar el total del hogar (Cleveland-McGill; NN/g).
3. **Sustituye cualquier dona comparativa por barras**; reserva la dona para una sola cifra protagonista al centro (concentración top = X%).
4. **Indicador de ritmo de quincena** como barra de progreso con etiqueta numérica + % y umbrales codificados por forma+posición, no por rojo solo (NN/g daltonismo).
5. **Para varianza vs. presupuesto**, un bullet graph titular y, en detalle, cascada o barras divergentes por categoría (Few; FT).
6. **Formato MXN consistente**: separador de miles localizado, alineación a la derecha, abreviación k/M, negativos con signo/paréntesis, símbolo explícito (CLDR; Workday).
7. **Recorta el chartjunk**: nada de 3D ni gradientes; el dashboard es de un vistazo y accionable (NN/g). Vigila que añadir paneles no reduzca la comprensión (ver §9).

### Hoja de captura rápida
8. **Un solo campo de monto** alineado a la derecha, teclado decimal nativo (`inputmode="decimal"`), teclas ≥1 cm, formateo on-blur, valor crudo almacenado (Baymard; MDN).
9. **Reemplaza la rejilla de 20+ pills** por: fila de recientes/frecuentes (aditiva) + búsqueda con autocompletado (4-8 sugerencias, porción predictiva resaltada) + grupos jerárquicos para la cola larga (NN/g listbox; Baymard autocomplete).
10. **Defaults por recencia/frecuencia** en categoría, fuente de pago y atribución por miembro, siempre **visibles y sobreescribibles en un toque**; nunca autoconfirmes una categoría que el usuario no vio (sesgo de automatización, §9).
11. **Divulgación progresiva**: monto + categoría arriba; concepto, atribución detallada beneficiario/pagador (%), fecha y notas detrás de "Más". Controles primarios fuera del borde inferior extremo (zona central más alcanzable, NN/g).
12. **Validación de monto solo on-blur**, conserva la entrada ante error, confirma solo montos atípicamente grandes (Baymard; NN/g).
13. **Dos dimensiones de atribución explícitas y separadas** (beneficiario vs. pagador) con etiquetas claras, para no colapsar "quién paga" con "quién consume" (Monarch; CSCW).

---

## 9. Contraevidencia y tensiones

- **Captura ultrarrápida vs. exactitud de categorización.** El sesgo de automatización lleva a aceptar valores prerellenados erróneos y dejar de verificar; la autocategorización ML solo alcanza ~95% tras decenas de correcciones; el compromiso velocidad-exactitud es estructural (Heitz, 2014). Además, hay una corriente deliberada (YNAB/Goodbudget) que trata la fricción de la entrada manual como *función*: registrar en el momento sostiene la conciencia de gasto. Tensión: optimiza registros correctos por esfuerzo, no toques absolutos; mantén defaults visibles y revisables.
- **Más datos en el dashboard pueden reducir comprensión.** Las barras apiladas figuran entre los gráficos de mayor error (NN/g); el exceso de transparencia produce sobrecarga y anclaje (Zerilli et al., 2022). Un panel más denso no es mejor: una sola señal clara de ritmo supera a múltiples métricas (NN/g).
- **La dona dificulta comparar.** Codificación angular/área, lenta e imprecisa (Cleveland-McGill; NN/g; Knaflic); colapsa con >5-6 categorías. Solo sobrevive como cifra única protagonista.
- **Alertas proactivas que contraproducen.** "Efecto avestruz": la gente evita revisar finanzas cuando sospecha malas noticias, y monitorea menos cuando tiene más deuda —justo quienes más necesitan la alerta se desentienden (Olafsson y Pagel, NBER 2017). Las alertas mal calibradas se sienten manipuladoras y se desactivan (NN/g). Mitigación: atar la alerta a una acción protectora, no a la mala noticia desnuda.
- **Límites de finanzas compartidas.** Transparencia total como default crea incomodidad; el neteo de deudas rompe el modelo "a quién le debo"; la división rígida falla; el rastreo granular puede dañar relaciones (teardowns Splitwise; Singh sobre poder oculto en el fondo común).
- **Choice overload no es ley universal.** Efecto medio ≈ 0 sin moderadores (Scheibehenne et al., 2010); real solo bajo cuatro condiciones (Chernev et al., 2015). Hick y Miller se aplican mal al caso de la rejilla.

---

## 10. Vacíos e incertidumbre

- **Falta evidencia directa sobre el modelo de doble atribución beneficiario/pagador en porcentajes.** No se halló estudio que valide específicamente separar "quién consume" de "quién paga" en repartos porcentuales; la recomendación se infiere de Monarch (mío/suyo/nuestro) y de la literatura CSCW. Confianza media; conviene prueba de usabilidad propia. [Inferencia]
- **Umbrales de ritmo (70%/90%) y la cifra "10 segundos" de safe-to-spend** provienen de fuentes de practicante o de dominio de proyecto, no de estudios de consumo validados. Confianza media-baja. [No verificado para consumo móvil]
- **El patrón "centavos primero"** carece de estudios controlados; evidencia de practicante. [Especulación sobre su efecto neto].
- **Transferencia de encuadre positivo** (Yuan et al., 2023) proviene de visualización de seguridad, no de finanzas personales; direccionalmente aplicable. [Inferencia]
- **Dos preprints arXiv con identificadores anómalos (futuros, `2603.*`)** surgidos en la búsqueda sobre propiedad/control de cuentas compartidas **no se citan** por no poder verificar autoría ni venue. [No verificado].
- **Cifras de "25-50% más rápido" con navegación facetada** (NN/g, ampliamente repetida) se tratan como direccionales, no exactas.

---

## 11. Bibliografía

**Nielsen Norman Group**
- Trustworthiness in Web Design: 4 Credibility Factors. https://www.nngroup.com/articles/trustworthy-design/
- Hierarchy of Trust: The 5 Experiential Levels of Website Commitment. https://www.nngroup.com/articles/commitment-levels/
- The Impact of Tone of Voice on Users' Brand Perception. https://www.nngroup.com/articles/tone-voice-users/
- Confirmation Dialogs Can Prevent User Errors (If Not Overused). https://www.nngroup.com/articles/confirmation-dialog/
- Dashboards: Making Charts and Graphs Easier to Understand. https://www.nngroup.com/articles/dashboards-preattentive/
- Choosing Chart Types: Consider Context. https://www.nngroup.com/articles/choosing-chart-types/
- A Checklist for Designing Mobile Input Fields. https://www.nngroup.com/articles/mobile-input-checklist/
- Design Guidelines for Input Steppers. https://www.nngroup.com/articles/input-steppers/
- 4 Principles to Reduce Cognitive Load in Forms. https://www.nngroup.com/articles/4-principles-reduce-cognitive-load/
- Placeholders in Form Fields Are Harmful. https://www.nngroup.com/videos/placeholders-form-fields/
- Bottom Sheets: Definition and UX Guidelines. https://www.nngroup.com/articles/bottom-sheet/
- Accidental Dismissal of Overlays. https://www.nngroup.com/articles/accidental-overlay-dismissal/
- Progressive Disclosure. https://www.nngroup.com/articles/progressive-disclosure/
- Marking Required Fields in Forms. https://www.nngroup.com/articles/required-fields/
- Touch Targets on Touchscreens. https://www.nngroup.com/articles/touch-target-size/
- Listboxes vs. Dropdown Lists. https://www.nngroup.com/articles/listbox-dropdown/
- Checkboxes vs. Radio Buttons. https://www.nngroup.com/articles/checkboxes-vs-radio-buttons/
- Accordions on Mobile / Complex Content. https://www.nngroup.com/articles/accordions-complex-content/
- 10 Usability Heuristics for User Interface Design. https://www.nngroup.com/articles/ten-usability-heuristics/
- F-Shaped Pattern For Reading Web Content. https://www.nngroup.com/articles/f-shaped-pattern-reading-web-content/
- Helping Users Make Decisions / Sludge in Decision-Making. https://www.nngroup.com/articles/sludge-decisions/
- Designing Useful Smart Home Notifications. https://www.nngroup.com/articles/smart-home-notifications/

**Baymard Institute**
- Touch Keyboard Types Cheat Sheet. https://baymard.com/labs/touch-keyboard-types
- Mobile Touch Keyboards. https://baymard.com/blog/mobile-touch-keyboards
- Usability Testing of Inline Form Validation. https://baymard.com/blog/inline-form-validation
- Input Fields (recommendations) / Input Masking. https://baymard.com/learn/input-fields ; https://baymard.com/blog/input-masking-form-field
- Avoid Splitting Single Input Entities. https://baymard.com/blog/mobile-form-usability-single-input-fields
- Autocomplete Design (Only 19% Get Everything Right). https://baymard.com/blog/autocomplete-design
- The State of Mobile E-Commerce Search and Navigation. https://baymard.com/blog/mobile-ecommerce-search-and-navigation
- Ecommerce Filter UI. https://baymard.com/learn/ecommerce-filter-ui
- Retain Data in Sensitive Fields after Validation Errors. https://baymard.com/blog/preserve-card-details-on-error

**Investigación revisada por pares y fuentes académicas**
- Cleveland, W.S. y McGill, R. (1984). Graphical Perception. *JASA* 79(387):531-554. DOI: 10.1080/01621459.1984.10478080
- Heer, J. y Bostock, M. (2010). Crowdsourcing Graphical Perception. *Proc. ACM CHI 2010*:203-212. DOI: 10.1145/1753326.1753357
- Iyengar, S. y Lepper, M. (2000). When Choice Is Demotivating. *J. Pers. Soc. Psychol.* 79(6):995-1006. DOI: 10.1037/0022-3514.79.6.995
- Scheibehenne, B., Greifeneder, R. y Todd, P. (2010). Can There Ever Be Too Many Options? *J. Consumer Research* 37(3). DOI: 10.1086/651235
- Chernev, A., Böckenholt, U. y Goodman, J. (2015). Choice Overload: A Conceptual Review and Meta-Analysis. *J. Consumer Psychology* 25(2):333-358. DOI: 10.1016/j.jcps.2014.08.002. https://chernev.com/wp-content/uploads/2017/02/ChoiceOverload_JCP_2015.pdf
- Kaye, J., McCuistion, M., Gulotta, R. y Shamma, D. (2014). Money Talks: Tracking Personal Finances. *Proc. ACM CHI 2014*. DOI: 10.1145/2556288.2556975
- Snow, S., Roe, P., Vyas, D. y Brereton, M. (2016). Social Organization of Household Finance. *Proc. ACM CSCW 2016*:1777-1789. DOI: 10.1145/2818048.2819937. https://eprints.soton.ac.uk/386427/
- Cowan, N. (2001). The magical number 4 in short-term memory. https://pmc.ncbi.nlm.nih.gov/articles/PMC4486516/
- Heitz, R. (2014). The speed-accuracy tradeoff. *Front. Neurosci.* https://pmc.ncbi.nlm.nih.gov/articles/PMC4052662/
- Zerilli, J. et al. (2022). How transparency modulates trust in AI. *Patterns*. DOI: 10.1016/j.patter.2022.100455. https://pmc.ncbi.nlm.nih.gov/articles/PMC9023880/
- Yuan et al. (2023). Framing effects on risk perception/comprehension. *IJERPH*. DOI: 10.3390/ijerph20043325
- Olafsson, A. y Pagel, M. (2017). The Ostrich in Us: Selective Attention to Financial Accounts. NBER WP 23945. DOI: 10.3386/w23945. https://www.nber.org/papers/w23945
- The Transparency Dilemma: How AI Disclosure Erodes Trust (2025). *OBHDP*. https://www.sciencedirect.com/science/article/pii/S0749597825000172
- Stanford Web Credibility Project (Fogg et al.). https://en.wikipedia.org/wiki/Stanford_Web_Credibility_Project

**Guías de visualización y practicantes reputados**
- Few, S. Bullet Graph Design Specification. https://www.perceptualedge.com/articles/misc/Bullet_Graph_Design_Spec.pdf
- Knaflic, C.N. (2015). Storytelling with Data. Wiley. ISBN 9781119002253
- Financial Times — Visual Vocabulary. https://github.com/Financial-Times/chart-doctor/blob/main/visual-vocabulary/README.md
- Unicode CLDR — Number and currency patterns. https://cldr.unicode.org/translation/number-currency-formats/number-and-currency-patterns
- Tableau — Examining Data Viz Rules: Don't Use Red/Green Together. https://www.tableau.com/blog/examining-data-viz-rules-dont-use-red-green-together
- Workday Design — The UX of Currency Display. https://medium.com/workday-design/the-ux-of-currency-display-whats-in-a-sign-6447cbc4fb88
- Depict Data Studio — When Pie Charts Are Okay. https://depictdatastudio.com/when-pie-charts-are-okay-seriously-guidelines-for-using-pie-and-donut-charts/
- MDN Web Docs — inputmode. https://developer.mozilla.org/en-US/docs/Web/HTML/Reference/Global_attributes/inputmode
- Laws of UX — Hick's Law. https://lawsofux.com/hicks-law/
- Centigrade — The Number Seven Is Not Magical. https://www.centigrade.de/en/blog/the-number-seven-is-not-magical-part-1/

**Teardowns y casos fintech**
- YNAB Method / Age of Money / Overspending. https://www.ynab.com/ynab-method ; https://www.ynab.com/blog/what-is-the-ideal-age-of-money ; https://support.ynab.com/en_us/overspending-in-ynab-a-guide-ryWoxEyi
- Simple Bank Safe-to-Spend (FinanceBuzz). https://financebuzz.com/simple-bank-review
- Monarch — For Couples / Households / Assign transactions. https://www.monarch.com/for-couples ; https://help.monarch.com/hc/en-us/articles/20926382202004-Monarch-for-Couples-and-Households ; https://www.monarch.com/blog/assign-transactions-to-a-household-member-for-review
- Splitwise — análisis de fricción social (teardowns). https://medium.com/@nidhibhat.1098/evaluating-splitwises-interface-and-its-influence-on-group-dynamics-f6f1f3ef1355
- UXDA — Top 20 Financial UX Do's and Don'ts. https://www.theuxda.com/blog/top-20-financial-ux-dos-and-donts-to-boost-customer-experience

---

*Nota de método: dos preprints con identificadores arXiv anómalos (`2603.*`) detectados en la búsqueda fueron descartados por no verificables. El texto completo de ACM (CHI/CSCW) no fue accesible a fetch automático (HTTP 403); las afirmaciones se sostienen sobre metadatos verificados (DBLP, ePrints Southampton) y DOIs canónicos.*
