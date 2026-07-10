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

---

## 7. Ronda 2 — correcciones de Agustín (2026-07-07)

Agustín revisó la tabla de clasificación de la Ronda 1 (su versión corregida quedó en `seed/TABLA_RECLASIFICACION_2026-07.md`) y pidió una capa de **canonicalización/homogeneización de conceptos** más una serie de correcciones. Todo lo de esta sección ya está aplicado en el ETL (`scripts/etl/excel_to_room_etl.py`), en las reglas (`scripts/etl/attribution_rules.json`) y en la semilla golden. La tabla final regenerada desde la BD vive en `seed/TABLA_CLASIFICACION_2026-07.md`.

**Conteos finales:** 37 quincenas · **917 gastos** (896 líneas del Excel + 21 hijos de split de teléfonos) · 4,442 atribuciones · 53 ingresos · total global **$2,103,111.13** (idéntico al Excel — los splits preservan el total exacto) · 56 conceptos únicos, sin variantes de mayúsculas duplicadas.

### 7.1 Capa de canonicalización de conceptos

Nueva capa declarativa en el ETL (`CANONICAL_CONCEPT_RULES` + `canonicalize_concept`): renombra el concepto **antes** de insertar y, cuando el canónico difiere del crudo, guarda en `notes` la traza `Concepto original Excel: <crudo>` (además de la hoja de origen, que ya se guardaba; separador `·`). Los mapeos condicionados por nombre de hijo se resuelven **por categoría resuelta o sección**, no solo por texto (el mismo token "DAVID"/"PAU"/"SANTIAGO" cae en categorías distintas según la sección del Excel).

| Crudos del Excel | Concepto canónico | Condición | Nota extra |
|---|---|---|---|
| `Berna`, `Bernardo` | **Bernardo** | — | — |
| `Didi`, `Didi Card`, `Didi tarjeta` | **DiDi** | — | — |
| `David` (14×), `David Abril/Febrero/Marzo/Mayo`, `David Febrero y 3000 Marzo`, `Inscripcion David` | **Escuela David** | categoría = ESCUELA.DAVID | — |
| `Santiago`, `Santiago+mes`, `Santi Junio/Mayo`, `Santiago inscripción`, `Inscripcion Santi`, `Libro Santi` | **Escuela Santiago** | categoría = ESCUELA.SANTIAGO | — |
| `Pau` (mesada 17×), `inscripción Pau` | **Escuela Pau** | categoría = ESCUELA.PAU | — |
| `Pau` ($800 en OTHERS), `Pau Raul` ($500) | **Pau** | categoría = TRANSFERENCIAS_FAMILIARES.PAU | Pau Raul: "gasto de Pau; Raúl es su novio" |
| `Hawainano` (typo) | **Hawaiano** | — | — |
| `Diversion` (sin tilde, 8×) | **Diversión** | — | — |
| `Cochecito` (17×) y `Gasolina chochecito` (19×) | **Gasolina Cochecito** | sección TRANSPORTATION | — |
| `Cochecito` ($8,000 en OTHERS) | **Cochecito (servicio)** | sección OTHER | — |
| `Prestamo Omar 3…10`, `Prestamo 1 Omar`, `Prestamo 2 (Omar)` | **Préstamo Omar** | — | "pago N de la serie" |
| `Mercado Libre 1 de 12` / `7 de 12` | **Mercado Libre MSI** | — | "pago N de 12" |
| `Buro de credito 1 de 3` / `2 de 3` | **Buró de Crédito** | — | "pago N de 3" |
| `David` ($249 en HOUSING) | **Teléfono David** | categoría = OTHER.TELEFONO | — |
| `Telefono David/Norma/Santi/Benji/Pau` | **Teléfono David/Norma/Santi/Benji/Pau** (con tilde) | — | — |

**Veredicto Cochecito: DISJUNTOS, se unificaron.** Se comprobó hoja por hoja en el Excel: `Cochecito` aparece SOLO en las quincenas 1–15 y `Gasolina chochecito` SOLO en las 16–fin de mes; **nunca coinciden en la misma hoja** — es el mismo cargo de gasolina (~$1,400–1,600/quincena) con nombre inconsistente. Ambos quedan como **"Gasolina Cochecito"** (36×, $54,600, TRANSPORTATION.GASOLINA, beneficia Pau/Agustín/David). El `Cochecito` de $8,000 en OTHERS (jun-2026) queda aparte como **"Cochecito (servicio)"** en TRANSPORTATION.MAINTENANCE.

