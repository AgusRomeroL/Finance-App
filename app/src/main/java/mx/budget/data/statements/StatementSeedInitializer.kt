package mx.budget.data.statements

import android.content.Context
import mx.budget.core.unaccent
import mx.budget.data.local.dao.ExpenseDao
import mx.budget.data.local.dao.MemberDao
import mx.budget.data.local.dao.QuincenaDao
import mx.budget.data.local.dao.StatementImportDao
import mx.budget.data.local.entity.CategoryEntity
import mx.budget.data.local.entity.ExpenseAttributionEntity
import mx.budget.data.local.entity.ExpenseEntity
import mx.budget.data.local.entity.InstallmentPlanEntity
import mx.budget.data.local.entity.StatementImportEntity
import mx.budget.data.installments.InstallmentSchedule
import mx.budget.data.local.entity.WalletTransferEntity
import mx.budget.data.repository.CategoryRepository
import mx.budget.data.repository.ExpenseRepository
import mx.budget.data.repository.InstallmentRepository
import mx.budget.data.repository.TransferRepository
import mx.budget.data.repository.WalletRepository
import mx.budget.data.settings.SettingsRepository
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/**
 * Sembrado histórico v2 de estados de cuenta (Tarea "estados v2").
 *
 * El asset embarcado es schema v1 (sin `statement_import`, sin gastos itemizados de
 * tarjeta), así que en el PRIMER arranque (post-migración, idempotente) se materializa
 * el histórico REAL mar–jun 2026 desde `assets/seed_statements.json` v2:
 *  - crea categorías nuevas (CUSTOM.*), wallets nuevas (DiDi Card) y planes MSI con avance;
 *  - por estado: convierte el pago agregado del Excel en transferencia banco→tarjeta,
 *    inserta las compras clasificadas (categoría + beneficiarios) como gastos POSTED,
 *    los intereses/comisiones como gasto con `installment_interest_mxn` (alimenta el
 *    widget de intereses), y omite lo que ya existe en el Excel (anti-doble-conteo);
 *  - deja el checklist "Estados del mes" en verde (marcador de ciclo) + auditoría real;
 *  - fija saldos absolutos por wallet (reconcileFinal) y recalcula el snapshot de cada
 *    quincena tocada.
 *
 * Todo con **IDs deterministas** (convergencia multi-dispositivo) y vía repos públicos
 * (sync). No-op si el asset no existe (ramas sin el reseed de Norma).
 */
