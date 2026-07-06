package mx.budget.data.capture

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import mx.budget.R
import mx.budget.ai.dispatch.AliasResolver
import mx.budget.ai.proactive.CaptureContext
import mx.budget.ai.proactive.NameShare
import mx.budget.ai.proactive.NlCaptureExtractor
import mx.budget.ai.proactive.ParsedCharge
import mx.budget.ai.proactive.RetroAttributionEngine
import mx.budget.core.unaccent
import mx.budget.data.local.entity.MemberEntity
import mx.budget.data.local.dao.CategoryDao
import mx.budget.data.local.dao.ExpenseDao
import mx.budget.data.local.dao.MemberDao
import mx.budget.data.local.dao.PaymentMethodDao
import mx.budget.data.local.dao.PendingCaptureDao
import mx.budget.data.local.entity.ExpenseAttributionEntity
import mx.budget.data.local.entity.ExpenseEntity
import mx.budget.data.local.entity.PendingCaptureEntity
import mx.budget.data.location.LocationProvider
import mx.budget.data.location.LocationSource
import mx.budget.data.repository.ExpenseRepository
import mx.budget.data.repository.QuincenaRepository
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

/**
 * Orquestador de la captura desde notificaciones bancarias (Feature D, §F.6).
 *
 * Es el punto único de lógica que comparten las tres superficies:
 *  - el [mx.budget.service.BankNotificationListenerService] llama [ingest] al
 *    detectar un cargo,
 *  - el chip del dashboard y la notificación propia llaman [confirm]/[dismiss].
 *
 * Mantener todo aquí evita duplicar la resolución de wallet/categoría/atribución
 * y la construcción del gasto entre la UI y el `BroadcastReceiver`.
 *
 * Principio rector: **propose-then-confirm**. [ingest] sólo deja una propuesta
 * (`PENDING`); nada toca el ledger hasta que el usuario confirma. La atribución
 * se infiere con el motor de B (Feature A/D comparten `RetroAttributionEngine`)
 * y, si no hay señal, cae a defaults conservadores y editables.
 */
