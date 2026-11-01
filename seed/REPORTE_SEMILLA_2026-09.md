# Reporte de la semilla: septiembre 2026

**Fase 10, trabajo previo que no requiere a Norma.**
**Fuente nueva:** `Copy of presupuesto 2.5 (2).xlsx` (38 hojas, descifrado por Agustín el 2026-09-05, en la raíz del checkout e ignorado por git).
**Comparada contra:** `Copy of presupuesto 2.5 (1).xlsx`, la fuente de la semilla vigente.
**Fecha del análisis:** 2026-11-01, en la rama `fase-10`.
**Estado:** no se generó la base definitiva ni se promovió la golden. Toda corrida del ETL de este reporte escribió en una ruta temporal fuera del repositorio; `app/src/main/assets/budget_database.db` y `seed/budget_database.golden.db` siguen intactos (`f1c541e3...`).

Resumen de una línea por punto:

1. **Diff:** cambian tres hojas (16 al 31 de julio, 1 al 15 y 16 al 31 de agosto) y hay una nueva (1 al 15 de septiembre); las otras 34 no tienen ni una celda distinta. La de septiembre es una **copia exacta** de la del 1 al 15 de agosto. La del 1 al 15 de julio sigue igual que el 7 de julio.
2. **Ingresos:** la quincena del 16 al 31 de agosto bajó el sueldo de Norma de $85,000 a **$75,000**; agosto 1 al 15 y septiembre siguen en $85,000. Benjamín no tiene ingreso en ninguna hoja desde octubre de 2025, pero aparece pagando partes de algunos renglones.
3. **ETL:** cubre las quincenas nuevas sin cambios de lógica. Con `BUDGET_TODAY=2026-09-15`: 38 quincenas, **942 gastos**, 55 ingresos, todos los invariantes en verde. Con el Excel nuevo y la fecha de la golden reproduce la golden fila por fila.
4. **Conceptos:** 94 conceptos en el Excel nuevo, **ninguno nuevo** contra el (1) y **ninguno sin regla explícita**. La lista para Norma sale vacía; lo que sí hay que preguntarle son montos, hojas y reglas de baja confianza.

---

## 1. Insumos

| Insumo | Bytes | sha256 (12) | Contenido |
|---|---:|---|---|
| `Copy of presupuesto 2.5 (1).xlsx` | 896,101 | `d62926a52823` | 37 hojas, 16 ene 2025 a 31 ago 2026 |
| `Copy of presupuesto 2.5 (2).xlsx` | 919,736 | `f3953d5c1183` | 38 hojas, 16 ene 2025 a 15 sep 2026 |

Los dos se leyeron por ruta absoluta; ninguno se copió al repositorio.

Estados de cuenta que ya existen en `Estados de Cuenta/` (gitignorada), por emisor, para saber desde qué mes pedirle a Norma:

| Emisor | Último estado que tenemos | Pedir desde |
|---|---|---|
| BBVA | 2026-06 | julio de 2026 |
| BanCoppel | 2026-05 | junio de 2026 |
| Banamex Clásica | 2026-05 (es el único) | junio de 2026 |
| Banamex MiCuenta | 2026-06 | julio de 2026 |
| DiDi Card | 2026-06 | julio de 2026 |
| Klar | 2026-05 | junio de 2026 |
| Liverpool | 2026-06 | julio de 2026 |
| Mercado Pago | 2026-05 | junio de 2026 |
| Sears | 2026-05 | junio de 2026 |
| Walmart Inbursa | 2026-06 | julio de 2026 |
| Coppel, Mercado Libre | nunca | los que tenga |

---

## 2. Diff celda a celda del Excel (2) contra el (1)

Generado con `scripts/etl/diff_excel.py` (comando en §8). El diff compara **valores** (las fórmulas se leen ya calculadas, `data_only`), etiqueta cada celda con la sección y el concepto de su renglón usando el mismo escáner que el ETL, y separa el diff de líneas presupuestales (lo único que lee el ETL) del resto de la hoja.

### 2.1 Lectura de los cambios

**16 al 31 de julio** (35 celdas, 23 renglones; total que lee el ETL: $55,405.20 → $55,256.20).
- Hipoteca sube de $4,000 a $4,500. El mismo aumento aparece en las dos de agosto y en septiembre: desde esta quincena la hipoteca es de $4,500.
- La mesada de los tres hijos mayores ("Pau, David, Agus", en la sección PERSONAL) baja de $4,500 a $3,000 y la de Santi de $700 a $500. Mismo cambio en agosto y septiembre.
- La inscripción de David sube de $17,000 a $18,300.
- Adobe pasa de $249 a 0: el ETL deja de leerla en esta quincena (vuelve a aparecer en la del 16 al 31 de agosto).
- El resto son totales recalculados de la cabecera y anotaciones laterales (columnas K a N: un desglose de Mercado Pago y cuentas sueltas como "Santi 160.60"), que el ETL no lee.

**1 al 15 de agosto** (50 celdas, 28 renglones; $57,197.00 → $52,137.00).
- Didi tarjeta baja de $6,500 a $4,000; Netflix sube de $329 a $369; hipoteca y mesadas como arriba.
- "Cochecito" (la gasolina del coche de los hijos) pasa de $1,400 a 0.
- El título de la hoja (B2) decía "1 al 15 de julio"; ahora dice agosto.
- La línea "David inscripcion" pierde los $20,000 de la columna de Norma, pero su proyectado ya estaba vacío, así que el ETL no la leía antes ni ahora.
- Columnas L y M: un desglose nuevo de pagos ("banamex" y "mercado": 444, 199, 498, 369, 99, 1,500, 39, 980...), informativo.

**16 al 31 de agosto** (53 celdas, 31 renglones; $42,505.20 → $39,405.20).
- **Sueldo de Norma (E5) de $85,000 a $75,000**, con su total (E6).
- Limpieza baja de $3,000 a $2,500; "Gasolina Chochecito" pasa de $1,400 a 0; hipoteca y mesadas como arriba.
- Notas: se borran los saldos BANAMEX $20,808, BBVA $630.28 y Coppel $2,000 que venían arrastrados desde enero de 2026, y la nota "AMAZON 732"; el rótulo B91 pasa de "Quincena 1 al 15 noviembre" a "Quincena 16 al 30 de agosto" con $33,300 al lado.

**1 al 15 de septiembre (nueva).** 25 líneas por $52,137.00, **idénticas una por una** (concepto, sección y monto) a las del 1 al 15 de agosto, e iguales también en las anotaciones laterales (L13 a N39). Es la hoja de agosto copiada como plantilla: o Norma todavía no la había llenado el 5 de septiembre, o es su presupuesto planeado y no lo ejercido. Sembrarla como gasto POSTED exige que ella lo confirme (pregunta 11 de la solicitud).

