package mx.budget.data.statements

import android.net.Uri
import androidx.room.withTransaction
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import mx.budget.ai.proactive.SuggestedShare
import mx.budget.core.unaccent
import mx.budget.data.local.BudgetDatabase
import mx.budget.data.local.dao.AttributionReviewDao
import mx.budget.data.local.dao.CategoryDao
import mx.budget.data.local.dao.ExpenseDao
import mx.budget.data.local.dao.MemberDao
import mx.budget.data.local.dao.QuincenaDao
import mx.budget.data.local.dao.StatementImportDao
import mx.budget.data.local.entity.AttributionReviewEntity
import mx.budget.data.local.entity.ExpenseAttributionEntity
import mx.budget.data.local.entity.ExpenseEntity
import mx.budget.data.local.entity.InstallmentPlanEntity
import mx.budget.data.local.entity.PaymentMethodEntity
import mx.budget.data.local.entity.StatementImportEntity
import mx.budget.data.local.entity.WalletTransferEntity
import mx.budget.data.repository.ExpenseRepository
import mx.budget.data.repository.InstallmentRepository
import mx.budget.data.repository.TransferRepository
import mx.budget.data.repository.WalletRepository
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Orquestador de la importación de estados de cuenta (Fase C, paquete C1 +
 * extensión "Reescribir movimientos").
 *
 * Une las piezas: extracción LOCAL de texto ([StatementTextExtractor]) →
 * análisis con LLM cloud ([NvidiaNimClient]) → reconciliación contra los repos
 * existentes ([WalletRepository], [InstallmentRepository]) + auditoría en
 * `statement_import`. Y, con confirmación explícita del usuario, la
 * **reescritura de movimientos** de la tarjeta:
 *
 *  - Convierte el gasto agregado de "pago de tarjeta" (categoría
 *    `LOANS.<TARJETA>` pagada desde un wallet bancario) en una transferencia
 *    banco→tarjeta (RF-41): deja de contar como gasto y queda como abono a la
 *    deuda. El borrado revierte el saldo del banco y la transferencia lo
 *    vuelve a mover, así que el neto del banco es cero.
 *  - Inserta las compras itemizadas del estado como gastos reales cargados al
 *    wallet de la tarjeta, con categoría sugerida por historial, beneficiarios
 *    en partes iguales, pagador 100% el adulto pagador (Norma, regla del hogar
 *    desde oct-2025) y una fila `attribution_review` PENDING para que la
 *    atribución se reasigne desde la pantalla de revisión / detalle del gasto.
 *
 * INVARIANTE DE SEGURIDAD DE DATOS: la reconciliación sola ([apply]) sigue sin
 * tocar el historial. La reescritura ([applyWithRewrite]) SÍ escribe el ledger,
 * pero **solo** lo que el usuario confirmó item por item, todo dentro de una
 * transacción Room y siempre a través de los repos públicos (que estampan
 * `updated_at` y encolan en `sync_queue` para el push a Firestore).
 */
