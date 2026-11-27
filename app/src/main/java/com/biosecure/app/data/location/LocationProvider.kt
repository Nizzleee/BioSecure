package com.biosecure.app.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Ubicación con precisión y antigüedad, para no aceptar GPS viejo o impreciso. */
data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val ageMs: Long
)

/** Ubicación actual con fallback a la última conocida; null si no hay permiso, GPS apagado o señal inválida. */
object LocationProvider {
    private const val MAX_ACCURACY_METERS = 50f
    private const val MAX_AGE_MS = 60_000L

    @SuppressLint("MissingPermission")
    suspend fun currentFix(context: Context): LocationFix? {
        val granted = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return null

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
            !manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        ) return null

        val client = LocationServices.getFusedLocationProviderClient(context)
        return suspendCancellableCoroutine { cont ->
            fun valid(loc: Location?): LocationFix? {
                if (loc == null) return null
                val accuracy = if (loc.hasAccuracy()) loc.accuracy else Float.MAX_VALUE
                val age = if (loc.elapsedRealtimeNanos > 0) {
                    (SystemClock.elapsedRealtimeNanos() - loc.elapsedRealtimeNanos) / 1_000_000L
                } else {
                    System.currentTimeMillis() - loc.time
                }
                if (accuracy > MAX_ACCURACY_METERS || age > MAX_AGE_MS) return null
                return LocationFix(loc.latitude, loc.longitude, accuracy, age)
            }
            fun fallback() {
                client.lastLocation
                    .addOnSuccessListener { loc -> cont.resume(valid(loc)) }
                    .addOnFailureListener { cont.resume(null) }
            }
            try {
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { loc ->
                        val fix = valid(loc)
                        if (fix != null) cont.resume(fix) else fallback()
                    }
                    .addOnFailureListener { fallback() }
            } catch (_: Exception) {
                fallback()
            }
        }
    }

    /** Compatibilidad con pantallas que sólo necesitan latitud/longitud. */
    @SuppressLint("MissingPermission")
    suspend fun current(context: Context): Pair<Double, Double>? =
        currentFix(context)?.let { it.latitude to it.longitude }
}
