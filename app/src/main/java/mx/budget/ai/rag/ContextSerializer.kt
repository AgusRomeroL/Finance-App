package mx.budget.ai.rag

import java.time.LocalDate

/**
 * Serializa de forma densa el contexto RAG a un formato tabular.
 * Omite prosa para maximizar densidad de información.
 */
object ContextSerializer {

    fun serialize(ctx: RagContext): String = buildString {
        appendLine("# LEDGER_SNAPSHOT")
        appendLine("now=${LocalDate.now()} currency=MXN")
        appendLine()

        appendLine("## QUINCENA_ACTIVA")
        with(ctx.currentQuincena) {
            appendLine("id=$id label=\"$label\" start=$startDate end=$endDate status=$status")
            appendLine("ingreso_proyectado=$projectedIncomeMxn ejecutado=$actualIncomeMxn")
            appendLine("gasto_proyectado=$projectedExpensesMxn ejecutado=$actualExpensesMxn")
        }
        appendLine()

        if (ctx.spendByCategory.isNotEmpty()) {
            appendLine("## GASTO_POR_CATEGORIA")
            appendLine("categoria|proyectado|ejecutado|restante|pct_exec")
            ctx.spendByCategory.forEach {
                appendLine("${it.categoryCode}|${it.projected}|${it.actual}|${it.remaining}|${it.pctExec}")
            }
            appendLine()
        }

        if (ctx.memberSpend.isNotEmpty()) {
            appendLine("## GASTO_POR_BENEFICIARIO")
            appendLine("miembro|total|n_gastos")
            ctx.memberSpend.forEach {
                appendLine("${it.memberName}|${it.totalMxn}|${it.expenseCount}")
            }
            appendLine()
        }

        if (ctx.topExpenses.isNotEmpty()) {
            appendLine("## GASTOS_DESTACADOS")
            appendLine("fecha|concepto|monto|categoria|wallet")
            ctx.topExpenses.forEach {
                appendLine("${it.date}|${it.concept}|${it.amountMxn}|${it.categoryName}|${it.walletName}")
            }
            appendLine()
        }

        if (ctx.walletsSnapshot.isNotEmpty()) {
            appendLine("## CUENTAS")
            appendLine("wallet|tipo|saldo|limite|utilizacion_pct")
            ctx.walletsSnapshot.forEach {
                appendLine("${it.displayName}|${it.kind}|${it.balance}|${it.creditLimit ?: "-"}|${it.utilizationPct ?: "-"}")
            }
            appendLine()
        }

        if (ctx.activeInstallments.isNotEmpty()) {
            appendLine("## CUOTAS_ACTIVAS")
            appendLine("plan|cuota_actual|total_cuotas|monto_cuota|proxima_fecha")
            ctx.activeInstallments.forEach {
                appendLine("${it.displayName}|${it.currentInstallment}|${it.totalInstallments}|${it.installmentAmountMxn}|${it.nextDate ?: "-"}")
            }
            appendLine()
        }

        if (ctx.history6q.isNotEmpty()) {
            appendLine("## ULTIMAS_QUINCENAS_CERRADAS")
            appendLine("label|gasto_total|ahorro")
            ctx.history6q.forEach {
                appendLine("${it.label}|${it.actualExpensesMxn}|${it.savingsMxn}")
            }
        }
    }
}
