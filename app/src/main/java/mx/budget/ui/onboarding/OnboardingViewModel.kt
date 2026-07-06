package mx.budget.ui.onboarding

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.budget.data.local.dao.HouseholdDao
import mx.budget.data.local.entity.CategoryEntity
import mx.budget.data.local.entity.HouseholdEntity
import mx.budget.data.local.entity.MemberEntity
import mx.budget.data.local.entity.PaymentMethodEntity
import mx.budget.data.quincena.QuincenaRollover
import mx.budget.data.repository.CategoryRepository
import mx.budget.data.repository.MemberRepository
import mx.budget.data.repository.QuincenaRepository
import mx.budget.data.repository.WalletRepository
import org.json.JSONObject
import java.util.UUID

/**
 * ViewModel del **wizard de onboarding** (paquete B2, Fase B). Solo se muestra la
 * primera vez, cuando la base de datos está vacía (sin miembros ni gastos): la
 * instalación de Norma (semilla de 793 gastos) entra directo al dashboard sin
 * pasar por aquí. Ver la detección en [mx.budget.BudgetApplication.needsOnboarding].
 *
 * Los pasos siembran datos localmente vía los repos públicos (Room = fuente de
 * verdad). La creación es idempotente: si el usuario reinicia el wizard a medias,
 * el household usa un id determinista y los miembros/wallets/categorías ya creados
 * no se duplican (se reconcilian por nombre/código antes de insertar).
 */
