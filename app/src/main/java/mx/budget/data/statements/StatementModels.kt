package mx.budget.data.statements

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Esquema estructurado que el LLM devuelve al analizar el texto de un estado de
 * cuenta (Fase C, paquete C1). Todos los campos son nullable: el LLM omite lo que
 * no encuentra y el usuario completa/corrige en el preview editable. Fechas en ISO
 * `YYYY-MM-DD`; montos en MXN positivos (cargos).
 */
@Serializable
data class ParsedStatement(
    val emisor: String? = null,
    val last4: String? = null,
    val periodo: StatementPeriod? = null,
    val fechaCorte: String? = null,
    val fechaLimitePago: String? = null,
    val saldoTotal: Double? = null,
    val pagoMinimo: Double? = null,
    val pagoNoIntereses: Double? = null,
    val tasaAnual: Double? = null,
    val movimientos: List<StatementMovement> = emptyList(),
)

@Serializable
data class StatementPeriod(
    val inicio: String? = null,
    val fin: String? = null,
)

@Serializable
data class StatementMovement(
    val fecha: String? = null,
    val concepto: String? = null,
    val monto: Double? = null,
    val esMsi: Boolean = false,
    @SerialName("msiPlazo") val msiPlazo: Int? = null,
    @SerialName("msiNumero") val msiNumero: Int? = null,
)
