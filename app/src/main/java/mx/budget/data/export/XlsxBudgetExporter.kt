package mx.budget.data.export

import mx.budget.data.export.XlsxWriter.Celda
import mx.budget.data.export.XlsxWriter.Estilo
import mx.budget.data.local.entity.MemberEntity
import mx.budget.data.local.entity.QuincenaEntity
import mx.budget.data.local.result.ExpenseWithDetails
import mx.budget.ui.common.AppLocale
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Exporta el presupuesto con el layout del Excel de Norma (RF-91): una hoja por
 * quincena, con su bloque de sueldos arriba, los gastos agrupados por seccion con
 * su subtotal, y el total y lo que sobra al final.
 *
 * Dos diferencias deliberadas con el original, que son mejoras y no perdidas:
 * - Los bloques se apilan en una sola columna en vez de repartirse en dos, que en
 *   el Excel se rompen en cuanto una seccion crece.
 * - Las columnas de pagador salen de los adultos que existan en el hogar, en vez
 *   de estar clavadas a dos nombres.
 *
 * Al final se anaden dos hojas que el Excel no tenia y que hacen util el archivo
 * fuera de la app: `Movimientos`, con una fila por gasto y su reparto, y
 * `Cuentas`, con los saldos.
 */
class XlsxBudgetExporter {

    private val fechaFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val zona: ZoneId = ZoneId.of("America/Mexico_City")

    fun write(reportes: List<QuincenaReport>, destino: File): File {
        require(reportes.isNotEmpty()) { "No hay quincenas en el rango elegido." }
        val libro = XlsxWriter()
        reportes.sortedByDescending { it.quincena.startDate }.forEach { hojaDeQuincena(libro, it) }
        hojaMovimientos(libro, reportes)
        hojaCuentas(libro, reportes.first())
        return libro.write(destino)
    }

    // ── Hoja por quincena ───────────────────────────────────────────────────