**No se creó `installment_plan`** para Mercado Libre MSI ni Buró de Crédito (montos inconsistentes entre pagos); queda anotado como mejora futura — el "N de M" se preserva en la nota de cada gasto.

### 7.2 Split de teléfonos por persona

Objetivo de Agustín: "por persona un mismo cargo con mismo concepto cada mes", aunque no sea fiel a la línea compartida del Excel. Nuevo mecanismo `SPLIT_RULES` en el ETL: una línea del Excel emite N gastos hijos con id determinista (`did(expense:<quincena>:<sección>:<crudo>:<member_key>)`), montos repartidos equitativamente en centavos con el residuo al último (el total exacto se preserva), beneficiario 100% el miembro respectivo y nota `Parte de línea compartida del Excel: <crudo> ($total)`.

| Línea del Excel | Hijos emitidos |
|---|---|
| `Telefono Pau y David` (15×) | **Teléfono Pau** (Pau 100%) + **Teléfono David** (David 100%), 50/50 |
| `Telefono Movistar` (2×) | **Teléfono Norma** + **Teléfono Pau** + **Teléfono David** + **Teléfono Santi**, 25% c/u |

Además se corrigió la regla vieja `tel_pau_david`, que hacía que el único `Telefono Pau` suelto ($218) beneficiara a David 50%: ahora hay regla `tel_pau` (exacta) con Pau 100%.

Resultado por persona (todos OTHER.TELEFONO): Teléfono Pau 18× $3,995 · Teléfono David 20× $4,525 (incluye el `David` de $249 en HOUSING) · Teléfono Norma 18× $8,227 · Teléfono Santi 18× $3,731 · Teléfono Benji 8× $6,080. Conteo total: 896 + 15 + 6 = **917 gastos**; el total MXN global no cambia.

### 7.3 Regla de pagador desde oct-2025 (regla de negocio confirmada)

**Benjamín no percibe sueldo desde oct-2025** (la celda E4 del Excel está vacía desde entonces). Regla implementada en `_derive_payer_shares`:

- Gastos con fecha **>= 2025-10-01**: PAYER = **Norma 100%**, con la única excepción de **Spotify → Benjamín 100%** (suscripción personal que él sigue pagando).
- Gastos anteriores: derivación **fiel** de las columnas Norma/Benjamín del Excel (comportamiento de siempre).

Dato observado: las 8 apariciones de Spotify son todas pre-oct-2025 y el Excel ya las pagaba Benjamín, así que la excepción no dispara en la semilla actual — queda implementada para cuando el ETL se recorra con datos futuros. **Desviación consciente respecto a la tabla de Agustín:** su columna "Pagado por" mostraba Norma 100% en algunos conceptos (Escuela Pau, Benji, mesadas Norma/Santi) cuyas quincenas pre-oct-2025 tienen pagos reales de Benjamín en el Excel; se respetó la regla acordada (fiel antes del corte), por eso la tabla final muestra p. ej. "Escuela Pau — Norma 69%, Benjamín 31%".

### 7.4 Beneficiarios según la tabla corregida

Cambiados a **los 6 miembros en partes iguales** (1666 bps c/u, residuo al último): Banamex Clasica, Buró de Crédito, Coppel, DiDi, Klar, Liverpool, Mercado Libre MSI, Mercado Pago, Walmart, **Hipoteca** (antes solo adultos), **Spotify** (antes solo Benjamín), Kigo, Google Nest, Caja Chica.

Otros cambios: **Bernardo → Benjamín 100%** · **Marco y omar → Norma 100%** · **Changan → Norma 100%** · **Contador → Benjamín 50 / Norma 50**. **Sears se queda Norma 100%** (decisión explícita de Agustín — NO pasa a todos). El resto quedó como estaba.

### 7.5 Categorías nuevas

Creadas en el ETL con ids deterministas `did("category:<CODE>")`:

| Código | Nombre | Parent | Kind | Gastos |
|---|---|---|---|---:|
| `LOANS.KLAR` | Klar | LOANS | EXPENSE_INSTALLMENT | 2 ($5,050, wallet Klar) |
| `SERVICIOS_EXTERNOS.BERNARDO` | Bernardo | SERVICIOS_EXTERNOS | EXPENSE_VARIABLE | 2 ($8,100) |
| `SERVICIOS_EXTERNOS.CONTADOR` | Contador | SERVICIOS_EXTERNOS | EXPENSE_VARIABLE | 1 ($2,000) |
| `SAVINGS.CAJA_CHICA` | Caja Chica | SAVINGS | SAVINGS | 1 ($1,000) |

