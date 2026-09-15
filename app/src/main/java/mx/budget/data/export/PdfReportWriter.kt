package mx.budget.data.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import mx.budget.R
import mx.budget.data.quincena.QuincenaLifecycle
import mx.budget.ui.common.AppLocale
import java.io.File
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Reporte de quincena en PDF (RF-34) con `android.graphics.pdf.PdfDocument`, sin
 * dependencias nuevas.
 *
 * Detalles que importan y no se ven en el codigo:
 * - Pagina A4 en puntos (595 x 842), que es la unidad de Skia para PDF.
 * - La tipografia se embebe por subconjunto y por cada `Typeface` distinto, asi
 *   que se reutilizan tres y no diez. Un PDF de dos o tres paginas ronda los
 *   300 KB.
 * - Nada de emojis de categoria: la fuente de emoji de Android es de mapa de bits
 *   y Skia no la embebe, asi que saldrian cuadros vacios.
 */
class PdfReportWriter(private val context: Context) {

    private val money: NumberFormat = NumberFormat.getCurrencyInstance(AppLocale)
    private val fechaLarga = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", AppLocale)

    private val regular: Typeface = tipografia(R.font.google_sans_flex_text_regular)
    private val semibold: Typeface = tipografia(R.font.google_sans_flex_text_semibold)
    private val display: Typeface = tipografia(R.font.google_sans_flex_display_light)

    private fun tipografia(recurso: Int): Typeface =
        runCatching { ResourcesCompat.getFont(context, recurso) }.getOrNull() ?: Typeface.DEFAULT

    fun write(report: QuincenaReport, destino: File): File {
        val documento = PdfDocument()
        val lienzo = Hoja(documento, report.quincena.label)
        try {
            portada(lienzo, report)
            kpis(lienzo, report)
            porCategoria(lienzo, report)
            porMiembro(lienzo, report)
            deudas(lienzo, report)
            programados(lienzo, report)
            lienzo.cerrar()
            destino.outputStream().use { documento.writeTo(it) }
        } finally {
            documento.close()
        }
        return destino
    }

    // ── Secciones ───────────────────────────────────────────────────────────

    private fun portada(hoja: Hoja, report: QuincenaReport) {
        val q = report.quincena
        val estado = when (q.status) {
            QuincenaLifecycle.CLOSED -> "Cerrada"
            QuincenaLifecycle.CLOSING_REVIEW -> "Pendiente de cierre"
            QuincenaLifecycle.ACTIVE -> "En curso"
            else -> "Por comenzar"
        }
        hoja.etiqueta("PRESUPUESTO FAMILIAR")
        hoja.titulo(q.label)
        val inicio = runCatching { LocalDate.parse(q.startDate).format(fechaLarga) }.getOrDefault(q.startDate)
        val fin = runCatching { LocalDate.parse(q.endDate).format(fechaLarga) }.getOrDefault(q.endDate)
        hoja.parrafo("Del $inicio al $fin · $estado")
        hoja.parrafo("Generado el ${LocalDate.now().format(fechaLarga)}")
        hoja.espacio(10f)
    }

    private fun kpis(hoja: Hoja, report: QuincenaReport) {
        val f = report.figures
        hoja.seccion("RESUMEN")
        hoja.cifraHeroe("Disponible", money.format(f.available))
        hoja.fila("Ingreso del periodo", money.format(f.income))
        hoja.fila("Gasto ejecutado", money.format(f.spent))
        hoja.fila("Reservado en pagos planeados", money.format(f.reserved))
        hoja.fila("Presupuesto ejercido", "${f.executionPct} %")
        if (report.ingresosPorMiembro.isNotEmpty()) {
            hoja.espacio(6f)
            hoja.subtitulo("Ingreso por persona")
            report.ingresosPorMiembro.forEach {
                hoja.fila("${it.memberName} (${etiquetaEstado(it.status)})", money.format(it.totalMxn))
            }
        }
    }

    private fun porCategoria(hoja: Hoja, report: QuincenaReport) {
        val filas = report.porCategoria.filter { it.actual > 0.0 }.sortedByDescending { it.actual }
        if (filas.isEmpty()) return
        hoja.seccion("GASTO POR CATEGORÍA")
        val mayor = filas.maxOf { it.actual }
        filas.forEach { hoja.filaConBarra(it.categoryName, money.format(it.actual), it.actual / mayor) }
        hoja.fila("Total", money.format(filas.sumOf { it.actual }), destacada = true)
    }

