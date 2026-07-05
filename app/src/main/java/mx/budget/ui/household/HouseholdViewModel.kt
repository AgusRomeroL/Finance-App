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
import kotlinx.coroutines.launch
import mx.budget.data.remote.AuthManager
import mx.budget.data.remote.MembershipRepository

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
        val busy: Boolean = false,
        val message: String? = null,
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

    init {
        // Recompone el estado combinado ante cambios de sesión o de hogares.
        viewModelScope.launch {
            authManager.currentUser.collect { recompute() }
        }
        viewModelScope.launch {
            householdsFlow.collect { recompute() }
        }
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
            _extra.value = _extra.value.copy(message = "Inicia sesión con Google primero")
            return
        }
        viewModelScope.launch {
            _extra.value = _extra.value.copy(busy = true, message = null)
            val displayName = user.displayName ?: user.email ?: "Yo"
            runCatching { membershipRepository.createHousehold(name.trim(), user.uid, displayName) }
                .onSuccess { hid ->
                    membershipRepository.setActiveHousehold(user.uid, hid)
                    _extra.value = _extra.value.copy(busy = false, message = "Grupo \"$name\" creado")
                    switchTo(hid)
                }
                .onFailure {
                    _extra.value = _extra.value.copy(busy = false, message = "No se pudo crear el grupo")
                }
        }
    }

    fun joinByCode(fullCode: String) {
        val user = authManager.currentUser.value ?: return
        if (user.isAnonymous) {
            _extra.value = _extra.value.copy(message = "Inicia sesión con Google primero")
            return
        }
        viewModelScope.launch {
            _extra.value = _extra.value.copy(busy = true, message = null)
            val displayName = user.displayName ?: user.email ?: "Yo"
            val hid = membershipRepository.joinByCode(fullCode.trim(), user.uid, displayName)
            if (hid != null) {
                membershipRepository.setActiveHousehold(user.uid, hid)
                _extra.value = _extra.value.copy(busy = false, message = "Te uniste al grupo")
                switchTo(hid)
            } else {
                _extra.value = _extra.value.copy(busy = false, message = "Código inválido o expirado")
            }
        }
    }

    fun generateInvite() {
        val user = authManager.currentUser.value ?: return
        viewModelScope.launch {
            _extra.value = _extra.value.copy(busy = true, message = null)
            runCatching {
                membershipRepository.generateInvite(
                    householdId = _extra.value.activeHouseholdId,
                    createdBy = user.uid,
                )
            }.onSuccess { code ->
                _extra.value = _extra.value.copy(busy = false, inviteCode = code)
                recompute()
            }.onFailure {
                _extra.value = _extra.value.copy(busy = false, message = "No se pudo generar el código")
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
        onSwitchHousehold(householdId)
    }

    fun clearMessage() {
        _extra.value = _extra.value.copy(message = null)
    }
}