**1 al 15 de julio (sospechosa).** Ni una celda distinta entre los dos archivos. Tiene 31 líneas por $72,070, incluidos cargos que no se repiten (Contador $2,000, "Pau Raul" $500, Bernardo $5,000, Changan $5,000, Santiago inscripción $16,000), y es la única hoja de 2026 con la celda E9 (Ahorro empresa) vacía. Como Norma sí volvió a las tres hojas siguientes y a esta no, hay dos lecturas: o el 7 de julio ya estaba completa, o no la revisó. La golden solo tomó las líneas fechadas hasta el 7 de julio (16 de 31, $17,223); la base nueva toma las 31. Tres de los cargos dudosos ("Pau Raul", Bernardo $5,000 y Changan) caen después del 7 de julio, así que la golden nunca los sembró: entran a la base por primera vez con esta corrida. Pregunta 10 de la solicitud.

**Cambios que se repiten.** Hipoteca +$500 desde el 16 de julio; mesadas de los hijos a $3,000 y de Santi a $500 desde el 16 de julio; la gasolina del cochecito desaparece desde agosto.

### 2.2 Celda por celda (salida de `diff_excel.py`)

| Archivo | Bytes | sha256 (12) | Hojas |
|---|---:|---|---:|
| viejo: `Copy of presupuesto 2.5 (1).xlsx` | 896,101 | `d62926a52823` | 37 |
| nuevo: `Copy of presupuesto 2.5 (2).xlsx` | 919,736 | `f3953d5c1183` | 38 |

Hojas nuevas: 'Quincena 1 al 15 Septiembre'.  
Hojas retiradas: ninguna.

Hojas comunes sin ningún cambio de valor: 34 de 37.

| Hoja con cambios | Celdas | Renglones | Rango de filas |
|---|---:|---:|---|
| Quincena 16 al 30 de Julio | 35 | 23 | 4–67 |
| Quincena 1al 15 Agosto | 50 | 28 | 2–63 |
| Quincena 16 al 30 Agosto | 53 | 31 | 4–91 |

#### Celdas de ingreso, hojas de 2026

| Hoja | Sueldo Benjamín (E4) viejo → nuevo | Sueldo Norma (E5) viejo → nuevo | Ahorro empresa (E9) viejo → nuevo |
|---|---|---|---|
| Quincena 1 al 15 Enero 2026 | (vacía) → (vacía) | 60,000 → 60,000 | 1,000 → 1,000 |
| Quincena 16 al 31 de Enero 2026 | (vacía) → (vacía) | 60,000 → 60,000 | 0 → 0 |
| Quincena 1 al 15 febrero 2026 | (vacía) → (vacía) | 60,000 → 60,000 | 1,000 → 1,000 |
| Quincena 16 al 28 de febrereo | (vacía) → (vacía) | 60,000 → 60,000 | 0 → 0 |
| Quincena 1al 15 Marzo | (vacía) → (vacía) | 60,000 → 60,000 | 1,000 → 1,000 |
| Quincena 16 al 31 de marzo | (vacía) → (vacía) | 60,000 → 60,000 | 0 → 0 |
| Quincena 1al 15 Abril | (vacía) → (vacía) | 60,000 → 60,000 | 1,000 → 1,000 |
| Quincena 16 al 30 de ABRIL | (vacía) → (vacía) | 60,000 → 60,000 | 0 → 0 |
| Quincena 1al 15 Mayo 2026 | (vacía) → (vacía) | 60,000 → 60,000 | 1,000 → 1,000 |
| Quincena 16 al 30 de Mayo 2026 | (vacía) → (vacía) | 60,000 → 60,000 | 0 → 0 |
| Quincena 1al 15 Junio 2026 | (vacía) → (vacía) | 60,000 → 60,000 | 1,000 → 1,000 |
| Quincena 16 al 30 de Junio 2026 | (vacía) → (vacía) | 75,000 → 75,000 | 0 → 0 |
| Quincena 1al 15 Julio 2026 | (vacía) → (vacía) | 75,000 → 75,000 | (vacía) → (vacía) |
| Quincena 16 al 30 de Julio | (vacía) → (vacía) | 75,000 → 75,000 | 0 → 0 |
| Quincena 1al 15 Agosto | (vacía) → (vacía) | 85,000 → 85,000 | 1,000 → 1,000 |
| Quincena 16 al 30 Agosto | (vacía) → (vacía) | 85,000 → 75,000 **(cambió)** | 0 → 0 |
| Quincena 1 al 15 Septiembre | (no existía) → (vacía) **(cambió)** | (no existía) → 85,000 **(cambió)** | (no existía) → 1,000 **(cambió)** |

#### Quincena 16 al 30 de Julio

Total proyectado que lee el ETL: $55,405.20 → $55,256.20 (-$149.00).

**Líneas presupuestales (lo que lee el ETL)**

| Tipo | Sección | Concepto | Detalle |
|---|---|---|---|
| baja | ENTERTAINMENT | Adobe | era $249.00 |
| cambio | HOUSING | Hipoteca | proyectado $4,000.00 → $4,500.00; Norma $4,000.00 → $4,500.00 |
| cambio | SEGUROS_MEDICOS | Pau, David, Agus | proyectado $4,500.00 → $3,000.00; Norma $4,500.00 → $3,000.00 |
| cambio | SEGUROS_MEDICOS | Santi | proyectado $700.00 → $500.00; Norma $700.00 → $500.00 |
| cambio | TRANSFERENCIAS_FAMILIARES | Inscripcion David | proyectado $17,000.00 → $18,300.00; Norma $17,000.00 → $18,300.00 |

**Celda por celda**

