package mx.budget.ui.masters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mx.budget.data.local.entity.CategoryEntity
import mx.budget.data.local.entity.IncomeSourceEntity
import mx.budget.data.local.entity.MemberEntity
import mx.budget.data.repository.CategoryRepository
import mx.budget.data.repository.IncomeRepository
import mx.budget.data.repository.MemberRepository
import mx.budget.data.repository.QuincenaRepository
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

private val ZONE: ZoneId = ZoneId.of("America/Mexico_City")

// ─────────────────────────────────────────────────────────────────────────────
// CRUD de miembros (paquete B2). LOCAL-ONLY: MemberRepositoryImpl no encola en
// sync_queue ni el SyncManager draina MEMBER. Al editar estampamos updated_at
// manualmente para que, cuando se cablee el push de MEMBER, el LWW ya sea correcto.
// ─────────────────────────────────────────────────────────────────────────────

class MembersMasterViewModel(
    private val repository: MemberRepository,
    private val householdId: String,
) : ViewModel() {

    /** Todos los miembros (activos e inactivos) para poder reactivar. */
    val members: StateFlow<List<MemberEntity>> =
        repository.observeAllMembers(householdId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addMember(name: String, role: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            repository.insert(
                MemberEntity(
                    id = UUID.randomUUID().toString(),
                    householdId = householdId,
                    displayName = clean,
                    role = role,
                    isActive = true,
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    fun updateMember(member: MemberEntity, name: String, role: String, isActive: Boolean) {
        viewModelScope.launch {
            repository.update(
                member.copy(
                    displayName = name.trim().ifBlank { member.displayName },
                    role = role,
                    isActive = isActive,
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    /** Baja lógica (isActive=false): no se borra por las FKs de expense/wallet. */
    fun deactivate(member: MemberEntity) {
        viewModelScope.launch {
            repository.update(member.copy(isActive = false, updatedAt = System.currentTimeMillis()))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CRUD de categorías (paquete B2). Sincronizable: CategoryRepositoryImpl estampa
// updated_at y encola CATEGORY|UPSERT. Se presenta en árbol (grupos raíz + hojas).
// ─────────────────────────────────────────────────────────────────────────────

class CategoriesMasterViewModel(
    private val repository: CategoryRepository,
    private val householdId: String,
) : ViewModel() {

    data class CategoryNode(
        val category: CategoryEntity,
        val children: List<CategoryEntity>,
    )

    /** Árbol: raíces con sus hijos directos, ordenado por sortOrder/nombre. */
    val tree: StateFlow<List<CategoryNode>> =
        repository.observeAll(householdId)
            .map { all ->
                val byParent = all.groupBy { it.parentId }
                val roots = (byParent[null] ?: emptyList()).sortedWith(compareBy({ it.sortOrder }, { it.displayName }))
                roots.map { root ->
                    CategoryNode(
                        category = root,
                        children = (byParent[root.id] ?: emptyList())
                            .sortedWith(compareBy({ it.sortOrder }, { it.displayName })),
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Alta bajo un padre opcional. El code se deriva del nombre para unicidad. */
    fun addCategory(name: String, parentId: String?, colorHex: String?, budget: Double?) {
        val clean = name.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            val code = uniqueCode(clean)
            repository.insert(
                CategoryEntity(
                    id = UUID.randomUUID().toString(),
                    householdId = householdId,
                    parentId = parentId,
                    code = code,
                    displayName = clean,
                    icon = null,
                    colorHex = colorHex,
                    kind = "EXPENSE_VARIABLE",
                    budgetDefaultMxn = budget,
                    sortOrder = 999,
                )
            )
        }
    }

    fun updateCategory(category: CategoryEntity, name: String, colorHex: String?, budget: Double?) {
        viewModelScope.launch {
            repository.update(
                category.copy(
                    displayName = name.trim().ifBlank { category.displayName },
                    colorHex = colorHex,
                    budgetDefaultMxn = budget,
                )
            )
        }
    }

    /**
     * Archiva la categoría renombrándola con prefijo y bajando su prioridad. No se
     * borra la fila (FK de expense.category_id); se mueve al fondo. Un borrado duro
     * rompería gastos históricos.
     */
    fun archive(category: CategoryEntity) {
        viewModelScope.launch {
            if (category.displayName.startsWith(ARCHIVED_PREFIX)) return@launch
            repository.update(
                category.copy(
                    displayName = ARCHIVED_PREFIX + category.displayName,
                    sortOrder = 9999,
                )
            )
        }
    }

    private suspend fun uniqueCode(name: String): String {
        val base = "CUSTOM." + name.uppercase()
            .map { if (it.isLetterOrDigit()) it else '_' }
            .joinToString("").take(24)
        var candidate = base
        var i = 1
        while (repository.getByCode(householdId, candidate) != null) {
            candidate = base + "_" + i
            i++
        }
        return candidate
    }

    private companion object {
        const val ARCHIVED_PREFIX = "(archivada) "
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CRUD de fuentes de ingreso (paquete B2). Sincronizable (IncomeRepositoryImpl
// encola INCOME). Se administran las fuentes de la QUINCENA ACTIVA — el income
// source es por quincena en el esquema. Alta como PLANNED (no mueve saldo hasta
// postearse en Cuentas).
// ─────────────────────────────────────────────────────────────────────────────

class IncomeSourcesMasterViewModel(
    private val repository: IncomeRepository,
    private val memberRepository: MemberRepository,
    private val quincenaRepository: QuincenaRepository,
    private val householdId: String,
) : ViewModel() {

    private val activeQuincena =
        quincenaRepository.observeActive(householdId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val members: StateFlow<List<MemberEntity>> =
        memberRepository.observeActiveMembers(householdId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Ingresos de la quincena activa (o vacío si no hay una). */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val sources: StateFlow<List<IncomeSourceEntity>> =
        activeQuincena
            .flatMapLatest { q ->
                if (q == null) kotlinx.coroutines.flow.flowOf(emptyList())
                else repository.observeByQuincena(q.id)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hasActiveQuincena: StateFlow<Boolean> =
        activeQuincena.map { it != null }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun addSource(memberId: String, label: String, amount: Double, cadence: String, colorHex: String?) {
        viewModelScope.launch {
            val quincena = quincenaRepository.getActive(householdId) ?: return@launch
            val today = LocalDate.now(ZONE).toString()
            repository.insert(
                IncomeSourceEntity(
                    id = UUID.randomUUID().toString(),
                    householdId = householdId,
                    quincenaId = quincena.id,
                    memberId = memberId,
                    label = label.trim().ifBlank { "Ingreso" },
                    amountMxn = amount,
                    cadence = cadence,
                    expectedDate = today,
                    status = "PLANNED",
                    colorHex = colorHex,
                    createdAt = System.currentTimeMillis(),
                )
            )
        }
    }

    fun updateSource(source: IncomeSourceEntity, memberId: String, label: String, amount: Double, cadence: String, colorHex: String?) {
        viewModelScope.launch {
            repository.update(
                source.copy(
                    memberId = memberId,
                    label = label.trim().ifBlank { source.label },
                    amountMxn = amount,
                    cadence = cadence,
                    colorHex = colorHex,
                )
            )
        }
    }
}
