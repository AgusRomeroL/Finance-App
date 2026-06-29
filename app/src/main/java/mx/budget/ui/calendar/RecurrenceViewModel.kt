package mx.budget.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mx.budget.data.local.entity.CategoryEntity
import mx.budget.data.local.entity.MemberEntity
import mx.budget.data.local.entity.PaymentMethodEntity
import mx.budget.data.local.entity.RecurrenceTemplateEntity
import mx.budget.data.recurrence.RecurrenceMaterializer
import mx.budget.data.repository.CategoryRepository
import mx.budget.data.repository.MemberRepository
import mx.budget.data.repository.QuincenaRepository
import mx.budget.data.repository.RecurrenceRepository
import mx.budget.data.repository.WalletRepository
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Cadencias soportadas por el editor (CUSTOM_CRON queda diferido, §G.2.3). */
enum class Cadence(val code: String, val label: String) {
    QUINCENAL_FIRST("QUINCENAL_FIRST", "1ª quincena (1-15)"),
    QUINCENAL_SECOND("QUINCENAL_SECOND", "2ª quincena (16-fin)"),
    QUINCENAL_EVERY("QUINCENAL_EVERY", "Cada quincena"),
    MONTHLY_SPECIFIC_HALF("MONTHLY_SPECIFIC_HALF", "Mensual (día específico)"),
    BIMONTHLY("BIMONTHLY", "Bimestral");

    companion object {
        fun fromCode(code: String): Cadence = entries.firstOrNull { it.code == code } ?: QUINCENAL_FIRST
    }
}

/** Estado de guardado del editor de plantillas. */
sealed interface TemplateSaveState {
    data object Idle : TemplateSaveState
    data object Saving : TemplateSaveState
    data class Error(val message: String) : TemplateSaveState
}

/**
 * ViewModel de gestión de plantillas recurrentes (Apéndice G.2, Fase 4 inc. 2c):
 * lista (activas + pausadas), crear/editar (cadencia, monto, día, lead, splits) y
 * pausar/reanudar/eliminar. El editor serializa a los mismos formatos JSON que lee
 * el [RecurrenceMaterializer] (`cadence_detail`, `default_payer_split` y, para
 * beneficiarios con % personalizado, el objeto `{id:bps}` que el materializer ya
 * acepta). Tras guardar materializa la quincena activa para que el PLANNED aparezca.
 */
