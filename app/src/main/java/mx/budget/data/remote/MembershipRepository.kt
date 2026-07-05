package mx.budget.data.remote

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import mx.budget.data.local.entity.MemberEntity
import java.util.UUID
import kotlin.random.Random

/**
 * Repositorio de **pertenencia multi-hogar** (Fase B), sobre Firestore.
 *
 * Modela quién puede acceder a qué hogar y con qué rol. Es independiente de la
 * capa offline-first de datos del hogar (Room = fuente de verdad de expenses,
 * etc.): esto vive SOLO en la nube porque describe la relación usuario↔hogar,
 * que por naturaleza es multi-dispositivo.
 *
 * ── Estructura Firestore ────────────────────────────────────────────────────
 * - `users/{uid}` = { displayName, email, photoUrl, activeHouseholdId }
 * - `households/{hid}` = { name, updatedAt }  (doc del hogar; los datos cuelgan
 *   de sus subcolecciones ya existentes)
 * - `households/{hid}/roles/{uid}` = { role: OWNER|COLLABORATOR, linkedMemberId?, displayName }
 * - `users/{uid}/households/{hid}` = { role, joinedAt }  (espejo para listar mis hogares)
 * - `households/{hid}/invites/{code}` = { role, expiresAt(epochMs), maxUses, uses, createdBy }
 *
 * ── Código de invitación ─────────────────────────────────────────────────────
 * El dueño comparte con el colaborador un código con formato **`{hid}.{code}`**
 * (ej. `default_household.7QX4K2AB`). La parte antes del punto es el household id;
 * la parte después (8 chars A-Z0-9) es el secreto que indexa el doc del invite.
 * La web usa el MISMO formato. [parseInviteCode] lo separa.
 */
