# Análisis Maestro: Aplicación de Presupuesto Familiar Quincenal

**Documento consolidado a partir de tres especificaciones fundacionales**
**Fecha de síntesis**: 18 de abril de 2026
**Estado**: Documento de referencia para iniciar desarrollo

---

## Tabla de contenidos

1. [Visión General del Proyecto](#1-visión-general-del-proyecto)
2. [El Problema que se Resuelve](#2-el-problema-que-se-resuelve)
3. [Usuarios y Contexto del Hogar](#3-usuarios-y-contexto-del-hogar)
4. [Arquitectura de Plataformas](#4-arquitectura-de-plataformas)
5. [Modelo de Datos](#5-modelo-de-datos)
6. [Motor Determinista (Inteligencia sin LLMs)](#6-motor-determinista-inteligencia-sin-llms)
7. [Módulo de IA On-Device (Gemini Nano)](#7-módulo-de-ia-on-device-gemini-nano)
8. [Flujos de Usuario Principales](#8-flujos-de-usuario-principales)
9. [Ecosistema Wear OS](#9-ecosistema-wear-os)
10. [Sistema Analítico y Dashboards](#10-sistema-analítico-y-dashboards)
11. [Diseño Visual y UX](#11-diseño-visual-y-ux)
12. [Catálogo de Requerimientos Funcionales](#12-catálogo-de-requerimientos-funcionales)
13. [Restricciones Técnicas y de Privacidad](#13-restricciones-técnicas-y-de-privacidad)
14. [Mapa de Dependencias entre Documentos](#14-mapa-de-dependencias-entre-documentos)
15. [Riesgos y Áreas de Ambigüedad](#15-riesgos-y-áreas-de-ambigüedad)
16. [Hoja de Ruta Sugerida para Desarrollo](#16-hoja-de-ruta-sugerida-para-desarrollo)

---

## 1. Visión General del Proyecto

### ¿Qué es?

Una **aplicación multiplataforma de gestión de presupuesto familiar quincenal** diseñada para reemplazar un libro de Excel (`presupuesto_2_5.xlsx`) de 33 hojas que una familia mexicana ha mantenido manualmente desde enero 2025. La app digitaliza, automatiza y extiende ese sistema con inteligencia determinista y, opcionalmente, un asistente conversacional on-device.

### Plataformas objetivo

| Plataforma | Dispositivo primario | Framework |
|---|---|---|
| **Android (Foldable)** | Pixel 9 Pro Fold | Kotlin 2.0 + Jetpack Compose + Material 3 Expressive |
| **Wear OS** | Pixel Watch 3 | Compose for Wear OS 1.4 + Protolayout 1.2 |
| **Web** | Navegador desktop/mobile | TypeScript 5.4 + React 19 + Tailwind 4 + shadcn/ui |

### Tres pilares documentados

```
┌─────────────────────────────────────────┐
│  ESPECIFICACION_PRESUPUESTO_APP.md      │  ← Modelo de datos, lógica de negocio,
│  (Doc Base – Datos y Negocio)           │     diagnóstico del Excel, requerimientos
├─────────────────────────────────────────┤
│  ESPECIFICACION_UX_HARDWARE_APP.md      │  ← UX, hardware, captura rápida, Wear OS,
│  (Doc Complementario – UX & Hardware)   │     analíticas visuales, motor determinista
├─────────────────────────────────────────┤
│  ADENDA_IA_ON_DEVICE.md                 │  ← Asistente conversacional con Gemini Nano,
│  (Apéndice E – IA Local)               │     RAG local, tool calling emulado
└─────────────────────────────────────────┘
```

> [!IMPORTANT]
> Los tres documentos están diseñados como **especificaciones para agentes de generación de código (vibe coding)**. Contienen código Kotlin/SQL/Python funcional que sirve como referencia de implementación, no solo como pseudocódigo conceptual.

---

## 2. El Problema que se Resuelve

### 2.1 Estado actual: un Excel de 33 hojas

El hogar gestiona su presupuesto en un libro Excel con una hoja por quincena. Se han identificado **12 fricciones estructurales** que la app resuelve:

| # | Fricción | Impacto | Solución en la app |
|---|---|---|---|
| 1 | Creación manual de hoja por quincena | Typos (`febrereo`), `#REF!`, paréntesis espurios | Entidad `Quincena` autogenerada con máquina de estados |
| 2 | Beneficiario codificado en el nombre del concepto | `"Telefono Santi"`, `"Pau, David, Agus"` | Multi-select con chips + parser textual como sugerencia |
| 3 | Numeración manual de cuotas | `Prestamo Omar 3, 4, 5...` copiado a mano | `InstallmentPlan` con contador automático |
| 4 | Conciliación bancaria en columnas libres (N–U) | Sin esquema, fórmulas ad-hoc | Saldo por `PaymentMethod` en tiempo real |
| 5 | `TAXES` mal etiquetado | Contiene mesadas a hijos, no impuestos | Categoría `TRANSFERENCIAS_FAMILIARES` |
| 6 | Agregación intertemporal imposible | No se puede saber "¿cuánto gastamos en David en 2025?" | Log unificado de `Expense` + queries parametrizadas |
| 7 | Registro diferido (solo cuando abren Excel) | Olvido de gastos | Wear OS, Quick Tap overlay, widgets |
| 8 | Sin visualización gráfica | Todo tabular numérico | Heatmaps, donuts, Sankey, treemaps |
| 9 | Sin estado granular plan vs ejecutado | Columnas confunden Projected/Actual | Status discreto: `PLANNED` → `POSTED` → `RECONCILED` |
| 10 | Método de pago no ligado al gasto | Se deduce de columnas auxiliares | `payment_method_id` obligatorio |
| 11 | Préstamos otorgados subdocumentados | Filas 116-120 sin trazabilidad | Entidad `Loan` con recordatorios |
| 12 | Sin vista colaborativa | Cada quien abre su propia copia | Modelo `Household` multi-miembro con sync |

### 2.2 Anatomía del libro Excel

Cada hoja tiene esta estructura vertical:

```
Filas 1–10    CABECERA (ingresos + dashboard de balance)
Filas 12–23   HOUSING / ENTERTAINMENT (tablas pareadas)
Filas 26–34   TRANSPORTATION / LOANS
Filas 36–42   PERSONAL (seguros) / TAXES (transferencias familiares)
Filas 43–47   FOOD / OTHERS (savings)
Filas 49–55   PETS / GIFTS
Filas 57–65   PERSONAL CARE / LEGAL
Filas 61–67   TOTALES (gasto quincenal · sobra · diferencia)
Filas 68–88   CONCILIACIÓN MANUAL (balances bancarios)
Filas 90–127  HISTÓRICO (ahorro efectivo · préstamos otorgados)
```

Existen **dos plantillas** de hoja: **A** (quincena 16-fin) y **B** (quincena 1-15), que difieren en columnas auxiliares y en una categoría (`TAXES` ↔ `SCHOOL`).

---

## 3. Usuarios y Contexto del Hogar

### 3.1 Miembros del hogar

| Rol | Nombre | Aliases | Ingreso / Notas |
|---|---|---|---|
| **Adulto-pagador** | Benjamín | Benji, Benjamin | $45,000 MXN quincenal |
| **Adulto-pagador** | Norma | Normita | $60,000 MXN quincenal |
| **Dependiente** | Pau | Paulina | Estudiante — mesada + seguro |
| **Dependiente** | David | David | Mesada $8,000–$15,000/mes |
| **Dependiente** | Agustín | Agus, Agustin | Seguro grupal |
| **Dependiente** | Santiago | Santi | Teléfono + inscripciones + mesada |
| Externo-servicio | Araceli | — | Empleada del hogar |
| Externo-acreedor | Omar | — | Préstamo personal (10 cuotas × $5,500) |
| Externo-deudor | Jaudiel | jaudiel | Préstamo otorgado por el hogar |
| Externo-servicio | Mary | — | No determinado |

### 3.2 Métodos de pago identificados

| Tipo | Instrumentos |
|---|---|
| Débito | Banamex, BBVA |
| Crédito bancario | Banamex Clásica |
| Departamentales | Coppel, Liverpool, Sears, Walmart |
| Digital/BNPL | Mercado Pago (monedero + CC), Mercado Libre, Klar |
| Otro | Efectivo, Ahorro empresa |

### 3.3 Moneda y zona horaria

- **Moneda única**: MXN (peso mexicano). Sin conversiones.
- **Timezone**: `America/Mexico_City`.
- **Calendario quincenal**: ancla `CALENDAR` (1-15 / 16-fin de mes).

---

## 4. Arquitectura de Plataformas

### 4.1 Stack tecnológico por capa

| Capa | Android (Fold) | Wear OS (Watch 3) | Web |
|---|---|---|---|
| Lenguaje | Kotlin 2.0 + Compose | Kotlin + Compose for Wear | TypeScript 5.4 |
| UI | Jetpack Compose 1.7 + M3 Expressive | Compose for Wear 1.4 + Protolayout | React 19 + Tailwind 4 + shadcn/ui |
| Persistencia | Room 2.7 + SQLite (WAL mode) | DataStore + Tiles state | IndexedDB (Dexie) + sync backend |
| Sync | WorkManager + protobuf/gRPC | Wear Data Layer API + MessageClient | HTTP/2 + WebSocket |
| Estado | ViewModel + StateFlow | TileService + ComplicationDataSourceService | Zustand + TanStack Query |
| Gráficas | Vico 2.0 (Compose-native) | Canvas + Protolayout | Recharts + D3 |

### 4.2 Arquitectura lógica compartida

```
┌──────────────────────────────────────────────────┐
│            Core Domain (Kotlin Multiplatform)     │
│  Targets: androidTarget, wasmJs                   │
│  Contenido:                                       │
│  • Entidades (§2 del doc presupuesto)             │
│  • Rule Engine de atribución (§5 del doc UX)      │
│  • Recurrence Matcher                             │
│  • Quincena State Machine                         │
└────────────┬─────────────┬───────────────────────┘
             │             │
     ┌───────▼──────┐  ┌───▼──────────┐
     │  Android App │  │  Wear OS App │
     └──────┬───────┘  └──────┬───────┘
            │                 │
            └── Data Layer API (protobuf) ──┐
                                            │
                                     ┌──────▼───────┐
                                     │ Web Frontend │
                                     └──────────────┘
```

> [!NOTE]
> La lógica determinista de atribución y recurrencia vive en el módulo KMP compartido. Esto garantiza que la misma entrada produce el mismo output en todas las superficies.

### 4.3 Módulos de la app Android

```
:app                  ← Application entry, DI, navegación
:core-domain          ← Entidades, use cases, state machines (KMP)
:core-data            ← Room DAOs, repositorios, sync
:feature-quincena     ← QuincenaScreen, dashboard
:feature-capture      ← Quick Capture overlay, formularios
:feature-wallets      ← WalletsScreen, conciliación
:feature-analytics    ← Módulos analíticos A–F
:feature-members      ← Gestión de miembros
:ai-local             ← Gemini Nano / AICore (Apéndice E)
:wear                 ← App completa Wear OS + Tiles + Complications
```

### 4.4 Contratos de latencia

| Operación | P50 | P95 |
|---|---|---|
| Cold start (Fold) | 420 ms | 900 ms |
| Registrar gasto (FAB → confirmación) | 2.1 s | 4.0 s |
| Quick Tap overlay | **1.4 s** | 2.5 s |
| Refresh de Tile (Watch) | 180 ms | 400 ms |
| Sync Watch↔Fold | 800 ms | 2.5 s |
| Query analítica (6 meses) | 60 ms | 150 ms |
| IA: round-trip completo (enter → render) | 1.4 s | 3.0 s |

---

## 5. Modelo de Datos

### 5.1 Principios de diseño

1. **Log inmutable de `Expense`**: todas las hojas colapsan en una colección indexable por fecha.
2. **Normalización total**: categorías, beneficiarios, pagadores y métodos de pago son tablas separadas.
3. **Relaciones N:M para atribución**: un gasto puede tener múltiples beneficiarios y pagadores con reparto proporcional en basis points (0–10,000 = 0–100%).
4. **Recurrencia como entidad de primer orden**: plantillas con generación proactiva de instancias esperadas.
5. **Agnóstico de frontend**: esquemas válidos para SQLite/Room, Firestore y Dexie.

### 5.2 Entidades principales (11 tablas)

```
┌──────────────┐
│   Household  │  ← Raíz de aislamiento. Una familia = un household.
└──────┬───────┘
       │
       ├── Member              (roles: PAYER_ADULT, BENEFICIARY_DEPENDENT,
       │                        EXTERNAL_CREDITOR, EXTERNAL_DEBTOR, EXTERNAL_SERVICE)
       │
       ├── Category            (jerárquica, parent_id self-reference)
       │                       (kinds: EXPENSE_FIXED, EXPENSE_VARIABLE,
       │                        EXPENSE_INSTALLMENT, TRANSFER_INTRA_HOUSEHOLD,
       │                        SAVINGS, INCOME, LOAN_RECEIVABLE)
       │
       ├── PaymentMethod       (kinds: DEBIT_ACCOUNT, CREDIT_CARD,
       │                        DEPARTMENT_STORE_CARD, BNPL_INSTALLMENT,
       │                        DIGITAL_WALLET, CASH, EMPLOYER_SAVINGS_FUND)
       │
       ├── Quincena            (período operativo, DFA con 4 estados)
       │
       ├── RecurrenceTemplate  (plantillas con cadencia + confidence score)
       │
       ├── InstallmentPlan     (planes de cuotas con contador automático)
       │
       ├── Loan                (préstamos otorgados por el hogar)
       │
       ├── SavingsGoal         (metas de ahorro)
       │
       └── Expense             (registro atómico de gasto)
            │
            └── ExpenseAttribution  (tabla puente 1:N, roles BENEFICIARY y PAYER)
```

### 5.3 Diagrama ER

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

### 5.4 Modelo dual de atribución

Cada gasto lleva **dos particiones independientes** entre miembros:

- **Partición BENEFICIARIO**: quién recibe/consume el bien o servicio.
- **Partición PAGADOR**: quién desembolsa el dinero.

Ambas deben sumar exactamente 10,000 basis points (100%).

**Ejemplo**:
```yaml
Gasto: "Seguro médico" $3,500 MXN
Beneficiarios:
  - Pau:     33.33% ($1,166.55)
  - David:   33.33% ($1,166.55)
  - Agustín: 33.34% ($1,166.90)
Pagadores:
  - Norma:   100% ($3,500)
```

### 5.5 Máquina de estados de la Quincena

```
PROVISIONED ──(materializar plantillas)──> ACTIVE ──(usuario cierra)──> CLOSING_REVIEW ──(confirma)──> CLOSED
                                                                                                      (inmutable)
```

- **PROVISIONED**: se crea T-3 días antes del inicio, materializa gastos `PLANNED`.
- **ACTIVE**: solo una activa por household a la vez.
- **CLOSING_REVIEW**: validación de gastos pendientes, varianza, saldos.
- **CLOSED**: datos congelados, alimenta analíticas históricas.

### 5.6 Taxonomía de categorías (semilla)

```
HOUSING > {Hipoteca, Internet, Electricidad, Agua, Fraccionamiento, Teléfono}
ENTERTAINMENT > {Streaming [Netflix, HBO, Prime, Disney+], Software [Adobe, Coursera, KIGO], Otros}
TRANSPORTATION > {Gasolina, Insurance, Licensing, Fuel, Maintenance}
LOANS > {Coppel, Sears, Liverpool, Banamex Clásica, Walmart, Mercado Libre, etc.}
SEGUROS_MEDICOS > {Benji, Norma, Pau+David+Agus, Santi}
TRANSFERENCIAS_FAMILIARES > {David, Santiago, Pau, Coche, Inscripciones}
FOOD > {Comida, Despensa, Limpieza}
PETS > {Comida Gatas, Grooming, Toys, Veterinario}
SAVINGS > {Ahorro empresa, Tarjeta de ahorro, Efectivo, Retirement, Investment}
INGRESOS > {Sueldo Benjamin, Sueldo Norma, Extras}
PRESTAMOS_OTORGADOS > {jaudiel, oficinas}
```

---

## 6. Motor Determinista (Inteligencia sin LLMs)

> [!IMPORTANT]
> **Restricción dura**: toda "inteligencia" se implementa con reglas deterministas. No hay dependencia de LLMs en runtime para las funciones core. La IA (§7) es un add-on opcional.

### 6.1 Algoritmo de sugerencia de atribución

Al capturar un gasto, la app pre-llena categoría, método de pago, beneficiarios y pagadores usando un pipeline de 4 pasos:

```
Paso 1 → Exact match por hash (concepto normalizado + bucket de monto)
         Si hit_count >= 3 → confianza alta, retorna inmediato

Paso 2 → Fuzzy match por Jaro-Winkler sobre concepto
         Score compuesto: 45% similitud texto + 25% cercanía monto +
                          20% frecuencia + 10% recencia
         Si score >= 0.78 → retorna sugerencia

Paso 3 → Reglas declarativas (YAML: attribution_rules.yaml)
         Patrones por regex/keywords para casos conocidos
         Ej: "teléfono santi" → HOUSING.TELEFONO, beneficiario: Santi

Paso 4 → Default del household (confianza baja 0.2)
```

### 6.2 Tabla de memoria de atribución

```sql
attribution_memory (
    key_hash BLOB PK,          -- sha256(concepto_norm || bucket_monto)
    concept_norm TEXT,          -- "netflix"
    amount_bucket INT,         -- bucket de 50 MXN
    category_id, payment_method_id, beneficiary_ids, payer_split,
    hit_count INT,             -- crece con cada confirmación
    confidence REAL,           -- converge a 0.99 si no hay conflictos
    last_seen_at INT
)
```

### 6.3 Aprendizaje incremental determinista

Cada vez que el usuario confirma un gasto:
- Si la atribución coincide con la memoria → incrementa `hit_count` y `confidence`.
- Si difiere → **adopta la nueva** (LWW), resetea contador, baja confianza a 0.4.
- Convergencia: la confianza crece monotónicamente tras 3-5 confirmaciones sin conflicto.
- **Todo es trazable**: el usuario puede ver "¿Por qué me sugirió esto?" con la regla o memoria exacta.

### 6.4 Motor de recurrencia

Detecta automáticamente gastos repetidos y propone convertirlos en plantillas:

1. **Identificación**: agrupa por concepto normalizado + categoría.
2. **Análisis temporal**: calcula mediana de intervalos y coeficiente de variación.
3. **Mapeo de cadencia**: 14-16 días → quincenal, 28-32 → mensual, 58-64 → bimestral.
4. **Score de confianza**: `0.4 * sigmoid(muestras-3) + 0.3 * regularidad + 0.3 * estabilidad_monto`.
5. **Propuesta**: si confidence >= 0.75, propone al usuario crear plantilla.

### 6.5 Motor de cuotas

Reemplaza la numeración manual `Préstamo Omar 3 → 4 → 5`:
- `InstallmentPlan` con contador automático.
- Al postear una cuota: `current_installment += 1`.
- Si llega al total: marca `PAID_OFF` y notifica.
- Calcula tabla de amortización francesa cuando se conoce la tasa.

### 6.6 Detector de outliers (análisis de varianza)

Usa **Mediana + MAD** (Median Absolute Deviation) en lugar de media + σ:
- Robusto ante pocos datos y outliers extremos.
- Z-score robusto: `|z| < 0.5` = normal, `0.5–1.5` = atención, `>= 1.5` = anomalía.

### 6.7 Pronóstico de liquidez

Media móvil ponderada (más peso a reciente) + intervalo de confianza +/-1.5 * MAD.

---

## 7. Módulo de IA On-Device (Gemini Nano)

> [!NOTE]
> Este módulo es **completamente opcional**. Si el dispositivo no soporta AICore, el usuario desactiva el módulo, o la cuota de batería se agota, la app funciona al 100% usando el motor determinista del §6.

### 7.1 Principios rectores

1. **La IA no decide nada**: solo traduce lenguaje natural → intent estructurada. La ejecución la hace el motor determinista.
2. **Contexto siempre acotado**: el prompt se ensambla con un retriever SQL, nunca se vuelca el ledger completo.
3. **Ventana pequeña**: <=4,000 tokens de entrada, <=240 tokens de salida (Gemini Nano-v2 rinde mejor sub-256).
4. **Zero network para inferencia**: todo corre en el edge TPU del Tensor G4, dentro de Private Compute Core.
5. **Structured output**: el modelo emite JSON contra un schema fijo (tool calling emulado).
6. **Todo evento explicable**: cada respuesta incluye trazabilidad (intent, rows_considered, latency).

### 7.2 Pipeline RAG local

```
Pregunta del usuario
        |
        v
+---------------------+
| PromptSanitizer     |  Limpia inyecciones, normaliza acentos
+---------+-----------+
          v
+---------------------+     SQL parametrico
| RagContextBuilder   |--------------------->  Room / SQLite
| (QuestionClassifier |     filas relevantes   (max 6 dimensiones)
|  heuristico lexico) |
+---------+-----------+
          v
+---------------------+
| ContextSerializer   |  Formato tabular compacto (densidad token/info)
+---------+-----------+
          v
+---------------------+
| PromptAssembler     |  System + Context + Schema + Pregunta (<=4K tokens)
+---------+-----------+
          v
+---------------------+
| AiCoreService       |  Gemini Nano-v2 en edge TPU
| (T=0.05, topK=16)  |  Cuasi-determinista
+---------+-----------+
          v  JSON validado
+---------------------+
| IntentDispatcher    |  Ejecuta funcion del motor determinista
+---------+-----------+
          v
Respuesta renderizada + trazabilidad
```

### 7.3 Catálogo de intents (18 total)

| Intent | Pregunta típica | Motor que ejecuta |
|---|---|---|
| `GET_CATEGORY_REMAINING` | "¿Cuánto queda para gasolina?" | `analytics.remainingForCategory` |
| `GET_TOP_SPENDER` | "¿Quién gastó más?" | `analytics.topBeneficiary` |
| `GET_SPEND_BY_MEMBER` | "¿Cuánto lleva David?" | `analytics.spendByMember` |
| `GET_WALLET_BALANCE` | "Saldo de Banamex" | `wallets.balance` |
| `GET_INSTALLMENT_STATUS` | "¿En qué cuota voy con Omar?" | `installments.status` |
| `PROJECT_SAVINGS_IF` | "¿Cuánto ahorro si no gasto en entretenimiento?" | `forecast.savingsIfCategoryFrozen` |
| `COMPARE_QUINCENAS` | "¿Gasto más que la quincena pasada?" | `variance.compareVsBaseline` |
| `EXPLAIN_VARIANCE` | "¿Por qué gasté más en comida?" | `variance.explainCategory` |
| `LIST_UPCOMING_INSTALLMENTS` | "¿Qué cuotas vienen?" | `installments.upcoming` |
| `SUMMARIZE_QUINCENA` | "Resúmeme esta quincena" | `analytics.snapshotSummary` |
| `FORECAST_NEXT_QUINCENA` | "Pronóstico próxima quincena" | `forecast.nextQuincena` |
| `LIST_RECURRENCE_TEMPLATES` | "¿Qué gastos son recurrentes?" | `recurrence.listActive` |
| `LIST_RECENT_EXPENSES` | "Gastos recientes" | `expenses.recent` |
| `GET_INTEREST_PAID` | "¿Cuánto he pagado de intereses?" | `analytics.interestPaid` |
| `GET_LOANS_RECEIVABLE` | "¿Quién me debe?" | `loans.receivable` |
| `GET_CATEGORY_SPENT_TO_DATE` | "¿Cuánto llevo en housing?" | `analytics.spentSince` |
| `GET_WALLET_UTILIZATION` | "Utilización de Banamex" | `wallets.utilization` |
| `UNKNOWN` | Pregunta fuera de dominio | Fallback amigable |

### 7.4 Fallback cuando falla el modelo

1. **Intento A**: Reparación por patrón (cierra JSON truncado).
2. **Intento B**: Pasa la pregunta al motor determinista como búsqueda textual.
3. **Intento C**: Mensaje explícito con CTA a búsqueda manual.

**Nunca se reintenta contra el LLM automáticamente** (respeta cuota + batería).

### 7.5 Degradación ante condiciones adversas

| Condición | Comportamiento |
|---|---|
| Sin AICore | Módulo oculto. Solo búsqueda determinista. |
| AICore descargando | Chat deshabilitado con banner de progreso. |
| `BUSY` | Un retry (250ms backoff); si falla, snackbar + 30s cooldown. |
| Cuota diaria agotada | Chat deshabilitado hasta medianoche. |
| JSON inválido | Degrada a motor determinista. |
| Batería < 15% | Solo usa IA con tap largo explícito. |
| Usuario desactiva | Módulo desmontado. |

### 7.6 Golden test suite

24 pares (pregunta → intent esperada). Se ejecuta al detectar cambio de versión del modelo. Si pass-rate < 92%, el chat se desactiva automáticamente.

---

## 8. Flujos de Usuario Principales

### 8.1 Captura de gastos — jerarquía por velocidad

| Ranking | Superficie | Trigger | Latencia | Dispositivo |
|---|---|---|---|---|
| 1 | **Quick Tap overlay** | Doble golpe dorso | 1.4 s | Pixel 9 Pro Fold |
| 2 | Tile "Registrar gasto" | Swipe lateral | 2.0 s | Pixel Watch 3 |
| 3 | Complicación watch face | Tap en esfera | 2.3 s | Pixel Watch 3 |
| 4 | Widget homescreen | Tap quick row | 2.8 s | Android |
| 5 | Atajo de Assistant | "Hey Google, registra gasto" | 3.1 s | Android |
| 6 | Notification quick reply | Tap respuesta | 3.5 s | Android |
| 7 | App completa (FAB) | Abrir app + FAB | 4.8 s | Android/Web |

**Meta**: desde que el gasto ocurre hasta que queda registrado <= 5 segundos en el 80% de los casos.

### 8.2 Quick Tap overlay (estrella del sistema)

```
+---------------------------------------------------+
| ⚡ Gasto rápido                          x cerrar |
+---------------------------------------------------+
|  $ [___________]  <- focus inicial, keypad num     |
|                                                    |
|  [Comida v]  [Banamex v]  [Norma 👤]              |
|                                                    |
|  Sugerencias (últimos 3):                          |
|  • Despensa  $1,950   Banamex                     |
|  • Gasolina  $1,400   Efectivo                    |
|  • Netflix   $329     Banamex Clásica             |
|                                                    |
|                         [  Guardar  (Enter)  ]     |
+---------------------------------------------------+
```

Implementado como: `QuickCaptureActivity` (Theme.Transparent) → `OverlayService` (foreground) → `WindowManager.addView()` con `TYPE_APPLICATION_OVERLAY`.

**Permisos**: `SYSTEM_ALERT_WINDOW`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE_SPECIAL_USE`.

### 8.3 Ciclo quincenal completo

```
Día -3   WorkManager crea Quincena(PROVISIONED) + materializa PLANNED desde plantillas
Día  0   AlarmManager → PROVISIONED → ACTIVE + push notification + Tile refresh
Día 0-N  Captura normal de gastos (POSTED) + motor de atribución sugiere
Día  N   Notification 22:00 "¿Cerrar quincena?"
Día N+1  Si aún ACTIVE → daily reminder
         CLOSING_REVIEW → usuario valida → CLOSED (inmutable)
```

### 8.4 Chat con asistente IA (Fold desplegado)

En el inner display del Pixel 9 Pro Fold, layout dual-pane:

```
+-------------- Chat (40%) ----------------+---------- Dashboard dinámico (60%) ----------+
|                                           |                                               |
|  "¿Cuánto llevo en comida?"              |  FOOD  $7,450/$9,500  78%                    |
|                                           |  [barra de progreso]                         |
|  Resp: $7,450 de $9,500 (78%)           |                                               |
|  Te quedan $2,050                        |  Gastos recientes de FOOD:                    |
|                                           |  • Despensa  $2,100  18 abr                  |
|  [¿Por qué? → trazabilidad]             |  • Comida    $520   17 abr                   |
|                                           |                                               |
|  Sugerencias contextuales:                |                                               |
|  [¿Por qué subió?] [vs marzo]           |  [ver todos los gastos de FOOD]              |
+-------------------------------------------+-----------------------------------------------+
```

---

## 9. Ecosistema Wear OS

### 9.1 Superficies nativas

| Superficie | Implementación | Uso |
|---|---|---|
| **App completa** | Compose for Wear OS | Registro detallado, revisión |
| **Tiles** (5) | TileService + Protolayout | Paneles swipeables desde watch face |
| **Complications** (5) | ComplicationDataSourceService | Datos en la esfera del reloj |
| **Ongoing Activity** | OngoingActivity API | Presencia persistente durante quincena activa |

### 9.2 Catálogo de Tiles

1. **BudgetRemainingTile**: presupuesto restante + % ejecución.
2. **SavingsProgressTile**: progreso de ahorro trimestral (arc segmented).
3. **UpcomingInstallmentsTile**: próximas 3 cuotas con deep links.
4. **QuickCaptureTile**: botón grande para registrar gasto.
5. **WalletBalanceTile**: saldo de wallet primaria con delta.

### 9.3 Catálogo de Complications

1. `BudgetRemainingComplication` (SHORT_TEXT): `$8,150 FALTA`.
2. `ExecutionPctComplication` (RANGED_VALUE): ring de progreso 0-100%.
3. `BalanceComplication` (LONG_TEXT): `Balance $12,340 ▲`.
4. `QuickCaptureComplication` (SMALL_IMAGE + tap): icono "+" → captura.
5. `NextInstallmentComplication` (SHORT_TEXT): `Omar 22abr`.

### 9.4 Sync Watch ←→ Phone

Canal: **Data Layer API** con nodos autodescubiertos.
Política de conflictos: **Last-Write-Wins con vector clock** por entidad.
La captura desde Watch es siempre **offline-first** con cola hasta pareo BT/Wi-Fi.

---

## 10. Sistema Analítico y Dashboards

### 10.1 KPIs de primer nivel (tiempo real)

| KPI | Fórmula |
|---|---|
| Balance quincenal | Σ income - Σ expense(POSTED) - Σ savings_locked |
| Presupuesto ejecutado | Σ expense.amount WHERE status='POSTED' |
| Falta gastar | Σ expense.amount WHERE status='PLANNED' |
| Tasa de ejecución | POSTED / (POSTED + PLANNED) |
| Gasto promedio quincenal | AVG(actual_expenses) over 6 quincenas cerradas |
| Ahorro efectivo acumulado | Σ SavingsGoal.current |
| Deuda revolvente total | Σ saldos de tarjetas de crédito |
| Compromisos de cuotas pendientes | Σ cuota * (total - current) |

### 10.2 Módulos analíticos de alto nivel (6 módulos)

| Módulo | Visualización | Datos |
|---|---|---|
| **A — Flujo de capital** | Diagrama Sankey | Ingresos → Categorías de gasto |
| **B — Gasto por miembro** | Stacked bar + Treemap | Costo per cápita del hogar |
| **C — Pagos por intereses** | Barras horizontales + timeline de amortización | Intereses por wallet, desglose capital/interés |
| **D — Varianza histórica** | Z-score table + line chart con banda σ + waterfall + heatmap | Quincena actual vs mediana 6Q |
| **E — Concentración deuda** | Donut + barras de utilización | Deuda por wallet vs límite |
| **F — Timeline del household** | Lista cronológica con KPIs por quincena | Histórico completo |

### 10.3 Alertas proactivas (5 reglas)

| Alerta | Trigger | Canal |
|---|---|---|
| Sobregasto categoría | spending >= 80% | Push |
| Cuota próxima | cuota en próximos 3 días + PLANNED | Push + Wear |
| Utilización crédito alta | >= 70% del límite | Push |
| Recurrencia detectada | confidence >= 0.75, no promovida | In-app |
| Sin gastos hoy | 22:00 + 0 registros del día | Wear |

### 10.4 Pantallas por plataforma

| Pantalla | Contenido | Target |
|---|---|---|
| **Home** | Balance, top 3 gastos recientes, accesos rápidos | Móvil + Web |
| **Quincena actual** | Presupuesto vs ejecutado por categoría | Fold (dual-pane) |
| **Timeline anual** | Heatmap de gasto por día | Web |
| **Por miembro** | Tarjeta por miembro con acumulados | Móvil |
| **Por categoría** | Drill-down jerárquico | Móvil + Web |
| **Cuentas** | Saldos, cortes, utilización | Móvil + Web |
| **Cuotas activas** | Barras de progreso N/Total | Móvil |
| **Pronóstico** | Proyección + tendencia | Web |
| **Wear glance** | Balance + "puedes gastar $X hoy" | Reloj |

---

## 11. Diseño Visual y UX

### 11.1 Material 3 Expressive

- **Animaciones spring-based**: `dampingRatio = 0.75`, `stiffness = 400`. Todo a >= 120 Hz en el Fold.
- **Shape expressivity**: FAB 28dp corners, cards 16dp, morphing entre estados.
- **Dynamic color v2**: paleta desde wallpaper, seed fallback `#006D3D` (verde-contable).
- **Typography**: `displayLargeEmphasized` (57sp, W700) para KPIs, RobotoFlex con variación de peso.

### 11.2 Colores semánticos extendidos

| Token | Color | Uso |
|---|---|---|
| `success` | `#1B6E3F` | Verde oscuro — operación exitosa |
| `warning` | `#8B5A00` | Ámbar — atención |
| `expense` | `#BA1A1A` | Rojo — gasto |
| `income` | `#0F5A2E` | Verde — ingreso |
| `overBudget` | `#B3261E` | Rojo peligro — sobre presupuesto |
| `underBudget` | `#2E7D32` | Verde — bajo presupuesto |

### 11.3 Layouts adaptativos del Fold

| Postura | Display | Layout |
|---|---|---|
| Plegado | Outer 6.3" | `COMPACT` — bottom sheet, accordion de categorías |
| Desplegado apaisado | Inner 8.0" | `EXPANDED` — dual-pane (lista 40% + detalle 60%) |
| Desplegado retrato | Inner 8.0" | `EXPANDED` — top chat 55% + contenido 45% |
| Tabletop (bisagra 90°) | Inner 8.0" | `MEDIUM` — gráfica arriba, controles abajo |

### 11.4 Gestos específicos del Fold

- Drag & drop entre paneles para recategorizar o cambiar beneficiario.
- Doble tap en barra de pane para colapsar/expandir.
- Tabletop posture: chat sobre bisagra, visual debajo.
- Haptic: `Confirm` al guardar, `Reject` al error de validación.

### 11.5 Densidades por contexto

- **Fold plegado**: `Density.Comfortable` — touch targets amplios.
- **Fold desplegado**: `Density.Compact` — aprovecha real estate.
- **Wear OS**: `Density.Spacious` — areas tappable >= 48x48 dp.

---

## 12. Catálogo de Requerimientos Funcionales

### Prioridades
- **P0**: Bloqueante para paridad con Excel.
- **P1**: Crítico para adopción.
- **P2**: Diferenciador.

### 12.1 Registro de gastos (RF-01 a RF-08)

| ID | Prioridad | Descripción |
|---|---|---|
| RF-01 | P0 | Registrar gasto en <= 3 taps con autocompletado desde plantillas |
| RF-02 | P0 | Capturar: concepto, monto, categoría, método, fecha, beneficiarios, pagadores |
| RF-03 | P0 | Gastos compartidos con basis points y validación suma=100% |
| RF-04 | P0 | Atribución N:M de pagadores |
| RF-05 | P0 | Estado `PLANNED` vs `POSTED` |
| RF-06 | P1 | Registro por voz desde Wear OS |
| RF-07 | P1 | Parser de SMS/notificación bancaria |
| RF-08 | P1 | Escáner OCR de tickets |

### 12.2 Plantillas y recurrencia (RF-10 a RF-14)

| ID | Prioridad | Descripción |
|---|---|---|
| RF-10 | P0 | Plantillas recurrentes con cadencia configurable |
| RF-11 | P0 | Auto-materialización de PLANNED al abrir quincena |
| RF-12 | P0 | Autocompletado de monto, categoría, método, beneficiarios |
| RF-13 | P1 | Pausar/editar/saltar instancia sin afectar plantilla |
| RF-14 | P1 | Promover gasto a plantilla tras detección de recurrencia |

### 12.3 Cuotas (RF-20 a RF-24)

| ID | Prioridad | Descripción |
|---|---|---|
| RF-20 | P0 | Plan de cuotas con generación automática de instancias |
| RF-21 | P0 | Autonumeración 3/10 en UI |
| RF-22 | P0 | Desglose capital + interés |
| RF-23 | P1 | Alerta a 3 días de vencimiento |
| RF-24 | P1 | Liquidación anticipada con recálculo de intereses |

### 12.4 Quincena operativa (RF-30 a RF-34)

| ID | Prioridad | Descripción |
|---|---|---|
| RF-30 | P0 | Abrir quincena clonando plantillas + presupuesto |
| RF-31 | P0 | Dashboard en tiempo real con KPIs principales |
| RF-32 | P0 | Cerrar quincena (inmutabilidad) |
| RF-33 | P0 | Comparar vs media histórica |
| RF-34 | P1 | Reporte exportable PDF/XLSX |

### 12.5 Cuentas y conciliación (RF-40 a RF-44)

| ID | Prioridad | Descripción |
|---|---|---|
| RF-40 | P0 | Saldo en vivo por método de pago |
| RF-41 | P0 | Transferencias entre cuentas (sin contar como gasto) |
| RF-42 | P0 | Conciliación con estado de cuenta |
| RF-43 | P1 | Alerta de sobrepago de tarjeta |
| RF-44 | P1 | Proyección de corte |

### 12.6 Préstamos (RF-50 a RF-52)

| ID | Prioridad | Descripción |
|---|---|---|
| RF-50 | P0 | CRUD préstamos otorgados con saldo remanente |
| RF-51 | P1 | Registrar pagos recibidos |
| RF-52 | P1 | Calcular intereses generados |

### 12.7 Miembros y atribución (RF-60 a RF-63)

| ID | Prioridad | Descripción |
|---|---|---|
| RF-60 | P0 | CRUD miembros con roles |
| RF-61 | P0 | Búsqueda por nombre/alias |
| RF-62 | P0 | Vista "Gasto por miembro" filtrable por fechas |
| RF-63 | P1 | Line chart histórico por beneficiario |

### 12.8 Categorías (RF-70 a RF-72)

| ID | Prioridad | Descripción |
|---|---|---|
| RF-70 | P0 | Jerarquía arbitraria padre-hijo |
| RF-71 | P0 | Recategorizar sin perder atribuciones |
| RF-72 | P1 | Sugerencia automática de categoría |

### 12.9 Sync multiplataforma (RF-80 a RF-82)

| ID | Prioridad | Descripción |
|---|---|---|
| RF-80 | P0 | Sync eventual (Android, Wear, Web). LWW con vector clock. |
| RF-81 | P0 | Offline-first en móvil y reloj |
| RF-82 | P1 | Multi-usuario por household con presence |

### 12.10 Importación/exportación (RF-90 a RF-92)

| ID | Prioridad | Descripción |
|---|---|---|
| RF-90 | P0 | Importador del XLSX con mapeo guiado |
| RF-91 | P1 | Exportador XLSX compatible |
| RF-92 | P1 | Exportador CSV |

---

## 13. Restricciones Técnicas y de Privacidad

### 13.1 Restricción de red para IA

```
+--------------------------------------------------------------+
|                      TELÉFONO DEL USUARIO                    |
|                                                              |
|  Room DB  <---->  RagBuilder  ---->  AICore (edge TPU)       |
|  (local)                            Gemini Nano              |
|                                                              |
|     🚫  ninguna flecha sale de este rectángulo               |
+--------------------------------------------------------------+
```

- AICore usa Private Compute Core de Android.
- Módulo `:ai-local` declara `nsc_deny_all` (bloquea todo tráfico saliente).
- Ninguna lib de telemetría de terceros en `:ai-local`.

### 13.2 Pantalla de transparencia

Settings → Privacidad → Asistente IA muestra:
- Estado de AICore (disponible/descargando/deshabilitado).
- Versión del modelo y fecha de último uso.
- Inferencias del día.
- Botón "Ver últimos 10 prompts" con audit log.
- Botón "Deshabilitar asistente".
- Botón "Borrar historial del asistente".

### 13.3 Anti prompt injection

Aunque no hay red, se mitiga inyección por texto de gastos:
1. Delimitadores robustos entre bloques del prompt.
2. Stripping de tokens peligrosos (`##`, `SYSTEM:`, etc.).
3. Truncamiento de conceptos a 64 caracteres en RAG.
4. Output forzado a schema JSON (no hay escape posible sin red).

### 13.4 Permisos del sistema

| Permiso | Uso | Superficie | Obligatoriedad |
|---|---|---|---|
| `SYSTEM_ALERT_WINDOW` | Quick Capture overlay | Android | Opcional |
| `POST_NOTIFICATIONS` | Recordatorios + Ongoing Activity | Android + Wear | Obligatorio |
| `FOREGROUND_SERVICE` | Overlay service | Android | Condicional |
| `USE_EXACT_ALARM` | Provisionar quincenas T-3 | Android | Obligatorio |
| `RECEIVE_BOOT_COMPLETED` | Restablecer alarmas | Android | Obligatorio |
| `ACCESS_COARSE_LOCATION` | Contexto de predicción | Android | Opcional |
| `READ_SMS` / `NOTIFICATION_LISTENER` | Parser bancario | Android | Opcional |

---

## 14. Mapa de Dependencias entre Documentos

### 14.1 Jerarquía de documentos

```
ESPECIFICACION_PRESUPUESTO_APP.md (Doc Base)
    |
    +-- Define: Entidades, Requerimientos, Atribución, Analíticas SQL, Migración XLSX
    |
    +---> ESPECIFICACION_UX_HARDWARE_APP.md (Complementario)
    |         |
    |         +-- Extiende con: Motor determinista, Quincena state machine,
    |         |                  Captura acelerada, Wear OS, Módulos analíticos,
    |         |                  Tokens de diseño M3E
    |         |
    |         +---> ADENDA_IA_ON_DEVICE.md (Apéndice E)
    |                   |
    |                   +-- Complementa con: RAG pipeline, Tool calling emulado,
    |                                        AliasResolver, Chat layouts del Fold,
    |                                        Golden test suite, Privacidad IA
```

### 14.2 Referencias cruzadas críticas

| Origen | Destino | Relación |
|---|---|---|
| Doc UX §5 (Motor Determinista) | Doc Base §5.2 (Analíticas SQL) | Reutiliza las mismas queries |
| Adenda §E.4.5 (AliasResolver) | Doc Base §2.2.2 (Member aliases) | Usa tabla de aliases |
| Adenda §E.4.4 (IntentDispatcher) | Doc UX §5 (Motor determinista) | Invoca funciones del motor |
| Adenda §E.6 (Fold layouts) | Doc UX §6 (Módulos analíticos) | Consume los mismos DispatchResult |
| Doc UX §2.2 (State machine) | Doc Base §3.4 (RF-30 a RF-33) | Implementa los requerimientos |
| Doc UX §3 (Captura acelerada) | Doc Base §3.1 (RF-01 a RF-08) | Implementa los requerimientos |

---

## 15. Riesgos y Áreas de Ambigüedad

### 15.1 Riesgos técnicos

| Riesgo | Probabilidad | Impacto | Mitigación documentada |
|---|---|---|---|
| Gemini Nano no disponible | Media | Bajo | Degradación grácil, app funcional sin IA |
| Cambio de versión Nano rompe intents | Media | Medio | Golden test suite, auto-desactivación < 92% |
| Cuota de batería AICore agotada | Alta | Bajo | Fallback a motor determinista hasta medianoche |
| Latencia sync Watch > 2.5s P95 | Media | Medio | Offline-first, cola de cambios |
| XLSX con variantes no previstas | Alta | Medio | Parser tolerante, warnings en ImportReport |

### 15.2 Áreas no resueltas / ambiguas

> [!WARNING]
> Los siguientes puntos **no están completamente especificados** y necesitarán decisiones de diseño durante el desarrollo:

1. **Backend de sincronización**: los docs mencionan gRPC/protobuf y WebSocket pero **no especifican el backend** (¿Firebase? ¿Supabase? ¿Custom?). La web necesita un servidor.

2. **Autenticación**: no hay especificación de login/auth. ¿Google Sign-In? ¿Local-only inicialmente?

3. **Migración de datos completa**: el importador XLSX está bien especificado algorítmicamente pero falta detalle sobre el handling de las columnas N-U (conciliación manual) y el bloque histórico (filas 90-127).

4. **Entidad IncomeSource**: aparece en el doc UX (§2.4) pero no en el modelo de datos del doc base (§2.2). ¿Es una tabla separada o un `Expense` con category `INCOME`?

5. **ExpenseSplit**: la tabla para dividir pagos entre wallets aparece en §2.5 del doc UX pero no en el schema DDL del doc base.

6. **Web platform**: la especificación está muy centrada en Android/Wear. La web (React/shadcn) tiene poca cobertura de pantallas y flujos específicos.

7. **Backup/restore**: no hay especificación de backup de la base de datos local.

8. **Versionado del schema de Room**: no hay plan de migraciones de base de datos.

9. **Testing strategy**: más allá de la golden suite de IA, no hay estrategia de tests unitarios/integración.

10. **CI/CD**: no hay especificación de pipeline de builds.

### 15.3 Decisiones de diseño implícitas

Estas decisiones están implícitas en los docs pero vale la pena hacerlas explícitas:

- **Moneda única**: solo MXN. No hay soporte multi-moneda.
- **Idioma**: la app es en español, los prompts de IA son en español, las reglas de atribución usan patrones en español.
- **Single household**: la UI actual asume un solo household por instalación.
- **Offline-first**: el móvil y reloj son la fuente de verdad; la web sincroniza contra el backend.

---

## 16. Hoja de Ruta Sugerida para Desarrollo

Basada en las prioridades documentadas (P0 bloqueante, P1 adopción, P2 diferenciador):

### Fase 1 — Paridad con Excel (P0)

> Objetivo: que el hogar pueda dejar de usar Excel completamente.

- [ ] Modelo de datos completo en Room (11 tablas + índices + triggers)
- [ ] CRUD de miembros, categorías, métodos de pago
- [ ] Quincena state machine (PROVISIONED → ACTIVE → CLOSING_REVIEW → CLOSED)
- [ ] Registro de gastos con atribución dual (beneficiario + pagador)
- [ ] Plantillas recurrentes + materialización automática
- [ ] InstallmentPlan con contador automático
- [ ] Dashboard quincenal (KPIs + categorías expandibles)
- [ ] Saldos por wallet en tiempo real (triggers SQL)
- [ ] Importador del XLSX existente
- [ ] Préstamos otorgados (CRUD + saldo)

### Fase 2 — Captura Acelerada + Wear (P0-P1)

> Objetivo: que registrar un gasto sea más rápido que abrir Excel.

- [ ] Quick Capture overlay (Quick Tap + fallbacks)
- [ ] Motor determinista de atribución (4 pasos)
- [ ] Persistence de memoria de atribución
- [ ] Reglas declarativas YAML
- [ ] Wear OS: app completa + 5 Tiles + 5 Complications + Ongoing Activity
- [ ] Sync Watch - Phone via Data Layer API
- [ ] Widget Android homescreen

### Fase 3 — Analíticas y Visualización (P0-P1)

> Objetivo: que la app aporte más insight que el Excel.

- [ ] Dashboard analítico con 8 KPIs en tiempo real
- [ ] Módulo D: Análisis de varianza (z-scores, outliers, pronóstico)
- [ ] Módulo B: Gasto por miembro (stacked bar + treemap)
- [ ] Módulo A: Flujo de capital (Sankey)
- [ ] Módulo C: Detección de intereses pagados
- [ ] Módulo E: Concentración de deuda
- [ ] Módulo F: Timeline del household
- [ ] Alertas proactivas (5 reglas)
- [ ] Exportación PDF/XLSX/CSV

### Fase 4 — IA On-Device (P2)

> Objetivo: consultas en lenguaje natural sobre el presupuesto.

- [ ] Módulo `:ai-local` con AiCoreService
- [ ] Pipeline RAG local (classifier, retriever, serializer, assembler)
- [ ] IntentDispatcher con 18 intents
- [ ] AliasResolver (fuzzy matching)
- [ ] Chat UI con layouts adaptativos (4 posturas del Fold)
- [ ] Dual-pane sincronizado (chat / dashboard)
- [ ] Golden test suite (24 casos)
- [ ] Pantalla de transparencia y privacidad
- [ ] Sistema de degradación grácil

### Fase 5 — Web + Multi-usuario (P1-P2)

> Objetivo: acceso desde cualquier dispositivo + captura concurrente.

- [ ] Backend de sincronización (a definir tecnología)
- [ ] Web frontend React + shadcn con tokens M3
- [ ] Multi-usuario por household con presence
- [ ] Autenticación (a definir)
- [ ] Conciliación con importación de estados de cuenta

---

> [!TIP]
> **Para empezar a codear**: el primer paso concreto es crear el módulo `:core-domain` con las entidades Kotlin, el schema SQL DDL completo (ya está en el doc base §2.4), y los DAOs de Room. Todo el código de referencia necesario está en los tres documentos fuente.

---

**Documentos fuente**:
- [ESPECIFICACION_PRESUPUESTO_APP.md](file:///g:/My%20Drive/Apps/Finance%20App/ESPECIFICACION_PRESUPUESTO_APP.md) — Modelo de datos, diagnóstico Excel, requerimientos
- [ESPECIFICACION_UX_HARDWARE_APP.md](file:///g:/My%20Drive/Apps/Finance%20App/ESPECIFICACION_UX_HARDWARE_APP.md) — UX, hardware, captura, Wear OS, analíticas, motor determinista
- [ADENDA_IA_ON_DEVICE.md](file:///g:/My%20Drive/Apps/Finance%20App/ADENDA_IA_ON_DEVICE.md) — Gemini Nano, RAG local, tool calling, chat layout
