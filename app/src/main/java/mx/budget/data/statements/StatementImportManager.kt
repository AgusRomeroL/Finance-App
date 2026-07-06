package mx.budget.data.statements

import android.net.Uri
import mx.budget.data.local.dao.StatementImportDao
import mx.budget.data.local.entity.InstallmentPlanEntity
import mx.budget.data.local.entity.PaymentMethodEntity
import mx.budget.data.local.entity.StatementImportEntity
import mx.budget.data.repository.InstallmentRepository
import mx.budget.data.repository.WalletRepository
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
    private val householdId: String,
) {

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
     * Paso 3: **reconciliación** con los datos (posiblemente editados) del preview.
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
        return runCatching { java.time.LocalDate.parse(iso).dayOfMonth }.getOrNull()
    }

    private fun today(): String =
        java.time.LocalDate.now(java.time.ZoneId.of("America/Mexico_City")).toString()
}
