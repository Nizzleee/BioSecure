package com.biosecure.app.data.network

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * URL del backend. Se lee de un archivo público del repositorio para que un
 * reinicio del túnel no obligue a recompilar la app. Si no hay internet o el
 * archivo no existe, se usa la última URL guardada o el valor por defecto.
 */
object ApiConfig {

    const val DEFAULT_BASE_URL = "https://donor-pete-carb-engage.trycloudflare.com/"

    private const val CONFIG_URL =
        "https://raw.githubusercontent.com/Nizzleee/BioSecure/merge/beta03-app/api-url.txt"
    private const val PREFS_NAME = "biosecure_api_config"
    private const val KEY_BASE_URL = "base_url"

    @Volatile
    var baseUrl: String = DEFAULT_BASE_URL
        private set

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun init(context: Context) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(KEY_BASE_URL, null)?.let { cached ->
            if (isValid(cached)) baseUrl = cached
        }

        try {
            val request = Request.Builder().url(CONFIG_URL).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use
                val remote = response.body?.string()?.trim().orEmpty()
                if (isValid(remote)) {
                    baseUrl = if (remote.endsWith("/")) remote else "$remote/"
                    prefs.edit().putString(KEY_BASE_URL, baseUrl).apply()
                }
            }
        } catch (_: Exception) {
            // Sin conexión: se mantiene la URL en caché o el valor por defecto.
        }
    }

    private fun isValid(url: String): Boolean =
        url.startsWith("https://") && url.contains(".")
}
