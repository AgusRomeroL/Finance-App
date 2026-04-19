# Especificación Técnica: Aplicación de Presupuesto Quincenal

**Documento fundacional para desarrollo multiplataforma (Android Foldable · Wear OS · Web)**
**Derivado por ingeniería inversa de `presupuesto_2_5.xlsx`** · 33 hojas quincenales · Enero 2025 – Junio 2026
**Formato destinado a agentes de generación de código (vibe coding)**

---

## Índice

1. [Diagnóstico del Sistema Actual](#1-diagnóstico-del-sistema-actual)
2. [Modelo de Datos Propuesto](#2-modelo-de-datos-propuesto)
3. [Requerimientos Funcionales Core](#3-requerimientos-funcionales-core)
4. [Sistema de Atribución y Recurrencia](#4-sistema-de-atribución-y-recurrencia)
5. [Arquitectura de Analíticas](#5-arquitectura-de-analíticas)
6. [Áreas de Mejora Estructural](#6-áreas-de-mejora-estructural)
7. [Apéndice A: Diccionario de Datos Inferido](#apéndice-a-diccionario-de-datos-inferido)
8. [Apéndice B: Plantilla de Migración de Datos](#apéndice-b-plantilla-de-migración-de-datos)

---

## 1. Diagnóstico del Sistema Actual

### 1.1 Arquitectura general del libro Excel

El libro implementa un **ledger quincenal federado por hojas**. Cada hoja representa exactamente un período de 15 días de operación financiera del hogar. Existen 33 hojas cubriendo desde `16-31 ENE 2025` hasta `16-30 JUN 2026`. No existe una hoja maestra: toda agregación intertemporal requiere interpretación humana.

Dos **plantillas estructurales** coexisten:

| Plantilla | Cols | Filas | Uso | Pistas de identificación |
|---|---|---|---|---|
| **A — Quincena par (16-fin)** | 16–21 | 126–127 | Quincenas "de fin de mes" | Título `PRESUPUESTO QUINCENAL DEL 16 AL ...` · contiene `TAXES` en H35 · sub-bloques `AHORRO`/`FALTA GASTAR` |
| **B — Quincena impar (1-15)** | 30 | 118–120 | Quincenas "de principio de mes" | Contiene `SCHOOL` en G35 · bloque `PERSONAL CARE` posicionado distinto · extiende columnas hasta AD |

Ambas plantillas comparten el esqueleto analítico; divergen en columnas auxiliares, nombre de una categoría (`TAXES` ↔ `SCHOOL`) y en el bloque de balance bancario.

### 1.2 Anatomía vertical de una hoja

```
Filas 1–10   BLOQUE DE CABECERA (ingresos + dashboard de balance)
Filas 12–23  HOUSING / ENTERTAINMENT (tablas pareadas)
Filas 26–34  TRANSPORTATION / LOANS
Filas 36–42  PERSONAL (seguros) / TAXES (realmente: transferencias por persona)
Filas 43–47  FOOD / OTHERS (antes SAVINGS)
Filas 49–55  PETS / GIFTS
Filas 57–65  PERSONAL CARE / LEGAL
Filas 61–67  BLOQUE DE TOTALES (Total gasto quincenal · Sobra · Diferencia)
Filas 68–88  BLOQUE DE CONCILIACIÓN MANUAL (balances bancarios libres)
Filas 90–127 BLOQUE HISTÓRICO (Ahorro Efectivo por quincena · préstamos)
```

### 1.3 Bloque de cabecera (filas 4–10) — Dashboard quincenal

| Celda | Contenido | Fórmula/Valor típico | Significado operativo |
|---|---|---|---|
| `B2` | Título | `PRESUPUESTO QUINCENAL DEL 16 AL 31 DE ENERO 2025` | Ancla temporal textual (no fechas reales) |
| `B4/C4` | `SUELDOS · Benjamin` | `E4 = 45000` | Ingreso de Benjamin |
| `C5/E5` | `Norma` | `E5 = 60000` | Ingreso de Norma |
| `C6/E6` | `Total monthly income` | `=SUM(E4:E5)` | Ingreso quincenal agregado |
| `C9/E9` | `Ahorro empresa` | `E9 = 1000` | Deducción obligatoria de nómina (fondo de ahorro empresarial) |
| `C10/E10` | `Total` | `=C23 + Entertainment[Totals] + Transportation[Totals] + Loans[Totals] + Insurance[Totals] + Food[Totals] + Pets[Totals] + Gifts[Totals] + PersonalCare[Totals] + Taxes[Totals]` | Gasto presupuestado total |
| `H4/K4` | `BALANCE` | `=E6 - (K62 + E9)` | Ingreso − (Gasto real + Ahorro empresa) |
| `H6/K6` | `FALTA GASTAR` | `=E10 - K64` | Presupuesto aún no ejecutado |
| `H8/K8` | `AHORRO` | `=E6 - E10` | Ahorro implícito = Ingreso − Presupuesto total |

### 1.4 Bloque de totales (filas 62–67) — KPI principales

```
K62 · Total de gasto quincenal  (proyectado) = suma Projected Cost de todas las tablas
K64 · Total gastado actual       (ejecutado)  = suma de columnas Norma/Benjamin/Actual Cost
K66/K67 · TOTAL DIFERENCIA       (varianza)   = K62 - K64  (o E5 - K63 en versión reciente)
```

### 1.5 Tablas de categoría — Modelo de columnas

Las 10 categorías estables se instancian como **tablas nombradas de Excel** (structured references). Cada una emplea uno de **tres patrones de columnas**:

#### Patrón P1 — "Atribución por pagador"  
Aplica a: `HOUSING`, `ENTERTAINMENT`, `TRANSPORTATION`, `PERSONAL/INSURANCE`, `FOOD`, `PETS`, `TAXES`

| Col | Header | Semántica |
|---|---|---|
| 1 | `<Concepto>` | Nombre del gasto (ej. `Hipoteca`, `Netflix`) |
| 2 | `Projected Cost` | Monto presupuestado (input) |
| 3 | `Norma` | Porción pagada por Norma (input) |
| 4 | `Benjamin` | Porción pagada por Benjamin (**fórmula implícita**: `= Projected − Norma`) |

Las columnas `Norma` y `Benjamin` son **columnas de atribución de pagador**, no beneficiarios. El pagador queda capturado automáticamente con una regla de complemento.

#### Patrón P2 — "Seguimiento proyectado vs. real"  
Aplica a: `LOANS`, `SAVINGS`, `GIFTS`, `LEGAL`, `PERSONAL CARE`, `OTHERS`

| Col | Header | Semántica |
|---|---|---|
| 1 | `<Concepto>` | Nombre del compromiso |
| 2 | `Projected Cost` | Plan |
| 3 | `Actual Cost` | Ejecutado |
| 4 | `Difference` | `= Projected − Actual` (fórmula) |

#### Patrón P3 — "Transferencia por persona" (caso TAXES mal etiquetado)
La categoría `TAXES` **no contiene impuestos**. Contiene transferencias a miembros del hogar (mesadas, pensiones, colegiaturas):

```
H36 David       I36=15000  J36=15000 (Norma paga)
H37 Santiago    I37=2000
H38 Pau         I38=0
H39 Calculadora de santi
```

La categoría debe renombrarse a `TRANSFERENCIAS_MIEMBROS` o `APOYOS_FAMILIARES` en el modelo nuevo.

### 1.6 Convenciones idiosincráticas descubiertas

#### Beneficiario embebido en el nombre del concepto

El archivo codifica al beneficiario de múltiples formas dentro de la cadena del concepto:

| Patrón textual | Beneficiario inferido | Ejemplos |
|---|---|---|
| `<Concepto> <Nombre>` | Un solo beneficiario | `Telefono Norma`, `Telefono Benji`, `Gasolina Camioneta` |
| `<Concepto> <N1>, <N2>, <N3>` | Múltiples beneficiarios | `Pau, David, Agus` |
| `<Concepto> <N1> y <N2>` | Pareja de beneficiarios | `Telefono Pau y David` |
| `<Concepto> <Mes>` | Misma persona, periodo implícito | `David Febrero`, `Santiago Marzo` |
| `<Concepto> <Mes1> y <monto> <Mes2>` | Pago fusionado | `David Febrero y 3000 Marzo` |
| `<Concepto> <N>` | Concepto + contador de cuota | `Prestamo Omar 8`, `Mercado libre 7 de 12`, `Buro de credito 2 de 3` |

#### Numeración de cuotas (contador manual)

Se identificaron tres contadores activos de cuotas:

- **`Prestamo Omar`**: cuotas 3 → 10 observadas (préstamo personal amortizado a 10+ pagos mensuales)
- **`Mercado libre`**: formato `X de 12` (MSI a 12 meses)
- **`Buro de credito`**: formato `X de 3` (parcialidades a 3 pagos)

El usuario **incrementa manualmente** el contador copiando y editando cada quincena. Este es un vector primario de error.

#### Inconsistencias ortográficas en nombres de hojas

```
"Quincena 16 al 30 junio)"           ← paréntesis espurio
"Quincena 16 al 28 de febrereo"      ← "febrereo" (typo)
"Quincena 1al 15 Marzo"              ← espacio faltante
"Quincena 1al 15 Abril "             ← espacio trailing
"Quincena 16 al 30 de ABRIL"         ← mayúsculas inconsistentes
```

#### Referencias rotas

La hoja `Quincena 16 al 31 Ene` contiene `=#REF!` en `C91` apuntando a `Quincena 1 al 15 noviembre` (hoja inexistente, borrada o renombrada).

### 1.7 Columnas auxiliares N–U — Libros de conciliación manual

Las hojas de la Plantilla A usan columnas N–U como **scratchpad de reconciliación bancaria**. El contenido es semi-libre y varía por hoja:

- `N11 "MERCADO PAGO"` → lista de cargos aplicados a la tarjeta Mercado Pago con subtotal en `N21` o `N29`
- `P11 "banamex"` / `P11 "Efectivo"` / `S11 "Banamex"` → desgloses por método de pago
- `O29 "banco"` → fórmula de conciliación tipo `=N21+T13`
- Celdas aisladas con cálculos ad-hoc tipo `=U24-T22`, `=Q29-(2500+3000)`

Estas columnas **no tienen esquema consistente**. Representan el principal caso de uso sin formalizar: **conciliación por método de pago**.

### 1.8 Bloque histórico (filas 90–127) — Ahorro efectivo

```
B90  "Ahorro Efectivo"
B91  "Quincena 1 al 15 noviembre"   C91  <monto>
B92  "Quincena 16 al 30 noviembre"  C92  18000
...
C126 =SUM(C97:C125)    ← Total acumulado de ahorro efectivo
I126 =SUM(I97:I125)    ← Total acumulado segunda columna
```

Intento de **log intertemporal de ahorro**, lamentablemente llenado a mano y hoja por hoja.

### 1.9 Bloque de préstamos otorgados (filas 116–120)

```
B116 "Prestamo oficinas"  C116 "jaudiel"
B117 10000                 C117 105000
B118 50000
```

El hogar **presta dinero a terceros**: `jaudiel` (persona) y `oficinas` (entidad). Saldo total prestado ~105,000 MXN. Este flujo está subdocumentado.

### 1.10 Miembros del hogar y terceros inferidos

| Rol | Entidad | Frecuencia | Naturaleza |
|---|---|---|---|
| Adulto-pagador | **Benjamin (Benji)** | 33/33 hojas | Ingreso fijo 45,000 MXN (o 0) |
| Adulto-pagador | **Norma (Normita)** | 33/33 hojas | Ingreso fijo 60,000 MXN |
| Beneficiario | **Pau** | 32/33 | Estudiante/hijo/a — mesada + seguro |
| Beneficiario | **David** | 26/33 | Estudiante/hijo — mesada mensual 8,000–15,000 MXN |
| Beneficiario | **Agus / Agustín** | 33/33 (en "Pau, David, Agus") | Hijo — seguro grupal |
| Beneficiario | **Santi / Santiago** | 33/33 | Estudiante — teléfono + inscripciones + mesada |
| Tercero | **Araceli** | 2/33 (bajo PETS) | Probablemente empleada del hogar (pago por servicios) |
| Tercero | **Omar** | 8/33 (prestamos) | Acreedor de préstamo personal amortizado |
| Tercero | **jaudiel** | 2/33 | Deudor del hogar (préstamo otorgado) |
| Tercero | **Mary** | 3/33 | No determinado — posible empleada o beneficiaria |

### 1.11 Inventario de métodos de pago (cuentas)

Deducidos del archivo:

- **Banamex** (cuenta débito + tarjeta clásica de crédito)
- **BBVA** (cuenta débito)
- **Mercado Pago** (monedero + tarjeta de crédito)
- **Coppel** (tarjeta departamental)
- **Liverpool** (tarjeta departamental)
- **Sears** (tarjeta departamental)
- **Walmart** (tarjeta departamental)
- **Mercado libre** (crédito a plazos — BNPL)
- **Klar** (tarjeta digital)
- **Efectivo**
- **Ahorro empresa** (fondo de ahorro del empleador)

---

## 2. Modelo de Datos Propuesto

### 2.1 Principios de diseño

1. **Un solo log inmutable de `Expense`** — todas las hojas colapsan en una colección indexable por fecha.
2. **Normalización total** — categorías, beneficiarios, pagadores y métodos de pago son tablas separadas, no strings embebidos.
3. **Relaciones N:M para atribución** — un gasto puede tener múltiples beneficiarios y múltiples pagadores (reparto proporcional).
4. **Recurrencia como entidad de primer orden** — plantillas de gasto recurrente con generación proactiva de instancias esperadas.
5. **Agnostic de frontend** — esquemas válidos para SQLite (Android), Room, Firestore/BQ (web) y proto de Wear OS.

### 2.2 Esquemas de colecciones (notación TypeScript/Zod-compatible)

#### 2.2.1 `Household` — raíz del aislamiento de datos

```ts
Household {
  id: UUID                       // PK
  name: string                   // "Casa Benjamín y Norma"
  currency: ISO4217              // "MXN"
  timezone: IANA                 // "America/Mexico_City"
  quincena_anchor: "CALENDAR"    // 1-15 / 16-fin (default MX)
                | "BIWEEKLY"     // cada 14 días desde epoch
  created_at: timestamp
}
```

#### 2.2.2 `Member` — miembros del hogar + terceros

```ts
Member {
  id: UUID
  household_id: UUID → Household.id
  display_name: string           // "Benjamín", "Pau"
  short_aliases: string[]        // ["Benji"], ["Pau"]   ← para matching textual
  role: enum {
    "PAYER_ADULT",               // Benjamín, Norma
    "BENEFICIARY_DEPENDENT",     // Pau, David, Agus, Santi
    "EXTERNAL_CREDITOR",         // Omar (nos prestó)
    "EXTERNAL_DEBTOR",           // jaudiel (le prestamos)
    "EXTERNAL_SERVICE"           // Araceli (empleada)
  }
  is_active: boolean
  default_income_mxn?: decimal   // sólo para PAYER_ADULT
  meta: jsonb                    // { color, avatar, birthdate }
}
```

#### 2.2.3 `Category` — taxonomía jerárquica

```ts
Category {
  id: UUID
  household_id: UUID
  parent_id?: UUID → Category.id       // null = raíz
  code: string                         // "HOUSING", "HOUSING.TELEFONO"
  display_name: string                 // "Vivienda", "Teléfono"
  icon: string                         // material-icon key
  color_hex: string
  kind: enum {
    "EXPENSE_FIXED",                   // Hipoteca, Internet
    "EXPENSE_VARIABLE",                // Despensa, Diversión
    "EXPENSE_INSTALLMENT",             // Loans (con intereses/cuotas)
    "TRANSFER_INTRA_HOUSEHOLD",        // mesadas a hijos (antes "TAXES")
    "SAVINGS",                         // Tarjeta de ahorro, Retirement
    "INCOME",                          // Sueldos, Ahorro empresa
    "LOAN_RECEIVABLE"                  // dinero prestado por el hogar
  }
  budget_default_mxn?: decimal         // presupuesto sugerido por quincena
  sort_order: int
}
```

**Semilla mínima** (deducida del Excel):

```
HOUSING > {Hipoteca, Internet, Electricidad, Agua, Fraccionamiento, Telefono}
ENTERTAINMENT > {Streaming: [Netflix, HBO, Prime, Disney+Star], Software: [Adobe, Coursera, KIGO], Otros: [Diversion, Hawaiano, Spotify, Suscripcion nivel 6]}
TRANSPORTATION > {Gasolina, Insurance, Licensing, Fuel, Maintenance}
LOANS > {Coppel, Sears, Liverpool, Banamex Clasica, Walmart, Mercado Libre, Mercado Pago, Didi, Prestamo Omar, Buro de credito, Mary}
SEGUROS_MEDICOS (antes PERSONAL) > {Benji, Norma, Pau+David+Agus, Santi}
TRANSFERENCIAS_FAMILIARES (antes TAXES) > {David, Santiago, Pau, Coche, Inscripciones}
FOOD > {Comida, Despensa, Limpieza}
PETS > {Comida Gatas, Grooming, Toys, Veterinario}
SERVICIOS_EXTERNOS > {Psicologa, Araceli}
SAVINGS > {Ahorro empresa, Tarjeta de ahorro, Efectivo, Retirement, Investment}
INGRESOS > {Sueldo Benjamin, Sueldo Norma, Extras}
PRESTAMOS_OTORGADOS > {jaudiel, oficinas}
```

#### 2.2.4 `PaymentMethod` — cuentas, tarjetas, efectivo

```ts
PaymentMethod {
  id: UUID
  household_id: UUID
  display_name: string               // "Banamex Clásica"
  kind: enum {
    "DEBIT_ACCOUNT",                 // Banamex débito, BBVA
    "CREDIT_CARD",                   // Banamex Clásica, Mercado Pago CC
    "DEPARTMENT_STORE_CARD",         // Coppel, Liverpool, Sears, Walmart
    "BNPL_INSTALLMENT",              // Mercado Libre
    "DIGITAL_WALLET",                // Mercado Pago monedero, Klar
    "CASH",
    "EMPLOYER_SAVINGS_FUND"          // Ahorro empresa
  }
  issuer: string                     // "Citibanamex", "BBVA México"
  last4?: string
  cutoff_day?: int                   // día de corte (1-31)
  due_day?: int                      // día de pago
  credit_limit_mxn?: decimal
  current_balance_mxn: decimal       // saldo actual (calculado)
  interest_apr?: decimal             // tasa anual si aplica
  owner_member_id: UUID → Member.id
  is_active: boolean
}
```

#### 2.2.5 `Expense` — registro atómico de gasto (evento inmutable)

```ts
Expense {
  id: UUID
  household_id: UUID
  occurred_at: timestamp             // fecha/hora del gasto
  quincena_id: UUID → Quincena.id    // denormalizado para queries rápidas
  category_id: UUID → Category.id
  concept: string                    // descripción libre ("Netflix", "Gasolina camioneta")
  amount_mxn: decimal                // monto total positivo
  payment_method_id: UUID → PaymentMethod.id
  recurrence_template_id?: UUID → RecurrenceTemplate.id
  installment_info?: {               // null si no es cuota
    template_id: UUID → InstallmentPlan.id
    installment_number: int          // 7
    total_installments: int          // 12
    principal_mxn: decimal
    interest_mxn: decimal
  }
  status: enum {
    "PLANNED",                       // presupuestado aún no ejecutado
    "POSTED",                        // confirmado
    "RECONCILED"                     // casado con estado de cuenta
  }
  notes?: string
  attachments?: UUID[]               // recibos/capturas
  created_at: timestamp
  created_by_member_id: UUID
}
```

#### 2.2.6 `ExpenseAttribution` — tabla puente (1 Expense → N Members beneficiarios)

```ts
ExpenseAttribution {
  id: UUID
  expense_id: UUID → Expense.id
  member_id: UUID → Member.id
  role: enum { "BENEFICIARY", "PAYER" }
  share_bps: int                     // basis points, 0–10000 (10000 = 100%)
  share_amount_mxn: decimal          // = expense.amount * share_bps / 10000
}
```

**Regla de integridad**: para cada `expense_id`, `SUM(share_bps) = 10000` tanto para `role=BENEFICIARY` como para `role=PAYER` (dos agregaciones independientes).

Ejemplo — el gasto `"Seguro Pau, David, Agus"` de 3,500 MXN pagado 100% por Norma:

```
expense:      { amount: 3500, concept: "Seguro médico", category: SEGUROS }
attributions: [
  { member: Pau,     role: BENEFICIARY, share_bps: 3333, share_amount: 1166.55 },
  { member: David,   role: BENEFICIARY, share_bps: 3333, share_amount: 1166.55 },
  { member: Agustín, role: BENEFICIARY, share_bps: 3334, share_amount: 1166.90 },
  { member: Norma,   role: PAYER,       share_bps: 10000, share_amount: 3500 }
]
```

#### 2.2.7 `Quincena` — período operativo (ancla de agregación)

```ts
Quincena {
  id: UUID
  household_id: UUID
  year: int
  month: int                         // 1-12
  half: enum { "FIRST" /*1-15*/, "SECOND" /*16-fin*/ }
  start_date: date
  end_date: date
  label: string                      // "Q1 Enero 2025", autogenerada
  projected_income_mxn: decimal      // snapshot del presupuesto
  projected_expenses_mxn: decimal
  actual_income_mxn: decimal
  actual_expenses_mxn: decimal
  closed_at?: timestamp              // bloqueada al cerrar quincena
}
```

#### 2.2.8 `RecurrenceTemplate` — plantilla de gasto recurrente

```ts
RecurrenceTemplate {
  id: UUID
  household_id: UUID
  concept: string                    // "Netflix"
  category_id: UUID
  default_amount_mxn: decimal        // 329
  default_payment_method_id: UUID
  cadence: enum {
    "QUINCENAL_FIRST",               // cada quincena 1-15
    "QUINCENAL_SECOND",              // cada quincena 16-fin
    "QUINCENAL_EVERY",               // ambas quincenas
    "MONTHLY_SPECIFIC_HALF",         // una quincena al mes (ej. sólo 16-fin)
    "BIMONTHLY",                     // cada dos meses (agua, luz)
    "CUSTOM_CRON"                    // expresión tipo cron
  }
  cadence_detail?: jsonb             // { day_of_month: 15, drift_tolerance_days: 3 }
  next_expected_date: date
  default_beneficiary_ids: UUID[]    // miembros que tipicamente se benefician
  default_payer_split: {             // distribución default del pagador
    [member_id: UUID]: int           // basis points
  }
  is_active: boolean
  confidence_score: decimal          // 0.0–1.0 — qué tan seguros estamos
  learned_from_expense_ids: UUID[]   // trazabilidad del aprendizaje
}
```

#### 2.2.9 `InstallmentPlan` — plan de pagos con intereses

```ts
InstallmentPlan {
  id: UUID
  household_id: UUID
  display_name: string               // "Préstamo Omar"
  creditor_member_id?: UUID          // Omar
  payment_method_id?: UUID           // Mercado Libre (BNPL)
  principal_mxn: decimal             // capital original
  total_installments: int            // 10
  installment_amount_mxn: decimal    // 5500
  interest_rate_apr?: decimal        // tasa si aplica
  start_date: date
  current_installment: int           // avance
  status: enum { "ACTIVE", "PAID_OFF", "DEFAULTED" }
  category_id: UUID                  // LOANS.*
}
```

#### 2.2.10 `Loan` — préstamos otorgados por el hogar

```ts
Loan {
  id: UUID
  household_id: UUID
  debtor_member_id: UUID             // jaudiel, oficinas
  principal_mxn: decimal             // 105000
  remaining_balance_mxn: decimal
  agreed_interest_mxn: decimal
  issued_at: date
  due_at?: date
  payment_schedule?: InstallmentPlan.id
  notes: string
}
```

#### 2.2.11 `SavingsGoal` — metas de ahorro

```ts
SavingsGoal {
  id: UUID
  household_id: UUID
  name: string
  target_mxn: decimal
  current_mxn: decimal
  target_date?: date
  linked_payment_method_id: UUID
}
```

### 2.3 Diagrama de relaciones (ER simplificado)

```
Household (1) ─┬─< (N) Member
               ├─< (N) Category ─── (self-ref parent_id)
               ├─< (N) PaymentMethod ── owner → Member
               ├─< (N) Quincena
               ├─< (N) RecurrenceTemplate ─── Category
               ├─< (N) InstallmentPlan
               ├─< (N) Loan ─── debtor → Member
               ├─< (N) SavingsGoal
               └─< (N) Expense ──┬─ Category
                                  ├─ PaymentMethod
                                  ├─ RecurrenceTemplate?
                                  ├─ InstallmentPlan? (installment_info)
                                  ├─ Quincena
                                  └─< (N) ExpenseAttribution ─── Member
```

### 2.4 Esquema SQL DDL (referencia para SQLite/Room)

```sql
CREATE TABLE household (
  id TEXT PRIMARY KEY, name TEXT NOT NULL, currency TEXT NOT NULL DEFAULT 'MXN',
  timezone TEXT NOT NULL DEFAULT 'America/Mexico_City', quincena_anchor TEXT DEFAULT 'CALENDAR',
  created_at INTEGER NOT NULL
);

CREATE TABLE member (
  id TEXT PRIMARY KEY, household_id TEXT NOT NULL REFERENCES household(id) ON DELETE CASCADE,
  display_name TEXT NOT NULL, short_aliases TEXT NOT NULL DEFAULT '[]',
  role TEXT NOT NULL, is_active INTEGER NOT NULL DEFAULT 1,
  default_income_mxn REAL, meta TEXT DEFAULT '{}'
);
CREATE INDEX idx_member_household ON member(household_id, is_active);

CREATE TABLE category (
  id TEXT PRIMARY KEY, household_id TEXT NOT NULL REFERENCES household(id),
  parent_id TEXT REFERENCES category(id), code TEXT NOT NULL, display_name TEXT NOT NULL,
  icon TEXT, color_hex TEXT, kind TEXT NOT NULL, budget_default_mxn REAL, sort_order INTEGER DEFAULT 0,
  UNIQUE(household_id, code)
);

CREATE TABLE payment_method (
  id TEXT PRIMARY KEY, household_id TEXT NOT NULL REFERENCES household(id),
  display_name TEXT NOT NULL, kind TEXT NOT NULL, issuer TEXT, last4 TEXT,
  cutoff_day INTEGER, due_day INTEGER, credit_limit_mxn REAL,
  current_balance_mxn REAL NOT NULL DEFAULT 0, interest_apr REAL,
  owner_member_id TEXT REFERENCES member(id), is_active INTEGER DEFAULT 1
);

CREATE TABLE quincena (
  id TEXT PRIMARY KEY, household_id TEXT NOT NULL REFERENCES household(id),
  year INTEGER NOT NULL, month INTEGER NOT NULL, half TEXT NOT NULL,
  start_date TEXT NOT NULL, end_date TEXT NOT NULL, label TEXT NOT NULL,
  projected_income_mxn REAL DEFAULT 0, projected_expenses_mxn REAL DEFAULT 0,
  actual_income_mxn REAL DEFAULT 0, actual_expenses_mxn REAL DEFAULT 0,
  closed_at INTEGER,
  UNIQUE(household_id, year, month, half)
);
CREATE INDEX idx_quincena_dates ON quincena(household_id, start_date, end_date);

CREATE TABLE recurrence_template (
  id TEXT PRIMARY KEY, household_id TEXT NOT NULL REFERENCES household(id),
  concept TEXT NOT NULL, category_id TEXT NOT NULL REFERENCES category(id),
  default_amount_mxn REAL NOT NULL, default_payment_method_id TEXT REFERENCES payment_method(id),
  cadence TEXT NOT NULL, cadence_detail TEXT DEFAULT '{}',
  next_expected_date TEXT, default_beneficiary_ids TEXT DEFAULT '[]',
  default_payer_split TEXT DEFAULT '{}', is_active INTEGER DEFAULT 1,
  confidence_score REAL DEFAULT 0.0, learned_from_expense_ids TEXT DEFAULT '[]'
);

CREATE TABLE installment_plan (
  id TEXT PRIMARY KEY, household_id TEXT NOT NULL REFERENCES household(id),
  display_name TEXT NOT NULL, creditor_member_id TEXT REFERENCES member(id),
  payment_method_id TEXT REFERENCES payment_method(id),
  principal_mxn REAL NOT NULL, total_installments INTEGER NOT NULL,
  installment_amount_mxn REAL NOT NULL, interest_rate_apr REAL,
  start_date TEXT NOT NULL, current_installment INTEGER DEFAULT 0,
  status TEXT DEFAULT 'ACTIVE', category_id TEXT REFERENCES category(id)
);

CREATE TABLE expense (
  id TEXT PRIMARY KEY, household_id TEXT NOT NULL REFERENCES household(id),
  occurred_at INTEGER NOT NULL, quincena_id TEXT NOT NULL REFERENCES quincena(id),
  category_id TEXT NOT NULL REFERENCES category(id), concept TEXT NOT NULL,
  amount_mxn REAL NOT NULL, payment_method_id TEXT NOT NULL REFERENCES payment_method(id),
  recurrence_template_id TEXT REFERENCES recurrence_template(id),
  installment_plan_id TEXT REFERENCES installment_plan(id), installment_number INTEGER,
  installment_principal_mxn REAL, installment_interest_mxn REAL,
  status TEXT DEFAULT 'POSTED', notes TEXT, created_at INTEGER NOT NULL,
  created_by_member_id TEXT REFERENCES member(id)
);
CREATE INDEX idx_expense_quincena ON expense(quincena_id, status);
CREATE INDEX idx_expense_date ON expense(household_id, occurred_at);
CREATE INDEX idx_expense_category ON expense(category_id, occurred_at);
CREATE INDEX idx_expense_recurrence ON expense(recurrence_template_id);

CREATE TABLE expense_attribution (
  id TEXT PRIMARY KEY, expense_id TEXT NOT NULL REFERENCES expense(id) ON DELETE CASCADE,
  member_id TEXT NOT NULL REFERENCES member(id), role TEXT NOT NULL,
  share_bps INTEGER NOT NULL CHECK(share_bps BETWEEN 0 AND 10000),
  share_amount_mxn REAL NOT NULL
);
CREATE INDEX idx_attr_expense ON expense_attribution(expense_id, role);
CREATE INDEX idx_attr_member ON expense_attribution(member_id, role);

CREATE TABLE loan (
  id TEXT PRIMARY KEY, household_id TEXT NOT NULL REFERENCES household(id),
  debtor_member_id TEXT NOT NULL REFERENCES member(id), principal_mxn REAL NOT NULL,
  remaining_balance_mxn REAL NOT NULL, agreed_interest_mxn REAL DEFAULT 0,
  issued_at TEXT NOT NULL, due_at TEXT, payment_schedule_id TEXT REFERENCES installment_plan(id),
  notes TEXT
);
```

---

## 3. Requerimientos Funcionales Core

Enumerados con prioridad `P0` (bloqueante para paridad con Excel), `P1` (crítico para adopción) y `P2` (diferenciador).

### 3.1 Registro de gastos

- **RF-01 [P0]** Registrar un gasto en ≤ 3 taps con autocompletado del concepto desde plantillas recurrentes.
- **RF-02 [P0]** Capturar en una sola operación: concepto, monto, categoría, método de pago, fecha, beneficiario(s), pagador(es).
- **RF-03 [P0]** Capturar gastos compartidos asignando basis points a N beneficiarios con validación de suma = 100%.
- **RF-04 [P0]** Registrar atribución N:M de pagadores (ej. Norma paga 60%, Benjamín 40%).
- **RF-05 [P0]** Soportar estado `PLANNED` vs `POSTED` (presupuestado vs ejecutado) para replicar las columnas `Projected Cost` / `Actual Cost`.
- **RF-06 [P1]** Registro por voz desde Wear OS ("Café 85 pesos, Banamex").
- **RF-07 [P1]** Parser de SMS/notificación bancaria para pre-llenar monto y método de pago.
- **RF-08 [P1]** Escáner OCR de tickets con detección de monto y categorización sugerida.

### 3.2 Plantillas y recurrencia

- **RF-10 [P0]** Definir plantillas recurrentes con cadencia (quincenal, mensual, bimestral, custom).
- **RF-11 [P0]** Al abrir una nueva quincena, materializar automáticamente todas las instancias `PLANNED` de las plantillas activas.
- **RF-12 [P0]** Autocompletar monto, categoría, método, beneficiarios y pagadores al escribir los primeros caracteres del concepto.
- **RF-13 [P1]** Pausar, editar o saltar una instancia recurrente sin afectar la plantilla.
- **RF-14 [P1]** Promover un gasto individual a plantilla recurrente tras N repeticiones detectadas (ver §4.3).

### 3.3 Planes de cuotas

- **RF-20 [P0]** Crear un plan de cuotas (ej. 10 pagos de 5,500 MXN) que genere automáticamente las N instancias pendientes.
- **RF-21 [P0]** Autonumerar el contador de cuota (`Préstamo Omar 8/10`) y mostrarlo en la UI.
- **RF-22 [P0]** Soportar desglose capital + interés por cuota cuando se capture la tasa.
- **RF-23 [P1]** Alertar cuando una cuota lleva 3 días vencida y no se ha marcado como `POSTED`.
- **RF-24 [P1]** Al liquidar anticipadamente, recalcular intereses restantes.

### 3.4 Quincena operativa

- **RF-30 [P0]** Abrir nueva quincena clonando plantillas activas + presupuesto por defecto.
- **RF-31 [P0]** Dashboard en tiempo real con `Ingreso`, `Presupuesto`, `Ejecutado`, `Falta Gastar`, `Balance`, `Ahorro implícito`.
- **RF-32 [P0]** Cerrar quincena (estado `closed_at`) congelando datos y alimentando analíticas.
- **RF-33 [P0]** Comparar quincena actual contra la media de las N quincenas anteriores.
- **RF-34 [P1]** Reporte exportable PDF/XLSX de la quincena cerrada (mantiene compatibilidad con stakeholders del hogar).

### 3.5 Cuentas y conciliación

- **RF-40 [P0]** Mostrar saldo en vivo por método de pago (reemplaza columnas N–U del Excel).
- **RF-41 [P0]** Registrar transferencias entre cuentas sin contabilizarlas como gasto.
- **RF-42 [P0]** Conciliar gasto con línea de estado de cuenta (match manual o automático por monto+fecha).
- **RF-43 [P1]** Alerta de sobrepago de tarjeta (saldo + próximos gastos planeados > límite).
- **RF-44 [P1]** Proyección de corte de tarjeta: qué se paga este mes.

### 3.6 Préstamos otorgados y recibidos

- **RF-50 [P0]** CRUD de préstamos otorgados (jaudiel, oficinas) con saldo remanente.
- **RF-51 [P1]** Registrar pagos recibidos y reducir saldo automáticamente.
- **RF-52 [P1]** Calcular intereses generados si aplica.

### 3.7 Miembros y atribución

- **RF-60 [P0]** CRUD de miembros con roles (adulto-pagador, dependiente, externo).
- **RF-61 [P0]** Búsqueda por nombre o alias.
- **RF-62 [P0]** Vista "Gasto por miembro" filtrable por rango de fechas.
- **RF-63 [P1]** Line chart histórico de gasto por beneficiario (ej. David consume vs Pau).

### 3.8 Categorías

- **RF-70 [P0]** Jerarquía arbitraria padre-hijo.
- **RF-71 [P0]** Recategorizar un gasto sin perder atribuciones.
- **RF-72 [P1]** Sugerir categoría automática basada en concepto y historial.

### 3.9 Sincronización multiplataforma

- **RF-80 [P0]** Sincronización eventual entre Android Foldable, Wear OS y Web. Last-write-wins con vector clock por entidad.
- **RF-81 [P0]** Offline-first en móvil y reloj con queue de cambios.
- **RF-82 [P1]** Multi-usuario por household (Benjamín y Norma capturando simultáneamente) con presence indicator.

### 3.10 Importación / exportación

- **RF-90 [P0]** Importador del XLSX actual con mapeo guiado por el usuario.
- **RF-91 [P1]** Exportador XLSX con el mismo layout para backup legible.
- **RF-92 [P1]** Exportador CSV estándar para uso externo.

---

## 4. Sistema de Atribución y Recurrencia

### 4.1 Modelo dual de atribución

Cada `Expense` lleva **dos particiones independientes** entre miembros del hogar:

- **Partición BENEFICIARIO** (quién recibe/consume el bien o servicio)
- **Partición PAGADOR** (quién desembolsa el dinero efectivamente)

Ambas viven en `ExpenseAttribution` con el campo `role`, y ambas deben sumar `10000` basis points para integridad referencial.

**Ejemplos canónicos mapeados del Excel:**

```yaml
# "Telefono Santi" 199 MXN, Norma paga todo
expense: { concept: "Teléfono Santi", amount: 199, category: HOUSING.TELEFONO }
beneficiaries: [ { Santi, 10000 bps, 199 MXN } ]
payers:        [ { Norma, 10000 bps, 199 MXN } ]

# "Pau, David, Agus" seguros 3500, Norma paga todo
expense: { concept: "Seguro médico anual prorrateado", amount: 3500, category: SEGUROS }
beneficiaries: [ { Pau, 3333 bps }, { David, 3333 bps }, { Agus, 3334 bps } ]
payers:        [ { Norma, 10000 bps } ]

# "Hipoteca" 4000, 100% Norma beneficiaria conjunta con Benjamín, pagada por Norma
expense: { concept: "Hipoteca", amount: 4000, category: HOUSING }
beneficiaries: [ { Norma, 5000 bps }, { Benjamín, 5000 bps } ]
payers:        [ { Norma, 10000 bps } ]

# "Diversion" 4500, pago compartido 100-0 (Norma paga todo), beneficia a toda la familia
expense: { concept: "Diversión familiar", amount: 4500, category: ENTERTAINMENT }
beneficiaries: [ { Norma, 2000 }, { Benjamín, 2000 }, { Pau, 1500 }, { David, 1500 }, { Agus, 1500 }, { Santi, 1500 } ]
payers:        [ { Norma, 10000 } ]
```

### 4.2 Parser textual de concepto → atribución inferida

Para mantener compatibilidad con la forma en que el usuario piensa ("escribir el nombre con el beneficiario dentro"), la app implementa un parser de strings que extrae beneficiarios desde el campo `concept`:

```pseudocode
function inferBeneficiariesFromConcept(concept: string, household: Household) -> BeneficiarySuggestion[]

  tokens = tokenize(concept)
  member_aliases = load_all_aliases(household)   # case-insensitive index
  suggestions = []

  for token in tokens:
    match = fuzzy_match(token, member_aliases, threshold=0.85)
    if match:
      suggestions.append(match.member)

  # Detectar patrones especiales
  if contains(concept, /,\s*/) or contains(concept, /\s+y\s+/i):
    # Lista enumerada: "Pau, David, Agus" o "Pau y David"
    return equal_split(suggestions)

  if matches(concept, /^(\w+)\s+(Febrero|Marzo|...)/i):
    # Persona + mes: "David Marzo"
    return [suggestions[0]]

  if matches(concept, /^(Telefono|Seguro|Gasolina|Inscripcion|Mesada)\s+(\w+)/i):
    # Categoría + persona: "Telefono Santi"
    return [suggestions[-1]]     # última coincidencia

  return suggestions
```

**El parser nunca escribe directo al modelo.** Siempre presenta un cuadro de diálogo de confirmación ("Detectamos: Santi. ¿Correcto?") — RF-62.

### 4.3 Motor de recurrencia — detección + memorización

El objetivo es que **cada gasto repetido se convierta automáticamente en una plantilla** después de umbrales de confianza. Especificación algorítmica:

#### 4.3.1 Fase A — Identificación de candidatos

```python
def detect_recurrence_candidates(expenses: List[Expense]) -> List[Candidate]:
    """
    Agrupa gastos por (concepto_normalizado, categoria) y evalúa si el patrón
    temporal y de monto sugiere recurrencia.
    """
    # 1. Normalizar concepto: lowercase, strip, collapse whitespace, remove digits/ordinals
    #    "Préstamo Omar 8" → "prestamo omar"
    #    "Mercado libre 7 de 12" → "mercado libre"
    groups = groupby(expenses, key=lambda e: (normalize(e.concept), e.category_id))

    candidates = []
    for key, group in groups:
        if len(group) < 2:
            continue

        # 2. Analizar intervalos entre ocurrencias
        dates = sorted(e.occurred_at for e in group)
        intervals = diff(dates)                     # timedeltas entre consecutivos
        median_interval = median(intervals)
        interval_cv = std(intervals) / median_interval  # coef. de variación

        # 3. Analizar estabilidad de monto
        amounts = [e.amount_mxn for e in group]
        median_amount = median(amounts)
        amount_cv = std(amounts) / median_amount

        # 4. Mapear intervalo mediano a cadencia conocida
        cadence = map_interval_to_cadence(median_interval)
        # 14-16 días → QUINCENAL_EVERY
        # 28-32 días → MENSUAL (detectar si siempre es 1a o 2a quincena)
        # 58-64 días → BIMESTRAL

        # 5. Score de confianza
        confidence = (
            0.4 * sigmoid(len(group) - 3) +        # premio por número de muestras
            0.3 * (1 - min(interval_cv, 1)) +      # premio por regularidad temporal
            0.3 * (1 - min(amount_cv, 1))          # premio por estabilidad de monto
        )

        if confidence > 0.65:
            candidates.append(Candidate(
                concept=key[0],
                category=key[1],
                cadence=cadence,
                suggested_amount=median_amount,
                confidence=confidence,
                source_expenses=group
            ))
    return candidates
```

#### 4.3.2 Fase B — Memorización de atribución

Para cada candidato, se calcula la distribución modal de beneficiarios y pagadores:

```python
def learn_attribution(candidate: Candidate) -> AttributionDefaults:
    beneficiary_counter = Counter()
    payer_split_accumulator = defaultdict(list)

    for expense in candidate.source_expenses:
        for attr in expense.attributions:
            if attr.role == 'BENEFICIARY':
                beneficiary_counter[attr.member_id] += 1
            elif attr.role == 'PAYER':
                payer_split_accumulator[attr.member_id].append(attr.share_bps)

    # Beneficiarios: quienes aparezcan en >= 70% de las ocurrencias
    threshold = 0.7 * len(candidate.source_expenses)
    default_beneficiaries = [m for m, c in beneficiary_counter.items() if c >= threshold]

    # Payer split: promedio de basis points por miembro
    default_payer_split = {
        m: int(mean(splits))
        for m, splits in payer_split_accumulator.items()
    }
    # Renormalizar a 10000 bps exactos
    total = sum(default_payer_split.values())
    default_payer_split = {m: int(v * 10000 / total) for m, v in default_payer_split.items()}

    return AttributionDefaults(default_beneficiaries, default_payer_split)
```

#### 4.3.3 Fase C — Proposición al usuario

Cuando `confidence >= 0.75`, la app propone convertir el patrón en plantilla. El usuario confirma/edita y la plantilla queda activa. Cada nuevo gasto que coincida con una plantilla actualizará el `confidence_score` incrementalmente (EMA con α = 0.2).

#### 4.3.4 Fase D — Autogeneración al iniciar quincena

```python
def bootstrap_quincena(q: Quincena):
    active_templates = query(RecurrenceTemplate, where=is_active=True)
    for t in active_templates:
        if falls_in_quincena(t, q):
            planned_expense = Expense(
                concept=t.concept,
                amount_mxn=t.default_amount_mxn,
                category_id=t.category_id,
                payment_method_id=t.default_payment_method_id,
                status='PLANNED',
                occurred_at=t.next_expected_date,
                quincena_id=q.id,
                recurrence_template_id=t.id
            )
            persist(planned_expense)
            materialize_attributions(planned_expense, t)
```

### 4.4 Motor de cuotas (installments) — contador automático

Reemplaza la numeración manual `Préstamo Omar 3 → 4 → 5 → ...`:

```python
def on_installment_posted(expense: Expense):
    plan = expense.installment_plan
    plan.current_installment += 1

    if plan.current_installment >= plan.total_installments:
        plan.status = 'PAID_OFF'
        notify(f"Plan '{plan.display_name}' liquidado")
    else:
        # Programar siguiente cuota según la cadencia vinculada
        next_date = plan.next_due_date()
        schedule_planned_expense(plan, next_date, plan.current_installment + 1)
```

La UI siempre muestra `3/10`, nunca obliga al usuario a contar.

### 4.5 Desglose capital + interés

Para planes con tasa conocida, se calcula la tabla de amortización francesa:

```
cuota_fija = P * [r(1+r)^n] / [(1+r)^n - 1]
  P = principal, r = tasa mensual = APR/12, n = número de cuotas

para cuota k:
  interés_k   = saldo_remanente_k * r
  capital_k   = cuota_fija - interés_k
  saldo_k+1   = saldo_remanente_k - capital_k
```

Esto habilita la analítica "distribución del pago de intereses" (§5.3).

---

## 5. Arquitectura de Analíticas

### 5.1 KPIs de primer nivel (cards del dashboard)

| KPI | Cálculo | Actualización |
|---|---|---|
| **Balance quincenal** | `Σ income − Σ expense (POSTED) − Σ savings_locked` | Tiempo real |
| **Presupuesto ejecutado** | `Σ expense.amount_mxn WHERE status='POSTED'` | Tiempo real |
| **Falta gastar** | `Σ expense.amount_mxn WHERE status='PLANNED' AND quincena=current` | Tiempo real |
| **Tasa de ejecución** | `POSTED / (POSTED + PLANNED)` | Tiempo real |
| **Gasto promedio quincenal** | `AVG(actual_expenses_mxn) over last 6 closed quincenas` | Al cerrar quincena |
| **Ahorro efectivo acumulado** | `Σ SavingsGoal.current_mxn` | Al confirmar transferencia |
| **Deuda revolvente total** | `Σ PaymentMethod.current_balance WHERE kind IN (CREDIT_CARD, DEPARTMENT_STORE_CARD)` | Tiempo real |
| **Compromisos de cuotas pendientes** | `Σ installment_amount * (total − current) sobre planes ACTIVE` | Tiempo real |

### 5.2 Consultas analíticas — ejemplos SQL ejecutables

#### 5.2.1 Fugas de capital (gastos sin atribución explícita o anómalos)

```sql
-- Gastos "Other" / "Otros" que no debieran ser genéricos
SELECT e.concept, c.display_name AS category, SUM(e.amount_mxn) AS total_drift,
       COUNT(*) AS occurrences
FROM expense e
JOIN category c ON c.id = e.category_id
WHERE e.occurred_at >= :last_90d
  AND (LOWER(e.concept) LIKE '%other%' OR LOWER(e.concept) LIKE '%otro%' OR e.concept = '')
GROUP BY e.concept, c.display_name
ORDER BY total_drift DESC
LIMIT 20;

-- Outliers vs. plantilla recurrente (>20% de desviación sobre default_amount)
SELECT e.concept, e.amount_mxn, rt.default_amount_mxn,
       ROUND((e.amount_mxn - rt.default_amount_mxn) / rt.default_amount_mxn * 100, 1) AS pct_drift,
       e.occurred_at
FROM expense e
JOIN recurrence_template rt ON rt.id = e.recurrence_template_id
WHERE ABS(e.amount_mxn - rt.default_amount_mxn) / rt.default_amount_mxn > 0.2
  AND e.occurred_at >= :last_180d
ORDER BY pct_drift DESC;
```

#### 5.2.2 Gasto por miembro del hogar (beneficiario)

```sql
SELECT m.display_name AS beneficiario,
       c.display_name AS categoria,
       SUM(ea.share_amount_mxn) AS total_benef,
       COUNT(DISTINCT e.id) AS n_gastos
FROM expense_attribution ea
JOIN expense e       ON e.id = ea.expense_id
JOIN member m        ON m.id = ea.member_id
JOIN category c      ON c.id = e.category_id
WHERE ea.role = 'BENEFICIARY'
  AND e.status = 'POSTED'
  AND e.occurred_at BETWEEN :from_date AND :to_date
GROUP BY m.display_name, c.display_name
ORDER BY total_benef DESC;
```

#### 5.2.3 Distribución del pago de intereses

```sql
-- Intereses pagados por tarjeta/plan en los últimos 12 meses
SELECT pm.display_name AS cuenta, pm.kind,
       SUM(COALESCE(e.installment_interest_mxn, 0)) AS intereses_pagados_mxn,
       SUM(e.amount_mxn) AS total_pagado_mxn,
       ROUND(100.0 * SUM(COALESCE(e.installment_interest_mxn, 0)) / NULLIF(SUM(e.amount_mxn),0), 2) AS pct_intereses
FROM expense e
JOIN payment_method pm ON pm.id = e.payment_method_id
WHERE e.status = 'POSTED'
  AND e.occurred_at >= date('now', '-12 months')
GROUP BY pm.id
ORDER BY intereses_pagados_mxn DESC;
```

#### 5.2.4 Pronóstico de liquidez quincenal

```sql
-- Para la próxima quincena, proyectar:
--   Ingreso esperado = AVG últimos 6 quincenas cerradas
--   Egreso esperado  = plantillas activas + cuotas vigentes + gasto promedio variable
WITH recent AS (
  SELECT actual_income_mxn, actual_expenses_mxn
  FROM quincena
  WHERE closed_at IS NOT NULL
  ORDER BY start_date DESC LIMIT 6
),
avg_flows AS (
  SELECT AVG(actual_income_mxn) AS avg_income,
         AVG(actual_expenses_mxn) AS avg_expenses
  FROM recent
),
upcoming_fixed AS (
  SELECT SUM(rt.default_amount_mxn) AS fixed_next
  FROM recurrence_template rt
  WHERE rt.is_active = 1
    AND rt.next_expected_date BETWEEN :next_q_start AND :next_q_end
),
upcoming_installments AS (
  SELECT SUM(ip.installment_amount_mxn) AS installments_next
  FROM installment_plan ip
  WHERE ip.status = 'ACTIVE'
    AND ip.current_installment < ip.total_installments
)
SELECT
  af.avg_income AS ingreso_esperado,
  COALESCE(uf.fixed_next, 0) + COALESCE(ui.installments_next, 0) AS egresos_comprometidos,
  af.avg_expenses - COALESCE(uf.fixed_next, 0) - COALESCE(ui.installments_next, 0) AS holgura_variable,
  af.avg_income - af.avg_expenses AS ahorro_proyectado
FROM avg_flows af, upcoming_fixed uf, upcoming_installments ui;
```

#### 5.2.5 Concentración de deuda por acreedor

```sql
SELECT pm.display_name AS acreedor,
       pm.current_balance_mxn AS saldo_actual,
       pm.credit_limit_mxn AS limite,
       ROUND(100.0 * pm.current_balance_mxn / NULLIF(pm.credit_limit_mxn, 0), 1) AS utilizacion_pct
FROM payment_method pm
WHERE pm.kind IN ('CREDIT_CARD', 'DEPARTMENT_STORE_CARD', 'BNPL_INSTALLMENT')
  AND pm.is_active = 1
ORDER BY saldo_actual DESC;
```

#### 5.2.6 Serie temporal de gasto por categoría

```sql
SELECT strftime('%Y-%m', datetime(e.occurred_at / 1000, 'unixepoch')) AS mes,
       c.display_name AS categoria,
       SUM(e.amount_mxn) AS total_mes
FROM expense e
JOIN category c ON c.id = e.category_id
WHERE e.status = 'POSTED'
  AND e.occurred_at >= :last_24m
GROUP BY mes, c.id
ORDER BY mes, total_mes DESC;
```

### 5.3 Vistas/dashboards propuestos

| Pantalla | Contenido | Target |
|---|---|---|
| **Home (hoy)** | Balance quincenal, top 3 gastos recientes, accesos rápidos | Móvil + Web |
| **Quincena actual** | Presupuesto vs ejecutado por categoría, falta gastar | Móvil (foldable aprovecha 2-pane: resumen + detalle lado a lado) |
| **Timeline anual** | Heatmap de gasto por día, overlay de ingresos | Web (pantalla grande) |
| **Por miembro** | Tarjeta por miembro con gasto acumulado, categorías top | Móvil |
| **Por categoría** | Drill-down a subcategorías y gastos individuales | Móvil + Web |
| **Cuentas** | Saldos, próximos cortes, utilización de crédito | Móvil + Web |
| **Cuotas activas** | Barras de progreso con N/Total, fecha próxima | Móvil |
| **Pronóstico** | Proyección de liquidez próxima quincena + gráfica de tendencia | Web |
| **Wear OS glance** | Balance + "puedes gastar $X hoy" | Reloj |

### 5.4 Alertas proactivas (reglas de notificación)

```yaml
- id: budget_overrun_80
  trigger: category_spending_pct >= 80 (en quincena activa)
  channel: push
  message: "Vas al 80% de {category} esta quincena. Restan ${remaining}."

- id: installment_due
  trigger: installment.planned_date in (next 3 days) AND status = 'PLANNED'
  channel: push + wear
  message: "Cuota {N}/{Total} de {display_name} el {date}. Monto ${amount}."

- id: credit_utilization_high
  trigger: payment_method.utilization_pct >= 70
  channel: push
  message: "Tarjeta {name} al {pct}% de uso. Riesgo de interés."

- id: recurrence_detected
  trigger: recurrence_candidate.confidence >= 0.75 AND not yet promoted
  channel: in_app
  message: "Detectamos que '{concept}' se repite cada {cadence}. ¿Crear plantilla?"

- id: no_expense_today
  trigger: current_time >= 22:00 AND count(expense WHERE date=today) == 0
  channel: wear
  message: "¿Algún gasto hoy que registrar?"
```

---

## 6. Áreas de Mejora Estructural

Mapeo directo **fricción detectada → solución de producto**.

### 6.1 Fricción #1 — Creación manual de hoja por quincena

**Síntoma observado**: 33 hojas duplicadas, con typos (`febrereo`), inconsistencias ortográficas y referencias rotas (`#REF!`).

**Solución**:
- Entidad `Quincena` auto-generada por el sistema al momento en que se cierra la anterior.
- Plantillas recurrentes materializan gastos `PLANNED` sin intervención.
- La UI nunca expone el concepto de "hoja". El usuario ve "Quincena actual", "Próxima quincena", "Histórico".

### 6.2 Fricción #2 — Atribución textual en el nombre del concepto

**Síntoma observado**: `"Telefono Santi"`, `"Pau, David, Agus"`, `"David Febrero y 3000 Marzo"` obligan al usuario a codificar metadatos en strings.

**Solución**:
- Beneficiarios como multi-select con chips.
- Parser textual (§4.2) como **sugerencia**, nunca canon.
- Búsqueda y agregación por beneficiario sin regex.

### 6.3 Fricción #3 — Numeración manual de cuotas

**Síntoma observado**: `Prestamo Omar 3, 4, 5, 6, 7, 8, 9, 10` tecleado cada vez.

**Solución**:
- Entidad `InstallmentPlan` con contador automático.
- UI siempre muestra `3/10` y calcula saldo restante.

### 6.4 Fricción #4 — Conciliación bancaria en columnas libres

**Síntoma observado**: Columnas N–U con listas de cargos por banco sin esquema.

**Solución**:
- Saldo por `PaymentMethod` en tiempo real, actualizado a cada `Expense`.
- Vista "Estado de cuenta" por método: lista cronológica de cargos/abonos, saldo corriente y conciliación manual con import de estado de cuenta.
- Importador de XML SAT / CSV de banco.

### 6.5 Fricción #5 — `TAXES` mal etiquetado como transferencias familiares

**Síntoma observado**: La tabla `TAXES` contiene pagos a David, Santiago, Pau, Coche — no impuestos.

**Solución**:
- Categoría `TRANSFERENCIAS_FAMILIARES` explícita, con subcategorías por miembro destinatario.
- Migración del XLSX reclasifica automáticamente estos registros.

### 6.6 Fricción #6 — Agregación intertemporal imposible

**Síntoma observado**: Imposible responder "¿cuánto gastamos en David en 2025?" sin abrir cada hoja manualmente.

**Solución**:
- Log unificado de `Expense`.
- Queries parametrizadas con ventanas temporales arbitrarias.
- Dashboard anual y comparador quincena vs quincena.

### 6.7 Fricción #7 — Inmediatez del registro

**Síntoma observado**: Las hojas deben llenarse "cuando tocas Excel", no cuando ocurre el gasto. Alto riesgo de olvido.

**Solución**:
- Registro desde Wear OS en 2 taps + dictado de voz.
- Widget Android con los 4 gastos recurrentes más probables para el momento del día.
- Parser de notificaciones bancarias (con permiso explícito).

### 6.8 Fricción #8 — Falta de visualización sobre el plano

**Síntoma observado**: El libro no tiene gráficas; todo es numérico tabular.

**Solución**:
- Heatmap diario · donut por categoría · stacked bar por miembro · line chart de tendencia.
- En foldable, layout dual con tabla + gráfica simultáneas.

### 6.9 Fricción #9 — Sin estado de plan vs ejecutado granular

**Síntoma observado**: Las columnas `Projected` y `Norma/Benjamin/Actual` confunden plan con realidad.

**Solución**:
- Campo `status` discreto (`PLANNED`, `POSTED`, `RECONCILED`).
- UI diferencia visualmente gastos planeados (outline) de ejecutados (sólidos).
- Botón "confirmar gasto" colapsa PLANNED → POSTED.

### 6.10 Fricción #10 — Pérdida de contexto de método de pago

**Síntoma observado**: El método de pago se deduce de la columna auxiliar N–U pero no está ligado al registro de gasto de la tabla principal.

**Solución**:
- `payment_method_id` como campo obligatorio en cada `Expense`.
- Autocomplete del último método usado para el concepto.

### 6.11 Fricción #11 — Préstamos otorgados subdocumentados

**Síntoma observado**: `"Prestamo oficinas · jaudiel · 105000 · 50000"` en filas 116-120 sin trazabilidad ni recordatorio.

**Solución**:
- Entidad `Loan` con deudor, saldo, intereses acordados, recordatorios.
- Dashboard "Me deben / Debo" separado del gasto.

### 6.12 Fricción #12 — Ausencia de vista familiar

**Síntoma observado**: No hay lugar para que Benjamín y Norma vean el mismo presupuesto colaborativamente.

**Solución**:
- Modelo `Household` multi-miembro con permisos.
- Captura concurrente y sincronización eventual.
- Auditoría por `created_by_member_id` en cada registro.

---

## Apéndice A: Diccionario de Datos Inferido

### A.1 Conceptos recurrentes detectados (frecuencia/33 hojas)

**Gastos fijos altamente estables** (candidatos P0 para plantillas):

| Concepto | Frec | Monto modal | Cadencia |
|---|---|---|---|
| Hipoteca | 33 | 4,000 | QUINCENAL_SECOND (o FIRST según hoja) |
| Comida Gatas | 33 | 980 | QUINCENAL_FIRST |
| Internet | 33 | 899 | QUINCENAL (una vez al mes) |
| Netflix | 33 | 329 | MENSUAL |
| Prime | 33 | 99 | MENSUAL |
| HBO | 28 | 167.3 | MENSUAL |
| Suscripcion nivel 6 | 17 | 129.9 | MENSUAL |
| KIGO | 8 | 100 | MENSUAL |
| Adobe | 9 | 249 | MENSUAL |
| Teléfono Norma | 32 | 498 | MENSUAL |
| Teléfono Santi | 15 | 199 | MENSUAL |
| Teléfono Pau y David | 15 | 444 | MENSUAL |
| Agua | 33 | 214 | BIMESTRAL |
| Electricidad | 33 | 1,000–2,693 | BIMESTRAL |
| Seguro Norma | 33 | 2,500 | MENSUAL |
| Seguro Pau+David+Agus | 33 | 3,500 | MENSUAL |
| Seguro Santi | 33 | 500 | MENSUAL |
| Mesada David | 26 | 8,000–15,000 | MENSUAL |
| Comida | 33 | 4,500–5,000 | QUINCENAL |
| Despensa | 33 | 1,950–2,500 | QUINCENAL |
| Limpieza | 17 | 3,000 | QUINCENAL |
| Gasolina Camioneta | 17 | 1,250–1,400 | QUINCENAL |
| Gasolina Cochecito | 16 | 1,400–1,600 | QUINCENAL |

**Cuotas activas** (candidatos para `InstallmentPlan`):

| Concepto | Progreso observado | Monto cuota | Total estimado |
|---|---|---|---|
| Préstamo Omar | 3 → 10 | 5,500 | 10 cuotas × 5,500 = 55,000 MXN |
| Mercado libre | "X de 12" | 1,342 | 12 cuotas |
| Buró de crédito | "2 de 3" | variable | 3 cuotas |
| Liverpool | 25 apariciones | 2,000–2,964 | BNPL rotativo |
| Sears | 23 apariciones | 1,300–1,483 | BNPL rotativo |
| Coppel | 17 apariciones | 380–3,660 | BNPL rotativo |
| Walmart | 20 apariciones | 7,500 | BNPL rotativo |
| Banamex Clásica | 22 apariciones | 1,500 | revolving |

### A.2 Miembros normalizados (semilla inicial)

```json
[
  { "display_name": "Benjamín", "short_aliases": ["Benji", "Benjamin"],
    "role": "PAYER_ADULT", "default_income_mxn": 45000 },
  { "display_name": "Norma",    "short_aliases": ["Normita"],
    "role": "PAYER_ADULT", "default_income_mxn": 60000 },
  { "display_name": "Pau",      "short_aliases": ["Paulina", "Pau"],
    "role": "BENEFICIARY_DEPENDENT" },
  { "display_name": "David",    "short_aliases": ["David"],
    "role": "BENEFICIARY_DEPENDENT" },
  { "display_name": "Agustín",  "short_aliases": ["Agus", "Agustin"],
    "role": "BENEFICIARY_DEPENDENT" },
  { "display_name": "Santiago", "short_aliases": ["Santi"],
    "role": "BENEFICIARY_DEPENDENT" },
  { "display_name": "Araceli",  "short_aliases": ["Araceli"],
    "role": "EXTERNAL_SERVICE" },
  { "display_name": "Omar",     "short_aliases": ["Omar"],
    "role": "EXTERNAL_CREDITOR" },
  { "display_name": "Jaudiel",  "short_aliases": ["jaudiel"],
    "role": "EXTERNAL_DEBTOR" },
  { "display_name": "Mary",     "short_aliases": ["Mary"],
    "role": "EXTERNAL_SERVICE" }
]
```

### A.3 Métodos de pago normalizados (semilla)

```json
[
  { "display_name": "Banamex Débito",   "kind": "DEBIT_ACCOUNT",           "issuer": "Citibanamex" },
  { "display_name": "BBVA Débito",       "kind": "DEBIT_ACCOUNT",           "issuer": "BBVA México" },
  { "display_name": "Banamex Clásica",   "kind": "CREDIT_CARD",             "issuer": "Citibanamex" },
  { "display_name": "Mercado Pago",      "kind": "DIGITAL_WALLET",          "issuer": "Mercado Pago" },
  { "display_name": "Mercado Libre BNPL","kind": "BNPL_INSTALLMENT",        "issuer": "Mercado Libre" },
  { "display_name": "Coppel",            "kind": "DEPARTMENT_STORE_CARD",   "issuer": "Coppel" },
  { "display_name": "Liverpool",         "kind": "DEPARTMENT_STORE_CARD",   "issuer": "Liverpool" },
  { "display_name": "Sears",             "kind": "DEPARTMENT_STORE_CARD",   "issuer": "Sears" },
  { "display_name": "Walmart",           "kind": "DEPARTMENT_STORE_CARD",   "issuer": "Walmart" },
  { "display_name": "Klar",              "kind": "DIGITAL_WALLET",          "issuer": "Klar" },
  { "display_name": "Efectivo",          "kind": "CASH" },
  { "display_name": "Ahorro Empresa",    "kind": "EMPLOYER_SAVINGS_FUND" }
]
```

---

## Apéndice B: Plantilla de Migración de Datos

### B.1 Algoritmo de importación del XLSX existente

```python
def import_legacy_xlsx(filepath: str, household: Household) -> ImportReport:
    wb = openpyxl.load_workbook(filepath, data_only=True)
    report = ImportReport()

    for sheet_name in wb.sheetnames:
        # 1. Parsear fechas desde el nombre de la hoja (tolerante a typos)
        period = parse_quincena_name(sheet_name)
        if not period:
            report.warn(f"Skip sheet '{sheet_name}': no reconocible")
            continue

        quincena = upsert_quincena(household, period)

        # 2. Parsear cabecera (ingresos)
        ws = wb[sheet_name]
        income_benji = ws['E4'].value or 0
        income_norma = ws['E5'].value or 0
        income_extra = ws['E9'].value or 0
        register_income(quincena, income_benji, income_norma, income_extra)

        # 3. Recorrer las 10 tablas categóricas conocidas por offset
        for cat_spec in KNOWN_CATEGORY_BLOCKS:
            for row in ws.iter_rows(min_row=cat_spec.start_row,
                                     max_row=cat_spec.end_row,
                                     values_only=True):
                concept = row[cat_spec.concept_col]
                projected = row[cat_spec.projected_col]
                norma_share = row[cat_spec.norma_col]
                benjamin_share = row[cat_spec.benjamin_col]

                if not concept or concept in ('Subtotal', 'Projected Cost'):
                    continue

                # 4. Crear gasto con atribuciones inferidas
                exp = Expense(
                    concept=concept,
                    amount_mxn=projected or 0,
                    category_id=resolve_category(cat_spec.code, concept),
                    quincena_id=quincena.id,
                    occurred_at=quincena.start_date,
                    status='POSTED',
                    payment_method_id=UNKNOWN_METHOD_ID
                )

                # Atribución pagador explícita
                add_payer_attribution(exp, NORMA_ID,    norma_share)
                add_payer_attribution(exp, BENJAMIN_ID, benjamin_share)

                # Atribución beneficiario desde parser textual (§4.2)
                beneficiaries = infer_beneficiaries_from_concept(concept, household)
                if beneficiaries:
                    add_beneficiary_split(exp, beneficiaries)
                else:
                    add_beneficiary_split(exp, [NORMA_ID, BENJAMIN_ID])  # default hogar

                persist(exp)

        # 5. Detectar patrones de cuota en concepto → InstallmentPlan
        detect_and_link_installment_plans(quincena)

    # 6. Post-procesar: entrenar plantillas recurrentes sobre todo el histórico importado
    candidates = detect_recurrence_candidates(all_expenses_for_household(household))
    for c in candidates:
        if c.confidence >= 0.75:
            promote_to_template(c)

    return report
```

### B.2 Mapeo de bloques categóricos (constante de importación)

```python
KNOWN_CATEGORY_BLOCKS = [
    CategoryBlock("HOUSING",        start_row=13, end_row=22, concept_col=1, projected_col=2, norma_col=3, benjamin_col=4),
    CategoryBlock("ENTERTAINMENT",  start_row=13, end_row=21, concept_col=6, projected_col=7, norma_col=8, benjamin_col=9),  # depende de plantilla A/B
    CategoryBlock("TRANSPORTATION", start_row=27, end_row=33, concept_col=1, projected_col=2, norma_col=3, benjamin_col=4),
    CategoryBlock("LOANS",          start_row=27, end_row=32, concept_col=6, projected_col=7, actual_col=8, difference_col=9),
    CategoryBlock("SEGUROS",        start_row=37, end_row=40, concept_col=1, projected_col=2, norma_col=3, benjamin_col=4),
    CategoryBlock("TRANSFERENCIAS", start_row=36, end_row=39, concept_col=6, projected_col=7, norma_col=8, benjamin_col=9),
    CategoryBlock("FOOD",           start_row=44, end_row=46, concept_col=1, projected_col=2, norma_col=3, benjamin_col=4),
    CategoryBlock("OTHERS_SAVINGS", start_row=43, end_row=45, concept_col=6, projected_col=7, actual_col=8, difference_col=9),
    CategoryBlock("PETS",           start_row=50, end_row=54, concept_col=1, projected_col=2, norma_col=3, benjamin_col=4),
    CategoryBlock("GIFTS",          start_row=50, end_row=51, concept_col=6, projected_col=7, actual_col=8, difference_col=9),
    CategoryBlock("LEGAL",          start_row=55, end_row=59, concept_col=6, projected_col=7, actual_col=8, difference_col=9),
    CategoryBlock("PERSONAL_CARE",  start_row=59, end_row=66, concept_col=1, projected_col=2, actual_col=3, difference_col=4),
]
```

### B.3 Parser de nombre de hoja → período

```python
def parse_quincena_name(name: str) -> Optional[Period]:
    # Normalizar: quitar paréntesis, corregir typos conocidos, collapsing spaces
    normalized = re.sub(r'[()]', '', name)
    normalized = normalized.replace('febrereo', 'febrero').replace('septiembre', 'septiembre')
    normalized = re.sub(r'\s+', ' ', normalized).strip().lower()

    # Patrón canónico: "quincena X al Y de MES [YYYY]"
    match = re.match(
        r'quin(?:cena|\.)\s*(\d+)\s*al\s*(\d+)(?:\s*de)?\s+(\w+)(?:\s+(\d{4}))?',
        normalized
    )
    if not match:
        return None

    start_day, end_day, month_name, year = match.groups()
    month = SPANISH_MONTHS[month_name[:3]]
    year = int(year) if year else infer_year_from_context()
    half = 'FIRST' if int(start_day) == 1 else 'SECOND'

    return Period(year, month, half,
                   start=date(year, month, int(start_day)),
                   end=date(year, month, int(end_day)))
```

### B.4 Validaciones post-importación

```yaml
assertions:
  - every_expense_has_quincena: COUNT(expense WHERE quincena_id IS NULL) == 0
  - attribution_sums_correct:
      query: SELECT expense_id, role, SUM(share_bps) FROM expense_attribution GROUP BY expense_id, role
      expect: all rows SUM = 10000
  - no_orphan_expenses: every expense.payment_method_id resolves
  - quincena_totals_match_excel:
      for each quincena: actual_expenses_mxn equals legacy K62 within 1 MXN tolerance
```

---

**Fin del documento.**
