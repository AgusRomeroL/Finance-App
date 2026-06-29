# Adenda Técnica: Capa de Inteligencia Proactiva y Normalización Retroactiva

**Sección complementaria a `ESPECIFICACION_PRESUPUESTO_APP.md`, `ESPECIFICACION_UX_HARDWARE_APP.md` y `ADENDA_IA_ON_DEVICE.md`.**
**Ámbito**: la app deja de ser reactiva (esperar a que el usuario registre) y pasa a ser **proactiva** — aprende del historial para sugerir atribución, anticipa qué se quiere registrar, normaliza la base histórica y captura gastos desde notificaciones del banco.
**Superficie primaria**: Pixel 9 Pro Fold + módulo Wear OS (Pixel Watch).
**Restricción dura heredada**: cero red para inferencia; **Room sigue siendo la única fuente de verdad** (ver `CLAUDE.md` §"Capa de datos — offline-first"). La IA solo propone; el motor determinista y el usuario confirman.
**Posición en la especificación**: se integra como **Apéndice F**, sin modificar el Apéndice E (asistente de consultas en lenguaje natural) ni secciones existentes.

> **Inspiración explícita — Pixel Journal.** Lo que se traslada de Journal es **la idea de proactividad** (sugerir contenido oportuno basado en contexto e historial, de forma ignorable y no invasiva), **no sus APIs** (que son exclusivas de Pixel y no están disponibles para apps de terceros). La investigación confirmó que Journal usa señales — fotos, Maps, Health Connect, mood — accesibles solo a apps de sistema. Nuestra proactividad se construye **únicamente con APIs públicas de Android** (WorkManager, hora/día del sistema, el propio historial en Room) y, opcionalmente, Gemini Nano on-device.

---

## Índice