| Celda | Qué es | Viejo | Nuevo |
|---|---|---|---|
| K4 | cabecera | 19,594.80 | 19,743.80 |
| K6 | cabecera (Total monthly income) | 55,405.20 | 55,256.20 |
| K8 | cabecera | 19,594.80 | 19,743.80 |
| E10 | cabecera (Total) | 55,405.20 | 55,256.20 |
| C13 | HOUSING · Hipoteca · proyectado | 4,000 | 4,500 |
| D13 | HOUSING · Hipoteca · Norma | 4,000 | 4,500 |
| N15 | fuera de bloque | (vacía) | 980 |
| N16 | fuera de bloque | 100 | 1,300 |
| N17 | fuera de bloque | 249 | 0 |
| N20 | fuera de bloque | 1,046.96 | 2,576.30 |
| I21 | ENTERTAINMENT · Adobe · proyectado | 249 | 0 |
| J21 | ENTERTAINMENT · Adobe · Norma | 249 | 0 |
| N21 | fuera de bloque | 1,692.26 | 5,152.60 |
| I22 | ENTERTAINMENT · Subtotal · proyectado | 646.20 | 397.20 |
| J22 | ENTERTAINMENT · Subtotal · Norma | 646.20 | 397.20 |
| C23 | HOUSING · Subtotal · proyectado | 6,419 | 6,919 |
| D23 | HOUSING · Subtotal · Norma | 6,419 | 6,919 |
| M26 | fuera de bloque | (vacía) | 1,162 |
| M27 | fuera de bloque | (vacía) | 160.60 |
| N27 | fuera de bloque | (vacía) | Santi |
| M31 | fuera de bloque | (vacía) | 9,795 |
| N32 | fuera de bloque | (vacía) | 7,660 |
| N34 | fuera de bloque | (vacía) | -2,340 |
| I36 | TRANSFERENCIAS_FAMILIARES · Inscripcion David · proyectado | 17,000 | 18,300 |
| J36 | TRANSFERENCIAS_FAMILIARES · Inscripcion David · Norma | 17,000 | 18,300 |
| C39 | SEGUROS_MEDICOS · Pau, David, Agus · proyectado | 4,500 | 3,000 |
| D39 | SEGUROS_MEDICOS · Pau, David, Agus · Norma | 4,500 | 3,000 |
| C40 | SEGUROS_MEDICOS · Santi · proyectado | 700 | 500 |
| D40 | SEGUROS_MEDICOS · Santi · Norma | 700 | 500 |
| I40 | TRANSFERENCIAS_FAMILIARES · Subtotal · proyectado | 20,300 | 21,600 |
| J40 | TRANSFERENCIAS_FAMILIARES · Subtotal · Norma | 20,300 | 21,600 |
| C41 | SEGUROS_MEDICOS · Subtotal · proyectado | 8,200 | 6,500 |
| D41 | SEGUROS_MEDICOS · Subtotal · Norma | 8,200 | 6,500 |
| K63 | notas | 55,405.20 | 55,256.20 |
| K67 | notas | 19,594.80 | 19,743.80 |

#### Quincena 1al 15 Agosto

Total proyectado que lee el ETL: $57,197.00 → $52,137.00 (-$5,060.00).

**Líneas presupuestales (lo que lee el ETL)**

| Tipo | Sección | Concepto | Detalle |
|---|---|---|---|
| cambio | ENTERTAINMENT | Didi tarjeta | proyectado $6,500.00 → $4,000.00; Norma $6,500.00 → $4,000.00 |
| cambio | ENTERTAINMENT | Netflix | proyectado $329.00 → $369.00; Norma $329.00 → $369.00 |
| cambio | HOUSING | Hipoteca | proyectado $4,000.00 → $4,500.00; Norma $4,000.00 → $4,500.00 |
| cambio | SEGUROS_MEDICOS | Pau, David, Agus | proyectado $4,500.00 → $3,000.00; Norma $4,500.00 → $3,000.00 |
| cambio | SEGUROS_MEDICOS | Santi | proyectado $700.00 → $500.00; Norma $700.00 → $500.00 |
| baja | TRANSPORTATION | Cochecito | era $1,400.00 |

**Celda por celda**

| Celda | Qué es | Viejo | Nuevo |
|---|---|---|---|
| B2 | cabecera | PRESUPUESTO QUINCENAL DEL 1 AL 15 DE  JULIO DE 2026 | PRESUPUESTO QUINCENAL DEL 1 AL 15 DE AGOSTO DE 2026 |
| J4 | cabecera | 8,553 | 33,613 |
| J6 | cabecera (Total quincenal) | 48,644 | 18,524 |
| AB7 | cabecera | 8,553 | 33,613 |
| J8 | cabecera | 8,553 | 33,613 |
| E10 | cabecera (Total) | 57,197 | 52,137 |
| C13 | HOUSING · Hipoteca · proyectado | 4,000 | 4,500 |
| D13 | HOUSING · Hipoteca · Norma | 4,000 | 4,500 |
| H15 | ENTERTAINMENT · Netflix · proyectado | 329 | 369 |
| I15 | ENTERTAINMENT · Netflix · Norma | 329 | 369 |
| L15 | fuera de bloque | (vacía) | 444 |
| M15 | fuera de bloque | (vacía) | 980 |
| L16 | fuera de bloque | (vacía) | 199 |
| L17 | fuera de bloque | (vacía) | 498 |
| L18 | fuera de bloque | (vacía) | 369 |
| L19 | fuera de bloque | (vacía) | 99 |
| L20 | fuera de bloque | (vacía) | 1,500 |
| M20 | fuera de bloque | (vacía) | 980 |
| H21 | ENTERTAINMENT · Didi tarjeta · proyectado | 6,500 | 4,000 |
| I21 | ENTERTAINMENT · Didi tarjeta · Norma | 6,500 | 4,000 |
| L21 | fuera de bloque | (vacía) | 39 |
| H22 | ENTERTAINMENT · Subtotal · proyectado | 9,796 | 7,336 |
| I22 | ENTERTAINMENT · Subtotal · Norma | 9,796 | 7,336 |
| L22 | fuera de bloque | (vacía) | 3,148 |
| C23 | HOUSING · Subtotal · proyectado | 5,141 | 5,641 |
| D23 | HOUSING · Subtotal · Norma | 5,141 | 5,641 |
| L23 | fuera de bloque | (vacía) | 3,010 |
| L25 | fuera de bloque | 0 | 4,128 |
| M27 | fuera de bloque | (vacía) | Agus |
| C28 | TRANSPORTATION · Cochecito · proyectado | 1,400 | 0 |
| D28 | TRANSPORTATION · Cochecito · Norma | 1,400 | 0 |
| M28 | fuera de bloque | (vacía) | 379 |
| C34 | TRANSPORTATION · Subtotal · proyectado | 2,700 | 1,300 |
| D34 | TRANSPORTATION · Subtotal · Norma | 2,700 | 1,300 |
| L36 | fuera de bloque | Santiago inscripcion | (vacía) |
| I37 | TRANSFERENCIAS_FAMILIARES · David inscripcion · Norma | 20,000 | 0 |
| L37 | fuera de bloque | 5,000 | (vacía) |
| C38 | SEGUROS_MEDICOS · Pau, David, Agus · proyectado | 4,500 | 3,000 |
| D38 | SEGUROS_MEDICOS · Pau, David, Agus · Norma | 4,500 | 3,000 |
| L38 | fuera de bloque | 1,000 | (vacía) |
| C39 | SEGUROS_MEDICOS · Santi · proyectado | 700 | 500 |
| D39 | SEGUROS_MEDICOS · Santi · Norma | 700 | 500 |
| L39 | fuera de bloque | 2,000 | (vacía) |
| N39 | fuera de bloque | 8,800 | 16,800 |
| I40 | TRANSFERENCIAS_FAMILIARES · Subtotal · Norma | 28,580 | 8,580 |
| L40 | fuera de bloque | 8,000 | (vacía) |
| C42 | SEGUROS_MEDICOS · Subtotal · proyectado | 8,200 | 6,500 |
| D42 | SEGUROS_MEDICOS · Subtotal · Norma | 8,200 | 6,500 |
| J61 | notas | 76,447 | 51,387 |
| J63 | notas | 8,553 | 33,613 |

