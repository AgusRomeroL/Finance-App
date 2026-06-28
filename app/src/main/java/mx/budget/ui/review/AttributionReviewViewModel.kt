package mx.budget.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import mx.budget.ai.proactive.SuggestedShare
import mx.budget.data.local.dao.AttributionReviewDao
import mx.budget.data.local.dao.ExpenseDao
import mx.budget.data.local.entity.AttributionReviewEntity
import mx.budget.data.local.entity.ExpenseEntity
import mx.budget.data.local.entity.MemberEntity
import mx.budget.data.repository.ExpenseRepository
import mx.budget.data.repository.MemberRepository
import kotlin.math.max
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// Modelo de UI — sugerencias agrupadas por concepto canónico (Apéndice F.3.7)
// ─────────────────────────────────────────────────────────────────────────────

/** En qué cubeta de la cola cae el grupo. */
enum class ReviewBucket { PENDING, AUTO_APPLIED }

/** Banda de confianza para la insignia (redundancia no-cromática en la UI). */
enum class ConfidenceBand { ALTA, MEDIA, BAJA }

/** Una porción de la distribución sugerida, lista para pintar (nombre + %). */
data class MemberShareUi(
    val memberId: String,
    val name: String,
    val pct: Int
)

/**
 * Sugerencia de una dimensión (BENEFICIARY o PAYER) para un grupo de gastos que
 * comparten clave canónica. [distributionBps] es la fuente autoritativa (suma
 * 10,000); [shares] es solo para mostrar.
 */
data class RoleSuggestionUi(
    val role: String,
    val distributionBps: Map<String, Int>,
    val shares: List<MemberShareUi>,
    val sampleSize: Int,
    val confidence: Double,
    val reviewIds: List<String>,
    val expenseIds: List<String>
)

/** Un grupo de revisión = un concepto canónico, con sus dos dimensiones. */
data class ReviewGroup(
    val canonicalKey: String,
    val label: String,
    val expenseCount: Int,
    val bucket: ReviewBucket,
    val band: ConfidenceBand,
    val beneficiary: RoleSuggestionUi?,
    val payer: RoleSuggestionUi?
) {
    /** Clave estable de UI (evita colisiones entre cubetas con misma canonical). */
    val key: String get() = "$bucket:$canonicalKey"
    val confidence: Double get() = max(beneficiary?.confidence ?: 0.0, payer?.confidence ?: 0.0)
}

data class AttributionReviewUiState(
    val pending: List<ReviewGroup> = emptyList(),
    val autoApplied: List<ReviewGroup> = emptyList(),
    val loading: Boolean = true
) {
    val isEmpty: Boolean get() = pending.isEmpty() && autoApplied.isEmpty()
}

// ─────────────────────────────────────────────────────────────────────────────
// AttributionReviewViewModel
// ─────────────────────────────────────────────────────────────────────────────

/**
 * ViewModel de la pantalla "Revisión de atribuciones" (Feature B, Apéndice F.3.7).
 *
 * Observa la cola `attribution_review` (PENDING + AUTO_APPLIED), la cruza con el
 * concepto/monto de cada gasto y los nombres de miembro, y la agrupa por concepto
 * canónico para revisión en lote. Las acciones escriben en `expense_attribution`
 * vía [ExpenseRepository.applyAttributionForRole] (que encola sync) y actualizan
 * el estado de la review (CONFIRMED/EDITED/REJECTED/PENDING).
 */
