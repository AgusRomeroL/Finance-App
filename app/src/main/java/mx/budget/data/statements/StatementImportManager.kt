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
import mx.budget.data.local.dao.PendingCaptureDao
import mx.budget.data.local.dao.QuincenaDao
import mx.budget.data.local.dao.StatementImportDao
import mx.budget.data.local.dao.StatementLineDao
import mx.budget.data.local.entity.AttributionReviewEntity
import mx.budget.data.local.entity.ExpenseAttributionEntity
import mx.budget.data.local.entity.ExpenseEntity
import mx.budget.data.local.entity.InstallmentPlanEntity
import mx.budget.data.local.entity.PaymentMethodEntity
import mx.budget.data.local.entity.PendingCaptureEntity
import mx.budget.data.local.entity.StatementImportEntity
import mx.budget.data.local.entity.StatementLineEntity
import mx.budget.data.local.entity.WalletTransferEntity
import mx.budget.data.repository.ExpenseRepository
import mx.budget.data.repository.InstallmentRepository
import mx.budget.data.repository.TransferRepository
import mx.budget.data.repository.WalletRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Orquestador de la importación de estados de cuenta (Fase C, paquete C1 +
 * extensión "Reescribir movimientos" + pre-match de conciliación Fase 5).
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
 * Además, el **pre-match** (Fase 5) reconcilia los movimientos del estado contra
 * los gastos POSTED ya registrados SIN duplicarlos: el vínculo movimiento↔gasto
 * vive en `statement_line`, jamás se edita `expense`. Los movimientos sin pareja
 * marcados como NEW van a la bandeja `pending_capture` (propose-then-confirm).
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
    private val statementLineDao: StatementLineDao,
    private val expenseRepository: ExpenseRepository,
    private val transferRepository: TransferRepository,
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao,
    private val memberDao: MemberDao,
    private val quincenaDao: QuincenaDao,
    private val attributionReviewDao: AttributionReviewDao,
    private val pendingCaptureDao: PendingCaptureDao,
    private val db: BudgetDatabase,
    private val householdId: String,
    private val matcher: StatementMatcher = StatementMatcher(),
) {

    private val zone = ZoneId.of("America/Mexico_City")
    private val json = Json { ignoreUnknownKeys = true }

    /** Decisión final por movimiento al aplicar (misma posición que la lista). */
    data class LineResolution(
        val movementIndex: Int,
        /** `MATCHED` | `NEW` | `IGNORED`. */
        val status: String,
        val matchedExpenseId: String? = null,
        val confidence: Double? = null,
        /** `LOCAL` | `NIM` | `USER`. */
        val source: String? = null,
    )

    /** Resumen del apply para el mensaje de éxito. */
    data class ApplyOutcome(
        val msiTouched: Int,
        val linked: Int,
        val queuedNew: Int,
        val ignored: Int,
        val duplicates: Int,
    )

    /** Resultado interno de [reconcileAndAudit]: MSI tocados + id del import. */
    private data class ReconcileResult(val msiTouched: Int, val importId: String)

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

    /**
     * Paso 2: envía SOLO el texto al LLM cloud y obtiene el JSON estructurado.
     * [kind] selecciona el prompt/esquema (estado de cuenta, compras, movimientos…).
     */
    suspend fun analyze(
        statementText: String,
        kind: DocumentKind = DocumentKind.BANK_STATEMENT,
    ): AnalyzeResult =
        when (val r = nimClient.analyze(statementText, buildLlmContext(), kind)) {
            is NvidiaNimClient.Result.Success -> AnalyzeResult.Success(r.statement, r.rawJson)
            is NvidiaNimClient.Result.Failure -> AnalyzeResult.Failure(r.message)
        }

    /**
     * Contexto del hogar (miembros beneficiarios + categorías hoja) para que el LLM
     * pueble `categoriaSugerida`/`beneficiariosSugeridos`. Best-effort: si falla, se
     * envía sin contexto (el modelo sigue extrayendo los campos base).
     */
    private suspend fun buildLlmContext(): StatementLlmContext? = runCatching {
        val miembros = activeHouseholdMembers().map { it.displayName }
        val categorias = categoryDao.getAll(householdId)
            .filter { it.parentId != null }               // hojas asignables
            .map { "${it.code} — ${it.displayName}" }
        if (miembros.isEmpty() && categorias.isEmpty()) null
        else StatementLlmContext(miembros = miembros, categorias = categorias)
    }.getOrNull()

    /**
     * Paso 2.5 (Fase 5): **pre-match** de los movimientos contra los gastos POSTED
     * del wallet en la ventana del periodo (±7 días). Primero el matching local
     * determinista; los movimientos que quedaron SIN pareja se mandan a NIM como
     * segunda opinión, y cada propuesta del modelo se acepta SOLO si pasa la
     * validación dura local ([StatementMatcher.validate]) — el LLM propone, la
     * regla dispone. Nunca lanza: cualquier fallo degrada al resultado local.
     */
    /** Resultado del pre-match + etiquetas legibles de los gastos involucrados. */
    data class PrematchOutcome(
        val matches: List<StatementMatcher.MovementMatch>,
        /** expenseId → "concepto · fecha · $monto" para la UI de conciliación. */
        val expenseLabels: Map<String, String>,
    )

    suspend fun prematch(
        statement: ParsedStatement,
        walletId: String,
        useNim: Boolean = true,
    ): PrematchOutcome {
        val movements = statement.movimientos
        if (movements.isEmpty()) return PrematchOutcome(emptyList(), emptyMap())

        val (fromEpoch, toEpoch) = candidateWindow(statement, movements)
        val candidates = runCatching {
            expenseDao.getPostedByWalletBetween(walletId, fromEpoch, toEpoch)
        }.getOrDefault(emptyList())
        // Gastos ya vinculados en imports anteriores de este wallet: no reofrecer.
        val alreadyLinked = runCatching {
            statementLineDao.getMatchedExpenseIds(walletId).toSet()
        }.getOrDefault(emptySet())

        val labels = candidates.associate { e ->
            e.id to buildString {
                append(e.concept.take(40))
                append(" · ")
                append(Instant.ofEpochMilli(e.occurredAt).atZone(zone).toLocalDate())
                append(" · $")
                append(String.format(java.util.Locale.US, "%,.2f", e.amountMxn))
            }
        }
        val local = matcher.match(movements, candidates, alreadyLinked)
        if (!useNim) return PrematchOutcome(local, labels)

        // Segunda opinión NIM solo para los movimientos sin pareja local.
        val unmatchedIdx = local.filter { it.expenseId == null }.map { it.movementIndex }
        if (unmatchedIdx.isEmpty() || candidates.isEmpty()) return PrematchOutcome(local, labels)

        val usedExpenseIds = local.mapNotNull { it.expenseId }.toMutableSet()
        val freeCandidates = candidates.filter { it.id !in usedExpenseIds && it.id !in alreadyLinked }
        if (freeCandidates.isEmpty()) return PrematchOutcome(local, labels)

        val nimItems = when (
            val r = runCatching {
                nimClient.prematch(
                    movements = unmatchedIdx.map { movements[it] },
                    candidates = freeCandidates.map { it.toCandidate() },
                )
            }.getOrElse { NvidiaNimClient.PrematchResult.Failure(it.message ?: "error") }
        ) {
            is NvidiaNimClient.PrematchResult.Success -> r.items
            is NvidiaNimClient.PrematchResult.Failure -> return PrematchOutcome(local, labels)
        }

        val byId = freeCandidates.associateBy { it.id }
        val result = local.toMutableList()
        for (item in nimItems) {
            // El índice del modelo refiere a la sublista enviada — remapear.
            val movIdx = unmatchedIdx.getOrNull(item.movimiento) ?: continue
            val expense = item.expenseId?.let { byId[it] } ?: continue
            if (expense.id in usedExpenseIds) continue
            // Validación dura local: si el par no cumple monto/fecha, se descarta.
            if (!matcher.validate(movements[movIdx], expense)) continue
            usedExpenseIds += expense.id
            result[movIdx] = StatementMatcher.MovementMatch(
                movementIndex = movIdx,
                expenseId = expense.id,
                confidence = item.confianza.coerceIn(0.0, 1.0).coerceAtLeast(StatementMatcher.SUGGEST_THRESHOLD),
                auto = false, // lo del LLM siempre queda como sugerencia, nunca auto
                source = "NIM",
            )
        }
        return PrematchOutcome(result, labels)
    }

    /** Ventana de candidatos: periodo del estado (o min/max de fechas) ± 7 días. */
    private fun candidateWindow(
        statement: ParsedStatement,
        movements: List<StatementMovement>,
    ): Pair<Long, Long> {
        val dates = movements.mapNotNull { it.fecha?.let { d -> runCatching { LocalDate.parse(d) }.getOrNull() } }
        val start = statement.periodo?.inicio?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: dates.minOrNull() ?: LocalDate.now(zone).minusMonths(2)
        val end = statement.periodo?.fin?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: dates.maxOrNull() ?: LocalDate.now(zone)
        return start.minusDays(7).atStartOfDay(zone).toInstant().toEpochMilli() to
            end.plusDays(7).atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
    }

    private fun ExpenseEntity.toCandidate() = NvidiaNimClient.CandidateExpense(
        id = id,
        fecha = Instant.ofEpochMilli(occurredAt).atZone(zone).toLocalDate().toString(),
        monto = amountMxn,
        concepto = concept,
    )

    /**
     * Paso 3 (ruta SIN reescritura — legado C1 / conciliación por pre-match):
     * **reconciliación** con los datos (posiblemente editados) del preview.
     * Solo añade claridad futura; no toca los gastos históricos — el vínculo
     * movimiento↔gasto vive en `statement_line`, JAMÁS se edita `expense`.
     *
     * @param statement datos finales tras la edición del usuario.
     * @param rawJson JSON crudo original del LLM (auditoría).
     * @param walletId wallet al que se aplica. Con wallet se hace la conciliación
     *  por `statement_line` (Fase 5); sin wallet (legado C1) solo audita/reconcilia
     *  saldos y MSI sin persistir líneas.
     * @param resolutions decisión final por movimiento (Vinculado/Nuevo/Ignorar).
     */
    suspend fun apply(
        statement: ParsedStatement,
        rawJson: String,
        walletId: String?,
        resolutions: List<LineResolution> = emptyList(),
    ): ApplyOutcome {
        var outcome = ApplyOutcome(0, 0, 0, 0, 0)
        db.withTransaction {
            val recon = reconcileAndAudit(statement, rawJson, walletId)
            outcome = if (walletId != null) {
                persistStatementLines(
                    statement = statement,
                    walletId = walletId,
                    importId = recon.importId,
                    resolutions = resolutions,
                    msiTouched = recon.msiTouched,
                )
            } else {
                // Sin wallet no hay conciliación por línea (statement_line.walletId
                // es NOT NULL): solo se reconcilió saldo/MSI y se auditó.
                ApplyOutcome(recon.msiTouched, 0, 0, 0, 0)
            }
        }
        return outcome
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
        // Categorías hoja por código (para mapear la categoriaSugerida del LLM).
        val leafByCode = categories.filter { it.parentId != null }.associateBy { it.code }
        val history = runCatching { expenseDao.getAll(householdId) }
            .getOrDefault(emptyList())
            .filter { it.status == "POSTED" }

        val members = activeHouseholdMembers()
        val payer = resolveDefaultPayer(members, wallet)
        // Índice nombre-sin-acentos → memberId para resolver beneficiariosSugeridos.
        val memberByName = members.associateBy { it.displayName.unaccent().lowercase() }

        val purchases = statement.movimientos
            .filter { (it.monto ?: 0.0) > 0.0 && !isPaymentMovement(it) }
            .map { mov ->
                val concepto = mov.concepto?.trim().orEmpty().ifBlank { "Compra tarjeta" }
                // Categoría: la sugerida por el LLM si matchea el catálogo; si no, la
                // heurística por historial.
                val llmCategoryId = mov.categoriaSugerida?.let { leafByCode[it.trim()]?.id }
                val categoryId = llmCategoryId ?: suggestCategory(concepto, history)
                // Beneficiarios: nombres del LLM → memberIds (los no resueltos se ignoran).
                val benIds = mov.beneficiariosSugeridos.orEmpty().mapNotNull { name ->
                    memberByName[name.unaccent().lowercase()]?.id
                }
                PlannedPurchase(
                    fecha = mov.fecha,
                    concepto = concepto,
                    montoMxn = mov.monto ?: 0.0,
                    esMsi = mov.esMsi && (mov.msiPlazo ?: 0) > 1,
                    suggestedCategoryId = categoryId,
                    suggestedCategoryName = categoryId?.let { categoriesById[it]?.displayName },
                    suggestedBeneficiaryIds = benIds,
                )
            }

        val aggregates = if (wallet != null) detectAggregates(statement, wallet) else emptyList()

        return RewritePlan(
            purchases = purchases,
            aggregates = aggregates,
            payerName = payer?.displayName,
            beneficiaryCount = members.size,
            members = members.map { RewriteMember(it.id, it.displayName) },
            categories = categories.filter { it.parentId != null }
                .map { RewriteMember(it.id, it.displayName) },
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
                    val allIds = members.map { it.id }
                    val payerBps = mapOf(payer.id to 10_000)
                    for (purchase in purchases) {
                        // Beneficiarios por compra: los ajustados con chips (o los del
                        // LLM); si ninguno, reparto equitativo entre todos.
                        val benIds = purchase.suggestedBeneficiaryIds
                            .filter { it in allIds }
                            .ifEmpty { allIds }
                        val beneficiaryBps = equalSplit(benIds)
                        val hadSuggestion = purchase.suggestedBeneficiaryIds.isNotEmpty()
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
                                // Confianza > 0 si el reparto vino del LLM/usuario (no el default equitativo).
                                confidence = if (hadSuggestion) 0.5 else 0.0,
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
            msiTouched = reconcileAndAudit(statement, rawJson, walletId).msiTouched
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
     * una transacción del caller. Devuelve el conteo de MSI tocados + el id del
     * import (para vincular las `statement_line` en [apply]).
     */
    private suspend fun reconcileAndAudit(
        statement: ParsedStatement,
        rawJson: String,
        walletId: String?,
    ): ReconcileResult {
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
        val importId = UUID.randomUUID().toString()
        val row = StatementImportEntity(
            id = importId,
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
        return ReconcileResult(msiTouched, importId)
    }

    /**
     * Paso (d) Fase 5: persiste cada movimiento como `statement_line` con su
     * decisión. El índice UNIQUE (wallet, fingerprint) hace el re-import
     * idempotente: una línea ya conciliada antes NO se duplica ni se vuelve a
     * encolar como Nueva. Los movimientos NEW van a la bandeja `pending_capture`
     * (propose-then-confirm) — NUNCA se inserta un gasto directo.
     */
    private suspend fun persistStatementLines(
        statement: ParsedStatement,
        walletId: String,
        importId: String,
        resolutions: List<LineResolution>,
        msiTouched: Int,
    ): ApplyOutcome {
        val now = System.currentTimeMillis()
        var linked = 0
        var queuedNew = 0
        var ignored = 0
        var duplicates = 0
        val byIndex = resolutions.associateBy { it.movementIndex }
        statement.movimientos.forEachIndexed { i, mov ->
            val res = byIndex[i] ?: LineResolution(i, status = "PENDING")
            val fingerprint = matcher.fingerprint(mov)
            val inserted = statementLineDao.insertIgnore(
                StatementLineEntity(
                    id = UUID.randomUUID().toString(),
                    householdId = householdId,
                    walletId = walletId,
                    importId = importId,
                    lineFingerprint = fingerprint,
                    postDate = mov.fecha,
                    description = mov.concepto.orEmpty().ifBlank { "Movimiento" },
                    descriptionCanonical = matcher.canonicalize(mov.concepto.orEmpty()),
                    amountMxn = mov.monto ?: 0.0,
                    matchStatus = res.status,
                    matchedExpenseId = res.matchedExpenseId,
                    matchConfidence = res.confidence,
                    matchSource = res.source,
                    createdAt = now,
                    updatedAt = now,
                )
            )
            if (inserted == -1L) {
                duplicates++
                return@forEachIndexed
            }
            when (res.status) {
                "MATCHED" -> linked++
                "IGNORED" -> ignored++
                "NEW" -> {
                    // Propose-then-confirm: el movimiento nuevo va a la bandeja
                    // de captura (Review); NUNCA se inserta un gasto directo.
                    pendingCaptureDao.insert(
                        PendingCaptureEntity(
                            id = "stmt:$fingerprint",
                            source = "STATEMENT",
                            amountMxn = mov.monto ?: 0.0,
                            concept = mov.concepto.orEmpty().ifBlank { "Movimiento del estado" },
                            occurredAt = mov.fecha
                                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                                ?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
                                ?: now,
                            suggestedWalletId = walletId,
                            status = "PENDING",
                            createdAt = now,
                        )
                    )
                    queuedNew++
                }
            }
        }
        return ApplyOutcome(msiTouched, linked, queuedNew, ignored, duplicates)
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
        // Plazo: del LLM, o inferido del concepto (caso Klar: "12 MSI"/"a 6 meses").
        val plazo = mov.msiPlazo ?: inferPlazoFromConcept(mov.concepto) ?: return false
        if (plazo <= 1) return false
        val concepto = mov.concepto?.trim().orEmpty().ifBlank { "MSI" }
        val last4 = statement.last4.orEmpty()
        val displayName = buildString {
            append(concepto)
            if (last4.isNotBlank()) append(" ****$last4")
        }.take(120)
        val monto = mov.monto ?: return false
        val normSelf = normalizeMsiConcept(concepto)

        // Match robusto: mismo wallet + concepto normalizado (sin "( n DE m )" ni
        // "PP####") + mismo plazo + cuota ±1% (tolera redondeos entre cortes).
        val match = existing.firstOrNull { plan ->
            plan.totalInstallments == plazo &&
                (walletId == null || plan.paymentMethodId == walletId) &&
                normalizeMsiConcept(plan.displayName) == normSelf &&
                (plan.installmentAmountMxn == 0.0 ||
                    kotlin.math.abs(plan.installmentAmountMxn - monto) <= monto * 0.01 + 0.5)
        }

        return if (match != null) {
            // Avanza solo si el estado trae un número mayor (guard anti-doble-avance
            // ante re-import del mismo estado).
            val current = (mov.msiNumero ?: match.currentInstallment).coerceIn(0, plazo)
            if (current > match.currentInstallment || statement.tasaAnual != null) {
                installmentRepository.update(
                    match.copy(
                        currentInstallment = current,
                        interestRateApr = statement.tasaAnual ?: match.interestRateApr,
                        // Vincula el wallet de la cuota si aún no lo tenía.
                        paymentMethodId = match.paymentMethodId ?: walletId,
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

    /** Normaliza el concepto de un MSI para el match: mayúsculas, sin "( n DE m )"
     * ni referencias "PP####", espacios colapsados. */
    private fun normalizeMsiConcept(s: String): String = s.uppercase()
        .replace(Regex("\\(\\s*\\d+\\s*DE\\s*\\d+\\s*\\)"), " ")
        .replace(Regex("\\bPP?\\d{3,}\\b"), " ")
        .replace(Regex("\\*+\\d{2,}"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    /** Infiere el plazo cuando el LLM no lo da (Klar: "12 MSI", "a 6 meses"). */
    private fun inferPlazoFromConcept(concepto: String?): Int? {
        val c = concepto?.uppercase() ?: return null
        return Regex("(\\d{1,2})\\s*(MSI|MESES|MENS)").find(c)?.groupValues?.get(1)?.toIntOrNull()
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