class BankCaptureManager(
    private val context: Context,
    private val pendingDao: PendingCaptureDao,
    private val paymentMethodDao: PaymentMethodDao,
    private val categoryDao: CategoryDao,
    private val expenseDao: ExpenseDao,
    private val memberDao: MemberDao,
    private val quincenaRepository: QuincenaRepository,
    private val expenseRepository: ExpenseRepository,
    private val engine: RetroAttributionEngine,
    private val householdId: String,
    private val nlCaptureExtractor: NlCaptureExtractor,
    private val locationProvider: LocationProvider,
) {

    // ── Ingreso de una propuesta (desde el listener) ────────────────────────────

    /**
     * Convierte un cargo parseado en una fila `PENDING`, resolviendo de una vez el
     * wallet (por `last4`/emisor) y una categoría tentativa (modal por comercio
     * histórico, con fallback). Postea además la notificación propia con acciones.
     * Idempotente por (banco, monto, comercio, día) para no duplicar reintentos.
     */
    suspend fun ingest(parsed: ParsedCharge) {
        val wallets = paymentMethodDao.getActive(householdId)
        val walletId = resolveWallet(parsed, wallets)
        val categoryId = resolveCategory(parsed.merchant)

        // Ubicación al ingresar (§G.4.2): solo bajo nivel PERSISTENT, porque ingest
        // corre en background (NotificationListenerService). Bajo "solo al usar" da
        // null y la ubicación llega al confirmar (foreground). Best-effort.
        val fix = locationProvider.currentFix(requireForeground = false)

        val capture = PendingCaptureEntity(
            id = UUID.randomUUID().toString(),
            source = "BANK",
            amountMxn = parsed.amountMxn,
            concept = parsed.merchant,
            occurredAt = parsed.occurredAt,
            suggestedWalletId = walletId,
            suggestedCategoryId = categoryId,
            status = "PENDING",
            createdAt = System.currentTimeMillis(),
            bankId = parsed.bankId,
            bankName = parsed.bankName,
            bankPackage = parsed.bankPackage,
            last4 = parsed.last4,
            latitude = fix?.latitude,
            longitude = fix?.longitude,
            placeLabel = fix?.placeLabel,
            locationSource = if (fix != null) LocationSource.CAPTURE else null,
        )
        enqueue(capture)
    }

    /**
     * Punto de entrada GENÉRICO a la bandeja unificada (§G.1): inserta la
     * propuesta `PENDING` y postea su notificación. Los futuros productores
     * (calendario, voz, widget, reloj) llaman aquí en vez de duplicar la lógica;
     * [ingest] es solo el productor `source="BANK"`. Confirmar/descartar (más
     * abajo) ya son agnósticos a la fuente.
     */
    suspend fun enqueue(capture: PendingCaptureEntity) {
        pendingDao.insert(capture)
        postNotification(capture)
    }

    /**
     * Productor de **lenguaje natural** (Apéndice G.3, rediseño A2): voz in-app,
     * widget, reloj. Flujo **crear-inmediato + enriquecer-async (D1)**:
     *
     *  1. SÍNCRONO (<1 s, sin LLM): corre solo el parser determinista
     *     ([NlCaptureExtractor.extractFast] — monto/concepto/fecha) e inserta la
     *     propuesta `PENDING` con `enrichStatus="ENRICHING"`. **Sin defaults
     *     silenciosos**: wallet/categoría solo si hay evidencia real (mención
     *     explícita en la frase, o —para categoría— match del historial); si no,
     *     `null` y la revisión los pedirá ("Por decidir").
     *  2. ASYNC: encola [EnrichCaptureWorker], que completa categoría/atribución
     *     con heurísticas y (solo si AICore está disponible) la pasada LLM rica,
     *     y termina marcando `READY`.
     *
     * @param source `VOICE | WIDGET | WATCH` (§G.1).
     * @param spokenAtEpochMs el **momento del dictado** (no el de confirmación):
     *   ancla de "hoy/ayer/antier" del parser.
     * @return el id de la captura creada, o `null` si no se extrajo un monto > 0
     *   (no se crea nada; el overlay avisa al usuario).
     */
    suspend fun ingestText(
        rawText: String,
        source: String,
        spokenAtEpochMs: Long = System.currentTimeMillis(),
    ): String? {
        val parsed = nlCaptureExtractor.extractFast(rawText, spokenAtEpochMs) ?: return null

        // Evidencia real, nada de "primer wallet / primera categoría":
        //  - wallet: solo si la frase menciona el nombre de un método de pago.
        //  - categoría: mención explícita del nombre, o modal del historial.
        val wallets = paymentMethodDao.getActive(householdId)
        val categories = categoryDao.getAll(householdId)
        val walletId = findMentioned(rawText, wallets.map { it.id to it.displayName })
        val categoryId = findMentioned(rawText, categories.map { it.id to it.displayName })
            ?: resolveCategoryFromHistory(parsed.concept)

        val capture = PendingCaptureEntity(
            id = UUID.randomUUID().toString(),
            source = source,
            amountMxn = parsed.amountMxn,
            concept = parsed.concept,
            occurredAt = parsed.occurredAt,
            suggestedWalletId = walletId,
            suggestedCategoryId = categoryId,
            status = "PENDING",
            enrichStatus = "ENRICHING",
            createdAt = System.currentTimeMillis(),
            rawText = rawText,
        )
        Log.d(
            "NlCapture",
            "captura inmediata: monto=${parsed.amountMxn} concepto='${parsed.concept}' " +
                "wallet=$walletId categoría=$categoryId (evidencia; null = por decidir)",
        )
        enqueue(capture)
        EnrichCaptureWorker.enqueue(context, capture.id)
        return capture.id
    }

    /**
     * Fase 2 del flujo D1 (la llama [EnrichCaptureWorker], bajo su watchdog):
     * enriquece una captura `ENRICHING` sin bloquear al usuario.
     *
     *  1. **LLM rica** — SOLO si AICore/Gemini Nano está disponible (el gate vive
     *     en [NlCaptureExtractor.enrich]; LiteRT-LM/Gemma queda PROHIBIDO en este
     *     camino — su carga de 3.7 GB era la causa del OOM-kill tras dictar):
     *     beneficiarios/pagadores/notas/hints de categoría y wallet.
     *  2. **Heurísticas deterministas** para lo que siga faltando: categoría por
     *     historial y atribución modal por concepto ([RetroAttributionEngine]).
     *
     * No inventa evidencia: lo que no se pueda inferir queda `null` y la revisión
     * lo marca "Por decidir". Termina siempre con `enrich_status='READY'` (vía
     * [PendingCaptureDao.updateEnrichment]); si esta corrida muere a medias, el
     * worker tiene además su [PendingCaptureDao.markEnrichReady] de seguridad.
     */
    suspend fun enrichCapture(captureId: String) {
        val row = pendingDao.getById(captureId) ?: return
        if (row.status != "PENDING" || row.enrichStatus != "ENRICHING") return

        var walletId: String? = null
        var categoryId: String? = null
        var beneficiaryJson: String? = null
        var payerJson: String? = null
        var notes: String? = null

        runCatching {
            val members = memberDao.getActiveMembers(householdId)
                .filter { !it.role.startsWith("EXTERNAL_") }
            val categories = categoryDao.getAll(householdId)
            val wallets = paymentMethodDao.getActive(householdId)
            val resolver = AliasResolver(members = members, categories = categories, wallets = wallets)

            // 1) Pasada LLM rica (solo AICore; nunca carga Gemma).
            val rich = row.rawText?.let { raw ->
                nlCaptureExtractor.enrich(
                    rawText = raw,
                    context = CaptureContext(
                        memberNames = members.map { it.displayName },
                        categoryNames = categories.map { it.displayName },
                        walletNames = wallets.map { it.displayName },
                    ),
                    // La fecha ya quedó resuelta al dictar; esto solo ancla al parser.
                    nowEpochMs = row.createdAt,
                )
            }
            if (rich != null) {
                categoryId = rich.categoryHint?.let { resolver.resolveCategory(it)?.id }
                walletId = rich.walletHint?.let { resolver.resolveWallet(it)?.id }
                beneficiaryJson = bpsToJson(resolveShares(rich.beneficiaries, resolver, members))
                payerJson = bpsToJson(resolveShares(rich.payers, resolver, members))
                notes = rich.notes
            }

            // 2) Heurísticas deterministas para lo que el LLM no aportó.
            if (categoryId == null && row.suggestedCategoryId == null) {
                categoryId = resolveCategoryFromHistory(row.concept)
            }
            if (beneficiaryJson == null && row.suggestedBeneficiaryJson == null) {
                beneficiaryJson = engine.suggest(row.concept, "BENEFICIARY")
                    ?.distribution?.let { bpsToJson(it) }
            }
            if (payerJson == null && row.suggestedPayerJson == null) {
                payerJson = engine.suggest(row.concept, "PAYER")
                    ?.distribution?.let { bpsToJson(it) }
            }
        }.onFailure { Log.w("NlCapture", "enriquecimiento de $captureId falló (best-effort)", it) }

        // COALESCE en el DAO: los null conservan lo ya resuelto al insertar.
        pendingDao.updateEnrichment(
            id = captureId,
            walletId = walletId,
            categoryId = categoryId,
            beneficiaryJson = beneficiaryJson,
            payerJson = payerJson,
            notes = notes,
        )
        Log.d(
            "NlCapture",
            "enriquecida $captureId: wallet=$walletId categoría=$categoryId " +
                "benef=${beneficiaryJson != null} pagador=${payerJson != null}",
        )
    }

    /**
     * Evidencia por mención explícita: devuelve el id cuyo `displayName` (≥3
     * caracteres, sin acentos, case-insensitive) aparece dentro de la frase.
     * Es deliberadamente conservador — mejor `null` (→ "Por decidir") que un
     * default silencioso equivocado.
     */
    private fun findMentioned(rawText: String, idToName: List<Pair<String, String>>): String? {
        val haystack = rawText.lowercase().unaccent()
        return idToName.firstOrNull { (_, name) ->
            val needle = name.trim().lowercase().unaccent()
            needle.length >= 3 && needle in haystack
        }?.first
    }

    /** Serializa `{memberId: bps}` a JSON, o null si no hay atribución. */
    private fun bpsToJson(bps: Map<String, Int>): String? =
        if (bps.isEmpty()) null else JSONObject(bps as Map<*, *>).toString()

    /**
     * Marca una captura como CONFIRMED y cancela su notificación, SIN crear gasto:
     * lo usa la ruta "revisar al confirmar", donde la hoja de captura ya registró el
     * gasto (con posibles ediciones del usuario). Complementa a [confirm], que sí
     * construye el gasto para las capturas simples.
     */
    suspend fun markConfirmedExternally(captureId: String) {
        pendingDao.updateStatus(captureId, "CONFIRMED")
        cancelNotification(captureId)
    }

    /**
     * Resuelve los nombres crudos del LLM ([NameShare]) a un mapa `memberId → bps`
     * (suma 10,000). Soporta el comodín "TODOS"/"familia" (= todos los miembros).
     * Si ningún `share` viene explícito → reparto equitativo; si todos vienen →
     * se reescalan; si vienen mezclados (algunos sí, otros no) → equitativo (seguro).
     */
    private fun resolveShares(
        shares: List<NameShare>,
        resolver: AliasResolver,
        members: List<MemberEntity>,
    ): Map<String, Int> {
        if (shares.isEmpty()) return emptyMap()
        // memberId → share (% explícito o null). LinkedHashMap conserva el orden.
        val resolved = LinkedHashMap<String, Int?>()
        for (ns in shares) {
            val token = ns.name.trim().lowercase().unaccent()
            if (token in ALL_TOKENS) {
                members.forEach { m -> if (m.id !in resolved) resolved[m.id] = null }
            } else {
                val m = resolver.resolveMember(ns.name) ?: continue
                // Un share explícito gana sobre un null previo (p.ej. de "TODOS").
                resolved[m.id] = ns.share ?: resolved[m.id]
            }
        }
        if (resolved.isEmpty()) return emptyMap()
        val explicit = resolved.filterValues { it != null }
        return when {
            explicit.isEmpty() -> equalSplit(resolved.keys.toList())
            explicit.size == resolved.size -> normalizeBps(resolved.mapValues { it.value!! })
            else -> equalSplit(resolved.keys.toList())
        }
    }

    // ── Confirmación / descarte (desde chip o notificación) ─────────────────────

    /** Confirma una captura: arma el gasto POSTED con atribución inferida y lo inserta. */
    suspend fun confirm(captureId: String) {
        val row = pendingDao.getById(captureId) ?: return
        if (row.status != "PENDING") return

        val quincena = quincenaRepository.getActive(householdId) ?: return
        val wallets = paymentMethodDao.getActive(householdId)
        val walletId = row.suggestedWalletId ?: wallets.firstOrNull()?.id ?: return
        val categoryId = row.suggestedCategoryId
            ?: categoryDao.getAll(householdId).firstOrNull()?.id ?: return

        val members = memberDao.getActiveMembers(householdId)
            .filter { !it.role.startsWith("EXTERNAL_") }
        val wallet = wallets.firstOrNull { it.id == walletId }
        val ownerId = wallet?.ownerMemberId
            ?: members.firstOrNull { it.role == "PAYER_ADULT" }?.id
            ?: members.firstOrNull()?.id

        // Atribución inferida por el concepto; fallback conservador y editable.
        val payerBps = engine.suggest(row.concept, "PAYER")?.distribution
            ?: ownerId?.let { mapOf(it to 10_000) } ?: emptyMap()
        val beneficiaryBps = engine.suggest(row.concept, "BENEFICIARY")?.distribution
            ?: equalSplit(members.map { it.id }).ifEmpty { payerBps }

        if (payerBps.isEmpty() || beneficiaryBps.isEmpty()) return

        val expenseId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        // Ubicación del gasto (§G.4.2), por prioridad:
        //  (a) la que la captura ya trae (fix tomado al ingresar, nivel persistente);
        //  (b) si no, y la confirmación cae dentro de la ventana corta del cargo,
        //      un fix fresco en este momento (confirmar = foreground) → CONFIRM;
        //  (c) si no, sin ubicación (NONE). Evita "ubicación = donde confirmaste horas
        //      después".
        val locationFix = when {
            row.locationSource != null -> LocationData(row.latitude, row.longitude, row.placeLabel, row.locationSource)
            now - row.occurredAt <= CONFIRM_WINDOW_MS ->
                locationProvider.currentFix(requireForeground = true)
                    ?.let { LocationData(it.latitude, it.longitude, it.placeLabel, LocationSource.CONFIRM) }
            else -> null
        }

        val expense = ExpenseEntity(
            id = expenseId,
            householdId = householdId,
            occurredAt = row.occurredAt,
            quincenaId = quincena.id,
            categoryId = categoryId,
            concept = row.concept.take(64),
            amountMxn = row.amountMxn,
            paymentMethodId = walletId,
            status = "POSTED",
            createdAt = now,
            latitude = locationFix?.latitude,
            longitude = locationFix?.longitude,
            placeLabel = locationFix?.placeLabel,
            locationSource = locationFix?.source ?: LocationSource.NONE,
        )
        val attributions =
            buildRows(expenseId, normalizeBps(beneficiaryBps), "BENEFICIARY", row.amountMxn) +
                buildRows(expenseId, normalizeBps(payerBps), "PAYER", row.amountMxn)

        expenseRepository.insertWithAttributions(expense, attributions)
        pendingDao.updateStatus(captureId, "CONFIRMED")
        cancelNotification(captureId)
    }

    /** Descarta una captura (señal negativa implícita). */
    suspend fun dismiss(captureId: String) {
        pendingDao.updateStatus(captureId, "DISMISSED")
        cancelNotification(captureId)
    }

    // ── Resolución ──────────────────────────────────────────────────────────────

    private fun resolveWallet(
        parsed: ParsedCharge,
        wallets: List<mx.budget.data.local.entity.PaymentMethodEntity>,
    ): String? {
        // 1) Match exacto por últimos 4 dígitos.
        parsed.last4?.let { l4 ->
            wallets.firstOrNull { it.last4 == l4 }?.let { return it.id }
        }
        // 2) Match por emisor (la app del banco → su(s) cuenta(s)).
        val byIssuer = wallets.firstOrNull {
            it.issuer?.contains(parsed.bankName, ignoreCase = true) == true
        }
        return byIssuer?.id
    }

    /**
     * Categoría modal entre gastos históricos del mismo comercio; fallback a
     * "otros"/primera. Solo la ruta BANK usa este fallback laxo (el comercio
     * viene de una notificación real); la ruta NL usa [resolveCategoryFromHistory]
     * a secas para no inventar evidencia.
     */
    private suspend fun resolveCategory(merchant: String): String? {
        resolveCategoryFromHistory(merchant)?.let { return it }
        val categories = categoryDao.getAll(householdId)
        val misc = categories.firstOrNull { c ->
            val s = "${c.displayName} ${c.code}".lowercase()
            listOf("otro", "vari", "sin categor", "misc", "general").any { it in s }
        }
        return misc?.id ?: categories.firstOrNull()?.id
    }

    /**
     * Evidencia de historial: categoría modal entre los gastos POSTED cuyo
     * concepto contiene el texto. SIN fallback — `null` si el historial no dice
     * nada (la captura queda "Por decidir" en vez de asumir una categoría).
     */
    private suspend fun resolveCategoryFromHistory(merchant: String): String? {
        val key = merchant.trim().lowercase()
        if (key.isEmpty()) return null
        val history = runCatching { expenseDao.getAll(householdId) }.getOrDefault(emptyList())
        return history
            .filter { it.status == "POSTED" && it.concept.lowercase().contains(key) }
            .groupingBy { it.categoryId }.eachCount()
            .maxByOrNull { it.value }?.key
    }

    // ── Helpers de atribución (mismo invariante que CaptureViewModel) ────────────

    private fun equalSplit(ids: List<String>): Map<String, Int> {
        if (ids.isEmpty()) return emptyMap()
        val base = 10_000 / ids.size
        val remainder = 10_000 - base * ids.size
        return ids.mapIndexed { i, id -> id to if (i == ids.lastIndex) base + remainder else base }.toMap()
    }

    /** Reescala para sumar exactamente 10,000 bps (el último absorbe el resto). */
    private fun normalizeBps(raw: Map<String, Int>): Map<String, Int> {
        if (raw.isEmpty()) return emptyMap()
        val total = raw.values.sum().takeIf { it > 0 } ?: return equalSplit(raw.keys.toList())
        val entries = raw.entries.toList()
        var assigned = 0
        return entries.mapIndexed { i, (id, bps) ->
            val scaled = if (i == entries.lastIndex) 10_000 - assigned
            else ((bps.toLong() * 10_000) / total).toInt().also { assigned += it }
            id to scaled
        }.toMap()
    }

    private fun buildRows(
        expenseId: String,
        bps: Map<String, Int>,
        role: String,
        amount: Double,
    ): List<ExpenseAttributionEntity> = bps.map { (memberId, share) ->
        ExpenseAttributionEntity(
            id = UUID.randomUUID().toString(),
            expenseId = expenseId,
            memberId = memberId,
            role = role,
            shareBps = share,
            shareAmountMxn = amount * share / 10_000.0,
        )
    }

    // ── Notificación propia (silenciosa, con acciones) ──────────────────────────

    private fun postNotification(capture: PendingCaptureEntity) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val money = NumberFormat.getCurrencyInstance(Locale("es", "MX")).format(capture.amountMxn)
        val confirmPi = actionIntent(capture.id, BankCaptureActions.CONFIRM)
        val dismissPi = actionIntent(capture.id, BankCaptureActions.DISMISS)

        // Título según la fuente: BANK antepone el banco; el resto, monto + concepto.
        val title = capture.bankName
            ?.let { "$it: $money en ${capture.concept}" }
            ?: "$money en ${capture.concept}"
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText("¿Registrar este gasto?")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Registrar", confirmPi)
            .addAction(0, "Descartar", dismissPi)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(capture.id.hashCode(), notif)
        }
    }

    private fun cancelNotification(captureId: String) {
        NotificationManagerCompat.from(context).cancel(captureId.hashCode())
    }

    private fun actionIntent(captureId: String, action: String): PendingIntent {
        val intent = Intent(context, BankCaptureActionReceiver::class.java).apply {
            this.action = action
            putExtra(BankCaptureActions.EXTRA_CAPTURE_ID, captureId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, (captureId + action).hashCode(), intent, flags)
    }

    /** Ubicación resuelta para el gasto al confirmar (interno). */
    private data class LocationData(
        val latitude: Double?,
        val longitude: Double?,
        val placeLabel: String?,
        val source: String,
    )

    companion object {
        const val CHANNEL_ID = "bank_capture"

        /** Comodines que la frase usa para "toda la familia" (ya sin acentos). */
        private val ALL_TOKENS = setOf("todos", "todas", "todo", "familia", "casa", "hogar")

        /**
         * Ventana corta (§G.4.2) para auto-adjuntar ubicación al confirmar una
         * captura de background: 15 min desde el cargo. Más allá, la confirmación
         * no representa el lugar del gasto → queda NONE.
         */
        const val CONFIRM_WINDOW_MS = 15 * 60 * 1000L

        /** Crea el canal de notificaciones de captura bancaria (idempotente). */
        fun ensureChannel(context: Context) {
            val mgr = context.getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Captura desde notificaciones",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Propuestas de gasto detectadas en notificaciones bancarias." }
            mgr.createNotificationChannel(channel)
        }
    }
}