#### Quincena 16 al 30 Agosto

Total proyectado que lee el ETL: $42,505.20 → $39,405.20 (-$3,100.00).

**Líneas presupuestales (lo que lee el ETL)**

| Tipo | Sección | Concepto | Detalle |
|---|---|---|---|
| cambio | FOOD | Limpieza | proyectado $3,000.00 → $2,500.00; Norma $3,000.00 → $2,500.00 |
| cambio | HOUSING | Hipoteca | proyectado $4,000.00 → $4,500.00; Norma $4,000.00 → $4,500.00 |
| cambio | SEGUROS_MEDICOS | Pau, David, Agus | proyectado $4,500.00 → $3,000.00; Norma $4,500.00 → $3,000.00 |
| cambio | SEGUROS_MEDICOS | Santi | proyectado $700.00 → $500.00; Norma $700.00 → $500.00 |
| baja | TRANSPORTATION | Gasolina Chochecito | era $1,400.00 |

**Celda por celda**

| Celda | Qué es | Viejo | Nuevo |
|---|---|---|---|
| K4 | cabecera | 42,494.80 | 35,594.80 |
| E5 | cabecera (Norma) | 85,000 | 75,000 |
| E6 | cabecera (Total monthly income) | 85,000 | 75,000 |
| K6 | cabecera (Total monthly income) | 42,505.20 | 39,405.20 |
| K8 | cabecera | 42,494.80 | 35,594.80 |
| E10 | cabecera (Total) | 42,505.20 | 39,405.20 |
| S12 | fuera de bloque | 1,600 | (vacía) |
| C13 | HOUSING · Hipoteca · proyectado | 4,000 | 4,500 |
| D13 | HOUSING · Hipoteca · Norma | 4,000 | 4,500 |
| O13 | fuera de bloque | (vacía) | 1,300 |
| S13 | fuera de bloque | 899 | (vacía) |
| O14 | fuera de bloque | (vacía) | 980 |
| S14 | fuera de bloque | 2,499 | (vacía) |
| R18 | fuera de bloque | 296.30 | (vacía) |
| N20 | fuera de bloque | 1,046.96 | 645.30 |
| O20 | fuera de bloque | (vacía) | 2,280 |
| N21 | fuera de bloque | 1,692.26 | 2,925.30 |
| C23 | HOUSING · Subtotal · proyectado | 6,419 | 6,919 |
| D23 | HOUSING · Subtotal · Norma | 6,419 | 6,919 |
| C28 | TRANSPORTATION · Gasolina Chochecito · proyectado | 1,400 | 0 |
| D28 | TRANSPORTATION · Gasolina Chochecito · Norma | 1,400 | 0 |
| C34 | TRANSPORTATION · Subtotal · proyectado | 2,700 | 1,300 |
| D34 | TRANSPORTATION · Subtotal · Norma | 2,700 | 1,300 |
| C39 | SEGUROS_MEDICOS · Pau, David, Agus · proyectado | 4,500 | 3,000 |
| D39 | SEGUROS_MEDICOS · Pau, David, Agus · Norma | 4,500 | 3,000 |
| C40 | SEGUROS_MEDICOS · Santi · proyectado | 700 | 500 |
| D40 | SEGUROS_MEDICOS · Santi · Norma | 700 | 500 |
| C41 | SEGUROS_MEDICOS · Subtotal · proyectado | 8,200 | 6,500 |
| D41 | SEGUROS_MEDICOS · Subtotal · Norma | 8,200 | 6,500 |
| C46 | FOOD · Limpieza · proyectado | 3,000 | 2,500 |
| D46 | FOOD · Limpieza · Norma | 3,000 | 2,500 |
| C47 | FOOD · Subtotal · proyectado | 10,500 | 10,000 |
| D47 | FOOD · Subtotal · Norma | 10,500 | 10,000 |
| K63 | notas | 42,505.20 | 39,405.20 |
| K67 | notas | 42,494.80 | 35,594.80 |
| H72 | notas | BANAMEX | (vacía) |
| I72 | notas | 20,808 | (vacía) |
| H73 | notas | BBVA | (vacía) |
| I73 | notas | 630.28 | (vacía) |
| K73 | notas | 22,539.28 | (vacía) |
| H74 | notas | Coppel | (vacía) |
| I74 | notas | 2,000 | (vacía) |
| K74 | notas | 20,539.28 | (vacía) |
| I75 | notas | 23,438.28 | (vacía) |
| K80 | notas | 4,787.54 | (vacía) |
| I81 | notas | 10,239.54 | (vacía) |
| K81 | notas | 2,019.29 | (vacía) |
| I82 | notas | 6,739.54 | (vacía) |
| I83 | notas | 2,768.25 | (vacía) |
| I88 | notas | AMAZON | (vacía) |
| J88 | notas | 732 | (vacía) |
| B91 | notas | Quincena 1 al 15 noviembre | Quincena 16 al 30 de agosto |
| C91 | notas | (vacía) | 33,300 |

#### Hoja nueva: Quincena 1 al 15 Septiembre

25 líneas, total proyectado $52,137.00.

Contra «Quincena 1al 15 Agosto» (misma mitad, 25 líneas, total $52,137.00): 25 líneas idénticas en concepto, sección y monto.

| Sección | Concepto | Proyectado | Norma | Benjamín |
|---|---|---:|---:|---:|
| HOUSING | Hipoteca | $4,500.00 | $4,500.00 | (vacía) |
| HOUSING | Telefono Pau y David | $444.00 | $444.00 | (vacía) |
| HOUSING | Telefono Santi | $199.00 | $199.00 | (vacía) |
| HOUSING | Telefono Norma | $498.00 | $498.00 | (vacía) |
| ENTERTAINMENT | Youtube | $179.00 | $179.00 | (vacía) |
| ENTERTAINMENT | Netflix | $369.00 | $369.00 | (vacía) |
| ENTERTAINMENT | Google nest | $200.00 | $200.00 | (vacía) |
| ENTERTAINMENT | Prime | $99.00 | $99.00 | (vacía) |
| ENTERTAINMENT | Google | $39.00 | $39.00 | (vacía) |
| ENTERTAINMENT | Hawaiano | $450.00 | $450.00 | (vacía) |
| ENTERTAINMENT | Klar | $2,000.00 | $2,000.00 | (vacía) |
| ENTERTAINMENT | Didi tarjeta | $4,000.00 | $4,000.00 | (vacía) |
| TRANSPORTATION | Gasolina | $1,300.00 | $1,300.00 | (vacía) |
| LOANS | Banamex Clasica | $1,500.00 | $1,500.00 | (vacía) |
| LOANS | Sears | $1,000.00 | $1,000.00 | (vacía) |
| LOANS | Walmart | $7,500.00 | $7,500.00 | (vacía) |
| LOANS | mercado pago | $1,300.00 | $550.00 | $750.00 |
| TRANSFERENCIAS_FAMILIARES | Pau | $8,580.00 | $8,580.00 | (vacía) |
| SEGUROS_MEDICOS | Norma | $3,000.00 | $3,000.00 | (vacía) |
| SEGUROS_MEDICOS | Pau, David, Agus | $3,000.00 | $3,000.00 | (vacía) |
| SEGUROS_MEDICOS | Santi | $500.00 | $500.00 | (vacía) |
| FOOD | Comida | $5,000.00 | $5,000.00 | (vacía) |
| FOOD | Despensa | $2,500.00 | $2,500.00 | (vacía) |
| FOOD | Limpieza | $3,000.00 | $3,000.00 | (vacía) |
| PETS | Comida Gatas | $980.00 | $980.00 | (vacía) |