class RecurrenceViewModel(
    private val householdId: String,
    private val recurrenceRepository: RecurrenceRepository,
    private val categoryRepository: CategoryRepository,
    private val walletRepository: WalletRepository,
    private val memberRepository: MemberRepository,
    private val quincenaRepository: QuincenaRepository,
    private val materializer: RecurrenceMaterializer,
) : ViewModel() {

    val templates: StateFlow<List<RecurrenceTemplateEntity>> =
        recurrenceRepository.observeAll(householdId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> =
        categoryRepository.observeAll(householdId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val wallets: StateFlow<List<PaymentMethodEntity>> =
        walletRepository.observeActive(householdId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val members: StateFlow<List<MemberEntity>> =
        memberRepository.observeActiveMembers(householdId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Estado del editor ───────────────────────────────────────────────────────
    private val _editorVisible = MutableStateFlow(false)
    val editorVisible: StateFlow<Boolean> = _editorVisible.asStateFlow()

    private var editingId: String? = null
    private var editingConfidence: Double = 0.0
    private var editingLearned: String = "[]"

    private val _concept = MutableStateFlow("")
    val concept: StateFlow<String> = _concept.asStateFlow()
    private val _amountText = MutableStateFlow("")
    val amountText: StateFlow<String> = _amountText.asStateFlow()
    private val _categoryId = MutableStateFlow<String?>(null)
    val categoryId: StateFlow<String?> = _categoryId.asStateFlow()
    private val _walletId = MutableStateFlow<String?>(null)
    val walletId: StateFlow<String?> = _walletId.asStateFlow()
    private val _cadence = MutableStateFlow(Cadence.QUINCENAL_FIRST)
    val cadence: StateFlow<Cadence> = _cadence.asStateFlow()
    private val _dayText = MutableStateFlow("1")
    val dayText: StateFlow<String> = _dayText.asStateFlow()
    private val _leadDays = MutableStateFlow(2)
    val leadDays: StateFlow<Int> = _leadDays.asStateFlow()
    private val _leadQuincenaStart = MutableStateFlow(false)
    val leadQuincenaStart: StateFlow<Boolean> = _leadQuincenaStart.asStateFlow()
    private val _beneficiaryShares = MutableStateFlow<Map<String, Int>>(emptyMap())
    val beneficiaryShares: StateFlow<Map<String, Int>> = _beneficiaryShares.asStateFlow()
    private val _payerShares = MutableStateFlow<Map<String, Int>>(emptyMap())
    val payerShares: StateFlow<Map<String, Int>> = _payerShares.asStateFlow()
    private val _saveState = MutableStateFlow<TemplateSaveState>(TemplateSaveState.Idle)
    val saveState: StateFlow<TemplateSaveState> = _saveState.asStateFlow()

    // ── Apertura del editor ───────────────────────────────────────────────────────

    fun openNew() {
        editingId = null
        editingConfidence = 0.0
        editingLearned = "[]"
        _concept.value = ""
        _amountText.value = ""
        _categoryId.value = null
        _cadence.value = Cadence.QUINCENAL_FIRST
        _dayText.value = "1"
        _leadDays.value = 2
        _leadQuincenaStart.value = false
        _beneficiaryShares.value = emptyMap()
        _payerShares.value = emptyMap()
        _saveState.value = TemplateSaveState.Idle
        _editorVisible.value = true
        viewModelScope.launch { applyDefaultSplits() }
    }

    fun openEdit(t: RecurrenceTemplateEntity) {
        editingId = t.id
        editingConfidence = t.confidenceScore
        editingLearned = t.learnedFromExpenseIds
        _concept.value = t.concept
        _amountText.value = t.defaultAmountMxn.toLong().toString()
        _categoryId.value = t.categoryId
        _walletId.value = t.defaultPaymentMethodId
        _cadence.value = Cadence.fromCode(t.cadence)
        val detail = runCatching { JSONObject(t.cadenceDetail) }.getOrDefault(JSONObject())
        _dayText.value = detail.optInt("day_of_month", 1).toString()
        val lead = detail.opt("reminder_lead_days")
        if (lead is String && lead == "QUINCENA_START") {
            _leadQuincenaStart.value = true
            _leadDays.value = 2
        } else {
            _leadQuincenaStart.value = false
            _leadDays.value = (lead as? Int) ?: detail.optInt("reminder_lead_days", 2)
        }
        _beneficiaryShares.value = parseSharesToPercent(t.defaultBeneficiaryIds)
        _payerShares.value = parseSharesToPercent(t.defaultPayerSplit)
        _saveState.value = TemplateSaveState.Idle
        _editorVisible.value = true
    }

    fun closeEditor() { _editorVisible.value = false }

    // ── Setters del form ──────────────────────────────────────────────────────────
    fun onConcept(v: String) { _concept.value = v }
    fun onAmount(v: String) { _amountText.value = v.filter { it.isDigit() || it == '.' } }
    fun onCategory(id: String) { _categoryId.value = id }
    fun onWallet(id: String) { _walletId.value = id }
    fun onCadence(c: Cadence) { _cadence.value = c }
    fun onDay(v: String) { _dayText.value = v.filter { it.isDigit() }.take(2) }
    fun onLeadDays(d: Int) { _leadDays.value = d; _leadQuincenaStart.value = false }
    fun onLeadQuincenaStart() { _leadQuincenaStart.value = true }

    fun onBeneficiaryToggle(id: String) {
        val ids = _beneficiaryShares.value.keys.toMutableSet().apply { if (!add(id)) remove(id) }
        _beneficiaryShares.value = equalSplit(ids)
    }
    fun onBeneficiaryDelta(id: String, d: Int) {
        val cur = _beneficiaryShares.value
        if (id !in cur) return
        _beneficiaryShares.value = cur + (id to (cur.getValue(id) + d).coerceIn(0, 100))
    }
    fun onBeneficiaryAll() { _beneficiaryShares.value = equalSplit(activeMemberIds()) }
    fun onBeneficiaryClear() { _beneficiaryShares.value = emptyMap() }

    fun onPayerToggle(id: String) {
        val ids = _payerShares.value.keys.toMutableSet().apply { if (!add(id)) remove(id) }
        _payerShares.value = equalSplit(ids)
    }
    fun onPayerDelta(id: String, d: Int) {
        val cur = _payerShares.value
        if (id !in cur) return
        _payerShares.value = cur + (id to (cur.getValue(id) + d).coerceIn(0, 100))
    }
    fun onPayerAll() { _payerShares.value = equalSplit(activeMemberIds()) }
    fun onPayerClear() { _payerShares.value = emptyMap() }

    // ── Acciones de lista ─────────────────────────────────────────────────────────
    fun pause(id: String) { viewModelScope.launch { recurrenceRepository.pause(id) } }
    fun resume(id: String) { viewModelScope.launch { recurrenceRepository.resume(id) } }
    fun delete(t: RecurrenceTemplateEntity) { viewModelScope.launch { recurrenceRepository.delete(t) } }

    // ── Guardado ──────────────────────────────────────────────────────────────────
    fun save() {
        if (_saveState.value is TemplateSaveState.Saving) return
        viewModelScope.launch {
            _saveState.value = TemplateSaveState.Saving
            try {
                val amount = _amountText.value.toDoubleOrNull()
                    ?: throw IllegalArgumentException("Ingresa un monto válido.")
                if (amount <= 0) throw IllegalArgumentException("El monto debe ser mayor a 0.")
                val concept = _concept.value.trim().ifBlank { throw IllegalArgumentException("Escribe un concepto.") }
                val categoryId = _categoryId.value ?: throw IllegalArgumentException("Selecciona una categoría.")
                val benef = _beneficiaryShares.value
                val payer = _payerShares.value
                if (benef.isEmpty() || benef.values.sum() != 100) {
                    throw IllegalArgumentException("Los beneficiarios deben sumar 100%.")
                }
                if (payer.isEmpty() || payer.values.sum() != 100) {
                    throw IllegalArgumentException("Los pagadores deben sumar 100%.")
                }

                val detail = JSONObject().apply {
                    put("day_of_month", _dayText.value.toIntOrNull()?.coerceIn(1, 31) ?: 1)
                    put("reminder_lead_days", if (_leadQuincenaStart.value) "QUINCENA_START" else _leadDays.value)
                }
                val template = RecurrenceTemplateEntity(
                    id = editingId ?: UUID.randomUUID().toString(),
                    householdId = householdId,
                    concept = concept.take(64),
                    categoryId = categoryId,
                    defaultAmountMxn = amount,
                    defaultPaymentMethodId = _walletId.value,
                    cadence = _cadence.value.code,
                    cadenceDetail = detail.toString(),
                    defaultBeneficiaryIds = sharesToBpsJson(benef),
                    defaultPayerSplit = sharesToBpsJson(payer),
                    isActive = true,
                    confidenceScore = editingConfidence,
                    learnedFromExpenseIds = editingLearned,
                )
                if (editingId == null) recurrenceRepository.insert(template)
                else recurrenceRepository.update(template)

                // Materializa la quincena activa para reflejar el PLANNED de inmediato
                // (idempotente; no re-crea si ya existía para esta quincena).
                runCatching {
                    quincenaRepository.getActive(householdId)?.let { materializer.materialize(it) }
                }
                _editorVisible.value = false
                _saveState.value = TemplateSaveState.Idle
            } catch (e: Exception) {
                _saveState.value = TemplateSaveState.Error(e.message ?: "No se pudo guardar.")
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────
    private suspend fun applyDefaultSplits() {
        val active = memberRepository.observeActiveMembers(householdId).first()
            .filter { !it.role.startsWith("EXTERNAL_") }
        if (active.isNotEmpty()) _beneficiaryShares.value = equalSplit(active.map { it.id })
        val w = walletRepository.observeActive(householdId).first()
        if (_walletId.value == null) _walletId.value = w.firstOrNull()?.id
        val owner = w.firstOrNull()?.ownerMemberId
            ?: active.firstOrNull { it.role == "PAYER_ADULT" }?.id
            ?: active.firstOrNull()?.id
        if (owner != null) _payerShares.value = mapOf(owner to 100)
    }

    private fun activeMemberIds() =
        members.value.filter { !it.role.startsWith("EXTERNAL_") }.map { it.id }

    private fun equalSplit(ids: Collection<String>): Map<String, Int> {
        if (ids.isEmpty()) return emptyMap()
        val list = ids.toList()
        val base = 100 / list.size
        val rem = 100 - base * list.size
        return list.mapIndexed { i, id -> id to if (i == list.lastIndex) base + rem else base }.toMap()
    }

    /** % por miembro → JSON `{id:bps}` (suma 10,000; el último absorbe el resto). */
    private fun sharesToBpsJson(shares: Map<String, Int>): String {
        val entries = shares.entries.toList()
        var assigned = 0
        val obj = JSONObject()
        entries.forEachIndexed { i, (id, pct) ->
            val bps = if (i == entries.lastIndex) 10_000 - assigned else (pct * 100).also { assigned += it }
            obj.put(id, bps)
        }
        return obj.toString()
    }

    /** JSON `{id:bps}` (nuevo) o `[id,...]` (histórico) → % por miembro para el editor. */
    private fun parseSharesToPercent(json: String): Map<String, Int> {
        runCatching {
            val obj = JSONObject(json)
            val map = obj.keys().asSequence().associateWith { obj.getInt(it) }.filterValues { it > 0 }
            if (map.isNotEmpty()) return map.mapValues { (it.value / 100).coerceIn(0, 100) }
        }
        runCatching {
            val arr = JSONArray(json)
            val ids = (0 until arr.length()).map { arr.getString(it) }.filter { it.isNotBlank() }
            if (ids.isNotEmpty()) return equalSplit(ids)
        }
        return emptyMap()
    }
}
