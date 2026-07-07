package mx.budget.data.statements

import android.net.Uri
import mx.budget.data.local.dao.ExpenseDao
import mx.budget.data.local.dao.PendingCaptureDao
import mx.budget.data.local.dao.StatementImportDao
import mx.budget.data.local.dao.StatementLineDao
import mx.budget.data.local.entity.ExpenseEntity
import mx.budget.data.local.entity.InstallmentPlanEntity
import mx.budget.data.local.entity.PaymentMethodEntity
import mx.budget.data.local.entity.PendingCaptureEntity
import mx.budget.data.local.entity.StatementImportEntity
import mx.budget.data.local.entity.StatementLineEntity
import mx.budget.data.repository.InstallmentRepository
import mx.budget.data.repository.WalletRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Orquestador de la importación de estados de cuenta (Fase C, paquete C1).
 *
 * Une las tres piezas: extracción LOCAL de texto ([StatementTextExtractor]) →
 * análisis con LLM cloud ([NvidiaNimClient]) → reconciliación contra los repos
 * existentes ([WalletRepository], [InstallmentRepository]) + auditoría en
 * `statement_import`.
 *
 * INVARIANTE DE SEGURIDAD DE DATOS: aplicar un estado de cuenta **NUNCA reescribe
 * el historial** — no borra ni edita ninguno de los 793 gastos sembrados. Solo
 * añade claridad futura: ajusta día de corte / límite de pago / saldo / tasa /
 * límite de crédito del wallet, y crea/actualiza planes MSI. Todo lo demás queda
 * intacto.
 */
class StatementImportManager(
    private val extractor: StatementTextExtractor,
    private val nimClient: NvidiaNimClient,
    private val walletRepository: WalletRepository,
    private val installmentRepository: InstallmentRepository,
    private val statementImportDao: StatementImportDao,
    private val statementLineDao: StatementLineDao,
    private val expenseDao: ExpenseDao,
    private val pendingCaptureDao: PendingCaptureDao,
    private val householdId: String,
    private val matcher: StatementMatcher = StatementMatcher(),
) {

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
                append(Instant.ofEpochMilli(e.occurredAt).atZone(ZONE).toLocalDate())
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
            ?: dates.minOrNull() ?: LocalDate.now(ZONE).minusMonths(2)
        val end = statement.periodo?.fin?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: dates.maxOrNull() ?: LocalDate.now(ZONE)
        return start.minusDays(7).atStartOfDay(ZONE).toInstant().toEpochMilli() to
            end.plusDays(7).atTime(23, 59, 59).atZone(ZONE).toInstant().toEpochMilli()
    }

    private fun ExpenseEntity.toCandidate() = NvidiaNimClient.CandidateExpense(
        id = id,
        fecha = Instant.ofEpochMilli(occurredAt).atZone(ZONE).toLocalDate().toString(),
        monto = amountMxn,
        concepto = concept,
    )

    /**
     * Paso 3: **reconciliación** con los datos (posiblemente editados) del preview.
     * Solo añade claridad futura; no toca los gastos históricos — el vínculo
     * movimiento↔gasto vive en `statement_line`, JAMÁS se edita `expense`.
     *
     * @param statement datos finales tras la edición del usuario.
     * @param rawJson JSON crudo original del LLM (auditoría).
     * @param walletId wallet al que se aplica. Desde Fase 5 es OBLIGATORIO
     *  (la UI bloquea el apply sin wallet): sin él no hay conciliación posible.
     * @param resolutions decisión final por movimiento (Vinculado/Nuevo/Ignorar).
     */
    suspend fun apply(
        statement: ParsedStatement,
        rawJson: String,
        walletId: String,
        resolutions: List<LineResolution> = emptyList(),
    ): ApplyOutcome {
        var msiTouched = 0

        // (a) Reconcilia el wallet elegido — solo campos que existen en la entidad.
        walletRepository.getById(walletId)?.let { wallet ->
            walletRepository.update(reconcileWallet(wallet, statement))
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

        // (d) Fase 5: persiste cada movimiento como `statement_line` con su
        // decisión. El índice UNIQUE (wallet, fingerprint) hace el re-import
        // idempotente: una línea ya conciliada antes NO se duplica ni se
        // vuelve a encolar como Nueva.
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
                                ?.atStartOfDay(ZONE)?.toInstant()?.toEpochMilli()
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
        return runCatching { java.time.LocalDate.parse(iso).dayOfMonth }.getOrNull()
    }

    private fun today(): String = LocalDate.now(ZONE).toString()

    private companion object {
        val ZONE: ZoneId = ZoneId.of("America/Mexico_City")
    }
}