---

## 3. Ingresos

| Quincena | Benjamín (E4) | Norma (E5) | Ahorro empresa (E9) |
|---|---|---:|---:|
| 1 ene a 15 jun 2026 (11 hojas) | vacía | $60,000 | $1,000 en las primeras mitades, 0 en las segundas |
| 16 al 30 jun 2026 | vacía | $75,000 | 0 |
| 1 al 15 jul 2026 | vacía | $75,000 | **vacía** |
| 16 al 31 jul 2026 | vacía | $75,000 | 0 |
| 1 al 15 ago 2026 | vacía | $85,000 | $1,000 |
| 16 al 31 ago 2026 | vacía | **$75,000** (era $85,000 en el (1)) | 0 |
| 1 al 15 sep 2026 | vacía | $85,000 | $1,000 |

- El ETL lee E5 tal cual, así que la base nueva tiene ingresos de $75,000, $85,000, $75,000 y $85,000 para las cuatro quincenas nuevas (+$320,000). La decisión global de 2026-07 ("$85,000 desde agosto") choca con la hoja del 16 al 31 de agosto. Pregunta 9.
- **Benjamín:** E4 vacía en todas las hojas desde octubre de 2025, coherente con la regla vigente. Pero en la columna "Benjamín" de los renglones sí aparecen pagos después de esa fecha: Mercado Pago ($822.65 el 1 al 15 de junio; $750 el 1 al 15 de agosto y el 1 al 15 de septiembre), Youtube $179 y Hawaiano $100 (febrero y marzo), $179 junto a Kigo en ocho quincenas, Didi ~$269 (diciembre y enero) y, en la sección PERSONAL, $1,300 de la línea "Norma" (octubre y noviembre de 2025), $2,500 de la de Benji y $500 de la de Santi (octubre de 2025). El ETL los ignora por la regla confirmada ("Norma paga todo desde octubre de 2025, salvo Spotify"). Pregunta 8, con esta evidencia.
- El Ahorro empresa acumulado pasa de $24,000 a $26,000 (agosto y septiembre).

---

## 4. ETL extendido a las quincenas nuevas

### 4.1 Qué hubo que cambiar

Nada de la lógica. El ETL ya está dirigido por fecha: con `BUDGET_TODAY` en septiembre ingiere las hojas nuevas, los nombres sin año ("Quincena 1 al 15 Septiembre") heredan 2026 del cursor de año, ningún concepto se repite en la misma sección y hoja (no hay colisión de ids) y el modelo de futuro se respeta solo (siembra hasta la quincena activa, cero PLANNED, futuro por plantillas). Los cambios de la rama son de herramientas y de reporte:

- `scripts/verify_db.py` acepta `--db`. Antes tenía cableada la ruta absoluta del asset del checkout de G:, y correrlo desde otro worktree lo modificaba.
- `excel_to_room_etl.py` imprime la fecha de corte (y si viene de `BUDGET_TODAY`) y las quincenas por estado; el docstring deja de fijar 33 quincenas. La base que produce no cambia.
- `scripts/etl/diff_excel.py`, `scripts/etl/seed_report.py` y el modo `--new-only` de `generate_questionnaire.py`, nuevos (§8).

### 4.2 Prueba de regresión

ETL con cada Excel y `BUDGET_TODAY=2026-07-07`, después `verify_db.py --db`, después `seed_report.py --golden seed/budget_database.golden.db`: **las dos bases salen idénticas a la golden** en once de las doce tablas (34 quincenas, 51 ingresos, 834 gastos, 4,038 atribuciones, 32 plantillas, 2 planes MSI, ignorando solo `created_at` y `closed_at`, que dependen del momento de la corrida). En la duodécima, `loan`, la única diferencia es la nota del préstamo "Deben", donde la golden aún guarda la raya larga que el código del ETL dejó de escribir en la Fase 7 (ahora dice "Deben $3,600: Medicina + rotafolio"). Se corrige sola al regenerar la base definitiva.

### 4.3 Corrida principal: `BUDGET_TODAY=2026-09-15`

El último día que cubre el archivo. La fecha definitiva será la del corte acordado con Norma, con su Excel final.

| Dato | Golden | Corrida 2026-09-15 |
|---|---:|---:|
| Quincenas (CLOSED / ACTIVE / PROVISIONED) | 34 (33 / 1 / 0) | **38 (37 / 1 / 0)** |
| Quincena ACTIVE | 1 al 15 jul 2026 | 1 al 15 sep 2026 |
| Gastos (todos POSTED) | 834 | **942** |
| Total de gastos | $1,919,956.73 | **$2,173,739.13** |
| Atribuciones | 4,038 | 4,567 |
| Ingresos | 51 ($2,849,000) | **55 ($3,169,000)** |
| Plantillas de recurrencia | 32 | 32 (todas recalculadas) |
| Planes MSI | 2 | 2 (sin cambio) |
| Préstamos / miembros / categorías / carteras | 2 / 10 / 88 / 12 | 2 / 10 / 88 / 12 |
| Meta Ahorro Empresa (current) | $24,000 | $26,000 |

Invariantes de `seed_report.py`, todos en verde: bps = 10000 por gasto y rol; todo gasto con beneficiario y pagador; `foreign_key_check` limpio; `user_version = 1`; una sola ACTIVE; cero PLANNED; nada posterior al 15 de septiembre; pagador Norma al 100 % desde octubre de 2025 salvo Spotify; huecos internos iguales a los deliberados (2025-02-SECOND y 2025-11-FIRST; 2025-01-FIRST queda antes de la primera hoja).

