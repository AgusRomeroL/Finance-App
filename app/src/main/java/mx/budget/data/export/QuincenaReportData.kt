package mx.budget.data.export

import kotlinx.coroutines.flow.first
import mx.budget.data.local.dao.CategoryDao
import mx.budget.data.local.dao.ExpenseAttributionDao
import mx.budget.data.local.dao.MemberDao
import mx.budget.data.local.entity.CategoryEntity
import mx.budget.data.local.entity.InstallmentPlanEntity
import mx.budget.data.local.entity.LoanEntity
import mx.budget.data.local.entity.MemberEntity
import mx.budget.data.local.entity.QuincenaEntity
import mx.budget.data.local.result.ExpenseWithDetails
import mx.budget.data.local.result.IncomeByMember
import mx.budget.data.local.result.PendingReimbursementByPayer
import mx.budget.data.local.result.SpendByCategory
import mx.budget.data.local.result.SpendByMember
import mx.budget.data.local.result.WalletBalanceInfo
import mx.budget.data.quincena.QuincenaFigures
import mx.budget.data.quincena.quincenaFigures
import mx.budget.data.repository.AnalyticsRepository
import mx.budget.data.repository.ExpenseRepository
import mx.budget.data.repository.IncomeRepository
import mx.budget.data.repository.InstallmentRepository
import mx.budget.data.repository.LoanRepository
import mx.budget.data.repository.QuincenaRepository
import mx.budget.data.repository.WalletRepository

/** Todo lo que el reporte de una quincena necesita, leido una sola vez. */
data class QuincenaReport(
    val quincena: QuincenaEntity,
    val figures: QuincenaFigures,
    val movimientos: List<ExpenseWithDetails>,
    val ingresosPorMiembro: List<IncomeByMember>,
    val porCategoria: List<SpendByCategory>,
    val porBeneficiario: List<SpendByMember>,
    val porPagador: List<SpendByMember>,
    val porReembolsar: List<PendingReimbursementByPayer>,
    val prestamos: List<LoanEntity>,
    val planesMsi: List<InstallmentPlanEntity>,
    val saldos: List<WalletBalanceInfo>,
    val miembros: List<MemberEntity>,
    val categorias: List<CategoryEntity>,
    val atribuciones: Map<String, List<AtribucionResumen>>,
) {
    val ejecutados: List<ExpenseWithDetails> get() = movimientos.filter { it.status == "POSTED" }
    val planeados: List<ExpenseWithDetails> get() = movimientos.filter { it.status == "PLANNED" }

    fun nombreMiembro(id: String): String =
        miembros.firstOrNull { it.id == id }?.displayName ?: "Sin nombre"

    /** Grupo raiz de una categoria hoja, o la propia si ya es raiz. */
    fun grupoDe(categoryId: String): String {
        val categoria = categorias.firstOrNull { it.id == categoryId } ?: return "Sin grupo"
        val padre = categoria.parentId ?: return categoria.displayName
        return categorias.firstOrNull { it.id == padre }?.displayName ?: categoria.displayName
    }
}

/** Reparto de un gasto en una dimension, ya resuelto a nombres. */
data class AtribucionResumen(val role: String, val memberId: String, val shareBps: Int)

/**
 * Reune el reporte de una quincena. Todas las lecturas son one-shot: un reporte
 * es una foto, no una pantalla que se actualiza sola.
 */
class QuincenaReportBuilder(
    private val quincenaRepository: QuincenaRepository,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val walletRepository: WalletRepository,
    private val loanRepository: LoanRepository,
    private val installmentRepository: InstallmentRepository,
    private val attributionDao: ExpenseAttributionDao,
    private val memberDao: MemberDao,
    private val categoryDao: CategoryDao,
    private val householdId: String,
) {

    suspend fun build(quincenaId: String): QuincenaReport? {
        val quincena = quincenaRepository.getById(quincenaId) ?: return null
        val movimientos = expenseRepository.observeWithDetails(quincenaId).first()
        val ejecutado = movimientos.filter { it.status == "POSTED" }.sumOf { it.amountMxn }
        val reservado = movimientos.filter { it.status == "PLANNED" }.sumOf { it.amountMxn }
        val recibido = runCatching { incomeRepository.observePostedTotal(quincenaId).first() }
            .getOrDefault(0.0)
        return QuincenaReport(
            quincena = quincena,
            figures = quincenaFigures(
                quincena = quincena,
                receivedIncome = recibido,
                spent = ejecutado,
                reserved = reservado,
                projectedExpensesFallback = ejecutado + reservado,
            ),
            movimientos = movimientos,
            ingresosPorMiembro = runCatching { incomeRepository.getIncomeByMember(quincenaId) }
                .getOrDefault(emptyList()),
            porCategoria = runCatching { analyticsRepository.getSpendByCategory(householdId, quincenaId) }
                .getOrDefault(emptyList()),
            porBeneficiario = runCatching { expenseRepository.observeSpendByMember(quincenaId).first() }
                .getOrDefault(emptyList()),
            porPagador = runCatching { expenseRepository.observePaidByMember(quincenaId).first() }
                .getOrDefault(emptyList()),
            porReembolsar = runCatching {
                expenseRepository.observePendingReimbursementTotals(householdId).first()
            }.getOrDefault(emptyList()),
            prestamos = runCatching { loanRepository.getOutstanding(householdId) }
                .getOrDefault(emptyList()),
            planesMsi = runCatching { installmentRepository.getActive(householdId) }
                .getOrDefault(emptyList()),
            saldos = runCatching { walletRepository.observeBalances(householdId).first() }
                .getOrDefault(emptyList()),
            miembros = runCatching { memberDao.observeAllMembers(householdId).first() }.getOrDefault(emptyList()),
            categorias = runCatching { categoryDao.getAll(householdId) }.getOrDefault(emptyList()),
            atribuciones = atribucionesDe(movimientos.map { it.expenseId }),
        )
    }

    /**
     * Atribuciones de varios gastos en lotes. SQLite tiene un tope de 999
     * variables por sentencia y un rango anual puede traer miles de gastos.
     */
    suspend fun atribucionesDe(ids: List<String>): Map<String, List<AtribucionResumen>> {
        if (ids.isEmpty()) return emptyMap()
        val resultado = HashMap<String, MutableList<AtribucionResumen>>()
        ids.chunked(400).forEach { lote ->
            runCatching { attributionDao.getForExpenses(lote) }.getOrDefault(emptyList())
                .forEach { fila ->
                    resultado.getOrPut(fila.expenseId) { mutableListOf() }
                        .add(AtribucionResumen(fila.role, fila.memberId, fila.shareBps))
                }
        }
        return resultado
    }
}
