package mx.budget.ui.statements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import mx.budget.data.local.dao.StatementImportDao
import mx.budget.data.repository.WalletRepository
import mx.budget.data.statements.StatementCycleStatus
import mx.budget.data.statements.StatementCycleTracker
import mx.budget.data.statements.WalletStatementStatus
import java.time.LocalDate
import java.time.ZoneId

/**
 * ViewModel del checklist "Estados del mes": combina los wallets activos con el
 * último estado importado por wallet y calcula, con [StatementCycleTracker], qué
 * tarjetas tienen su estado del ciclo importado / pendiente / sin corte conocido.
 */
class StatementsChecklistViewModel(
    walletRepository: WalletRepository,
    statementImportDao: StatementImportDao,
    householdId: String,
) : ViewModel() {

    private val zone = ZoneId.of("America/Mexico_City")

    val statuses: StateFlow<List<WalletStatementStatus>> =
        combine(
            walletRepository.observeActive(householdId),
            statementImportDao.observeLastImportEndByWallet(householdId),
        ) { wallets, lastImports ->
            val byWallet = lastImports.associate { it.walletId to parseIso(it.lastPeriodEnd) }
            StatementCycleTracker.compute(wallets, byWallet, LocalDate.now(zone))
                .sortedWith(
                    // Pendientes primero, luego por nombre.
                    compareBy({ it.status != StatementCycleStatus.PENDING }, { it.walletName })
                )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Cuántas tarjetas tienen su estado importado / total con corte conocido. */
    val progress: StateFlow<Pair<Int, Int>> = statuses
        .map { list ->
            val withCutoff = list.filter { it.status != StatementCycleStatus.NO_CUTOFF }
            val imported = withCutoff.count { it.status == StatementCycleStatus.IMPORTED }
            imported to withCutoff.size
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0 to 0)

    /** Siguiente wallet pendiente distinto de [excluding] (para encadenar imports). */
    fun nextPendingWalletId(excluding: String?): String? =
        statuses.value.firstOrNull {
            it.status == StatementCycleStatus.PENDING && it.walletId != excluding
        }?.walletId

    private fun parseIso(s: String?): LocalDate? =
        if (s.isNullOrBlank()) null else runCatching { LocalDate.parse(s.take(10)) }.getOrNull()
}
