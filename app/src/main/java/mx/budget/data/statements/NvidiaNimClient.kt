package mx.budget.data.statements

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import mx.budget.ai.dispatch.JsonRepairer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Cliente del endpoint OpenAI-compatible de **NVIDIA NIM** (Fase C, paquete C1).
 *
 * Envía el TEXTO ya extraído localmente (nunca el PDF/imagen crudo) y pide al
 * modelo que devuelva SOLO un JSON con el esquema de [ParsedStatement].
 *
 * GOTCHA de modelo: `google/diffusiongemma-26b-a4b-it` es un modelo **de texto**
 * (no multimodal / no visión). Por eso la extracción de texto se hace on-device
 * ANTES (ver [StatementTextExtractor]) y aquí solo viaja texto plano.
 *
 * La API key se lee del DataStore justo antes de llamar (nunca hardcodeada ni
 * logueada). Errores de red/HTTP/sin-key se devuelven como [Result.Failure] con
 * un mensaje claro; jamás lanza.
 */
class NvidiaNimClient(
    private val apiKeyProvider: suspend () -> String,
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    sealed interface Result {
        data class Success(val statement: ParsedStatement, val rawJson: String) : Result
        data class Failure(val message: String) : Result
    }

    /**
     * Analiza el [statementText] con el modelo. Corre en IO. Devuelve el
     * [ParsedStatement] + el JSON crudo (para auditoría), o un [Result.Failure].
     */
    suspend fun analyze(
        statementText: String,
        context: StatementLlmContext? = null,
        kind: DocumentKind = DocumentKind.BANK_STATEMENT,
    ): Result = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider()
        if (apiKey.isBlank()) {
            return@withContext Result.Failure(
                "Falta la API key de NVIDIA. Pégala en Perfil → Importar estado de cuenta."
            )
        }

        val bodyJson = buildRequestBody(statementText, context, kind)
        val request = Request.Builder()
            .url(ENDPOINT)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .post(bodyJson.toRequestBody(JSON_MEDIA))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.Failure(httpErrorMessage(response.code, responseText))
                }
                val content = extractContent(responseText)
                    ?: return@withContext Result.Failure("El modelo no devolvió contenido.")
                parseStatement(content)
            }
        } catch (e: java.net.UnknownHostException) {
            Result.Failure("Sin conexión a internet. Conéctate e inténtalo de nuevo.")
        } catch (e: java.net.SocketTimeoutException) {
            Result.Failure("El servidor tardó demasiado en responder. Inténtalo de nuevo.")
        } catch (e: Exception) {
            Result.Failure("Error al contactar el servidor: ${e.message ?: "desconocido"}")
        }
    }

    // ── Fase 5: pre-match movimientos ↔ gastos existentes ───────────────────

    /** Gasto candidato compacto que viaja al modelo (id, fecha, monto, concepto). */
    data class CandidateExpense(
        val id: String,
        val fecha: String,
        val monto: Double,
        val concepto: String,
    )

    /** Vínculo propuesto por el modelo para un movimiento (índice 0-based). */
    data class PrematchItem(
        val movimiento: Int,
        val expenseId: String?,
        val confianza: Double,
    )

    sealed interface PrematchResult {
        data class Success(val items: List<PrematchItem>) : PrematchResult
        data class Failure(val message: String) : PrematchResult
    }

    /**
     * Pide al modelo emparejar movimientos del estado con gastos YA registrados.
     * La respuesta es solo una PROPUESTA: el llamador la valida contra las
     * reglas duras de [StatementMatcher.validate] antes de aceptar nada
     * (el LLM propone, la regla dispone). Cualquier fallo → Failure y el
     * flujo sigue con el match local puro.
     */
    suspend fun prematch(
        movements: List<StatementMovement>,
        candidates: List<CandidateExpense>,
    ): PrematchResult = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider()
        if (apiKey.isBlank()) return@withContext PrematchResult.Failure("Sin API key.")
        if (movements.isEmpty() || candidates.isEmpty()) {
            return@withContext PrematchResult.Success(emptyList())
        }

        val body = JSONObject().apply {
            put("model", MODEL)
            put(
                "messages",
                JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", PREMATCH_PROMPT))
                    put(JSONObject().put("role", "user").put("content", prematchUserPrompt(movements, candidates)))
                }
            )
            put("temperature", 0.0)
            put("max_tokens", 2048)
        }.toString()

        val request = Request.Builder()
            .url(ENDPOINT)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .post(body.toRequestBody(JSON_MEDIA))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext PrematchResult.Failure(httpErrorMessage(response.code, responseText))
                }
                val content = extractContent(responseText)
                    ?: return@withContext PrematchResult.Failure("El modelo no devolvió contenido.")
                parsePrematch(content)
            }
        } catch (e: Exception) {
            PrematchResult.Failure("Error de red en el pre-match: ${e.message ?: "desconocido"}")
        }
    }

    private fun prematchUserPrompt(
        movements: List<StatementMovement>,
        candidates: List<CandidateExpense>,
    ): String = buildString {
        appendLine("MOVIMIENTOS DEL ESTADO (indice | fecha | monto | concepto):")
        movements.forEachIndexed { i, m ->
            appendLine("$i | ${m.fecha ?: "?"} | ${m.monto ?: 0.0} | ${m.concepto.orEmpty().take(60)}")
        }
        appendLine()
        appendLine("GASTOS YA REGISTRADOS (id | fecha | monto | concepto):")
        candidates.forEach { c ->
            appendLine("${c.id} | ${c.fecha} | ${c.monto} | ${c.concepto.take(60)}")
        }
    }

    /** Parsea la respuesta del pre-match con la misma reparación tolerante. */
    private fun parsePrematch(content: String): PrematchResult = runCatching {
        val repaired = JsonRepairer.repair(content)
        val root = JSONObject(repaired)
        val arr = root.optJSONArray("matches") ?: JSONArray()
        val items = buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val mov = if (o.has("movimiento")) o.optInt("movimiento", -1) else -1
                if (mov < 0) continue
                val expenseId = o.optString("expenseId", "").ifBlank { null }
                add(PrematchItem(mov, expenseId, o.optDouble("confianza", 0.0)))
            }
        }
        PrematchResult.Success(items)
    }.getOrElse { PrematchResult.Failure("Respuesta de pre-match no interpretable.") }

    /** Arma el cuerpo del chat/completions (OpenAI-compatible) con org.json. */
    private fun buildRequestBody(
        statementText: String,
        context: StatementLlmContext?,
        kind: DocumentKind,
    ): String {
        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPromptFor(kind)))
            put(JSONObject().put("role", "user").put("content", userPrompt(statementText, context, kind)))
        }
        return JSONObject().apply {
            put("model", MODEL)
            put("messages", messages)
            put("temperature", 0.1)
            // 8192 (antes 4096): los estados de cuenta largos (cuentas de débito con
            // 15-20 movimientos) truncaban la salida y perdían movimientos.
            put("max_tokens", 8192)
        }.toString()
    }

    /** Saca `choices[0].message.content` de la respuesta OpenAI-compatible. */
    private fun extractContent(responseText: String): String? = runCatching {
        JSONObject(responseText)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }.getOrNull()

    /** Parsea (con reparación tolerante) el JSON del modelo a [ParsedStatement]. */
    private fun parseStatement(content: String): Result {
        val repaired = JsonRepairer.repair(content)
        return runCatching {
            val parsed = json.decodeFromString(ParsedStatement.serializer(), repaired)
            Result.Success(parsed, repaired)
        }.getOrElse {
            Result.Failure("El modelo devolvió un formato que no se pudo interpretar.")
        }
    }

    private fun httpErrorMessage(code: Int, body: String): String = when (code) {
        401, 403 -> "API key inválida o sin permisos. Revísala en Perfil."
        404 -> "Modelo no disponible en tu cuenta de NVIDIA ($MODEL)."
        429 -> "Límite de peticiones alcanzado. Espera un momento e inténtalo de nuevo."
        in 500..599 -> "El servidor de NVIDIA tuvo un problema (HTTP $code). Inténtalo más tarde."
        else -> "Error del servidor (HTTP $code)."
    }

    private fun userPrompt(
        statementText: String,
        context: StatementLlmContext?,
        kind: DocumentKind,
    ): String {
        val household = context?.let {
            val miembros = it.miembros.joinToString(", ")
            val categorias = it.categorias.joinToString("; ")
            "\nContexto del hogar (para categoriaSugerida y beneficiariosSugeridos):\n" +
                "MIEMBROS (usa estos nombres exactos): $miembros\n" +
                "CATEGORÍAS (código — nombre; usa el CÓDIGO exacto): $categorias\n"
        }.orEmpty()
        val (accion, marca) = when (kind) {
            DocumentKind.BANK_STATEMENT -> "el siguiente estado de cuenta" to "ESTADO DE CUENTA"
            DocumentKind.PURCHASE_HISTORY -> "el siguiente historial de compras" to "HISTORIAL DE COMPRAS"
            DocumentKind.WALLET_MOVEMENTS -> "los siguientes movimientos de dinero" to "MOVIMIENTOS DE DINERO"
            DocumentKind.INVOICE_CFDI -> "las siguientes facturas (CFDI)" to "FACTURAS CFDI"
        }
        return "Analiza $accion y devuelve SOLO el JSON del esquema.\n" +
            household +
            "\n===== $marca =====\n$statementText\n===== FIN ====="
    }

    companion object {
        const val ENDPOINT = "https://integrate.api.nvidia.com/v1/chat/completions"
        const val MODEL = "google/diffusiongemma-26b-a4b-it"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        /** Selecciona el system prompt según el tipo de documento. */
        fun systemPromptFor(kind: DocumentKind): String = when (kind) {
            DocumentKind.BANK_STATEMENT -> STATEMENT_PROMPT
            DocumentKind.PURCHASE_HISTORY -> PURCHASE_PROMPT
            DocumentKind.WALLET_MOVEMENTS -> WALLET_PROMPT
            DocumentKind.INVOICE_CFDI -> CFDI_PROMPT
        }

        /** Alias histórico (= prompt de estado de cuenta). Ver [systemPromptFor]. */
        val SYSTEM_PROMPT: String get() = STATEMENT_PROMPT

        /** Esquema JSON común a todos los tipos (se reutiliza [ParsedStatement]). */
        private const val SCHEMA = """
            Esquema EXACTO (usa null si un dato no aparece; NO inventes):
            {
              "emisor": string|null,            // emisor/tienda, ej. "Citibanamex", "Amazon"
              "last4": string|null,             // últimos 4 dígitos de la tarjeta/cuenta
              "periodo": { "inicio": "YYYY-MM-DD"|null, "fin": "YYYY-MM-DD"|null },
              "fechaCorte": "YYYY-MM-DD"|null,
              "fechaLimitePago": "YYYY-MM-DD"|null,
              "saldoTotal": number|null,
              "pagoMinimo": number|null,
              "pagoNoIntereses": number|null,
              "tasaAnual": number|null,
              "movimientos": [
                {
                  "fecha": "YYYY-MM-DD"|null,
                  "concepto": string,
                  "monto": number,              // SIEMPRE positivo, con centavos
                  "tipo": "COMPRA"|"CARGO"|"INTERES"|"IVA"|"COMISION"|"PAGO"|"ABONO"|"OTRO",
                  "esMsi": boolean,
                  "msiPlazo": number|null,      // total de mensualidades del plan
                  "msiNumero": number|null,     // mensualidad actual (ej. 3 de 12)
                  "categoriaSugerida": string|null,
                  "beneficiariosSugeridos": [string]|null
                }
              ]
            }
        """

        /** Reglas de atribución del hogar, comunes a todos los tipos. */
        private const val HOUSEHOLD_RULES = """
            - CATEGORÍA y BENEFICIARIOS: solo si abajo se te da la lista del hogar.
              Sugiere "categoriaSugerida" (CÓDIGO EXACTO de la lista) y
              "beneficiariosSugeridos" (nombres EXACTOS) según el PRODUCTO/comercio.
              Si dudas, usa null y []. No lo apliques a INTERES/IVA/COMISION/PAGO/ABONO.
            - SOFTWARE/DEV (GitHub, Microsoft, Adobe, Coursera, dominios/hosting) lo
              paga y usa NORMA: beneficiariosSugeridos ["Norma"], NUNCA un hijo.
            - Ropa/exámenes universitarios (CENEVAL) → el hijo estudiante; líneas de
              teléfono del plan familiar → sus dueños; suscripciones compartidas
              (Netflix, HBO, Prime) → todos.
        """

        /** Estado de cuenta bancario / tarjeta de crédito (flujo C1, mejorado). */
        val STATEMENT_PROMPT = """
            Eres un extractor de datos de estados de cuenta bancarios mexicanos (MXN).
            Recibes el TEXTO PLANO de un estado de cuenta (puede venir de OCR, con
            ruido). Devuelve EXCLUSIVAMENTE un objeto JSON válido, sin texto adicional,
            sin markdown, sin explicaciones.

            $SCHEMA

            REGLAS CRÍTICAS:
            1. MONTOS: cópialos TAL CUAL, con sus centavos. Quita solo "${'$'}" y las
               comas de millares. "159.73" es 159.73, NUNCA 1597.3. "1,234.56" es
               1234.56. Jamás muevas el punto decimal ni multipliques.
            2. EXTRAE TODOS los movimientos del detalle, de principio a fin, sin omitir
               ni resumir. Si el estado tiene 20 movimientos, devuelve los 20.
            3. TIPO de cada movimiento:
               - COMPRA/CARGO = consumo o cargo. INTERES = intereses ordinarios.
                 IVA = el I.V.A. de intereses/comisiones (NO lo mezcles con COMPRA).
                 COMISION = comisiones/cuotas de manejo.
               - PAGO/ABONO = dinero a favor (pagos a la tarjeta, abonos, devoluciones,
                 depósitos, nómina, cashback). OTRO = traspasos/movimientos que no encajan.
               Señales de abono: "Su Pago", "Gracias", "Abono", "Devolución",
               "Depósito", "Crédito por Aclaración"; signo "-" antepuesto o guion
               pospuesto ("244.00-"); columna de abonos.
            4. SIGNO: en "monto" pon SIEMPRE el valor absoluto (positivo). La dirección
               la da "tipo", no el signo.
            5. FECHAS: ISO YYYY-MM-DD, infiriendo el año del periodo. Si una fecha está
               corrupta o ilegible (ruido de OCR tipo "Nnf-60"), usa null; nunca copies
               texto que no sea una fecha.
            6. MSI: detecta "3/12", "3 de 12", "N DE M", "MSI", "Meses sin intereses",
               "N MENS", "PP#####", "en N Cuotas (n/N)". esMsi=true y rellena msiPlazo
               (total) y msiNumero (actual) cuando el texto los dé. Si dice "Amazon A
               Meses" SIN número/plazo, esMsi=true con msiPlazo=null y msiNumero=null.
            7. DIFERIMIENTOS (DiDi "Diferimientos especiales"): es un traspaso de deuda
               existente, NO una compra nueva. Sepáralo en Capital (tipo CARGO,
               categoriaSugerida "LOANS.DIDI"), Interés (tipo INTERES) e IVA (tipo IVA).
            8. CUENTAS DE DÉBITO / WALLETS (BBVA, Banamex MiCuenta, BanCoppel, Mercado
               Pago): retiros/compras/SPEI enviados = CARGO/COMPRA; depósitos/nómina =
               ABONO. No hay MSI ni pago mínimo (usa null en esos).
            $HOUSEHOLD_RULES
            9. Si el texto no es un estado de cuenta, devuelve todos los campos null y
               "movimientos": [].
        """.trimIndent()

        /** Historial de compras (Amazon "Mis pedidos", Mercado Libre "Tus compras"). */
        val PURCHASE_PROMPT = """
            Eres un extractor de HISTORIALES DE COMPRA (Amazon, Mercado Libre) de un
            hogar mexicano (MXN). Recibes filas ya parseadas de un CSV/JSON de pedidos.
            Devuelve EXCLUSIVAMENTE un objeto JSON válido con el mismo esquema, donde
            cada "movimiento" es UNA COMPRA (un producto). Sin markdown ni explicaciones.

            $SCHEMA

            REGLAS:
            1. Una fila = un movimiento. "concepto" = NOMBRE DEL PRODUCTO (limpio, sin
               códigos ASIN). "monto" = precio total pagado por esa línea (positivo, con
               centavos; respeta cantidad × precio unitario si vienen separados).
            2. "fecha" = fecha del pedido (ISO YYYY-MM-DD).
            3. "tipo" = COMPRA. Devoluciones/reembolsos = ABONO. Cargos de envío o
               suscripción (Prime) = COMPRA.
            4. MSI: si la compra fue "a meses"/"MSI", esMsi=true (msiPlazo/msiNumero si
               se indican, si no null).
            5. emisor = "Amazon" o "Mercado Libre"; last4/periodo/saldo/tasa/pagos = null.
            6. El NOMBRE DEL PRODUCTO es tu MEJOR señal de categoría y beneficiario:
               úsalo (p. ej. crema facial → cuidado personal del hijo destinatario;
               herramienta dev → software/Norma; juguete/útil escolar → el hijo).
               Si el pedido trae DESTINATARIO/dirección de un miembro, ése es el
               beneficiario.
            $HOUSEHOLD_RULES
            7. Si el texto no es un historial de compras, devuelve campos null y
               "movimientos": [].
        """.trimIndent()

        /** Movimientos de dinero de una wallet (Mercado Pago "Movimientos"). */
        val WALLET_PROMPT = """
            Eres un extractor de MOVIMIENTOS DE DINERO de una billetera digital mexicana
            (Mercado Pago). Recibes filas parseadas de un CSV de movimientos. Devuelve
            EXCLUSIVAMENTE un objeto JSON del mismo esquema; cada "movimiento" es una
            transacción. Sin markdown ni explicaciones.

            $SCHEMA

            REGLAS:
            1. "monto" positivo siempre; la dirección la da "tipo".
            2. TIPO: pagos/compras/retiros/transferencias enviadas = COMPRA o CARGO;
               depósitos/ingresos/cobros recibidos/cashback = ABONO; disposición de
               crédito o préstamo ("Acreditación Préstamo", línea de crédito) = OTRO
               (es deuda, no gasto).
            3. "concepto" = descripción del comercio/contraparte. "fecha" ISO.
            4. esMsi/msiPlazo/msiNumero = null (las wallets no traen MSI por línea).
            5. emisor = "Mercado Pago"; periodo según el rango del reporte; saldoTotal =
               saldo final si aparece.
            $HOUSEHOLD_RULES
            6. Si el texto no son movimientos de dinero, devuelve campos null y
               "movimientos": [].
        """.trimIndent()

        /** Facturas CFDI (SAT descarga masiva) — texto/valores de los XML. */
        val CFDI_PROMPT = """
            Eres un extractor de FACTURAS CFDI mexicanas (SAT). Recibes el texto/valores
            de uno o más comprobantes. Devuelve EXCLUSIVAMENTE un objeto JSON del mismo
            esquema; cada CONCEPTO facturado es un "movimiento". Sin markdown.

            $SCHEMA

            REGLAS:
            1. Un concepto de la factura = un movimiento. "concepto" = descripción del
               bien/servicio. "monto" = importe del concepto (positivo, con centavos).
            2. "fecha" = fecha de emisión (ISO). emisor = razón social del emisor.
               El IVA del comprobante = un movimiento tipo IVA.
            3. tipo: bienes/servicios recibidos = COMPRA; si es una factura EMITIDA por
               el hogar (ingreso) = ABONO.
            4. esMsi = false salvo que el concepto lo indique.
            $HOUSEHOLD_RULES
            5. Si el texto no es una factura, devuelve campos null y "movimientos": [].
        """.trimIndent()

        /**
         * Prompt del pre-match (Fase 5): el modelo solo PROPONE vínculos; la
         * validación dura (monto ±1 %, fecha ±3 días) es local y determinista.
         */
        val PREMATCH_PROMPT = """
            Eres un conciliador bancario. Recibes (a) movimientos de un estado de
            cuenta y (b) gastos ya registrados en una app de presupuesto. Empareja
            cada movimiento con el gasto que corresponda al MISMO cargo real, si
            existe. Un gasto solo puede usarse una vez. NO inventes ids.

            Criterios: mismo monto (o casi), fecha cercana (el registro manual
            puede diferir 1-3 dias del posteo bancario), concepto compatible
            (el estado abrevia: "OXXO MTY NTE" = "Oxxo").

            Devuelve EXCLUSIVAMENTE JSON valido, sin markdown:
            {"matches":[{"movimiento": <indice>, "expenseId": <id del gasto o null>, "confianza": <0..1>}]}
            Incluye SOLO los movimientos donde encontraste pareja (omite el resto).
        """.trimIndent()
    }
}

/**
 * Tipo de documento financiero que la app puede ingerir. Selecciona el prompt/
 * esquema de NVIDIA y el modo de extracción local. El esquema de salida es el
 * mismo ([ParsedStatement]) para todos: cada "movimiento" es un cargo, compra o
 * transacción, de modo que el flujo de materialización (gasto + atribución) se
 * reutiliza tal cual.
 */
enum class DocumentKind(val displayName: String) {
    BANK_STATEMENT("Estado de cuenta"),
    PURCHASE_HISTORY("Historial de compras (Amazon / Mercado Libre)"),
    WALLET_MOVEMENTS("Movimientos de dinero (Mercado Pago)"),
    INVOICE_CFDI("Facturas CFDI (SAT)"),
}

/**
 * Contexto opcional del hogar que se inyecta al prompt para que el modelo sugiera
 * `categoriaSugerida` y `beneficiariosSugeridos` por movimiento. Se arma desde los
 * repos (miembros beneficiarios + categorías hoja del catálogo).
 */
data class StatementLlmContext(
    val miembros: List<String>,
    /** Cada entrada en formato "CODIGO — Nombre" (ej. "FOOD.DESPENSA — Despensa"). */
    val categorias: List<String>,
)
