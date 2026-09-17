package mx.budget.ui.capture

/**
 * Aritmetica pura de los repartos de la captura: la interfaz edita porcentajes
 * enteros por miembro y el ledger guarda basis points. Las dos particiones de
 * un gasto (BENEFICIARY y PAYER) tienen que sumar exactamente 10,000 bps, asi
 * que en cada conversion el ultimo miembro absorbe el resto de la division.
 *
 * Vive fuera del ViewModel para que se pueda probar en la JVM sin construirlo.
 */
object AttributionShares {

    const val TOTAL_PCT = 100
    const val TOTAL_BPS = 10_000

    /** Convierte un mapa memberId a bps (suma 10,000) a memberId a % (suma 100). */
    fun bpsToPercent(distribution: Map<String, Int>): Map<String, Int> {
        val entries = distribution.entries.toList()
        if (entries.isEmpty()) return emptyMap()
        var assigned = 0
        return entries.mapIndexed { i, (memberId, bps) ->
            val pct = if (i == entries.lastIndex) TOTAL_PCT - assigned
            else (bps / 100).also { assigned += it }
            memberId to pct
        }.toMap()
    }

    /**
     * Convierte un mapa memberId a % (suma 100) a memberId a bps: el ultimo
     * absorbe el resto para sumar 10,000 exactos. Conserva el orden de entrada.
     */
    fun percentToBps(shares: Map<String, Int>): Map<String, Int> {
        val entries = shares.entries.toList()
        if (entries.isEmpty()) return emptyMap()
        var assigned = 0
        return entries.mapIndexed { i, (memberId, pct) ->
            val bps = if (i == entries.lastIndex) TOTAL_BPS - assigned
            else (pct * 100).also { assigned += it }
            memberId to bps
        }.toMap()
    }

    /** Reparte 100% equitativamente entre [ids], con el resto en el ultimo. */
    fun equalSplit(ids: Collection<String>): Map<String, Int> {
        val list = ids.toList()
        if (list.isEmpty()) return emptyMap()
        val base = TOTAL_PCT / list.size
        val remainder = TOTAL_PCT - base * list.size
        return list.mapIndexed { i, id ->
            id to if (i == list.lastIndex) base + remainder else base
        }.toMap()
    }
}
