package com.biosecure.app.data.location

import android.annotation.SuppressLint
import android.location.Location
import android.os.Looper
import android.os.SystemClock
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference

/**
 * Obtiene la mejor ubicación posible en lugar de aceptar la última caché.
 *
 * El servidor rechaza precisiones mayores a MAX_ACCURACY_METERS (50 m), así que
 * si la primera lectura es imprecisa se sigue escuchando al GPS unos segundos
 * hasta encontrar un fix mejor. Sin esto, en interiores la app fallaba con
 * "ACCURACY_TOO_HIGH" incluso teniendo señal.
 */
class BestLocationProvider(private val client: FusedLocationProviderClient) {

    @SuppressLint("MissingPermission")
    suspend fun awaitBest(
        minAccuracyMeters: Float = 50f,
        timeoutMs: Long = 20_000L,
        /** Se llama cada vez que aparece un fix mejor, para mostrar el avance. */
        onProgress: ((Location, Float) -> Unit)? = null
    ): Location? {
        val best = AtomicReference<Location?>(null)

        fun consider(location: Location?) {
            if (location == null || !location.hasAccuracy()) return
            // La "última ubicación" del sistema puede tener días de antigüedad
            // (en este equipo llegó a mostrar et=+10d). Enviarla hace que el
            // backend rechace el marcaje por precisión/ubicación inválida, así
            // que solo aceptamos fixes recientes.
            if (ageMillis(location) > MAX_AGE_MS) return
            val current = best.get()
            if (current == null || location.accuracy < current.accuracy) {
                best.set(location)
                onProgress?.invoke(location, location.accuracy)
            }
        }

        fun goodEnough(limit: Float = minAccuracyMeters): Boolean {
            val location = best.get() ?: return false
            return location.accuracy > 0f && location.accuracy <= limit
        }

        // 1. Última ubicación conocida: respuesta inmediata si es buena y reciente.
        runCatching { client.lastLocation.await() }.getOrNull()?.let(::consider)
        if (goodEnough()) return best.get()

        // 2. Pedir un fix nuevo con alta precisión.
        val token = CancellationTokenSource()
        try {
            runCatching {
                withTimeoutOrNull(8_000L) {
                    client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token).await()
                }
            }.getOrNull()?.let(::consider)
        } finally {
            token.cancel()
        }
        if (goodEnough(EXCELLENT_ACCURACY_METERS)) return best.get()

        // 3. Escuchar actualizaciones para mejorar la precisión. Con
        //    setWaitForAccurateLocation(false) llegan fixes intermedios y
        //    podemos quedarnos con el mejor; con true el proveedor podía no
        //    entregar nada dentro del tiempo de espera.
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
            .setMinUpdateIntervalMillis(500L)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach(::consider)
            }
        }

        return try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                delay(250L)
                // Con ≤35 m hay margen suficiente frente al límite de 50 m.
                if (goodEnough(EXCELLENT_ACCURACY_METERS)) break
            }
            best.get()
        } catch (e: Exception) {
            best.get()
        } finally {
            runCatching { client.removeLocationUpdates(callback) }
        }
    }

    private fun ageMillis(location: Location): Long {
        val elapsed = location.elapsedRealtimeNanos
        if (elapsed <= 0L) return Long.MAX_VALUE
        return (SystemClock.elapsedRealtimeNanos() - elapsed) / 1_000_000L
    }

    private companion object {
        /** Más viejo que esto no sirve para marcar asistencia. */
        const val MAX_AGE_MS = 60_000L

        /** Con esta precisión dejamos de buscar para no hacer esperar de más. */
        const val EXCELLENT_ACCURACY_METERS = 35f
    }
}
