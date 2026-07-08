package mx.budget.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import mx.budget.data.local.result.InterestByWallet
import mx.budget.data.local.result.QuincenaSnapshot
import mx.budget.data.local.result.SpendByCategory
import mx.budget.data.local.result.SpendByMember
import mx.budget.data.local.result.TopConcept
import mx.budget.data.local.result.WalletBalanceInfo
import mx.budget.data.local.dao.AnalyticsDao
import mx.budget.data.local.entity.QuincenaEntity
import mx.budget.data.repository.AnalyticsRepository
import mx.budget.data.repository.ExpenseRepository
import mx.budget.data.repository.IncomeRepository
import mx.budget.data.repository.InstallmentRepository
import mx.budget.data.repository.LoanRepository
import mx.budget.data.repository.QuincenaRepository
import mx.budget.data.repository.SavingsRepository

/**
 * ViewModel de la pantalla Analíticas (MVP Fase 3).
 *
 * Solo lectura: compone las agregaciones del [AnalyticsRepository] (módulos
 * A/C/E), la tendencia de quincenas cerradas y los KPIs de la hoja de balance
 * (ahorro, MSI comprometido, por cobrar). Todo scoped a la quincena activa.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModel(
    private val analyticsRepository: AnalyticsRepository,
    private val analyticsDao: AnalyticsDao,
    private val expenseRepository: ExpenseRepository,
    quincenaRepository: QuincenaRepository,
    private val incomeRepository: IncomeRepository,
    savingsRepository: SavingsRepository,
    installmentRepository: InstallmentRepository,
    loanRepository: LoanRepository,
    private val householdId: String,
) : ViewModel() {

    val activeQuincena: StateFlow<QuincenaEntity?> =
        quincenaRepository.observeActive(householdId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Módulo A: presupuesto vs ejecutado por categoría (quincena activa). */
    val spendByCategory: StateFlow<List<SpendByCategory>> =
        activeQuincena.flatMapLatest { q ->
            if (q == null) flowOf(emptyList())
            else analyticsRepository.observeSpendByCategory(householdId, q.id)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Distribución del gasto por MIEMBRO (rol BENEFICIARY = quién consume) de la
     * quincena activa. Reutiliza el mismo flujo que alimenta las barras por miembro
     * del dashboard (`ExpenseAttributionDao.observeSpendByMember(quincenaId, role)`,
     * agrega `share_amount_mxn` sobre gastos POSTED y trae `display_name` por JOIN,
     * así que no requiere mapeo manual member_id→nombre). Alimenta la dona
     * "Distribución por miembro".
     */
    val spendByMember: StateFlow<List<SpendByMember>> =
        activeQuincena.flatMapLatest { q ->
            if (q == null) flowOf(emptyList())
            else expenseRepository.observeSpendByMember(q.id)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Ingreso RECIBIDO (POSTED) en vivo de la quincena activa — como el dashboard.
     * Las columnas agregadas de quincena (`actualIncomeMxn`) no se mantienen y
     * llegan en 0; se usa esto para el ingreso "recibido" junto al proyectado.
     */
    val postedIncome: StateFlow<Double> =
        activeQuincena.flatMapLatest { q ->
            if (q == null) flowOf(0.0) else incomeRepository.observePostedTotal(q.id)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    /** Tendencia: últimas quincenas cerradas (ingreso/gasto real). */
    val trend: StateFlow<List<QuincenaSnapshot>> =
        quincenaRepository.observeClosedSnapshots(householdId, TREND_N)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Top conceptos de la quincena activa (clave canónica). */
    val topConcepts: StateFlow<List<TopConcept>> =
        activeQuincena.flatMapLatest { q ->
            if (q == null) flowOf(emptyList())
            else flow { emit(analyticsDao.getTopConcepts(q.id, TOP_N)) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Módulo E: concentración de deuda revolvente. */
    val debtConcentration: StateFlow<List<WalletBalanceInfo>> =
        activeQuincena.flatMapLatest {
            flow { emit(analyticsRepository.getDebtConcentration(householdId)) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Módulo C: intereses pagados en los últimos 90 días. */
    val interestByWallet: StateFlow<List<InterestByWallet>> =
        activeQuincena.flatMapLatest {
            flow {
                val now = System.currentTimeMillis()
                emit(analyticsRepository.getInterestByWallet(householdId, now - NINETY_DAYS_MS, now))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── KPIs de hoja de balance ──────────────────────────────────────────────

    val totalSavings: StateFlow<Double> =
        savingsRepository.observeTotalSavings(householdId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    val totalCommitment: StateFlow<Double> =
        installmentRepository.observeTotalCommitment(householdId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    val totalReceivable: StateFlow<Double> =
        loanRepository.observeTotalReceivable(householdId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    private companion object {
        const val TREND_N = 8
        const val TOP_N = 10
        const val NINETY_DAYS_MS = 90L * 24 * 60 * 60 * 1000
    }
}
