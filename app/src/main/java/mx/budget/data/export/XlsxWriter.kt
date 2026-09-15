package mx.budget.data.export

import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Escritor minimo de libros XLSX, sin dependencias.
 *
 * Se descarto `fastexcel` (que funcionaria) porque anade una dependencia y su
 * transitiva sin aportar nada que este layout necesite, y Apache POI completo
 * esta excluido por tamano.
 *
 * Reglas del formato que Excel si exige, aunque openpyxl las perdone:
 * - `[Content_Types].xml` es la primera entrada del zip.
 * - Dentro de la hoja el orden es `dimension`, `cols`, `sheetData`, `mergeCells`;
 *   las filas y las celdas van en orden ascendente y su atributo `r` tiene que
 *   coincidir con su posicion real.
 * - `fills` lleva exactamente `none` y `gray125` en ese orden.
 * - Los numeros se escriben con `BigDecimal.toPlainString`, nunca con
 *   `Double.toString`, que produce notacion cientifica.
 * - Los nombres de hoja no pasan de 31 caracteres ni admiten `\ / ? * [ ] :`.
 */
class XlsxWriter {

    /** Estilos disponibles; el indice coincide con el orden de `cellXfs`. */
    enum class Estilo(val id: Int) {
        NORMAL(0),
        CABECERA(1),
        MONEDA(2),
        TITULO(3),
        MONEDA_FUERTE(4),
    }

    sealed interface Celda {
        data class Texto(val valor: String, val estilo: Estilo = Estilo.NORMAL) : Celda
        data class Numero(val valor: Double, val estilo: Estilo = Estilo.MONEDA) : Celda
        data object Vacia : Celda
    }

    class Hoja(nombreCrudo: String) {
        val nombre: String = sanearNombre(nombreCrudo)

        /** fila (base 1) -> columna (base 1) -> celda. */
        private val celdas = sortedMapOf<Int, MutableMap<Int, Celda>>()
        private val fusiones = mutableListOf<String>()
        private val anchos = sortedMapOf<Int, Double>()

        fun set(fila: Int, columna: Int, celda: Celda) {
            if (celda is Celda.Vacia) return
            celdas.getOrPut(fila) { sortedMapOf() }[columna] = celda
        }

        fun texto(fila: Int, columna: Int, valor: String, estilo: Estilo = Estilo.NORMAL) =
            set(fila, columna, Celda.Texto(valor, estilo))

        fun numero(fila: Int, columna: Int, valor: Double, estilo: Estilo = Estilo.MONEDA) =
            set(fila, columna, Celda.Numero(valor, estilo))

        fun fusionar(fila: Int, desde: Int, hasta: Int) {
            fusiones += "${ref(fila, desde)}:${ref(fila, hasta)}"
        }

        fun ancho(columna: Int, caracteres: Double) {
            anchos[columna] = caracteres
        }

        internal fun xml(): String {
            val sb = StringBuilder(4096)
            sb.append(CABECERA_XML)
            sb.append("<worksheet xmlns=\"$NS_HOJA\">")
            sb.append("<dimension ref=\"${dimension()}\"/>")
            if (anchos.isNotEmpty()) {
                sb.append("<cols>")
                anchos.forEach { (columna, ancho) ->
                    sb.append("<col min=\"$columna\" max=\"$columna\" width=\"$ancho\" customWidth=\"1\"/>")
                }
                sb.append("</cols>")
            }
            sb.append("<sheetData>")
            celdas.forEach { (fila, columnas) ->
                sb.append("<row r=\"$fila\">")
                columnas.forEach { (columna, celda) ->
                    val referencia = ref(fila, columna)
                    when (celda) {
                        is Celda.Texto -> sb.append(
                            "<c r=\"$referencia\" s=\"${celda.estilo.id}\" t=\"inlineStr\">" +
                                "<is><t xml:space=\"preserve\">${escapar(celda.valor)}</t></is></c>"
                        )
                        is Celda.Numero -> sb.append(
                            "<c r=\"$referencia\" s=\"${celda.estilo.id}\"><v>${plano(celda.valor)}</v></c>"
                        )
                        Celda.Vacia -> Unit
                    }
                }
                sb.append("</row>")
            }
            sb.append("</sheetData>")
            if (fusiones.isNotEmpty()) {
                sb.append("<mergeCells count=\"${fusiones.size}\">")
                fusiones.forEach { sb.append("<mergeCell ref=\"$it\"/>") }
                sb.append("</mergeCells>")
            }
            sb.append("</worksheet>")
            return sb.toString()
        }

        private fun dimension(): String {
            if (celdas.isEmpty()) return "A1"
            val primeraFila = celdas.keys.first()
            val ultimaFila = celdas.keys.last()
            val ultimaColumna = celdas.values.maxOf { it.keys.max() }
            return "${ref(primeraFila, 1)}:${ref(ultimaFila, ultimaColumna)}"
        }
    }

    private val hojas = mutableListOf<Hoja>()

    fun hoja(nombre: String): Hoja {
        val hoja = Hoja(nombreUnico(nombre))
        hojas += hoja
        return hoja
    }

    private fun nombreUnico(nombre: String): String {
        val base = sanearNombre(nombre)
        if (hojas.none { it.nombre == base }) return base
        var indice = 2
        while (hojas.any { it.nombre == recortar("$base ($indice)") }) indice++
        return recortar("$base ($indice)")
    }