class AttributionReviewViewModel(
    private val reviewDao: AttributionReviewDao,
    private val expenseDao: ExpenseDao,
    private val expenseRepository: ExpenseRepository,
    private val memberRepository: MemberRepository,
    private val householdId: String
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Miembros del hogar para sembrar el editor de % al editar un grupo. Excluye
     * terceros (EXTERNAL_*: acreedores/deudores/proveedores), igual que la captura
     * — no son beneficiarios/pagadores válidos del reparto interno.
     */
    val members: StateFlow<List<MemberEntity>> = memberRepository
        .observeActiveMembers(householdId)
        .map { list -> list.filter { !it.role.startsWith("EXTERNAL_") } }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<AttributionReviewUiState> = combine(
        reviewDao.observePending(),
        reviewDao.observeAutoApplied(),
        memberRepository.observeActiveMembers(householdId)
    ) { pending, auto, members ->
        // Concepto + monto de cada gasto para etiquetar los grupos. ~800 filas,
        // relectura puntual al cambiar la cola — barato.
        val expenses = expenseDao.getAll(householdId).associateBy { it.id }
        val names = members.associate { it.id to it.displayName }
        AttributionReviewUiState(
            pending = buildGroups(pending, names, expenses, ReviewBucket.PENDING),
            autoApplied = buildGroups(auto, names, expenses, ReviewBucket.AUTO_APPLIED),
            loading = false
        )
    }
        .catch { emit(AttributionReviewUiState(loading = false)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AttributionReviewUiState())

    private fun buildGroups(
        reviews: List<AttributionReviewEntity>,
        names: Map<String, String>,
        expenses: Map<String, ExpenseEntity>,
        bucket: ReviewBucket
    ): List<ReviewGroup> {
        return reviews.groupBy { it.conceptCanonical ?: "" }.map { (canonicalKey, rows) ->
            val groupExpenses = rows.mapNotNull { expenses[it.expenseId] }
            val expenseIds = rows.map { it.expenseId }.distinct()
            val label = groupExpenses
                .map { it.concept }
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key
                ?: canonicalKey.ifBlank { "Sin concepto" }

            val byRole = rows.groupBy { it.role }
            val beneficiary = byRole["BENEFICIARY"]?.let { roleSuggestion("BENEFICIARY", it, names) }
            val payer = byRole["PAYER"]?.let { roleSuggestion("PAYER", it, names) }

            val conf = max(beneficiary?.confidence ?: 0.0, payer?.confidence ?: 0.0)
            val band = when {
                bucket == ReviewBucket.AUTO_APPLIED -> ConfidenceBand.ALTA
                conf >= 0.4 -> ConfidenceBand.MEDIA
                else -> ConfidenceBand.BAJA
            }

            ReviewGroup(
                canonicalKey = canonicalKey,
                label = label,
                expenseCount = expenseIds.size,
                bucket = bucket,
                band = band,
                beneficiary = beneficiary,
                payer = payer
            )
        }.sortedByDescending { it.confidence }
    }

    private fun roleSuggestion(
        role: String,
        rows: List<AttributionReviewEntity>,
        names: Map<String, String>
    ): RoleSuggestionUi {
        // Todas las filas de un (canonical, role) comparten distribución (el motor
        // infiere por clave canónica), así que el representante basta para mostrar.
        val rep = rows.first()
        val dist = runCatching {
            json.decodeFromString<List<SuggestedShare>>(rep.suggestedJson)
        }.getOrDefault(emptyList())
        return RoleSuggestionUi(
            role = role,
            distributionBps = dist.associate { it.memberId to it.shareBps },
            shares = dist.map {
                MemberShareUi(it.memberId, names[it.memberId] ?: "—", (it.shareBps / 100.0).roundToInt())
            },
            sampleSize = rep.sampleSize,
            confidence = rep.confidence,
            reviewIds = rows.map { it.id },
            expenseIds = rows.map { it.expenseId }.distinct()
        )
    }

    // ── Acciones ───────────────────────────────────────────────────────────────

    /** Aplica la sugerencia tal cual a todos los gastos del grupo (CONFIRMED). */
    fun applyGroup(group: ReviewGroup) = viewModelScope.launch {
        listOfNotNull(group.beneficiary, group.payer).forEach { role ->
            role.expenseIds.forEach { expenseId ->
                expenseRepository.applyAttributionForRole(expenseId, role.role, role.distributionBps)
            }
            role.reviewIds.forEach { reviewDao.updateStatus(it, STATUS_CONFIRMED) }
        }
    }

    /**
     * Aplica una distribución EDITADA por el usuario (mapa memberId→% que suma 100)
     * a la dimensión correspondiente del grupo (EDITED). Mapas null = no tocar ese rol.
     */
    fun applyEdited(
        group: ReviewGroup,
        beneficiaryPct: Map<String, Int>?,
        payerPct: Map<String, Int>?
    ) = viewModelScope.launch {
        group.beneficiary?.let { role ->
            if (beneficiaryPct != null) applyRoleEdited(role, beneficiaryPct)
        }
        group.payer?.let { role ->
            if (payerPct != null) applyRoleEdited(role, payerPct)
        }
    }

    private suspend fun applyRoleEdited(role: RoleSuggestionUi, pct: Map<String, Int>) {
        val bps = pctToBps(pct)
        role.expenseIds.forEach { expenseId ->
            expenseRepository.applyAttributionForRole(expenseId, role.role, bps)
        }
        role.reviewIds.forEach { reviewDao.updateStatus(it, STATUS_EDITED) }
    }

    /**
     * Marca un grupo auto-aplicado como "visto": conserva la atribución que la
     * máquina aplicó y pasa la review a CONFIRMED (decisión humana). Sale de la
     * sección de auto-aplicados y el worker ya no la re-toca en re-normalizaciones.
     */
    fun acknowledgeGroup(group: ReviewGroup) = viewModelScope.launch {
        listOfNotNull(group.beneficiary, group.payer).forEach { role ->
            role.reviewIds.forEach { reviewDao.updateStatus(it, STATUS_CONFIRMED) }
        }
    }

    /** Ignora el grupo: marca REJECTED (señal negativa) sin tocar atribuciones. */
    fun ignoreGroup(group: ReviewGroup) = viewModelScope.launch {
        listOfNotNull(group.beneficiary, group.payer).forEach { role ->
            role.reviewIds.forEach { reviewDao.updateStatus(it, STATUS_REJECTED) }
        }
    }

    /**
     * Revierte un grupo auto-aplicado: borra la atribución máquina de cada gasto y
     * devuelve la review a PENDING (vuelve a la cola accionable para revisión manual).
     */
    fun revertGroup(group: ReviewGroup) = viewModelScope.launch {
        listOfNotNull(group.beneficiary, group.payer).forEach { role ->
            role.expenseIds.forEach { expenseId ->
                expenseRepository.applyAttributionForRole(expenseId, role.role, emptyMap())
            }
            role.reviewIds.forEach { reviewDao.updateStatus(it, STATUS_PENDING) }
        }
    }

    /** % (suma 100) → bps (suma 10,000); el último absorbe el resto. Mapa vacío = limpia. */
    private fun pctToBps(pct: Map<String, Int>): Map<String, Int> {
        val entries = pct.entries.filter { it.value > 0 }
        if (entries.isEmpty()) return emptyMap()
        var assigned = 0
        return entries.mapIndexed { i, (memberId, p) ->
            val bps = if (i == entries.lastIndex) 10_000 - assigned else (p * 100).also { assigned += it }
            memberId to bps
        }.toMap()
    }

    companion object {
        private const val STATUS_PENDING = "PENDING"
        private const val STATUS_CONFIRMED = "CONFIRMED"
        private const val STATUS_EDITED = "EDITED"
        private const val STATUS_REJECTED = "REJECTED"
    }
}