| Quincena | Golden | Corrida 2026-09-15 |
|---|---|---|
| 1 al 15 jul 2026 | ACTIVE, 16 gastos, $17,223.00 | CLOSED, 31 gastos, $72,070.00 |
| 16 al 31 jul 2026 | no existía | CLOSED, 21 gastos, $55,256.20, ingreso $75,000 |
| 1 al 15 ago 2026 | no existía | CLOSED, 26 gastos, $52,137.00, ingreso $85,000 |
| 16 al 31 ago 2026 | no existía | CLOSED, 20 gastos, $39,405.20, ingreso $75,000 |
| 1 al 15 sep 2026 | no existía | ACTIVE, 26 gastos, $52,137.00, ingreso $85,000 |

Las otras 33 quincenas salen idénticas. Ningún gasto, ingreso ni atribución de la golden cambia o desaparece: todo es alta (108 gastos, 529 atribuciones, 4 ingresos, 4 quincenas). Las del 1 al 15 de agosto y de septiembre tienen 26 gastos con 25 líneas porque "Telefono Pau y David" se parte en dos.

### 4.4 Sensibilidad: `BUDGET_TODAY=2026-09-05`

El día en que Norma entregó el archivo. Igual que la anterior salvo la quincena activa, que solo siembra 10 de sus 26 gastos ($6,527) porque el ETL reparte las fechas a lo largo de la quincena y solo persiste lo ya ocurrido: 926 gastos y 4,488 atribuciones. El resto lo pondrían las plantillas en la app. Es el comportamiento que tendrá la base definitiva si el corte cae a media quincena.

### 4.5 Clasificación de los 108 gastos nuevos

Todos los clasifica una regla explícita de `attribution_rules.json` (§5). Agrupados por cómo quedaron:

| Concepto | Categoría | Cartera | Beneficiarios | Gastos | Total | Quincenas |
|---|---|---|---|---:|---:|---|
| HBO | ENTERTAINMENT.HBO | Mercado Pago | los 6 | 2 | $334.60 | 16–31 jul, 16–31 ago |
| Suscripción Nivel 6 | ENTERTAINMENT.MELI_PLUS | Mercado Pago | los 6 | 2 | $259.80 | 16–31 jul, 16–31 ago |
| Netflix | ENTERTAINMENT.NETFLIX | Banamex Clásica | los 6 | 2 | $738.00 | 1–15 ago, 1–15 sep |
| Prime | ENTERTAINMENT.PRIME | BBVA | los 6 | 2 | $198.00 | 1–15 ago, 1–15 sep |
| YouTube | ENTERTAINMENT.YOUTUBE_PREMIUM | Efectivo | los 6 | 2 | $358.00 | 1–15 ago, 1–15 sep |
| Escuela David | ESCUELA.DAVID | Efectivo | David | 1 | $18,300.00 | 16–31 jul |
| Escuela Normita | ESCUELA.PAU | Efectivo | Normita | 5 | $19,754.00 | 1–15 jul, 16–31 jul, 1–15 ago, 16–31 ago, 1–15 sep |
| Escuela Santiago | ESCUELA.SANTIAGO | Efectivo | Santiago | 3 | $25,100.00 | 1–15 jul, 16–31 jul, 16–31 ago |
| Comida | FOOD.COMIDA | Efectivo | los 6 | 5 | $25,000.00 | 1–15 jul, 16–31 jul, 1–15 ago, 16–31 ago, 1–15 sep |
| Despensa | FOOD.DESPENSA | Efectivo | los 6 | 5 | $12,500.00 | 1–15 jul, 16–31 jul, 1–15 ago, 16–31 ago, 1–15 sep |
| Limpieza | FOOD.LIMPIEZA | Efectivo | los 6 | 5 | $13,500.00 | 1–15 jul, 16–31 jul, 1–15 ago, 16–31 ago, 1–15 sep |
| Agua | HOUSING.AGUA | Efectivo | los 6 | 2 | $440.00 | 16–31 jul, 16–31 ago |
| Electricidad | HOUSING.ELECTRICIDAD | Efectivo | los 6 | 2 | $2,600.00 | 16–31 jul, 16–31 ago |
| Hipoteca | HOUSING.HIPOTECA | Efectivo | los 6 | 4 | $18,000.00 | 16–31 jul, 1–15 ago, 16–31 ago, 1–15 sep |
| Internet | HOUSING.INTERNET | BBVA | los 6 | 2 | $1,798.00 | 16–31 jul, 16–31 ago |
| Banamex Clásica | LOANS.BANAMEX_CC | Banamex Débito | los 6 | 2 | $3,000.00 | 1–15 ago, 1–15 sep |
| Coppel | LOANS.COPPEL | Efectivo | los 6 | 2 | $7,320.00 | 16–31 jul, 16–31 ago |
| DiDi | LOANS.DIDI | Efectivo | los 6 | 2 | $8,000.00 | 1–15 ago, 1–15 sep |
| Klar | LOANS.KLAR | Banamex Débito | los 6 | 2 | $4,000.00 | 1–15 ago, 1–15 sep |
| Liverpool | LOANS.LIVERPOOL | Efectivo | los 6 | 2 | $4,000.00 | 16–31 jul, 16–31 ago |
| Mercado Pago | LOANS.MERCADO_PAGO | BBVA | los 6 | 3 | $3,973.00 | 1–15 jul, 1–15 ago, 1–15 sep |
| Sears | LOANS.SEARS | Banamex Débito | Norma | 2 | $2,000.00 | 1–15 ago, 1–15 sep |
| Walmart | LOANS.WALMART | Efectivo | los 6 | 3 | $22,500.00 | 1–15 jul, 1–15 ago, 1–15 sep |
| Adobe | OTHER.ADOBE | Efectivo | Norma | 1 | $249.00 | 16–31 ago |
| Google Home | OTHER.GOOGLE_HOME | Efectivo | los 6 | 2 | $400.00 | 1–15 ago, 1–15 sep |
| Google | OTHER.GOOGLE_ONE | Klar | Norma | 2 | $78.00 | 1–15 ago, 1–15 sep |
| Hawaiano | OTHER.HAWAIANO | Efectivo | Normita | 2 | $900.00 | 1–15 ago, 1–15 sep |
| Kigo | OTHER.KIGO | Banamex Débito | los 6 | 2 | $200.00 | 16–31 jul, 16–31 ago |
| Teléfono David | OTHER.TELEFONO | BBVA | David | 2 | $444.00 | 1–15 ago, 1–15 sep |
| Teléfono Norma | OTHER.TELEFONO | BBVA | Norma | 2 | $996.00 | 1–15 ago, 1–15 sep |
| Teléfono Normita | OTHER.TELEFONO | BBVA | Normita | 2 | $444.00 | 1–15 ago, 1–15 sep |
| Teléfono Santi | OTHER.TELEFONO | BBVA | Santiago | 2 | $398.00 | 1–15 ago, 1–15 sep |
| Comida Gatas | PETS.COMIDA | Efectivo | los 6 | 5 | $4,900.00 | 1–15 jul, 16–31 jul, 1–15 ago, 16–31 ago, 1–15 sep |
| Bernardo | SERVICIOS_EXTERNOS.BERNARDO | Efectivo | Benjamín | 1 | $5,000.00 | 1–15 jul |
| Contador | SERVICIOS_EXTERNOS.CONTADOR | Efectivo | Benjamín, Norma | 1 | $2,000.00 | 1–15 jul |
| Normita, David y Agus | TRANSFERENCIAS_FAMILIARES | Efectivo | Normita, David, Agustín | 5 | $15,000.00 | 1–15 jul, 16–31 jul, 1–15 ago, 16–31 ago, 1–15 sep |
| Norma | TRANSFERENCIAS_FAMILIARES.NORMA | Efectivo | Norma | 5 | $14,500.00 | 1–15 jul, 16–31 jul, 1–15 ago, 16–31 ago, 1–15 sep |
| Normita | TRANSFERENCIAS_FAMILIARES.PAU | Efectivo | Normita | 1 | $500.00 | 1–15 jul |
| Santi | TRANSFERENCIAS_FAMILIARES.SANTIAGO | Efectivo | Santiago | 5 | $2,500.00 | 1–15 jul, 16–31 jul, 1–15 ago, 16–31 ago, 1–15 sep |
| Changan | TRANSPORTATION | Efectivo | Norma | 1 | $5,000.00 | 1–15 jul |
| Gasolina | TRANSPORTATION.GASOLINA | Efectivo | Norma | 2 | $2,600.00 | 1–15 ago, 1–15 sep |
| Gasolina Camioneta | TRANSPORTATION.GASOLINA | Efectivo | Norma | 2 | $2,600.00 | 16–31 jul, 16–31 ago |
| Gasolina Cochecito | TRANSPORTATION.GASOLINA | Efectivo | Agustín, David, Normita | 1 | $1,400.00 | 16–31 jul |
| **Total** | | | | **108** | **$253,782.40** | |