Caja Chica va en SAVINGS (no en OTHER) — confirmado por Agustín: es un ahorro para todos.

### 7.6 Validación y promoción a golden

Validado sobre el asset final (`app/src/main/assets/budget_database.db`, tras `verify_db.py`): 917 gastos · total $2,103,111.13 exacto · bps = 10000 por (gasto, rol) en los 917×2 · todo gasto con ambos roles · `PRAGMA foreign_key_check` limpio · `user_version = 1` · 1 sola quincena ACTIVE (2026-07-FIRST) · 0 POSTED futuros · PAYER Norma 100% en todo gasto >= 2025-10-01 · conceptos canónicos presentes y **cero restos de los crudos renombrados** · categorías nuevas pobladas · `household_id = default_household`. Asset promovido a `seed/budget_database.golden.db` (+ `.sha256` regenerado) y `scripts/check_seed_integrity.sh` en verde.

**Cosmético pendiente (no pedido, no aplicado):** algunos conceptos conservan la capitalización cruda del Excel (`caja chica`, `KIGO`, `Google nest`, `mercado pago`, `Suscripcion nivel 6`). Cada uno tiene UNA sola variante en todo el Excel, así que no hay duplicados — solo estética. Si se quiere, son cinco entradas más en `CANONICAL_CONCEPT_RULES`.

## 8. Notas sueltas del Excel — análisis por patrones y proximidad (2026-07-07)

Escaneé los bloques inferiores (filas 60–140) de las 37 hojas. La mayoría es **plantilla arrastrada**: al duplicar la hoja de la quincena anterior, Norma arrastra notas viejas sin borrarlas (p. ej. "Banamex 5015.59 / Mercado Pago 4837" idéntico en mayo→oct 2025; "Omar 3500 / Mensualidad prestamo 4260.08" idéntico de may-2025 a mar-2026). Lo genuinamente informativo:

1. **Fondo único de $59,000 (Q1 feb-2025, filas 69–75).** Una tabla real de gasto con saldo decreciente: Mueble $19,900 → Pau $10,000 → Liverpool $7,663.50 → Despensa $2,078 → Sears $2,696 → Reparación coche $6,900 → **saldo final $9,762.50**. Por la fecha (primera quincena de febrero) parece **aguinaldo/bono gastado fuera del presupuesto**. NO se importó a la BD: "Mueble" y "Reparación coche" serían gastos reales nuevos (~$26,800), pero Liverpool/Sears/Despensa/Pau podrían duplicar líneas ya presupuestadas. **Decisión de Agustín pendiente**: ¿importar Mueble y Reparación coche como gastos one-off de feb-2025?
2. **"Mensualidad prestamo $4,260.08 / Omar $3,500 / Total $760.08 (Mercado pago)".** Patrón estable desde may-2025: hay una **mensualidad de préstamo Mercado Pago de $4,260.08 de la que Omar aporta $3,500** y el hogar solo absorbe $760.08 netos. Esto explica la serie "Préstamo Omar" (los $5,500×10 son otro préstamo distinto, ya clasificado). Documental; la app ya registra los pagos brutos.
3. **Cuentas por cobrar ("Deben").** dic-2025/ene-2026: "Deben $1,900"; oct-2025: "Deben $3,600 · Medicina + rotafolio". Alguien le debe a Norma por compras adelantadas. La app tiene préstamos por cobrar (Cuentas) si se quiere capturar.
4. **Saldos/pagos de corte de tarjetas por quincena** (BANAMEX $1,057→$1,783→$20,808; BBVA $800→$630.28; Coppel $2,000): tracking manual de estados de cuenta — exactamente lo que reemplaza la nueva función de reescritura por estado de cuenta.
5. **Viáticos del trabajo de Norma (Q1 oct-2025, cols E–J):** "Airbnb capacitadores (Inbursa)", "viáticos Néstor", "Coahuila", "accidente/reposición" — gastos **reembolsables del trabajo**, no del hogar. Correctamente fuera de la BD.
6. **Ahorro efectivo**: "Q2 nov: $18,000" (hoja ene-2025) y "Ahorro $1,700 · 30-ene-2026" — apartados en efectivo puntuales, no capturados (el goal "Ahorro Empresa" solo suma los $1,000 de E9).

## 9. Ronda 3 — captura de las notas confirmadas (2026-07-07)

