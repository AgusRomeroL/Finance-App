package mx.budget.data.statements

import android.content.Context
import mx.budget.data.local.dao.StatementImportDao
import mx.budget.data.local.entity.StatementImportEntity
import mx.budget.data.repository.WalletRepository
import mx.budget.data.settings.SettingsRepository
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Sembrado único de "estados de cuenta ya importados" (Tarea 4).
 *
 * El asset embarcado (`budget_database.db`) es schema v1 y **no puede** contener la
 * tabla `statement_import` (nace por migración). Por eso, para que la BD de Norma
 * arranque con sus tarjetas ya marcadas como subidas (los estados reales mar-jun
 * 2026 que procesamos), se siembra en el PRIMER arranque, tras las migraciones:
 * por cada tarjeta del asset `assets/seed_statements.json` se fija el día de corte,
 * últimos 4, saldo y tasa reales, y se inserta una fila `statement_import` aplicada
 * cuyo `periodo_fin` cubre el ciclo vigente → el checklist "Estados del mes" las
 * muestra en verde.
 *
 * Idempotente (flag [SettingsRepository.isStatementSeedDone]); no-op si el asset no
 * existe (ramas sin el reseed de Norma). No toca gastos ni atribuciones.
 */
object StatementSeedInitializer {

    private const val ASSET = "seed_statements.json"

    suspend fun seedOnce(
        context: Context,
        settings: SettingsRepository,
        walletRepository: WalletRepository,
        statementImportDao: StatementImportDao,
        householdId: String,
    ) {
        if (settings.isStatementSeedDone()) return
        val json = runCatching { context.assets.open(ASSET).bufferedReader().use { it.readText() } }
            .getOrNull() ?: run {
                // Sin asset (rama sin reseed): marca hecho para no reintentar cada arranque.
                settings.setStatementSeedDone(true)
                return
            }

        val wallets = walletRepository.getActive(householdId)
        val byName = wallets.associateBy { it.displayName.trim().lowercase() }
        val today = LocalDate.now(ZoneId.of("America/Mexico_City")).toString()
        val now = System.currentTimeMillis()

        val arr = JSONObject(json).optJSONArray("statements") ?: return
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val wallet = byName[o.getString("wallet").trim().lowercase()] ?: continue
            val cutoff = o.optInt("cutoffDay").takeIf { o.has("cutoffDay") }
            val last4 = if (o.isNull("last4")) null else o.optString("last4", "").ifBlank { null }
            val saldo = if (o.isNull("saldo")) null else o.optDouble("saldo")
            val tasa = if (o.isNull("tasa")) null else o.optDouble("tasa")

            walletRepository.update(
                wallet.copy(
                    cutoffDay = cutoff ?: wallet.cutoffDay,
                    last4 = last4 ?: wallet.last4,
                    currentBalanceMxn = saldo ?: wallet.currentBalanceMxn,
                    interestApr = tasa ?: wallet.interestApr,
                    updatedAt = now,
                )
            )
            statementImportDao.insert(
                StatementImportEntity(
                    id = UUID.randomUUID().toString(),
                    householdId = householdId,
                    walletId = wallet.id,
                    emisor = o.optString("emisor", null),
                    last4 = last4,
                    periodoInicio = o.optString("periodoInicio", null),
                    periodoFin = today,
                    fechaCorte = today,
                    fechaLimitePago = null,
                    saldoTotal = saldo,
                    pagoMinimo = null,
                    pagoNoIntereses = null,
                    tasaAnual = tasa,
                    payloadJson = "{}",
                    createdAt = now,
                    appliedAt = now,
                )
            )
        }
        settings.setStatementSeedDone(true)
    }
}
