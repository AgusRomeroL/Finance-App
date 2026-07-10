package mx.budget.ui.tutorial

import mx.budget.ai.proactive.ProactiveSuggestion
import mx.budget.data.local.entity.CategoryEntity
import mx.budget.data.local.entity.MemberEntity
import mx.budget.data.local.entity.PendingCaptureEntity
import mx.budget.data.local.entity.QuincenaEntity
import mx.budget.data.local.result.ExpenseWithDetails
import mx.budget.data.local.result.SpendByCategory
import mx.budget.data.local.result.SpendByMember
import mx.budget.data.local.result.TopConcept
import mx.budget.ui.dashboard.DashboardUiState
import java.time.LocalDate
import java.time.ZoneId

/**
 * Dataset canned que el **tutorial guiado** muestra mientras corre (ver `TUTORIAL.md`).
 *
 * REGLA DE ORO: **solo vive en pantalla, NUNCA se escribe en Room ni sube al sync.** Cada screen,
 * cuando `tutorialController?.demoActive == true`, sustituye su estado real por estos valores justo
 * tras el `collectAsState()`. Al terminar el tour el flag baja y las pantallas vuelven a los flujos
 * reales — cero residuos, cero riesgo para la Wallet real.
 *
 * `ExpenseWithDetails` es denormalizado (trae nombre/color/wallet inline), así que se renderiza sin
 * necesidad de filas reales. Los datos son coherentes entre pantallas (mismos miembros y montos).
 */
object TutorialDemoData {

    private const val HH = "default_household"
    private val zone: ZoneId = ZoneId.of("America/Mexico_City")

    private fun at(daysFromToday: Long, hour: Int = 9): Long =
        LocalDate.now(zone).plusDays(daysFromToday)
            .atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    // ── Quincena demo (abarca "hoy" para que el progreso se vea real) ────────────
    val quincena: QuincenaEntity = run {
        val today = LocalDate.now(zone)
        val firstHalf = today.dayOfMonth <= 15
        val start = if (firstHalf) today.withDayOfMonth(1) else today.withDayOfMonth(16)
        val end = if (firstHalf) today.withDayOfMonth(15) else today.withDayOfMonth(today.lengthOfMonth())
        QuincenaEntity(
            id = "demo-quincena",
            householdId = HH,
            year = today.year,
            month = today.monthValue,
            half = if (firstHalf) "FIRST" else "SECOND",
            startDate = start.toString(),
            endDate = end.toString(),
            label = "Quincena de ejemplo",
            projectedIncomeMxn = 25000.0,
            projectedExpensesMxn = 18000.0,
            actualIncomeMxn = 25000.0,
            actualExpensesMxn = 8450.0,
            status = "ACTIVE",
            closedAt = null,
            updatedAt = 0,
        )
    }

    // ── Miembros (atribución en captura + barras del dashboard) ──────────────────
    val members: List<MemberEntity> = listOf(
        MemberEntity(id = "demo-m1", householdId = HH, displayName = "Ana", role = "PAYER_ADULT", defaultIncomeMxn = 15000.0),
        MemberEntity(id = "demo-m2", householdId = HH, displayName = "Luis", role = "PAYER_ADULT", defaultIncomeMxn = 10000.0),
        MemberEntity(id = "demo-m3", householdId = HH, displayName = "Sofía", role = "BENEFICIARY_DEPENDENT"),
    )

    // ── Categorías recientes (tarjeta de categoría en captura) ───────────────────
    val recentCategories: List<CategoryEntity> = listOf(
        CategoryEntity(id = "demo-c1", householdId = HH, code = "FOOD.DESPENSA", displayName = "Despensa", colorHex = "#4CAF50", kind = "EXPENSE_VARIABLE"),
        CategoryEntity(id = "demo-c2", householdId = HH, code = "TRANSPORT.GASOLINA", displayName = "Gasolina", colorHex = "#FF9800", kind = "EXPENSE_VARIABLE"),
        CategoryEntity(id = "demo-c3", householdId = HH, code = "FUN.RESTAURANTE", displayName = "Restaurante", colorHex = "#E91E63", kind = "EXPENSE_VARIABLE"),
        CategoryEntity(id = "demo-c4", householdId = HH, code = "HOUSING.INTERNET", displayName = "Internet", colorHex = "#3F51B5", kind = "EXPENSE_FIXED"),
    )

    // ── Distribución por miembro (suma = postedTotal = 8450) ─────────────────────
    val beneficiaryDistribution: List<SpendByMember> = listOf(
        SpendByMember(memberId = "demo-m1", memberName = "Ana", totalMxn = 3900.0, expenseCount = 9),
        SpendByMember(memberId = "demo-m2", memberName = "Luis", totalMxn = 2800.0, expenseCount = 6),
        SpendByMember(memberId = "demo-m3", memberName = "Sofía", totalMxn = 1750.0, expenseCount = 4),
    )
    val payerDistribution: List<SpendByMember> = listOf(
        SpendByMember(memberId = "demo-m1", memberName = "Ana", totalMxn = 5600.0, expenseCount = 13),
        SpendByMember(memberId = "demo-m2", memberName = "Luis", totalMxn = 2850.0, expenseCount = 6),
    )

