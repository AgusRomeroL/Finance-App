# Reporte de auditoría — método de pago + beneficiarios (2026-07-08)

> Auditoría que re-cuestiona, contra los 27 estados de cuenta reales (mar–jun 2026) y el Excel, (1) el método de pago por gasto, (2) los pesos de beneficiario, y (3) las decisiones de clasificación previas. Documento PII-safe (solo últimos-4). Complementa `REPORTE_SEMILLA_2026-07.md` y `REPORTE_DESGLOSE_2026-07.md`. **Estado: pendiente del gate de confirmación de Agustín antes de regenerar el asset.**

## 0. Baseline exacto (golden actual, 834 gastos POSTED = $1,919,955.59)

**Consumo (beneficiario):** David $526,823 · Norma $403,985 · Normita $320,825 · Santiago $261,629 · Agustín $216,726 · Benjamín $189,968.
**Pagador:** Norma $1,852,544 · Benjamín $67,413.
**Método de pago:** Efectivo **833** · Klar **1**.

---

## 1. Método de pago — evidencia manda (hallazgo que corrige la premisa)

**La premisa "833/834 efectivo es irreal" es solo PARCIALMENTE cierta.** Los dos pagos de tarjeta más grandes del seed son **efectivo comprobado**, no un default:
- **Walmart** ($135k en el seed): el estado dice literal *"Su Pago en Wal Mart Gracias - **En Efectivo**" $7,500* los 3 meses (= monto del seed). **Efectivo correcto.**
- **DiDi tarjeta** ($63k): *"Pagar en OXXO"* $6,500/$6,700/$4,000. OXXO = efectivo. **Efectivo correcto** (la porción de *préstamo* DiDi ~$1,028/mes sí sale por BBVA).

Aun así, **~261 gastos SÍ tienen un método real** verificable en los estados de débito. Fuente de verdad: las salidas de BBVA (…1806), Banamex MiCuenta (…2298) y BanCoppel (…7380).

### 1.1 Cambios propuestos (de `efectivo`/`klar` → método real)

| Concepto | Veces | Método propuesto | Confianza | Evidencia |
|---|--:|---|---|---|
| Banamex Clásica | 18 | `banamex_deb` | **Alta** | Banamex MiCuenta "PAGO … A TB …5426" $2,762/$2,750/$2,970 |
| Mercado Pago | 13 | `bbva` | **Alta** | BBVA "MERPAGO*MERCADOPAGOMEN" $4,316/$1,595/$1,372 |
| Internet | 18 | `bbva` | **Alta** | BBVA "PAGO MI TELMEX" $899 |
| Netflix | 18 | `banamex_cc` | **Alta** | Banamex Clásica "NETFLIX" $329 (rota con Klar) |
| Prime | 18 | `bbva` | **Alta** | BBVA "STRIPE *AMAZONPRIMESUB" $99 |
| HBO (Max) | 19 | `mercado_pago` | **Alta** | Mercado Pago "Pago suscripción Max" $167.30 |
| Suscripción Nivel 6 (Meli+) | 19 | `mercado_pago` | **Alta** | Mercado Pago "Meli+" $99 |
| Google | 10 | `klar` | **Alta** | Klar "Google One" $39 |
| Coursera | 6 | `banamex_deb` | **Alta** | Banamex MiCuenta "PAYPAL *HELPCOURSER" $288 |
| Sears | 18 | `banamex_deb` | Media | Banamex MiCuenta / BanCoppel "SEARS POR INTERNET" $700/$1,368 |
| Klar (pago) | 2 | `banamex_deb` | Media | Banamex MiCuenta "A KLAR Pago tarjeta" $2,000/$1,900 (hoy wallet=klar, incoherente: se paga Klar DESDE débito) |
| Kigo | 10 | `banamex_deb` | Media | Banamex MiCuenta "OPENPAY*KIGO" ≈$104/mes |
| Teléfono Norma/David/Normita/Santi | 74 | `bbva` | Media | BBVA "SPEI … Movistar" $3,108 (paquete de las 4 líneas, no separable) |

**Resultado:** distribución nueva ≈ Efectivo ~573 · BBVA ~123 · Banamex Débito ~54 · Mercado Pago ~38 · Banamex Clásica ~18 · Klar ~10 · Google→Klar 10. **Ya NO 833/834 efectivo.**

