# Reporte de desglose de estados de cuenta — 2026-07

> Registro de decisión sobre el **desglose de los conceptos agregados de tarjeta** a partir de los estados de cuenta reales de Norma (mar–jun 2026). Documento PII-safe: sin números de cuenta ni detalle de movimientos (esos viven, gitignored, en `Estados de Cuenta/_gold/`, `_arquetipo/` y `ANALISIS_ARQUETIPO.md`). Complementa `REPORTE_SEMILLA_2026-07.md`.

## Contexto

El seed modela cada pago de tarjeta como **un gasto agregado mensual** (concepto = nombre de la tarjeta, categoría `LOANS.*`, wallet Efectivo salvo Klar). El objetivo de la Tarea 1 era desglosar esos agregados en registros específicos (compras con beneficiario/categoría) usando los estados de cuenta reales, como estándar de oro para el pipeline NVIDIA.

Se recolectaron y analizaron **27 estados de 10 emisores** (mar–jun 2026), con extracción local de texto y gold hecho a mano verificado al centavo contra los totales impresos de cada estado.

## Hallazgo central: todas las tarjetas son abono a deuda (DEBT_PAYMENT), no compras itemizables

El análisis por tarjeta (cruzando el pago agregado del seed contra el estado real) arrojó un veredicto uniforme:

| Tarjeta (seed) | Naturaleza real | Por qué NO es itemizable 1:1 |
|---|---|---|
| Walmart | Revolvente ~$82k @73% (CAT 102%) | De cada pago ~66% es interés; compras del periodo ≈10× menores que el pago |
| Banamex Clásica | Revolvente ~$15.7k @54.14% | El pago cubre apenas los cargos; saldo baja poco |
| Sears | Revolvente @58.86% + planes MSI | El pago se reparte entre planes; un mes el pago no llegó (cobranza) |
| Liverpool | Revolvente creciente | Compras del periodo son MSI ya financiados; el pago es abono, no compra |
| DiDi | Revolvente ~$21k | El pago (OXXO) cubre 19–31% del saldo; no rastrea compras |
| Klar | Revolvente ~$11k @60–100% | Saldo clavado; pagos << saldo total |
| Mercado Pago | Préstamo / línea de crédito | Cuota de préstamo (~$4,260); compras del wallet no la reconstruyen |

**Consecuencia:** el desglose "1:1 en compras con beneficiario/categoría" que asumía la Tarea 1 **no aplica** — no existen compras que sumen el pago; los pagos son servicio de deuda sobre saldos revolventes grandes. El único desglose fiel posible sería interés+IVA+abono a capital, y solo para los 3–4 meses con estado disponible.

## Segundo hallazgo: los montos del seed son presupuesto del Excel, no el pago bancario real

Las cuentas de débito (BBVA, Banamex MiCuenta, BanCoppel) revelan los pagos REALES a las tarjetas, que **difieren de los agregados del seed**:

- Banamex Clásica: seed **$1,500**/mes vs pago real **$2,750–2,970**.
- Klar: seed **$3,050** vs salidas reales **$1,900–2,000**.
- DiDi: el segundo agregado del seed **$1,028.40** coincide exacto con una amortización "préstamo DiDi" que sale por débito (es deuda, no consumo).

El seed es **fiel al presupuesto del Excel** (lo que Norma planeó pagar), no al movimiento bancario. Corregir los montos a la realidad rompería el invariante de fidelidad al Excel/golden — por eso **no se corrigen**.

## Decisión (Agustín, 2026-07-08): NO mutar el seed

Se evaluaron tres opciones para materializar el desglose:

1. **No mutar el seed** ✅ **(elegida).** La semilla queda fiel al Excel. El valor analítico ya está entregado (gold + arquetipo + este reporte). El desglose real de movimientos entra **hacia adelante** por la feature de alimentación mensual (Tarea 4), ejecutada sobre los estados reales cada mes.
2. Split interés/capital solo mar–jun 2026 — descartada: dejaría el seed inconsistente entre meses (2025 y jul sin partir) y mutaría el golden por ganancia marginal (misma categoría, mismo total).
3. Split interés/capital en todos los meses extrapolando — descartada: inventaría datos para meses sin evidencia, rompiendo la fidelidad al Excel.

**Efecto en el pipeline de seed:** ninguno. No hay cambios en `excel_to_room_etl.py`, ni en el asset `budget_database.db`, ni en el golden. `check_seed_integrity.sh` sigue verde sin acción. No se creó `statement_breakdowns.json`.

## Insumos entregados (gitignored, en `Estados de Cuenta/`)

- `_gold/raw/<Banco>-<AAAA-MM>.json` — 27 extracciones a mano, verificadas al centavo (estándar de oro para la Tarea 3).
- `_arquetipo/<Banco>.md` (10) + `ANALISIS_ARQUETIPO.md` — dónde vive cada campo por banco, formatos de tabla, notación MSI, trampas de extracción, veredicto por tarjeta.
- `_nvidia/baseline/*.json` + `METRICAS_BASELINE.md` — corrida NVIDIA baseline (F1 0.767) para la Tarea 3.
- `MAPEO.md` — archivo→banco/últimos4/mes→wallet key.

## Insumo para la conciliación mensual (Tarea 4)

El cruce cuenta-de-débito → tarjeta documenta la contraparte real de cada pago (útil para evitar doble conteo cuando la feature importe estados): las salidas `PAGO … A TB` (Banamex Clásica), `A KLAR "Pago tarjeta"`, `Pago didi tarjeta`, `Transf. a DIDI PR`, y `PAGO MI TELMEX` son los pagos reales que la feature debe reconciliar contra el agregado del seed, no capturar como gasto nuevo.
