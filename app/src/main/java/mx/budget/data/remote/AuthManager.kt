package mx.budget.data.remote

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await

class AuthManager {

    private val auth = FirebaseAuth.getInstance()

    suspend fun signInAnonymously(): FirebaseUser? {
        if (auth.currentUser != null) {
            return auth.currentUser
        }
        
        return try {
            val result = auth.signInAnonymously().await()
            Log.d("AuthManager", "Signed in anonymously: ${result.user?.uid}")
            result.user
        } catch (e: Exception) {
            Log.e("AuthManager", "Error signing in anonymously", e)
            null
        }
    }

    fun getCurrentUser(): FirebaseUser? = auth.currentUser
}
