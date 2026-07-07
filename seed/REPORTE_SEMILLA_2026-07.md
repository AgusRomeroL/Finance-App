# Reporte de la semilla — julio 2026

**Fuente:** `presupuesto 2.5` (Excel descifrado, 37 hojas).
**Fecha del análisis:** 2026-07-07.
**Alcance de este documento:** auditoría de clasificación de conceptos, reglas nuevas añadidas a `scripts/etl/attribution_rules.json`, decisiones globales ya confirmadas, datos del Excel que el ETL no captura, y comparación contra la semilla anterior (`seed/budget_database.golden.db`).

---

## 1. Resumen del Excel nuevo

| Dato | Valor |
|---|---|
| Hojas de quincena | **37** (todas se parsean correctamente) |
| Rango cubierto | **2025-01-SECOND → 2026-08-SECOND** (16-ene-2025 al 30-ago-2026) |
| Líneas presupuestadas | **896** (renglones con monto proyectado > 0) |
| Conceptos únicos (normalizados) | 94 |

**Quincenas que NO existen en el Excel** (se dejan como huecos en la base, igual que en el Excel original):

- 2025-01-FIRST (1 al 15 de enero 2025)
- 2025-02-SECOND (16 al 28 de febrero 2025)
- 2025-11-FIRST (1 al 15 de noviembre 2025)

Con las reglas nuevas, **las 896 líneas quedan clasificadas por regla explícita o por heurística confiable; ya no queda ningún concepto en fallback pobre** (categoría genérica + beneficiarios por defecto + cartera por defecto a la vez).

---

## 2. Reglas nuevas de clasificación (14)

Cada regla dice: qué concepto del Excel atrapa, a qué categoría/beneficiarios/cartera lo manda, cuánto dinero mueve en total (todas las quincenas juntas) y por qué se decidió así. Las marcadas con ⚠️ son decisiones de **baja confianza** que conviene que Agustín revise con Norma.

| Concepto (Excel) | Categoría | Beneficiarios | Cartera | Total afectado | Razonamiento |
|---|---|---|---|---:|---|
| `Klar` (2 líneas en ENTERTAINMENT) | Tarjetas y préstamos (raíz LOANS) | Norma | **klar** | $5,050.00 | Es el pago de la tarjeta Klar, no entretenimiento; no existe subcategoría LOANS.KLAR (ver pendientes). |
| `Telefono David` (2 en HOUSING) | Teléfono celular (OTHER.TELEFONO) | David | efectivo | $499.00 | Consistente con las reglas de teléfono de Norma, Santi, Benji y Pau. |
| `Inscripcion David` | Colegiatura David (ESCUELA.DAVID) | David | efectivo | $17,000.00 | Inscripción escolar → mismo destino que sus colegiaturas, no mesada. |
| `inscripción Pau` | Colegiatura Pau (ESCUELA.PAU) | Pau | efectivo | $7,780.00 | Igual que la anterior; la tilde se elimina al normalizar. |
| `Libro Santi` | Colegiatura Santiago (ESCUELA.SANTIAGO) | Santiago | efectivo | $2,500.00 | Libro escolar → gasto de escuela de Santiago. |
| `Santi Junio` (2 líneas) | Colegiatura Santiago (ESCUELA.SANTIAGO) | Santiago | efectivo | $13,200.00 | Mismo patrón que "Santiago Febrero/Marzo/…": hijo + mes = colegiatura. |
| `Santi Mayo` | Colegiatura Santiago (ESCUELA.SANTIAGO) | Santiago | efectivo | $6,600.00 | Ídem. |
| ⚠️ `Pau Raul` | Mesada Pau (TRANSFERENCIAS_FAMILIARES.PAU) | Pau | efectivo | $500.00 | Se trata como transferencia a Pau; no sabemos quién es Raúl. |
| `Contador` | Servicios externos (raíz) | Norma y Benjamín | efectivo | $2,000.00 | Honorarios del contador = servicio profesional del hogar/negocio. |
| `caja chica` | Otros (raíz OTHER, deliberado) | Norma y Benjamín | efectivo | $1,000.00 | Gasto operativo genérico del hogar; se documenta para que no cuente como "sin clasificar". |
| ⚠️ `Berna` y `Bernardo` (1 regla cubre ambos) | Servicios externos (raíz) | Norma y Benjamín | efectivo | $8,100.00 | Pagos a una persona, presumiblemente por un servicio; falta confirmar quién es Bernardo. |
| ⚠️ `Marco y omar` | Otros (raíz OTHER, deliberado) | Norma y Benjamín | efectivo | $2,120.00 | Pago a personas. Podría ser abono al préstamo de Omar, pero el monto no coincide con la mensualidad de $5,500 — se deja en Otros para revisión. |
| ⚠️ `Cochecito` (suelto en OTHERS, jun-2026) | Mantenimiento vehículo (TRANSPORTATION.MAINTENANCE) | Agustín, David, Pau | efectivo | $8,000.00 | $8,000 es demasiado para gasolina; se asume reparación/servicio del coche de los hijos. |
| ⚠️ `Changan` (en GIFTS, jul-2026) | Transporte (raíz) | Norma y Benjamín | efectivo | $5,000.00 | Changan es una marca de coche; el contexto de la hoja no aclara (¿enganche?, ¿servicio?, ¿regalo relacionado al coche?). Revisar con Norma. |