class MembershipRepository(
    private val firestore: FirebaseFirestore,
) {

    // ── Roles ────────────────────────────────────────────────────────────────

    /** Un hogar al que pertenece el usuario (para el selector de grupo activo). */
    data class HouseholdMembership(
        val householdId: String,
        val role: String,
        val displayName: String,
    )

    /** Propuesta de gasto de un colaborador (propose-then-confirm). */
    data class Proposal(
        val id: String,
        val concept: String,
        val amountMxn: Double,
        val occurredAt: Long,
        val note: String? = null,
        val proposedBy: String? = null,
        val status: String = "PENDING",
    )

    private fun users() = firestore.collection("users")
    private fun households() = firestore.collection("households")

    /**
     * Observa los hogares del usuario leyendo su espejo `users/{uid}/households`.
     * Emite la lista cada vez que cambia (unión a un grupo nuevo, etc.).
     */
    fun observeMyHouseholds(uid: String): Flow<List<HouseholdMembership>> = callbackFlow {
        val reg: ListenerRegistration = users().document(uid).collection("households")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.w(TAG, "observeMyHouseholds falló", err)
                    return@addSnapshotListener
                }
                val list = snap?.documents?.map { doc ->
                    HouseholdMembership(
                        householdId = doc.id,
                        role = doc.getString("role") ?: "COLLABORATOR",
                        displayName = doc.getString("displayName") ?: doc.id,
                    )
                }.orEmpty()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    /** El household activo del usuario (o null). */
    suspend fun getActiveHousehold(uid: String): String? =
        runCatching { users().document(uid).get().await().getString("activeHouseholdId") }.getOrNull()

    /** Fija el household activo del usuario (persistido también en Firestore). */
    suspend fun setActiveHousehold(uid: String, householdId: String) {
        runCatching {
            users().document(uid).set(
                mapOf("activeHouseholdId" to householdId),
                com.google.firebase.firestore.SetOptions.merge(),
            ).await()
        }.onFailure { Log.w(TAG, "setActiveHousehold falló", it) }
    }

    /**
     * Escribe/actualiza el perfil del usuario en `users/{uid}`. Idempotente
     * (merge). Se llama al vincular Google para tener displayName/email/foto.
     */
    suspend fun upsertUserProfile(
        uid: String,
        displayName: String?,
        email: String?,
        photoUrl: String?,
    ) {
        runCatching {
            users().document(uid).set(
                mapOf(
                    "displayName" to (displayName ?: ""),
                    "email" to (email ?: ""),
                    "photoUrl" to (photoUrl ?: ""),
                ),
                com.google.firebase.firestore.SetOptions.merge(),
            ).await()
        }.onFailure { Log.w(TAG, "upsertUserProfile falló", it) }
    }

    /**
     * Crea un hogar nuevo con el usuario como OWNER. Escribe:
     * - doc `households/{hid}` (name, updatedAt)
     * - `households/{hid}/roles/{uid}` = OWNER
     * - espejo `users/{uid}/households/{hid}`
     * - un [MemberEntity] PAYER_ADULT para el dueño en la subcolección `members`
     *   del hogar (para que la app tenga al menos un miembro tras el pull).
     * @return el household id creado.
     */
    suspend fun createHousehold(name: String, uid: String, displayName: String): String {
        val hid = "hh_" + UUID.randomUUID().toString().take(8)
        val now = System.currentTimeMillis()

        households().document(hid).set(
            mapOf("name" to name, "updatedAt" to now)
        ).await()

        households().document(hid).collection("roles").document(uid).set(
            mapOf(
                "role" to ROLE_OWNER,
                "displayName" to displayName,
                "linkedMemberId" to null,
            )
        ).await()

        users().document(uid).collection("households").document(hid).set(
            mapOf("role" to ROLE_OWNER, "joinedAt" to now, "displayName" to name)
        ).await()

        // Miembro dueño (para que el hogar tenga al menos un pagador adulto).
        val member = MemberEntity(
            id = UUID.randomUUID().toString(),
            householdId = hid,
            displayName = displayName.ifBlank { "Yo" },
            role = "PAYER_ADULT",
            isActive = true,
            updatedAt = now,
        )
        households().document(hid).collection("members").document(member.id).set(
            mapOf(
                "id" to member.id,
                "household_id" to member.householdId,
                "display_name" to member.displayName,
                "short_aliases" to member.shortAliases,
                "role" to member.role,
                "is_active" to member.isActive,
                "meta" to member.meta,
                "updated_at" to member.updatedAt,
            )
        ).await()

        return hid
    }

    /**
     * Migra la instalación de Norma: registra el uid como OWNER del hogar ya
     * sembrado (típicamente `default_household`) y fija `activeHouseholdId`. NO
     * crea miembros (el hogar ya los tiene sembrados). Idempotente.
     */
    suspend fun claimExistingHousehold(
        householdId: String,
        uid: String,
        displayName: String,
        householdName: String = "Mi hogar",
    ) {
        val now = System.currentTimeMillis()
        runCatching {
            households().document(householdId).set(
                mapOf("name" to householdName, "updatedAt" to now),
                com.google.firebase.firestore.SetOptions.merge(),
            ).await()

            households().document(householdId).collection("roles").document(uid).set(
                mapOf("role" to ROLE_OWNER, "displayName" to displayName, "linkedMemberId" to null)
            ).await()

            users().document(uid).collection("households").document(householdId).set(
                mapOf("role" to ROLE_OWNER, "joinedAt" to now, "displayName" to householdName)
            ).await()

            setActiveHousehold(uid, householdId)
        }.onFailure { Log.w(TAG, "claimExistingHousehold falló", it) }
    }

    // ── Invitaciones ───────────────────────────────────────────────────────────

    /**
     * Genera un código de invitación al hogar con el rol dado. Devuelve el
     * código completo con formato **`{hid}.{code}`** para compartir.
     *
     * @param role rol que recibirá quien lo canjee (default COLLABORATOR).
     * @param maxUses usos permitidos (default 5).
     * @param ttlMillis vigencia desde ahora (default 7 días).
     */
    suspend fun generateInvite(
        householdId: String,
        createdBy: String,
        role: String = ROLE_COLLABORATOR,
        maxUses: Int = 5,
        ttlMillis: Long = 7L * 24 * 60 * 60 * 1000,
    ): String {
        val code = randomCode()
        households().document(householdId).collection("invites").document(code).set(
            mapOf(
                "role" to role,
                "expiresAt" to (System.currentTimeMillis() + ttlMillis),
                "maxUses" to maxUses,
                "uses" to 0,
                "createdBy" to createdBy,
            )
        ).await()
        return "$householdId.$code"
    }

    /**
     * Canjea un código `{hid}.{code}`: valida vigencia/usos en cliente, crea
     * `roles/{uid}` con el rol del invite + el espejo del usuario, e incrementa
     * `uses`. Devuelve el household id al que se unió, o null si es inválido.
     */
    suspend fun joinByCode(fullCode: String, uid: String, displayName: String): String? {
        val (hid, code) = parseInviteCode(fullCode) ?: run {
            Log.w(TAG, "Código de invitación mal formado: $fullCode")
            return null
        }
        return try {
            val inviteRef = households().document(hid).collection("invites").document(code)
            val invite = inviteRef.get().await()
            if (!invite.exists()) {
                Log.w(TAG, "Invitación inexistente: $hid/$code")
                return null
            }
            val expiresAt = invite.getLong("expiresAt") ?: 0L
            val maxUses = invite.getLong("maxUses") ?: 0L
            val uses = invite.getLong("uses") ?: 0L
            if (expiresAt in 1..System.currentTimeMillis()) {
                Log.w(TAG, "Invitación expirada: $hid/$code")
                return null
            }
            if (maxUses > 0 && uses >= maxUses) {
                Log.w(TAG, "Invitación sin usos: $hid/$code")
                return null
            }
            val role = invite.getString("role") ?: ROLE_COLLABORATOR
            val now = System.currentTimeMillis()

            households().document(hid).collection("roles").document(uid).set(
                mapOf("role" to role, "displayName" to displayName, "linkedMemberId" to null)
            ).await()
            users().document(uid).collection("households").document(hid).set(
                mapOf("role" to role, "joinedAt" to now, "displayName" to hid)
            ).await()
            inviteRef.update("uses", FieldValue.increment(1)).await()

            hid
        } catch (e: Exception) {
            Log.w(TAG, "joinByCode falló para $fullCode", e)
            null
        }
    }

    // ── Propuestas (colaborador → dueño) ─────────────────────────────────────

    /**
     * Un colaborador propone un gasto (no puede escribir en el ledger). Se guarda
     * en `households/{hid}/proposals/`; el teléfono del dueño lo refleja en su
     * bandeja `pending_capture` vía [mx.budget.data.sync.RemotePullSync].
     */
    suspend fun submitProposal(householdId: String, proposal: Proposal) {
        runCatching {
            households().document(householdId).collection("proposals").document(proposal.id).set(
                mapOf(
                    "concept" to proposal.concept,
                    "amountMxn" to proposal.amountMxn,
                    "occurredAt" to proposal.occurredAt,
                    "note" to proposal.note,
                    "proposedBy" to proposal.proposedBy,
                    "status" to proposal.status,
                    "createdAt" to System.currentTimeMillis(),
                )
            ).await()
        }.onFailure { Log.w(TAG, "submitProposal falló", it) }
    }

    companion object {
        private const val TAG = "MembershipRepository"

        const val ROLE_OWNER = "OWNER"
        const val ROLE_COLLABORATOR = "COLLABORATOR"

        private const val CODE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        private const val CODE_LENGTH = 8

        private fun randomCode(): String =
            (1..CODE_LENGTH).map { CODE_ALPHABET[Random.nextInt(CODE_ALPHABET.length)] }.joinToString("")

        /** Separa `{hid}.{code}` → (hid, code). null si no tiene el formato. */
        fun parseInviteCode(fullCode: String): Pair<String, String>? {
            val trimmed = fullCode.trim()
            val dot = trimmed.lastIndexOf('.')
            if (dot <= 0 || dot == trimmed.length - 1) return null
            val hid = trimmed.substring(0, dot)
            val code = trimmed.substring(dot + 1).uppercase()
            if (hid.isBlank() || code.length != CODE_LENGTH) return null
            return hid to code
        }
    }
}
