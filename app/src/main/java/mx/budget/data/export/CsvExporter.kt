package mx.budget.data.export

import kotlinx.coroutines.flow.first
import mx.budget.data.local.dao.CategoryDao
import mx.budget.data.local.dao.MemberDao
import mx.budget.data.local.dao.PaymentMethodDao
import mx.budget.data.local.dao.QuincenaDao
import mx.budget.data.repository.ExpenseRepository
import mx.budget.data.repository.TransferRepository
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Exportacion CSV estandar por rango de fechas (RF-92).
 *
 * Detalles que deciden si el archivo se abre bien en Excel de escritorio:
 * - Marca de orden de bytes UTF-8 al inicio, sin la cual Excel destroza los
 *   acentos.
 * - Separador coma y comillas segun RFC 4180 (las comillas internas se duplican).
 * - Fechas ISO y montos con punto decimal, que es lo que espera cualquier
 *   herramienta que no sea una hoja de calculo.
 *
 * Incluye tambien las transferencias entre cuentas, que no son gastos pero sin
 * ellas el archivo no cuadra con el libro mayor de la app.
 */
class CsvExporter(
    private val expenseRepository: ExpenseRepository,
    private val transferRepository: TransferRepository,
    private val reportBuilder: QuincenaReportBuilder,
    private val quincenaDao: QuincenaDao,
    private val categoryDao: CategoryDao,
    private val memberDao: MemberDao,
    private val paymentMethodDao: PaymentMethodDao,
    private val householdId: String,
    private val zona: ZoneId = ZoneId.of("America/Mexico_City"),
) {

    private val fechaFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    suspend fun write(desde: LocalDate, hasta: LocalDate, destino: File): File {
        val inicio = desde.atStartOfDay(zona).toInstant().toEpochMilli()
        val fin = hasta.plusDays(1).atStartOfDay(zona).toInstant().toEpochMilli() - 1

        val gastos = expenseRepository.getByDateRange(householdId, inicio, fin)
        val atribuciones = reportBuilder.atribucionesDe(gastos.map { it.id })
        val transferencias = runCatching {
            transferRepository.observeTransfersInRange(householdId, inicio, fin).first()
        }.getOrDefault(emptyList())

        val categorias = categoryDao.getAll(householdId).associateBy { it.id }
        val miembros = memberDao.observeAllMembers(householdId).first().associateBy { it.id }
        val cuentas = paymentMethodDao.observeActive(householdId).first().associateBy { it.id }
        val quincenas = quincenaDao.observeAll(householdId).first().associateBy { it.id }

        fun nombreMiembro(id: String) = miembros[id]?.displayName ?: "Sin nombre"
        fun grupo(categoryId: String?): String {
            val categoria = categorias[categoryId] ?: return ""
            val padre = categoria.parentId ?: return categoria.displayName
            return categorias[padre]?.displayName ?: categoria.displayName
        }
        fun reparto(expenseId: String, role: String) =
            atribuciones[expenseId].orEmpty()
                .filter { it.role == role }
                .sortedByDescending { it.shareBps }
                .joinToString("; ") { "${nombreMiembro(it.memberId)} ${it.shareBps / 100}%" }

        destino.bufferedWriter(Charsets.UTF_8).use { salida ->
            salida.write("﻿")
            salida.write(CABECERA.joinToString(",") { escapar(it) })
            salida.write("\r\n")

            gastos.sortedBy { it.occurredAt }.forEach { gasto ->
                val fila = listOf(
                    "GASTO",
                    fecha(gasto.occurredAt),
                    quincenas[gasto.quincenaId]?.label.orEmpty(),
                    gasto.concept,
                    categorias[gasto.categoryId]?.displayName.orEmpty(),
                    grupo(gasto.categoryId),
                    monto(gasto.amountMxn),
                    estado(gasto.status),
                    cuentas[gasto.paymentMethodId]?.displayName.orEmpty(),
                    cuentas[gasto.paymentMethodId]?.kind.orEmpty(),
                    reparto(gasto.id, "BENEFICIARY"),
                    reparto(gasto.id, "PAYER"),
                    gasto.settlementStatus,
                    gasto.notes.orEmpty(),
                    gasto.id,
                )
                salida.write(fila.joinToString(",") { escapar(it) })
                salida.write("\r\n")
            }

            transferencias.sortedBy { it.occurredAt }.forEach { transferencia ->
                val fila = listOf(
                    "TRANSFERENCIA",
                    fecha(transferencia.occurredAt),
                    "",
                    "${transferencia.fromName} a ${transferencia.toName}",
                    "",
                    "",
                    monto(transferencia.amountMxn),
                    "Ejecutado",
                    transferencia.fromName,
                    "",
                    "",
                    "",
                    "",
                    transferencia.note.orEmpty(),
                    transferencia.id,
                )
                salida.write(fila.joinToString(",") { escapar(it) })
                salida.write("\r\n")
            }
        }
        return destino
    }

    private fun fecha(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zona).toLocalDate().format(fechaFmt)

    private fun monto(valor: Double): String = String.format(java.util.Locale.ROOT, "%.2f", valor)

    private fun estado(status: String): String = when (status) {
        "POSTED" -> "Ejecutado"
        "PLANNED" -> "Programado"
        "RECONCILED" -> "Conciliado"
        else -> status
    }

    private fun escapar(campo: String): String {
        val limpio = campo.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ')
        return "\"" + limpio.replace("\"", "\"\"") + "\""
    }

    private companion object {
        val CABECERA = listOf(
            "tipo", "fecha", "quincena", "concepto", "categoria", "grupo", "monto_mxn",
            "estado", "cuenta", "metodo", "beneficiarios", "pagadores", "liquidacion",
            "notas", "id",
        )
    }
}