- [F.1 Principios rectores de la proactividad](#f1-principios-rectores-de-la-proactividad)
- [F.2 Inventario de features y orden de construcción](#f2-inventario-de-features-y-orden-de-construcción)
- [F.3 FEATURE B — Normalización y atribución retroactiva](#f3-feature-b--normalización-y-atribución-retroactiva)  ⟵ **foco de este chat**
- [F.4 FEATURE A — Sugerencia de atribución al capturar (chip inline)](#f4-feature-a--sugerencia-de-atribución-al-capturar-chip-inline)
- [F.5 FEATURE C — Sugerencia proactiva al abrir la app](#f5-feature-c--sugerencia-proactiva-al-abrir-la-app)
- [F.6 FEATURE D — Captura desde notificaciones bancarias](#f6-feature-d--captura-desde-notificaciones-bancarias)
- [F.7 Superficie cross-cutting — Widget Glance + Pixel Watch](#f7-superficie-cross-cutting--widget-glance--pixel-watch)
- [F.8 Capa 3 — Gemini Nano (Prompt API alpha) para razonamiento proactivo](#f8-capa-3--gemini-nano-prompt-api-alpha-para-razonamiento-proactivo)
- [F.9 Hallazgos de investigación que fundamentan estas decisiones](#f9-hallazgos-de-investigación-que-fundamentan-estas-decisiones)

---

## F.1 Principios rectores de la proactividad

1. **El historial propio es el modelo.** Para ~800 gastos con conceptos repetitivos en español, una agregación SQL sobre Room (distribución histórica de atribución por concepto canónico) da el 80% del valor con 0% de complejidad de ML. ML real (TFLite, Gemini Nano) solo entra donde SQL no alcanza (conceptos nuevos, razonamiento contextual). **No introducir ML donde una query basta.**
2. **Proactividad ignorable, nunca invasiva.** Toda sugerencia vive **dentro de la app** como un chip/banner descartable (estilo autocompletado del teclado), no como notificación push intrusiva. La excepción es Feature D (notificaciones bancarias), donde la notificación silenciosa es el patrón correcto porque el evento (cargo bancario) ocurrió fuera de la app.
3. **Propose-then-confirm.** La IA nunca escribe estado del hogar de forma silenciosa con baja confianza. Propone un borrador; el usuario confirma con un tap. La excepción es la auto-aplicación retroactiva de alta confianza (§F.3.6), que siempre queda auditada y es reversible.
4. **La no-selección es señal negativa implícita.** Cuando el usuario ignora una sugerencia y captura algo distinto, eso es feedback. Se registra sin fricción (sin "thumbs down") y degrada la confianza futura de esa sugerencia. (Fundamento verificado en investigación — ver §F.9.)
5. **Redundancia no-cromática y explicabilidad.** Toda sugerencia muestra **por qué** se propuso ("Basado en tus últimos 18 gastos de 'Despensa'") y su nivel de confianza, coherente con `AmountSemantics.kt` y la pantalla de transparencia del Apéndice E.5.3.
6. **Degradación grácil.** Sin AICore, sin permiso de notificaciones, sin historial suficiente (cold start) o con la feature desactivada, la app sigue 100% funcional con captura manual. Cada feature declara su comportamiento de fallback.
7. **Room es la verdad; el sync no se rompe.** Todo lo que escriba la capa proactiva pasa por los repos públicos (que encolan en `sync_queue`) **salvo** los procesos batch internos, que deben respetar la regla anti-eco del Apéndice de datos. Las tablas nuevas de esta adenda que no deban sincronizarse (cola de revisión, sugerencias efímeras, telemetría) se marcan como **locales-only** y no se suben a Firestore.

---

## F.2 Inventario de features y orden de construcción

| Cód. | Feature | Capa técnica | Depende de | Estado |
|---|---|---|---|---|
| **B** | Normalización + atribución retroactiva de los ~800 gastos | SQL + WorkManager + pantalla de revisión | — | **Este chat (spec build-ready)** |
| **A** | Sugerencia de atribución al teclear concepto (chip inline en captura) | SQL (motor de B) | B | Otro chat |
| **C** | Sugerencia proactiva al abrir la app (chip en dashboard + señal de quincena) | SQL + WorkManager | B | Otro chat |
| **D** | Captura automática desde notificaciones bancarias | `NotificationListenerService` + motor de B | B (atribución) | Otro chat |
| — | Widget Glance + espejo en Pixel Watch (captura rápida cross-device) | Glance + Wear Data Layer | A | Otro chat |
| **Capa 3** | Razonamiento proactivo con Gemini Nano (Prompt API alpha) | ML Kit GenAI | C | Otro chat (alpha) |

**Orden confirmado: B → A → C → D.** Razón: A, C y D consumen el **motor de canonicalización + distribución histórica de atribución** que construye B. Sin una base histórica normalizada y confiable, las sugerencias de A/C/D heredan ruido. B es el cimiento.

> **Para el lector de otro chat:** §F.3 (B) está escrita para implementarse directamente. §F.4 (A), §F.5 (C), §F.6 (D), §F.7 y §F.8 están especificadas a nivel de contrato y arquitectura; cada una asume que el `RetroAttributionEngine` de §F.3.5 ya existe y es reutilizable.

---

## F.3 FEATURE B — Normalización y atribución retroactiva

**Objetivo:** procesar los ~800 gastos históricos (sembrados desde el Excel) para (1) **agrupar conceptos equivalentes escritos distinto** ("Colegiatura Santi" ≡ "Santiago Colegiatura"), (2) **inferir la atribución BENEFICIARY/PAYER faltante o inconsistente** desde el patrón histórico, y (3) dejar las inferencias de **baja confianza en una cola de revisión** para que el usuario las confirme. Todo en background, sin bloquear la UI, sin romper el sync ni el esquema congelado del asset.

### F.3.1 Por qué es el cimiento

La atribución dual (BENEFICIARY = quién consume, PAYER = quién paga) en basis points que suman 10,000 es el modelo central de la app (`ExpenseAttributionEntity`). El ETL sembró atribuciones, pero con la "mugre" heredada del Excel (typos, beneficiarios embebidos en el concepto, categorías mal etiquetadas — ver `scripts/etl/attribution_rules.json`). Si A/C/D aprenden de esa base sin normalizar, propagan el ruido. B la limpia una vez.

### F.3.2 Deduplicación difusa de conceptos (canonicalización)

El corazón de B. Convierte el texto libre `expense.concept` en una **clave canónica** estable, de modo que variantes de escritura colapsen al mismo grupo.

**Algoritmo `ConceptCanonicalizer` (determinista, sin ML):**

```
entrada:  concept (texto libre, ej. "Colegiatura Santi")
salida:   canonicalKey (ej. "colegiatura|m:santi")

1. NORMALIZAR
   lower → unaccent() (ya existe en core/StringExtensions.kt)
   → quitar puntuación → colapsar espacios → trim

2. TOKENIZAR + EXPANDIR ALIASES DE MIEMBRO
   Partir en tokens. Para cada token, intentar resolverlo contra
   member.short_aliases (JSON) y member.display_name vía Jaro-Winkler ≥ 0.92.
   Si matchea, sustituir el token por el marcador canónico "m:<memberId-corto>".
   Ej.: "santi" → "m:santi";  "santiago" → "m:santi"  (si el alias de Santi
   incluye "Santiago"; si no, lo añade verify_db / se captura en review).

3. ORDENAR TOKENS (word-order-invariant)
   {colegiatura, m:santi}  ← tanto de "Colegiatura Santi"
                               como de "Santiago Colegiatura"

4. STEMMING LIGERO ES (opcional, conservador)
   Reglas de sufijo español muy acotadas (plurales, género) sobre tokens
   NO-miembro. Sin librería; tabla pequeña. Evitar sobre-stemming.

5. CLAVE = tokens ordenados unidos por "|"
```

**Clustering residual (segunda pasada).** Tras canonicalizar todos los gastos, conceptos con claves parecidas pero no idénticas (typos no cubiertos por aliases) se agrupan por **similitud de conjunto de tokens (Jaccard ≥ 0.6) + Jaro-Winkler sobre el residual ≥ 0.9**. Cada cluster recibe una `canonicalKey` representativa (la más frecuente) y una etiqueta legible (`displayLabel`, el concepto crudo más frecuente del cluster, ej. "Colegiatura Santiago").

> **Decisión:** la canonicalización es **determinista y auditable**, no ML. Con ~800 registros y nombres propios conocidos del hogar (Benjamín, Norma, Pau, David, Agustín, Santi, Omar…), las reglas + aliases + Jaro-Winkler superan a cualquier modelo entrenable con tan pocos datos (ver contraevidencia §F.9).

### F.3.3 Cambios de esquema (ZONA DE PELIGRO — leer `CLAUDE.md` §Room)

B requiere persistir la clave canónica y la cola de revisión. El asset `budget_database.db` está **congelado en schema v1**; el código está en **v2** con `MIGRATION_1_2`. B sube a **v3** siguiendo el protocolo exacto: subir `version`, escribir `MIGRATION_2_3`, y que su SQL coincida **literal** con el `createSql` que KSP genere en `app/schemas/3.json` (compilar primero, copiar de ahí). **Prohibido `fallbackToDestructiveMigration()`** (borraría los 793 gastos).

Cambios mínimos (todos aditivos, bajo riesgo):

```sql
-- 1) Clave canónica del concepto (nullable → ADD COLUMN trivial).
ALTER TABLE expense ADD COLUMN concept_canonical TEXT;
-- índice para agrupar rápido por clave:
CREATE INDEX IF NOT EXISTS index_expense_concept_canonical
  ON expense(concept_canonical);

-- 2) Cola de revisión de atribuciones inferidas (tabla nueva, LOCAL-ONLY).
CREATE TABLE IF NOT EXISTS attribution_review (
  id                TEXT NOT NULL PRIMARY KEY,
  expense_id        TEXT NOT NULL,
  role              TEXT NOT NULL,           -- 'BENEFICIARY' | 'PAYER'
  suggested_json    TEXT NOT NULL,           -- [{memberId, shareBps}] propuesto
  confidence        REAL NOT NULL,           -- 0.0–1.0
  sample_size       INTEGER NOT NULL,        -- nº de gastos histórricos usados
  status            TEXT NOT NULL,           -- 'PENDING'|'CONFIRMED'|'REJECTED'|'EDITED'
  created_at        INTEGER NOT NULL,
  FOREIGN KEY(expense_id) REFERENCES expense(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS index_attribution_review_status
  ON attribution_review(status);
```

> **Nota de exactitud del SQL:** el bloque anterior es el *intent*. El SQL real que va en `MIGRATION_2_3` debe copiarse **carácter por carácter** del `createSql` de `app/schemas/3.json` tras compilar con las entidades nuevas declaradas. Cualquier diferencia (orden de columnas, comillas backtick, `NOT NULL`) rompe el identityHash en arranque.

**Provenance de atribuciones (opcional, recomendado).** Para no sobrescribir nunca lo que el usuario tecleó a mano, considerar `ALTER TABLE expense_attribution ADD COLUMN source TEXT NOT NULL DEFAULT 'SEED'` con valores `SEED|USER|INFERRED`. Si se omite por minimizar riesgo, B aplica una regla más conservadora: **solo infiere para gastos cuyo `role` no tiene atribución alguna, o cuya suma de bps ≠ 10,000** (inconsistencia detectable sin columna nueva).

Nuevas entidades/DAOs Room (no cambian el esquema más allá del SQL anterior):
- `AttributionReviewEntity` + `AttributionReviewDao` (CRUD sobre `attribution_review`).
- Métodos nuevos en `ExpenseDao`: `getAllForCanonicalization()`, `updateConceptCanonical(id, key)`, `observePostedByCanonical(key)`.
- Método nuevo en `ExpenseAttributionDao`: `findHistoricalByCanonical(canonicalKey, role)` (JOIN expense↔attribution agrupando por la clave canónica).

### F.3.4 Pipeline de ejecución (WorkManager)

Dos workers encadenados, idempotentes, en `Dispatchers.IO`, observables por la UI vía `WorkInfo`:

```
┌─────────────────────────────┐     ┌──────────────────────────────────┐
│ CanonicalizeConceptsWorker  │ ──▶ │ RetroAttributionWorker            │
│ (OneTimeWorkRequest)        │     │ (OneTimeWorkRequest, depende del  │
│                             │     │  anterior vía WorkContinuation)   │
│ • lee todos los expense     │     │ • por cada gasto sin atribución   │
│ • ConceptCanonicalizer      │     │   válida en un role:              │
│ • clustering residual       │     │   - RetroAttributionEngine infiere│
│ • UPDATE concept_canonical  │     │   - confianza ≥ τ → auto-aplica   │
│   en lotes (withTransaction)│     │   - confianza < τ → attribution_  │
│                             │     │     review (PENDING)              │
└─────────────────────────────┘     └──────────────────────────────────┘
```

- **Disparo:** una sola vez, tras el primer arranque post-actualización (flag en DataStore `retro_labeling_done`). Re-ejecutable manualmente desde Perfil → "Re-normalizar historial".
- **Batching:** procesar en lotes de ~100 dentro de `db.withTransaction {}` para no mantener una transacción gigante. Progreso reportado vía `setProgress`.
- **Constraints:** `setRequiresBatteryNotLow(true)`. No requiere red (es 100% local).
- **Anti-eco / sync:** la auto-aplicación de atribuciones **sí** debe propagarse a Firestore (es un cambio real de datos del usuario), así que pasa por `ExpenseRepository`/`ExpenseAttributionDao` con encolado normal en `sync_queue`. La escritura en `attribution_review` es **local-only** (no se sincroniza). Cuidado: si el batch escribe miles de filas, encolar miles en `sync_queue` — considerar un encolado agregado o un flag de "bulk migration" que el `SyncManager` drene en background sin saturar.

### F.3.5 `RetroAttributionEngine` — el motor reutilizable (lo consumen A/C/D)

API central. Dado un concepto canónico y un rol, devuelve la distribución histórica más probable con su confianza.

```kotlin
data class AttributionSuggestion(
    val role: String,                      // "BENEFICIARY" | "PAYER"
    val distribution: Map<String, Int>,    // memberId → shareBps (suma 10_000)
    val confidence: Double,                // 0.0–1.0
    val sampleSize: Int,                   // nº de gastos que respaldan
    val basis: String                      // texto explicable para la UI
)

class RetroAttributionEngine(
    private val attributionDao: ExpenseAttributionDao,
    private val canonicalizer: ConceptCanonicalizer
) {
    suspend fun suggest(concept: String, role: String): AttributionSuggestion? {
        val key = canonicalizer.canonicalize(concept) ?: return null
        val historical = attributionDao.findHistoricalByCanonical(key, role)
        if (historical.isEmpty()) return null            // cold start → null

        // Agrupar por "huella" de distribución (set memberId→bucket de bps),
        // contar la moda, calcular acuerdo.
        val fingerprints = historical.groupBy { it.fingerprint() }
        val modal = fingerprints.maxBy { it.value.size }
        val agreement = modal.value.size.toDouble() / historical.size
        val n = historical.size

        // Confianza: acuerdo penalizado por tamaño de muestra pequeño.
        val confidence = agreement * minOf(1.0, n / SAMPLE_FLOOR)

        return AttributionSuggestion(
            role = role,
            distribution = modal.value.averagedDistributionBps(),
            confidence = confidence,
            sampleSize = n,
            basis = "Basado en $n gastos de \"${key.displayLabel()}\""
        )
    }
    companion object { const val SAMPLE_FLOOR = 5.0 }
}
```

### F.3.6 Score de confianza y umbrales

| Caso | Confianza | Acción del `RetroAttributionWorker` |
|---|---|---|
| ≥ 5 muestras y ≥ 80% de acuerdo | **alta (≥ 0.7)** | Auto-aplica a `expense_attribution` (propaga a sync). |
| 3–4 muestras, o acuerdo 50–80% | **media (0.4–0.7)** | A `attribution_review` (PENDING). |
| < 3 muestras o sin historial | **nula/baja** | A `attribution_review` (PENDING) o se omite (cold start). |
| Atribución existente y consistente | — | No tocar (respeta lo del usuario/seed). |

El umbral τ = 0.7 es configurable. La auto-aplicación **siempre** registra provenance/auditoría para ser reversible desde la pantalla de revisión.

### F.3.7 Pantalla "Revisión de atribuciones"

Nueva pantalla (ruta `attribution_review`, hoy `ledger`/`analytics` son placeholders — ver `CLAUDE.md` §Navegación). Entrada desde Perfil y desde un badge en el dashboard ("12 gastos por revisar").

Patrón de UX — **agrupar por concepto canónico**, no gasto por gasto (reduce fricción):

```
┌ Revisión de atribuciones ───────────────────────────────┐
│  Agrupados por concepto similar                          │
│                                                          │
│  ▸ "Colegiatura Santiago"            12 gastos · media   │
│     Beneficiario sugerido:  Santi 100%                   │
│     Pagador sugerido:       Norma 100%                   │
│     Basado en 12 gastos · 83% de acuerdo                 │
│     [ Aplicar a los 12 ]   [ Editar ]   [ Ignorar ]     │
│                                                          │
│  ▸ "Despensa"                        34 gastos · alta ✓  │
│     (auto-aplicado — toca para revertir)                 │
│                                                          │
│  ▸ "WALMART"                          8 gastos · baja    │
│     Sin patrón claro — asignar manualmente              │
│     [ Asignar ]                                          │
└──────────────────────────────────────────────────────────┘
```

- **Acción en lote**: confirmar/editar/ignorar un grupo entero aplica a todos sus gastos PENDING.
- Reutiliza el editor de % por miembro del `CaptureBottomSheet` (mismos `beneficiaryShares`/`payerShares`, stepper ±5, "Todos").
- "Ignorar" marca `REJECTED` → señal negativa que degrada esa sugerencia para A/C/D.

### F.3.8 Checklist de implementación de B

1. Declarar `AttributionReviewEntity`, subir `@Database` a v3, escribir `MIGRATION_2_3` (SQL copiado de `schemas/3.json` tras compilar).
2. `ConceptCanonicalizer` (+ tests informales con pares reales del Excel).
3. DAOs nuevos (`AttributionReviewDao`, métodos en `ExpenseDao`/`ExpenseAttributionDao`).
4. `RetroAttributionEngine` (reutilizable — exponerlo desde `BudgetApplication` para A/C/D).
5. `CanonicalizeConceptsWorker` + `RetroAttributionWorker` con `WorkContinuation`.
6. Disparo idempotente (flag DataStore) + acción manual en Perfil.
7. Pantalla "Revisión de atribuciones" + ruta en `BudgetNavGraph` + badge en dashboard.
8. Verificar `:app:compileDebugKotlin`, instalar en `FinanceFold`, correr el worker, revisar la cola con `run-as … sqlite3` (ver `CLAUDE.md` §Comandos).

---

## F.4 FEATURE A — Sugerencia de atribución al capturar (chip inline)

**Objetivo:** mientras el usuario teclea el concepto en el `CaptureBottomSheet`, sugerir la atribución BENEFICIARY/PAYER aprendida del historial, como un **chip inline visible pero ignorable**.

**Arquitectura (consume el motor de B):**
- En `CaptureViewModel`, observar `_concept` con `debounce(250ms)` → llamar `RetroAttributionEngine.suggest(concept, "BENEFICIARY")` y `(…, "PAYER")`.
- Exponer `attributionSuggestion: StateFlow<AttributionSuggestion?>`.
- La UI muestra un chip bajo el campo de concepto: *"Beneficia a Santi 100% · Pagó Norma — basado en 12 gastos"* con `[Aplicar]`.
- **Aplicar** rellena `beneficiaryShares`/`payerShares` (que ya existen, §`CaptureViewModel`).
- **Ignorar** (capturar algo distinto) = no-selección → señal negativa implícita (registrar para degradar confianza). Sin "thumbs down".

**Reglas:**
- Solo se muestra si `confidence ≥ 0.5` (no molestar con ruido).
- Nunca auto-aplica; siempre requiere el tap (propose-then-confirm en el flujo en vivo).
- Fallback: sin sugerencia, el flujo manual actual queda intacto.

**Estado:** especificado a contrato. Construir en otro chat tras B.

---

## F.5 FEATURE C — Sugerencia proactiva al abrir la app

**Objetivo:** la experiencia tipo Journal — al abrir la app, un chip en el dashboard sugiere **qué gasto se va a querer registrar ahora**, según hora, día de semana y **día de quincena** (la señal diferenciadora de esta app).

### F.5.1 Señales contextuales (solo APIs públicas)
| Señal | Fuente | Permiso |
|---|---|---|
| Hora del día (franja) | `System.currentTimeMillis` / `LocalTime` | ninguno |
| Día de la semana | `LocalDate.dayOfWeek` | ninguno |
| **Día de quincena** (1/16 = inicio) | quincena activa en Room | ninguno |
| Patrón histórico por franja+día | query SQL sobre `expense` | ninguno |

> No se usa ubicación, `UsageStatsManager` general ni `SCHEDULE_EXACT_ALARM` (la investigación confirmó que están restringidos/penalizados por Play Store en Android 14+ — ver §F.9). WorkManager (`PeriodicWorkRequest`, piso 15 min, exento de las restricciones de background de Android 14) es el mecanismo de pre-cómputo.

### F.5.2 Lógica
- Query: gastos POSTED con mismo `dayOfWeek` y franja horaria ±2h, ordenados por recencia/frecuencia → top candidatos por concepto canónico.
- **Señal de quincena (tu sugerencia 5):** en días 1 y 16, priorizar los gastos grandes recurrentes del periodo anterior (renta, servicios, colegiaturas) detectados por frecuencia quincenal → checklist "¿Ya registraste estos gastos del periodo?".
- Pre-cómputo opcional con un `ProactiveSuggestionWorker` que escribe a una tabla `proactive_suggestion` (local-only); el dashboard la lee al abrir y recalcula si está stale.

### F.5.3 Superficie (no invasiva)
Chip/banner en el tope del dashboard, descartable, redundante con color+texto:
```
┌ Sugerencia ──────────────────────────────────────┐
│ 🛒 ¿Registrar "Despensa"?                          │
│ Sueles hacerlo los sábados por la mañana          │
│ [ Registrar ]                          [ Ahora no ]│
└────────────────────────────────────────────────────┘
```
"Ahora no" = señal negativa implícita. **Nunca** notificación push para esto (vive en la app).

**Estado:** especificado a contrato. Construir en otro chat tras B. La versión "razonada" (lenguaje natural + ranking inteligente) es la Capa 3 (§F.8).

---

## F.6 FEATURE D — Captura desde notificaciones bancarias

**Objetivo:** interceptar notificaciones de apps de banco (BBVA, Citibanamex, etc.), extraer monto/comercio/fecha y proponer un gasto pre-llenado y pre-atribuido (vía motor de B) para confirmar con un tap.

### F.6.1 Mecanismo y advertencias de plataforma
- `NotificationListenerService` + permiso especial `BIND_NOTIFICATION_LISTENER_SERVICE`. **Alta fricción:** el usuario debe concederlo manualmente en Ajustes → Acceso a notificaciones (no es un runtime permission normal).
- **Política Play Store (riesgo real):** el acceso a notificaciones está fuertemente escrutado; debe ser **core functionality**, declarado con justificación y, probablemente, declaración de uso de datos. Documentar y pedir consentimiento explícito en onboarding. (Ver contraevidencia §F.9 sobre rechazos por políticas de permisos.)
- **Privacidad:** allowlist de **package names de bancos**; ignorar y nunca persistir cualquier otra notificación. Parsing 100% on-device.

### F.6.2 Pipeline
```
StatusBarNotification (de un package en la allowlist)
   → BankNotificationParser (regex/plantillas por banco)
      extrae: amount, merchant, date, last4 (wallet hint)
   → crea Expense DRAFT/PLANNED + match de wallet por last4
   → RetroAttributionEngine sugiere BENEFICIARY/PAYER por el merchant canónico
   → notificación silenciosa propia + chip en dashboard:
      "BBVA: $480 en OXXO. ¿Registrar?"  [Confirmar] [Descartar]
   → Confirmar → POSTED (propose-then-confirm)
```
- Las plantillas por banco viven en un asset versionable (`assets/bank_templates.json`), análogo a `attribution_rules.json`.
- Fallback total: feature opt-in; sin permiso, la app no la ofrece.

**Estado:** especificado a contrato. Construir en otro chat tras B. Es la pieza que más se acerca a "captura sin esfuerzo": el cargo se auto-detecta y el motor de B lo auto-atribuye; el usuario solo confirma.

---

## F.7 Superficie cross-cutting — Widget Glance + Pixel Watch

**Objetivo:** captura rápida desde fuera de la app, con el **Pixel Watch reflejando los mismos widgets** que el teléfono.

- **Widget (Jetpack Glance):** monto + confirmar; al teclear monto, el motor de A/B sugiere concepto y atribución. Un tap registra.
- **Pixel Watch (Wear):** el reloj muestra el mismo quick-capture y **delega al teléfono** vía Wear Data Layer (ya existen `WearSyncManager.kt`, `WearPaths.kt`, `BudgetWearListenerService.kt`). Modelo: el reloj envía la intención → el teléfono ejecuta la inserción real (con el motor de atribución) y devuelve confirmación. Esto evita duplicar la lógica de Room/atribución en el reloj.
- **Pendiente de plataforma:** el módulo `wear/` hoy **no compila** (sin `AndroidManifest`, fuera de `settings.gradle.kts` — ver `CLAUDE.md` §Estado). Habilitar el módulo Wear es prerequisito de la parte del reloj.

**Estado:** especificado a contrato. Construir tras A.

---

## F.8 Capa 3 — Gemini Nano (Prompt API alpha) para razonamiento proactivo

**Objetivo:** la versión "inteligente" de C — en vez de un ranking SQL, Gemini Nano razona sobre {hora, día, día de quincena, últimos N gastos} y devuelve una sugerencia priorizada con explicación en lenguaje natural.

### F.8.1 Estado de la API (hallazgos verificados — §F.9)
- **AICore corre 100% on-device**, sin red para inferencia. ✓
- Las **GenAI APIs no listan "clasificación estructurada"** como tarea nombrada; hacen generación (prompt, summarize, rewrite, etc.). Para obtener un label/JSON hay que usarlas en modo generación con structured output (igual que el Apéndice E.4).
- **ML Kit GenAI Prompt API** (alpha, oct-2025) permite prompts arbitrarios a Gemini Nano on-device → es la vía para esto.
- **Disponibilidad fragmentada:** solo chips Tensor/Dimensity/Snapdragon vía AICore; el Prompt API rinde mejor en Pixel 10; en Pixel 9 (nuestro target) es acceso experimental. **No asumir disponibilidad universal.**

### F.8.2 Diseño
- Reutiliza toda la infraestructura del Apéndice E: `AiCoreManager`/`AiCoreService`, structured output por JSON schema, `GenerationConfig` cuasi-determinista (T=0.05).
- Nuevo intent de "sugerencia proactiva": entrada = contexto serializado compacto (franja, día, día-quincena, top conceptos por contexto); salida = `{"suggestion": "...", "canonicalKey": "...", "reason": "..."}`.
- **Foreground-only** (AICore bloquea background — `BACKGROUND_USE_BLOCKED`): la inferencia ocurre al abrir el dashboard, no en un worker. El pre-cómputo de candidatos (SQL) sí puede ir en worker; solo el "razonamiento" final es foreground.
- **Fallback obligatorio:** sin AICore o con cuota agotada, C cae a la versión SQL de §F.5. La Capa 3 es un *enhancement*, nunca un requisito.

### F.8.3 Riesgo
Por ser **alpha** + disponibilidad fragmentada + foreground-only, la Capa 3 es la **última** en construirse y siempre detrás de la versión SQL de C. No bloquear el roadmap en ella.

### F.8.4 Implementación REAL y hallazgos en hardware (2026-06-28)

Construida y **validada en un Pixel 9 Pro físico (Tensor G4)**. El camino no fue el planeado; se documenta lo aprendido porque ahorra días a quien continúe.

**Arquitectura final = híbrido de 3 niveles** (`mx.budget.ai.service`):
- Interfaz común **`OnDeviceLlm`** (`ensureReady(): LlmReadiness`, `generate(prompt): Result<String>`, `close()`) + sellada `LlmReadiness { Available | Unavailable | Pending }`.
- **`AiCoreManager`** — Gemini Nano vía **ML Kit GenAI Prompt API** (`com.google.mlkit:genai-prompt`). TPU, gratis, sin almacenamiento. Solo donde Google provisiona el feature.
- **`LiteRtLmManager`** — modelo Gemma `.litertlm` local vía **LiteRT-LM** (`com.google.ai.edge.litertlm:litertlm-android`). CPU/GPU, independiente de AICore.
- **`HybridLlm`** — coordina: **AICore → LiteRT-LM → SQL**. `ProactiveReasoner` toma `OnDeviceLlm`.

**Cronología de hallazgos (todos verificados en hardware):**
1. **Bug del Context (commit `00f66e1`):** el viejo SDK `com.google.ai.edge.aicore` exige el `Context` DENTRO de `generationConfig { context = ... }`. Sin él, en emulador se enmascaraba como `UnsupportedDevice`; en Tensor real lanzaba `Context is required`. **La Capa 3 LLM nunca había corrido.**
2. **AICore experimental = callejón sin salida en Pixel 9:** tras el fix, el SDK `aicore` daba `PERMISSION_DENIED` (canal de terceros sin allowlist por-app). Se **migró a ML Kit GenAI Prompt API** (vía GA, sin allowlist) — commit `9a01d44`.
3. **El Prompt API no está en Tensor G4:** ML Kit `checkStatus()` lanza `GenAiException 606-FEATURE_NOT_FOUND ("Feature 636 not available")` en Pixel 9 — el Gemini Nano nuevo del Prompt API llegó primero a **Pixel 10**; en Pixel 9/Fold Google **aún no lo habilita** (device-side, fuera de nuestro control). La AI Edge Gallery de Google también muestra "Gemini Nano via AICore" solo en Pixel 10, no en Pixel 9.
4. **LiteRT-LM SÍ corre en Tensor G4 (commits `f0e7c15`, `9b2c18f`):** corre un modelo Gemma local, independiente de AICore. **Validado en Pixel 9:** `gemma-4-E4B-it.litertlm` (3.7GB) carga el engine en ~20s e infiere en ~5s (respuesta corta), devolviendo texto real. **PRIMERA vez que la Capa 3 razona en el target.** Correrá igual en el Fold de Norma (mismo chip).

**Gotchas de LiteRT-LM (críticos):**
- **Backend `CPU`**, no `GPU`: `Backend.GPU()` da `INTERNAL` al compilar el modelo en Pixel 9; `Backend.CPU()` funciona (más lento).
- **El archivo del modelo debe ser propiedad de la app** (`getExternalFilesDir`/`filesDir`): el `open()` nativo de LiteRT-LM da `PERMISSION_DENIED` si lo colocó otro uid (p. ej. `shell` por adb) — se arregló en pruebas con `chmod 666`; **en producción la app lo descarga a su propia carpeta** (sin el problema).
- `NativeLibraryLoader` es `internal` (no se llama; la lib se auto-carga).
- API exacta de litertlm sacada por **`javap` del AAR**, no de la doc (incompleta).

**Gotchas de toolchain (en `gradle.properties`/`build.gradle.kts`):**
- ML Kit + LiteRT-LM traen **metadata Kotlin 2.2** (proyecto en 2.0.21) → `freeCompilerArgs += "-Xskip-metadata-version-check"` en `kotlinOptions`.
- Ese flag **corrompe la compilación incremental** ("Couldn't load KotlinClass" flaky) → `kotlin.incremental=false`.
- LiteRT-LM trae **bytecode Java 21** que Jetifier no procesa → `android.jetifier.ignorelist=litertlm-android`.
- LiteRT-LM añade ~50MB de `.so` (APK debug ~135MB) y **dispara el warning de 16 KB** (sus libs no están alineadas; debug-only, no afecta release — ver pendientes).

**Estado:** **funcional y validado en hardware.** El `ProactiveReasoner` (re-ranker sobre candidatos SQL, §F.8.2) ya enruta al híbrido.

**PENDIENTE para producción (no hecho):**
- **Descarga in-app del modelo** Gemma `.litertlm` (HuggingFace `litert-community`, *gated*: licencia Gemma + token, 3.7GB) con progreso, a la carpeta de la app. Hoy el modelo de prueba se copió de la AI Edge Gallery del usuario por adb. **Es la pieza más grande que falta.**
- **Tuning de latencia:** CPU es lento para un 4B; el prompt real de re-ranking tardará más que los ~5s de prueba. Evaluar arreglar GPU/NPU, o un modelo más chico (Gemma 3 1B) para el re-ranking.
- Golden suite del Apéndice E.8 extendida para el intent proactivo.
- Warning de 16 KB (ver CLAUDE.md / pendientes pre-lanzamiento).

---

## F.9 Hallazgos de investigación que fundamentan estas decisiones

Tres investigaciones profundas paralelas (UX proactiva en finanzas; ML on-device en Android; Jornal/Pixel + APIs de contexto). Resumen de lo **verificado** y la **contraevidencia** relevante. La verificación adversarial fue agresiva (rate-limiting masivo degradó muchas confirmaciones a "no verificado"); donde un claim quedó sin verificar pero es plausible, se marca como hipótesis, no como hecho.

**Verificado (alta confianza):**
- AICore/Gemini Nano corre on-device sin red para inferencia.
- Las GenAI APIs no exponen clasificación estructurada como tarea nombrada (usar generación + structured output).
- GenAI solo en chipsets Tensor/Dimensity/Snapdragon; no universal.
- ML Kit Prompt API liberado en **alpha oct-2025**; mejor en Pixel 10.
- La **no-selección de una sugerencia es señal negativa implícita** válida y operacionalizable sin acción explícita del usuario (arXiv 2410.11009). → fundamenta el principio F.1.4.
- WorkManager está **exento** de las restricciones de background de Android 14 (vía marco soportado). → fundamenta C/B en WorkManager.
- `SCHEDULE_EXACT_ALARM` denegado por default en Android 14+ y restringido por Play Store. → por eso C no usa alarmas exactas.
- Background location prohibido por Play Store salvo core functionality. → por eso C no usa ubicación.
- Pixel Journal y sus señales (fotos/Maps/Health Connect/mood) son de app de sistema, no accesibles a terceros. → trasladamos la *idea*, no las APIs.

**Contraevidencia / disenso (a tener en cuenta):**
- **ML en datasets pequeños:** con ~800 registros, heurísticas simples suelen superar a ML entrenable. → por eso B es SQL/determinista, no un modelo.
- **Cold start:** ML de personalización falla sin datos locales suficientes. → por eso A/C devuelven null sin historial y caen a flujo manual.
- **Smart defaults mal calibrados** pueden causar errores de atribución difíciles de corregir. → por eso propose-then-confirm + cola de revisión + confianza visible.
- **Proactividad de IA puede percibirse como amenaza a la autonomía** (hipótesis teórica no verificada al nivel de fuente, pero plausible). → por eso todo es ignorable y nunca push (salvo D, donde el evento es externo).
- **Acceso a notificaciones** es un campo minado de políticas Play Store. → D exige justificación, consentimiento y allowlist estricta.

**Vacíos abiertos (validar con uso real):**
- Umbral mínimo de muestras para que la señal de no-selección sea fiable.
- Si la atribución compartida (multi-pagador) es UX más difícil que en apps de un solo usuario.
- Latencia real del Prompt API en Pixel 9 Pro Fold (target) vs Pixel 10.

---

**Fin de la adenda.** Complementa los documentos base y el Apéndice E sin redefinir entidades, queries ni contratos existentes. Referencias `§E.x` aluden a `ADENDA_IA_ON_DEVICE.md`; `§F.x` a este documento.
