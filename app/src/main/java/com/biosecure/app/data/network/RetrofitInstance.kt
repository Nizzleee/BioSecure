package com.biosecure.app.data.network

import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okio.Buffer
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitInstance {

    // Con el celular conectado por USB: adb reverse tcp:3001 tcp:3001
    // Para WiFi, cambiar por la IP LAN del PC, ej. http://192.168.18.5:3001/
    private const val BASE_URL = "http://127.0.0.1:3001/"

    /**
     * El backend responde errores con { ok:false, error, code } y HTTP 4xx/5xx.
     * Retrofit lanza HttpException y pierde ese cuerpo; lo convertimos a HTTP 200
     * para que ApiResponse conserve el código y el mensaje real del servidor.
     */
    private val errorBodyInterceptor = { chain: okhttp3.Interceptor.Chain ->
        val response = chain.proceed(chain.request())
        val body = response.body
        if (response.code in 400..599 && body != null) {
            val buffered = okio.Buffer()
            body.source().use { it.readAll(buffered) }
            val raw = buffered.clone().readUtf8()
            if (raw.trimStart().startsWith("{")) {
                response.newBuilder()
                    .code(200)
                    .message("OK")
                    .body(ResponseBody.create(body.contentType(), buffered.readByteArray()))
                    .build()
            } else response
        } else response
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(errorBodyInterceptor)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    val mongoApi: MongoApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MongoApi::class.java)
    }
}