    private fun hojaDeQuincena(libro: XlsxWriter, report: QuincenaReport) {
        val q = report.quincena
        val hoja = libro.hoja(QuincenaNaming.sheetName(q))
        hoja.ancho(2, 34.0)
        hoja.ancho(3, 16.0)

        val adultos = report.miembros
            .filter { it.role == "PAYER_ADULT" }
            .sortedBy { it.displayName }
        adultos.indices.forEach { hoja.ancho(4 + it, 14.0) }

        hoja.texto(2, 2, QuincenaNaming.title(q), Estilo.TITULO)
        hoja.fusionar(2, 2, 3 + adultos.size.coerceAtLeast(1))

        // ── Cabecera: sueldos, totales y balance ────────────────────────────
        hoja.texto(4, 2, "SUELDOS", Estilo.CABECERA)
        var fila = 4
        report.ingresosPorMiembro
            .groupBy { it.memberName }
            .forEach { (nombre, entradas) ->
                hoja.texto(fila, 3, nombre)
                hoja.numero(fila, 4, entradas.sumOf { it.totalMxn })
                fila++
            }
        hoja.texto(fila, 3, "Total quincenal", Estilo.CABECERA)
        hoja.numero(fila, 4, report.figures.income, Estilo.MONEDA_FUERTE)
        fila += 2

        hoja.texto(fila, 2, "TOTAL GASTOS", Estilo.CABECERA)
        hoja.numero(fila, 4, report.figures.spent, Estilo.MONEDA_FUERTE)
        fila++
        hoja.texto(fila, 3, "Reservado")
        hoja.numero(fila, 4, report.figures.reserved)
        fila++
        hoja.texto(fila, 3, "Presupuesto", Estilo.CABECERA)
        hoja.numero(fila, 4, q.projectedExpensesMxn, Estilo.MONEDA_FUERTE)
        fila++
        hoja.texto(fila, 2, "BALANCE", Estilo.CABECERA)
        hoja.numero(fila, 4, report.figures.income - report.figures.spent, Estilo.MONEDA_FUERTE)
        fila++
        hoja.texto(fila, 2, "FALTA GASTAR", Estilo.CABECERA)
        hoja.numero(fila, 4, report.figures.available, Estilo.MONEDA_FUERTE)
        fila += 2

        // ── Bloques por grupo de categoria ──────────────────────────────────
        val porGrupo = report.ejecutados.groupBy { report.grupoDe(it.categoryId) }
            .toSortedMap(compareBy { it.lowercase(AppLocale) })

        porGrupo.forEach { (grupo, gastos) ->
            hoja.texto(fila, 2, grupo.uppercase(AppLocale), Estilo.CABECERA)
            hoja.texto(fila, 3, "Projected Cost", Estilo.CABECERA)
            adultos.forEachIndexed { indice, adulto ->
                hoja.texto(fila, 4 + indice, adulto.displayName, Estilo.CABECERA)
            }
            fila++
            gastos.sortedBy { it.occurredAt }.forEach { gasto ->
                hoja.texto(fila, 2, gasto.concept)
                hoja.numero(fila, 3, gasto.amountMxn)
                repartoPorPagador(report, gasto, adultos).forEachIndexed { indice, monto ->
                    if (monto > 0.0) hoja.numero(fila, 4 + indice, monto)
                }
                fila++
            }
            hoja.texto(fila, 2, "Subtotal", Estilo.CABECERA)
            hoja.numero(fila, 3, gastos.sumOf { it.amountMxn }, Estilo.MONEDA_FUERTE)
            adultos.forEachIndexed { indice, adulto ->
                val suma = gastos.sumOf { gasto ->
                    repartoPorPagador(report, gasto, adultos).getOrElse(indice) { 0.0 }
                }
                if (suma > 0.0) hoja.numero(fila, 4 + indice, suma, Estilo.MONEDA_FUERTE)
            }
            fila += 2
        }

        hoja.texto(fila, 2, "Total de gasto quincenal", Estilo.CABECERA)
        hoja.numero(fila, 3, report.figures.spent, Estilo.MONEDA_FUERTE)
        fila++
        hoja.texto(fila, 2, "Sobra", Estilo.CABECERA)
        hoja.numero(fila, 3, report.figures.available, Estilo.MONEDA_FUERTE)
    }

    /** Monto de [gasto] que le toca a cada adulto segun la atribucion PAYER. */
    private fun repartoPorPagador(
        report: QuincenaReport,
        gasto: ExpenseWithDetails,
        adultos: List<MemberEntity>,
    ): List<Double> {
        val reparto = report.atribuciones[gasto.expenseId].orEmpty().filter { it.role == "PAYER" }
        return adultos.map { adulto ->
            val bps = reparto.firstOrNull { it.memberId == adulto.id }?.shareBps ?: 0
            gasto.amountMxn * bps / 10_000.0
        }
    }

    // ── Hojas de detalle ────────────────────────────────────────────────────

    private fun hojaMovimientos(libro: XlsxWriter, reportes: List<QuincenaReport>) {
        val hoja = libro.hoja("Movimientos")
        val columnas = listOf(
            "Fecha", "Quincena", "Concepto", "Categoría", "Grupo", "Monto", "Estado",
            "Cuenta", "Beneficiarios", "Pagadores", "Notas", "Id",
        )
        columnas.forEachIndexed { indice, titulo ->
            hoja.texto(1, indice + 1, titulo, Estilo.CABECERA)
        }
        hoja.ancho(1, 12.0); hoja.ancho(2, 18.0); hoja.ancho(3, 30.0); hoja.ancho(4, 20.0)
        hoja.ancho(5, 20.0); hoja.ancho(8, 18.0); hoja.ancho(9, 26.0); hoja.ancho(10, 26.0)

        var fila = 2
        reportes.sortedByDescending { it.quincena.startDate }.forEach { report ->
            report.movimientos.sortedBy { it.occurredAt }.forEach { movimiento ->
                hoja.texto(fila, 1, fecha(movimiento.occurredAt))
                hoja.texto(fila, 2, report.quincena.label)
                hoja.texto(fila, 3, movimiento.concept)
                hoja.texto(fila, 4, movimiento.categoryName)
                hoja.texto(fila, 5, report.grupoDe(movimiento.categoryId))
                hoja.numero(fila, 6, movimiento.amountMxn)
                hoja.texto(fila, 7, estado(movimiento.status))
                hoja.texto(fila, 8, movimiento.paymentMethodName)
                hoja.texto(fila, 9, reparto(report, movimiento.expenseId, "BENEFICIARY"))
                hoja.texto(fila, 10, reparto(report, movimiento.expenseId, "PAYER"))
                hoja.texto(fila, 11, movimiento.notes.orEmpty())
                hoja.texto(fila, 12, movimiento.expenseId)
                fila++
            }
        }
    }

