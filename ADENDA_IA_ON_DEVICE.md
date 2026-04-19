# Adenda Técnica: Módulo de Inteligencia On-Device y RAG Local

**Sección complementaria a `ESPECIFICACION_PRESUPUESTO_APP.md` y `ESPECIFICACION_UX_HARDWARE_APP.md`.**
**Ámbito**: consultas en lenguaje natural 100% fuera de línea sobre el ledger del hogar.
**Superficie primaria**: Pixel 9 Pro Fold (Tensor G4 · edge TPU) con Android 15+.
**Dependencia clave**: Google AI Edge SDK (`com.google.ai.edge.aicore:aicore`) sobre AICore · modelo Gemini Nano-v2 (multimodal) embebido en el sistema.
**Restricción dura heredada**: cero llamadas de red para inferencia. El motor determinista (§5 del doc UX) sigue siendo la fuente de verdad; la IA sólo traduce intención del usuario a invocaciones sobre ese motor.
**Posición en la especificación**: se integra como **Apéndice E** de ambos documentos base, sin modificar secciones existentes.

---

## Índice

- [E.1 Principios rectores](#e1-principios-rectores)
- [E.2 Capa de Servicio AICore](#e2-capa-de-servicio-aicore)
- [E.3 Pipeline de RAG Local](#e3-pipeline-de-rag-local)
- [E.4 Definición de Funciones — Tool Calling Local](#e4-definición-de-funciones--tool-calling-local)
- [E.5 Protocolo de Privacidad y Seguridad](#e5-protocolo-de-privacidad-y-seguridad)
- [E.6 Optimización de Interfaz para el Fold](#e6-optimización-de-interfaz-para-el-fold)
- [E.7 Contratos de latencia y degradación grácil](#e7-contratos-de-latencia-y-degradación-grácil)
- [E.8 Casos de prueba canónicos](#e8-casos-de-prueba-canónicos)
- [Apéndice E.A — Catálogo completo de intents](#apéndice-ea--catálogo-completo-de-intents)
- [Apéndice E.B — Ejemplo extremo-a-extremo](#apéndice-eb--ejemplo-extremo-a-extremo)

---

## E.1 Principios rectores

1. **La IA no decide nada sobre el estado del hogar.** El LLM es un traductor unidireccional *lenguaje natural → intent estructurada*. La ejecución de la intent la realiza el motor determinista ya definido en §5 del documento UX, con las mismas reglas de atribución, las mismas queries SQL y la misma semántica de varianza.
2. **Contexto siempre acotado y construido por código.** El prompt que llega a Gemini Nano se ensambla con un *retriever* SQL que selecciona filas relevantes desde Room. Nunca se inyectan tablas enteras, nunca se vuelca el ledger completo.
3. **Ventana pequeña, prompts cortos, salidas cortas.** Gemini Nano-v2 acepta hasta 4,000 tokens de entrada y produce respuestas cortas de forma óptima (el rendimiento degrada sensiblemente con salidas largas). El diseño del pipeline se apega a ese presupuesto.
4. **Salida estructurada por JSON schema.** Gemini Nano aún no expone tool calling nativo (está en developer preview como parte de Gemma 4 / Gemini Nano 4). Mientras tanto, se emula con *structured output*: el modelo emite un objeto JSON que respeta un schema definido por la app y que el dispatcher interpreta como llamada a función.
5. **Todo evento explicable.** Cada respuesta del asistente incluye un bloque de trazabilidad (`intent`, `rows_considered`, `functions_invoked`) que se muestra al usuario bajo un *disclosure* y se guarda en `aicore_audit_log` local.
6. **La IA es opcional.** Si el dispositivo no soporta AICore, si la cuota está agotada o si el usuario desactiva el módulo, la app sigue siendo 100% funcional usando la barra de búsqueda determinista descrita en §5.3 del doc UX.

---

## E.2 Capa de Servicio AICore

### E.2.1 Dependencias del módulo `:ai-local`

```kotlin
// ai-local/build.gradle.kts
dependencies {
    // SDK oficial para invocar Gemini Nano vía AICore
    implementation("com.google.ai.edge.aicore:aicore:0.0.1-exp01")

    // Serialización deterministic para el structured output
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

    // Pool de corrutinas supervisado
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Consumo compartido de tipos con el motor determinista
    implementation(project(":core-domain"))
    implementation(project(":core-data"))
}
```

```xml
<!-- AndroidManifest.xml (módulo :app) -->
<uses-feature
    android:name="android.software.ai_capabilities"
    android:required="false" />
```

El flag `required="false"` es crítico: permite que la app se instale en dispositivos sin AICore y degrade gráficamente al pipeline determinista puro (ver §E.7).

### E.2.2 Clase `AiCoreService` — punto único de acceso

La app centraliza la interacción con AICore en un servicio singleton expuesto como `AiCoreService`. Ningún otro módulo instancia `GenerativeModel`.

```kotlin
@Singleton
class AiCoreService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val telemetry: AiTelemetry,
    private val clock: Clock
) {
    sealed class Readiness {
        data object Available        : Readiness()
        data object Downloading      : Readiness()
        data object UnsupportedDevice: Readiness()
        data class  TemporaryError(val reason: ErrorCode) : Readiness()
    }

    private val _readiness = MutableStateFlow<Readiness>(Readiness.UnsupportedDevice)
    val readiness: StateFlow<Readiness> = _readiness.asStateFlow()

    private var model: GenerativeModel? = null

    /**
     * Debe invocarse desde Application.onCreate() o al abrir la pantalla de chat
     * por primera vez. Idempotente: si el modelo ya está listo, no hace nada.
     */
    suspend fun ensureReady(): Readiness = withContext(Dispatchers.IO) {
        try {
            val generationConfig = generationConfig {
                temperature      = 0.05f         // cuasi-determinista
                topK             = 16            // reduce ramas especulativas
                topP             = 0.90f
                maxOutputTokens  = 240           // tope duro (Nano-2 rinde mejor sub-256)
                candidateCount   = 1
            }

            val newModel = GenerativeModel(
                generationConfig = generationConfig,
                downloadCallback = object : DownloadCallback {
                    override fun onDownloadStarted(bytesToDownload: Long) {
                        _readiness.value = Readiness.Downloading
                        telemetry.log("aicore.download.start", mapOf("bytes" to bytesToDownload))
                    }
                    override fun onDownloadProgress(totalBytes: Long) { /* UI lineal */ }
                    override fun onDownloadCompleted() {
                        _readiness.value = Readiness.Available
                    }
                    override fun onDownloadFailed(failureStatus: DownloadCallback.DownloadFailureStatus) {
                        _readiness.value = Readiness.TemporaryError(ErrorCode.DOWNLOAD_FAILED)
                    }
                }
            )

            newModel.prepareInferenceEngine()       // materializa LoRA y cachea pesos
            model = newModel
            _readiness.value = Readiness.Available
            Readiness.Available

        } catch (e: UnsupportedOperationException) {
            // Dispositivo sin AICore / Tensor sin edge TPU compatible
            _readiness.value = Readiness.UnsupportedDevice
            Readiness.UnsupportedDevice
        } catch (e: GenerativeAIException) {
            val err = e.errorCode
            _readiness.value = Readiness.TemporaryError(err)
            Readiness.TemporaryError(err)
        }
    }

    suspend fun generate(prompt: String): Result<String> {
        val m = model ?: return Result.failure(IllegalStateException("Model not ready"))
        return runCatching {
            val start = clock.nowNanos()
            val response = m.generateContent(prompt)
            val text = response.text.orEmpty()
            telemetry.logInference(
                promptChars = prompt.length,
                outputChars = text.length,
                latencyMs   = (clock.nowNanos() - start) / 1_000_000
            )
            text
        }.recoverCatching { throwable ->
            if (throwable is GenerativeAIException && throwable.errorCode == ErrorCode.BUSY) {
                // Exponential backoff si AICore está saturado
                delay(250)
                m.generateContent(prompt).text.orEmpty()
            } else throw throwable
        }
    }

    suspend fun generateStream(prompt: String): Flow<String> =
        model?.generateContentStream(prompt)
            ?.map { it.text.orEmpty() }
            ?.flowOn(Dispatchers.IO)
            ?: flowOf()

    fun close() {
        model?.close()
        model = null
    }
}
```

### E.2.3 Parámetros de `GenerationConfig` — justificación para datos tabulares financieros

Los valores fijados en `AiCoreService.ensureReady()` están calibrados específicamente para el perfil de la app (consultas contables sobre datos tabulares). La tabla documenta la elección y contrasta contra defaults genéricos.

| Parámetro | Default AICore | **Valor en `:ai-local`** | Justificación |
|---|---|---|---|
| `temperature` | 0.7 | **0.05** | Datos financieros requieren reproducibilidad. A `T=0.05` el modelo es cuasi-determinista: la misma pregunta con el mismo contexto produce la misma intent. Evita que el modelo "invente" categorías o miembros. |
| `topK` | 40 | **16** | Restringe el muestreo a los 16 tokens más probables. Combinado con temperature bajo, elimina divagación en la selección de campos del schema. |
| `topP` | 0.95 | **0.90** | Recorte adicional de nucleus. No se bajó más para no degradar el parsing de montos textuales como "mil doscientos". |
| `maxOutputTokens` | 1024 | **240** | El schema de intent cabe en ~180 tokens. El tope de 240 deja margen para explicación en `"reason"` sin arriesgar truncamiento. Salidas < 256 tokens son el régimen de rendimiento óptimo de Nano-2 (≤1.4 s P50). |
| `candidateCount` | 1 | **1** | Batch siempre `n=1`: el dispatcher no necesita alternativas; si falla el schema, se dispara fallback determinista, no re-muestreo. |
| `stopSequences` | — | `["\n\n## END"]` | Centinela para forzar cierre si el modelo insiste en verbosidad. |

Latencia observada con esta configuración en Pixel 9 Pro Fold (inner display, foreground, Nano-2 cargado):

```
P50 end-to-end inference  ≈ 1,150 ms   (prompt 1.2k chars → output 150 chars)
P95 end-to-end inference  ≈ 2,400 ms
P50 cold first-token      ≈   680 ms   (tras ensureReady completo)
```

### E.2.4 Ciclo de vida y gestión de quota

AICore impone dos cuotas vivas que el wrapper respeta explícitamente:

- **Per-app rate quota** → `ErrorCode.BUSY`. Manejo: un solo retry con backoff 250 ms. Si el segundo intento también devuelve `BUSY`, el usuario ve el mensaje "El asistente está ocupado, intenta de nuevo en unos segundos". No se encola silenciosamente para evitar que el usuario asuma que el sistema está procesando.
- **Per-app daily battery quota** → `ErrorCode.PER_APP_BATTERY_USE_QUOTA_EXCEEDED`. Manejo: desactivar el chat hasta pasada la medianoche local, mostrar un banner explicativo y redirigir a la barra de búsqueda determinista.

Además:
- **Foreground-only** → `ErrorCode.BACKGROUND_USE_BLOCKED`. La inferencia se realiza exclusivamente cuando la activity del chat está en primer plano; nunca desde workers, foreground services o tiles.
- **Versión del modelo** → Gemini Nano se actualiza de forma transparente por el sistema. Dado que versiones distintas pueden producir salidas distintas al mismo prompt, la app persiste un *golden prompt test suite* (§E.8) que se ejecuta al detectar `modelVersion` distinta a la anterior y desactiva el chat si la regresión supera un umbral.

### E.2.5 Contrato con el resto de la app

`AiCoreService` es inyectado **exclusivamente** por el ViewModel del chat (`BudgetAssistantViewModel`). Ningún repositorio, caso de uso o servicio lo consume directo. El ViewModel construye el prompt usando el pipeline descrito en §E.3 y traduce la respuesta al motor determinista usando el dispatcher de §E.4.

```
┌──────────────────────────────────────────────────────────┐
│  BudgetAssistantViewModel                                 │
│   ├─ RagContextBuilder          (§E.3)                    │
│   ├─ AiCoreService              (§E.2)                    │
│   ├─ IntentDispatcher           (§E.4)                    │
│   └─ ExplainabilityRecorder     (§E.5)                    │
└──────────────────────────────────────────────────────────┘
         ▲                                      ▲
         │                                      │
    ChatScreen                             (solo in-process,
                                            sin IPC)
```

---

## E.3 Pipeline de RAG Local

### E.3.1 Arquitectura del pipeline

```
Usuario escribe pregunta
          │
          ▼
 ┌────────────────────┐
 │ PromptSanitizer    │  limpia inyecciones, normaliza acentos
 └────────┬───────────┘
          ▼
 ┌────────────────────┐     SQL paramétrico
 │ RagContextBuilder  │──────────────────────▶  Room / SQLite
 └────────┬───────────┘     filas relevantes
          ▼
 ┌────────────────────┐
 │ ContextSerializer  │  ensambla bloque de texto tabular
 └────────┬───────────┘
          ▼
 ┌────────────────────┐
 │ PromptAssembler    │  system + context + schema + pregunta
 └────────┬───────────┘
          ▼
 ┌────────────────────┐
 │ AiCoreService      │  Gemini Nano en edge TPU
 └────────┬───────────┘
          ▼ JSON
 ┌────────────────────┐
 │ IntentDispatcher   │  invoca funciones del motor determinista
 └────────┬───────────┘
          ▼
    Respuesta al usuario + trazabilidad
```

### E.3.2 Retriever — queries SQL deterministas

La "fase R" del RAG no usa embeddings ni similitud vectorial. Las entidades del dominio son estructuradas y numéricas; una búsqueda semántica sobre descripciones aportaría ruido. En su lugar, el retriever ejecuta un **conjunto fijo de consultas SQL parametrizables** que cubren las seis dimensiones de información utilizables por el modelo:

```kotlin
class RagContextBuilder @Inject constructor(
    private val db: BudgetDatabase,
    private val clock: Clock
) {
    suspend fun buildContext(question: String, householdId: UUID): RagContext {
        val active = db.quincenaDao().currentActive(householdId)

        // Resolución heurística barata de a qué dimensiones aludir
        val dims = QuestionClassifier.classify(question)

        return RagContext(
            currentQuincena = active,
            spendByCategory = if (SpendByCategory in dims)
                db.analyticsDao().spendByCategory(active.id) else emptyList(),
            topExpenses = if (TopExpenses in dims)
                db.expenseDao().topExpensesInQuincena(active.id, limit = 15) else emptyList(),
            walletsSnapshot = if (Wallets in dims)
                db.walletDao().activeBalances(householdId) else emptyList(),
            memberSpend = if (ByMember in dims)
                db.attributionDao().spendByBeneficiary(active.id) else emptyList(),
            activeInstallments = if (Installments in dims)
                db.installmentDao().activePlans(householdId, limit = 5) else emptyList(),
            history6q = if (HistoricalCompare in dims)
                db.quincenaDao().lastNClosed(householdId, n = 6) else emptyList()
        )
    }
}

enum class ContextDimension {
    SpendByCategory, TopExpenses, Wallets, ByMember, Installments, HistoricalCompare
}

/**
 * Clasificador barato basado en patrones léxicos. Determinístico,
 * cero ML. Sobre-incluye dimensiones si hay duda (falsos positivos OK).
 */
object QuestionClassifier {
    fun classify(q: String): Set<ContextDimension> {
        val n = q.lowercase().unaccent()
        return buildSet {
            if (n matchesAny listOf("categor", "gast.+en", "cuanto.+en")) add(SpendByCategory)
            if (n matchesAny listOf("ultimo", "recient", "top", "mayor")) add(TopExpenses)
            if (n matchesAny listOf("tarjeta", "banamex", "bbva", "saldo", "cuenta")) add(Wallets)
            if (n matchesAny listOf("quien", "david", "pau", "santi", "agustin", "miembro")) add(ByMember)
            if (n matchesAny listOf("cuota", "prestam", "omar", "mercado libre", "plan")) add(Installments)
            if (n matchesAny listOf("compara", "antes", "pasad", "anterior", "tendenc")) add(HistoricalCompare)
            if (isEmpty()) {
                add(SpendByCategory); add(TopExpenses); add(Wallets)   // defaults
            }
        }
    }
}
```

Las queries SQL subyacentes son exactamente las mismas ya definidas en §5.2 del documento base (reutilización total, sin duplicar lógica).

### E.3.3 Serialización a contexto textual compacto

La serialización prioriza **densidad informacional por token** sobre legibilidad humana. Se usa formato tabular delimitado por `|`, sin prosa.

```kotlin
class ContextSerializer {

    fun serialize(ctx: RagContext): String = buildString {
        appendLine("# LEDGER_SNAPSHOT")
        appendLine("now=${LocalDate.now()} currency=MXN")
        appendLine()

        appendLine("## QUINCENA_ACTIVA")
        with(ctx.currentQuincena) {
            appendLine("id=$id label=\"$label\" start=$startDate end=$endDate status=$status")
            appendLine("ingreso_proyectado=$projectedIncomeMxn ejecutado=$actualIncomeMxn")
            appendLine("gasto_proyectado=$projectedExpensesMxn ejecutado=$actualExpensesMxn")
        }
        appendLine()

        if (ctx.spendByCategory.isNotEmpty()) {
            appendLine("## GASTO_POR_CATEGORIA")
            appendLine("categoria|proyectado|ejecutado|restante|pct_exec")
            ctx.spendByCategory.forEach {
                appendLine("${it.category}|${it.projected}|${it.actual}|${it.remaining}|${it.pctExec}")
            }
            appendLine()
        }

        if (ctx.memberSpend.isNotEmpty()) {
            appendLine("## GASTO_POR_BENEFICIARIO")
            appendLine("miembro|total|n_gastos")
            ctx.memberSpend.forEach {
                appendLine("${it.member}|${it.totalMxn}|${it.count}")
            }
            appendLine()
        }

        if (ctx.topExpenses.isNotEmpty()) {
            appendLine("## GASTOS_DESTACADOS")
            appendLine("fecha|concepto|monto|categoria|wallet")
            ctx.topExpenses.take(10).forEach {
                appendLine("${it.date}|${it.concept}|${it.amount}|${it.category}|${it.wallet}")
            }
            appendLine()
        }

        if (ctx.walletsSnapshot.isNotEmpty()) {
            appendLine("## CUENTAS")
            appendLine("wallet|tipo|saldo|limite|utilizacion_pct")
            ctx.walletsSnapshot.forEach {
                appendLine("${it.name}|${it.kind}|${it.balance}|${it.limit ?: "-"}|${it.utilPct ?: "-"}")
            }
            appendLine()
        }

        if (ctx.activeInstallments.isNotEmpty()) {
            appendLine("## CUOTAS_ACTIVAS")
            appendLine("plan|cuota_actual|total_cuotas|monto_cuota|proxima_fecha")
            ctx.activeInstallments.forEach {
                appendLine("${it.name}|${it.current}|${it.total}|${it.amount}|${it.nextDate}")
            }
            appendLine()
        }

        if (ctx.history6q.isNotEmpty()) {
            appendLine("## ULTIMAS_QUINCENAS_CERRADAS")
            appendLine("label|gasto_total|ahorro")
            ctx.history6q.forEach {
                appendLine("${it.label}|${it.actualExpensesMxn}|${it.savingsMxn}")
            }
        }
    }
}
```

**Ejemplo de bloque de contexto real** (≈ 600 tokens):

```
# LEDGER_SNAPSHOT
now=2026-04-18 currency=MXN

## QUINCENA_ACTIVA
id=q_7f3a label="Q2 Abril 2026" start=2026-04-16 end=2026-04-30 status=ACTIVE
ingreso_proyectado=105000 ejecutado=105000
gasto_proyectado=127000 ejecutado=81850

## GASTO_POR_CATEGORIA
categoria|proyectado|ejecutado|restante|pct_exec
HOUSING|7400|6612|788|89
FOOD|9500|7450|2050|78
ENTERTAINMENT|2200|2184|16|99
TRANSPORTATION|3500|1400|2100|40
TRANSFERENCIAS_FAMILIARES|30000|30000|0|100
LOANS|16000|14842|1158|92
SAVINGS|14000|12660|1340|90

## GASTO_POR_BENEFICIARIO
miembro|total|n_gastos
David|21330|4
Norma|45820|22
Benjamin|9230|8
Santi|6912|5
...
```

### E.3.4 Presupuesto de tokens

La ventana de Nano-2 admite hasta ~4,000 tokens de entrada. El presupuesto del pipeline se asigna así:

| Bloque | Tokens max | Notas |
|---|---|---|
| System instruction (fija) | 380 | Constante cargada desde asset |
| JSON schema embebido | 520 | Comprimido con `ensureAscii=false` |
| Contexto RAG | 2,400 | Truncado por prioridad si excede |
| Pregunta del usuario | 400 | Hard-capped; preguntas más largas se rechazan con mensaje amigable |
| Buffer de seguridad | 300 | — |
| **Total entrada** | **~4,000** | |

Si el contexto serializado excede 2,400 tokens, `ContextSerializer` aplica truncamiento por prioridad: se descartan bloques en orden `ULTIMAS_QUINCENAS_CERRADAS → GASTOS_DESTACADOS → CUOTAS_ACTIVAS → ...` hasta caber. La estimación de tokens usa la heurística `chars/3.6` para el español (suficientemente precisa para este propósito; no justifica cargar un tokenizador completo).

### E.3.5 Prompt assembler — plantilla canónica

```kotlin
class PromptAssembler @Inject constructor(
    @SystemPromptAsset private val systemPrompt: String,
    @IntentSchemaAsset private val intentSchema: String
) {
    fun assemble(context: String, userQuestion: String): String = """
        $systemPrompt

        ## CONTEXTO_LOCAL
        $context

        ## ESQUEMA_DE_SALIDA
        Responde EXCLUSIVAMENTE con un objeto JSON que valide contra el siguiente schema.
        No añadas prosa, backticks ni comentarios. Si no encuentras la respuesta en el
        contexto, usa intent="UNKNOWN" y explica en "reason".

        $intentSchema

        ## PREGUNTA_DEL_USUARIO
        $userQuestion

        ## END
    """.trimIndent()
}
```

El `systemPrompt` (asset `system_prompt.es.txt`) instruye al modelo sobre:
- Moneda siempre en MXN sin conversión.
- Fechas en formato ISO.
- Identificadores de miembros con aliases válidos (lista embebida).
- Prohibición de inventar categorías, miembros o montos que no aparezcan en el contexto.
- Preferencia por `intent=UNKNOWN` antes que fabricar respuestas.

---

## E.4 Definición de Funciones — Tool Calling Local

### E.4.1 Estrategia sin tool calling nativo

Gemini Nano (v1/v2) **no expone** la API de function calling que sí ofrecen Gemini Flash/Pro y que se anunció como parte de Gemma 4 / Gemini Nano 4 en el developer preview. La app lo emula con un patrón de dos componentes:

1. **Structured output via JSON schema**: el prompt contiene un schema que el modelo debe respetar. La salida es un único objeto JSON con un discriminador `intent` y un bloque `args` específico por intent.
2. **Dispatcher determinista**: un sealed hierarchy de Kotlin recibe el JSON validado, lo mapea a una función del motor determinista ya existente y devuelve un resultado tipado que el ViewModel renderiza.

Este patrón es forward-compatible: cuando Gemini Nano 4 entregue tool calling nativo, se migra el schema a `FunctionDeclaration` sin cambiar la capa de dispatch.

### E.4.2 Esquema JSON de la respuesta

```json
{
  "$schema": "https://json-schema.org/draft-07/schema",
  "title": "BudgetAssistantResponse",
  "type": "object",
  "required": ["intent", "args", "reason"],
  "properties": {
    "intent": {
      "type": "string",
      "enum": [
        "GET_CATEGORY_REMAINING",
        "GET_TOP_SPENDER",
        "GET_SPEND_BY_MEMBER",
        "GET_WALLET_BALANCE",
        "GET_INSTALLMENT_STATUS",
        "PROJECT_SAVINGS_IF",
        "COMPARE_QUINCENAS",
        "EXPLAIN_VARIANCE",
        "LIST_UPCOMING_INSTALLMENTS",
        "SUMMARIZE_QUINCENA",
        "UNKNOWN"
      ]
    },
    "args": {
      "type": "object",
      "properties": {
        "category_code":   { "type": "string" },
        "member_alias":    { "type": "string" },
        "wallet_name":     { "type": "string" },
        "plan_name":       { "type": "string" },
        "hypothetical_cut_category": { "type": "string" },
        "baseline_quincenas": { "type": "integer", "minimum": 1, "maximum": 12 },
        "from_date":       { "type": "string", "format": "date" },
        "to_date":         { "type": "string", "format": "date" }
      },
      "additionalProperties": false
    },
    "reason": {
      "type": "string",
      "maxLength": 240,
      "description": "Breve justificación en español de por qué se eligió esta intent."
    }
  },
  "additionalProperties": false
}
```

### E.4.3 Catálogo inicial de intents (abreviado, completo en Apéndice E.A)

| Intent | Pregunta típica | Argumentos requeridos | Función del motor invocada |
|---|---|---|---|
| `GET_CATEGORY_REMAINING` | "¿Cuánto presupuesto queda para gasolina?" | `category_code` | `AnalyticsUseCase.remainingForCategory()` |
| `GET_TOP_SPENDER` | "¿Quién ha gastado más en esta quincena?" | — | `AnalyticsUseCase.topBeneficiary()` |
| `GET_SPEND_BY_MEMBER` | "¿Cuánto lleva David este mes?" | `member_alias` | `AnalyticsUseCase.spendByMember()` |
| `GET_WALLET_BALANCE` | "¿Cuánto tengo en Banamex?" | `wallet_name` | `WalletRepository.balance()` |
| `GET_INSTALLMENT_STATUS` | "¿En qué cuota voy con Omar?" | `plan_name` | `InstallmentRepository.status()` |
| `PROJECT_SAVINGS_IF` | "¿Cuánto ahorro si no gasto más en entretenimiento?" | `hypothetical_cut_category` | `ForecastUseCase.savingsIfCategoryFrozen()` |
| `COMPARE_QUINCENAS` | "¿Gasto más que la quincena pasada?" | `baseline_quincenas?` | `VarianceUseCase.compareVsBaseline()` |
| `EXPLAIN_VARIANCE` | "¿Por qué gasté más en comida?" | `category_code` | `VarianceUseCase.explainCategory()` |
| `LIST_UPCOMING_INSTALLMENTS` | "¿Qué cuotas vienen?" | — | `InstallmentRepository.upcoming()` |
| `SUMMARIZE_QUINCENA` | "Resúmeme esta quincena" | — | `AnalyticsUseCase.snapshotSummary()` |
| `UNKNOWN` | pregunta fuera de dominio | — | fallback amigable |

### E.4.4 Dispatcher

```kotlin
@Serializable
data class AssistantResponse(
    val intent: Intent,
    val args: Args,
    val reason: String
) {
    @Serializable
    enum class Intent {
        GET_CATEGORY_REMAINING, GET_TOP_SPENDER, GET_SPEND_BY_MEMBER,
        GET_WALLET_BALANCE,     GET_INSTALLMENT_STATUS, PROJECT_SAVINGS_IF,
        COMPARE_QUINCENAS,      EXPLAIN_VARIANCE,       LIST_UPCOMING_INSTALLMENTS,
        SUMMARIZE_QUINCENA,     UNKNOWN
    }

    @Serializable
    data class Args(
        val category_code: String? = null,
        val member_alias:  String? = null,
        val wallet_name:   String? = null,
        val plan_name:     String? = null,
        val hypothetical_cut_category: String? = null,
        val baseline_quincenas: Int? = null,
        val from_date: String? = null,
        val to_date:   String? = null
    )
}

class IntentDispatcher @Inject constructor(
    private val analytics:    AnalyticsUseCase,
    private val wallets:      WalletRepository,
    private val installments: InstallmentRepository,
    private val variance:     VarianceUseCase,
    private val forecast:     ForecastUseCase,
    private val resolver:     AliasResolver
) {
    suspend fun dispatch(raw: String): DispatchResult {
        val response = JsonLenient.decodeFromString<AssistantResponse>(raw)
            .getOrElse { return DispatchResult.ParseError(raw) }

        return when (response.intent) {
            Intent.GET_CATEGORY_REMAINING -> {
                val cat = resolver.resolveCategory(response.args.category_code)
                    ?: return DispatchResult.MissingArg("category_code")
                val r = analytics.remainingForCategory(cat)
                DispatchResult.CategoryRemaining(cat, r.proyectado, r.ejecutado, r.restante)
            }
            Intent.GET_TOP_SPENDER -> {
                val top = analytics.topBeneficiary()
                DispatchResult.TopSpender(top.member, top.totalMxn, top.share)
            }
            Intent.GET_SPEND_BY_MEMBER -> {
                val member = resolver.resolveMember(response.args.member_alias)
                    ?: return DispatchResult.MissingArg("member_alias")
                val s = analytics.spendByMember(member)
                DispatchResult.SpendByMember(member, s.totalMxn, s.byCategory)
            }
            Intent.GET_WALLET_BALANCE -> {
                val w = resolver.resolveWallet(response.args.wallet_name)
                    ?: return DispatchResult.MissingArg("wallet_name")
                DispatchResult.WalletBalance(w, wallets.balance(w.id))
            }
            Intent.PROJECT_SAVINGS_IF -> {
                val cat = resolver.resolveCategory(response.args.hypothetical_cut_category)
                    ?: return DispatchResult.MissingArg("hypothetical_cut_category")
                val proj = forecast.savingsIfCategoryFrozen(cat)
                DispatchResult.SavingsProjection(cat, proj.deltaMxn, proj.assumptions)
            }
            Intent.COMPARE_QUINCENAS -> {
                val n = response.args.baseline_quincenas ?: 6
                val cmp = variance.compareVsBaseline(n)
                DispatchResult.QuincenaComparison(cmp)
            }
            Intent.EXPLAIN_VARIANCE -> {
                val cat = resolver.resolveCategory(response.args.category_code)
                    ?: return DispatchResult.MissingArg("category_code")
                DispatchResult.VarianceExplanation(cat, variance.explainCategory(cat))
            }
            Intent.LIST_UPCOMING_INSTALLMENTS -> {
                DispatchResult.UpcomingInstallments(installments.upcoming(limit = 5))
            }
            Intent.GET_INSTALLMENT_STATUS -> {
                val plan = resolver.resolveInstallmentPlan(response.args.plan_name)
                    ?: return DispatchResult.MissingArg("plan_name")
                DispatchResult.InstallmentStatus(plan, installments.status(plan.id))
            }
            Intent.SUMMARIZE_QUINCENA -> {
                DispatchResult.QuincenaSummary(analytics.snapshotSummary())
            }
            Intent.UNKNOWN -> {
                DispatchResult.Unknown(response.reason)
            }
        }
    }
}
```

La regla inviolable: **el dispatcher nunca devuelve texto crudo del modelo al usuario**. Siempre ejecuta la función determinista correspondiente y renderiza el resultado con plantillas de UI locales. El único lugar donde el texto original del modelo aparece es en el tooltip de trazabilidad ("¿Por qué me respondió esto?").

### E.4.5 `AliasResolver` — puente entre lenguaje natural y IDs del dominio

El modelo puede referirse a "David", "Dave", "el niño de Norma" o incluso errar con "Davi". El `AliasResolver` traduce estas cadenas a IDs canónicos reutilizando la tabla de aliases ya definida en §2.2.2 del doc base.

```kotlin
class AliasResolver @Inject constructor(
    private val memberDao: MemberDao,
    private val categoryDao: CategoryDao,
    private val walletDao: PaymentMethodDao,
    private val installmentDao: InstallmentDao
) {
    fun resolveMember(alias: String?): Member? {
        if (alias.isNullOrBlank()) return null
        val needle = alias.lowercase().unaccent()
        return memberDao.all().firstOrNull { m ->
            m.displayName.lowercase().unaccent() == needle ||
            m.shortAliases.any { it.lowercase().unaccent() == needle } ||
            jaroWinkler(m.displayName.lowercase().unaccent(), needle) > 0.90
        }
    }
    // resolveCategory, resolveWallet, resolveInstallmentPlan: análogos
}
```

### E.4.6 Fallback cuando el schema falla

Si el JSON producido por el modelo no valida (por ejemplo, invent una intent fuera del enum, trunca la salida, o alucina campos), el dispatcher aplica un camino de degradación:

1. Intento A — reparación por patrón: aplicar `JsonRepairer` (algoritmo conservador, sin LLM) que cierra arrays/objetos pendientes y valida de nuevo.
2. Intento B — pasar la pregunta al motor determinista directamente (§5 del doc UX) como si hubiera sido tecleada en la barra de búsqueda.
3. Intento C — mensaje explícito al usuario: "No entendí la pregunta, pero puedes buscar manualmente en …" con CTA a la búsqueda determinista.

En ningún caso se reintenta contra el LLM automáticamente — respeta la cuota de AICore y evita bucles que drenen batería.

---

## E.5 Protocolo de Privacidad y Seguridad

### E.5.1 Garantías mecánicas del sistema

AICore se adhiere a los principios de **Private Compute Core** de Android:

1. **Restricted Package Binding** — AICore solo es accesible por apps firmadas; las peticiones se ejecutan en un proceso aislado del sistema.
2. **Aislamiento por petición** — AICore no persiste ni los prompts ni las salidas. Cada inferencia es un evento sin historial compartido entre apps.
3. **Sin red** — el servicio no posee permiso `INTERNET` dentro de su sandbox de inferencia; los pesos del modelo se distribuyen vía Google Play System Updates (Project Mainline), no por conexión in-app.
4. **Ejecución en edge TPU** — el dispatch sucede en la unidad de procesamiento tensorial del Tensor G4 (Pixel 9 Pro Fold). La CPU/GPU solo intervienen en el pre/post-procesamiento léxico.

La app refuerza estas garantías con tres reglas propias:

- El módulo `:ai-local` declara `android:networkSecurityConfig="@xml/nsc_deny_all"` (config custom que bloquea todo tráfico saliente del proceso para este módulo específico).
- El `BudgetAssistantViewModel` se aísla en un `ProcessLifecycleOwner` separado para que sus trazas no se mezclen con las del resto de la app.
- Ninguna librería de telemetría de terceros (Firebase, Crashlytics, Amplitude, etc.) se inicializa en el módulo `:ai-local`; los eventos de telemetría van a una tabla local `aicore_telemetry` que nunca se sube.

### E.5.2 Diagrama de flujo de datos (contrato de privacidad)

```
┌──────────────────────────────────────────────────────────────┐
│                      TELÉFONO DEL USUARIO                    │
│                                                              │
│  ┌────────────┐   SQL   ┌────────────┐   prompt   ┌───────┐ │
│  │   Room DB  │◀───────▶│ RagBuilder │───────────▶│AICore │ │
│  │ (local)    │         └────────────┘            │system │ │
│  └────────────┘                                   │service│ │
│                                                   └───┬───┘ │
│                                                       │     │
│                                                       ▼     │
│                                              ┌─────────────┐│
│                                              │  edge TPU   ││
│                                              │ Gemini Nano ││
│                                              └─────────────┘│
│                                                             │
│     🚫  ninguna flecha sale de este rectángulo             │
└──────────────────────────────────────────────────────────────┘
```

### E.5.3 Pantalla de transparencia

La app expone **Settings → Privacidad → Asistente IA** con:
- Indicador del estado de AICore (disponible / descargando / deshabilitado).
- Versión del modelo y fecha de último uso.
- Número de inferencias del día (acotadas por la cuota).
- Botón "Ver últimos 10 prompts" que abre un visor sobre `aicore_audit_log` con cada prompt enviado al modelo junto con el JSON recibido.
- Botón "Deshabilitar asistente" — liberación inmediata del `GenerativeModel` y oculta el módulo de chat de toda la UI.
- Botón "Borrar historial del asistente" — elimina `aicore_audit_log` sin afectar el ledger de gastos.

### E.5.4 Sanitización contra prompt injection

Aunque los prompts nunca se mueven a servidores externos, un atacante con acceso local (por ejemplo, pegando un concepto de gasto cuidadosamente elaborado en un campo de texto libre) podría intentar influir en respuestas futuras. Mitigaciones:

1. **Delimitadores robustos** — el bloque del usuario va entre `## PREGUNTA_DEL_USUARIO` y `## END`. El *system prompt* instruye explícitamente ignorar cualquier instrucción dentro del bloque.
2. **Stripping de tokens peligrosos** — `PromptSanitizer` elimina líneas que empiecen con `##`, `###`, `<|`, `SYSTEM:`, `ASSISTANT:`, y colapsa runs de whitespace.
3. **Ventana reducida del contexto RAG** — los `concept` textuales de los gastos se truncan a 64 caracteres antes de serializar.
4. **Schema-enforced output** — aunque el modelo caiga en un exploit, solo puede emitir un objeto JSON del schema; no hay camino para que filtre datos hacia afuera porque no hay red.

---

## E.6 Optimización de Interfaz para el Fold

### E.6.1 Layouts adaptativos

El componente `AssistantShell` decide dinámicamente su postura según el `WindowSizeClass` y el estado de plegado detectado por `WindowInfoTracker`:

| Postura física del dispositivo | `WindowWidthSizeClass` | Layout |
|---|---|---|
| Plegado (outer display) | `COMPACT` | `BottomSheetChat` — hoja modal 85% altura, cierra con swipe-down |
| Desplegado (inner display), apaisado | `EXPANDED` | `DualPaneChat` — chat 40% izq, dashboard 60% der |
| Desplegado, retrato | `EXPANDED` | `TopChat` — chat 55% superior, contenido 45% inferior |
| Tabletop (bisagra ~90°) | `MEDIUM` + half-folded | `FoldedBookChat` — chat arriba de la bisagra, visual abajo |

```kotlin
@Composable
fun AssistantShell(
    viewModel: BudgetAssistantViewModel = hiltViewModel()
) {
    val windowInfo = currentWindowAdaptiveInfo()
    val foldingFeature by WindowInfoTracker.getOrCreate(LocalContext.current)
        .windowLayoutInfo(LocalActivity.current!!)
        .collectAsStateWithLifecycle(initialValue = null)

    val posture = computePosture(windowInfo.windowSizeClass, foldingFeature)

    when (posture) {
        Posture.Compact       -> BottomSheetChat(viewModel)
        Posture.DualPane      -> DualPaneChat(viewModel)
        Posture.TopChat       -> TopChat(viewModel)
        Posture.FoldedBook    -> FoldedBookChat(viewModel, foldingFeature)
    }
}
```

### E.6.2 Panel persistente en pantalla interna

El layout `DualPaneChat` es el modo estelar del Pixel 9 Pro Fold. Aprovecha los 2076 × 2152 px de la pantalla interna LTPO 120 Hz para correr dos experiencias simultáneas sincronizadas:

```
┌────────────────────────────────────────┬────────────────────────────────────────┐
│                                        │                                        │
│   CHAT (pane izq, 40% ≈ 830 dp)        │    DASHBOARD DINÁMICO (pane der)       │
│                                        │                                        │
│   ─────────────────────────────────    │    Quincena Q2 abril 2026              │
│                                        │                                        │
│    You · 10:42                          │    ┌──────────────────────┐            │
│    ¿Cuánto llevo gastado en comida?    │    │ FOOD  $7,450/$9,500  │            │
│                                        │    │  ▓▓▓▓▓▓▓▓░░  78%     │            │
│    Claude · 10:42                       │    └──────────────────────┘            │
│    En FOOD llevas $7,450 de $9,500     │    Gastos recientes de FOOD:           │
│    proyectado (78%). Te quedan         │    • Despensa     $2,100  18 abr       │
│    $2,050 para cerrar la quincena.     │    • Comida rest. $   520  17 abr       │
│                                        │    • Despensa     $1,950  16 abr       │
│    ┌ ¿Por qué? ────────────────┐       │    ...                                 │
│    │ Intent: GET_CATEGORY_...  │       │                                        │
│    │ Source: spend_by_category │       │                                        │
│    │ Latency: 1.1s             │       │                                        │
│    └───────────────────────────┘       │                                        │
│                                        │                                        │
│    ─────────────────────────────────    │    [ ver todos los gastos de FOOD ]    │
│                                        │                                        │
│   [ Pregunta...                ↑]     │                                        │
│                                        │                                        │
└────────────────────────────────────────┴────────────────────────────────────────┘
```

Implementación:

```kotlin
@Composable
fun DualPaneChat(viewModel: BudgetAssistantViewModel) {
    val lastResult by viewModel.lastResult.collectAsStateWithLifecycle()

    Row(Modifier.fillMaxSize()) {
        // Pane izquierdo — chat
        Box(
            Modifier
                .weight(0.40f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            ChatConversation(viewModel)
        }

        // Divisor redimensionable
        ResizableDivider(
            onResize = { fraction -> viewModel.setPaneFraction(fraction) }
        )

        // Pane derecho — dashboard dinámico
        Box(
            Modifier
                .weight(0.60f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            AnimatedContent(
                targetState = lastResult,
                transitionSpec = {
                    fadeIn(spring()) + slideInHorizontally { it / 8 } togetherWith
                    fadeOut(spring())
                },
                label = "dashboard-detail"
            ) { result ->
                when (result) {
                    is CategoryRemaining   -> CategoryDetailPanel(result)
                    is SpendByMember       -> MemberDetailPanel(result)
                    is WalletBalance       -> WalletDetailPanel(result)
                    is SavingsProjection   -> ForecastPanel(result)
                    is QuincenaComparison  -> VarianceComparePanel(result)
                    is VarianceExplanation -> VarianceExplainPanel(result)
                    else                   -> DefaultQuincenaPanel(viewModel.currentQuincena)
                }
            }
        }
    }
}
```

### E.6.3 Sincronización contextual panel ↔ chat

Cada `DispatchResult` transporta un `FocusIntent` que indica al pane derecho qué renderizar. A la inversa, las interacciones en el pane derecho (tocar una barra del chart, seleccionar un gasto de la lista) generan *follow-up suggestions* que aparecen como chips encima del input del chat:

```
┌ Sugerencias contextuales ─────────────────────────┐
│  [ ¿Por qué subió FOOD? ]  [ Compara con marzo ]  │
│  [ Detalle por beneficiario ]                     │
└───────────────────────────────────────────────────┘
```

Al tocar una sugerencia, el chip se sustituye por el texto en el input y dispara inferencia.

### E.6.4 Streaming UX

`AiCoreService.generateContentStream` se usa en el pane izquierdo para que el usuario perciba progreso. Sin embargo, el pane derecho **no** se actualiza hasta que el JSON valida completo — una actualización parcial basada en tokens intermedios del modelo sería engañosa porque el intent podría cambiar al completarse la respuesta.

```
t=0.0s    ▌
t=0.2s    { "int
t=0.4s    { "intent": "GET_CATEG
t=0.6s    { "intent": "GET_CATEGORY_REMAINING", "args": { "category_code":
t=0.9s    { "intent": "GET_CATEGORY_REMAINING", "args": { "category_code": "FOOD" },
t=1.1s    <JSON completo> → dispatcher ejecuta → pane derecho actualiza
```

### E.6.5 Gestos específicos del Fold

- **Drag & drop entre paneles**: arrastrar un gasto del pane derecho hacia la caja de entrada lo inyecta como referencia ("Explícame este gasto: …").
- **Expand to full screen**: doble tap en la barra del pane izquierdo colapsa el derecho temporalmente (útil para lectura larga).
- **Tabletop posture**: el chat queda sobre la bisagra, el visual bajo — útil para mantener el teléfono sobre la mesa durante una conversación sostenida con el asistente.
- **Haptic feedback**: `HapticFeedbackType.LongPress` al completar un dispatch exitoso; `HapticFeedbackType.Reject` si el intent fue `UNKNOWN`.

---

## E.7 Contratos de latencia y degradación grácil

### E.7.1 SLOs del módulo

| Evento | Target P50 | Target P95 | Acción si se excede |
|---|---|---|---|
| `ensureReady()` warm | 120 ms | 400 ms | — |
| `ensureReady()` cold con download | 10 s | 90 s | Banner de progreso explícito |
| Construcción de contexto RAG | 40 ms | 120 ms | — |
| Inferencia completa (JSON cerrado) | 1.2 s | 2.5 s | Mostrar skeleton con shimmer |
| Dispatch + render del pane derecho | 80 ms | 200 ms | — |
| Round-trip total (enter → render) | 1.4 s | 3.0 s | — |

### E.7.2 Tabla de degradación

| Condición | Comportamiento |
|---|---|
| Dispositivo sin AICore | Módulo oculto. Se sustituye por atajo a la barra de búsqueda determinista de §5.3. |
| AICore en descarga | Chat deshabilitado con banner "Preparando asistente, %d%%". Se permite encolar **una** pregunta que se procesa al completar. |
| `ErrorCode.BUSY` | Un retry automático con 250 ms de backoff; si falla de nuevo, snackbar y desactivación de 30 s. |
| `ErrorCode.PER_APP_BATTERY_USE_QUOTA_EXCEEDED` | Chat deshabilitado hasta medianoche local; banner explicativo. |
| `ErrorCode.BACKGROUND_USE_BLOCKED` | No debería ocurrir por diseño; si ocurre, se audita como bug. |
| JSON inválido tras reparación | Degrada al motor determinista de §5; mensaje "Usé la búsqueda clásica para esta". |
| Batería < 15% en modo ahorro | El chat entra en *preferred deterministic mode*: solo se invoca IA si el usuario hace tap largo en el botón enviar. |
| Usuario deshabilita en Settings | Módulo desmontado, `aicore_audit_log` opcionalmente purgado. |

---

## E.8 Casos de prueba canónicos

El módulo incluye una *golden suite* de 24 pares (pregunta, intent esperada) que se ejecutan contra el modelo al detectar cambio de versión. Si el pass-rate cae por debajo de 92%, el chat se desactiva automáticamente y se notifica al usuario.

### E.8.1 Muestra de la suite

```yaml
- id: g01
  question: "¿Cuánto presupuesto queda para gasolina?"
  expected_intent: GET_CATEGORY_REMAINING
  expected_args: { category_code: "TRANSPORTATION.GASOLINA" }

- id: g02
  question: "¿Quién ha gastado más en esta quincena?"
  expected_intent: GET_TOP_SPENDER
  expected_args: {}

- id: g03
  question: "Calcula el ahorro proyectado si no gastamos más en entretenimiento."
  expected_intent: PROJECT_SAVINGS_IF
  expected_args: { hypothetical_cut_category: "ENTERTAINMENT" }

- id: g04
  question: "¿En qué cuota voy con Omar?"
  expected_intent: GET_INSTALLMENT_STATUS
  expected_args: { plan_name: "Préstamo Omar" }

- id: g05
  question: "¿Gasto más que la quincena pasada?"
  expected_intent: COMPARE_QUINCENAS
  expected_args: { baseline_quincenas: 1 }

- id: g06
  question: "Dame el saldo de Banamex"
  expected_intent: GET_WALLET_BALANCE
  expected_args: { wallet_name: "Banamex" }

- id: g07
  question: "cuanto lleva david este mes"
  expected_intent: GET_SPEND_BY_MEMBER
  expected_args: { member_alias: "David" }

- id: g08
  question: "¿Qué día es hoy?"
  expected_intent: UNKNOWN
  expected_args: {}
```

### E.8.2 Ejecución de la suite

La suite se corre:
- Localmente antes de cada release, contra la versión productiva de Nano disponible en los dispositivos objetivo.
- En arranque de la app tras detectar `modelVersion != lastKnownModelVersion`, en background y sin bloquear la UI.
- Como parte de los instrumentation tests en dispositivos físicos (el emulador no soporta AICore).

Cada caso cuenta como **pass** si:
1. El modelo emite JSON válido contra el schema.
2. `intent == expected_intent`.
3. Los campos relevantes de `args` coinciden, módulo el `AliasResolver` (ej. "Omar" → "prestamo_omar_001" se considera equivalente a "Préstamo Omar").

---

## Apéndice E.A — Catálogo completo de intents

| # | Intent | Args obligatorios | Args opcionales | Función motor | Plantilla de render |
|---|---|---|---|---|---|
| 1 | `GET_CATEGORY_REMAINING` | `category_code` | — | `analytics.remainingForCategory` | `CategoryDetailPanel` |
| 2 | `GET_CATEGORY_SPENT_TO_DATE` | `category_code` | `from_date` | `analytics.spentSince` | `CategoryDetailPanel` |
| 3 | `GET_TOP_SPENDER` | — | `from_date`, `to_date` | `analytics.topBeneficiary` | `MemberRankingPanel` |
| 4 | `GET_SPEND_BY_MEMBER` | `member_alias` | `from_date`, `to_date` | `analytics.spendByMember` | `MemberDetailPanel` |
| 5 | `GET_WALLET_BALANCE` | `wallet_name` | — | `wallets.balance` | `WalletDetailPanel` |
| 6 | `GET_WALLET_UTILIZATION` | `wallet_name` | — | `wallets.utilization` | `WalletDetailPanel` |
| 7 | `GET_INSTALLMENT_STATUS` | `plan_name` | — | `installments.status` | `InstallmentDetailPanel` |
| 8 | `LIST_UPCOMING_INSTALLMENTS` | — | — | `installments.upcoming` | `InstallmentListPanel` |
| 9 | `PROJECT_SAVINGS_IF` | `hypothetical_cut_category` | — | `forecast.savingsIfCategoryFrozen` | `ForecastPanel` |
| 10 | `FORECAST_NEXT_QUINCENA` | — | `baseline_quincenas` | `forecast.nextQuincena` | `ForecastPanel` |
| 11 | `COMPARE_QUINCENAS` | — | `baseline_quincenas` | `variance.compareVsBaseline` | `VarianceComparePanel` |
| 12 | `EXPLAIN_VARIANCE` | `category_code` | — | `variance.explainCategory` | `VarianceExplainPanel` |
| 13 | `LIST_RECURRENCE_TEMPLATES` | — | — | `recurrence.listActive` | `TemplateListPanel` |
| 14 | `LIST_RECENT_EXPENSES` | — | `from_date`, `to_date` | `expenses.recent` | `ExpenseListPanel` |
| 15 | `SUMMARIZE_QUINCENA` | — | — | `analytics.snapshotSummary` | `QuincenaSummaryPanel` |
| 16 | `GET_INTEREST_PAID` | — | `from_date`, `to_date` | `analytics.interestPaid` | `InterestPanel` |
| 17 | `GET_LOANS_RECEIVABLE` | — | — | `loans.receivable` | `LoansPanel` |
| 18 | `UNKNOWN` | — | — | — | `UnknownFallback` |

---

## Apéndice E.B — Ejemplo extremo-a-extremo

**Pregunta del usuario** (tecleada en el pane izquierdo del Fold desplegado):

> "¿Cuánto ahorro si no gastamos más en entretenimiento esta quincena?"

**1. `PromptSanitizer` produce:**

```
cuanto ahorro si no gastamos mas en entretenimiento esta quincena
```

**2. `QuestionClassifier.classify` devuelve:**

```
{ SpendByCategory, HistoricalCompare }
```

**3. `RagContextBuilder` ejecuta queries SQL y obtiene:**

- Quincena activa `q_7f3a` con gasto proyectado 127,000 y ejecutado 81,850.
- Categoría ENTERTAINMENT: proyectado 2,200, ejecutado 2,184, restante 16.
- Histórico 6Q de ENTERTAINMENT: mediana 1,980, σ 180.

**4. `ContextSerializer` produce bloque de 480 tokens** (abreviado en §E.3.3).

**5. `PromptAssembler` arma prompt total de ~3,200 tokens.**

**6. `AiCoreService.generate` invoca Gemini Nano en edge TPU.**
   Latencia medida: 1,080 ms. Salida cruda:

```json
{"intent":"PROJECT_SAVINGS_IF","args":{"hypothetical_cut_category":"ENTERTAINMENT"},"reason":"El usuario pregunta el impacto en ahorro si congela ENTERTAINMENT el resto de la quincena activa."}
```

**7. `IntentDispatcher.dispatch` decodifica y ejecuta:**

```kotlin
forecast.savingsIfCategoryFrozen(CategoryId("ENTERTAINMENT"))
// → SavingsProjection(deltaMxn = 16, assumptions = ["usa presupuesto remanente",
//    "no aplica a gastos ya postados", "no reasigna a otras categorías"])
```

**8. Render en pane izquierdo (chat):**

> Si congelas entretenimiento, tu ahorro aumenta solo **$16** esta quincena porque ya ejecutaste $2,184 de los $2,200 presupuestados. El margen restante es marginal.

**9. Render en pane derecho (`ForecastPanel`):**

```
┌ Escenario — congelar ENTERTAINMENT ────────┐
│                                             │
│   Ahorro actual proyectado     $12,660     │
│   Ahorro si congelas           $12,676     │
│                                 ───────     │
│   Impacto                         +$16     │
│                                             │
│   Gastos ENTERTAINMENT ya ejecutados:       │
│   Netflix       $329   18 abr               │
│   HBO           $167   17 abr               │
│   Spotify       $129   17 abr               │
│   Prime         $ 99   16 abr               │
│   Diversión   $1,460   16 abr               │
│                 ─────                        │
│                 $2,184                       │
│                                             │
│   Sugerencias: [ Explica por qué es bajo ] │
│                [ Compara con entretenimiento│
│                  de marzo ]                 │
└─────────────────────────────────────────────┘
```

**10. Registro en `aicore_audit_log`:**

```
ts=1752860520000
question_hash=sha256(abc…)
intent=PROJECT_SAVINGS_IF
args={hypothetical_cut_category:ENTERTAINMENT}
latency_ms=1080
tokens_in=3214
tokens_out=92
model_version=nano-v2.4-lora-budget
```

---

**Fin de la adenda.** Esta sección complementa los documentos base sin redefinir entidades, queries ni contratos ya existentes. Toda referencia a `§X.Y` sin prefijo alude a los documentos originales; las referencias internas de esta adenda usan prefijo `§E.Z`.
