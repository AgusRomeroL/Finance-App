package mx.budget.data.remote

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Autenticación de Firebase para la Fase B (multi-tenant).
 *
 * Dos caminos:
 * - **Anónimo** ([signInAnonymously]): comportamiento histórico. La app arranca
 *   anónima y funciona sin que el usuario decida nada (la experiencia de Norma
 *   hoy). Es dueña anónima de `default_household`.
 * - **Google** ([signInWithGoogle]): vía **Credential Manager** + Google ID token.
 *   CRÍTICO: si ya hay una sesión anónima, se hace `linkWithCredential` para
 *   CONSERVAR el uid (y con él la propiedad del household ya sembrado). Si el link
 *   falla porque la credencial de Google ya está asociada a otra cuenta
 *   (`ERROR_CREDENTIAL_ALREADY_IN_USE`), se cae a `signInWithCredential` (el uid
 *   cambia; la migración de membership se re-ejecuta con el nuevo uid).
 *
 * [currentUser] es un [StateFlow] observable para que la UI reaccione al
 * login/logout sin sondear.
 */
class AuthManager(context: Context? = null) {

    private val appContext = context?.applicationContext
    private val auth = FirebaseAuth.getInstance()

    private val _currentUser = MutableStateFlow(auth.currentUser)
    /** Usuario actual (anónimo o Google), observable. `null` = sin sesión. */
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    init {
        // Mantiene el StateFlow al día ante cualquier cambio de sesión (login,
        // logout, refresh de token) que ocurra fuera de esta clase.
        auth.addAuthStateListener { fb -> _currentUser.value = fb.currentUser }
    }

    /**
     * Inicia (o reutiliza) una sesión anónima. Idempotente: si ya hay usuario
     * (anónimo o Google), lo devuelve tal cual sin crear otro.
     */
    suspend fun signInAnonymously(): FirebaseUser? {
        auth.currentUser?.let { return it }
        return try {
            val result = auth.signInAnonymously().await()
            Log.d(TAG, "Sesión anónima: ${result.user?.uid}")
            _currentUser.value = result.user
            result.user
        } catch (e: Exception) {
            Log.e(TAG, "Error en sign-in anónimo", e)
            null
        }
    }

    /**
     * Inicia sesión con Google usando Credential Manager. Debe llamarse con un
     * `Context` de Activity (la hoja de credenciales necesita una ventana).
     *
     * @return el [FirebaseUser] resultante, o `null` si el usuario canceló o
     *  hubo error (se loguea; nunca crashea).
     */
    suspend fun signInWithGoogle(activityContext: Context): FirebaseUser? {
        return try {
            val option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(WEB_CLIENT_ID)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build()

            val manager = CredentialManager.create(activityContext)
            val response = manager.getCredential(activityContext, request)
            val credential = response.credential

            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                Log.w(TAG, "Credencial devuelta no es Google ID: ${credential.type}")
                return null
            }

            val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)

            val existing = auth.currentUser
            val user = if (existing != null && existing.isAnonymous) {
                // Preserva el uid anónimo (dueño de default_household).
                try {
                    existing.linkWithCredential(firebaseCredential).await().user
                } catch (link: Exception) {
                    Log.w(TAG, "linkWithCredential falló; se hace signIn (uid cambia)", link)
                    auth.signInWithCredential(firebaseCredential).await().user
                }
            } else {
                auth.signInWithCredential(firebaseCredential).await().user
            }
            Log.d(TAG, "Sesión Google: ${user?.uid} (${user?.email})")
            _currentUser.value = user
            user
        } catch (e: Exception) {
            Log.e(TAG, "Error en sign-in con Google", e)
            null
        }
    }

    /** Cierra la sesión de Google/Firebase y vuelve a una sesión anónima nueva. */
    suspend fun signOut(): FirebaseUser? {
        auth.signOut()
        _currentUser.value = null
        return signInAnonymously()
    }

    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    /** `true` si hay usuario y NO es anónimo (ya vinculó una cuenta Google). */
    fun isLinked(): Boolean = auth.currentUser?.let { !it.isAnonymous } ?: false

    companion object {
        private const val TAG = "AuthManager"

        /**
         * Web client ID (OAuth type 3) requerido por GetGoogleIdOption. El
         * `google-services.json` actual trae `oauth_client: []` vacío, así que
         * NO hay un web client todavía. Placeholder hasta que el usuario cree un
         * "OAuth 2.0 Client ID (Web application)" en Google Cloud Console /
         * Firebase Auth y pegue aquí su value. Con este placeholder el sign-in
         * real de Google fallará (se loguea y devuelve null) SIN crashear.
         */
        const val WEB_CLIENT_ID = "TODO_WEB_CLIENT_ID"
    }
}
