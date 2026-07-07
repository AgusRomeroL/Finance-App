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
    suspend fun analyze(statementText: String): Result = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider()
        if (apiKey.isBlank()) {
            return@withContext Result.Failure(
                "Falta la API key de NVIDIA. Pégala en Perfil → Importar estado de cuenta."
            )
        }

        val bodyJson = buildRequestBody(statementText)
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
    private fun buildRequestBody(statementText: String): String {
        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
            put(JSONObject().put("role", "user").put("content", userPrompt(statementText)))
        }
        return JSONObject().apply {
            put("model", MODEL)
            put("messages", messages)
            put("temperature", 0.1)
            put("max_tokens", 4096)
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

    private fun userPrompt(statementText: String): String =
        "Analiza el siguiente estado de cuenta y devuelve SOLO el JSON del esquema.\n\n" +
            "===== ESTADO DE CUENTA =====\n$statementText\n===== FIN ====="

    companion object {
        const val ENDPOINT = "https://integrate.api.nvidia.com/v1/chat/completions"
        const val MODEL = "google/diffusiongemma-26b-a4b-it"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        /**
         * System prompt: exige SOLO JSON con el esquema estricto de C1. El modelo
         * es de texto, así que se le pasa el texto ya extraído del PDF/imagen.
         */
        val SYSTEM_PROMPT = """
            Eres un extractor de datos de estados de cuenta bancarios mexicanos (MXN).
            Recibes el TEXTO PLANO de un estado de cuenta. Devuelve EXCLUSIVAMENTE un
            objeto JSON válido, sin texto adicional, sin markdown, sin explicaciones.

            Esquema EXACTO (usa null si un dato no aparece; no inventes):
            {
              "emisor": string|null,            // banco emisor, ej. "Citibanamex"
              "last4": string|null,             // últimos 4 dígitos de la tarjeta
              "periodo": { "inicio": "YYYY-MM-DD"|null, "fin": "YYYY-MM-DD"|null },
              "fechaCorte": "YYYY-MM-DD"|null,
              "fechaLimitePago": "YYYY-MM-DD"|null,
              "saldoTotal": number|null,        // saldo al corte, MXN
              "pagoMinimo": number|null,
              "pagoNoIntereses": number|null,   // pago para no generar intereses
              "tasaAnual": number|null,         // % anual (CAT o tasa de interés)
              "movimientos": [
                {
                  "fecha": "YYYY-MM-DD"|null,
                  "concepto": string,
                  "monto": number,              // cargo en MXN, positivo
                  "esMsi": boolean,             // true si es Meses Sin Intereses / a cuotas
                  "msiPlazo": number|null,      // total de mensualidades del plan
                  "msiNumero": number|null      // número de mensualidad actual (ej. 3 de 12)
                }
              ]
            }

            Reglas:
            - Todas las fechas en formato ISO YYYY-MM-DD. Infiere el año del periodo.
            - Montos como número decimal sin símbolo ni comas de miles (1234.56).
            - Detecta MSI/mensualidades: "3/12", "3 de 12", "MSI", "Meses sin intereses",
              "a X meses". Rellena esMsi=true, msiPlazo y msiNumero cuando puedas.
            - No incluyas pagos/abonos ni intereses como movimientos de cargo.
            - Si el texto no es un estado de cuenta, devuelve el objeto con todos los
              campos en null y "movimientos": [].
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