    private fun hojaCuentas(libro: XlsxWriter, report: QuincenaReport) {
        if (report.saldos.isEmpty()) return
        val hoja = libro.hoja("Cuentas")
        hoja.ancho(1, 26.0); hoja.ancho(2, 18.0); hoja.ancho(3, 16.0)
        hoja.texto(1, 1, "Cuenta", Estilo.CABECERA)
        hoja.texto(1, 2, "Tipo", Estilo.CABECERA)
        hoja.texto(1, 3, "Saldo", Estilo.CABECERA)
        report.saldos.forEachIndexed { indice, saldo ->
            val fila = indice + 2
            hoja.texto(fila, 1, saldo.displayName)
            hoja.texto(fila, 2, saldo.kind)
            hoja.numero(fila, 3, saldo.balance)
        }
    }

    private fun reparto(report: QuincenaReport, expenseId: String, role: String): String =
        report.atribuciones[expenseId].orEmpty()
            .filter { it.role == role }
            .sortedByDescending { it.shareBps }
            .joinToString(", ") { "${report.nombreMiembro(it.memberId)} ${it.shareBps / 100}%" }

    private fun fecha(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zona).toLocalDate().format(fechaFmt)

    private fun estado(status: String): String = when (status) {
        "POSTED" -> "Ejecutado"
        "PLANNED" -> "Programado"
        "RECONCILED" -> "Conciliado"
        else -> status
    }
}

/**
 * Nombres con los que Norma reconoce sus propias quincenas. Nada en el
 * repositorio construia esta cadena: el ETL solo la lee del Excel con una
 * expresion regular tolerante y guarda la etiqueta corta `Q1 Enero 2026`.
 */
object QuincenaNaming {

    private val meses = listOf(
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre",
    )

    /** "Quincena 1 al 15 Septiembre 2026", recortado si no cabe en una hoja. */
    fun sheetName(q: QuincenaEntity): String {
        val largo = "Quincena ${rango(q)} ${mes(q)} ${q.year}"
        if (largo.length <= 31) return largo
        return "Quin. ${rango(q)} ${mes(q)} ${q.year}"
    }

    /** "PRESUPUESTO QUINCENAL DEL 1 AL 15 DE SEPTIEMBRE DE 2026". */
    fun title(q: QuincenaEntity): String {
        val (inicio, fin) = dias(q)
        return "PRESUPUESTO QUINCENAL DEL $inicio AL $fin DE ${mes(q).uppercase(AppLocale)} DE ${q.year}"
    }

    private fun rango(q: QuincenaEntity): String {
        val (inicio, fin) = dias(q)
        return "$inicio al $fin"
    }

    private fun dias(q: QuincenaEntity): Pair<Int, Int> {
        val inicio = runCatching { LocalDate.parse(q.startDate).dayOfMonth }
            .getOrDefault(if (q.half == "FIRST") 1 else 16)
        val fin = runCatching { LocalDate.parse(q.endDate).dayOfMonth }.getOrDefault(if (q.half == "FIRST") 15 else 30)
        return inicio to fin
    }

    private fun mes(q: QuincenaEntity): String = meses.getOrElse(q.month - 1) { "" }
}
