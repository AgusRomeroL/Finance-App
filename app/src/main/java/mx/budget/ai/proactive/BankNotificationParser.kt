package mx.budget.ai.proactive

import org.json.JSONObject

/**
 * Cargo extraído de una notificación bancaria (Feature D, §F.6.2).
 *
 * @param bankId        id interno del banco en `bank_templates.json`.
 * @param bankName      nombre legible ("BBVA").
 * @param bankPackage   package de la app que emitió la notificación.
 * @param amountMxn     monto positivo en MXN.
 * @param merchant      comercio crudo extraído ("OXXO", "AMAZON MX").
 * @param last4         últimos 4 dígitos de la tarjeta, si se detectaron (hint de wallet).
 * @param occurredAt    epoch millis (post time de la notificación).
 */
data class ParsedCharge(
    val bankId: String,
    val bankName: String,
    val bankPackage: String,
    val amountMxn: Double,
    val merchant: String,
    val last4: String?,
    val occurredAt: Long,
)

/** Patrones de extracción (heredables del bloque `default`). */
data class BankPatterns(
    val ignoreIfContains: List<String>,
    val amount: List<Regex>,
    val merchant: List<Regex>,
    val last4: List<Regex>,
)

/** Config de un banco de la allowlist. */
data class BankTemplate(
    val id: String,
    val name: String,
    val packages: List<String>,
    val issuerMatch: List<String>,
    val patterns: BankPatterns,
)

/**
 * Allowlist + plantillas, parseadas de `assets/ai/bank_templates.json`.
 * Indexa por package para resolución O(1) en el hot path del listener.
 */
class BankTemplates(val banks: List<BankTemplate>) {

    private val byPackage: Map<String, BankTemplate> =
        banks.flatMap { b -> b.packages.map { it to b } }.toMap()

    fun isAllowed(packageName: String): Boolean = packageName in byPackage

    fun forPackage(packageName: String): BankTemplate? = byPackage[packageName]

    companion object {
        private val IGNORE_CASE = setOf(RegexOption.IGNORE_CASE)

        /** Parsea el asset JSON. Lanza si el JSON está corrupto (es un asset versionado). */
        fun fromJson(json: String): BankTemplates {
            val root = JSONObject(json)
            val default = parsePatterns(root.getJSONObject("default"), null)
            val banksArr = root.getJSONArray("banks")
            val banks = ArrayList<BankTemplate>(banksArr.length())
            for (i in 0 until banksArr.length()) {
                val o = banksArr.getJSONObject(i)
                val patterns = if (o.has("patterns")) parsePatterns(o.getJSONObject("patterns"), default) else default
                banks.add(
                    BankTemplate(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        packages = o.getJSONArray("packages").toStringList(),
                        issuerMatch = o.optJSONArray("issuerMatch")?.toStringList() ?: emptyList(),
                        patterns = patterns,
                    )
                )
            }
            return BankTemplates(banks)
        }

        private fun parsePatterns(o: JSONObject, fallback: BankPatterns?): BankPatterns {
            fun regexes(key: String): List<Regex>? =
                o.optJSONArray(key)?.toStringList()?.map { Regex(it, IGNORE_CASE) }
            return BankPatterns(
                ignoreIfContains = o.optJSONArray("ignoreIfContains")?.toStringList()
                    ?: fallback?.ignoreIfContains ?: emptyList(),
                amount = regexes("amount") ?: fallback?.amount ?: emptyList(),
                merchant = regexes("merchant") ?: fallback?.merchant ?: emptyList(),
                last4 = regexes("last4") ?: fallback?.last4 ?: emptyList(),
            )
        }

        private fun org.json.JSONArray.toStringList(): List<String> =
            (0 until length()).map { getString(it) }
    }
}

/**
 * Parser determinista (100% on-device, sin red ni ML) de notificaciones bancarias.
 *
 * Dado el package + título + texto de una notificación, decide si es un **cargo**
 * de un banco de la allowlist y extrae monto/comercio/last4. Devuelve `null` para
 * cualquier package no permitido, para notificaciones que no parecen un cargo
 * (saldo, OTP, depósito recibido, etc.) o cuando no logra extraer un monto válido.
 *
 * Es la primera etapa del pipeline §F.6.2; lo consume [BankNotificationListenerService].
 */
class BankNotificationParser(private val templates: BankTemplates) {

    fun isAllowed(packageName: String): Boolean = templates.isAllowed(packageName)

    fun parse(packageName: String, title: String?, text: String?, occurredAt: Long): ParsedCharge? {
        val bank = templates.forPackage(packageName) ?: return null
        val body = listOfNotNull(title, text).joinToString(" ").trim()
        if (body.isBlank()) return null

        val lower = body.lowercase()
        // Descarta lo que claramente no es un cargo (ingreso, aviso, código…).
        if (bank.patterns.ignoreIfContains.any { it in lower }) return null

        val amount = extractAmount(body, bank.patterns.amount) ?: return null
        if (amount <= 0.0) return null

        val merchant = extractMerchant(body, bank.patterns.merchant) ?: "Cargo ${bank.name}"
        val last4 = extractFirst(body, bank.patterns.last4)

        return ParsedCharge(
            bankId = bank.id,
            bankName = bank.name,
            bankPackage = packageName,
            amountMxn = amount,
            merchant = merchant,
            last4 = last4,
            occurredAt = occurredAt,
        )
    }

    /** Primer patrón que produzca un número MXN plausible. Formato MX: $1,234.56. */
    private fun extractAmount(body: String, patterns: List<Regex>): Double? {
        for (re in patterns) {
            val raw = re.find(body)?.groupValues?.getOrNull(1) ?: continue
            val normalized = normalizeAmount(raw) ?: continue
            if (normalized > 0.0) return normalized
        }
        return null
    }

    /** "1,234.56" → 1234.56 ; "1.234,56" → 1234.56 (tolerante a ambos separadores). */
    private fun normalizeAmount(raw: String): Double? {
        var s = raw.trim().trimEnd('.', ',')
        if (s.isEmpty()) return null
        val lastDot = s.lastIndexOf('.')
        val lastComma = s.lastIndexOf(',')
        // El separador decimal es el que aparece más a la derecha.
        s = when {
            lastDot >= 0 && lastComma >= 0 ->
                if (lastDot > lastComma) s.replace(",", "")
                else s.replace(".", "").replace(',', '.')
            lastComma >= 0 -> // sólo comas: decimal si hay exactamente 2 dígitos tras la última
                if (s.length - lastComma - 1 == 2) s.replace(',', '.') else s.replace(",", "")
            else -> s // sólo puntos (o ninguno): el punto ya es decimal
        }
        return s.toDoubleOrNull()
    }

    private fun extractMerchant(body: String, patterns: List<Regex>): String? {
        val raw = extractFirst(body, patterns) ?: return null
        val cleaned = raw.trim().trim('.', ',', ';', ' ').replace(Regex("\\s+"), " ")
        return cleaned.takeIf { it.length in 2..40 }
    }

    private fun extractFirst(body: String, patterns: List<Regex>): String? {
        for (re in patterns) {
            val g = re.find(body)?.groupValues?.getOrNull(1)
            if (!g.isNullOrBlank()) return g
        }
        return null
    }
}