**Verificaciones de cobertura de las reglas que ya existían** (no requirieron cambios):

- `DIVERSIÓN` con tilde → la normalización quita tildes, la regla `diversion` la atrapa. ✓
- `GASOLINA CHOCHECITO` (typo) → regla `gasolina_chochecito` la atrapa. ✓
- `DAVID ABRIL/FEBRERO/MARZO/MAYO`, `SANTIAGO FEBRERO/MARZO/ABRIL/MAYO`, `David Febrero y 3000 Marzo` → reglas de mesadas/colegiaturas existentes las atrapan por `contains`. ✓
- `PRESTAMO OMAR 3…10`, `Prestamo 1 Omar`, `Prestamo 2 (Omar)` → `prestamo_omar` + variantes `alt1`/`alt2` los cubren. ✓
- `MERCADO LIBRE 1 DE 12` / `7 DE 12` → regla `mercado_libre`. ✓
- `DIDI CARD` y `Didi tarjeta` → reglas `didi_card` / `didi_tarjeta_ent`. ✓
- `BURO DE CREDITO 1 DE 3` / `2 DE 3` → regla `buro_credito`. ✓
- `MARY` → regla `mary`. ✓
- `TELEFONO BENJI` → regla `tel_benji`; `Benji` en SEGUROS_MEDICOS → regla `seguro_benji`. ✓
- `INSCRIPCION SANTI` / `Santiago inscripción` → reglas existentes. ✓

**Pendientes — categorías que convendría crear en el futuro** (NO se inventaron; las reglas usan la raíz mientras tanto):

1. `LOANS.KLAR` — para que el pago de la tarjeta Klar tenga su propia subcategoría como Coppel/Sears/Walmart.
2. `SERVICIOS_EXTERNOS.CONTADOR` — el contador es un servicio recurrente potencial.
3. `SERVICIOS_EXTERNOS.BERNARDO` (o el nombre correcto) — si Agustín confirma quién es y si volverá a aparecer.

**Nota sobre la cartera de Klar:** las demás tarjetas (Coppel, Liverpool, etc.) se pagan desde `efectivo` por convención; para Klar se usó la cartera `klar` por decisión explícita de esta migración. Si se prefiere homologar, basta cambiar `override_payment_method` a `"efectivo"` en la regla `klar_pago_tarjeta`.

---

## 3. Decisiones globales ya confirmadas por Agustín

Estas decisiones se documentan aquí; no requieren acción:

1. **Sueldo de Benjamín ausente desde oct-2025:** el Excel tiene la celda E4 vacía desde entonces y la semilla lo refleja fiel (no se inventa ingreso).
2. **Sueldo de Norma:** $60,000 → **$75,000 desde jun-2026** → **$85,000 desde ago-2026**.
3. **Gastos futuros:** las quincenas posteriores a hoy se siembran como **PLANNED** (planeados, confirmables desde la app), no como gastos ya ejercidos.
4. **Préstamo a Jaudiel de $105,000** se conserva en la semilla como préstamo por cobrar.
5. **Ahorro "Ahorro empresa":** $1,000/mes acumulado al campo `current_mxn` de la meta de ahorro.

---

## 4. Datos del Excel que el ETL NO captura