### 1.2 Se quedan en `efectivo`
- **Efectivo comprobado / correcto:** Walmart, DiDi (tarjeta), y los sobres reales (Comida, Despensa, Limpieza, Gasolinas, Diversión, Comida Gatas, colegiaturas, mesadas, Psicóloga, Mary, Hawaiano, Caja Chica).
- **Sin evidencia (Baja — quedan efectivo pero podrían no serlo):** Liverpool, Coppel, Buró, Mercado Libre MSI, Préstamo Omar, Agua, Electricidad, Hipoteca, Mueble, Changan, Cochecito, Reparación coche, Contador, Benji, Bernardo, Google Nest, Spotify, YouTube, Teléfono Benji, **Adobe** (el seed lo modela $249/mes pero el único "Adobe" en estados es $40/mes en Klar — suscripción distinta).

### 1.3 Extrapolación a meses sin estado (ene-2025→feb-2026, jul–ago-2026)
Los recurrentes con comercio fijo conservan el mismo método bajando a **Media**; los one-offs quedan efectivo. Advertencia: BanCoppel no tiene wallet key en el seed, así que sus pagos caen al débito alterno del mes o a efectivo.

---

## 2. Beneficiarios — reponderación híbrida + correcciones factuales

### 2.1 Base seed (golden) — reponderación híbrida del pool de consumibles
- **Fijos colectivos (se quedan 17%×6):** Hipoteca, Internet, Agua, Electricidad, Google Nest, Buró, y el servicio de deuda `LOANS.*`. Pool ≈ $171,669 (+ deuda).
- **Consumibles variables (se reponderan):** Comida, Despensa, Limpieza, Diversión, Comida Gatas, streaming (Netflix/Prime/HBO/YouTube/Spotify/Meli+). Pool = **$415,433.50** (hoy $69,239 c/u equitativo).

**Vector propuesto (a confirmar en el gate)** — base: carga universitaria (Normita IBERO×2 + cursos/campamentos; David escuela + Amazon), presencia en casa, y que Agustín/Benjamín consumen menos del hogar (Agustín independiente con ingreso propio; Benjamín con gastos propios). El **software NO es de Agustín** (corrección de abajo), así que Agustín queda sin consumo personal identificado.

| Miembro | Peso propuesto | $ del pool | vs equitativo |
|---|--:|--:|--:|
| Normita | 20% | $83,087 | +$13,848 |
| David | 20% | $83,087 | +$13,848 |
| Santiago | 18% | $74,778 | +$5,539 |
| Norma | 17% | $70,624 | +$1,385 |
| Agustín | 13% | $54,006 | −$15,233 |
| Benjamín | 12% | $49,852 | −$19,387 |

**Pesos globales resultantes (aprox):** David $540,671 · Norma $405,370 · Normita $334,673 · Santiago $267,168 · Agustín $201,493 · Benjamín $170,581. (Mueve en la dirección de la hipótesis sin tocar hipoteca/servicios.)

### 2.2 Corrección factual: software/dev → Norma (NO Agustín) — el gold estaba MAL
El gold clasificó GitHub/Microsoft/Adobe/Coursera como `agustin`. Regla real: post-oct-2025 → **Norma**. Todos los estados son mar–jun 2026 → **Norma**. **10 movimientos, 20 ediciones** (gold + `seed_statements.json`):

| Comercio | Monto | Fecha | Gold | seed_statements |
|---|--:|---|---|---|
| Coursera | $288.00 | 03-13 | Klar-03:30 | :1025 |
| Adobe | $40.00 | 03-16 | Klar-03:33 | :1042 |
| Adobe | $40.00 | 04-16 | Klar-04:43 | :1310 |
| Adobe | $40.00 | 05-16 | Klar-05:41 | :1488 |
| GitHub | $186.66 | 04-09 | Walmart-04:28 | :2516 |
| Microsoft | $224.99 | 04-09 | Walmart-04:29 | :2529 |
| Microsoft | $224.99 | 05-08 | Walmart-05:22 | :2702 |
| GitHub | $181.86 | 05-09 | Walmart-05:23 | :2715 |
| Microsoft | $224.99 | 06-08 | Walmart-06:24 | :2914 |
| GitHub | $184.07 | 06-09 | Walmart-06:25 | :2927 |

(Flag: **Google CR $59** en Banamex Clásica, hoy `agustin` — ¿también → Norma, o servicio familiar? CONFIRMAR.)

### 2.3 GitHub neteado — solo jun $184.07 es real
GitHub abr $186.66 + may $181.86 fueron **reembolsados** por aclaración en jun (Walmart-06 pagosAbonos). `seed_statements.json` importa los 3 cargos → **sobre-cuenta $368.52**. Recomendación: dejar solo el cargo de junio ($184.07); quitar abr+may. (Microsoft no tiene reembolso — sus 3 meses quedan.)