    private fun porMiembro(hoja: Hoja, report: QuincenaReport) {
        if (report.porBeneficiario.isEmpty() && report.porPagador.isEmpty()) return
        hoja.seccion("REPARTO ENTRE LA FAMILIA")
        if (report.porBeneficiario.isNotEmpty()) {
            hoja.subtitulo("Quién consumió")
            report.porBeneficiario.forEach { hoja.fila(it.memberName, money.format(it.totalMxn)) }
        }
        if (report.porPagador.isNotEmpty()) {
            hoja.espacio(6f)
            hoja.subtitulo("Quién pagó")
            report.porPagador.forEach { hoja.fila(it.memberName, money.format(it.totalMxn)) }
        }
    }

    private fun deudas(hoja: Hoja, report: QuincenaReport) {
        val hayAlgo = report.porReembolsar.isNotEmpty() || report.prestamos.isNotEmpty() ||
            report.planesMsi.isNotEmpty() || report.saldos.isNotEmpty()
        if (!hayAlgo) return
        hoja.seccion("DEUDAS Y SALDOS")
        if (report.porReembolsar.isNotEmpty()) {
            hoja.subtitulo("Por reembolsar")
            report.porReembolsar.forEach {
                val quien = it.externalPayerMemberId?.let(report::nombreMiembro) ?: "Sin identificar"
                hoja.fila(quien, money.format(it.totalMxn))
            }
            hoja.espacio(6f)
        }
        if (report.prestamos.isNotEmpty()) {
            hoja.subtitulo("Préstamos por cobrar")
            report.prestamos.forEach {
                hoja.fila(report.nombreMiembro(it.debtorMemberId), money.format(it.remainingBalanceMxn))
            }
            hoja.espacio(6f)
        }
        if (report.planesMsi.isNotEmpty()) {
            hoja.subtitulo("Compras a meses")
            report.planesMsi.forEach {
                val restantes = (it.totalInstallments - it.currentInstallment).coerceAtLeast(0)
                hoja.fila(
                    "${it.displayName} (${it.currentInstallment}/${it.totalInstallments})",
                    money.format(it.installmentAmountMxn * restantes),
                )
            }
            hoja.espacio(6f)
        }
        if (report.saldos.isNotEmpty()) {
            hoja.subtitulo("Saldos de las cuentas")
            report.saldos.forEach { hoja.fila(it.displayName, money.format(it.balance)) }
        }
    }

    private fun programados(hoja: Hoja, report: QuincenaReport) {
        val planeados = report.planeados
        if (planeados.isEmpty()) return
        hoja.seccion("PAGOS PROGRAMADOS SIN EJECUTAR")
        planeados.sortedBy { it.occurredAt }.forEach {
            hoja.fila("${it.concept} · ${it.categoryName}", money.format(it.amountMxn))
        }
        hoja.fila("Total", money.format(planeados.sumOf { it.amountMxn }), destacada = true)
    }

    private fun etiquetaEstado(status: String): String =
        if (status == "POSTED") "recibido" else "proyectado"

    // ── Motor de pintado ────────────────────────────────────────────────────