### 4.6 Plantillas y planes MSI

**Las plantillas siguen la mediana del histórico, no el monto vigente.** Con la historia nueva, 12 de las 32 tienen un monto distinto del último gasto real: Hipoteca $4,000 (el vigente es $4,500), Netflix $329 ($369), Escuela David $16,262 ($18,300), Electricidad $1,000 ($1,300), HBO $99 ($167.30), Escuela Normita $7,780 ($8,580), Liverpool $2,964 ($2,000), Mercado Pago $3,746.08 ($1,300), Sears $1,483 ($1,000), Agua $214 ($220), Suscripción Nivel 6 $129 ($129.90), y DiDi, que es quincenal de $1,046.96 en la plantilla cuando hoy es un pago de $4,000 en la primera mitad. Esto ya pasaba en la golden; no se cambió para no mover la regresión. **Decisión para Agustín** antes de la base definitiva: monto de la plantilla = el del gasto más reciente (igual que ya se toma de ahí la categoría, la cartera y los beneficiarios) o mantener la mediana.

**Plantillas dormidas.** Cinco siguen activas aunque su concepto dejó de aparecer en el Excel: Spotify y Teléfono Benji (último en septiembre de 2025), Psicóloga (febrero de 2026), Préstamo Omar y Coursera (mayo de 2026). La app materializaría cargos PLANNED que ya no existen. Pregunta 13 de la solicitud; si Norma confirma, se sacan de `RECURRENCE_TEMPLATE_CONCEPTS` o se siembran inactivas.

**Planes MSI abiertos.** "Mercado Libre 12 MSI" queda en 8 de 12 (último pago en diciembre de 2025) y "Buró de Crédito 3 pagos" en 2 de 3 (los dos en octubre de 2025), ambos ACTIVE. El Excel no trae pagos posteriores, así que `InstallmentMaterializer` proyectaría las cuotas restantes como PLANNED. Pregunta 12.

**Candidatos a plantilla nueva:** ninguno, porque no hay conceptos nuevos.

---

## 5. Conceptos nuevos sin regla

Generado con `generate_questionnaire.py --new-only` (comando en §8), que juzga cada línea con las funciones reales del ETL (`match_rule`, `resolve_*`, `canonicalize_concept`).

| Medida | Resultado |
|---|---:|
| Conceptos (concepto normalizado + sección) en el Excel (2) | 94 |
| Nuevos contra el Excel (1) | **0** |
| Nuevos sin regla explícita (van a la lista para Norma) | **0** |
| Nuevos atrapados por una regla existente | 0 |
| Ya conocidos que el ETL clasifica solo por heurística | **0** |

Todo lo que aparece de julio a septiembre ya existía en el (1), incluidas las líneas del 16 de julio al 31 de agosto, que se clasificaron en 2026-07 aunque la golden no las sembró. Para validar que la herramienta no da cero por defecto se corrió también con un archivo de reglas vacío: los 94 conceptos caen en "solo heurística", como debe ser.

El anexo de la solicitud lo dice en lenguaje llano. Cuando llegue el Excel final hay que repetir el comando con el (2) como base: cualquier concepto que Norma agregue después del 5 de septiembre saldrá en esa lista.

El criterio de salida de la fase ("cero conceptos sin regla") ya se cumple con este archivo. El otro criterio, cero reglas de baja confianza, no: ver §6.

---

## 6. Inventario de reglas y datos de baja confianza

Cada fila dice qué decide hoy el código, quién lo decidió y qué pregunta de `seed/SOLICITUD_A_NORMA_2026-09.md` lo cierra.