### 2.4 Mesada $3,500 — el seed YA es correcto (no requiere cambio)
Verificado: "Normita, David y Agus" reparte a Agustín/David/Normita (excluye Santiago), y la línea **separada "Santi"** ($468/quincena ≈ $500) ES la mesada de Santiago. Los $1,000×3 + $500 ya viven en **dos conceptos distintos**; **no hay doble conteo ni Santiago ausente**. *Matiz:* el promedio histórico del Excel corre en ~$1,140/kid (no $1,000 exactos) porque los montos varían por quincena. **Decisión a confirmar:** ¿dejar como está (recomendado, fiel al Excel) o forzar el nominal $1,000/$500?

### 2.5 Amazon / compras genéricas → David (pendiente export)
20 líneas (~$7,830) de Amazon/Temu/Mercado Libre hoy "hogar equitativo". David tiene compras propias (cremas). **No se reasignan aún** — el carve-out por producto llega con el export de Amazon/ML de Norma (ver catálogo). Se listan para ese segundo pase.

---

## 3. Costo del plan telefónico por persona

| Persona | Línea seed | $/mes seed | Real (estado) |
|---|---|--:|---|
| Norma | Teléfono Norma | $457.06 | parte del paquete |
| David | Teléfono David | $226.25 | parte del paquete |
| Normita | Teléfono Normita | $221.94 | parte del paquete |
| Santiago | Teléfono Santi | $207.28 | parte del paquete |
| Benjamín | Teléfono Benji | $760.00 | línea propia (paga él, sin estado) |
| Agustín | — | $0 | sin línea |

**Real:** el paquete conjunto de las 4 líneas (Norma/Normita/Santiago/David) es **Telefónica $850.95/mes** (Banamex Clásica) y **Movistar $825.95/mes** (Klar) — mismo carrier/monto, distinto mes/tarjeta → **mismo plan familiar**. El seed modela $1,112/mes para esas 4 líneas (sobrestima ~$260 vs el real ~$826–851).

**Acciones:** (a) Movistar $825.95 hoy beneficia a "los 6" → cambiar a los 4 (Norma/David/Normita/Santiago). **CONFIRMAR.** (b) Reparto del paquete: equitativo-4 ($212.74 c/u) vs. proporción del seed (Norma $349.55/David $173.05/Normita $169.76/Santi $158.54). **CONFIRMAR** cuál prefieres.

---

## 4. Auditoría adversarial de decisiones previas (a–e)

- **(a) "DEBT_PAYMENT / no itemizable" — CONFIRMADO (matiz correcto).** El pago agregado de tarjeta no es itemizable 1:1 (de $7,500 Walmart, ~$4,990 es interés), pero los estados sí traen compras clasificables (ya materializadas en seed v2). El veredicto aplica al *pago agregado*, no a las compras.
- **(b) Clasificación del gold — 1 ERROR sistemático + dudas.** Error: software/dev → Agustín (corregido a Norma, §2.2). Dudas por resolver (§5).
- **(c) Anti-doble-conteo MATCHED_SEED — CONFIRMADO.** Los casados (Netflix $329, Prime $99, HBO/Max $167.30, Coursera $288, Google $39) coinciden en monto exacto con el seed; sin falsos positivos detectados. La lógica evita duplicar suscripciones ya sembradas.
- **(d) 6 categorías CUSTOM — recomendación de remapeo:**
  - `CUSTOM.ESTACIONAMIENTO` → **TRANSPORTATION** (Kigo Parkimovi es transporte).
  - `CUSTOM.EXAMENES` → **ESCUELA.<hijo>** (CENEVAL es escolar).
  - `CUSTOM.INTERESES` → **LOANS.<tarjeta>** (interés/IVA es servicio de deuda; ya alimenta el widget de intereses).
  - `CUSTOM.SOFTWARE`, `CUSTOM.COMPRAS_ONLINE`, `CUSTOM.ROPA` → **se quedan** (no hay nativa equivalente; opcional colgarlas de OTHER/PERSONAL_CARE). CONFIRMAR.
