package com.biosecure.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

data class UserLoginResult(
    val user: FirebaseUser,
    val role: String,
    val name: String,
    val companyId: String = "",
    val image: String = ""
)

class AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    val currentUser: FirebaseUser? get() = auth.currentUser

    suspend fun loginWithRole(email: String, password: String): Result<UserLoginResult> {
        return try {
            val trimmedEmail = email.trim()
            val authResult = auth.signInWithEmailAndPassword(trimmedEmail, password.trim()).await()
            val user = authResult.user!!
            val tokenTask = user.getIdToken(true).await()
            var role = tokenTask.claims["role"] as? String
            var companyId = tokenTask.claims["companyId"] as? String

            val doc = db.collection("users").document(user.uid).get(Source.SERVER).await()
            if (doc.exists()) {
                role = doc.getString("role") ?: role
                companyId = doc.getString("companyId") ?: companyId ?: "BioSecure"
                val name = doc.getString("name") ?: user.displayName ?: trimmedEmail
                val image = doc.getString("image") ?: ""
                if (role != null) {
                    return Result.success(UserLoginResult(user, role, name, companyId!!, image))
                }
            }
            auth.signOut()
            Result.failure(Exception("Usuario sin rol asignado en el sistema"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            Result.success(result.user!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loginAndGetRole(email: String, password: String): Pair<FirebaseUser, String> =
        loginWithRole(email, password).map { Pair(it.user, it.role) }.getOrThrow()

    suspend fun getRoleForCurrentUser(): String? {
        val uid = auth.currentUser?.uid ?: return null
        return try {
            val doc = db.collection("users").document(uid).get().await()
            doc.getString("role")
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getUserData(): UserLoginResult? {
        val user = auth.currentUser ?: return null
        return try {
            val doc = db.collection("users").document(user.uid).get().await()
            val role = doc.getString("role") ?: return null
            val name = doc.getString("name") ?: user.email ?: ""
            val companyId = doc.getString("companyId") ?: ""
            val image = doc.getString("image") ?: ""
            UserLoginResult(user, role, name, companyId, image)
        } catch (e: Exception) { null }
    }

    suspend fun getCurrentUserResult(): Result<UserLoginResult> {
        val user = auth.currentUser ?: return Result.failure(Exception("No hay sesión activa"))
        return try {
            val tokenTask = user.getIdToken(false).await()
            var role = tokenTask.claims["role"] as? String
            var companyId = tokenTask.claims["companyId"] as? String

            val doc = db.collection("users").document(user.uid).get(Source.SERVER).await()
            if (doc.exists()) {
                role = doc.getString("role") ?: role
                companyId = doc.getString("companyId") ?: companyId ?: "BioSecure"
                val name = doc.getString("name") ?: user.displayName ?: user.email ?: "Usuario"
                val image = doc.getString("image") ?: ""
                if (role != null) {
                    return Result.success(UserLoginResult(user, role, name, companyId!!, image))
                }
            }
            Result.failure(Exception("Usuario sin datos en Firestore"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isLoggedIn(): Boolean = auth.currentUser != null

    fun logout() { auth.signOut() }
}
