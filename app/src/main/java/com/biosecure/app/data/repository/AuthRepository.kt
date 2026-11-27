package com.biosecure.app.data.repository

import android.content.Context
import com.biosecure.app.data.network.ApiUser
import com.biosecure.app.data.network.LoginRequest
import com.biosecure.app.data.network.RetrofitInstance
import com.biosecure.app.data.prefs.SessionStore

/** Usuario de la app (antes FirebaseUser). Ahora viene de MongoDB. */
data class AuthUser(
    val uid: String,
    val email: String? = null,
    val role: String = "employee",
    val name: String = ""
)

data class UserLoginResult(
    val user: AuthUser,
    val role: String,
    val name: String,
    val companyId: String = "",
    val image: String = ""
)

class AuthRepository(context: Context) {
    private val session = SessionStore(context)
    private val api = RetrofitInstance.mongoApi

    val currentUser: AuthUser?
        get() = session.uid?.let { AuthUser(it, session.email, session.role, session.name) }

    suspend fun loginWithRole(email: String, password: String): Result<UserLoginResult> {
        return try {
            val resp = api.login(LoginRequest(email.trim(), password.trim()))
            val data = resp.data ?: return Result.failure(Exception(resp.error ?: "Error de autenticación"))
            if (!resp.ok) return Result.failure(Exception(resp.error ?: "Error de autenticación"))
            session.token = data.token
            session.uid = data.user.uid
            session.email = data.user.email
            session.role = data.user.role
            session.companyId = data.user.companyId
            session.name = data.user.name
            Result.success(data.user.toLoginResult())
        } catch (e: Exception) {
            Result.failure(Exception(friendlyError(e)))
        }
    }

    suspend fun loginWithGoogle(idToken: String): Result<UserLoginResult> {
        return try {
            val resp = api.loginGoogle(mapOf("idToken" to idToken))
            val data = resp.data ?: return Result.failure(Exception(resp.error ?: "Error con Google"))
            if (!resp.ok) return Result.failure(Exception(resp.error ?: "Error con Google"))
            session.token = data.token
            session.uid = data.user.uid
            session.email = data.user.email
            session.role = data.user.role
            session.companyId = data.user.companyId
            session.name = data.user.name
            Result.success(data.user.toLoginResult())
        } catch (e: Exception) {
            Result.failure(Exception(friendlyError(e)))
        }
    }

    suspend fun register(email: String, password: String): Result<AuthUser> {
        return try {
            val resp = api.createUser(session.authHeader(), mapOf(
                "email" to email.trim(), "password" to password,
                "name" to email.substringBefore("@"), "role" to "employee",
                "companyId" to session.companyId
            ))
            val u = resp.data ?: return Result.failure(Exception(resp.error ?: "No se pudo registrar"))
            Result.success(AuthUser(u.uid, u.email, u.role, u.name))
        } catch (e: Exception) {
            Result.failure(Exception(friendlyError(e)))
        }
    }

    suspend fun loginAndGetRole(email: String, password: String): Pair<AuthUser, String> =
        loginWithRole(email, password).map { Pair(it.user, it.role) }.getOrThrow()

    suspend fun getRoleForCurrentUser(): String? = session.uid?.let { session.role }

    suspend fun getUserData(): UserLoginResult? {
        if (session.token == null) return null
        return try {
            val resp = api.me(session.authHeader())
            resp.data?.toLoginResult()
        } catch (e: Exception) { null }
    }

    suspend fun getCurrentUserResult(): Result<UserLoginResult> {
        val token = session.token ?: return Result.failure(Exception("No hay sesión activa"))
        return try {
            val resp = api.me("Bearer $token")
            if (!resp.ok || resp.data == null) {
                return Result.failure(Exception(resp.error ?: "Sesión inválida"))
            }
            val user = resp.data!!.toLoginResult()
            session.uid = user.user.uid
            session.email = user.user.email ?: ""
            session.role = user.role
            session.companyId = user.companyId
            session.name = user.name
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyError(e)))
        }
    }

    fun isLoggedIn(): Boolean = !session.token.isNullOrBlank() && !session.isTokenExpired()

    fun logout() {
        session.clear()
    }

    private fun ApiUser.toLoginResult() = UserLoginResult(
        user = AuthUser(uid, email, role, name),
        role = role,
        name = name.ifBlank { email },
        companyId = companyId,
        image = image
    )

    private fun friendlyError(e: Exception): String {
        val m = e.message ?: ""
        return when {
            m.contains("Failed to connect") || m.contains("Connection refused") || m.contains("timeout") ->
                "No se pudo conectar con el servidor MongoDB. Revisa la red o adb reverse tcp:3001."
            m.contains("401") -> "Correo o contraseña incorrectos"
            else -> m.ifBlank { "Error de conexión" }
        }
    }
}