Las hojas tienen un bloque inferior de notas y mini-tablas sueltas (saldos de cuentas, deudas puntuales, recordatorios) que el ETL ignora a propósito porque no son renglones de presupuesto. Se listan para que Agustín decida si algo merece captura manual en la app:

| Hoja | Nota | Contenido |
|---|---|---|
| Todas las hojas (2026) | "Prestamo oficinas / jaudiel" | 105,000 / 10,000 / 50,000 — es el préstamo a Jaudiel ya cubierto por la decisión global n.º 4. |
| Quincena 16 al 31 Ene (2025) | Saldos | BANAMEX 1,057.38 / BBVA 800; reparto Norma 50,046 vs Benjamín 15,227 ("Sobra" −5,746 / 29,073); "Ahorro Efectivo: Q1 nov #REF! / Q2 nov 18,000". |
| Quin. 1 al 15 Febrero (2025) | Mini-tabla de un fondo de $59,000 | Mueble 19,900 → restan 39,100; Pau 10,000 → 29,100; Liverpool 7,663.50 → 21,436.50; etc. |
| Quincena 1 al 15 Enero 2026 | Saldos y deudas | **"BBVA 1555 / Banamex 996 / Deben 1900"**; al lado: MercadoPago 3,380.68 / Sears 1,920 / Banamex 1,500 / Suscripciones 976 (total 7,776.68); "didi 4,800 (3,100.63)" y "klar 2,618.77"; nota "1 de marzo". |
| Quincena 16 al 31 de Enero 2026 | Saldos | BANAMEX 20,808 / BBVA 630.28 / Coppel 2,000 (total 22,539.28). |
| Quincena 1al 15 Agosto (2026) | Notas | "Ahorro 1,700"; "30 de enero de 2026". |
| Quincena 16 al 30 Agosto (2026) | Deudas/pagos | **"Omar 3,500" / "Mensualidad prestamo 4,260.08"** / "Total 760.08 Mercado pago" / "AMAZON 732"; mismos saldos BANAMEX/BBVA/Coppel que ene-2026. |
| Varias hojas | Etiqueta "Ahorro empresa" | Aparece en la cabecera de cada hoja (celda C9); ya está cubierta por la decisión global n.º 5. |

Además, muchas hojas conservan renglones "plantilla" en cero (Disney y Star, Insurance, Licensing, Grooming, Charity, Attorney…) que el ETL descarta correctamente por no tener monto.

---

## 5. Comparación contra la semilla anterior (golden)

La semilla vieja tenía **33 quincenas y 793 gastos** (2025-01-SECOND → 2026-06-SECOND). El Excel nuevo produce **37 quincenas y 896 líneas** (hasta 2026-08-SECOND).

En el rango que ambas comparten, **las primeras 28 quincenas son idénticas** (mismo total proyectado y mismo conteo). Cambiaron las últimas 5 — es decir, Norma actualizó abril-junio 2026 después de que se generó la semilla vieja:

| Quincena | Proyectado viejo | Proyectado nuevo | Gastos viejos | Gastos nuevos | Cambio |
|---|---:|---:|---:|---:|---|
| 2025-01-SECOND … 2026-04-FIRST (28 quincenas) | — | — | — | — | Sin cambios |
| **2026-04-SECOND** | $58,198.20 | $54,026.20 | 22 | 21 | ▼ −$4,172.00 |
| **2026-05-FIRST** | $59,765.00 | $60,437.00 | 28 | 26 | ▲ +$672.00 |
| **2026-05-SECOND** | $58,021.20 | $61,578.20 | 22 | 22 | ▲ +$3,557.00 |
| **2026-06-FIRST** | $53,606.66 | $58,669.65 | 27 | 27 | ▲ +$5,062.99 |
| **2026-06-SECOND** | $36,321.20 | $72,575.20 | 20 | 26 | ▲ **+$36,254.00** (la vieja estaba a medio llenar) |

Quincenas **nuevas** que no existían en la semilla vieja:

| Quincena | Proyectado | Líneas |
|---|---:|---:|
| 2026-07-FIRST | $72,070.00 | 31 |
| 2026-07-SECOND | $55,405.20 | 22 |
| 2026-08-FIRST | $57,197.00 | 26 |
| 2026-08-SECOND | $42,505.20 | 21 |

---

## 6. Nota final

El correo de Norma **`normly@hotmail.com`** queda documentado aquí para su futuro inicio de sesión en la app; **no forma parte de la base de datos** sembrada.