class StatementSeedInitializer(
    private val context: Context,
    private val settings: SettingsRepository,
    private val householdId: String,
    private val categoryRepository: CategoryRepository,
    private val walletRepository: WalletRepository,
    private val installmentRepository: InstallmentRepository,
    private val expenseRepository: ExpenseRepository,
    private val transferRepository: TransferRepository,
    private val expenseDao: ExpenseDao,
    private val memberDao: MemberDao,
    private val quincenaDao: QuincenaDao,
    private val statementImportDao: StatementImportDao,
) {
    private val zone = ZoneId.of("America/Mexico_City")

    suspend fun seedOnce() {
        if (settings.isStatementSeedV2Done()) return
        val text = runCatching {
            context.assets.open(ASSET).bufferedReader().use { it.readText() }
        }.getOrNull() ?: run {
            // Sin asset (rama sin reseed): marca hecho para no reintentar.
            settings.setStatementSeedV2Done(true)
            return
        }
        val root = runCatching { JSONObject(text) }.getOrNull() ?: run {
            settings.setStatementSeedV2Done(true); return
        }

        // Migración del sembrado v1: borra sus 6 filas sintéticas (checklist verde falso).
        if (settings.isStatementSeedDone()) {
            runCatching { statementImportDao.deleteSyntheticV1(householdId) }
        }

        val now = System.currentTimeMillis()
        val today = LocalDate.now(zone).toString()
        val touchedQuincenas = HashSet<String>()

        // Mapa member-key → memberId (por displayName sin acentos).
        val members = memberDao.getActiveMembers(householdId)
        val keyToMember = HashMap<String, String>()
        root.optJSONObject("miembros")?.let { mm ->
            for (k in mm.keys()) {
                val name = mm.getString(k)
                members.firstOrNull { it.displayName.unaccent().equals(name.unaccent(), true) }
                    ?.let { keyToMember[k] = it.id }
            }
        }
        val normaId = keyToMember["norma"] ?: members.firstOrNull {
            it.displayName.unaccent().equals("norma", true)
        }?.id

        // (1) Categorías nuevas.
        root.optJSONArray("categoriasNuevas")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val code = o.getString("code")
                if (categoryRepository.getByCode(householdId, code) == null) {
                    categoryRepository.insert(
                        CategoryEntity(
                            id = det("seedcat2:$code"),
                            householdId = householdId,
                            parentId = null,
                            code = code,
                            displayName = o.optString("displayName", code),
                            kind = o.optString("kind", "EXPENSE_VARIABLE"),
                            sortOrder = 900,
                        )
                    )
                }
            }
        }
        // code → id (cache; se resuelve on-demand vía getByCode).
        val catByCode = HashMap<String, String>()
        suspend fun categoryId(code: String?): String? {
            if (code.isNullOrBlank()) return null
            catByCode[code]?.let { return it }
            val id = categoryRepository.getByCode(householdId, code)?.id ?: return null
            catByCode[code] = id
            return id
        }

        // (2) Wallets nuevas (DiDi Card).
        root.optJSONArray("walletsNuevas")?.let { arr ->
            val existing = walletRepository.getActive(householdId)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val name = o.getString("displayName")
                if (existing.any { it.displayName.equals(name, true) }) continue
                walletRepository.insert(
                    mx.budget.data.local.entity.PaymentMethodEntity(
                        id = det("seedw2:${o.getString("key")}"),
                        householdId = householdId,
                        displayName = name,
                        kind = o.optString("kind", "CREDIT_CARD"),
                        issuer = o.optStringOrNull("issuer"),
                        last4 = o.optStringOrNull("last4")?.ifBlank { null },
                        cutoffDay = o.optIntOrNull("cutoffDay"),
                        dueDay = o.optIntOrNull("dueDay"),
                        creditLimitMxn = o.optDoubleOrNull("creditLimitMxn"),
                        currentBalanceMxn = o.optDouble("openingBalanceMxn", 0.0),
                        openingBalanceMxn = o.optDouble("openingBalanceMxn", 0.0),
                        interestApr = o.optDoubleOrNull("interestApr"),
                        ownerMemberId = o.optStringOrNull("owner")?.let { keyToMember[it] },
                        updatedAt = now,
                    )
                )
            }
        }
        // Mapa display_name → wallet id (incluye las nuevas).
        val walletByName = walletRepository.getActive(householdId)
            .associateBy { it.displayName.trim().lowercase() }
        fun walletId(name: String?): String? =
            name?.let { walletByName[it.trim().lowercase()]?.id }

        // (3) Planes MSI consolidados.
        root.optJSONArray("planesMsi")?.let { arr ->
            val existing = installmentRepository.getActive(householdId).map { it.id }.toHashSet()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val planKey = o.getString("planKey")
                val id = det("seedmsi:$planKey")
                if (id in existing) continue
                val plazo = o.getInt("plazo")
                val cuota = o.getDouble("montoCuotaMxn")
                val pagadas = o.optInt("cuotasPagadas", 0).coerceIn(0, plazo)
                installmentRepository.insert(
                    InstallmentPlanEntity(
                        id = id,
                        householdId = householdId,
                        displayName = o.optString("displayName", planKey),
                        paymentMethodId = walletId(o.optStringOrNull("wallet")),
                        principalMxn = cuota * plazo,
                        totalInstallments = plazo,
                        installmentAmountMxn = cuota,
                        interestRateApr = o.optDoubleOrNull("aprPct"),
                        startDate = o.optString("primeraCuota", today),
                        currentInstallment = pagadas,
                        status = if (pagadas >= plazo) "PAID_OFF" else o.optString("status", "ACTIVE"),
                        categoryId = categoryId(o.optStringOrNull("categoria")),
                    )
                )
            }
        }

        // (5) Por estado, en orden cronológico por periodo.fin.
        val statements = root.optJSONArray("statements")
        val ordered = (0 until (statements?.length() ?: 0))
            .map { statements!!.getJSONObject(it) }
            .sortedBy { it.optJSONObject("periodo")?.optString("fin") ?: "" }
        for (st in ordered) {
            val key = st.getString("key")
            val stmtId = det("seedst2:$key")
            if (statementImportDao.getById(stmtId) != null) continue // gate idempotente

            val cardWalletId = walletId(st.optStringOrNull("wallet"))

            // (a) Pagos agregados → transferencia banco→tarjeta (borra el gasto Excel).
            st.optJSONArray("pagosAgregados")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val p = arr.getJSONObject(i)
                    val sm = p.optJSONObject("seedMatch")
                    val fromId = walletId(p.optString("origenWallet", "BBVA")) ?: cardWalletId
                    val monto = p.optDouble("montoTotal", sm?.optDouble("monto", 0.0) ?: 0.0)
                    // Localiza el gasto agregado del Excel y bórralo (revierte saldo + sync DELETE).
                    if (sm != null) {
                        val target = findSeedExpense(sm)
                        if (target != null) expenseRepository.deleteAndRevertBalance(target.id)
                    }
                    if (fromId != null && cardWalletId != null && fromId != cardWalletId && monto > 0) {
                        transferRepository.recordTransfer(
                            WalletTransferEntity(
                                id = det("seedtr2:$key:$i"),
                                householdId = householdId,
                                fromPaymentMethodId = fromId,
                                toPaymentMethodId = cardWalletId,
                                amountMxn = monto,
                                occurredAt = epoch(p.optStringOrNull("fecha")) ?: now,
                                note = "Pago de tarjeta (histórico): ${st.optString("wallet", "")}".take(120),
                                createdAt = now,
                            )
                        )
                    }
                }
            }

            // (b) Movimientos NEW → gastos POSTED.
            val movs = st.optJSONArray("movimientos")
            for (i in 0 until (movs?.length() ?: 0)) {
                val m = movs!!.getJSONObject(i)
                if (!m.optString("resolucion", "NEW").equals("NEW", true)) continue // MATCHED_SEED → skip
                val monto = m.optDoubleOrNull("monto") ?: continue
                if (monto <= 0) continue
                val fecha = m.optStringOrNull("fecha")
                val q = fecha?.let { quincenaDao.getForDate(householdId, it) } ?: continue
                val catId = categoryId(m.optStringOrNull("categoria")) ?: continue
                val cw = cardWalletId ?: continue // sin wallet no se puede cargar el gasto
                val expId = det("seedmv2:$key:$i")
                val tipo = m.optString("tipo", "COMPRA").uppercase()
                val isFinance = tipo == "INTERES" || tipo == "IVA" || tipo == "COMISION"
                val benKeys = m.optJSONArray("beneficiarios")?.let { ba ->
                    (0 until ba.length()).mapNotNull { keyToMember[ba.getString(it)] }
                }.orEmpty()
                val benIds = benKeys.ifEmpty { listOfNotNull(normaId) }
                val msi = m.optJSONObject("msi")
                val planId = msi?.optStringOrNull("planKey")?.let { det("seedmsi:$it") }
                val expense = ExpenseEntity(
                    id = expId,
                    householdId = householdId,
                    occurredAt = epoch(fecha) ?: now,
                    quincenaId = q.id,
                    categoryId = catId,
                    concept = m.optString("concepto", "Movimiento").take(64),
                    amountMxn = monto,
                    paymentMethodId = cw,
                    installmentPlanId = planId,
                    installmentNumber = msi?.optIntOrNull("numero"),
                    installmentPrincipalMxn = if (isFinance) 0.0 else null,
                    installmentInterestMxn = if (isFinance) monto else null,
                    status = "POSTED",
                    notes = "Estado de cuenta ${st.optString("wallet", "")} $key",
                    createdAt = now,
                )
                val benBps = equalSplit(benIds)
                val attrs = buildAttrs(expId, benBps, "BENEFICIARY", monto) +
                    buildAttrs(expId, mapOf((normaId ?: benIds.first()) to 10000), "PAYER", monto)
                runCatching { expenseRepository.insertWithAttributions(expense, attrs) }
                touchedQuincenas += q.id
            }

            // (d) Fila de auditoría real del estado.
            statementImportDao.insert(
                StatementImportEntity(
                    id = stmtId,
                    householdId = householdId,
                    walletId = cardWalletId,
                    emisor = st.optStringOrNull("emisor"),
                    last4 = st.optStringOrNull("last4")?.ifBlank { null },
                    periodoInicio = st.optJSONObject("periodo")?.optStringOrNull("inicio"),
                    periodoFin = st.optJSONObject("periodo")?.optStringOrNull("fin"),
                    fechaCorte = st.optStringOrNull("fechaCorte"),
                    fechaLimitePago = st.optStringOrNull("fechaLimitePago"),
                    saldoTotal = st.optDoubleOrNull("saldoTotal"),
                    pagoMinimo = st.optDoubleOrNull("pagoMinimo"),
                    pagoNoIntereses = st.optDoubleOrNull("pagoNoIntereses"),
                    tasaAnual = st.optDoubleOrNull("tasaAnual"),
                    payloadJson = "{\"seed\":\"v2\"}",
                    createdAt = now,
                    appliedAt = now,
                )
            )
        }

        // (6) reconcileFinal: saldos absolutos + metadatos + marcador de ciclo (verde).
        root.optJSONArray("reconcileFinal")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val w = walletRepository.getActive(householdId)
                    .firstOrNull { it.displayName.equals(o.getString("wallet"), true) } ?: continue
                walletRepository.update(
                    w.copy(
                        last4 = o.optStringOrNull("last4")?.ifBlank { null } ?: w.last4,
                        cutoffDay = o.optIntOrNull("cutoffDay") ?: w.cutoffDay,
                        dueDay = o.optIntOrNull("dueDay") ?: w.dueDay,
                        creditLimitMxn = o.optDoubleOrNull("limiteMxn") ?: w.creditLimitMxn,
                        currentBalanceMxn = o.optDoubleOrNull("saldo") ?: w.currentBalanceMxn,
                        interestApr = o.optDoubleOrNull("tasa") ?: w.interestApr,
                        updatedAt = now,
                    )
                )
                // Marcador de ciclo vigente (checklist en verde) por tarjeta de crédito.
                if (StatementCycleTracker.isStatementCard(w.kind) && w.cutoffDay != null) {
                    statementImportDao.insert(
                        StatementImportEntity(
                            id = det("seedcycle2:${w.id}"),
                            householdId = householdId,
                            walletId = w.id,
                            emisor = o.optStringOrNull("emisor"),
                            last4 = w.last4,
                            periodoInicio = null,
                            periodoFin = today,
                            fechaCorte = today,
                            fechaLimitePago = null, // marcador, no una fila de deuda real
                            saldoTotal = o.optDoubleOrNull("saldo"),
                            pagoMinimo = null,
                            pagoNoIntereses = null,
                            tasaAnual = o.optDoubleOrNull("tasa"),
                            payloadJson = "{}",
                            createdAt = now,
                            appliedAt = now,
                        )
                    )
                }
            }
        }

        // (7) Recalcular snapshots de las quincenas tocadas.
        for (qid in touchedQuincenas) {
            runCatching { quincenaDao.recalcActualExpenses(qid, now) }
        }

        settings.setStatementSeedV2Done(true)
        settings.setStatementSeedDone(true)
    }

    /** Localiza el gasto agregado del Excel por (quincena, monto ±0.01, concepto). */
    private suspend fun findSeedExpense(seedMatch: JSONObject): ExpenseEntity? {
        val quincena = seedMatch.optStringOrNull("quincena")
        val monto = seedMatch.optDouble("monto", -1.0)
        val concepto = seedMatch.optString("concepto", "").uppercase()
        return expenseDao.getAll(householdId).firstOrNull { e ->
            (quincena == null || e.quincenaId == quincena) &&
                kotlin.math.abs(e.amountMxn - monto) <= 0.01 &&
                e.concept.uppercase().contains(concepto.take(6))
        }
    }

    private fun buildAttrs(
        expenseId: String,
        bps: Map<String, Int>,
        role: String,
        amount: Double,
    ): List<ExpenseAttributionEntity> = bps.map { (memberId, share) ->
        ExpenseAttributionEntity(
            id = det("attr2:$expenseId:$role:$memberId"),
            expenseId = expenseId,
            memberId = memberId,
            role = role,
            shareBps = share,
            shareAmountMxn = kotlin.math.round(amount * share / 10000.0 * 100) / 100.0,
        )
    }

    private fun equalSplit(ids: List<String>): Map<String, Int> {
        if (ids.isEmpty()) return emptyMap()
        val base = 10000 / ids.size
        val rem = 10000 - base * ids.size
        return ids.mapIndexed { i, id -> id to (base + if (i == 0) rem else 0) }.toMap()
    }

    private fun epoch(iso: String?): Long? =
        InstallmentSchedule.parseIso(iso)?.atTime(12, 0)?.toInstant(ZoneOffset.UTC)?.toEpochMilli()

    private fun det(s: String): String = UUID.nameUUIDFromBytes(s.toByteArray()).toString()

    companion object {
        private const val ASSET = "seed_statements.json"
    }
}

// Helpers org.json para nullables.
private fun JSONObject.optIntOrNull(key: String): Int? = if (has(key) && !isNull(key)) optInt(key) else null
private fun JSONObject.optDoubleOrNull(key: String): Double? = if (has(key) && !isNull(key)) optDouble(key) else null
private fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) getString(key) else null