- **(e) Decisiones puntuales:**
  - **PayClip "SERV AGUSTIN R" $949.46:** los `_dudas` lo marcaban como posible *ingreso* de Agustín, pero ya está **decidido por Agustín como gasto de agustin** (`_ASSET_REPORT.md`). Se deja. (El export SAT CFDI emitidas lo confirmaría.)
  - **MP → Benjamín $4,000 = mesada/drawdown:** confirmado (drawdown de la línea MP que pasa a Benjamín). Se deja.
  - **DiDi "diferimientos especiales" = interés/traspaso de deuda:** confirmado (capital $4,561 + interés $300 + IVA $48, resumen no itemizado). Correcto como movimiento de deuda, no compra nueva.

---

## 5. Dudas de beneficiario a confirmar (con Agustín)

| Ítem | Monto | Hoy | Propuesta | Acción |
|---|--:|---|---|---|
| CENEVAL | $3,245 | agustin+santiago (inválido) | **Santiago** (estudiante inscrito) — o Agustín | CONFIRMAR uno |
| Ropa Universitarios (Sears ×3) | $3,109 | david | David (default) — o Santiago/Agustín | CONFIRMAR |
| CirculoDerm (dermatólogo) | $1,730 | los 6 | **1 paciente** (NO 6-way) — ¿quién? | CONFIRMAR |
| MerPago*WIM (recurrente) | $249×2 | los 6 | suscripción personal — ¿de quién? | CONFIRMAR |
| Kigo Parkimovi (8×) | ~$194 | norma | Norma (conductor default) | OK (menor) |
| Movistar $825.95 | — | los 6 | los 4 del plan | CONFIRMAR |
| Google CR | $59 | agustin | ¿Norma o familiar? | CONFIRMAR |

---

## 6. Decisiones para el gate

1. **Método de pago:** aplicar las asignaciones Alta+Media (§1.1, ~261 gastos); dejar el resto en efectivo (§1.2). ¿OK?
2. **Beneficiarios base:** aplicar el vector de consumibles (§2.1) — ¿confirmas 20/20/18/17/13/12 o ajustas? La mesada NO cambia (§2.4).
3. **Capa de estados:** aplicar software→Norma (§2.2) + GitHub netting (§2.3) + Movistar→4 (§3). ¿OK?
4. **Dudas (§5):** resolver las CONFIRMAR.
5. **Secuencia del asset:** ¿regenerar el asset **ya** con esto (correcciones + método + reponderación), y hacer un **segundo pase** cuando lleguen los exports de Amazon/ML (carve-out de David + dudas)? ¿O esperar a los exports para regenerar una sola vez?

---

## 7. Resultado aplicado (2026-07-08, gate confirmado por Agustín)

**Decisiones del gate:** método de pago = Alta+Media; consumibles = **sin reponderar** (solo correcciones factuales); secuencia = **regenerar ahora + 2º pase con exports**.

### Base seed (golden re-promovido)
- `attribution_rules.json`: 18 reglas con `override_payment_method` nuevo (evidencia §1.1).
- ETL: mecanismo `beneficiary_weights` (reparto desigual, hoy inerte — listo para el 2º pase) + override `BUDGET_TODAY` para regeneración **reproducible**.
- **Regenerado con `BUDGET_TODAY=2026-07-07`** (epoch del golden) → asset idéntico al golden **salvo método de pago**: 834 gastos, **beneficiarios/categorías/montos IDÉNTICOS** (0 diferencias), **222 gastos** cambiaron de método.
- Distribución nueva: Efectivo **612** · BBVA 113 · Banamex Débito 49 · Mercado Pago 34 · Banamex Clásica 17 · Klar 9 (antes 833/834 efectivo).
- Invariantes OK (`user_version=1`, FK limpio, bps=10000/rol). **Golden promovido**, `check_seed_integrity` → verde (`8e8a6a77…321e`).
- **Reproducibilidad:** regenerar SIEMPRE con `BUDGET_TODAY=2026-07-07` (si no, el status por fecha deriva del día del corrido y el asset difiere del golden).

### Capa de estados (`seed_statements.json` v2)
- **Software→Norma:** 10 movimientos (GitHub/Microsoft/Adobe/Coursera) `agustin`→`norma`.
- **Movistar→4:** $825.95 de "los 6" → norma/david/normita/santiago.
- **GitHub netting:** removidos los 2 cargos reembolsados (abr $186.66 + may $181.86); queda solo jun $184.07. Total movimientos 129→127.

### Pendiente (2º pase, requiere exports de Norma)
- Carve-out de las compras Amazon de David (§2.5), dudas §5 (CENEVAL, CirculoDerm, ropa universitarios, WIM, Google CR), y el gold clasificado + prompt NVIDIA con la regla software→Norma. La feature de ingesta generalizada (workstream E) habilita estos exports.
