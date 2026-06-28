package mx.budget.ai.proactive

import mx.budget.ai.dispatch.JsonRepairer
import mx.budget.ai.service.AiCoreManager
import mx.budget.data.local.entity.QuincenaEntity
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Capa 3 (§F.8): "razonamiento" proactivo con Gemini Nano on-device.
 *
 * **No genera gastos.** Re-ordena y reexplica los candidatos que el motor SQL
 * determinista de Feature C ([ProactiveSuggestionEngine]) ya extrajo del
 * historial canonicalizado. El LLM solo escoge/prioriza entre **claves canónicas
 * que ya existen** y reescribe el "porqué" en lenguaje natural; cualquier clave
 * que invente se descarta. Así el LLM no puede alucinar gastos: el universo de
 * salida está acotado por SQL.
 *
 * **Fallback obligatorio (§F.8.2):** si AICore no está disponible (emulador,
 * chip sin Tensor, cuota agotada) o el JSON es inválido/vacío, devuelve INTACTO
 * el ranking SQL recibido. La Capa 3 es un *enhancement*, nunca un requisito —
 * la app se comporta exactamente como Feature C cuando el LLM no está.
 *
 * **Foreground-only:** AICore bloquea inferencia en background
 * (`BACKGROUND_USE_BLOCKED`), por eso esto se invoca al abrir el dashboard, no
 * desde un worker. El pre-cómputo de candidatos (SQL) sí podría ir en worker;
 * solo el razonamiento final es foreground.
 *
 * Es puro salvo por [aiCore]: recibe los candidatos y el contexto temporal, así
 * que el armado del prompt, el parseo y la validación de claves son testeables
 * sin hardware (el camino LLM real solo se verifica en un Pixel con Tensor).
 */
class ProactiveReasoner(
    private val aiCore: AiCoreManager,
    private val systemPrompt: String,
    private val zone: ZoneId = ZoneId.of("America/Mexico_City"),
) {

    /**
     * Re-prioriza y reexplica [candidates] con Gemini Nano. Devuelve la misma
     * lista (mismo contenido, posible nuevo orden y `reason` reescrito) o, si el
     * LLM no está disponible o falla, [candidates] sin tocar.
     *
     * Garantías sobre el resultado:
     *  - Solo contiene claves presentes en [candidates] (nunca inventadas).
     *  - Nunca pierde candidatos: los que el LLM omitió se anexan al final con su
     *    `reason` SQL original (cobertura completa para el carrusel del dashboard).
     */
    suspend fun reason(
        candidates: List<ProactiveSuggestion>,
        activeQuincena: QuincenaEntity?,
        nowEpochMs: Long,
    ): List<ProactiveSuggestion> {
        if (candidates.isEmpty()) return candidates

        // Disponibilidad del motor on-device. Cualquier estado != Available
        // (UnsupportedDevice en emulador, TemporaryError por cuota/descarga) cae
        // al ranking SQL. ensureReady es idempotente.
        val ready = runCatching { aiCore.ensureReady() }.getOrNull()
        if (ready != AiCoreManager.Readiness.Available) return candidates

        val prompt = buildPrompt(candidates, activeQuincena, nowEpochMs)
        val raw = runCatching { aiCore.generate(prompt).getOrNull() }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return candidates

        val reordered = parse(raw, candidates)
        return reordered.ifEmpty { candidates }
    }

    // ── Prompt ──────────────────────────────────────────────────────────────────

    private fun buildPrompt(
        candidates: List<ProactiveSuggestion>,
        activeQuincena: QuincenaEntity?,
        nowEpochMs: Long,
    ): String {
        val now = Instant.ofEpochMilli(nowEpochMs).atZone(zone)
        val dow = dayPhrase(now.dayOfWeek)
        val band = bandPhrase(now.hour)
        val quincenaLine = quincenaDayPhrase(activeQuincena, now.toLocalDate())

        val candidatesBlock = candidates.joinToString("\n") { c ->
            "- ${c.canonicalKey} | ${c.concept} | ${c.basis} veces"
        }

        return buildString {
            appendLine(systemPrompt.trim())
            appendLine()
            appendLine("CONTEXTO:")
            appendLine("- Día: $dow")
            appendLine("- Franja: $band")
            appendLine("- Quincena: $quincenaLine")
            appendLine()
            appendLine("CANDIDATOS (clave | concepto | veces):")
            appendLine(candidatesBlock)
        }
    }

    // ── Parseo + validación ───────────────────────────────────────────────────

    /**
     * Convierte la salida cruda del LLM en una lista de [ProactiveSuggestion]
     * acotada a [candidates]. Tolerante a JSON truncado (vía [JsonRepairer]) y a
     * dos formas: objeto `{"ranked":[…]}` o arreglo top-level `[…]`. Devuelve
     * lista vacía si no logra extraer nada parseable (el caller cae al SQL).
     */
    private fun parse(
        raw: String,
        candidates: List<ProactiveSuggestion>,
    ): List<ProactiveSuggestion> {
        val byKey = candidates.associateBy { it.canonicalKey }
        val array = extractRankedArray(raw) ?: return emptyList()

        val out = LinkedHashMap<String, ProactiveSuggestion>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val key = obj.optString("key").trim()
            val base = byKey[key] ?: continue            // descarta claves inventadas
            if (key in out) continue                      // dedup, conserva el 1er orden
            val llmReason = obj.optString("reason").trim()
            out[key] = if (llmReason.isNotEmpty()) base.copy(reason = llmReason) else base
        }

        if (out.isEmpty()) return emptyList()

        // Cobertura: anexa los candidatos que el LLM omitió, en su orden SQL.
        for (c in candidates) out.getOrPut(c.canonicalKey) { c }
        return out.values.toList()
    }

    /** Extrae el arreglo `ranked` aceptando objeto envoltorio o arreglo desnudo. */
    private fun extractRankedArray(raw: String): JSONArray? {
        val repaired = JsonRepairer.repair(raw)
        runCatching { JSONObject(repaired).optJSONArray("ranked") }.getOrNull()?.let { return it }
        // Algunos modelos devuelven el arreglo directo, sin envoltorio.
        val start = raw.indexOf('[')
        if (start >= 0) {
            runCatching { JSONArray(JsonRepairer.repair(raw.substring(start))) }.getOrNull()?.let { return it }
        }
        return null
    }

    // ── Frases de contexto (eco de ProactiveSuggestionEngine para consistencia) ──

    private fun quincenaDayPhrase(quincena: QuincenaEntity?, today: LocalDate): String {
        val start = quincena?.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return "desconocido"
        val dayNumber = ChronoUnit.DAYS.between(start, today) + 1
        return when {
            dayNumber <= 0 -> "aún no inicia"
            dayNumber <= 2 -> "inicio de quincena (día $dayNumber)"
            else -> "día $dayNumber"
        }
    }

    private fun dayPhrase(dow: DayOfWeek): String = when (dow) {
        DayOfWeek.MONDAY -> "lunes"
        DayOfWeek.TUESDAY -> "martes"
        DayOfWeek.WEDNESDAY -> "miércoles"
        DayOfWeek.THURSDAY -> "jueves"
        DayOfWeek.FRIDAY -> "viernes"
        DayOfWeek.SATURDAY -> "sábado"
        DayOfWeek.SUNDAY -> "domingo"
    }

    private fun bandPhrase(hour: Int): String = when (hour) {
        in 5..11 -> "mañana"
        in 12..18 -> "tarde"
        else -> "noche"
    }
}