    private fun tx(
        id: String, concept: String, amount: Double, days: Long,
        cat: String, catName: String, code: String, color: String,
        wallet: String, kind: String, status: String,
    ) = ExpenseWithDetails(
        expenseId = id, concept = concept, amountMxn = amount, occurredAt = at(days),
        status = status, categoryId = cat, categoryName = catName, categoryCode = code,
        categoryColorHex = color, paymentMethodName = wallet, paymentMethodKind = kind,
        quincenaLabel = quincena.label, installmentNumber = null, installmentTotal = null, notes = null,
    )

    // ── Movimientos recientes / Libro Mayor (4, POSTED) ──────────────────────────
    val ledgerRows: List<ExpenseWithDetails> = listOf(
        tx("demo-t1", "Despensa Costco", 1850.0, -1, "demo-c1", "Despensa", "FOOD.DESPENSA", "#4CAF50", "Tarjeta BBVA", "CREDIT_CARD", "POSTED"),
        tx("demo-t2", "Gasolina", 900.0, -2, "demo-c2", "Gasolina", "TRANSPORT.GASOLINA", "#FF9800", "Débito Banamex", "DEBIT_ACCOUNT", "POSTED"),
        tx("demo-t3", "Cena familiar", 1200.0, -3, "demo-c3", "Restaurante", "FUN.RESTAURANTE", "#E91E63", "Efectivo", "CASH", "POSTED"),
        tx("demo-t4", "Internet del mes", 599.0, -4, "demo-c4", "Internet", "HOUSING.INTERNET", "#3F51B5", "Débito Banamex", "DEBIT_ACCOUNT", "POSTED"),
    )

    /** Recientes del dashboard = mismos movimientos del ledger. */
    val transactions: List<ExpenseWithDetails> get() = ledgerRows

    // ── Pagos planeados (Calendario, PLANNED, fechas futuras → marcan días) ──────
    val plannedExpenses: List<ExpenseWithDetails> = listOf(
        tx("demo-p1", "Renta", 6500.0, 2, "demo-c4", "Vivienda", "HOUSING.RENTA", "#3F51B5", "Débito Banamex", "DEBIT_ACCOUNT", "PLANNED"),
        tx("demo-p2", "Colegiatura", 3200.0, 5, "demo-c1", "Educación", "EDU.COLEGIATURA", "#009688", "Tarjeta BBVA", "CREDIT_CARD", "PLANNED"),
        tx("demo-p3", "Netflix", 219.0, 9, "demo-c3", "Suscripciones", "FUN.SUSCRIPCIONES", "#E91E63", "Tarjeta BBVA", "CREDIT_CARD", "PLANNED"),
    )

    // ── Sugerencias inteligentes (carrusel del dashboard) ────────────────────────
    val proactiveSuggestions: List<ProactiveSuggestion> = listOf(
        ProactiveSuggestion(canonicalKey = "demo-sug-despensa", concept = "Despensa semanal", categoryId = "demo-c1", reason = "Sueles registrarla los sábados", basis = 7),
        ProactiveSuggestion(canonicalKey = "demo-sug-gas", concept = "Gasolina", categoryId = "demo-c2", reason = "Cada 5 días aproximadamente", basis = 5),
    )

    val bankCaptures: List<PendingCaptureEntity> = listOf(
        PendingCaptureEntity(
            id = "demo-cap-1", source = "BANK", amountMxn = 450.0, concept = "Cargo en OXXO",
            occurredAt = at(0), status = "PENDING", enrichStatus = "READY", createdAt = at(0),
            bankId = "bbva", bankName = "BBVA", last4 = "4521",
        ),
    )

    // ── Dashboard ────────────────────────────────────────────────────────────────
    val dashboardState: DashboardUiState.Success = DashboardUiState.Success(
        quincena = quincena,
        transactions = transactions,
        postedTotal = 8450.0,
        plannedTotal = 9919.0,   // suma de los 3 planeados demo
        balance = 25000.0 - 8450.0,
        actualIncome = 25000.0,
        beneficiaryDistribution = beneficiaryDistribution,
        payerDistribution = payerDistribution,
        canViewOlder = true,
        canViewNewer = false,
        viewingActive = true,
    )

    // ── Analíticas ────────────────────────────────────────────────────────────────
    val spendByCategory: List<SpendByCategory> = listOf(
        SpendByCategory("demo-c1", "Despensa", "FOOD.DESPENSA", "#4CAF50", projected = 3000.0, actual = 1850.0, remaining = 1150.0, pctExec = 62),
        SpendByCategory("demo-c2", "Gasolina", "TRANSPORT.GASOLINA", "#FF9800", projected = 1500.0, actual = 900.0, remaining = 600.0, pctExec = 60),
        SpendByCategory("demo-c3", "Restaurante", "FUN.RESTAURANTE", "#E91E63", projected = 1000.0, actual = 1200.0, remaining = -200.0, pctExec = 120),
        SpendByCategory("demo-c4", "Internet", "HOUSING.INTERNET", "#3F51B5", projected = 600.0, actual = 599.0, remaining = 1.0, pctExec = 100),
    )
    val topConcepts: List<TopConcept> = listOf(
        TopConcept(concept = "Despensa Costco", totalMxn = 1850.0, timesCount = 3),
        TopConcept(concept = "Cena familiar", totalMxn = 1200.0, timesCount = 2),
        TopConcept(concept = "Gasolina", totalMxn = 900.0, timesCount = 4),
        TopConcept(concept = "Internet del mes", totalMxn = 599.0, timesCount = 1),
    )
    const val postedIncome: Double = 25000.0
    const val totalSavings: Double = 18000.0
    const val totalReceivable: Double = 4500.0
    const val totalCommitment: Double = 6200.0
}