class OnboardingViewModel(
    private val appContext: Context,
    private val householdId: String,
    private val householdDao: HouseholdDao,
    private val memberRepository: MemberRepository,
    private val walletRepository: WalletRepository,
    private val categoryRepository: CategoryRepository,
    private val quincenaRepository: QuincenaRepository,
    private val quincenaDao: mx.budget.data.local.dao.QuincenaDao,
    /** Registra el hogar en la nube si hay usuario Google (opcional). */
    private val onCreateCloudHousehold: (suspend (name: String) -> Unit)? = null,
) : ViewModel() {

    // ── Modelos de captura en memoria (aún no persistidos) ──────────────────────

    data class DraftMember(
        val localId: String = UUID.randomUUID().toString(),
        val name: String,
        val role: String, // PAYER_ADULT | BENEFICIARY_DEPENDENT
    )

    data class DraftWallet(
        val localId: String = UUID.randomUUID().toString(),
        val name: String,
        val kind: String, // DEBIT_ACCOUNT | CREDIT_CARD | CASH
        val openingBalance: Double,
    )

    /** Un grupo/hoja de la plantilla de categorías, con su estado de selección. */
    data class CategoryOption(
        val code: String,
        val name: String,
        val kind: String,
        val icon: String?,
        val color: String?,
        val budget: Double?,
        val parentCode: String?, // null = grupo raíz
        val selected: Boolean,
    )

    data class UiState(
        val step: Int = 0, // 0..4
        val householdName: String = "",
        val members: List<DraftMember> = emptyList(),
        val wallets: List<DraftWallet> = emptyList(),
        val categories: List<CategoryOption> = emptyList(),
        val busy: Boolean = false,
        val finished: Boolean = false,
    ) {
        /** Al menos un adulto pagador (invariante del paso de miembros). */
        val hasPayerAdult: Boolean get() = members.any { it.role == "PAYER_ADULT" }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(categories = loadCategoryTemplate()) }
    }

    // ── Navegación entre pasos ──────────────────────────────────────────────────

    fun goToStep(step: Int) = _uiState.update { it.copy(step = step.coerceIn(0, LAST_STEP)) }
    fun next() = _uiState.update { it.copy(step = (it.step + 1).coerceAtMost(LAST_STEP)) }
    fun back() = _uiState.update { it.copy(step = (it.step - 1).coerceAtLeast(0)) }

    // ── Paso 1: household ────────────────────────────────────────────────────────

    fun setHouseholdName(name: String) = _uiState.update { it.copy(householdName = name) }

    // ── Paso 2: miembros ─────────────────────────────────────────────────────────

    fun addMember(name: String, role: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        _uiState.update { it.copy(members = it.members + DraftMember(name = clean, role = role)) }
    }

    fun removeMember(localId: String) =
        _uiState.update { it.copy(members = it.members.filterNot { m -> m.localId == localId }) }

    // ── Paso 3: wallets ──────────────────────────────────────────────────────────

    fun addWallet(name: String, kind: String, openingBalance: Double) {
        val clean = name.trim()
        if (clean.isBlank()) return
        _uiState.update {
            it.copy(wallets = it.wallets + DraftWallet(name = clean, kind = kind, openingBalance = openingBalance))
        }
    }

    fun removeWallet(localId: String) =
        _uiState.update { it.copy(wallets = it.wallets.filterNot { w -> w.localId == localId }) }

    // ── Paso 4: categorías ───────────────────────────────────────────────────────

    fun toggleCategory(code: String) = _uiState.update { state ->
        state.copy(categories = state.categories.map { c ->
            if (c.code == code) c.copy(selected = !c.selected) else c
        })
    }

    // ── Paso 5: commit final ─────────────────────────────────────────────────────

    /**
     * Persiste todo el borrador y garantiza la quincena activa de hoy. Idempotente:
     * el household usa el id resuelto por la app (determinista) y las categorías
     * padre se crean una sola vez. Al terminar marca [UiState.finished] para que la
     * pantalla navegue al dashboard.
     */
    fun finish() {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            runCatching { commit() }
                .onFailure { Log.e(TAG, "onboarding commit falló", it) }
            _uiState.update { it.copy(busy = false, finished = true) }
        }
    }

    private suspend fun commit() {
        val state = _uiState.value
        val now = System.currentTimeMillis()

        // 1) Household (Room). Idempotente: si ya existe, no lo duplica (el id es
        // el resuelto por la app, determinista; REPLACE en el DAO).
        val existingId = householdDao.getSingleId()
        val hid = existingId ?: householdId
        householdDao.insert(
            HouseholdEntity(
                id = hid,
                name = state.householdName.ifBlank { "Mi hogar" },
                currency = "MXN",
                timezone = "America/Mexico_City",
                createdAt = now,
                updatedAt = now,
            )
        )
        // Nube (opcional): registra el hogar si hay usuario Google.
        runCatching { onCreateCloudHousehold?.invoke(state.householdName.ifBlank { "Mi hogar" }) }

        // 2) Miembros.
        val members = state.members.map { d ->
            MemberEntity(
                id = d.localId,
                householdId = hid,
                displayName = d.name,
                role = d.role,
                isActive = true,
                updatedAt = now,
            )
        }
        if (members.isNotEmpty()) members.forEach { memberRepository.insert(it) }

        // 3) Wallets.
        state.wallets.forEach { d ->
            walletRepository.insert(
                PaymentMethodEntity(
                    id = d.localId,
                    householdId = hid,
                    displayName = d.name,
                    kind = d.kind,
                    currentBalanceMxn = d.openingBalance,
                    openingBalanceMxn = d.openingBalance,
                    isActive = true,
                )
            )
        }

        // 4) Categorías base (padres primero para respetar la FK parent_id).
        seedCategories(hid, state.categories.filter { it.selected }, now)

        // 5) Primera quincena activa (reusa el rollover determinista).
        runCatching {
            QuincenaRollover(dao = quincenaDao, householdId = hid).ensureActiveForToday()
        }.onFailure { Log.w(TAG, "ensureActiveForToday falló en onboarding", it) }
    }

    private suspend fun seedCategories(hid: String, selected: List<CategoryOption>, now: Long) {
        if (selected.isEmpty()) return
        // Determina qué grupos padre necesitamos (los de las hojas elegidas).
        val neededParentCodes = selected.mapNotNull { it.parentCode }.toSet()
        val template = _uiState.value.categories
        val codeToId = HashMap<String, String>()
        var order = 0

        // 4a) Padres (los grupos necesarios, aunque el usuario no los marcara como hoja).
        for (parentCode in neededParentCodes) {
            val group = template.firstOrNull { it.code == parentCode && it.parentCode == null } ?: continue
            val existing = categoryRepository.getByCode(hid, parentCode)
            val id = existing?.id ?: UUID.randomUUID().toString()
            codeToId[parentCode] = id
            if (existing == null) {
                categoryRepository.insert(
                    CategoryEntity(
                        id = id,
                        householdId = hid,
                        parentId = null,
                        code = parentCode,
                        displayName = group.name,
                        icon = group.icon,
                        colorHex = group.color,
                        kind = "EXPENSE_VARIABLE",
                        budgetDefaultMxn = null,
                        sortOrder = order++,
                        updatedAt = now,
                    )
                )
            }
        }

        // 4b) Hojas seleccionadas.
        for (leaf in selected.filter { it.parentCode != null }) {
            val existing = categoryRepository.getByCode(hid, leaf.code)
            if (existing != null) continue
            val parentId = leaf.parentCode?.let { codeToId[it] ?: categoryRepository.getByCode(hid, it)?.id }
            categoryRepository.insert(
                CategoryEntity(
                    id = UUID.randomUUID().toString(),
                    householdId = hid,
                    parentId = parentId,
                    code = leaf.code,
                    displayName = leaf.name,
                    icon = leaf.icon,
                    colorHex = leaf.color,
                    kind = leaf.kind,
                    budgetDefaultMxn = leaf.budget,
                    sortOrder = order++,
                    updatedAt = now,
                )
            )
        }
    }

    /** Lee `assets/onboarding/default_categories.es.json` → lista plana de opciones. */
    private fun loadCategoryTemplate(): List<CategoryOption> = runCatching {
        val json = appContext.assets.open("onboarding/default_categories.es.json")
            .bufferedReader().use { it.readText() }
        val root = JSONObject(json)
        val groups = root.getJSONArray("groups")
        val out = ArrayList<CategoryOption>()
        for (i in 0 until groups.length()) {
            val g = groups.getJSONObject(i)
            val gCode = g.getString("code")
            val gName = g.getString("name")
            val gIcon = g.optString("icon").ifBlank { null }
            val gColor = g.optString("color").ifBlank { null }
            // El grupo raíz también aparece como opción (encabezado), sin selección directa.
            out.add(
                CategoryOption(
                    code = gCode, name = gName, kind = "EXPENSE_VARIABLE",
                    icon = gIcon, color = gColor, budget = null,
                    parentCode = null, selected = false,
                )
            )
            val children = g.getJSONArray("children")
            for (j in 0 until children.length()) {
                val c = children.getJSONObject(j)
                out.add(
                    CategoryOption(
                        code = c.getString("code"),
                        name = c.getString("name"),
                        kind = c.optString("kind").ifBlank { "EXPENSE_VARIABLE" },
                        icon = gIcon,
                        color = gColor,
                        budget = if (c.has("budget")) c.getDouble("budget") else null,
                        parentCode = gCode,
                        selected = c.optBoolean("defaultOn", true),
                    )
                )
            }
        }
        out
    }.getOrElse {
        Log.w(TAG, "No se pudo cargar la plantilla de categorías", it)
        emptyList()
    }

    private companion object {
        const val TAG = "OnboardingVM"
        const val LAST_STEP = 4
    }
}