Agustín confirmó que el fondo de $59,000 de feb-2025 fue un **aguinaldo** y autorizó registrar lo propuesto en §8:

1. **Ingreso "Aguinaldo"** de $59,000 (Norma, Q1 feb-2025, wallet Efectivo, cadencia UNICO, POSTED).
2. **Gastos one-off del fondo** que no duplican presupuesto: **Mueble $19,900** (HOUSING.MUEBLES — categoría nueva; beneficia a los 6) y **Reparación coche $6,900** (TRANSPORTATION.MAINTENANCE; beneficiaria Norma — corregible si el coche era de otro). Pagados por Norma (su aguinaldo). Los renglones restantes del fondo (Liverpool $7,663.50, Sears $2,696, Despensa $2,078, Pau $10,000) NO se importaron: ya existen como líneas presupuestadas de esa quincena.
3. Los **actuales de Q1 feb-2025** dejan de ser == projected: ingreso real $164,000 (105k sueldos + 59k aguinaldo), gasto real $85,349 (58,549 presupuestado + 26,800 del fondo).
4. **Cuenta por cobrar "Deben"**: préstamo por cobrar de $3,600 (oct-2025, "Medicina + rotafolio") con saldo $1,900 (nota de dic-2025/ene-2026), deudor **"Por identificar"** (miembro externo nuevo `deudor_pendiente` — renombrar en la app cuando Norma diga quién es).

Totales finales: **919 gastos** ($2,129,911.13 = 2,103,111.13 del presupuesto + 26,800 del fondo), 54 ingresos, 2 préstamos por cobrar (Jaudiel $105,000 + Deben $1,900), 10 miembros, golden re-promovido y verificado en emulador (instalación fresca, migra 1→15, sin crash).

## 10. Ronda 4 — ajustes finales (2026-07-07)

1. **"Reparación coche"** (one-off del aguinaldo): beneficiarios corregidos a **Agustín/David/Normita** (33% c/u).
2. **Pau se llama "Normita" en toda la app**: display del miembro, conceptos ("Escuela Normita", "Teléfono Normita", "Normita", "Normita, David y Agus") y categorías ("Mesada Normita", "Colegiatura Normita"). El key interno del miembro y los alias conservan "pau" porque así viene en el Excel; se añadió el alias "normita".
3. **Correo apartado en Firebase Auth**: `normly@gmail.com` (CORRECCIÓN: gmail, no hotmail) creado vía Admin SDK — uid `fQP5T1frbuZyShweNq7m3bTnVqg2`, display name "Norma". Sigue pendiente habilitar el proveedor anónimo (y Google) en la consola para el sign-in del cliente.

Totales sin cambio: 919 gastos / $2,129,911.13 / 54 ingresos / 2 préstamos / 10 miembros. Golden re-promovido, verificado en emulador (instalación fresca, migra 1→15, sin crash).

## 11. Ronda 5 — modelo de futuro: solo quincena activa + plantillas de recurrencia + MSI (2026-07-07)

Agustín rediseñó cómo la semilla representa el **futuro**. Antes el ETL sembraba ~85 gastos PLANNED con fechas repartidas por todo el periodo (el calendario marcaba casi todos los días) y 3 quincenas futuras (jul-2ª, ago-1ª, ago-2ª). Nuevo modelo: la semilla **solo llega hasta la quincena activa** y las obligaciones fijas se expresan como **plantillas de recurrencia** que la app materializa en runtime.

### Qué se quitó
- **Quincenas futuras eliminadas.** `_ingest_sheets` descarta toda hoja cuyo `start_date` sea posterior al `end_date` de la quincena activa (2026-07-15). Se saltaron 3 hojas: `Quincena 16 al 30 de Julio`, `Quincena 1al 15 Agosto`, `Quincena 16 al 30 Agosto`. Resultado: **34 quincenas** (33 CLOSED + 1 ACTIVE `2026-07-FIRST`, **0 PROVISIONED**). Las futuras las creará el rollover de la app.
- **Cero gastos PLANNED.** `_insert_expense` ahora hace early-return si el status derivado no es POSTED. La semilla solo persiste lo ya ocurrido (`occurred_at <= HOY`). En la quincena activa quedan solo los gastos de jul 1-7; jul 8-15 ya no se siembran (vendrán de las plantillas). Ningún ingreso queda PLANNED tampoco.

