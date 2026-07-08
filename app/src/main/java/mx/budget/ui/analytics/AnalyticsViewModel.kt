package mx.budget.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
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

    /** Periodo de agregación de la dona por miembro (default: histórico = todo). */
    private val _memberPeriod = MutableStateFlow(MemberPeriod.HISTORICO)
    val memberPeriod: StateFlow<MemberPeriod> = _memberPeriod.asStateFlow()

    /** Cambia el periodo de agregación de la dona "Distribución por miembro". */
    fun onMemberPeriodSelected(period: MemberPeriod) {
        _memberPeriod.value = period
    }

    /**
     * Distribución del gasto por MIEMBRO (rol BENEFICIARY = quién consume) para el
     * [MemberPeriod] seleccionado. Reutiliza la agregación de atribuciones
     * (`share_amount_mxn` sobre gastos POSTED, `display_name` por JOIN — sin mapeo
     * manual member_id→nombre): para HISTORICO/ANUAL/MENSUAL usa un rango de fechas
     * del hogar (`observeSpendByMemberRange`), y para QUINCENAL el rango
     * `start_date..end_date` de la quincena activa. Alimenta la dona "Distribución
     * por miembro" y su total central. El rango se calcula en `America/Mexico_City`.
     */
    val spendByMember: StateFlow<List<SpendByMember>> =
        combine(_memberPeriod, activeQuincena) { period, q -> period to q }
            .flatMapLatest { (period, q) ->
                val range = memberRangeMs(period, q)
                if (range == null) flowOf(emptyList())
                else expenseRepository.observeSpendByMemberRange(
                    householdId, "BENEFICIARY", range.first, range.second
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Rango [startMs, endMs] (epoch millis, zona México) del [period]. Devuelve
     * null solo cuando QUINCENAL no tiene quincena activa (dona vacía). HISTORICO
     * abarca todo (0..Long.MAX_VALUE).
     */
    private fun memberRangeMs(period: MemberPeriod, quincena: QuincenaEntity?): Pair<Long, Long>? {
        val zone = ZoneId.of("America/Mexico_City")
        fun startMs(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
        // Fin exclusivo → inclusivo: primer instante del día siguiente menos 1 ms.
        fun endMs(exclusiveDay: LocalDate) = startMs(exclusiveDay) - 1
        return when (period) {
            MemberPeriod.HISTORICO -> 0L to Long.MAX_VALUE
            MemberPeriod.ANUAL -> {
                val today = LocalDate.now(zone)
                val start = LocalDate.of(today.year, 1, 1)
                startMs(start) to endMs(start.plusYears(1))
            }
            MemberPeriod.MENSUAL -> {
                val today = LocalDate.now(zone)
                val start = today.withDayOfMonth(1)
                startMs(start) to endMs(start.plusMonths(1))
            }
            MemberPeriod.QUINCENAL -> {
                if (quincena == null) return null
                val start = runCatching { LocalDate.parse(quincena.startDate) }.getOrNull() ?: return null
                val end = runCatching { LocalDate.parse(quincena.endDate) }.getOrNull() ?: return null
                startMs(start) to endMs(end.plusDays(1))
            }
        }
    }

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

/**
 * Periodo de agregación de la dona "Distribución por miembro" (Analíticas).
 * Cada valor lleva su etiqueta en español para las pills.
 */
enum class MemberPeriod(val label: String) {
    HISTORICO("Histórico"),
    ANUAL("Anual"),
    MENSUAL("Mensual"),
    QUINCENAL("Quincenal"),
}
