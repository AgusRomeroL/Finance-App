package mx.budget.ui.household

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import mx.budget.data.remote.AuthManager
import mx.budget.data.remote.MembershipRepository
import mx.budget.data.repository.MemberRepository

/**
 * ViewModel de la sección "Cuenta y grupos" (Fase B).
 *
 * Expone la sesión actual (anónima vs Google), la lista de hogares del usuario,
 * el hogar activo y las acciones de crear/unirse/invitar. La reactividad del
 * `householdId` de la app la maneja [mx.budget.BudgetApplication.switchActiveHousehold]
 * (ver su doc: el pull se re-ancla en caliente; la UI ya montada requiere
 * reinicio de la Activity para leer Room con el hogar nuevo).
 */
class HouseholdViewModel(
    private val authManager: AuthManager,
    private val membershipRepository: MembershipRepository,
    private val activeHouseholdId: String,
    /** Repo local de members: alimenta el selector de invitación nominada cuando el hogar activo es el local. */
    private val memberRepository: MemberRepository,
    /**
     * Member vinculado a ESTA sesión (roles v2, `BudgetApplication.linkedMemberId`):
     * se excluye de [UiState.eligibleMembers] — uno no se invita a sí mismo.
     * `null` = identidad aún sin resolver (la pantalla aplica un segundo filtro
     * con `LocalSessionMemberId` como cinturón).
     */
    private val sessionMemberId: String? = null,
    private val onSignInWithGoogle: suspend () -> Boolean,
    private val onSwitchHousehold: (String) -> Unit,
) : ViewModel() {

    data class UiState(
        val isLinked: Boolean = false,
        val displayName: String? = null,
        val email: String? = null,
        val households: List<MembershipRepository.HouseholdMembership> = emptyList(),
        val activeHouseholdId: String = "",
        val inviteCode: String? = null,
        /**
         * Members ACTIVOS del hogar activo, elegibles para la invitación
         * nominada (roles v2): el invite se liga a uno de ellos y el rol deriva
         * de su `role` de member (PAYER_* → Administrador; resto → Colaborador).
         */
        val eligibleMembers: List<MembershipRepository.HouseholdMemberInfo> = emptyList(),
        val busy: Boolean = false,
        val message: String? = null,
        /** true = el mensaje es de error (el banner usa colores de error). */
        val messageIsError: Boolean = false,
        /**
         * Contadores de éxito de crear/unirse. La pantalla los observa para
         * limpiar los campos de texto SOLO cuando la operación tuvo éxito
         * (antes se borraban incondicionalmente al pulsar el botón).
         */
        val createSuccessCount: Int = 0,
        val joinSuccessCount: Int = 0,
    )

    private val _extra = MutableStateFlow(
        UiState(activeHouseholdId = activeHouseholdId)
    )

    @Suppress("OPT_IN_USAGE")
    private val householdsFlow: StateFlow<List<MembershipRepository.HouseholdMembership>> =
        authManager.currentUser
            .flatMapLatest { user ->
                if (user != null && !user.isAnonymous) {
                    membershipRepository.observeMyHouseholds(user.uid)
                } else {
                    flowOf(emptyList())
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _combined = MutableStateFlow(UiState(activeHouseholdId = activeHouseholdId))

    val uiState: StateFlow<UiState> = _combined.asStateFlow()

    /** Members elegibles del hogar activo (invitación nominada, roles v2). */
    private val eligibleMembersFlow = MutableStateFlow<List<MembershipRepository.HouseholdMemberInfo>>(emptyList())

    /**
     * Carga los members ACTIVOS del hogar activo para el selector de invitación:
     * - Hogar activo == el hogar LOCAL de la app → Room ([memberRepository]),
     *   que es la fuente de verdad de esta instalación.
     * - Hogar activo remoto (recién creado/unido, aún sin pull local) → get
     *   puntual de la subcolección Firestore `households/{hid}/members`.
     * Excluye inactivos, los espejo EXTERNAL_* (no son personas invitables) y el
     * member vinculado a la propia sesión ([sessionMemberId]) — tanto en la
     * lista local (Room) como en la remota (Firestore).
     */
    private fun loadEligibleMembers() {
        val hid = _extra.value.activeHouseholdId
        if (hid.isBlank()) {
            eligibleMembersFlow.value = emptyList()
            return
        }
        viewModelScope.launch {
            val list = runCatching {
                if (hid == activeHouseholdId) {
                    memberRepository.observeActiveMembers(hid).first().map { m ->
                        MembershipRepository.HouseholdMemberInfo(
                            id = m.id,
                            displayName = m.displayName,
                            role = m.role,
                            isActive = m.isActive,
                        )
                    }
                } else {
                    membershipRepository.getHouseholdMembers(hid)
                }
            }.getOrDefault(emptyList())
            eligibleMembersFlow.value = list
                .filter { it.isActive && !it.role.startsWith("EXTERNAL_") && it.id != sessionMemberId }
        }
    }

    init {
        // Recompone el estado combinado ante cambios de sesión o de hogares.
        viewModelScope.launch {
            authManager.currentUser.collect { recompute() }
        }
        viewModelScope.launch {
            householdsFlow.collect { recompute() }
        }
        // CRÍTICO: toda escritura a `_extra` (mensajes de error, busy, invite)
        // debe reflejarse en el uiState combinado. Sin este colector, los
        // caminos que escribían `_extra` sin llamar recompute() (p. ej. el
        // onFailure de createHousehold) dejaban el error INVISIBLE para la UI.
        viewModelScope.launch {
            _extra.collect { recompute() }
        }
        viewModelScope.launch {
            eligibleMembersFlow.collect { recompute() }
        }
        loadEligibleMembers()
    }

    private fun recompute() {
        val user = authManager.currentUser.value
        val base = _extra.value
        _combined.value = base.copy(
            isLinked = user != null && !user.isAnonymous,
            displayName = user?.displayName,
            email = user?.email,
            households = householdsFlow.value,
            activeHouseholdId = base.activeHouseholdId,
            eligibleMembers = eligibleMembersFlow.value,
        )
    }

    fun signInWithGoogle() {
        viewModelScope.launch {
            _extra.value = _extra.value.copy(busy = true, message = null)
            val ok = onSignInWithGoogle()
            _extra.value = _extra.value.copy(
                busy = false,
                message = if (ok) "Sesión iniciada" else "No se pudo iniciar sesión con Google",
            )
            recompute()
        }
    }

    fun createHousehold(name: String) {
        val user = authManager.currentUser.value ?: return
        if (user.isAnonymous) {
            _extra.value = _extra.value.copy(message = "Inicia sesión con Google primero", messageIsError = true)
            return
        }
        viewModelScope.launch {
            _extra.value = _extra.value.copy(busy = true, message = null)
            val displayName = user.displayName ?: user.email ?: "Yo"
            runCatching { membershipRepository.createHousehold(name.trim(), user.uid, displayName) }
                .onSuccess { hid ->
                    membershipRepository.setActiveHousehold(user.uid, hid)
                    _extra.value = _extra.value.copy(
                        busy = false,
                        message = "Grupo \"$name\" creado",
                        messageIsError = false,
                        createSuccessCount = _extra.value.createSuccessCount + 1,
                    )
                    switchTo(hid)
                }
                .onFailure {
                    // El colector de `_extra` del init propaga este mensaje al
                    // uiState — antes se perdía por no llamar recompute().
                    _extra.value = _extra.value.copy(busy = false, message = "No se pudo crear el grupo", messageIsError = true)
                }
        }
    }

    fun joinByCode(fullCode: String) {
        val user = authManager.currentUser.value ?: return
        if (user.isAnonymous) {
            _extra.value = _extra.value.copy(message = "Inicia sesión con Google primero", messageIsError = true)
            return
        }
        viewModelScope.launch {
            _extra.value = _extra.value.copy(busy = true, message = null)
            val displayName = user.displayName ?: user.email ?: "Yo"
            when (val res = membershipRepository.joinByCode(fullCode.trim(), user.uid, displayName)) {
                is mx.budget.data.remote.MembershipRepository.JoinResult.Joined -> {
                    membershipRepository.setActiveHousehold(user.uid, res.householdId)
                    val asPayer = MembershipRepository.normalizeRole(res.role) ==
                        MembershipRepository.ROLE_PAYER
                    _extra.value = _extra.value.copy(
                        busy = false,
                        message = if (asPayer) "Te uniste como Administrador — reiniciando con el hogar nuevo"
                        else "Te uniste al grupo",
                        messageIsError = false,
                        joinSuccessCount = _extra.value.joinSuccessCount + 1,
                    )
                    // Canje como PAYER (roles v2): este dispositivo pasa a OPERAR el
                    // hogar del invite (escribe al ledger como el member vinculado).
                    // switchTo → onSwitchHousehold → BudgetApplication.switchActiveHousehold
                    // (re-ancla pull/push y re-resuelve linkedMemberId) + MainActivity
                    // recreate(), que garantiza que toda la UI lea el hogar nuevo.
                    // Para MEMBER el flujo es el mismo de siempre (también switchTo).
                    switchTo(res.householdId)
                }
                is mx.budget.data.remote.MembershipRepository.JoinResult.AlreadyMember -> {
                    _extra.value = _extra.value.copy(
                        busy = false,
                        message = "Ya perteneces a este grupo — tu rol no cambió",
                    )
                }
                mx.budget.data.remote.MembershipRepository.JoinResult.Invalid -> {
                    // Visible gracias al colector de `_extra` (mismo fix que crear).
                    _extra.value = _extra.value.copy(busy = false, message = "Código inválido o expirado", messageIsError = true)
                }
            }
        }
    }

    /**
     * Rol de invitación que DERIVA del member elegido (roles v2): un member cuyo
     * role empieza con "PAYER" (PAYER_ADULT…) se invita como PAYER
     * ("Administrador", escribe al ledger); cualquier otro como MEMBER
     * ("Colaborador", propone). La UI muestra la derivación al seleccionar.
     */
    fun deriveInviteRole(member: MembershipRepository.HouseholdMemberInfo): String =
        if (member.role.startsWith("PAYER")) MembershipRepository.ROLE_PAYER
        else MembershipRepository.ROLE_MEMBER

    /**
     * Genera un código de invitación NOMINADA ligado al member [memberId] del
     * hogar activo. El rol del invite deriva del member (ver [deriveInviteRole]).
     */
    fun generateInvite(memberId: String) {
        // Guard de producto (espejo del gate de la UI): los invitados entran a un
        // GRUPO creado por el usuario, jamás al hogar sembrado con sus datos reales.
        if (_extra.value.activeHouseholdId == "default_household") {
            _extra.value = _extra.value.copy(
                message = "Crea un grupo y selecciónalo antes de invitar.",
                messageIsError = true
            )
            return
        }
        // Invitación nominada: el member elegido debe seguir siendo elegible
        // (la lista pudo refrescarse entre la selección y el tap).
        val member = eligibleMembersFlow.value.firstOrNull { it.id == memberId }
        if (member == null) {
            _extra.value = _extra.value.copy(
                message = "Elige a quién representa la invitación.",
                messageIsError = true
            )
            return
        }
        val user = authManager.currentUser.value ?: return
        viewModelScope.launch {
            _extra.value = _extra.value.copy(busy = true, message = null)
            runCatching {
                membershipRepository.generateInvite(
                    householdId = _extra.value.activeHouseholdId,
                    createdBy = user.uid,
                    linkedMemberId = member.id,
                    role = deriveInviteRole(member),
                )
            }.onSuccess { code ->
                _extra.value = _extra.value.copy(busy = false, inviteCode = code)
                recompute()
            }.onFailure {
                _extra.value = _extra.value.copy(busy = false, message = "No se pudo generar el código", messageIsError = true)
            }
        }
    }

    fun switchTo(householdId: String) {
        val user = authManager.currentUser.value
        if (user != null && !user.isAnonymous) {
            viewModelScope.launch { membershipRepository.setActiveHousehold(user.uid, householdId) }
        }
        _extra.value = _extra.value.copy(activeHouseholdId = householdId, inviteCode = null)
        recompute()
        // El selector de invitación nominada depende del hogar activo.
        loadEligibleMembers()
        onSwitchHousehold(householdId)
    }

    fun clearMessage() {
        _extra.value = _extra.value.copy(message = null)
    }
}