### Plantillas de recurrencia (`recurrence_template`) — 36 creadas
Una plantilla por obligación fija recurrente. **Excluidos** por variables: Comida, Despensa, Limpieza, Gasolina, Gasolina Camioneta, Gasolina Cochecito, Diversión. Cada plantilla deriva de los gastos POSTED históricos del concepto: `default_amount_mxn` = mediana; `category_id`/`default_payment_method_id`/`default_beneficiary_ids`/`default_payer_split` = del gasto más reciente (pagador Norma 100% por la regla del hogar desde oct-2025, salvo **Spotify** que paga Benjamín); `confidence_score` = min(1, nq/12); `learned_from_expense_ids` = primeros 5 ids.

**Esquema JSON usado (verificado contra `RecurrenceMaterializer.kt` para que la app lo parsee):**
- `cadence`: enum que reconoce el materializador — **`QUINCENAL_EVERY`** (≈2/mes, aparece en ambas mitades en ≥50% de los meses) o **`MONTHLY_SPECIFIC_HALF`** (≈1/mes). *(Nota: un literal `"QUINCENAL"`/`"MONTHLY"` NO lo parsea la app y no materializaría nada.)*
- `cadence_detail`: `{"day_of_month": <1-31>}` (mediana del día de `occurred_at`). El materializador lo coacciona a 1-15 (primera mitad) / 16-fin (segunda) según la cadencia.
- `default_beneficiary_ids`: array JSON `["<member_id>", …]` (reparto equitativo — formato histórico de `parseBeneficiaries()`).
- `default_payer_split`: objeto JSON `{"<member_id>": <bps>}` que suma 10000 (`parsePayerSplit()`).
- `next_expected_date`: primera fecha futura (> HOY) según cadencia + día típico (ISO), rango generado 2026-07-08 … 2026-08-07.

Distribución de cadencias: **5 `QUINCENAL_EVERY`** (Hipoteca, DiDi, Comida Gatas, Psicóloga, Normita/David/Agus) + **31 `MONTHLY_SPECIFIC_HALF`** (el resto).

Conceptos templatizados (36): Hipoteca, Internet, Agua, Electricidad, Netflix, Prime, HBO, Spotify, YouTube, Adobe, Google, Google Nest, Kigo, Coursera, Suscripción Nivel 6, Teléfono Norma/Normita/David/Santi/Benji, Walmart, Banamex Clásica, Liverpool, Sears, Coppel, Mercado Pago, DiDi, Klar, Comida Gatas, Psicóloga, Préstamo Omar, Benji (seguro), Escuela David/Santiago/Normita, y **Normita, David y Agus** (seguro de los tres hijos mayores). *(La lista de Agustín mencionaba "Normita" y "David y Agus (seguro)" por separado, pero en la semilla es UN solo concepto de seguro → 36 plantillas, no 37.)*

### Planes MSI (`installment_plan`) — 2 creados
Series a meses reales documentadas en el concepto del Excel:
- **Mercado Libre 12 MSI** — 12 cuotas, cuota mediana $1,342, principal $16,104, `current_installment` 8, `ACTIVE`, inicio 2025-05-23, wallet Mercado Libre BNPL, categoría `LOANS.MERCADO_LIBRE`, interés 0%.
- **Buró de Crédito 3 pagos** — 3 pagos, cuota mediana $4,300, principal $12,900, `current_installment` 2, `ACTIVE`, inicio 2025-10-12, categoría `LOANS.BURO`, interés 0%.

**NO** se siembran Walmart/Coppel/Sears/Liverpool/Banamex Clásica como `installment_plan`: son crédito **revolvente** (saldo rotativo, no una serie cerrada de N cuotas). Sí viven como plantillas de recurrencia.

### Conteos finales
- **34 quincenas** (33 CLOSED + 1 ACTIVE `2026-07-FIRST`; 0 PROVISIONED).
- **834 gastos** (todos POSTED; 0 PLANNED; 0 con `occurred_at > HOY`). *(919 − 70 de las 3 quincenas futuras − 15 PLANNED de la 2ª semana de la activa.)*
- **51 ingresos** (54 − 3 futuros; 0 PLANNED), **4,038 atribuciones** (bps=10000 por gasto/rol), **36 plantillas**, **2 planes MSI**, 2 préstamos, 10 miembros, 88 categorías, 12 wallets.
- Total MXN POSTED ≈ $1,919,956.73. `user_version=1`, `household_id=default_household`, `PRAGMA foreign_key_check` limpio.

Golden re-promovido (`seed/budget_database.golden.db` + `.sha256` = `686a2eb1…93282f`), `scripts/check_seed_integrity.sh` → `OK: asset == golden`.