| # | Regla o dato | Decisión vigente en el código | Origen | Pregunta | Qué cambia al responder |
|---:|---|---|---|---:|---|
| 1 | `pau_raul` ("Pau Raul", $500, 1 al 15 jul 2026) | TRANSFERENCIAS_FAMILIARES.PAU, Normita 100 %, efectivo; concepto canónico "Normita" | Ronda 1 (baja confianza) y Ronda 2 de Agustín | 1 | **Incoherencia:** el comentario del JSON dice "se desconoce quién es Raúl" y la nota canónica del ETL dice "Raúl es su novio". La respuesta fija la regla y se borra el comentario de baja confianza. |
| 2 | `berna_bernardo` ("Berna" $3,100 jun, "Bernardo" $5,000 jul) | SERVICIOS_EXTERNOS.BERNARDO, Benjamín 100 % | Ronda 2 de Agustín | 2 | **Incoherencia:** la memoria de la semilla lo describe como préstamo a un amigo de Benjamín; si es préstamo, no es un servicio (sería un préstamo por cobrar o una transferencia). |
| 3 | `marco_y_omar` ($2,120, jun 2026) | OTHER raíz a propósito, Norma 100 % | Ronda 2 de Agustín | 3 | Es la única regla que deja una línea en OTHER; el objetivo de la fase es que ninguna quede ahí. Si es abono al préstamo de Omar, va con "Préstamo Omar". |
| 4 | `cochecito_other` ("Cochecito" $8,000 en OTHERS, jun 2026) | TRANSPORTATION.MAINTENANCE, Agustín/David/Normita | Ronda 1 (baja confianza) | 4 | Categoría y beneficiarios según la respuesta. |
| 5 | `changan_gifts` ($5,000 en GIFTS, 1 al 15 jul 2026) | TRANSPORTATION raíz, Norma 100 % | Ronda 2 de Agustín | 5 | Si fue regalo, GIFTS; si fue enganche o servicio, una subcategoría de transporte. |
| 6 | Préstamo por cobrar "Deben" ($3,600, saldo $1,900) | Deudor "Por identificar" (`deudor_pendiente`) | Ronda 3 de Agustín | 6 | Se renombra el miembro externo en el ETL (`MEMBERS`). |
| 7 | Dudas de los estados de cuenta (`app/src/main/assets/seed_statements.json`) | CENEVAL $3,245 en CUSTOM.EXAMENES para Agustín y Santiago; CirculoDerm $1,730 en OTHER.SALUD para los 6; Ropa Universitarios (3 cargos) en CUSTOM.ROPA para David; MerPago*WIM $249 (dos meses) en OTHER para los 6; Google CR $59 en OTHER.GOOGLE_ONE para Agustín (choca con la regla "el software lo paga Norma"); unas 20 líneas (~$7,830) de Amazon, Temu y Mercado Libre repartidas al hogar | Auditoría 2026-07-08 | 7 | Se corrigen las líneas de la versión 3 de `seed_statements.json` (punto 4 de la fase). |
| 8 | Regla de pagador (`PAYER_RULE_CUTOVER`, 2025-10-01) | Norma 100 % en todo, salvo Spotify (Benjamín) | Agustín, 2026-07-07 | 8 | El Excel muestra pagos de Benjamín después del corte (§3). Si siguen siendo reales, la regla necesita excepciones (Mercado Pago, por ejemplo). |
| 9 | Sueldo de Norma | Se lee E5 de cada hoja: $75,000 el 16 al 31 de agosto | Excel | 9 | Si el bueno es $85,000, se corrige el ingreso de esa quincena en el ETL o Norma corrige la celda en su Excel final. |
| 10 | Hoja del 1 al 15 de julio | Se siembran sus 31 líneas como ejercidas | Excel | 10 | Si cambió algo, entra con el Excel final. |
| 11 | Hoja del 1 al 15 de septiembre | Se siembran sus 25 líneas como ejercidas (copia de agosto) | Excel | 11 | Si es plantilla, el corte define qué entra; nada de esa hoja debe nacer POSTED sin confirmarse. |
| 12 | Planes MSI de Mercado Libre (8 de 12) y Buró (2 de 3) | ACTIVE | ETL | 12 | Si están liquidados, `PAID_OFF` (el ETL ya escribe ese valor cuando cuenta todos los pagos). |
| 13 | Plantillas dormidas (Spotify, Teléfono Benji, Psicóloga, Préstamo Omar, Coursera) | Activas | ETL | 13 | Se quitan o se siembran inactivas. |

Cambios a la solicitud que salen de este reporte: la pregunta 1 decía "2025" y el cargo es de julio de 2026; la 2 ya no presupone que Bernardo da un servicio; la 8 y la 9 citan la evidencia de §3; la tabla de estados de cuenta pide desde el mes siguiente al último que tenemos de cada emisor; las preguntas 10 a 13 son nuevas; el anexo dice que no hay conceptos nuevos; y la fecha queda en blanco (`2026-__-__`) para que Agustín la anote al enviarla.

Las reglas que la Ronda 2 cerró por decisión de Agustín (Contador, Caja Chica, Klar, los teléfonos por persona, "los 6" en servicios compartidos, Sears solo para Norma) no se listan: no son de baja confianza.

---

## 7. Lo que queda para cuando respondan (y la Fase 8 esté cerrada)

1. Incorporar las respuestas a `attribution_rules.json` y a `CANONICAL_CONCEPT_RULES`; borrar los dos comentarios "BAJA CONFIANZA"; renombrar al deudor (punto 3 de la fase).
2. Correr `diff_excel.py` del (2) contra el Excel final y `generate_questionnaire.py --new-only` con el (2) como base; si sale algún concepto, una segunda ronda corta con Norma.
3. Extraer y clasificar los estados de cuenta nuevos y llevar `seed_statements.json` a la versión 3 (punto 4).
4. Decidir el monto de las plantillas (mediana o el último) y las plantillas dormidas (§4.6).
5. Regenerar con `BUDGET_TODAY` = día del corte, `verify_db.py --db`, `seed_report.py --golden` en verde, promover la golden con su `.sha256` y `check_seed_integrity.sh` (punto 5); instalación fresca en FinanceFold (punto 6); documentos y memoria (punto 7).

---

## 8. Cómo reproducir

Desde la raíz del checkout; `$X1` y `$X2` son las rutas de los dos Excel y `$TMP` una carpeta fuera del repositorio.

```bash
# Diff del Excel
PYTHONUTF8=1 python scripts/etl/diff_excel.py --old "$X1" --new "$X2" --output "$TMP/diff.md"

# Regresión contra la golden
PYTHONUTF8=1 BUDGET_TODAY=2026-07-07 python scripts/etl/excel_to_room_etl.py --excel "$X2" --output "$TMP/x2_2026-07-07.db"
python scripts/verify_db.py --db "$TMP/x2_2026-07-07.db"
PYTHONUTF8=1 python scripts/etl/seed_report.py --db "$TMP/x2_2026-07-07.db" --golden seed/budget_database.golden.db --today 2026-07-07

# Corrida principal
PYTHONUTF8=1 BUDGET_TODAY=2026-09-15 python scripts/etl/excel_to_room_etl.py --excel "$X2" --output "$TMP/x2_2026-09-15.db"
python scripts/verify_db.py --db "$TMP/x2_2026-09-15.db"
PYTHONUTF8=1 python scripts/etl/seed_report.py --db "$TMP/x2_2026-09-15.db" --golden seed/budget_database.golden.db --today 2026-09-15

# Conceptos nuevos sin regla y anexo para Norma
PYTHONUTF8=1 python scripts/etl/generate_questionnaire.py --new-only --excel "$X2" --baseline "$X1" --output "$TMP/conceptos.md" --anexo "$TMP/anexo.md"
```

Nunca se corre `verify_db.py` sin `--db` contra una base temporal: sin el argumento modifica el asset del checkout.
