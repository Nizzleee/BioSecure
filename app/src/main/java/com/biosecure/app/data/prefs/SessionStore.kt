package com.biosecure.app.data.prefs

import android.content.Context

/** Sesión local basada en el token JWT del backend MongoDB (reemplaza Firebase Auth). */
class SessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("biosecure_session", Context.MODE_PRIVATE)

    var token: String?
        get() = prefs.getString("token", null)
        set(value) {
            if (value == null) prefs.edit().remove("token").apply()
            else prefs.edit().putString("token", value).apply()
        }

    var uid: String?
        get() = prefs.getString("uid", null)
        set(value) = prefs.edit().putString("uid", value).apply()

    var email: String
        get() = prefs.getString("email", "") ?: ""
        set(value) = prefs.edit().putString("email", value).apply()

    var role: String
        get() = prefs.getString("role", "employee") ?: "employee"
        set(value) = prefs.edit().putString("role", value).apply()

    var companyId: String
        get() = prefs.getString("companyId", "") ?: ""
        set(value) = prefs.edit().putString("companyId", value).apply()

    var name: String
        get() = prefs.getString("name", "") ?: ""
        set(value) = prefs.edit().putString("name", value).apply()

    fun clear() = prefs.edit().clear().apply()

    fun authHeader(): String = "Bearer ${token ?: ""}"
}