    /**
     * Cursor de pagina: sabe donde va el siguiente renglon y abre pagina nueva
     * cuando lo que sigue no cabe, repitiendo cabecera y pie.
     */
    private inner class Hoja(
        private val documento: PdfDocument,
        private val encabezado: String,
    ) {
        private var pagina: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var numero = 0
        private var y = 0f

        private val tinta = Color.rgb(28, 30, 30)
        private val tintaSuave = Color.rgb(110, 116, 118)
        private val acento = Color.rgb(0, 108, 68)

        private val pEtiqueta = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = semibold; textSize = 8f; color = tintaSuave; letterSpacing = 0.12f
        }
        private val pTitulo = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = display; textSize = 26f; color = tinta
        }
        private val pHeroe = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = display; textSize = 30f; color = acento
        }
        private val pSeccion = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = semibold; textSize = 9f; color = acento; letterSpacing = 0.14f
        }
        private val pSub = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = semibold; textSize = 10f; color = tinta
        }
        private val pCuerpo = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regular; textSize = 10f; color = tinta
        }
        private val pSuave = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regular; textSize = 9f; color = tintaSuave
        }
        private val pImporte = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regular; textSize = 10f; color = tinta; textAlign = Paint.Align.RIGHT
        }
        private val pImporteFuerte = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = semibold; textSize = 10f; color = tinta; textAlign = Paint.Align.RIGHT
        }
        private val pBarra = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(214, 232, 223) }

        init {
            nuevaPagina()
        }

        fun etiqueta(texto: String) = renglon(texto, pEtiqueta, 12f)

        fun titulo(texto: String) = renglon(texto, pTitulo, 32f)

        fun parrafo(texto: String) = bloque(texto, pSuave)

        fun subtitulo(texto: String) = renglon(texto, pSub, 15f)

        fun seccion(texto: String) {
            espacio(14f)
            asegurar(40f)
            renglon(texto, pSeccion, 16f)
        }

        fun cifraHeroe(etiqueta: String, valor: String) {
            asegurar(52f)
            renglon(etiqueta, pSuave, 13f)
            renglon(valor, pHeroe, 34f)
        }

        fun fila(etiqueta: String, valor: String, destacada: Boolean = false) {
            asegurar(16f)
            val lienzo = canvas ?: return
            val pintaTexto = if (destacada) pSub else pCuerpo
            val pintaValor = if (destacada) pImporteFuerte else pImporte
            val ancho = ANCHO_UTIL - 90f
            val recortado = TextUtils.ellipsize(etiqueta, pintaTexto, ancho, TextUtils.TruncateAt.END)
            lienzo.drawText(recortado.toString(), MARGEN, y, pintaTexto)
            lienzo.drawText(valor, MARGEN + ANCHO_UTIL, y, pintaValor)
            y += 14f
        }

        fun filaConBarra(etiqueta: String, valor: String, proporcion: Double) {
            fila(etiqueta, valor)
            val lienzo = canvas ?: return
            val largo = (ANCHO_UTIL * proporcion.coerceIn(0.0, 1.0)).toFloat()
            lienzo.drawRect(MARGEN, y - 8f, MARGEN + largo, y - 5f, pBarra)
            y += 5f
        }

        fun espacio(alto: Float) {
            y += alto
        }

        fun cerrar() {
            pagina?.let { documento.finishPage(it) }
            pagina = null
            canvas = null
        }

        private fun renglon(texto: String, pinta: TextPaint, avance: Float) {
            asegurar(avance)
            canvas?.drawText(texto, MARGEN, y, pinta)
            y += avance
        }

        private fun bloque(texto: String, pinta: TextPaint) {
            val layout = StaticLayout.Builder
                .obtain(texto, 0, texto.length, pinta, ANCHO_UTIL.toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build()
            asegurar(layout.height.toFloat() + 4f)
            val lienzo = canvas ?: return
            lienzo.save()
            lienzo.translate(MARGEN, y - pinta.textSize)
            layout.draw(lienzo)
            lienzo.restore()
            y += layout.height + 4f
        }

        private fun asegurar(alto: Float) {
            if (y + alto > ALTO - MARGEN - 24f) nuevaPagina()
        }

        private fun nuevaPagina() {
            cerrar()
            numero++
            val info = PdfDocument.PageInfo.Builder(ANCHO.toInt(), ALTO.toInt(), numero).create()
            val nueva = documento.startPage(info)
            pagina = nueva
            canvas = nueva.canvas
            y = MARGEN + 10f
            if (numero > 1) {
                canvas?.drawText(encabezado, MARGEN, MARGEN - 12f, pSuave)
                y = MARGEN + 14f
            }
            val pie = TextPaint(pSuave).apply { textAlign = Paint.Align.RIGHT }
            canvas?.drawText("Página $numero", MARGEN + ANCHO_UTIL, ALTO - MARGEN + 14f, pie)
        }
    }

    private companion object {
        /** A4 en puntos PostScript, que es la unidad de PdfDocument. */
        const val ANCHO = 595f
        const val ALTO = 842f
        const val MARGEN = 40f
        const val ANCHO_UTIL = ANCHO - MARGEN * 2
    }
}