class StatementImportManager(
    private val extractor: StatementTextExtractor,
    private val nimClient: NvidiaNimClient,
    private val walletRepository: WalletRepository,
    private val installmentRepository: InstallmentRepository,
    private val statementImportDao: StatementImportDao,
    private val expenseRepository: ExpenseRepository,
    private val transferRepository: TransferRepository,
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao,
    private val memberDao: MemberDao,
    private val quincenaDao: QuincenaDao,
    private val attributionReviewDao: AttributionReviewDao,
    private val db: BudgetDatabase,
    private val householdId: String,
) {

    private val zone = ZoneId.of("America/Mexico_City")
    private val json = Json { ignoreUnknownKeys = true }

    sealed interface ExtractResult {
        data class Success(val text: String) : ExtractResult
        data class Failure(val message: String) : ExtractResult
    }

    sealed interface AnalyzeResult {
        data class Success(val statement: ParsedStatement, val rawJson: String) : AnalyzeResult
        data class Failure(val message: String) : AnalyzeResult
    }

    /** Paso 1: extrae el texto del archivo, 100% local. */
    suspend fun extractText(uri: Uri): ExtractResult =
        when (val r = extractor.extract(uri)) {
            is StatementTextExtractor.Result.Success -> ExtractResult.Success(r.text)
            is StatementTextExtractor.Result.Failure -> ExtractResult.Failure(r.message)
        }

    /** Paso 2: envía SOLO el texto al LLM cloud y obtiene el JSON estructurado. */
    suspend fun analyze(statementText: String): AnalyzeResult =
        when (val r = nimClient.analyze(statementText)) {
            is NvidiaNimClient.Result.Success -> AnalyzeResult.Success(r.statement, r.rawJson)
            is NvidiaNimClient.Result.Failure -> AnalyzeResult.Failure(r.message)
        }

    /**
     * Paso 3 (ruta SIN reescritura — sin wallet elegido o legado C1):
     * **reconciliación** con los datos (posiblemente editados) del preview.
     * Solo añade claridad futura; no toca los gastos históricos.
     *
     * @param statement datos finales tras la edición del usuario.
     * @param rawJson JSON crudo original del LLM (auditoría).
     * @param walletId wallet (payment_method) al que se aplica; null = solo audita.
     * @return conteo de planes MSI creados/actualizados (para el mensaje de éxito).
     */
    suspend fun apply(
        statement: ParsedStatement,
        rawJson: String,
        walletId: String?,
    ): Int {
        var msiTouched = 0
        db.withTransaction {
            msiTouched = reconcileAndAudit(statement, rawJson, walletId)
        }
        return msiTouched
    }

    // ── Reescritura de movimientos (extensión C1) ───────────────────────────────

    /**
     * Construye el plan de reescritura para el preview del paso "Reescribir
     * movimientos": compras candidatas (con categoría sugerida por historial) y
     * gastos agregados de pago de tarjeta detectados en el periodo. Solo lee;
     * no escribe nada.
     */
    suspend fun buildRewritePlan(
        statement: ParsedStatement,
        walletId: String,
    ): RewritePlan {
        val wallet = walletRepository.getById(walletId)
        val categories = categoryDao.getAll(householdId)
        val categoriesById = categories.associateBy { it.id }
        val history = runCatching { expenseDao.getAll(householdId) }
            .getOrDefault(emptyList())
            .filter { it.status == "POSTED" }

        val purchases = statement.movimientos
            .filter { (it.monto ?: 0.0) > 0.0 && !isPaymentMovement(it) }
            .map { mov ->
                val concepto = mov.concepto?.trim().orEmpty().ifBlank { "Compra tarjeta" }
                val categoryId = suggestCategory(concepto, history)
                PlannedPurchase(
                    fecha = mov.fecha,
                    concepto = concepto,
                    montoMxn = mov.monto ?: 0.0,
                    esMsi = mov.esMsi && (mov.msiPlazo ?: 0) > 1,
                    suggestedCategoryId = categoryId,
                    suggestedCategoryName = categoryId?.let { categoriesById[it]?.displayName },
                )
            }

        val aggregates = if (wallet != null) detectAggregates(statement, wallet) else emptyList()

        val members = activeHouseholdMembers()
        val payer = resolveDefaultPayer(members, wallet)

        return RewritePlan(
            purchases = purchases,
            aggregates = aggregates,
            payerName = payer?.displayName,
            beneficiaryCount = members.size,
        )
    }

    /**
     * Paso 3 (ruta CON reescritura): aplica en UNA transacción Room, en este orden:
     *
     *  1. Convierte los agregados confirmados: borra el gasto revirtiendo su
     *     saldo ([ExpenseRepository.deleteAndRevertBalance]) y registra la
     *     transferencia banco→tarjeta ([TransferRepository.recordTransfer]).
     *  2. Inserta las compras confirmadas como gastos POSTED del wallet de la
     *     tarjeta ([ExpenseRepository.insertWithAttributions]) + fila
     *     `attribution_review` PENDING (rol BENEFICIARY) por cada una.
     *  3. Reconcilia el wallet (saldo/corte/límite/tasa) — el saldo absoluto del
     *     estado va AL FINAL para que mande sobre los movimientos del paso 1-2.
     *  4. Upsert de planes MSI + fila de auditoría (idéntico a C1).
     *
     * Todos los pasos 1-2 pasan por los repos públicos: estampan `updated_at` y
     * encolan `sync_queue` (EXPENSE/TRANSFER/WALLET) — NUNCA DAO directo.
     */
    suspend fun applyWithRewrite(
        statement: ParsedStatement,
        rawJson: String,
        walletId: String,
        purchases: List<PlannedPurchase>,
        aggregateExpenseIds: List<String>,
    ): RewriteResult {
        var inserted = 0
        var insertedTotal = 0.0
        var converted = 0
        var msiTouched = 0

        db.withTransaction {
            // (1) Agregados → transferencia banco→tarjeta.
            for (expenseId in aggregateExpenseIds) {
                val expense = expenseDao.getById(expenseId) ?: continue
                if (expense.status != "POSTED" || expense.paymentMethodId == walletId) continue
                expenseRepository.deleteAndRevertBalance(expenseId)
                transferRepository.recordTransfer(
                    WalletTransferEntity(
                        id = UUID.randomUUID().toString(),
                        householdId = householdId,
                        fromPaymentMethodId = expense.paymentMethodId,
                        toPaymentMethodId = walletId,
                        amountMxn = expense.amountMxn,
                        occurredAt = expense.occurredAt,
                        note = "Pago de tarjeta (estado de cuenta): ${expense.concept}".take(120),
                        createdAt = System.currentTimeMillis(),
                    )
                )
                converted++
            }

            // (2) Compras itemizadas → gastos reales + revisión de atribución.
            if (purchases.isNotEmpty()) {
                val members = activeHouseholdMembers()
                val payer = resolveDefaultPayer(members, walletRepository.getById(walletId))
                val fallbackCategoryId = fallbackCategoryId()
                val importTag = buildImportTag(statement)

                if (members.isNotEmpty() && payer != null && fallbackCategoryId != null) {
                    val beneficiaryBps = equalSplit(members.map { it.id })
                    val payerBps = mapOf(payer.id to 10_000)
                    for (purchase in purchases) {
                        val occurredAt = resolveOccurredAt(purchase.fecha, statement)
                        val quincenaId = resolveQuincenaId(occurredAt) ?: continue
                        val expenseId = UUID.randomUUID().toString()
                        val amount = purchase.montoMxn
                        val expense = ExpenseEntity(
                            id = expenseId,
                            householdId = householdId,
                            occurredAt = occurredAt,
                            quincenaId = quincenaId,
                            categoryId = purchase.suggestedCategoryId ?: fallbackCategoryId,
                            concept = purchase.concepto.take(64),
                            amountMxn = amount,
                            paymentMethodId = walletId,
                            status = "POSTED",
                            notes = importTag,
                            createdAt = System.currentTimeMillis(),
                        )
                        val attributions =
                            buildRows(expenseId, beneficiaryBps, "BENEFICIARY", amount) +
                                buildRows(expenseId, payerBps, "PAYER", amount)
                        expenseRepository.insertWithAttributions(expense, attributions)

                        // Cola de revisión: el reparto equitativo es un default,
                        // no una verdad — Norma reasigna quién se benefició.
                        attributionReviewDao.insert(
                            AttributionReviewEntity(
                                id = UUID.randomUUID().toString(),
                                expenseId = expenseId,
                                role = "BENEFICIARY",
                                suggestedJson = json.encodeToString(
                                    ListSerializer(SuggestedShare.serializer()),
                                    beneficiaryBps.map { SuggestedShare(it.key, it.value) },
                                ),
                                confidence = 0.0,
                                sampleSize = 0,
                                conceptCanonical = null,
                                status = "PENDING",
                                createdAt = System.currentTimeMillis(),
                            )
                        )
                        inserted++
                        insertedTotal += amount
                    }
                }
            }

            // (3) + (4) Reconciliación C1 (wallet al final: el saldo del estado
            // manda) + MSI + auditoría.
            msiTouched = reconcileAndAudit(statement, rawJson, walletId)
        }

        return RewriteResult(
            msiTouched = msiTouched,
            insertedExpenses = inserted,
            insertedTotalMxn = insertedTotal,
            convertedTransfers = converted,
        )
    }

    /**
     * ¿El movimiento es un pago/abono (dinero a favor), no una compra? El esquema
     * extendido trae `tipo` (PAGO/ABONO); para respuestas viejas sin `tipo` cae a una
     * heurística por concepto. Evita reinsertar pagos como gastos al reescribir.
     */
    private fun isPaymentMovement(mov: StatementMovement): Boolean {
        val tipo = mov.tipo?.trim()?.uppercase()
        if (tipo == "PAGO" || tipo == "ABONO") return true
        val concepto = mov.concepto?.uppercase().orEmpty()
        return PAYMENT_CONCEPT_REGEX.containsMatchIn(concepto)
    }

    // ── Detección de agregados ──────────────────────────────────────────────────

    /**
     * Gastos POSTED dentro del rango del estado de cuenta cuya categoría es
     * `LOANS.*` y cuyo texto (categoría/código/concepto) coincide con la tarjeta
     * (displayName/issuer del wallet), pagados desde OTRO wallet. Son los
     * "pagos de tarjeta" agregados de la semilla (p. ej. "Liverpool" $2,700
     * desde Banamex) convertibles a transferencia.
     */
    private suspend fun detectAggregates(
        statement: ParsedStatement,
        wallet: PaymentMethodEntity,
    ): List<AggregateCandidate> {
        val range = statementEpochRange(statement) ?: return emptyList()
        val walletKeys = listOfNotNull(wallet.displayName, wallet.issuer)
            .map { it.trim().lowercase().unaccent() }
            .filter { it.length >= 3 }
        if (walletKeys.isEmpty()) return emptyList()

        val categoriesById = categoryDao.getAll(householdId).associateBy { it.id }
        val walletsById = walletRepository.getActive(householdId).associateBy { it.id }

        return expenseDao.getByDateRange(householdId, range.first, range.second)
            .asSequence()
            .filter { it.status == "POSTED" && it.paymentMethodId != wallet.id }
            .mapNotNull { expense ->
                val category = categoriesById[expense.categoryId] ?: return@mapNotNull null
                if (!category.code.uppercase().startsWith("LOANS")) return@mapNotNull null
                val matches = matchesAnyKey(
                    walletKeys,
                    category.displayName,
                    category.code.substringAfter('.', ""),
                    expense.concept,
                )
                if (!matches) return@mapNotNull null
                AggregateCandidate(
                    expenseId = expense.id,
                    concept = expense.concept,
                    amountMxn = expense.amountMxn,
                    occurredAt = expense.occurredAt,
                    fromWalletId = expense.paymentMethodId,
                    fromWalletName = walletsById[expense.paymentMethodId]?.displayName
                        ?: "Cuenta",
                    categoryName = category.displayName,
                )
            }
            .toList()
    }

    /**
     * Rango epoch [inicio, fin] del estado: del inicio del periodo (o corte−31d)
     * hasta la fecha más tardía entre fin de periodo / corte / límite de pago
     * (el pago agregado puede registrarse después del corte).
     */
    private fun statementEpochRange(statement: ParsedStatement): Pair<Long, Long>? {
        val start = parseIsoDate(statement.periodo?.inicio)
            ?: parseIsoDate(statement.fechaCorte)?.minusDays(31)
            ?: return null
        val end = listOfNotNull(
            parseIsoDate(statement.periodo?.fin),
            parseIsoDate(statement.fechaCorte),
            parseIsoDate(statement.fechaLimitePago),
        ).maxOrNull() ?: return null
        if (end < start) return null
        val from = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return from to to
    }

    /** Containment bidireccional (sin acentos, ≥3 chars) contra las claves del wallet. */
    private fun matchesAnyKey(keys: List<String>, vararg texts: String?): Boolean =
        texts.asSequence()
            .filterNotNull()
            .map { it.trim().lowercase().unaccent() }
            .filter { it.length >= 3 }
            .any { text -> keys.any { key -> text.contains(key) || key.contains(text) } }

    // ── Sugerencia de categoría (heurística por comercio, estilo BankCaptureManager) ──

    /**
     * Categoría modal entre los gastos POSTED históricos que comparten tokens
     * (≥4 chars, sin acentos) con el concepto del estado de cuenta. Los nombres
     * de comercio de los estados ("WAL MART SUPERCENTER") rara vez coinciden
     * literalmente con los conceptos de la app, por eso se cruza por tokens.
     * `null` si el historial no dice nada — el apply cae a la categoría "otros".
     */
    private fun suggestCategory(concept: String, history: List<ExpenseEntity>): String? {
        val tokens = tokensOf(concept)
        if (tokens.isEmpty()) return null
        return history
            .filter { tokensOf(it.concept).any { t -> t in tokens } }
            .groupingBy { it.categoryId }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
    }

    private fun tokensOf(text: String): Set<String> =
        text.lowercase().unaccent()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 4 }
            .toSet()

    /** Categoría comodín ("otros"/"varios"/…) o la primera del hogar. */
    private suspend fun fallbackCategoryId(): String? {
        val categories = categoryDao.getAll(householdId)
        val misc = categories.firstOrNull { c ->
            val s = "${c.displayName} ${c.code}".lowercase()
            listOf("otro", "vari", "sin categor", "misc", "general").any { it in s }
        }
        return misc?.id ?: categories.firstOrNull()?.id
    }

    // ── Resolución de miembros / pagador default ────────────────────────────────

    private suspend fun activeHouseholdMembers() =
        memberDao.getActiveMembers(householdId).filter { !it.role.startsWith("EXTERNAL_") }

    /**
     * PAYER 100% de las compras importadas: Norma (regla del hogar desde
     * oct-2025), con fallbacks conservadores: dueño del wallet de la tarjeta →
     * primer PAYER_ADULT → primer miembro.
     */
    private fun resolveDefaultPayer(
        members: List<mx.budget.data.local.entity.MemberEntity>,
        wallet: PaymentMethodEntity?,
    ): mx.budget.data.local.entity.MemberEntity? =
        members.firstOrNull {
            it.role == "PAYER_ADULT" && it.displayName.lowercase().unaccent().contains("norma")
        }
            ?: wallet?.ownerMemberId?.let { ownerId -> members.firstOrNull { it.id == ownerId } }
            ?: members.firstOrNull { it.role == "PAYER_ADULT" }
            ?: members.firstOrNull()

    // ── Fechas / quincena ───────────────────────────────────────────────────────

    private fun parseIsoDate(iso: String?): LocalDate? {
        if (iso.isNullOrBlank()) return null
        return runCatching { LocalDate.parse(iso.trim()) }.getOrNull()
    }

    /** Epoch del cargo: mediodía local de su fecha (o corte / fin de periodo / ahora). */
    private fun resolveOccurredAt(fecha: String?, statement: ParsedStatement): Long {
        val date = parseIsoDate(fecha)
            ?: parseIsoDate(statement.fechaCorte)
            ?: parseIsoDate(statement.periodo?.fin)
            ?: return System.currentTimeMillis()
        return date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    }

    /**
     * Quincena que contiene la fecha del cargo; fallback a la ACTIVE. `null`
     * (gasto omitido) solo si no existe ninguna — la FK `expense.quincena_id`
     * jamás debe romperse.
     */
    private suspend fun resolveQuincenaId(occurredAt: Long): String? {
        val iso = java.time.Instant.ofEpochMilli(occurredAt).atZone(zone).toLocalDate().toString()
        return quincenaDao.getForDate(householdId, iso)?.id
            ?: quincenaDao.getActive(householdId)?.id
    }

    // ── Atribución (mismos invariantes que BankCaptureManager/CaptureViewModel) ──

    private fun equalSplit(ids: List<String>): Map<String, Int> {
        if (ids.isEmpty()) return emptyMap()
        val base = 10_000 / ids.size
        val remainder = 10_000 - base * ids.size
        return ids.mapIndexed { i, id -> id to if (i == ids.lastIndex) base + remainder else base }
            .toMap()
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

    private fun buildImportTag(statement: ParsedStatement): String {
        val emisor = statement.emisor?.trim().orEmpty().ifBlank { "tarjeta" }
        val periodo = listOfNotNull(statement.periodo?.inicio, statement.periodo?.fin)
            .joinToString(" a ")
            .ifBlank { statement.fechaCorte.orEmpty() }
        return buildString {
            append("Importado del estado de cuenta $emisor")
            if (periodo.isNotBlank()) append(" ($periodo)")
        }.take(160)
    }

    // ── Reconciliación C1 (wallet + MSI + auditoría) ────────────────────────────

    /**
     * Núcleo compartido por [apply] y [applyWithRewrite]: reconcilia el wallet,
     * upserta planes MSI e inserta la fila de auditoría. Debe correr dentro de
     * una transacción del caller.
     */
    private suspend fun reconcileAndAudit(
        statement: ParsedStatement,
        rawJson: String,
        walletId: String?,
    ): Int {
        var msiTouched = 0

        // (a) Reconcilia el wallet elegido — solo campos que existen en la entidad.
        if (walletId != null) {
            val wallet = walletRepository.getById(walletId)
            if (wallet != null) {
                walletRepository.update(reconcileWallet(wallet, statement))
            }
        }

        // (b) Planes MSI: crea/actualiza uno por cada movimiento MSI detectado.
        val msiMovements = statement.movimientos.filter { it.esMsi && (it.msiPlazo ?: 0) > 1 }
        if (msiMovements.isNotEmpty()) {
            val existing = installmentRepository.getActive(householdId)
            for (mov in msiMovements) {
                if (upsertInstallment(mov, statement, walletId, existing)) msiTouched++
            }
        }

        // (c) Auditoría: inserta la fila y márcala aplicada.
        val now = System.currentTimeMillis()
        val row = StatementImportEntity(
            id = UUID.randomUUID().toString(),
            householdId = householdId,
            walletId = walletId,
            emisor = statement.emisor,
            last4 = statement.last4,
            periodoInicio = statement.periodo?.inicio,
            periodoFin = statement.periodo?.fin,
            fechaCorte = statement.fechaCorte,
            fechaLimitePago = statement.fechaLimitePago,
            saldoTotal = statement.saldoTotal,
            pagoMinimo = statement.pagoMinimo,
            pagoNoIntereses = statement.pagoNoIntereses,
            tasaAnual = statement.tasaAnual,
            payloadJson = rawJson,
            createdAt = now,
            appliedAt = now,
        )
        statementImportDao.insert(row)
        return msiTouched
    }

    /**
     * Devuelve una copia del wallet con día de corte, día de pago, saldo actual
     * (como reconciliación), límite de crédito y tasa actualizados desde el estado
     * de cuenta. Deja intacto todo lo que el estado no menciona (COALESCE-like).
     */
    private fun reconcileWallet(
        wallet: PaymentMethodEntity,
        statement: ParsedStatement,
    ): PaymentMethodEntity = wallet.copy(
        issuer = statement.emisor ?: wallet.issuer,
        last4 = statement.last4 ?: wallet.last4,
        cutoffDay = dayOfMonth(statement.fechaCorte) ?: wallet.cutoffDay,
        dueDay = dayOfMonth(statement.fechaLimitePago) ?: wallet.dueDay,
        // El saldo al corte es la deuda actual del crédito → reconcilia el saldo.
        currentBalanceMxn = statement.saldoTotal ?: wallet.currentBalanceMxn,
        interestApr = statement.tasaAnual ?: wallet.interestApr,
    )

    /**
     * Crea o actualiza un plan MSI a partir de un movimiento. Best-effort para NO
     * duplicar: hace match por displayName + last4 + plazo total contra los planes
     * activos. Devuelve `true` si tocó (creó o actualizó) un plan.
     */
    private suspend fun upsertInstallment(
        mov: StatementMovement,
        statement: ParsedStatement,
        walletId: String?,
        existing: List<InstallmentPlanEntity>,
    ): Boolean {
        val plazo = mov.msiPlazo ?: return false
        val concepto = mov.concepto?.trim().orEmpty().ifBlank { "MSI" }
        val last4 = statement.last4.orEmpty()
        val displayName = buildString {
            append(concepto)
            if (last4.isNotBlank()) append(" ****$last4")
        }.take(120)
        val monto = mov.monto ?: return false

        // Match best-effort: mismo displayName (case-insensitive) y mismo plazo total.
        val match = existing.firstOrNull {
            it.totalInstallments == plazo &&
                it.displayName.equals(displayName, ignoreCase = true)
        }

        return if (match != null) {
            // Actualiza el avance si el estado de cuenta lo trae más adelantado.
            val current = (mov.msiNumero ?: match.currentInstallment)
                .coerceIn(0, plazo)
            if (current > match.currentInstallment || statement.tasaAnual != null) {
                installmentRepository.update(
                    match.copy(
                        currentInstallment = current,
                        interestRateApr = statement.tasaAnual ?: match.interestRateApr,
                    )
                )
                true
            } else {
                false
            }
        } else {
            installmentRepository.insert(
                InstallmentPlanEntity(
                    id = UUID.randomUUID().toString(),
                    householdId = householdId,
                    displayName = displayName,
                    paymentMethodId = walletId,
                    // Principal estimado = cuota × plazo (el estado suele dar la cuota).
                    principalMxn = monto * plazo,
                    totalInstallments = plazo,
                    installmentAmountMxn = monto,
                    interestRateApr = statement.tasaAnual,
                    startDate = statement.periodo?.inicio ?: statement.fechaCorte
                        ?: mov.fecha ?: today(),
                    currentInstallment = (mov.msiNumero ?: 0).coerceIn(0, plazo),
                    status = "ACTIVE",
                )
            )
            true
        }
    }

    /** Extrae el día del mes (1..31) de una fecha ISO YYYY-MM-DD; null si no parsea. */
    private fun dayOfMonth(iso: String?): Int? {
        if (iso.isNullOrBlank()) return null
        return runCatching { LocalDate.parse(iso).dayOfMonth }.getOrNull()
    }

    private fun today(): String = LocalDate.now(zone).toString()

    companion object {
        /** Heurística de respaldo para clasificar pagos cuando el LLM no da `tipo`. */
        private val PAYMENT_CONCEPT_REGEX =
            Regex("SU PAGO|PAGO GRACIAS|GRACIAS POR SU PAGO|ABONO|BONIFICAC|DEVOLUC|DEPOSITO|CASHBACK")
    }
}
