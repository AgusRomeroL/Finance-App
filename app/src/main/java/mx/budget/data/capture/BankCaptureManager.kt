package mx.budget.data.capture

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import mx.budget.R
import mx.budget.ai.proactive.ParsedCharge
import mx.budget.ai.proactive.RetroAttributionEngine
import mx.budget.data.local.dao.CategoryDao
import mx.budget.data.local.dao.ExpenseDao
import mx.budget.data.local.dao.MemberDao
import mx.budget.data.local.dao.PaymentMethodDao
import mx.budget.data.local.dao.PendingBankCaptureDao
import mx.budget.data.local.entity.ExpenseAttributionEntity
import mx.budget.data.local.entity.ExpenseEntity
import mx.budget.data.local.entity.PendingBankCaptureEntity
import mx.budget.data.repository.ExpenseRepository
import mx.budget.data.repository.QuincenaRepository
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
    private val pendingDao: PendingBankCaptureDao,
    private val paymentMethodDao: PaymentMethodDao,
    private val categoryDao: CategoryDao,
    private val expenseDao: ExpenseDao,
    private val memberDao: MemberDao,
    private val quincenaRepository: QuincenaRepository,
    private val expenseRepository: ExpenseRepository,
    private val engine: RetroAttributionEngine,
    private val householdId: String,
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

        val capture = PendingBankCaptureEntity(
            id = UUID.randomUUID().toString(),
            bankId = parsed.bankId,
            bankName = parsed.bankName,
            bankPackage = parsed.bankPackage,
            amountMxn = parsed.amountMxn,
            merchant = parsed.merchant,
            last4 = parsed.last4,
            occurredAt = parsed.occurredAt,
            suggestedWalletId = walletId,
            suggestedCategoryId = categoryId,
            status = "PENDING",
            createdAt = System.currentTimeMillis(),
        )
        pendingDao.insert(capture)
        postNotification(capture)
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

        // Atribución inferida por el comercio; fallback conservador y editable.
        val payerBps = engine.suggest(row.merchant, "PAYER")?.distribution
            ?: ownerId?.let { mapOf(it to 10_000) } ?: emptyMap()
        val beneficiaryBps = engine.suggest(row.merchant, "BENEFICIARY")?.distribution
            ?: equalSplit(members.map { it.id }).ifEmpty { payerBps }

        if (payerBps.isEmpty() || beneficiaryBps.isEmpty()) return

        val expenseId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val expense = ExpenseEntity(
            id = expenseId,
            householdId = householdId,
            occurredAt = row.occurredAt,
            quincenaId = quincena.id,
            categoryId = categoryId,
            concept = row.merchant.take(64),
            amountMxn = row.amountMxn,
            paymentMethodId = walletId,
            status = "POSTED",
            createdAt = now,
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

    /** Categoría modal entre gastos históricos del mismo comercio; fallback a "otros"/primera. */
    private suspend fun resolveCategory(merchant: String): String? {
        val key = merchant.trim().lowercase()
        if (key.isNotEmpty()) {
            val history = runCatching { expenseDao.getAll(householdId) }.getOrDefault(emptyList())
            val modal = history
                .filter { it.status == "POSTED" && it.concept.lowercase().contains(key) }
                .groupingBy { it.categoryId }.eachCount()
                .maxByOrNull { it.value }?.key
            if (modal != null) return modal
        }
        val categories = categoryDao.getAll(householdId)
        val misc = categories.firstOrNull { c ->
            val s = "${c.displayName} ${c.code}".lowercase()
            listOf("otro", "vari", "sin categor", "misc", "general").any { it in s }
        }
        return misc?.id ?: categories.firstOrNull()?.id
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

    private fun postNotification(capture: PendingBankCaptureEntity) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val money = NumberFormat.getCurrencyInstance(Locale("es", "MX")).format(capture.amountMxn)
        val confirmPi = actionIntent(capture.id, BankCaptureActions.CONFIRM)
        val dismissPi = actionIntent(capture.id, BankCaptureActions.DISMISS)

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("${capture.bankName}: $money en ${capture.merchant}")
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

    companion object {
        const val CHANNEL_ID = "bank_capture"

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
