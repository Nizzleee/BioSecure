package com.biosecure.app.data.network

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * URL del backend. Se lee del repositorio para que un reinicio del túnel no
 * obligue a recompilar la app. Se usa la API de GitHub (sin caché CDN) y como
 * respaldo el raw con cache-busting. Si no hay internet, se usa la caché local.
 */
object ApiConfig {

    const val DEFAULT_BASE_URL = "https://fioricet-heard-techrepublic-globe.trycloudflare.com/"

    private const val CONFIG_API =
        "https://api.github.com/repos/Nizzleee/BioSecure/contents/api-url.txt?ref=merge/beta03-app"
    private const val CONFIG_RAW =
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

        val remote = fetchFromApi() ?: fetchFromRaw()
        if (isValid(remote)) {
            val url = remote!!
            baseUrl = if (url.endsWith("/")) url else "$url/"
            prefs.edit().putString(KEY_BASE_URL, baseUrl).apply()
        }
    }

    private fun fetchFromApi(): String? = try {
        val request = Request.Builder().url(CONFIG_API).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            val encoded = JSONObject(body).optString("content").replace("\n", "")
            if (encoded.isBlank()) null else String(Base64.decode(encoded, Base64.DEFAULT)).trim()
        }
    } catch (_: Exception) {
        null
    }

    private fun fetchFromRaw(): String? = try {
        val url = "$CONFIG_RAW?t=${System.currentTimeMillis()}"
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.string()?.trim()
        }
    } catch (_: Exception) {
        null
    }

    private fun isValid(url: String?): Boolean =
        !url.isNullOrBlank() && url.startsWith("https://") && url.contains(".")
}