    fun write(destino: File): File {
        require(hojas.isNotEmpty()) { "Un libro necesita al menos una hoja." }
        ZipOutputStream(destino.outputStream().buffered()).use { zip ->
            zip.entrada("[Content_Types].xml", contentTypes())
            zip.entrada("_rels/.rels", relsRaiz())
            zip.entrada("xl/workbook.xml", workbook())
            zip.entrada("xl/_rels/workbook.xml.rels", relsWorkbook())
            zip.entrada("xl/styles.xml", estilos())
            hojas.forEachIndexed { indice, hoja ->
                zip.entrada("xl/worksheets/sheet${indice + 1}.xml", hoja.xml())
            }
        }
        return destino
    }

    private fun ZipOutputStream.entrada(ruta: String, contenido: String) {
        putNextEntry(ZipEntry(ruta))
        write(contenido.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun contentTypes(): String = buildString {
        append(CABECERA_XML)
        append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
        append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
        append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>")
        hojas.indices.forEach {
            append("<Override PartName=\"/xl/worksheets/sheet${it + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
        }
        append("</Types>")
    }

    private fun relsRaiz(): String = CABECERA_XML +
        "<Relationships xmlns=\"$NS_REL_PAQUETE\">" +
        "<Relationship Id=\"rId1\" Type=\"$NS_REL_DOC/officeDocument\" Target=\"xl/workbook.xml\"/>" +
        "</Relationships>"

    private fun workbook(): String = buildString {
        append(CABECERA_XML)
        append("<workbook xmlns=\"$NS_HOJA\" xmlns:r=\"$NS_REL_DOC_ATTR\"><sheets>")
        hojas.forEachIndexed { indice, hoja ->
            append("<sheet name=\"${escapar(hoja.nombre)}\" sheetId=\"${indice + 1}\" r:id=\"rId${indice + 1}\"/>")
        }
        append("</sheets></workbook>")
    }

    private fun relsWorkbook(): String = buildString {
        append(CABECERA_XML)
        append("<Relationships xmlns=\"$NS_REL_PAQUETE\">")
        hojas.indices.forEach {
            append("<Relationship Id=\"rId${it + 1}\" Type=\"$NS_REL_DOC/worksheet\" Target=\"worksheets/sheet${it + 1}.xml\"/>")
        }
        append("<Relationship Id=\"rId${hojas.size + 1}\" Type=\"$NS_REL_DOC/styles\" Target=\"styles.xml\"/>")
        append("</Relationships>")
    }

    private fun estilos(): String = CABECERA_XML +
        "<styleSheet xmlns=\"$NS_HOJA\">" +
        "<numFmts count=\"1\"><numFmt numFmtId=\"164\" formatCode=\"&quot;$&quot;#,##0.00\"/></numFmts>" +
        "<fonts count=\"3\">" +
        "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
        "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
        "<font><b/><sz val=\"14\"/><name val=\"Calibri\"/></font>" +
        "</fonts>" +
        // Excel exige estos dos rellenos, en este orden, aunque no se usen.
        "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill>" +
        "<fill><patternFill patternType=\"gray125\"/></fill></fills>" +
        "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
        "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
        "<cellXfs count=\"5\">" +
        "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
        "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>" +
        "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
        "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>" +
        "<xf numFmtId=\"164\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\" applyFont=\"1\"/>" +
        "</cellXfs>" +
        "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>" +
        "</styleSheet>"

    companion object {
        private const val CABECERA_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        private const val NS_HOJA = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        private const val NS_REL_PAQUETE = "http://schemas.openxmlformats.org/package/2006/relationships"
        private const val NS_REL_DOC = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        private const val NS_REL_DOC_ATTR = NS_REL_DOC

        /** "A1", "AB7": referencia de celda desde fila y columna en base 1. */
        fun ref(fila: Int, columna: Int): String = "${letra(columna)}$fila"

        fun letra(columna: Int): String {
            var restante = columna
            val sb = StringBuilder()
            while (restante > 0) {
                val resto = (restante - 1) % 26
                sb.insert(0, ('A' + resto))
                restante = (restante - 1) / 26
            }
            return sb.toString()
        }

        fun sanearNombre(nombre: String): String {
            val limpio = nombre.replace(Regex("[\\\\/?*\\[\\]:]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .trim('\'')
            return recortar(limpio.ifBlank { "Hoja" })
        }

        private fun recortar(nombre: String): String =
            if (nombre.length <= 31) nombre else nombre.take(31).trim()

        fun plano(valor: Double): String =
            BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP).toPlainString()

        fun escapar(texto: String): String {
            val sb = StringBuilder(texto.length + 16)
            texto.forEach { c ->
                when {
                    c == '&' -> sb.append("&amp;")
                    c == '<' -> sb.append("&lt;")
                    c == '>' -> sb.append("&gt;")
                    c == '"' -> sb.append("&quot;")
                    c == '\'' -> sb.append("&apos;")
                    // Los caracteres de control rompen el XML; solo se conservan
                    // tabulador y salto de linea.
                    c.code < 0x20 && c != '\t' && c != '\n' -> sb.append(' ')
                    else -> sb.append(c)
                }
            }
            return sb.toString()
        }
    }
}
